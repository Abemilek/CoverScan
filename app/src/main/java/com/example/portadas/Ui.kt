package com.example.portadas

import android.Manifest
import android.util.Size
import android.view.MotionEvent
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App(vm: MainViewModel = viewModel()) {
    val context = LocalContext.current
    val docs by vm.docs.collectAsState()
    val cola by vm.cola.collectAsState()
    val editando by vm.editando.collectAsState()
    val ocr by vm.ocrEnCurso.collectAsState()
    val mensaje by vm.mensaje.collectAsState()

    var pantalla by rememberSaveable { mutableStateOf(0) }
    var linterna by rememberSaveable { mutableStateOf(false) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var camara by remember { mutableStateOf<Camera?>(null) }
    var tienePermiso by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }

    val permisoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        tienePermiso = it
    }
    val galeria = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        vm.procesarUris(uris)
    }
    val exportar = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) vm.exportar(uri)
    }

    LaunchedEffect(Unit) {
        if (!tienePermiso) permisoLauncher.launch(Manifest.permission.CAMERA)
    }
    LaunchedEffect(mensaje) {
        mensaje?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            vm.limpiarMensaje()
        }
    }

    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (pantalla == 0) Button({ }, Modifier.weight(1f)) { Text("Escanear") }
                else OutlinedButton({ pantalla = 0 }, Modifier.weight(1f)) { Text("Escanear") }

                if (pantalla == 1) Button({ }, Modifier.weight(1f)) { Text("Lista (${docs.size})") }
                else OutlinedButton({ pantalla = 1 }, Modifier.weight(1f)) { Text("Lista (${docs.size})") }

                OutlinedButton(
                    onClick = {
                        if (docs.isEmpty()) {
                            Toast.makeText(context, "Aún no hay documentos guardados", Toast.LENGTH_SHORT).show()
                        } else {
                            val nombre = "coverscan_" +
                                SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date()) + ".json"
                            exportar.launch(nombre)
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) { Text("Exportar") }
            }

            if (pantalla == 0) {
                Column(Modifier.weight(1f).fillMaxWidth()) {
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        if (tienePermiso) {
                            CameraPreview(
                                modifier = Modifier.fillMaxSize(),
                                onReady = { captura, cam ->
                                    imageCapture = captura
                                    camara = cam
                                    if (linterna) cam.cameraControl.enableTorch(true)
                                },
                                onRelease = {
                                    imageCapture = null
                                    camara = null
                                },
                                onError = { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
                            )
                        } else {
                            Column(
                                Modifier.fillMaxSize().padding(24.dp),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text("Se necesita permiso de cámara (o usa la galería).")
                                Button({ permisoLauncher.launch(Manifest.permission.CAMERA) }) { Text("Dar permiso") }
                            }
                        }
                        if (ocr > 0) CircularProgressIndicator(Modifier.align(Alignment.Center))
                    }
                    Text(
                        "Guardados: ${docs.size} · Por revisar: ${cola.size}",
                        Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Row(
                        Modifier.fillMaxWidth().padding(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                imageCapture?.let { vm.capturar(it, ContextCompat.getMainExecutor(context)) }
                            },
                            enabled = imageCapture != null && ocr == 0 && tienePermiso,
                            modifier = Modifier.weight(2f)
                        ) { Text("Capturar") }
                        OutlinedButton({ galeria.launch("image/*") }, Modifier.weight(1f)) { Text("Galería") }
                        if (camara?.cameraInfo?.hasFlashUnit() == true) {
                            OutlinedButton(
                                onClick = {
                                    linterna = !linterna
                                    camara?.cameraControl?.enableTorch(linterna)
                                },
                                modifier = Modifier.weight(1f)
                            ) { Text(if (linterna) "Luz: sí" else "Luz: no") }
                        }
                    }
                }
            } else {
                ListaScreen(
                    docs = docs,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    onEditar = { vm.editar(it) },
                    onBorrar = { vm.borrar(it) }
                )
            }
        }
    }

    // Revisión: primero lo recién escaneado, si no, el que se está editando desde la lista
    val nuevo = cola.firstOrNull()
    if (nuevo != null) {
        RevisionDialog(
            doc = nuevo,
            titulo = "Revisar escaneo" + if (cola.size > 1) " (+${cola.size - 1} en espera)" else "",
            textoGuardar = "Guardar y seguir",
            textoCancelar = "Descartar",
            onGuardar = { vm.guardarNuevo(it) },
            onCancelar = { vm.descartarPrimero() }
        )
    } else if (editando != null) {
        RevisionDialog(
            doc = editando!!,
            titulo = "Editar documento",
            textoGuardar = "Guardar cambios",
            textoCancelar = "Cancelar",
            onGuardar = { vm.guardarEdicion(it) },
            onCancelar = { vm.cancelarEdicion() }
        )
    }
}

