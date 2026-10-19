package com.example.portadas

import kotlin.math.abs

/**
 * Convierte las filas de texto del OCR en campos. Es una heurística: las portadas no son
 * iguales, así que lo que no se reconoce va a "otros" y todo se puede corregir en la revisión.
 * Para ajustar el reconocimiento, edita las expresiones regulares de este archivo.
 *
 * Los campos que no aparecen en la portada quedan vacíos (se exportan como null).
 */
object PortadaParser {
    private val IC = RegexOption.IGNORE_CASE

    /**
     * Tras una etiqueta de autor ("Autores:", "Elaborado por:"…) acepta también líneas que parecen
     * un nombre aunque no empiecen con "Br."/"Lic."… Ponlo en false si empieza a absorber títulos.
     */
    private const val NOMBRES_SIN_PREFIJO = true

    private const val ROLES_AUTOR =
        "autor|alumn|estudiante|sustentante|integrante|bachiller|egresad[oa]|maestrante|investigador|" +
            "presentad[oa] por|elaborad[oa] por|realizad[oa] por"
    private const val ROLES_TUTOR =
        "tutor|asesor|director|revisor|supervisor|jurado|docente gu[ií]a|profesor gu[ií]a|coordinador|" +
            "docente|profesor|catedr[aá]tic[oa]"
    private const val MES =
        "enero|febrero|marzo|abril|mayo|junio|julio|agosto|se[p]?tiembre|octubre|noviembre|diciembre"
    private const val COMILLAS = "“”\"'«»‟„‘’ "

    private val prefijoTitulo =
        Regex("""^(br|bra|bach|msc|m\.sc|dr|dra|lic|licda|ing|mtro|mtra|phd|prof|profa)\b\.?\s""", IC)

    private val rol = Regex(
        """^\s*((?:co)?(?:$ROLES_AUTOR|$ROLES_TUTOR|presentan?(?=\s*:|\s*$))""" +
            """(?:a|as|es|o|os|s)?(?:\s*/\s*(?:es|as|a))?""" +
            """(?:\s+(?:cient[ií]fic[oa]|metodol[oó]gic[oa]|principal|acad[eé]mic[oa]))?)""" +
            """(?=[\s:]|$)\s*:?\s*(.*)$""", IC
    )
    private val rolAutor = Regex(
        "autor|alumn|estudiante|sustentante|integrante|bachiller|egresad|maestrante|investigador|" +
            "present|elaborad|realizad", IC
    )

    // Facultad / área / centro (siempre) y unidades "débiles" (solo cerca del encabezado y con la forma "Escuela de …")
    private val unidadFuerte =
        Regex("""^(facultad|[aá]rea de conocimiento|centro universitario)\b""", IC)
    private val unidadDebil = Regex(
        """^(?:(?:departamento|escuela|instituto|carrera)\s+(?:de|del)\b|(?:maestr[ií]a|doctorado)\s+en\b)""", IC
    )

    private val tipoTrabajoRe = Regex(
        """^[^\p{L}]*(inscripci[oó]n de tema|seminario de graduaci[oó]n|seminario para optar|""" +
            """seminario de (?:investigaci[oó]n|tesis)|para optar|tesis|monograf[ií]a|trabajo monogr[aá]fico|""" +
            """trabajo de (?:graduaci[oó]n|curso|diploma|investigaci[oó]n)|informe final|""" +
            """informe de (?:pr[aá]ctica|pasant[ií]a|investigaci[oó]n)|protocolo de)\b""", IC
    )

    private val fechaRe = Regex(
        """\b(?:(?:$MES)\b|(?:ene|feb|mar|abr|jun|jul|ago|sept?|set|oct|nov|dic)(?:\.|\b)).{0,14}?\b(?:19|20)\d{2}\b""", IC
    )
    private val anioRe = Regex("""\b(?:19|20)\d{2}\b""")
    private val fechaNumRe = Regex("""\b\d{1,2}[/.\-]\d{1,2}[/.\-](?:\d{2}|\d{4})\b""")

    private val lemaRe = Regex("""universidad del pueblo""", IC)
    private val inicioInstRe = Regex("""^[^\p{L}]*universidad\b""", IC)
    private val siglasRe = Regex("""\b(UNAN|UNICA|UPOLI|UAM|UCC|UCA|UNI|UNICIT|BICU|URACCAN)\b""") // solo mayúsculas
    private val prepFinal = Regex("""\b(de|del|en|al|y|la|el|los|las)$""", IC)

