package com.example.portadas

/**
 * Convierte las filas de texto del OCR en campos. Es una heurística: las portadas no son
 * iguales, así que lo que no se reconoce va a "otros" y todo se puede corregir en la revisión.
 * Para ajustar el reconocimiento, edita las expresiones regulares de este archivo.
 */
object PortadaParser {
    private val IC = RegexOption.IGNORE_CASE

    private val prefijoTitulo =
        Regex("""^(br|bra|bach|msc|m\.sc|dr|dra|lic|licda|ing|mtro|mtra|phd|prof|profa)\b\.?\s""", IC)

    private val rol = Regex(
        """^\s*((?:co)?(?:autor|tutor|asesor|director|alumn|estudiante|sustentante|presentad[oa] por|elaborad[oa] por|realizad[oa] por)(?:a|as|es|o|os|s)?(?:\s*/\s*(?:es|as|a))?""" +
            """(?:\s+(?:cient[ií]fic[oa]|metodol[oó]gic[oa]|principal|acad[eé]mic[oa]))?)""" +
            """(?=[\s:]|$)\s*:?\s*(.*)$""", IC
    )

    private val unidad = Regex(
        """^(facultad|[aá]rea de conocimiento|centro universitario|departamento|escuela|instituto|carrera|maestr[ií]a|doctorado)\b""", IC
    )

    private val tipoTrabajoRe = Regex(
        """^[^\p{L}]*(inscripci[oó]n de tema|seminario de graduaci[oó]n|seminario para optar|seminario de|tesis|monograf[ií]a|trabajo de (graduaci[oó]n|curso|diploma)|informe final|protocolo de)""", IC
    )

    private val fechaRe = Regex(
        """(enero|febrero|marzo|abril|mayo|junio|julio|agosto|se[p]?tiembre|octubre|noviembre|diciembre)\b.{0,14}\b(19|20)\d{2}""", IC
    )

    private val lemaRe = Regex("""universidad del pueblo""", IC)
    private val unanRe = Regex("""\bunan\b""", IC)
    private val apellidosRe = Regex("""^\p{Lu}\p{Ll}+(\s*,\s*\p{Lu}\p{Ll}+){1,5}$""")
    private val prefijoBr = Regex("""^(br|bra)\.?\s""", IC)