@Composable
fun CameraPreview(
    modifier: Modifier,
    onReady: (ImageCapture, Camera) -> Unit,
    onRelease: () -> Unit,
    onError: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context) }
    var camara by remember { mutableStateOf<Camera?>(null) }

    LaunchedEffect(Unit) {
        val futuro = ProcessCameraProvider.getInstance(context)
        futuro.addListener({
            try {
                val provider = futuro.get()
                val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
                // ML Kit no mejora con caracteres de más de ~24 px: ~12 MP bastan y el OCR va más rápido
                val resolucion = ResolutionSelector.Builder()
                    .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                    .setResolutionStrategy(
                        ResolutionStrategy(Size(4032, 3024), ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER)
                    )
                    .build()
                val captura = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                    .setResolutionSelector(resolucion)
                    .build()
                provider.unbindAll()
                val cam = provider.bindToLifecycle(
                    lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, captura
                )
                camara = cam
                onReady(captura, cam)
            } catch (e: Exception) {
                onError("No se pudo iniciar la cámara: ${e.message}")
            }
        }, ContextCompat.getMainExecutor(context))
    }
    DisposableEffect(Unit) {
        onDispose {
            onRelease()
            runCatching { ProcessCameraProvider.getInstance(context).get().unbindAll() }
        }
    }
    AndroidView(
        factory = {
            previewView.apply {
                // Tocar la vista previa enfoca en ese punto
                setOnTouchListener { v, ev ->
                    if (ev.action == MotionEvent.ACTION_UP) {
                        camara?.let { c ->
                            val punto = meteringPointFactory.createPoint(ev.x, ev.y)
                            c.cameraControl.startFocusAndMetering(FocusMeteringAction.Builder(punto).build())
                        }
                        v.performClick()
                    }
                    true
                }
            }
        },
        modifier = modifier
    )
}

