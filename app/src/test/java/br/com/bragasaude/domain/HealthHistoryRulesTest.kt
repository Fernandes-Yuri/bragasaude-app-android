package br.com.bragasaude.domain

import br.com.bragasaude.data.local.BiometryEntity
import br.com.bragasaude.data.remote.model.RemoteVitalSign
import java.util.Date
import java.time.Instant
import org.junit.Assert.*
import org.junit.Test

class HealthHistoryRulesTest {
    @Test fun `weight uses full 48 hour window and excludes older and future records`() {
        val now = 1_800_000_000_000L
        fun record(hoursAgo: Int, weight: Float) = BiometryEntity(userId = "u", weight = weight,
            height = 1.7f, imc = 24f, measuredAt = Date(now - hoursAgo * 3_600_000L))
        assertNull(HealthHistoryRules.weightGain(72f, now, listOf(record(49, 60f), record(-1, 60f))))
        assertEquals(2f, HealthHistoryRules.weightGain(72f, now, listOf(record(48, 70f), record(1, 71.5f)))!!, 0f)
        assertNull(HealthHistoryRules.weightGain(68f, now, listOf(record(24, 72f))))
    }

    @Test fun `trend sorts filters duplicates and emits only the third distinct high reading`() {
        fun vital(day: Int, sys: Int?) = RemoteVitalSign(id = "$day", userId = "u",
            systolicPressure = sys, measuredAt = Instant.ofEpochSecond(day * 86400L).toString())
        val current = vital(5, 185)
        val value: (RemoteVitalSign) -> Int? = { it.systolicPressure }
        val high: (Int) -> Boolean = { it >= 180 }
        assertTrue(HealthHistoryRules.startsHighTrend(current, listOf(vital(2, 120), vital(4, 181), current, vital(3, 190), vital(4, 181)), value, high))
        assertFalse(HealthHistoryRules.startsHighTrend(current, listOf(vital(4, 181), vital(4, 181)), value, high))
        assertFalse(HealthHistoryRules.startsHighTrend(current, listOf(vital(4, 181), vital(3, 190), vital(2, 190)), value, high))
        assertFalse(HealthHistoryRules.startsHighTrend(current, listOf(vital(4, 181), vital(6, 190)), value, high))
    }
}