    private val apellidosRe = Regex("""^\p{Lu}\p{Ll}+(\s*,\s*\p{Lu}\p{Ll}+){1,5}$""")
    private val prefijoBr = Regex("""^(br|bra)\.?\s""", IC)
    private val nombreRe =
        Regex("""^\p{Lu}[\p{L}'’.\-]*(?:\s+(?:de|del|la|las|los|y|van|von|da|di|\p{Lu}[\p{L}'’.\-]*)){1,5}$""")
    private val noNombre = Regex(
        "ingenier|licenciad|doctor|maestr|grado|t[ií]tulo|facultad|universidad|seminario|tesis|monograf|" +
            "departamento|escuela|instituto|carrera|nicaragua|managua|carazo|jinotepe|centro|[aá]rea|" +
            "conocimiento|propuesta|informe|proyecto", IC
    )
    private val discoRe = Regex("""\b(?:cd|dvd|cd-?rom|discos?)\b""", IC)

    // Hora del teléfono u otro ruido de pantalla (cuando se escanea un PDF mostrado en el móvil)
    private val ruidoRe = Regex("""\b\d{1,2}:\d{2}\b""")
    // "Carrera:" solo en una línea (el valor va en la siguiente) o "Asignatura: Cálculo" en la misma línea
    private val etiquetaSoloRe = Regex("""^\s*(\p{L}[\p{L} ]{1,28}?)\s*:\s*$""", IC)
    private val etiquetaExtraRe = Regex(
        """^\s*(carrera|asignatura|curso|materia|componente|grupo|modalidad|turno|ciclo|semestre|secci[oó]n|""" +
            """per[ií]odo|m[oó]dulo|c[oó]digo|edici[oó]n|volumen|editorial|isbn|serie)\s*:\s*(\S.*)$""", IC
    )
    private val tituloEtiquetaRe = Regex("""^(?:t[ií]tulo|tema|nombre del (?:proyecto|trabajo))$""", IC)
    private val tituloInlineRe = Regex("""^\s*(?:t[ií]tulo|tema)\s*:\s*(\S.*)$""", IC)
    // "Departamento…", "Escuela…", "Instituto…" justo después de otra línea de unidad (sin "de")
    private val unidadCadena = Regex("""^(?:departamento|escuela|instituto)\b""", IC)
    // "Docente"/"Profesor" solo cuentan como etiqueta si llevan dos puntos ("Docente: Fulano")
    private val soloConDosPuntos = Regex("""^(?:docente|profesor|catedr[aá]tic)\w*$""", IC)

    /** Quita la coma/punto final que deja el OCR en listas de nombres ("…Avellan," / "…Cruz."). */
    private fun limpiarNombre(s: String): String {
        var t = s.trim().trimEnd(',', ';').trim()
        if (t.endsWith('.')) {
            val sin = t.dropLast(1)
            if (sin.substringAfterLast(' ').length > 1) t = sin // conserva iniciales como "J."
        }
        return t
    }

    /** Devuelve el match de una línea de etiqueta ("Autor: …", "Tutor", …) solo si de verdad parece una etiqueta. */
    private fun rolDe(linea: String): MatchResult? {
        val m = rol.find(linea) ?: return null
        val resto = m.groupValues[2].trim()
        val inicioResto = m.groups[2]?.range?.first ?: linea.length
        val conDosPuntos = linea.substring(0, inicioResto).contains(':')
        if (soloConDosPuntos.matches(m.groupValues[1].trim()) && !conDosPuntos) return null
        val valido = resto.isEmpty() || conDosPuntos ||
            prefijoTitulo.containsMatchIn("$resto ") || nombreRe.matches(limpiarNombre(resto))
        return if (valido) m else null
    }

    private fun esUnidad(linea: String, idx: Int, finEncabezado: Int, marca: BooleanArray): Boolean {
        if (unidadFuerte.containsMatchIn(linea)) return true
        if (unidadDebil.containsMatchIn(linea) && idx <= finEncabezado + 4 && linea.length <= 70) return true
        return idx > 0 && marca[idx - 1] && unidadCadena.containsMatchIn(linea) &&
            !linea.contains(':') && linea.length <= 70
    }

    private fun aceptaNombre(linea: String, previos: List<String>, esAutor: Boolean): Boolean {
        if (prefijoTitulo.containsMatchIn(linea)) return true
        if (previos.isEmpty()) {
            return linea.length <= 60 && linea.none { it.isDigit() } &&
                !tipoTrabajoRe.containsMatchIn(linea) && !fechaRe.containsMatchIn(linea)
        }
        return NOMBRES_SIN_PREFIJO && esAutor && previos.size < 8 && linea.length <= 60 &&
            nombreRe.matches(limpiarNombre(linea)) && !noNombre.containsMatchIn(linea)
    }

