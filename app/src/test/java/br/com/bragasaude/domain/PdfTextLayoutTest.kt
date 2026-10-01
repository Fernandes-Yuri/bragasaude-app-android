package br.com.bragasaude.domain

import org.junit.Assert.*
import org.junit.Test

class PdfTextLayoutTest {
    private val measure: (String) -> Float = { it.codePointCount(0, it.length).toFloat() }

    @Test fun quebraResumoSemUltrapassarMargens() {
        val text = "Este documento reúne exames registrados para organização e compartilhamento com seu médico."
        val lines = PdfTextLayout.wrap(text, 28f, measure)
        assertTrue(lines.size > 1)
        assertTrue(lines.all { measure(it) <= 28f })
        assertEquals(text, lines.joinToString(" "))
    }

    @Test fun quebraIdentificadorLongoSemPerderConteudo() {
        val text = "exame_" + "a".repeat(500)
        val lines = PdfTextLayout.wrap(text, 30f, measure)
        assertTrue(lines.all { measure(it) <= 30f })
        assertEquals(text, lines.joinToString(""))
    }

    @Test fun preservaUnicodeEQuebrasDeParagrafo() {
        val symbol = String(Character.toChars(0x1D11E))
        val text = symbol.repeat(7)
        val lines = PdfTextLayout.wrap(text, 2f, measure)
        assertEquals(text, lines.joinToString(""))
        assertTrue(lines.all { it.length % 2 == 0 })
        assertEquals(listOf("Exame", "", "Data"), PdfTextLayout.wrap("Exame\n\nData", 20f, measure))
    }
}
