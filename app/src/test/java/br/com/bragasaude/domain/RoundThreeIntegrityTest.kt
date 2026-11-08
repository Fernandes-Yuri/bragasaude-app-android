package br.com.bragasaude.domain

import br.com.bragasaude.data.remote.model.RemoteProfile
import br.com.bragasaude.data.remote.model.RemoteVitalSign
import br.com.bragasaude.data.util.HealthFormatter
import br.com.bragasaude.ui.components.InputMask
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import org.junit.Assert.*
import org.junit.Test
import java.time.*

class RoundThreeIntegrityTest {
    @Test fun birthDateUsesARealCalendarAndRejectsFutureBirths() {
        assertFalse(HealthFormatter.isValidDate("31/02/2024"))
        assertFalse(HealthFormatter.isValidDate("31/04/2000"))
        assertFalse(HealthFormatter.isValidDate("29/02/2023"))
        assertTrue(HealthFormatter.isValidDate("29/02/2024"))
        assertTrue(HealthFormatter.isValidDate("2024-02-29"))
        assertFalse(HealthFormatter.isValidDate(LocalDate.now().plusDays(1).toString()))
    }

    @Test fun exportedProfileUsesCentimetersAndBrazilianPresentation() {
        val text = ReportPatientInfo.line(RemoteProfile(id = "fixture", height = 175.0, weight = 75.5, birthDate = "1988-12-12"))
        assertTrue(text.contains("175 cm"))
        assertTrue(text.contains("75,5 kg"))
        assertTrue(text.contains("12/12/1988"))
        assertFalse(text.contains("175.0m"))
        assertFalse(ReportPatientInfo.line(RemoteProfile(id = "fixture")).contains("null"))
    }

    @Test fun mixedVitalsNeverBorrowAnotherReadingsTimeOrAnotherAccountsValue() {
        val rows = listOf(
            RemoteVitalSign(userId = "patient", hydrationMl = 500, measuredAt = "2026-11-08T09:00:00-0300"),
            RemoteVitalSign(userId = "patient", glucoseLevel = 114, measuredAt = "2026-11-08T07:00:00Z"),
            RemoteVitalSign(userId = "other", systolicPressure = 200, diastolicPressure = 110, measuredAt = "2026-11-08T08:00:00Z"),
            RemoteVitalSign(userId = "patient", systolicPressure = 120, diastolicPressure = 80, measuredAt = "2026-11-07T07:00:00Z"))
        val result = HomeReadingSummary.from("patient", rows)
        assertTrue(result.loaded)
        assertEquals(120, result.pressure?.systolicPressure)
        assertEquals("2026-11-07T07:00:00Z", result.pressure?.measuredAt)
        assertEquals("2026-11-08T07:00:00Z", result.glucose?.measuredAt)
        assertNull(HomeReadingSummary.from("new-account", rows).pressure)
        assertFalse(HomeReadingSummary().loaded)
    }

    @Test fun aSevenDayWindowIncludesAllSamplesAndSkipsOnlyInvalidOrOldDates() {
        val zone = ZoneId.of("America/Sao_Paulo")
        val stamps = listOf("2026-11-01T23:00:00-0300", "2026-11-02T00:00:00-03:00", "invalid", "2026-11-08T07:00:00Z", "2026-11-08T09:00:00-0300")
        assertEquals(listOf(1, 3, 4), ChartTimeline.indices(List(5) { 120.0 }, stamps, 7, LocalDate.of(2026,11,8), zone))
        assertEquals(Instant.parse("2026-11-08T12:00:00Z"), ChartTimeline.instant(stamps[4]))
        assertEquals(Instant.parse("2026-11-08T03:00:00Z"), ChartTimeline.instant("2026-11-08", zone))
    }

    @Test fun missingDaysRemainGapsAndSpacingReflectsElapsedTime() {
        val zone = ZoneId.of("America/Sao_Paulo")
        assertEquals(listOf(1), ChartTimeline.indices(listOf(Double.NaN, 100.0), listOf("2026-11-07", "2026-11-08"), 7, LocalDate.of(2026,11,8), zone))
        val x = ChartTimeline.fractions(listOf("2026-11-01T00:00:00Z", "2026-11-02T00:00:00Z", "2026-11-08T00:00:00Z"))
        assertEquals(1f/7f, x[1], .0001f)
    }

    @Test fun maskPreservesSelectionWhenPastingAndEditingAtSeparators() {
        val pasted = InputMask.DATE.edit(TextFieldValue("08/11/2026", TextRange(10)))
        assertEquals("08112026", pasted.text)
        assertEquals(TextRange(8), pasted.selection)
        val middle = InputMask.TIME.edit(TextFieldValue("0914", TextRange(2)))
        assertEquals("09:14", InputMask.TIME.display(middle.text))
        assertEquals(TextRange(2), middle.selection)
        val list = InputMask.TIME_LIST.edit(TextFieldValue("08:00, 20:00", TextRange(12)))
        assertEquals("0800,2000", list.text)
        assertEquals(TextRange(9), list.selection)
        val mapping = InputMask.TIME_LIST.transformation().filter(androidx.compose.ui.text.AnnotatedString(list.text)).offsetMapping
        for (offset in 0..list.text.length) assertEquals(offset, mapping.transformedToOriginal(mapping.originalToTransformed(offset)))
    }
    @Test fun aPartialPressureRecordDoesNotShiftTheDiastolicSeriesInTheExport() {
        val now = java.time.OffsetDateTime.now().toString()
        val report = ClinicalReportAggregator.aggregate30DayCycle(RemoteProfile(id = "patient"), listOf(
            RemoteVitalSign(userId = "patient", systolicPressure = 120, measuredAt = now),
            RemoteVitalSign(userId = "patient", systolicPressure = 130, diastolicPressure = 80, measuredAt = now)
        ), emptyList())
        assertEquals(2, report.pressureStats.systolicSeries.size)
        assertEquals(2, report.pressureStats.diastolicSeries.size)
        assertTrue(report.pressureStats.diastolicSeries.first().isNaN())
        assertEquals(80.0, report.pressureStats.diastolicSeries.last(), 0.0)
    }

}
