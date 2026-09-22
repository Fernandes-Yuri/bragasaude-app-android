package br.com.bragasaude.data.remote.sync

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import androidx.core.app.NotificationCompat
import br.com.bragasaude.MainActivity
import br.com.bragasaude.R
import br.com.bragasaude.data.remote.service.NotificationClient
import br.com.bragasaude.ui.util.FamilyNotificationService
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class BragaFirebaseMessagingService : FirebaseMessagingService() {

    @Inject lateinit var notificationClient: NotificationClient

    companion object {
        const val CHANNEL_EMERGENCY = "bragasaude_emergency"
        const val CHANNEL_CLINICAL = "bragasaude_clinic_alerts"
        const val CHANNEL_PROACTIVE = "bragasaude_proactive_reminders"
        const val CHANNEL_CONSULTATION = "bragasaude_consultation"
        const val CHANNEL_UPDATES = "bragasaude_app_updates"

        /**
         * Canal padrao declarado no AndroidManifest
         * (com.google.firebase.messaging.default_notification_channel_id). E por
         * ele que o Play Services desenha o balao quando o app esta FECHADO —
         * nesse caso o onMessageReceived nao roda, e sem canal o Android 8+
         * simplesmente descarta a notificacao.
         */
        const val CHANNEL_DEFAULT = "bragasaude_family_alerts"

        /**
         * Cria todos os canais de push no startup (chamado pelo BragaApplication).
         *
         * Antes cada canal so era criado dentro do seu showXxxNotification(), ou
         * seja, so existia DEPOIS que um push daquele tipo chegava com o app
         * aberto. Se o primeiro push chegava com o app fechado, o canal nao
         * existia e a notificacao era descartada pelo NotificationManager.
         */
        fun initChannels(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            fun criar(id: String, nome: String, descricao: String, alta: Boolean) {
                manager.createNotificationChannel(
                    NotificationChannel(
                        id, nome,
                        if (alta) NotificationManager.IMPORTANCE_HIGH else NotificationManager.IMPORTANCE_DEFAULT
                    ).apply {
                        this.description = descricao
                        enableVibration(true)
                        if (alta) {
                            enableLights(true)
                            lightColor = Color.RED
                        }
                    }
                )
            }

            criar(CHANNEL_DEFAULT, "Avisos da família", "Mensagens e avisos de cuidado da família", alta = true)
            criar(CHANNEL_EMERGENCY, "Alertas de Emergência", "Alertas imediatos de socorro ou crise aguda", alta = true)
            criar(CHANNEL_CLINICAL, "Alertas clínicos", "Leituras críticas (pressão, glicose) enviadas ao cuidador", alta = true)
            criar(CHANNEL_PROACTIVE, "Lembretes proativos", "Lembretes de autocuidado (manhã, hidratação, noite)", alta = false)
            criar(CHANNEL_CONSULTATION, "Consultas", "Status de consultas agendadas", alta = false)
            criar(CHANNEL_UPDATES, "Atualizações do app", "Novas versões e melhorias do Braga Saúde", alta = true)
        }

        // IDs próprios do FCM (o FamilyNotificationService usa a faixa 1001-1005).
        private const val ID_FAMILY_MESSAGE = 2001
        private const val ID_CONSULTATION = 2010
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        val title = remoteMessage.notification?.title ?: remoteMessage.data["title"] ?: "Braga Saúde"
        val message = remoteMessage.notification?.body ?: remoteMessage.data["message"] ?: remoteMessage.data["body"] ?: ""
        val type = remoteMessage.data["type"] ?: "PROACTIVE_BRAGA"

        if (message.isNotBlank()) {
            when (type) {
                "EMERGENCY" -> showEmergencyNotification(title, message)
                "ALERT_CRITICAL" -> showClinicNotification(title, message, isCritical = true)
                "FAMILY_MESSAGE" -> showFamilyMessageNotification(title, message)
                "CONSULTATION_CREATED", "CONSULTATION_ACCEPTED", "CONSULTATION_REJECTED",
                "CONSULTATION_CANCELLED", "CONSULTATION_COMPLETED" ->
                    showConsultationNotification(title, message)
                "APP_UPDATE" -> {
                    val downloadUrl = remoteMessage.data["download_url"] ?: "https://api.bragasaude.online/api/app/download"
                    showUpdateNotification(title, message, downloadUrl)
                }
                else -> showClinicNotification(title, message, isCritical = false)
            }
        }
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        applicationContext.getSharedPreferences("braga_prefs", Context.MODE_PRIVATE)
            .edit()
            .putString("fcm_token", token)
            .apply()

        // Sincronizar token com o Hub do Lenovo G460
        val auth = FirebaseAuth.getInstance()
        val userId = auth.currentUser?.uid ?: "anonymous"
        CoroutineScope(Dispatchers.IO).launch {
            try {
                notificationClient.registerDevice(
                    userId = userId,
                    token = token,
                    role = "USER",
                    name = auth.currentUser?.displayName ?: "Usuário Braga"
                )
            } catch (_: Exception) {}
        }
    }

    private fun showEmergencyNotification(title: String, message: String) {
        val context = applicationContext
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_EMERGENCY,
                "Alertas de Emergência e Pânico",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alertas imediatos de socorro ou crises agudas da família"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 200, 500, 200, 500)
                enableLights(true)
                lightColor = Color.RED
            }
            manager.createNotificationChannel(channel)
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("OPEN_EMERGENCY", true)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            192,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_EMERGENCY)
            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setAutoCancel(true)
            .setColor(Color.RED)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .build()

        manager.notify(192, notification)
    }

    private fun showClinicNotification(title: String, message: String, isCritical: Boolean) {
        val channelId = if (isCritical) CHANNEL_CLINICAL else CHANNEL_PROACTIVE
        val channelName = if (isCritical) "Avisos Clínicos e Cuidado Familiar" else "Lembretes do Braga"
        val context = applicationContext
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                channelName,
                if (isCritical) NotificationManager.IMPORTANCE_HIGH else NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Lembretes, orientações educativas e mensagens da sua rede de cuidado"
                enableVibration(true)
            }
            manager.createNotificationChannel(channel)
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            System.currentTimeMillis().toInt(),
            intent,
            PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(if (isCritical) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
            .build()

        manager.notify(System.currentTimeMillis().toInt(), notification)
    }

    private fun showFamilyMessageNotification(title: String, message: String) {
        // Canal da família (family_messages_channel): criado no startup pelo
        // FamilyNotificationService.initChannels (plano de notificações, doc 10 §3.1/G1).
        val context = applicationContext
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("OPEN_FAMILY_MESSAGES", true)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            ID_FAMILY_MESSAGE,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(context, FamilyNotificationService.CHANNEL_MESSAGES)
            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(R.drawable.ic_shield_ecg)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .build()

        manager.notify(ID_FAMILY_MESSAGE, notification)
    }

    private fun showConsultationNotification(title: String, message: String) {
        // Canal de consultas (bragasaude_consultation): mesmo id usado pelo
        // NotificationHelper, então os lembretes locais e os pushes ficam juntos.
        val context = applicationContext
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_CONSULTATION,
                "Lembretes de Consulta",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Lembretes e avisos de consultas médicas"
                enableVibration(true)
            }
            manager.createNotificationChannel(channel)
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("OPEN_CONSULTATION", true)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            ID_CONSULTATION,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_CONSULTATION)
            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(android.R.drawable.ic_menu_my_calendar)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .build()

        manager.notify(ID_CONSULTATION, notification)
    }

    private fun showUpdateNotification(title: String, message: String, downloadUrl: String) {
        val channelId = "bragasaude_app_updates"
        val channelName = "Atualizações do Braga Saúde"
        val context = applicationContext
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                channelName,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notificações de novas versões e melhorias do aplicativo"
                enableVibration(true)
            }
            manager.createNotificationChannel(channel)
        }

        // AUD-AN26: este ID 1001 colidia com a faixa do FamilyNotificationService
        // (1001-1005). Na prática a notificação de update podia SOBRESCREVER a
        // notificação familiar (e vice-versa) e, no Android 14+, um notify() com ID
        // de foreground service pode fazer o sistema ENCERRAR o StepTrackingService.
        // Faixa dedicada e alta para updates de app.
        val notifyId = 7777
        val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(downloadUrl)).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            notifyId,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .build()

        manager.notify(notifyId, notification)
    }
}
