package br.com.bragasaude.data.notification

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE, application = Application::class)
class HydrationNotificationsTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private val manager get() = context.getSystemService(NotificationManager::class.java)
    private fun notify(user: String = "patient", day: String = "2026-10-29") =
        HydrationNotifications.notifyOnce(context, user, day, "Meta de hidratação alcançada!", "Meta diária de 2625 ml alcançada.")

    @Before fun reset() {
        manager.cancelAll()
        context.getSharedPreferences("hydration_achievements", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test fun sendsHighPriorityAchievementWithHydrationDestination() {
        assertTrue(notify())
        val notification = manager.activeNotifications.single().notification
        assertEquals("conquistas", notification.channelId)
        assertEquals(NotificationManager.IMPORTANCE_HIGH, manager.getNotificationChannel("conquistas").importance)
        assertEquals("Meta de hidratação alcançada!", notification.extras.getString(Notification.EXTRA_TITLE))
        assertTrue(shadowOf(notification.contentIntent).savedIntent.getBooleanExtra("OPEN_HYDRATION", false))
    }

    @Test fun dailyFlagSurvivesDismissalAndAllowsNextDayOrAnotherAccount() {
        assertTrue(notify())
        manager.cancelAll()
        assertFalse(notify())
        assertTrue(manager.activeNotifications.isEmpty())
        assertTrue(notify(day = "2026-10-30"))
        assertTrue(notify(user = "another"))
    }

    @Test @Config(sdk = [33]) fun deniedPermissionDoesNotCrashOrConsumeDailyFlag() {
        shadowOf(context).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        assertFalse(notify())
        shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        assertTrue(notify())
    }

    @Test fun blockedChannelDoesNotConsumeDailyFlag() {
        manager.createNotificationChannel(NotificationChannel("conquistas", "Conquistas", NotificationManager.IMPORTANCE_NONE))
        assertFalse(notify())
        assertFalse(context.getSharedPreferences("hydration_achievements", Context.MODE_PRIVATE).contains("last_notified_day_patient"))
    }
}
