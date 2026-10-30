package br.com.bragasaude.data.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import br.com.bragasaude.MainActivity
import br.com.bragasaude.R

object HydrationNotifications {
    const val CHANNEL_ID = "conquistas"
    private const val NOTIFICATION_ID = 2003
    private const val HALF_NOTIFICATION_ID = 2005

    /** Persisted per account/day, including after the process is recreated. */
    @Synchronized
    fun notifyOnce(context: Context, userId: String, day: String, title: String, message: String): Boolean {
        return notifyAchievement(context, userId, day, title, message, "last_notified_day_$userId", NOTIFICATION_ID)
    }

    @Synchronized
    fun notifyHalfOnce(context: Context, userId: String, day: String, title: String, message: String): Boolean {
        return notifyAchievement(context, userId, day, title, message, "last_notified_half_$userId", HALF_NOTIFICATION_ID)
    }

    @Synchronized
    fun clearFullNotification(context: Context, userId: String, day: String) {
        clearAchievement(context, userId, day, "last_notified_day_$userId", NOTIFICATION_ID)
    }

    @Synchronized
    fun clearHalfNotification(context: Context, userId: String, day: String) {
        clearAchievement(context, userId, day, "last_notified_half_$userId", HALF_NOTIFICATION_ID)
    }

    private fun clearAchievement(context: Context, userId: String, day: String, key: String, notificationId: Int) {
        val preferences = context.getSharedPreferences("hydration_achievements", Context.MODE_PRIVATE)
        if (preferences.getString(key, null) == day) {
            preferences.edit().remove(key).apply()
        }
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancel("hydration:$userId:$day", notificationId)
    }

    private fun notifyAchievement(
        context: Context, userId: String, day: String, title: String, message: String,
        key: String, notificationId: Int
    ): Boolean {
        val preferences = context.getSharedPreferences("hydration_achievements", Context.MODE_PRIVATE)
        if (preferences.getString(key, null) == day) return false
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context,
                Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return false
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, "Conquistas", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Conquistas das metas diárias de cuidado"
            })
            if (manager.getNotificationChannel(CHANNEL_ID)?.importance == NotificationManager.IMPORTANCE_NONE) return false
        }
        val openHydration = PendingIntent.getActivity(context, notificationId,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra("OPEN_HYDRATION", true)
            }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_water_bottle)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_SOUND)
            .setContentIntent(openHydration)
            .setAutoCancel(true)
            .build()
        return try {
            manager.notify("hydration:$userId:$day", notificationId, notification)
            preferences.edit().putString(key, day).apply()
            true
        } catch (_: SecurityException) {
            false // Permission may be revoked between the check and notify.
        }
    }
}