    fun parse(filas: List<String>): Documento {
        val l = filas.map { it.trim() }.filter { it.isNotEmpty() }
        val n = l.size
        val usada = BooleanArray(n)

        // Lema de la universidad (se descarta)
        for (i in 0 until n) if (lemaRe.containsMatchIn(l[i])) usada[i] = true

        // Institución: bloque que empieza en "Universidad..." y termina en "UNAN..."
        val instituciones = mutableListOf<String>()
        var i = 0
        while (i < n) {
            if (!usada[i] && l[i].contains("universidad", true)) {
                var fin = -1
                for (j in i..minOf(n - 1, i + 7)) {
                    if (usada[j]) break
                    if (unanRe.containsMatchIn(l[j])) { fin = j; break }
                }
                if (fin >= 0) {
                    instituciones += l.subList(i, fin + 1).joinToString(" ")
                    for (k in i..fin) usada[k] = true
                    i = fin + 1
                    continue
                }
            }
            i++
        }
        val institucion = instituciones.firstOrNull().orEmpty()

        // Facultad / área / centro
        val unidades = mutableListOf<String>()
        for (k in 0 until n) {
            if (!usada[k] && unidad.containsMatchIn(l[k])) {
                usada[k] = true
                var txt = l[k]
                if (txt.length <= 24 && k + 1 < n && !usada[k + 1] &&
                    !unidad.containsMatchIn(l[k + 1]) && rol.find(l[k + 1]) == null
                ) {
                    txt += " " + l[k + 1]
                    usada[k + 1] = true
                }
                unidades += txt
            }
        }

        // Autores / tutores / asesores
        val autores = mutableListOf<String>()
        val tutores = mutableListOf<String>()
        var p = 0
        while (p < n) {
            val m = if (!usada[p]) rol.find(l[p]) else null
            if (m == null) { p++; continue }
            usada[p] = true
            val etiqueta = m.groupValues[1].trim().replaceFirstChar { it.uppercase() }
            val nombres = mutableListOf<String>()
            if (m.groupValues[2].isNotBlank()) nombres += m.groupValues[2].trim()
            var j = p + 1
            while (j < n && !usada[j] && rol.find(l[j]) == null && !unidad.containsMatchIn(l[j]) &&
                (prefijoTitulo.containsMatchIn(l[j]) ||
                    (nombres.isEmpty() && l[j].length <= 60 && l[j].none { it.isDigit() }))
            ) {
                nombres += l[j]
                usada[j] = true
                j++
            }
            if (Regex("autor|alumn|estudiante|sustentante|presentad|elaborad|realizad", IC).containsMatchIn(etiqueta)) {
                autores += nombres.map { it.replace(prefijoBr, "") }
            } else {
                tutores += nombres.map { "$etiqueta: $it" }
            }
            p = j
        }

        // Tipo de trabajo (Seminario de graduación, Inscripción de tema, ...)
        var tipoTrabajo = ""
        for (k in 0 until n) {
            if (!usada[k] && tipoTrabajoRe.containsMatchIn(l[k])) {
                usada[k] = true
                tipoTrabajo = l[k]
                if (l[k].contains("optar", true) && k + 1 < n && !usada[k + 1] && l[k + 1].length < 50) {
                    tipoTrabajo += " " + l[k + 1]
                    usada[k + 1] = true
                }
                break
            }
        }

        // Fecha / lugar: línea corta con mes y año, en la parte final de la portada
        var fechaLugar = ""
        for (k in n - 1 downTo 0) {
            if (!usada[k] && k >= (n * 0.6).toInt() && l[k].length <= 50 && fechaRe.containsMatchIn(l[k])) {
                usada[k] = true
                fechaLugar = l[k].trim().trimEnd('.')
                break
            }
        }

        // Autores sin etiqueta, tipo "Ramos, Pérez, Palacios"
        if (autores.isEmpty()) {
            for (k in 0 until n) {
                if (!usada[k] && l[k].length < 60 && apellidosRe.matches(l[k])) {
                    autores += l[k].split(",").map { it.trim() }
                    usada[k] = true
                    break
                }
            }
        }

        // Título: el grupo de líneas consecutivas sin clasificar con más texto
        var mejor = emptyList<Int>()
        var mejorLen = 0
        var k = 0
        while (k < n) {
            if (usada[k]) { k++; continue }
            val idx = mutableListOf<Int>()
            var q = k
            while (q < n && !usada[q]) { idx += q; q++ }
            val len = idx.sumOf { l[it].length }
            if (len > mejorLen) { mejorLen = len; mejor = idx }
            k = q
        }
        val titulo = mejor.joinToString(" ") { l[it] }.trim()
            .trim('“', '”', '"', '\'', '«', '»', ' ')
        mejor.forEach { usada[it] = true }

        val otros = l.filterIndexed { idx, _ -> !usada[idx] }

        return Documento(
            categoria = adivinarCategoria(l.joinToString(" "), n, tipoTrabajo.isNotEmpty() || autores.isNotEmpty()),
            tipoTrabajo = tipoTrabajo,
            titulo = titulo,
            autores = autores,
            tutores = tutores,
            institucion = institucion,
            facultadArea = unidades.joinToString(" | "),
            fechaLugar = fechaLugar,
            otros = otros,
            textoCompleto = l.joinToString("\n")
        )
    }

    private fun adivinarCategoria(texto: String, filas: Int, pareceTesis: Boolean): String {
        if (pareceTesis) return "Tesis/Seminario"
        if (Regex("""\b(cd|dvd)\b|cd-?rom|disco""", IC).containsMatchIn(texto)) return "Disco/CD"
        return if (filas <= 4) "Libro" else "Otro"
    }
}
