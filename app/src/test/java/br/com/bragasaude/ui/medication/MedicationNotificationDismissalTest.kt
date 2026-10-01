package br.com.bragasaude.ui.medication

import android.app.NotificationManager
import android.content.Context
import io.mockk.*
import org.junit.Assert.assertEquals
import org.junit.Test

class MedicationNotificationDismissalTest {
    @Test fun `confirmacao cancela o id do lembrete exibido sem cancelar outros horarios`() {
        val context = mockk<Context>()
        val manager = mockk<NotificationManager>(relaxed = true)
        every { context.getSystemService(Context.NOTIFICATION_SERVICE) } returns manager

        MedicationNotificationScheduler.dismissDoseNotification(context, "med-a", "08:00", "2026-10-01T08:00")

        assertEquals("med-a:exact:08:00".hashCode(), MedicationNotificationScheduler.exactNotificationId("med-a", "08:00"))
        verify(exactly = 1) { manager.cancel(MedicationNotificationScheduler.exactNotificationId("med-a", "08:00")) }
        verify(exactly = 0) { manager.cancel(MedicationNotificationScheduler.exactNotificationId("med-a", "20:00")) }
        verify(exactly = 0) { manager.cancel(MedicationNotificationScheduler.exactNotificationId("med-b", "08:00")) }
        verify(exactly = 0) { manager.cancelAll() }
    }

    @Test fun `confirmacao tambem limpa pre aviso e formatos anteriores`() {
        val context = mockk<Context>()
        val manager = mockk<NotificationManager>(relaxed = true)
        every { context.getSystemService(Context.NOTIFICATION_SERVICE) } returns manager

        MedicationNotificationScheduler.dismissDoseNotification(context, "med-a", "08:00", "2026-10-01T08:00")

        verify { manager.cancel("med-a:pre:08:00".hashCode()) }
        verify { manager.cancel("med-a:2026-10-01T08:00".hashCode()) }
        verify { manager.cancel("med-a".hashCode()) }
    }
}
