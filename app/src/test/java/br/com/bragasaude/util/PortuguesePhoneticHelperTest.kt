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
}
