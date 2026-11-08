package br.com.bragasaude.domain

import org.junit.Assert.*
import org.junit.Test

class HealthReadingInputTest {
    @Test fun manualValuesRespectUnitsAndDecimalComma() {
        assertEquals(98.5, HealthReadingInput.value(HealthReadingInput.OXYGEN, "98,5")!!, 0.001)
        assertEquals(98.5, HealthReadingInput.value(HealthReadingInput.OXYGEN, "98.5")!!, 0.001)
        assertEquals(98.25, HealthReadingInput.value(HealthReadingInput.OXYGEN, "98,25")!!, 0.001)
        assertEquals(72.0, HealthReadingInput.value(HealthReadingInput.HEART, "72")!!, 0.001)
        assertEquals(20.0, HealthReadingInput.value(HealthReadingInput.HEART, "20")!!, 0.001)
        assertEquals(250.0, HealthReadingInput.value(HealthReadingInput.HEART, "250")!!, 0.001)
        assertEquals(30.0, HealthReadingInput.value(HealthReadingInput.OXYGEN, "30")!!, 0.001)
        assertEquals(100.0, HealthReadingInput.value(HealthReadingInput.OXYGEN, "100")!!, 0.001)
        listOf("0", "-1", "29,9", "98,125", "101", "NaN", "Infinity").forEach {
            assertNull(it, HealthReadingInput.value(HealthReadingInput.OXYGEN, it))
        }
        listOf("0", "19", "251", "300", "72,5", "NaN").forEach {
            assertNull(it, HealthReadingInput.value(HealthReadingInput.HEART, it))
        }
        assertEquals("98,0", HealthReadingInput.formatOxygenDisplay(98.0))
        assertEquals("98,5", HealthReadingInput.formatOxygenDisplay(98.5))
        assertEquals("98,25", HealthReadingInput.formatOxygenDisplay(98.25))
        assertEquals("98", HealthReadingInput.formatOxygenCompact(98.0))
        assertEquals("98,5", HealthReadingInput.formatOxygenCompact(98.5))
    }
    @Test fun voiceUnderstandsBothIndicatorsWithoutWatch() {
        assertEquals(HealthReadingDraft(HealthReadingInput.HEART, 72.0), HealthReadingInput.spoken("Minha frequência cardíaca deu setenta e dois"))
        assertEquals(HealthReadingDraft(HealthReadingInput.OXYGEN, 98.0), HealthReadingInput.spoken("Minha oxigenação deu noventa e oito"))
        assertEquals(HealthReadingDraft(HealthReadingInput.OXYGEN, 98.5), HealthReadingInput.spoken("SpO2 98,5"))
        assertEquals(HealthReadingDraft(HealthReadingInput.HEART, 72.0), HealthReadingInput.spoken("72", HealthReadingInput.HEART))
    }
    @Test fun ambiguousOrUnrelatedSpeechIsNotTurnedIntoARecord() {
        listOf("batimentos 72 e oxigenação 98", "batimentos 72 às 14 horas", "qual saturação é normal 98", "oxigenação -2", "saturação 101", "meu pai está com saturação 98", "registre 98").forEach { assertNull(it, HealthReadingInput.spoken(it)) }
        assertNull(HealthReadingInput.spoken("saturação 98", HealthReadingInput.HEART))
    }
}
