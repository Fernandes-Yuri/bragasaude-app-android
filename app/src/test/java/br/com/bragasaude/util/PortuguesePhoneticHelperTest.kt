package br.com.bragasaude.util

import org.junit.Assert.assertEquals
import org.junit.Test

class PortuguesePhoneticHelperTest {

    @Test
    fun `extractFirstName should return only the first name`() {
        assertEquals("Welisa", PortuguesePhoneticHelper.extractFirstName("Welisa Ferreira da Silva"))
        assertEquals("Yuri", PortuguesePhoneticHelper.extractFirstName("Yuri Fernandes"))
        assertEquals("Maria", PortuguesePhoneticHelper.extractFirstName("Maria"))
        assertEquals("", PortuguesePhoneticHelper.extractFirstName(""))
        assertEquals("", PortuguesePhoneticHelper.extractFirstName(null))
    }

    @Test
    fun `toTtsFriendlyName should correctly normalize Welisa to Uelisa with accent`() {
        val result = PortuguesePhoneticHelper.toTtsFriendlyName("Welisa")
        assertEquals("Uélisa", result)

        val resultFromFull = PortuguesePhoneticHelper.toTtsFriendlyName("Welisa Ferreira")
        assertEquals("Uélisa", resultFromFull)
    }

    @Test
    fun `toTtsFriendlyName should normalize other Brazilian W names`() {
        assertEquals("Uéslei", PortuguesePhoneticHelper.toTtsFriendlyName("Wesley"))
        assertEquals("Uíliam", PortuguesePhoneticHelper.toTtsFriendlyName("William"))
        assertEquals("Uélinton", PortuguesePhoneticHelper.toTtsFriendlyName("Wellington"))
        assertEquals("Vágner", PortuguesePhoneticHelper.toTtsFriendlyName("Wagner"))
    }

    @Test
    fun `toTtsFriendlyName should normalize Y and digraph names`() {
        assertEquals("Iúri", PortuguesePhoneticHelper.toTtsFriendlyName("Yuri"))
        assertEquals("Iasmin", PortuguesePhoneticHelper.toTtsFriendlyName("Yasmin"))
        assertEquals("Tiago", PortuguesePhoneticHelper.toTtsFriendlyName("Thiago"))
        assertEquals("Rafael", PortuguesePhoneticHelper.toTtsFriendlyName("Raphael"))
        assertEquals("Kéli", PortuguesePhoneticHelper.toTtsFriendlyName("Kelly"))
    }

    @Test
    fun `toTtsFriendlyName should preserve regular Portuguese names`() {
        assertEquals("Carlos", PortuguesePhoneticHelper.toTtsFriendlyName("Carlos"))
        assertEquals("Ana", PortuguesePhoneticHelper.toTtsFriendlyName("Ana"))
        assertEquals("Sebastião", PortuguesePhoneticHelper.toTtsFriendlyName("Sebastião"))
    }

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
}
