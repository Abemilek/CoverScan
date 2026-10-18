package com.example.portadas

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pruebas del parser con el texto (ya agrupado en filas) de portadas reales y de casos límite.
 * Ejecutar: ./gradlew testDebugUnitTest
 *
 * Para agregar un caso que falle en tu bodega: copia el campo "texto_completo" del JSON exportado
 * (una línea por fila), pégalo aquí como listOf(...) y escribe lo que esperabas.
 */
class PortadaParserTest {

    @Test
    fun inscripcionDeTema_unan() {
        val d = PortadaParser.parse(
            listOf(
                "UNIVERSIDAD NACIONAL AUTÓNOMA DE NICARAGUA", "UNAN-MANAGUA", "FACULTAD DE CIENCIAS MÉDICAS",
                "UNIVERSIDAD", "NACIONAL", "AUTÓNOMA DE", "NICARAGUA,", "MANAGUA", "UNAN - MANAGUA",
                "INSCRIPCIÓN DE TEMA",
                "“Resultados Perinatales en adolescentes embarazadas, Hospital Escuela Regional",
                "Santiago de Jinotepe, Enero-Marzo 2018”",
                "Autoras: Br. Gutiérrez Narváez Edmy Shasary", "Br. Hernández Mora Marilia Gabriel",
                "Tutor científico: Dr. Alan José Juárez Vásquez", "Gineco – Obstetra",
                "Tutor metodológico: Dra. Maritza Narváez Flores",
                "Docente Farmacología Clínica", "FAREM Carazo", "Jinotepe, Carazo, diciembre del 2018."
            )
        )
        assertEquals("Tesis/Seminario", d.categoria)
        assertEquals("INSCRIPCIÓN DE TEMA", d.tipoTrabajo)
        assertEquals(
            "Resultados Perinatales en adolescentes embarazadas, Hospital Escuela Regional " +
                "Santiago de Jinotepe, Enero-Marzo 2018",
            d.titulo
        )
        assertEquals(listOf("Gutiérrez Narváez Edmy Shasary", "Hernández Mora Marilia Gabriel"), d.autores)
        assertEquals(
            listOf(
                "Tutor científico: Dr. Alan José Juárez Vásquez",
                "Tutor metodológico: Dra. Maritza Narváez Flores"
            ),
            d.tutores
        )
        assertEquals("UNIVERSIDAD NACIONAL AUTÓNOMA DE NICARAGUA UNAN-MANAGUA", d.institucion)
        assertEquals("FACULTAD DE CIENCIAS MÉDICAS", d.facultadArea)
        assertEquals("Jinotepe, Carazo, diciembre del 2018", d.fechaLugar)
    }

    @Test
    fun seminario_autoresSinEtiqueta() {
        val d = PortadaParser.parse(
            listOf(
                "UNIVERSIDAD", "NACIONAL", "AUTÓNOMA DE", "NICARAGUA,", "MANAGUA", "UNAN-MANAGUA",
                "SEMINARIO DE GRADUACIÓN",
                "Propuesta de mejora al proceso productivo de bobinas",
                "plásticas estilo Silera 24\" x 0.500\", en el área de extrusión",
                "de la empresa plásticos Flexibles de Nicaragua S.A ubicada",
                "en el km 45.5 carretera Jinotepe-San Marcos, durante el",
                "segundo semestre del año 2024.",
                "Ramos, Pérez, Palacios", "Tutor", "Msc. Francisco José Hernández García",
                "AREA DE CONOCIMIENTO CIENCIAS BÁSICA", "TECNOLOGÍA",
                "!universidad del Pueblo y para el pueblo!"
            )
        )
        assertEquals("SEMINARIO DE GRADUACIÓN", d.tipoTrabajo)
        assertEquals(listOf("Ramos", "Pérez", "Palacios"), d.autores)
        assertEquals(listOf("Tutor: Msc. Francisco José Hernández García"), d.tutores)
        assertTrue(d.titulo.startsWith("Propuesta de mejora al proceso productivo de bobinas"))
        assertTrue(d.titulo.endsWith("segundo semestre del año 2024."))
        assertEquals("", d.fechaLugar) // esta portada no trae fecha → null al exportar
    }

