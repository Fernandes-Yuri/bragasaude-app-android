package br.com.bragasaude.domain.smoking

import java.time.*
import org.junit.Assert.*
import org.junit.Test

class SmokingSupportRulesTest {
    private val clock = Clock.fixed(Instant.parse("2026-10-23T01:00:00Z"), ZoneOffset.UTC)
    private val rules = SmokingSupportRules(clock, ZoneId.of("America/Sao_Paulo"))
    private val start = LocalDate.parse("2026-10-20")

    @Test fun unknownDatesAndPrePlanHistoryAreNotAbstinence() {
        assertEquals(1, rules.daysRecordedWithoutSmoking(start, mapOf(
            start.minusDays(1) to false, start to false, start.plusDays(2) to true, start.plusDays(3) to false)))
        assertEquals(LocalDate.parse("2026-10-22"), rules.today())
    }
    @Test fun midnightUsesInjectedZone() {
        val utc = SmokingSupportRules(clock, ZoneOffset.UTC)
        utc.validateEntry(LocalDate.parse("2026-10-23"), start, false, 0)
        invalid { rules.validateEntry(LocalDate.parse("2026-10-23"), start, false, 0) }
    }
    @Test fun positiveConsumptionMayHaveUnknownCountButNeverZeroOrNegative() {
        rules.validateEntry(start, start, true, null)
        rules.validateEntry(start, start, true, 2)
        invalid { rules.validateEntry(start, start, true, 0) }
        invalid { rules.validateEntry(start, start, true, -1) }
        invalid { rules.validateEntry(start, start, false, null) }
        invalid { rules.validateEntry(start.minusDays(1), start, false, 0) }
    }
    @Test fun preparationTargetDoesNotShiftStartOrCountDays() {
        rules.validatePlan(start, start.plusMonths(1), null, emptySet())
        assertEquals(0, rules.daysRecordedWithoutSmoking(start, emptyMap()))
        invalid { rules.validatePlan(start.plusDays(5), null, null, emptySet()) }
        invalid { rules.validatePlan(start, start.minusDays(1), null, emptySet()) }
        invalid { rules.validatePlan(start, null, "a".repeat(301), emptySet()) }
    }
    private fun invalid(block: () -> Unit) {
        try { block(); fail("A regra deveria rejeitar a entrada") } catch (_: IllegalArgumentException) { }
    }
}
