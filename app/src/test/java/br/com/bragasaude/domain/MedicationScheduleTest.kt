package br.com.bragasaude.domain

import java.time.LocalDate
import java.time.LocalDateTime
import org.junit.Assert.*
import org.junit.Test

class MedicationScheduleTest {
    @Test fun `prescribed times are validated sorted and deduplicated`() {
        assertEquals(listOf("08:00", "14:00", "20:00"), MedicationSchedule.parse("20:00,08:00;14:00,08:00"))
        listOf("", "25:00", "12:60", "08:00,", "8h", "08:00, amanhã").forEach {
            assertNull(MedicationSchedule.parse(it))
        }
        assertEquals(listOf("08:00"), MedicationSchedule.times(null, "08:00:00"))
    }

    @Test fun `same occurrence has stable id but another dose day or user does not`() {
        val key = MedicationSchedule.key(LocalDate.of(2026, 9, 9), "08:00")
        assertEquals(MedicationSchedule.logId("u", "m", key), MedicationSchedule.logId("u", "m", key))
        assertNotEquals(MedicationSchedule.logId("u", "m", key), MedicationSchedule.logId("other", "m", key))
        assertNotEquals(MedicationSchedule.logId("u", "m", key), MedicationSchedule.logId("u", "m", "2026-09-09T14:00"))
        assertNotEquals(MedicationSchedule.logId("u", "m", key), MedicationSchedule.logId("u", "m", "2026-09-10T08:00"))
    }

    @Test fun `availability starts two hours before prescribed time`() {
        val date = LocalDate.of(2026, 9, 9)
        assertFalse(MedicationSchedule.available(date, "08:00", LocalDateTime.parse("2026-09-09T05:59")))
        assertTrue(MedicationSchedule.available(date, "08:00", LocalDateTime.parse("2026-09-09T06:00")))
    }
}
