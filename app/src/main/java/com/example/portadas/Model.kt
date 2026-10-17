package com.example.portadas

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

val CATEGORIAS = listOf("Tesis/Seminario", "Disco/CD", "Libro", "Otro")

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
    val textoCompleto: String = "",
    val notas: String = "",
    val escaneadoEn: String = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).format(Date())
)
