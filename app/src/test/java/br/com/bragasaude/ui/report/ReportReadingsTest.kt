package br.com.bragasaude.ui.report

import br.com.bragasaude.data.remote.model.RemoteVitalSign
import org.junit.Assert.*
import org.junit.Test

class ReportReadingsTest {
    @Test fun bmiUsesWeightAndHeightAndReferencesHaveExplicitBoundaries() {
        assertEquals(25.0, reportBmi(100f, 200f)!!, 0.0001)
        assertNull(reportBmi(70f, 0f))
        assertNull(reportBmi(Float.NaN, 170f))
        assertEquals("Baixo peso", adultBmiReference(18.4))
        assertEquals("Peso adequado", adultBmiReference(18.5))
        assertEquals("Sobrepeso", adultBmiReference(25.0))
        assertEquals("Obesidade", adultBmiReference(30.0))
        assertEquals("Dentro da referência geral", pressureReference(119, 79))
        assertEquals("Faixa de atenção", pressureReference(120, 80))
        assertEquals("Acima da referência geral", pressureReference(140, 80))
        assertEquals("Conferir leitura baixa", pressureReference(85, 50))
        assertNull(reportAge(null, "2026-10-23T10:00:00Z"))
        assertEquals(36, reportAge("1990-01-01", "2026-10-23T10:00:00Z"))
    }
    @Test fun latestMeasurementIsChosenIndependentlyForEachSignal() {
        val pressure = RemoteVitalSign(userId = "u", systolicPressure = 120, diastolicPressure = 80,
            heartRate = 65, measuredAt = "2026-10-20T10:00:00Z")
        val newerWater = RemoteVitalSign(userId = "u", hydrationMl = 250, measuredAt = "2026-10-22T10:00:00Z")
        val older = pressure.copy(systolicPressure = 110, glucoseLevel = 95, measuredAt = "2026-10-19T10:00:00Z")
        val readings = latestReportReadings(listOf(newerWater, older, pressure)).associateBy { it.label }
        assertEquals("120/80 mmHg", readings.getValue("Pressão arterial").value)
        assertEquals(pressure.measuredAt, readings.getValue("Pressão arterial").measuredAt)
        assertEquals("95 mg/dL", readings.getValue("Glicemia").value)
        assertEquals("65 bpm", readings.getValue("Batimentos").value)
        assertEquals("250 ml", readings.getValue("Hidratação (último registro)").value)
    }
    @Test fun emptyOrPartialRecordsDoNotInventMeasurementsOrPairDifferentPressures() {
        val readings = latestReportReadings(listOf(
            RemoteVitalSign(userId = "u", systolicPressure = 120),
            RemoteVitalSign(userId = "u", diastolicPressure = 80)))
        assertTrue(readings.all { it.value == "Sem registro" && it.measuredAt == null })
        assertTrue(latestReportReadings(emptyList()).all { it.value == "Sem registro" })
    }
}
