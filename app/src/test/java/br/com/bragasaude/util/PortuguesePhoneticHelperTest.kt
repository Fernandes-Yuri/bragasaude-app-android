package br.com.bragasaude.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PortuguesePhoneticHelperTest {

    @Test
    fun `normalizeDatesAndNumbersForSpeech should convert date to natural words`() {
        val dateText = "Sua consulta foi em 30/09/2026."
        val normalized = PortuguesePhoneticHelper.normalizeDatesAndNumbersForSpeech(dateText)
        assertEquals("Sua consulta foi no dia trinta, do nove de dois mil e vinte e seis.", normalized)
    }

    @Test
    fun `cleanTextForTts should convert dates, times and numbers into natural spoken Portuguese`() {
        val input = "Sua última pressão foi 120 por 80, em 30/09/2026 às 14:30."
        val output = PortuguesePhoneticHelper.cleanTextForTts(input)
        assertEquals("Sua última pressão foi cento e vinte por oitenta, no dia trinta, do nove de dois mil e vinte e seis às quatorze e trinta.", output)
    }

    @Test
    fun `cleanTextForTts should handle hydration percentages and units`() {
        val input = "Você bebeu 500 ml hoje, que é 25% da meta."
        val output = PortuguesePhoneticHelper.cleanTextForTts(input)
        assertEquals("Você bebeu quinhentos ml hoje, que é vinte e cinco por cento da meta.", output)
    }

    @Test
    fun `cleanTextForTts preserva digitos repetidos e reduz apenas letras repetidas`() {
        val agua = PortuguesePhoneticHelper.cleanTextForTts("A referência é de 2000 ml.")
        assertTrue(agua, agua.contains("dois mil"))
        assertFalse(agua, agua.contains("vinte"))

        val glicemia = PortuguesePhoneticHelper.cleanTextForTts("Sua glicemia foi 111 mg/dL.")
        assertFalse(glicemia, glicemia.contains(" um mg"))
        assertTrue(glicemia, glicemia.contains("onze"))

        assertEquals("oi, tudo bem?", PortuguesePhoneticHelper.cleanTextForTts("oiiii, tudo bem?"))
    }
}
