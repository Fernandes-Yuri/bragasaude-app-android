package br.com.bragasaude.domain

import org.junit.Assert.*
import org.junit.Test

class HealthReadingInputTest {
    @Test fun manualValuesRespectUnitsAndDecimalComma() {
        assertEquals(98.5, HealthReadingInput.value(HealthReadingInput.OXYGEN, "98,5")!!, 0.001)
        assertEquals(72.0, HealthReadingInput.value(HealthReadingInput.HEART, "72")!!, 0.001)
        listOf("0", "-1", "101", "NaN", "Infinity").forEach { assertNull(HealthReadingInput.value(HealthReadingInput.OXYGEN, it)) }
        assertNull(HealthReadingInput.value(HealthReadingInput.HEART, "72,5"))
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
