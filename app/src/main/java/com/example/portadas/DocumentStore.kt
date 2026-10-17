package com.example.portadas

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Guarda todo en un único archivo JSON interno (se actualiza con cada documento guardado). */
class DocumentStore(context: Context) {
    private val archivo = File(context.filesDir, "documentos.json")

    fun load(): List<Documento> {
        if (!archivo.exists()) return emptyList()
        return try {
            fromJsonString(archivo.readText(Charsets.UTF_8))
        } catch (e: Exception) {
            archivo.renameTo(File(archivo.parentFile, "documentos.corrupto.json"))
            emptyList()
        }
    }

    fun save(docs: List<Documento>) {
        val tmp = File(archivo.parentFile, "documentos.json.tmp")
        tmp.writeText(toJsonString(docs), Charsets.UTF_8)
        if (!tmp.renameTo(archivo)) {
            archivo.delete()
            tmp.renameTo(archivo)
        }
    }

    companion object {
        fun toJsonString(docs: List<Documento>): String {
            val raiz = JSONObject()
            raiz.put("version", 1)
            raiz.put("exportado_en", SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).format(Date()))
            raiz.put("total", docs.size)
            val arr = JSONArray()
            docs.forEach { arr.put(toJson(it)) }
            raiz.put("documentos", arr)
            return raiz.toString(2).replace("\\/", "/")
        }

        fun fromJsonString(s: String): List<Documento> {
            val arr = JSONObject(s).optJSONArray("documentos") ?: return emptyList()
            return List(arr.length()) { fromJson(arr.getJSONObject(it)) }
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
            put("texto_completo", txt(d.textoCompleto))
            put("notas", txt(d.notas))
            put("escaneado_en", d.escaneadoEn)
        }

        private fun str(o: JSONObject, k: String): String =
            if (o.isNull(k)) "" else o.optString(k, "")

        private fun fromJson(o: JSONObject) = Documento(
            id = str(o, "id"),
            categoria = str(o, "categoria").ifBlank { "Otro" },
            tipoTrabajo = str(o, "tipo_trabajo"),
            titulo = str(o, "titulo"),
            autores = lista(o.optJSONArray("autores")),
            tutores = lista(o.optJSONArray("tutores")),
            institucion = str(o, "institucion"),
            facultadArea = str(o, "facultad_area"),
            fechaLugar = str(o, "fecha_lugar"),
            otros = lista(o.optJSONArray("otros")),
            textoCompleto = str(o, "texto_completo"),
            notas = str(o, "notas"),
            escaneadoEn = str(o, "escaneado_en")
        )

        private fun lista(a: JSONArray?): List<String> =
            if (a == null) emptyList() else List(a.length()) { a.getString(it) }
    }
}
