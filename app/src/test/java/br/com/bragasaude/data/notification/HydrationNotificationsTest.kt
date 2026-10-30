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
    private fun notifyHalf(user: String = "patient", day: String = "2026-10-29") =
        HydrationNotifications.notifyHalfOnce(context, user, day, "Metade da meta alcançada!", "Você atingiu 50% da meta diária.")

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
        assertFalse(notifyHalf())
        shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        assertTrue(notify())
        assertTrue(notifyHalf())
    }

    @Test fun blockedChannelDoesNotConsumeDailyFlag() {
        manager.createNotificationChannel(NotificationChannel("conquistas", "Conquistas", NotificationManager.IMPORTANCE_NONE))
        assertFalse(notify())
        assertFalse(notifyHalf())
        assertFalse(context.getSharedPreferences("hydration_achievements", Context.MODE_PRIVATE).contains("last_notified_day_patient"))
        assertFalse(context.getSharedPreferences("hydration_achievements", Context.MODE_PRIVATE).contains("last_notified_half_patient"))
    }

    @Test fun halfAndFullAreIndependentAndPersistAfterDismissal() {
        assertTrue(notifyHalf())
        val half = manager.activeNotifications.single().notification
        assertEquals("conquistas", half.channelId)
        assertEquals("Metade da meta alcançada!", half.extras.getString(Notification.EXTRA_TITLE))
        assertTrue(shadowOf(half.contentIntent).savedIntent.getBooleanExtra("OPEN_HYDRATION", false))
        assertFalse(notifyHalf())
        assertTrue(notify())
        assertEquals(2, manager.activeNotifications.size)
        manager.cancelAll()
        assertFalse(notifyHalf())
        assertFalse(notify())
        assertTrue(notifyHalf(day = "2026-10-30"))
    }

    @Test fun rollbackCancelsOnlyItsLevelAndAllowsReachingItAgain() {
        assertTrue(notifyHalf())
        assertTrue(notify())
        HydrationNotifications.clearFullNotification(context, "patient", "2026-10-29")
        assertEquals("Metade da meta alcançada!", manager.activeNotifications.single().notification.extras.getString(Notification.EXTRA_TITLE))
        assertFalse(notifyHalf())
        assertTrue(notify())
        HydrationNotifications.clearHalfNotification(context, "patient", "2026-10-29")
        assertEquals("Meta de hidratação alcançada!", manager.activeNotifications.single().notification.extras.getString(Notification.EXTRA_TITLE))
        assertFalse(notify())
        assertTrue(notifyHalf())
    }

    @Test fun rollbackDoesNotClearAnotherAccountOrAnotherDay() {
        assertTrue(notify())
        assertTrue(notifyHalf())
        assertTrue(notify(user = "another"))
        assertTrue(notifyHalf(user = "another"))
        HydrationNotifications.clearFullNotification(context, "patient", "2026-10-28")
        HydrationNotifications.clearHalfNotification(context, "patient", "2026-10-28")
        assertEquals(4, manager.activeNotifications.size)
        assertFalse(notify())
        assertFalse(notifyHalf())
        HydrationNotifications.clearFullNotification(context, "patient", "2026-10-29")
        HydrationNotifications.clearHalfNotification(context, "patient", "2026-10-29")
        assertEquals(2, manager.activeNotifications.size)
        assertFalse(notify(user = "another"))
        assertFalse(notifyHalf(user = "another"))
        assertTrue(notify())
        assertTrue(notifyHalf())
    }

    @Test fun rollbackAfterDismissalStillRearmsBothLevels() {
        assertTrue(notify())
        assertTrue(notifyHalf())
        manager.cancelAll()
        repeat(2) {
            HydrationNotifications.clearFullNotification(context, "patient", "2026-10-29")
            HydrationNotifications.clearHalfNotification(context, "patient", "2026-10-29")
        }
        assertTrue(notify())
        assertTrue(notifyHalf())
    }
}
