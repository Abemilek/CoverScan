package com.example.portadas

import android.content.Context
import android.util.AtomicFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * Guarda todo en un único archivo JSON interno (se actualiza con cada documento guardado).
 * Usa AtomicFile: si la app se cierra a mitad de una escritura, se conserva la versión anterior.
 * [nombre] permite reutilizarlo para otros archivos (p. ej. "pendientes.json").
 */
class DocumentStore(context: Context, nombre: String = "documentos.json") {
    private val archivo = AtomicFile(File(context.filesDir, nombre))

    /** true si al cargar se encontró un archivo ilegible (se renombró a documentos.corrupto.json). */
    var archivoCorrupto = false
        private set

    fun load(): List<Documento> {
        if (!archivo.baseFile.exists()) return emptyList()
        return try {
            fromJsonString(String(archivo.readFully(), Charsets.UTF_8))
        } catch (e: Exception) {
            val base = archivo.baseFile
            base.renameTo(File(base.parentFile, base.name.removeSuffix(".json") + ".corrupto.json"))
            archivoCorrupto = true
            emptyList()
        }
    }

    @Throws(Exception::class)
    fun save(docs: List<Documento>) {
        val salida = archivo.startWrite()
        try {
            salida.write(toJsonString(docs).toByteArray(Charsets.UTF_8))
            archivo.finishWrite(salida)
        } catch (e: Exception) {
            archivo.failWrite(salida)
            throw e
        }
    }

    companion object {
        fun toJsonString(docs: List<Documento>): String {
            val raiz = JSONObject()
            raiz.put("version", 1)
            raiz.put("exportado_en", ahoraIso())
            raiz.put("total", docs.size)
            val arr = JSONArray()
            docs.forEach { arr.put(toJson(it)) }
            raiz.put("documentos", arr)
            return raiz.toString(2).replace("\\/", "/")
        }

        /** Lanza excepción solo si el JSON raíz está mal formado; los registros dañados se omiten. */
        fun fromJsonString(s: String): List<Documento> {
            val arr = JSONObject(s).optJSONArray("documentos") ?: return emptyList()
            val docs = ArrayList<Documento>(arr.length())
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                try {
                    docs += fromJson(o)
                } catch (e: Exception) {
                    // Registro dañado: se salta para no perder el resto del archivo
                }
            }
            return docs
        }

        // Los campos que no aparecen en la portada se guardan como null (la clave siempre existe)
        private fun txt(s: String): Any = if (s.isBlank()) JSONObject.NULL else s
        private fun arr(l: List<String>): Any = if (l.isEmpty()) JSONObject.NULL else JSONArray(l)

        private fun toJson(d: Documento) = JSONObject().apply {
            put("id", d.id)
            put("categoria", d.categoria)
            put("tipo_trabajo", txt(d.tipoTrabajo))
            put("titulo", txt(d.titulo))
            put("autores", arr(d.autores))
            put("tutores", arr(d.tutores))
            put("institucion", txt(d.institucion))
            put("facultad_area", txt(d.facultadArea))
            put("fecha_lugar", txt(d.fechaLugar))
            put("otros", arr(d.otros))
            put("campos_extra", if (d.camposExtra.isEmpty()) JSONObject.NULL else JSONObject(d.camposExtra))
            put("texto_completo", txt(d.textoCompleto))
            put("notas", txt(d.notas))
            put("escaneado_en", d.escaneadoEn)
        }

        private fun str(o: JSONObject, k: String): String =
            if (o.isNull(k)) "" else o.optString(k, "")

        private fun fromJson(o: JSONObject) = Documento(
            id = str(o, "id").ifBlank { UUID.randomUUID().toString() },
            categoria = str(o, "categoria").ifBlank { "Otro" },
            tipoTrabajo = str(o, "tipo_trabajo"),
            titulo = str(o, "titulo"),
            autores = lista(o.optJSONArray("autores")),
            tutores = lista(o.optJSONArray("tutores")),
            institucion = str(o, "institucion"),
            facultadArea = str(o, "facultad_area"),
            fechaLugar = str(o, "fecha_lugar"),
            otros = lista(o.optJSONArray("otros")),
            camposExtra = o.optJSONObject("campos_extra")?.let { j ->
                j.keys().asSequence().associateWith { k -> j.optString(k) }
            } ?: emptyMap(),
            textoCompleto = str(o, "texto_completo"),
            notas = str(o, "notas"),
            escaneadoEn = str(o, "escaneado_en").ifBlank { ahoraIso() }
        )

        private fun lista(a: JSONArray?): List<String> =
            if (a == null) emptyList() else List(a.length()) { a.optString(it) }.filter { it.isNotBlank() }
    }
}
