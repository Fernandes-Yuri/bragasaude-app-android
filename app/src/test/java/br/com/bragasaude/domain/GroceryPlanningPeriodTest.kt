package br.com.bragasaude.domain

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class GroceryPlanningPeriodTest {
    @Test fun datesCrossMonthsAndDoNotResetOnMonday() {
        assertEquals(30, GroceryPlanningPeriod.daysRemaining("2026-10-16", 30, LocalDate.parse("2026-10-16")))
        assertEquals(27, GroceryPlanningPeriod.daysRemaining("2026-10-16", 30, LocalDate.parse("2026-10-19")))
        assertEquals(1, GroceryPlanningPeriod.daysRemaining("2026-10-16", 30, LocalDate.parse("2026-11-14")))
        assertEquals(0, GroceryPlanningPeriod.daysRemaining("2026-10-16", 30, LocalDate.parse("2026-11-15")))
    }
    @Test fun goalAndStockUseTheSelectedHorizon() {
        for (days in GroceryPlanningPeriod.options) {
            assertEquals(2000.0 * days, GroceryWeeklyGoal.calculate(2000.0, emptyList(), days).target, 0.001)
            assertFalse(GroceryConsumption.insufficient(100.0, days * 100.0, 1, days))
            assertTrue(GroceryConsumption.insufficient(99.0, days * 100.0, 1, days))
        }
    }
    @Test(expected = IllegalArgumentException::class) fun unsupportedDaysAreRejected() {
        WeeklyGroceryPreferences(planningDays = 15).normalized()
    }
}
