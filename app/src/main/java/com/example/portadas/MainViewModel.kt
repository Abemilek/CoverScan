package com.example.portadas

import android.app.Application
import android.net.Uri
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.lifecycle.AndroidViewModel
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.text.Normalizer
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import kotlin.math.abs

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val store = DocumentStore(app)
    private val storeCola = DocumentStore(app, "pendientes.json")
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    /** Un solo hilo para el disco: las escrituras se hacen en orden y sin bloquear la interfaz. */
    private val io = Executors.newSingleThreadExecutor()

    private val _docs = MutableStateFlow(store.load())
    val docs: StateFlow<List<Documento>> = _docs.asStateFlow()

    /** Escaneos leídos que esperan revisión (el primero es el que se muestra). Se guardan en disco. */
    private val _cola = MutableStateFlow(storeCola.load())
    val cola: StateFlow<List<Documento>> = _cola.asStateFlow()

    private val _editando = MutableStateFlow<Documento?>(null)
    val editando: StateFlow<Documento?> = _editando.asStateFlow()

    private val _ocr = MutableStateFlow(0)
    val ocrEnCurso: StateFlow<Int> = _ocr.asStateFlow()

    private val _mensaje = MutableStateFlow(
        when {
            store.archivoCorrupto ->
                "El archivo interno estaba dañado; se guardó como documentos.corrupto.json"
            storeCola.archivoCorrupto ->
                "Los pendientes de revisión estaban dañados; se guardaron como pendientes.corrupto.json"
            else -> null
        }
    )
    val mensaje: StateFlow<String?> = _mensaje.asStateFlow()

    fun limpiarMensaje() { _mensaje.value = null }

    override fun onCleared() {
        io.shutdown() // deja terminar las escrituras pendientes
        recognizer.close()
    }

    // ---------- Captura ----------

    @OptIn(ExperimentalGetImage::class)
    fun capturar(ic: ImageCapture, executor: Executor) {
        _ocr.update { it + 1 }
        ic.takePicture(executor, object : ImageCapture.OnImageCapturedCallback() {
            @androidx.annotation.OptIn(markerClass = [ExperimentalGetImage::class])
            override fun onCaptureSuccess(image: ImageProxy) {
                val media = image.image
                if (media == null) {
                    image.close()
                    _ocr.update { it - 1 }
                    return
                }
                val input = InputImage.fromMediaImage(media, image.imageInfo.rotationDegrees)
                _ocr.update { it - 1 }
                reconocer(input) { image.close() }
            }

            override fun onError(exception: ImageCaptureException) {
                _ocr.update { it - 1 }
                _mensaje.value = "Error al capturar: ${exception.message}"
            }
        })
    }

    fun procesarUris(uris: List<Uri>) {
        if (uris.isEmpty()) return
        val input = try {
            InputImage.fromFilePath(getApplication(), uris.first())
        } catch (e: Exception) {
            null
        }
        if (input == null) {
            _mensaje.value = "No se pudo abrir una de las imágenes"
            procesarUris(uris.drop(1))
        } else {
            reconocer(input) { procesarUris(uris.drop(1)) }
        }
    }

    private fun reconocer(input: InputImage, alTerminar: () -> Unit) {
        _ocr.update { it + 1 }
        recognizer.process(input)
            .addOnSuccessListener { texto ->
                val filas = filasDeTexto(texto)
                if (filas.isEmpty()) {
                    _mensaje.value = "No se detectó texto, intenta de nuevo"
                } else {
                    _cola.update { it + PortadaParser.parse(filas) }
                    persistirCola()
                }
            }
            .addOnFailureListener { _mensaje.value = "Error de OCR: ${it.message}" }
            .addOnCompleteListener {
                _ocr.update { it - 1 }
                alTerminar()
            }
    }

    /** Ordena las líneas de arriba a abajo y junta las que están a la misma altura ("Autoras:" + nombre). */
    private fun filasDeTexto(texto: Text): List<String> {
        val todas = texto.textBlocks.flatMap { it.lines }
        val conCaja = todas.filter { it.boundingBox != null }.sortedBy { it.boundingBox!!.exactCenterY() }
        val sinCaja = todas.filter { it.boundingBox == null }.map { it.text }
        val filas = mutableListOf<MutableList<Text.Line>>()
        for (linea in conCaja) {
            val actual = filas.lastOrNull()
            if (actual != null) {
                val ref = actual.first().boundingBox!!
                val b = linea.boundingBox!!
                val tol = 0.5f * minOf(ref.height(), b.height())
                if (abs(b.exactCenterY() - ref.exactCenterY()) < tol) {
                    actual.add(linea)
                    continue
                }
            }
            filas.add(mutableListOf(linea))
        }
        return filas.map { f -> f.sortedBy { it.boundingBox!!.left }.joinToString(" ") { it.text } } + sinCaja
    }

    // ---------- Guardado / edición ----------

    /** Escribe el estado actual en disco (en segundo plano). Si falla, avisa en vez de cerrar la app. */
    private fun persistir() {
        val copia = _docs.value
        io.execute {
            try {
                store.save(copia)
            } catch (e: Exception) {
                _mensaje.value = "No se pudo guardar en el teléfono: ${e.message}"
            }
        }
    }

    /** Igual que persistir(), pero para la cola de pendientes de revisión. */
    private fun persistirCola() {
        val copia = _cola.value
        io.execute {
            try {
                storeCola.save(copia)
            } catch (e: Exception) {
                _mensaje.value = "No se pudieron guardar los pendientes: ${e.message}"
            }
        }
    }

    /** Texto sin tildes, en minúsculas y sin espacios ni signos, para comparar ("García" == "Garcia"). */
    private fun normalizar(s: String): String =
        Normalizer.normalize(s, Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .lowercase().filter { it.isLetterOrDigit() }

    /** Dos documentos son el mismo si coinciden título y primer autor (el autor se ignora si falta). */
    private fun mismoDocumento(a: Documento, b: Documento): Boolean =
        a.titulo.isNotBlank() && b.titulo.isNotBlank() &&
            normalizar(a.titulo) == normalizar(b.titulo) &&
            (a.autores.isEmpty() || b.autores.isEmpty() ||
                normalizar(a.autores.first()) == normalizar(b.autores.first()))

    fun guardarNuevo(d: Documento) {
        val duplicado = _docs.value.any { mismoDocumento(it, d) }
        _docs.update { listOf(d) + it }
        persistir()
        _cola.update { it.drop(1) }
        persistirCola()
        if (duplicado) _mensaje.value = "Aviso: ya existía un documento con el mismo título (se guardó igual)"
    }

    fun descartarPrimero() {
        _cola.update { it.drop(1) }
        persistirCola()
    }

    fun editar(d: Documento) { _editando.value = d }
    fun cancelarEdicion() { _editando.value = null }

    fun guardarEdicion(d: Documento) {
        _docs.update { lista -> lista.map { if (it.id == d.id) d else it } }
        persistir()
        _editando.value = null
    }

    fun borrar(d: Documento) {
        _docs.update { lista -> lista.filter { it.id != d.id } }
        persistir()
    }

    /** Exporta en el orden en que se escanearon (el más antiguo primero). El resultado se avisa con un mensaje. */
    fun exportar(uri: Uri) {
        val copia = _docs.value.reversed()
        io.execute {
            val ok = try {
                val salida = getApplication<Application>().contentResolver.openOutputStream(uri, "wt")
                if (salida == null) {
                    false
                } else {
                    salida.use { it.write(DocumentStore.toJsonString(copia).toByteArray(Charsets.UTF_8)) }
                    true
                }
            } catch (e: Exception) {
                false
            }
            _mensaje.value =
                if (ok) "JSON guardado (${copia.size} documentos)" else "No se pudo guardar el archivo"
        }
    }

    /**
     * Fusiona un JSON exportado (p. ej. el de otro teléfono del equipo): agrega los documentos
     * cuyo id todavía no exista. El resto del archivo se conserva y todo queda en orden de escaneo.
     */
    fun importar(uri: Uri) {
        io.execute {
            val importados = try {
                val texto = getApplication<Application>().contentResolver.openInputStream(uri)
                    ?.use { it.readBytes().toString(Charsets.UTF_8) }
                    .orEmpty()
                if (texto.isBlank()) emptyList() else DocumentStore.fromJsonString(texto)
            } catch (e: Exception) {
                _mensaje.value = "No se pudo leer el archivo JSON"
                return@execute
            }
            if (importados.isEmpty()) {
                _mensaje.value = "El archivo no contiene documentos"
                return@execute
            }
            val existentes = _docs.value.mapTo(HashSet()) { it.id }
            val nuevos = importados.filter { it.id !in existentes }
            val repetidos = importados.size - nuevos.size
            _docs.update { (it + nuevos).sortedByDescending { doc -> doc.escaneadoEn } }
            persistir()
            _mensaje.value = buildString {
                append("Importados ${nuevos.size} documento(s)")
                if (repetidos > 0) append("; $repetidos ya existían")
            }
        }
    }
}
