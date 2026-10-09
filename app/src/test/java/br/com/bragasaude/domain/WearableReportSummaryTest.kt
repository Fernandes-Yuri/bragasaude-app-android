package br.com.bragasaude.domain

import br.com.bragasaude.data.local.WearableReading
import br.com.bragasaude.data.util.localDayStart
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class WearableReportSummaryTest {
    private val zone = ZoneId.of("America/Sao_Paulo")
    private fun reading(value: Double, source: String = "watch.app", metric: String = "HEART_RATE") =
        WearableReading("user", "$source-$value-$metric", metric, value, Instant.parse("2026-09-09T01:00:00Z").toEpochMilli(), source, "Watch")

    @Test fun `daily summary respects local date and keeps heart and oxygen separate`() {
        val rows = summarizeWearableReadings(listOf(reading(60.0), reading(80.0), reading(98.0, metric = "OXYGEN_SATURATION")), zone)
        assertEquals(2, rows.size)
        val heart = rows.first { it.metric == "HEART_RATE" }
        assertEquals("2026-09-08", heart.date)
        assertEquals(2, heart.count)
        assertEquals(60.0, heart.minimum, 0.01)
        assertEquals(70.0, heart.average, 0.01)
        assertEquals(80.0, heart.maximum, 0.01)
    }
    @Test fun `different sources are not averaged together`() {
        assertEquals(2, summarizeWearableReadings(listOf(reading(60.0), reading(80.0, "other.app")), zone).size)
    }
    @Test fun `no records produces no invented values`() {
        assertTrue(summarizeWearableReadings(emptyList(), zone).isEmpty())
    }
    @Test fun `step day starts at local midnight instead of UTC`() {
        assertEquals(Instant.parse("2026-09-08T03:00:00Z"), localDayStart(Instant.parse("2026-09-09T01:00:00Z"), zone))
    }
    @Test fun `local midnight uses the zone offset on daylight saving transitions`() {
        assertEquals(Instant.parse("2026-03-08T05:00:00Z"), localDayStart(Instant.parse("2026-03-08T19:00:00Z"), ZoneId.of("America/New_York")))
    }
}
