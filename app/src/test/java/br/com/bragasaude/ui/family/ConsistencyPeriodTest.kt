package br.com.bragasaude.ui.family

import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class ConsistencyPeriodTest {
    @Test fun calendarExcludesTodayAndDoesNotUseStepsDatesForWater() {
        val today = LocalDate.of(2026, 11, 7)
        val actual = consistencyPeriod(today, 7, mapOf(today.toString() to 5000,
            "2026-11-06" to 2500, "2026-11-05" to 500, "2026-10-01" to 5000), 2000)
        assertEquals(ConsistencyPeriod(1, 2, 5), actual)
    }
    @Test fun emptyPeriodHasUnknownDaysRatherThanZeroPercentAdherence() {
        assertEquals(ConsistencyPeriod(0, 0, 30), consistencyPeriod(LocalDate.of(2026, 3, 1), 30, emptyMap(), 2000))
    }
}
