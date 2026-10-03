package br.com.bragasaude.data.local.voice

import android.Manifest
import android.app.Notification
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
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VoicePreparationNotifications @Inject constructor(@ApplicationContext private val context: Context) {
    companion object {
        const val ONGOING_ID = 7301
        private const val RESULT_ID = 7302
        private const val PROGRESS_CHANNEL = "voice_preparation"
        private const val RESULT_CHANNEL = "voice_preparation_result"
    }

    private fun channels() {
        val manager = requireNotNull(context.getSystemService(NotificationManager::class.java))
        manager.createNotificationChannel(NotificationChannel(PROGRESS_CHANNEL, "Preparação do assistente", NotificationManager.IMPORTANCE_LOW))
        manager.createNotificationChannel(NotificationChannel(RESULT_CHANNEL, "Assistente pronto", NotificationManager.IMPORTANCE_DEFAULT))
    }

    private fun openApp() = PendingIntent.getActivity(context, RESULT_ID,
        Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    fun ongoing(): Notification {
        channels()
        return NotificationCompat.Builder(context, PROGRESS_CHANNEL)
            .setSmallIcon(R.drawable.ic_shield_ecg)
            .setContentTitle("Preparando seu assistente")
            .setContentText("Você pode continuar usando o aplicativo.")
            .setContentIntent(openApp())
            .setProgress(0, 0, true)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    fun result(voiceName: String, success: Boolean) {
        channels()
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return
        val text = if (success) "Seu assistente com a voz $voiceName está pronto. Toque para abrir o Braga Saúde."
            else "Não foi possível preparar seu assistente. Abra as configurações do assistente para tentar novamente."
        try {
            manager.notify(RESULT_ID, NotificationCompat.Builder(context, RESULT_CHANNEL)
                .setSmallIcon(R.drawable.ic_shield_ecg)
                .setContentTitle(if (success) "Seu assistente está pronto" else "A preparação precisa de atenção")
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setContentIntent(openApp())
                .setAutoCancel(true)
                .setOnlyAlertOnce(true)
                .build())
        } catch (_: SecurityException) { /* Permissão pode ser revogada durante a preparação. */ }
    }
}