    /** Une líneas; si una termina en guion y la siguiente sigue la palabra ("produc-" + "tivo") las pega. */
    private fun unirLineas(lineas: List<String>): String {
        val sb = StringBuilder()
        for (s in lineas) {
            if (sb.isEmpty()) { sb.append(s); continue }
            val largo = sb.length
            if (sb[largo - 1] == '-' && largo >= 2 && sb[largo - 2].isLetter() && s.isNotEmpty()) {
                if (s[0].isLowerCase()) sb.setLength(largo - 1)
                sb.append(s)
            } else {
                sb.append(' ').append(s)
            }
        }
        return sb.toString()
    }

    fun parse(filas: List<String>): Documento {
        val l = filas.map { it.trim() }.filter { it.isNotEmpty() }
        val n = l.size
        val usada = BooleanArray(n)

        // Lema de la universidad y ruido de pantalla (hora del teléfono): se descartan (siguen en texto_completo)
        val marca = BooleanArray(n) // líneas ya reconocidas como unidad
        for (i in 0 until n) {
            if (lemaRe.containsMatchIn(l[i]) || (ruidoRe.containsMatchIn(l[i]) && l[i].length <= 25)) usada[i] = true
        }

        // Institución: bloque que empieza en "Universidad…" y termina en una sigla conocida (UNAN, UCA, UNI…).
        // Sin sigla: la línea sola (más la siguiente si la frase queda cortada en "de", "y"…).
        val instituciones = mutableListOf<String>()
        var finEncabezado = -1
        var i = 0
        while (i < n) {
            if (!usada[i] && inicioInstRe.containsMatchIn(l[i])) {
                var fin = -1
                for (j in i..minOf(n - 1, i + 7)) {
                    if (usada[j]) break
                    if (siglasRe.containsMatchIn(l[j])) { fin = j; break }
                }
                if (fin < 0) {
                    fin = i
                    while (fin + 1 < n && fin - i < 3 && !usada[fin + 1] && prepFinal.containsMatchIn(l[fin])) fin++
                }
                instituciones += l.subList(i, fin + 1).joinToString(" ")
                for (k in i..fin) usada[k] = true
                if (finEncabezado < 0) finEncabezado = fin
                i = fin + 1
                continue
            }
            i++
        }
        val institucion = instituciones.firstOrNull().orEmpty()

        // Facultad / área / centro
        val unidades = mutableListOf<String>()
        for (k in 0 until n) {
            if (!usada[k] && esUnidad(l[k], k, finEncabezado, marca)) {
                usada[k] = true
                marca[k] = true
                var txt = l[k]
                if (txt.length <= 24 && k + 1 < n && !usada[k + 1] && l[k + 1].length <= 60 &&
                    !esUnidad(l[k + 1], k + 1, finEncabezado, marca) && rolDe(l[k + 1]) == null
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
            val m = if (!usada[p]) rolDe(l[p]) else null
            if (m == null) { p++; continue }
            usada[p] = true
            val etiqueta = m.groupValues[1].trim().replaceFirstChar { it.uppercase() }
            val esAutor = rolAutor.containsMatchIn(etiqueta)
            val nombres = mutableListOf<String>()
            if (m.groupValues[2].isNotBlank()) nombres += m.groupValues[2].trim()
            var j = p + 1
            while (j < n && !usada[j] && rolDe(l[j]) == null && !esUnidad(l[j], j, finEncabezado, marca) &&
                aceptaNombre(l[j], nombres, esAutor)
            ) {
                nombres += l[j]
                usada[j] = true
                j++
            }
            if (esAutor) {
                autores += nombres.map { limpiarNombre(it.replace(prefijoBr, "")) }
            } else {
                tutores += nombres.map { "$etiqueta: ${limpiarNombre(it)}" }
            }
            p = j
        }

        // Pares "Etiqueta: valor" (Carrera, Asignatura, Componente…) y título explícito ("Tema:", "Título:")
        val extras = linkedMapOf<String, String>()
        var tituloExplicito = ""
        for (k in 0 until n) {
            if (usada[k]) continue
            val inlineTitulo = tituloInlineRe.find(l[k])
            if (inlineTitulo != null) {
                usada[k] = true
                if (tituloExplicito.isEmpty()) tituloExplicito = inlineTitulo.groupValues[1].trim()
                continue
            }
            val inline = etiquetaExtraRe.find(l[k])
            if (inline != null) {
                usada[k] = true
                extras[inline.groupValues[1].trim().lowercase().replaceFirstChar { it.uppercase() }] =
                    inline.groupValues[2].trim()
                continue
            }
            val solo = etiquetaSoloRe.find(l[k])
            if (solo != null && solo.groupValues[1].trim().split(Regex("\\s+")).size <= 3 &&
                k + 1 < n && !usada[k + 1] && l[k + 1].length <= 100 &&
                rolDe(l[k + 1]) == null && !esUnidad(l[k + 1], k + 1, finEncabezado, marca) &&
                !etiquetaSoloRe.containsMatchIn(l[k + 1])
            ) {
                val etiqueta = solo.groupValues[1].trim()
                usada[k] = true
                usada[k + 1] = true
                if (tituloEtiquetaRe.matches(etiqueta)) {
                    if (tituloExplicito.isEmpty()) tituloExplicito = l[k + 1]
                } else {
                    extras[etiqueta.replaceFirstChar { it.uppercase() }] = l[k + 1]
                }
            }
        }

        // Tipo de trabajo (Seminario de graduación, Inscripción de tema, Tesis para optar al título de…)
        var tipoTrabajo = ""
        for (k in 0 until n) {
            if (!usada[k] && tipoTrabajoRe.containsMatchIn(l[k])) {
                usada[k] = true
                tipoTrabajo = l[k]
                if (prepFinal.containsMatchIn(l[k]) && k + 1 < n && !usada[k + 1] && l[k + 1].length < 50 &&
                    rolDe(l[k + 1]) == null && !esUnidad(l[k + 1], k + 1, finEncabezado, marca)
                ) {
                    tipoTrabajo += " " + l[k + 1]
                    usada[k + 1] = true
                }
                break
            }
        }

        // Fecha / lugar: línea corta con mes y año en la parte final; si no, un año suelto en las últimas 3 líneas
        var fechaLugar = ""
        var fechaIdx = -1
        for (k in n - 1 downTo 0) {
            if (!usada[k] && k >= (n * 0.6).toInt() && l[k].length <= 50 && fechaRe.containsMatchIn(l[k])) {
                usada[k] = true
                fechaIdx = k
                fechaLugar = l[k].trim().trimEnd('.')
                break
            }
        }
        if (fechaLugar.isEmpty()) {
            for (k in n - 1 downTo maxOf(0, n - 3)) {
                val s = l[k]
                if (!usada[k] && s.length <= 50 && s.split(Regex("\\s+")).size <= 6 &&
                    (anioRe.containsMatchIn(s) || fechaNumRe.containsMatchIn(s)) &&
                    rolDe(s) == null && !tipoTrabajoRe.containsMatchIn(s)
                ) {
                    usada[k] = true
                    fechaIdx = k
                    fechaLugar = s.trim().trimEnd('.')
                    break
                }
            }
        }

        // Autores sin etiqueta, tipo "Ramos, Pérez, Palacios" (se descartan líneas pegadas a la fecha o al final: son lugares)
        if (autores.isEmpty()) {
            for (k in 0 until n) {
                if (!usada[k] && k < n - 2 && (fechaIdx < 0 || abs(k - fechaIdx) > 1) &&
                    l[k].length < 60 && apellidosRe.matches(l[k])
                ) {
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
        val tituloAuto = unirLineas(mejor.map { l[it] }).trim { it in COMILLAS }
        val titulo = if (tituloExplicito.isNotEmpty()) tituloExplicito else tituloAuto
        if (tituloExplicito.isEmpty()) mejor.forEach { usada[it] = true }

        val otros = l.filterIndexed { idx, _ -> !usada[idx] }

        val pareceTesis = tipoTrabajo.isNotEmpty() || tutores.isNotEmpty() ||
            (institucion.isNotEmpty() && autores.isNotEmpty())

        return Documento(
            categoria = adivinarCategoria(l.joinToString(" "), n, pareceTesis),
            tipoTrabajo = tipoTrabajo,
            titulo = titulo,
            autores = autores,
            tutores = tutores,
            institucion = institucion,
            facultadArea = unidades.joinToString(" | "),
            fechaLugar = fechaLugar,
            otros = otros,
            camposExtra = extras,
            textoCompleto = l.joinToString("\n")
        )
    }

    private fun adivinarCategoria(texto: String, filas: Int, pareceTesis: Boolean): String {
        if (pareceTesis) return "Tesis/Seminario"
        if (discoRe.containsMatchIn(texto)) return "Disco/CD"
        return if (filas <= 4) "Libro" else "Otro"
    }
}
