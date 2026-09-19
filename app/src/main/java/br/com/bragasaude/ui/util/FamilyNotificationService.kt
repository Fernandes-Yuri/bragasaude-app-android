package br.com.bragasaude.ui.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import br.com.bragasaude.MainActivity
import br.com.bragasaude.R

/**
 * Servico de Notificacoes do modulo Familia & Cuidado.
 *
 * Canal FAMILY_MESSAGES_CHANNEL para mensagens e bilhetes no celular do idoso.
 * Alertas de protecao passiva para o cuidador quando metricas criticas sao registradas.
 */
object FamilyNotificationService {

    const val CHANNEL_MESSAGES = "family_messages_channel"
    const val CHANNEL_MESSAGES_NAME = "Mensagens Familiares"
    const val CHANNEL_ALERTS = "family_alerts_channel"
    const val CHANNEL_ALERTS_NAME = "Avisos de Cuidado Familiar"

    private const val ID_MESSAGE_NOTIFICATION = 1001
    private const val ID_BP_ALERT = 1002
    private const val ID_HYDRATION_REMINDER = 1003
    const val ID_CRITICAL_ALERT = 1004
    const val ID_MED_DELAY_ALERT = 1005

    /** Inicializar canais de notificacao. Deve ser chamado na aplicacao ou MainActivity. */
    fun initChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Canal para mensagens do familiar
        val messageChannel = NotificationChannel(
            CHANNEL_MESSAGES,
            CHANNEL_MESSAGES_NAME,
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Mensagens e bilhetes de carinho entre você e sua família"
            enableVibration(true)
            enableLights(true)
            vibrationPattern = longArrayOf(0, 300, 100, 300)
        }

        // Canal para avisos de cuidado e bem-estar
        val alertChannel = NotificationChannel(
            CHANNEL_ALERTS,
            CHANNEL_ALERTS_NAME,
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Avisos de acompanhamento e sinais vitais da pessoa acompanhada"
            enableVibration(true)
            enableLights(true)
            lightColor = android.graphics.Color.YELLOW
            vibrationPattern = longArrayOf(0, 400, 200, 400)
        }

