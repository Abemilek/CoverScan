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
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import kotlin.math.abs

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val store = DocumentStore(app)
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    /** Un solo hilo para el disco: las escrituras se hacen en orden y sin bloquear la interfaz. */
    private val io = Executors.newSingleThreadExecutor()

    private val _docs = MutableStateFlow(store.load())
    val docs: StateFlow<List<Documento>> = _docs.asStateFlow()

    /** Escaneos leídos que esperan revisión (el primero es el que se muestra). */
    private val _cola = MutableStateFlow<List<Documento>>(emptyList())
    val cola: StateFlow<List<Documento>> = _cola.asStateFlow()

    private val _editando = MutableStateFlow<Documento?>(null)
    val editando: StateFlow<Documento?> = _editando.asStateFlow()

    private val _ocr = MutableStateFlow(0)
    val ocrEnCurso: StateFlow<Int> = _ocr.asStateFlow()

    private val _mensaje = MutableStateFlow(
        if (store.archivoCorrupto)
            "El archivo interno estaba dañado; se guardó como documentos.corrupto.json"
        else null
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

    private fun normalizar(s: String) = s.lowercase().filter { it.isLetterOrDigit() }

    fun guardarNuevo(d: Documento) {
        val duplicado = d.titulo.isNotBlank() &&
            _docs.value.any { normalizar(it.titulo) == normalizar(d.titulo) }
        _docs.update { listOf(d) + it }
        persistir()
        _cola.update { it.drop(1) }
        if (duplicado) _mensaje.value = "Aviso: ya existía un documento con el mismo título (se guardó igual)"
    }

    fun descartarPrimero() { _cola.update { it.drop(1) } }

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
}
