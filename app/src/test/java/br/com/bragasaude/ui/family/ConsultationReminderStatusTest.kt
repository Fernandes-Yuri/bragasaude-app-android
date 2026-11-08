package br.com.bragasaude.ui.family

import androidx.work.WorkInfo
import br.com.bragasaude.ui.util.pendingConsultationReminderTags
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.*
import org.junit.Test

class ConsultationReminderStatusTest {
    private val now = 1_000_000L
    private val scheduled = now + 48 * 3_600_000L
    private fun work(state: WorkInfo.State, at: Long? = scheduled): WorkInfo = mockk {
        every { this@mockk.state } returns state
        every { tags } returns if (at == null) emptySet() else setOf("consultation_at_$at")
    }

    @Test fun onlyPendingWorkForCurrentScheduleCounts() {
        assertEquals(setOf("24h", "1h"), pendingConsultationReminderTags(
            listOf(work(WorkInfo.State.ENQUEUED)), listOf(work(WorkInfo.State.BLOCKED)), scheduled, now))
        for (finished in listOf(WorkInfo.State.CANCELLED, WorkInfo.State.FAILED, WorkInfo.State.SUCCEEDED)) {
            assertTrue(pendingConsultationReminderTags(listOf(work(finished)), emptyList(), scheduled, now).isEmpty())
        }
        assertTrue(pendingConsultationReminderTags(
            listOf(work(WorkInfo.State.ENQUEUED, scheduled + 1)), emptyList(), scheduled, now).isEmpty())
    }

    @Test fun legacyWorkRemainsVisibleAndElapsedReminderIsOmitted() {
        assertEquals(setOf("24h"), pendingConsultationReminderTags(
            listOf(work(WorkInfo.State.ENQUEUED, null)), emptyList(), scheduled, now))
        assertEquals(setOf("1h"), pendingConsultationReminderTags(
            listOf(work(WorkInfo.State.ENQUEUED)), listOf(work(WorkInfo.State.ENQUEUED)), scheduled,
            scheduled - 2 * 3_600_000L))
    }

    @Test fun badgeDoesNotPromiseNotificationsWhenPreferenceOrPermissionIsOff() {
        val active = ConsultationReminderStatus(setOf("24h", "1h"), loading = false)
        assertEquals("Avisos: 24h e 1h antes", active.label)
        assertTrue(active.active)
        assertEquals("Avisos desativados no app", active.copy(appEnabled = false).label)
        assertFalse(active.copy(appEnabled = false).active)
        assertEquals("Avisos bloqueados no aparelho", active.copy(deviceEnabled = false).label)
        assertFalse(active.copy(deviceEnabled = false).active)
        assertEquals("Avisos não agendados", active.copy(scheduledTags = emptySet()).label)
        assertEquals("Aviso: 1h antes", active.copy(scheduledTags = setOf("1h")).label)
    }
}