        notificationManager.createNotificationChannel(messageChannel)
        notificationManager.createNotificationChannel(alertChannel)
    }

    /**
     * Disparar notificacao de mensagem recebida no celular do familiar acompanhado.
     * Ex.: "Mensagem de carinho do seu filho Carlos: Hora de tomar seu remédio!"
     */
    fun notifyPatientMessage(
        context: Context,
        senderName: String,
        messageText: String
    ) {
        val pendingIntent = PendingIntent.getActivity(
            context,
            ID_MESSAGE_NOTIFICATION,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                putExtra("OPEN_FAMILY_MESSAGES", true)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_MESSAGES)
            .setSmallIcon(R.drawable.ic_shield_ecg)
            .setContentTitle("Mensagem de $senderName")
            .setContentText(messageText)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setStyle(NotificationCompat.BigTextStyle().bigText(messageText))

        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(ID_MESSAGE_NOTIFICATION, builder.build())
    }

    /** Alias acolhedor para notifyPatientMessage */
    fun notifyFamilyMemberMessage(
        context: Context,
        senderName: String,
        messageText: String
    ) {
        notifyPatientMessage(context, senderName, messageText)
    }

    /**
     * Disparar aviso visual para o CUIDADOR sobre Pressao Arterial fora da faixa esperada.
     */
    fun notifyCaregiverCriticalBP(
        context: Context,
        patientName: String,
        systolic: Int,
        diastolic: Int
    ) {
        val severityLabel = if (systolic >= 160) "Muito Alta" else "Baixa"
        val alertText =
            "Aviso de cuidado: A pressão de $patientName ($severityLabel) foi registrada como ${systolic}/${diastolic} mmHg. Vale ligar ou mandar uma mensagem para saber como ele(a) está."

        val pendingIntent = PendingIntent.getActivity(
            context,
            ID_BP_ALERT,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                putExtra("SHOW_CRITICAL_ALERT", true)
                putExtra("BP_SYSTEMIC", systolic)
                putExtra("BP_DIASTOLIC", diastolic)
                putExtra("PATIENT_NAME", patientName)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ALERTS)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("Aviso de Cuidado Familiar")
            .setContentText(alertText)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setColor(android.graphics.Color.DKGRAY)
            .setContentIntent(pendingIntent)
            .setStyle(NotificationCompat.BigTextStyle().bigText(alertText))

        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(ID_BP_ALERT, builder.build())
    }

    /**
     * Disparar notificacao de lembrete de hábito/medicação no celular do titular.
     */
    fun notifyMedicationReminder(
        context: Context,
        medicationName: String,
        dosage: String?
    ) {
        val text = if (!dosage.isNullOrBlank()) {
            "Hora do hábito: $medicationName ($dosage)"
        } else {
            "Hora do hábito: $medicationName"
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            ID_HYDRATION_REMINDER,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_MESSAGES)
            .setSmallIcon(R.drawable.ic_shield_ecg)
            .setContentTitle("Lembrete de Autocuidado")
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(ID_HYDRATION_REMINDER, builder.build())
    }

    /**
     * Aviso de AUTOCUIDADO para o próprio paciente (doc 10 §3.4).
     *
     * Antes a notificação local de sinal vital crítico era escrita para o cuidador
     * ("A pressão de Dona Maria foi registrada...") e exibida no aparelho de quem
     * mediu — o paciente lia um texto sobre si mesmo em terceira pessoa. O cuidador
     * continua sendo avisado pelo push do servidor (VitalsRepository ->
     * /api/family/health-alert); a notificação LOCAL fica com o autocuidado.
     */
    fun notifyPatientSelfCareAlert(
        context: Context,
        alertTitle: String,
        alertMessage: String
    ) {
        val pendingIntent = PendingIntent.getActivity(
            context,
            ID_CRITICAL_ALERT,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                putExtra("SHOW_CRITICAL_ALERT", true)
                putExtra("ALERT_MESSAGE", alertMessage)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_MESSAGES)
            .setSmallIcon(R.drawable.ic_shield_ecg)
            .setContentTitle(alertTitle)
            .setContentText(alertMessage)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setStyle(NotificationCompat.BigTextStyle().bigText(alertMessage))

        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(ID_CRITICAL_ALERT, builder.build())
    }

    /**
     * Disparo de Aviso ao Cuidador sobre sinal vital registrado fora da faixa habitual.
     */
    fun notifyCaregiverCriticalVitals(
        context: Context,
        patientName: String,
        alertMessage: String
    ) {
        val pendingIntent = PendingIntent.getActivity(
            context,
            ID_CRITICAL_ALERT,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                putExtra("SHOW_CRITICAL_ALERT", true)
                putExtra("PATIENT_NAME", patientName)
                putExtra("ALERT_MESSAGE", alertMessage)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ALERTS)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("Aviso de Bem-Estar — $patientName")
            .setContentText(alertMessage)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setColor(android.graphics.Color.DKGRAY)
            .setContentIntent(pendingIntent)
            .setStyle(NotificationCompat.BigTextStyle().bigText(alertMessage))

        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(ID_CRITICAL_ALERT, builder.build())
    }

    /**
     * Aviso carinhoso ao cuidador se o familiar atrasar o registro de uma dose.
     */
    fun notifyCaregiverMedicationDelay(
        context: Context,
        memberName: String,
        medicationName: String,
        scheduledTime: String
    ) {
        val pendingIntent = PendingIntent.getActivity(
            context,
            ID_MED_DELAY_ALERT,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                putExtra("OPEN_FAMILY_DASHBOARD", true)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val message = "Lembrete com carinho: $memberName ainda não registrou o hábito ($medicationName, previsto para às $scheduledTime). Vale mandar uma mensagem carinhosa."

        val builder = NotificationCompat.Builder(context, CHANNEL_MESSAGES)
            .setSmallIcon(R.drawable.ic_shield_ecg)
            .setContentTitle("Lembrete de Cuidado — $memberName")
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(ID_MED_DELAY_ALERT, builder.build())
    }
}
