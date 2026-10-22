package br.com.bragasaude.ui.family

import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import br.com.bragasaude.ui.util.FamilyNotificationService
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34], application=Application::class)
class FamilyNotificationPrivacyTest {
    @Test fun lockedScreenVersionContainsNeitherSenderNorMessage() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        FamilyNotificationService.initChannels(context)
        FamilyNotificationService.notifyPatientMessage(context, "Nome privado", "Texto privado")
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = manager.activeNotifications.single().notification
        assertEquals(Notification.VISIBILITY_PRIVATE, notification.visibility)
        assertEquals("Mensagem de Nome privado", notification.extras.getCharSequence(Notification.EXTRA_TITLE))
        val publicVersion = requireNotNull(notification.publicVersion)
        assertEquals("Braga Saúde", publicVersion.extras.getCharSequence(Notification.EXTRA_TITLE))
        assertEquals("Você recebeu uma mensagem da família.", publicVersion.extras.getCharSequence(Notification.EXTRA_TEXT))
    }
}
