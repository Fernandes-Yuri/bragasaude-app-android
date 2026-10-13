package br.com.bragasaude.domain

import br.com.bragasaude.data.local.VitalSignEntity
import br.com.bragasaude.data.local.WearableReading
import java.util.Date
import org.junit.Assert.*
import org.junit.Test

class HealthReadingHistoryTest {
    @Test fun whatsappReadingsAppearWithoutWearableAndKeepTimeAndOrigin() {
        val vital = VitalSignEntity(localId = 1, remoteId = "remote", userId = "me", heartRate = 99,
            oxygenSaturation = 98, measuredAt = Date(2000), confirmedVia = "whatsapp")
        for ((metric, value) in listOf(HealthReadingInput.HEART to 99.0, HealthReadingInput.OXYGEN to 98.0)) {
            val reading = HealthReadingHistory.merge("me", metric, emptyList(), listOf(vital)).single()
            assertEquals(value, reading.value, 0.0)
            assertEquals(2000L, reading.measuredAt)
            assertEquals(HealthReadingHistory.WHATSAPP, reading.sourcePackage)
        }
    }

    @Test fun bothSourcesAreOrderedAndOtherAccountIsExcluded() {
        val wearable = WearableReading("me", "device", HealthReadingInput.HEART, 70.0, 3000, "watch", null)
        val mine = VitalSignEntity(localId = 1, userId = "me", heartRate = 99, measuredAt = Date(2000))
        val other = mine.copy(userId = "other", localId = 2, measuredAt = Date(9000))
        val readings = HealthReadingHistory.merge("me", HealthReadingInput.HEART, listOf(wearable), listOf(mine, other))
        assertEquals(listOf(70.0, 99.0), readings.map { it.value })
        assertEquals("watch", readings.first().sourcePackage)
        assertTrue(HealthReadingHistory.merge("", HealthReadingInput.HEART, listOf(wearable), listOf(mine)).isEmpty())
    }

    @Test fun missingMetricAndInvalidValuesDoNotInventReadings() {
        val vital = VitalSignEntity(localId = 1, userId = "me", oxygenSaturation = 198)
        assertTrue(HealthReadingHistory.merge("me", HealthReadingInput.HEART, emptyList(), listOf(vital)).isEmpty())
        assertTrue(HealthReadingHistory.merge("me", HealthReadingInput.OXYGEN, emptyList(), listOf(vital)).isEmpty())
    }
}
