package br.com.bragasaude.domain

import java.util.Calendar
import java.util.TimeZone
import org.junit.Assert.*
import org.junit.Test

class PersonalReportPeriodTest {
    @Test fun incluiExatamenteTrintaDiasDeCalendarioSemRegistrosFuturos() {
        val now = Calendar.getInstance(TimeZone.getTimeZone("America/Sao_Paulo")).apply {
            clear()
            set(2026, Calendar.OCTOBER, 1, 16, 50, 0)
        }
        val period = PersonalReportPeriod.current(now)
        val start = (now.clone() as Calendar).apply {
            set(2026, Calendar.SEPTEMBER, 2, 0, 0, 0)
        }
        assertEquals(start.timeInMillis, period.startMillis)
        assertTrue(period.contains(period.startMillis))
        assertTrue(period.contains(now.timeInMillis))
        assertFalse(period.contains(period.startMillis - 1))
        assertFalse(period.contains(now.timeInMillis + 1))
        assertEquals(Calendar.OCTOBER, now.get(Calendar.MONTH))
    }
}