@Composable
fun ListaScreen(
    docs: List<Documento>,
    modifier: Modifier,
    onEditar: (Documento) -> Unit,
    onBorrar: (Documento) -> Unit
) {
    var aBorrar by remember { mutableStateOf<Documento?>(null) }

    if (docs.isEmpty()) {
        Box(modifier, contentAlignment = Alignment.Center) { Text("Todavía no hay documentos.") }
    } else {
        LazyColumn(modifier) {
            items(docs, key = { it.id }) { d ->
                Card(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp).clickable { onEditar(d) }) {
                    Column(Modifier.padding(12.dp)) {
                        Text(d.categoria, style = MaterialTheme.typography.labelSmall)
                        Text(d.titulo.ifBlank { "(sin título)" }, style = MaterialTheme.typography.titleMedium)
                        if (d.autores.isNotEmpty()) Text(d.autores.joinToString(", "), style = MaterialTheme.typography.bodySmall)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            TextButton({ aBorrar = d }) { Text("Borrar") }
                        }
                    }
                }
            }
        }
    }

    aBorrar?.let { d ->
        AlertDialog(
            onDismissRequest = { aBorrar = null },
            title = { Text("¿Borrar este documento?") },
            text = { Text(d.titulo.ifBlank { "(sin título)" }) },
            confirmButton = { TextButton({ onBorrar(d); aBorrar = null }) { Text("Borrar") } },
            dismissButton = { TextButton({ aBorrar = null }) { Text("Cancelar") } }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RevisionDialog(
    doc: Documento,
    titulo: String,
    textoGuardar: String,
    textoCancelar: String,
    onGuardar: (Documento) -> Unit,
    onCancelar: () -> Unit
) {
    var categoria by rememberSaveable(doc.id) { mutableStateOf(doc.categoria) }
    var tipoTrabajo by rememberSaveable(doc.id) { mutableStateOf(doc.tipoTrabajo) }
    var tituloDoc by rememberSaveable(doc.id) { mutableStateOf(doc.titulo) }
    var autores by rememberSaveable(doc.id) { mutableStateOf(doc.autores.joinToString("\n")) }
    var tutores by rememberSaveable(doc.id) { mutableStateOf(doc.tutores.joinToString("\n")) }
    var institucion by rememberSaveable(doc.id) { mutableStateOf(doc.institucion) }
    var facultad by rememberSaveable(doc.id) { mutableStateOf(doc.facultadArea) }
    var fecha by rememberSaveable(doc.id) { mutableStateOf(doc.fechaLugar) }
    var otros by rememberSaveable(doc.id) { mutableStateOf(doc.otros.joinToString("\n")) }
    var extras by rememberSaveable(doc.id) {
        mutableStateOf(doc.camposExtra.entries.joinToString("\n") { "${it.key}: ${it.value}" })
    }
    var notas by rememberSaveable(doc.id) { mutableStateOf(doc.notas) }
    var texto by rememberSaveable(doc.id) { mutableStateOf(doc.textoCompleto) }

    fun lineas(s: String) = s.split("\n").map { it.trim() }.filter { it.isNotEmpty() }

    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = false,
            dismissOnClickOutside = false
        )
    ) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                Column(
                    Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(titulo, style = MaterialTheme.typography.titleLarge)

                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CATEGORIAS.forEach { c ->
                            FilterChip(selected = categoria == c, onClick = { categoria = c }, label = { Text(c) })
                        }
                    }
                    Campo("Título", tituloDoc, 2) { tituloDoc = it }
                    Campo("Tipo de trabajo", tipoTrabajo, 1) { tipoTrabajo = it }
                    Campo("Autores (uno por línea)", autores, 2) { autores = it }
                    Campo("Tutores / asesores (uno por línea)", tutores, 2) { tutores = it }
                    Campo("Institución", institucion, 1) { institucion = it }
                    Campo("Facultad / área / centro", facultad, 1) { facultad = it }
                    Campo("Fecha / lugar", fecha, 1) { fecha = it }
                    Campo("Otros campos (Etiqueta: valor, uno por línea)", extras, 2) { extras = it }
                    Campo("Otros textos (uno por línea)", otros, 2) { otros = it }
                    Campo("Notas (ej. ubicación en la bodega)", notas, 1) { notas = it }
                    Campo("Texto completo leído (OCR)", texto, 4) { texto = it }
                }
                Row(
                    Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(onCancelar, Modifier.weight(1f)) { Text(textoCancelar) }
                    Button(
                        onClick = {
                            // "Etiqueta: valor" → campos extra; las líneas sin dos puntos pasan a "otros"
                            val extrasMapa = linkedMapOf<String, String>()
                            val sueltas = mutableListOf<String>()
                            lineas(extras).forEach { ln ->
                                val p = ln.indexOf(':')
                                if (p > 0 && ln.substring(p + 1).isNotBlank()) {
                                    extrasMapa[ln.substring(0, p).trim()] = ln.substring(p + 1).trim()
                                } else {
                                    sueltas += ln
                                }
                            }
                            onGuardar(
                                doc.copy(
                                    categoria = categoria,
                                    tipoTrabajo = tipoTrabajo.trim(),
                                    titulo = tituloDoc.trim(),
                                    autores = lineas(autores),
                                    tutores = lineas(tutores),
                                    institucion = institucion.trim(),
                                    facultadArea = facultad.trim(),
                                    fechaLugar = fecha.trim(),
                                    otros = lineas(otros) + sueltas,
                                    camposExtra = extrasMapa,
                                    notas = notas.trim(),
                                    textoCompleto = texto
                                )
                            )
                        },
                        modifier = Modifier.weight(2f)
                    ) { Text(textoGuardar) }
                }
            }
        }
    }
}

@Composable
private fun Campo(etiqueta: String, valor: String, lineasMin: Int, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = valor,
        onValueChange = onChange,
        label = { Text(etiqueta) },
        minLines = lineasMin,
        modifier = Modifier.fillMaxWidth()
    )
}
