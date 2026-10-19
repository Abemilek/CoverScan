package com.example.portadas

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

val CATEGORIAS = listOf("Tesis/Seminario", "Disco/CD", "Libro", "Otro")

/** Fecha y hora local con zona horaria, p. ej. 2026-10-17T15:30:00-06:00 */
fun ahoraIso(): String = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US).format(Date())

data class Documento(
    val id: String = UUID.randomUUID().toString(),
    val categoria: String = "Otro",
    val tipoTrabajo: String = "",
    val titulo: String = "",
    val autores: List<String> = emptyList(),
    val tutores: List<String> = emptyList(),
    val institucion: String = "",
    val facultadArea: String = "",
    val fechaLugar: String = "",
    val otros: List<String> = emptyList(),
    /** Pares "Etiqueta: valor" que no encajan en otro campo (Carrera, Asignatura, Componente…). */
    val camposExtra: Map<String, String> = emptyMap(),
    val textoCompleto: String = "",
    val notas: String = "",
    val escaneadoEn: String = ahoraIso()
)