    @Test
    fun seminarioParaOptar_variosAutores() {
        val d = PortadaParser.parse(
            listOf(
                "UNIVERSIDAD", "NACIONAL", "AUTÓNOMA DE", "NICARAGUA,", "MANAGUA", "UNAN-MANAGUA",
                "Área de Conocimiento", "Ciencias Básicas y Tecnología", "Centro Universitario Regional de Carazo",
                "Propuestas de mejora para incrementar la productividad del proceso",
                "productivo de alimentos caninos en Avícola San Francisco S.A, durante el",
                "segundo semestre del año 2024.",
                "Autor/es:", "Br. Elaine Daniela Aburto Ruiz", "Br. Fernanda José Cano López",
                "Br. Oscar Ottoniel Rosales Sánchez",
                "Asesor/es:", "Msc. Francisco José Hernández García",
                "Seminario para optar al grado de", "Ingeniero Industrial", "Noviembre, 2024",
                "¡Universidad del pueblo y para el pueblo!"
            )
        )
        assertEquals("Seminario para optar al grado de Ingeniero Industrial", d.tipoTrabajo)
        assertEquals(3, d.autores.size)
        assertEquals("Elaine Daniela Aburto Ruiz", d.autores[0])
        assertEquals(
            "Área de Conocimiento Ciencias Básicas y Tecnología | Centro Universitario Regional de Carazo",
            d.facultadArea
        )
        assertEquals("Noviembre, 2024", d.fechaLugar)
    }

    @Test
    fun otraUniversidad_autoresSinPrefijo_anioSinMes() {
        val d = PortadaParser.parse(
            listOf(
                "UNIVERSIDAD CENTROAMERICANA", "UCA", "Facultad de Ciencias Jurídicas", "Tesis de grado",
                "El acceso a la justicia en Nicaragua", "Elaborado por:", "Juan Pérez López", "María Gómez Ruiz",
                "Tutor: Lic. Pedro Ramírez", "Managua, Nicaragua 2019"
            )
        )
        assertEquals("UNIVERSIDAD CENTROAMERICANA UCA", d.institucion)
        assertEquals(listOf("Juan Pérez López", "María Gómez Ruiz"), d.autores)
        assertEquals("El acceso a la justicia en Nicaragua", d.titulo)
        assertEquals("Managua, Nicaragua 2019", d.fechaLugar)
    }

    @Test
    fun libroConAutor_noEsTesis() {
        val d = PortadaParser.parse(listOf("Historia de Nicaragua", "Autor: Sergio Ramírez"))
        assertEquals("Libro", d.categoria)
        assertEquals("Historia de Nicaragua", d.titulo)
        assertEquals(listOf("Sergio Ramírez"), d.autores)
    }

    @Test
    fun lugarSinEtiquetaNoEsAutor() {
        val d = PortadaParser.parse(listOf("Manual de procedimientos", "Jinotepe, Carazo", "Noviembre de 2024"))
        assertTrue(d.autores.isEmpty())
        assertEquals("Noviembre de 2024", d.fechaLugar)
    }

    @Test
    fun tituloQueEmpiezaConEscuelaOEstudiantes_noSeConfundeConEtiqueta() {
        val d = PortadaParser.parse(
            listOf(
                "UNIVERSIDAD NACIONAL AUTÓNOMA DE NICARAGUA", "UNAN-MANAGUA",
                "Escuela y comunidad en el desarrollo local", "de Jinotepe",
                "Estudiantes con discapacidad visual en la UNAN", "Autor: Br. Luis Mora Díaz", "Octubre 2022"
            )
        )
        assertTrue(d.titulo.startsWith("Escuela y comunidad"))
        assertTrue(d.titulo.contains("Estudiantes con discapacidad visual"))
        assertEquals(listOf("Luis Mora Díaz"), d.autores)
    }

    @Test
    fun guionDeFinDeLinea_seUneLaPalabra() {
        val d = PortadaParser.parse(
            listOf(
                "Universidad Nacional de", "Ingeniería", "Facultad de Electrotecnia",
                "Propuesta de mejora al proceso produc-", "tivo de bobinas", "Presentan:", "Ana Torres", "Luis Mora",
                "Jinotepe, Carazo", "Nov. 2024"
            )
        )
        assertEquals("Universidad Nacional de Ingeniería", d.institucion)
        assertEquals("Propuesta de mejora al proceso productivo de bobinas", d.titulo)
        assertEquals(listOf("Ana Torres", "Luis Mora"), d.autores)
        assertEquals("Nov. 2024", d.fechaLugar)
    }
}
