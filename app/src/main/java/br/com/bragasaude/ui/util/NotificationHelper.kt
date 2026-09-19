package br.com.bragasaude.ui.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import br.com.bragasaude.MainActivity

object NotificationHelper {
    private const val CHANNEL_ID = "bragasaude_alerts"
    private const val CHANNEL_NAME = "Alertas do Braga Saúde"
    private const val NOTIFICATION_ID = 2001

    private const val HYDRATION_CHANNEL_ID = "bragasaude_hydration"
    private const val HYDRATION_CHANNEL_NAME = "Lembretes de Hidratação"
    private const val HYDRATION_NOTIFICATION_ID = 2002

    fun sendPermissionNotification(context: Context) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notificações para ativação de recursos de saúde"
            }
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("REQUEST_PERMISSIONS", true)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Ativar Monitoramento de Passos")
            .setContentText("Clique aqui para autorizar o rastreamento de sua atividade física e melhorar seu Score de Saúde.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        notificationManager.notify(NOTIFICATION_ID, builder.build())
    }

    fun sendHydrationNotification(context: Context, currentMl: Int, targetMl: Int) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                HYDRATION_CHANNEL_ID,
                HYDRATION_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Lembretes periódicos para beber água e manter a saúde"
            }
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("OPEN_HYDRATION", true)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            1,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val remaining = (targetMl - currentMl).coerceAtLeast(0)
        val progressPercent = if (targetMl > 0) (currentMl * 100 / targetMl).coerceIn(0, 100) else 0

        val text = if (remaining > 0) {
            "Hora de cuidar de você! Que tal um copo d'água agora? Você já registrou $currentMl ml hoje ($progressPercent% da sua meta)."
        } else {
            "Muito bem! Você já alcançou sua meta de $targetMl ml de água hoje. Continue com esse ótimo hábito!"
        }

        val quickIntent = Intent(context, HydrationQuickActionReceiver::class.java).apply {
            putExtra("amount_ml", 250)
        }
        val quickPendingIntent = PendingIntent.getBroadcast(
            context,
            2,
            quickIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, HYDRATION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_rotate)
            .setContentTitle("Lembrete de Hidratação")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_input_add, "Registrar 250ml", quickPendingIntent)

        notificationManager.notify(HYDRATION_NOTIFICATION_ID, builder.build())
    }

    private const val GROCERY_CHANNEL_ID = "bragasaude_grocery"
    private const val GROCERY_CHANNEL_NAME = "Lista de Feira da Família"
    private const val GROCERY_NOTIFICATION_ID = 2003

    private const val CONSULTATION_CHANNEL_ID = "bragasaude_consultation"
    private const val CONSULTATION_CHANNEL_NAME = "Lembretes de Consulta"
    private const val CONSULTATION_NOTIFICATION_ID = 2004
    private const val CONSULTATION_REMINDER_NOTIFICATION_ID = 2005

    fun sendConsulationScheduledNotification(context: Context, title: String, dateText: String, timeText: String) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CONSULTATION_CHANNEL_ID,
                CONSULTATION_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Lembretes e avisos de consultas médicas"
            }
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("OPEN_CONSULTATION", true)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            4,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val text = "Nova consulta agendada: $title - $dateText às $timeText"

        val builder = NotificationCompat.Builder(context, CONSULTATION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_add)
            .setContentTitle("Consulta Médica Agendada")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        notificationManager.notify(CONSULTATION_NOTIFICATION_ID, builder.build())
    }

    fun sendConsultationReminderNotification(context: Context, title: String, dateText: String, timeText: String) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CONSULTATION_CHANNEL_ID,
                CONSULTATION_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Lembretes e avisos de consultas médicas"
            }
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("OPEN_CONSULTATION", true)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            5,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val text = "Lembrete: Sua consulta de $title é amanhã, $dateText às $timeText"

        val builder = NotificationCompat.Builder(context, CONSULTATION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle("Lembrete de Consulta")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        notificationManager.notify(CONSULTATION_REMINDER_NOTIFICATION_ID, builder.build())
    }

    fun sendConsultationAcceptedNotification(context: Context, title: String, caregiverName: String) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CONSULTATION_CHANNEL_ID,
                CONSULTATION_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Lembretes e avisos de consultas médicas"
            }
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("OPEN_CONSULTATION", true)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            6,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val text = "Consulta aceita! $caregiverName sugeriu uma consulta de $title"

        val builder = NotificationCompat.Builder(context, CONSULTATION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_add)
            .setContentTitle("Consulta Aceita")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        notificationManager.notify(CONSULTATION_NOTIFICATION_ID + 1, builder.build())
    }

    fun sendConsultationRejectedNotification(context: Context, title: String) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CONSULTATION_CHANNEL_ID,
                CONSULTATION_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Lembretes e avisos de consultas médicas"
            }
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("OPEN_CONSULTATION", true)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            7,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val text = "Consulta recusada: $title"

        val builder = NotificationCompat.Builder(context, CONSULTATION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_close_clear_cancel)
            .setContentTitle("Consulta Recusada")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        notificationManager.notify(CONSULTATION_NOTIFICATION_ID + 2, builder.build())
    }

    fun sendGroceryUpdatedNotification(context: Context, familyMemberName: String, itemsSummary: String) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                GROCERY_CHANNEL_ID,
                GROCERY_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Avisos quando itens são adicionados à lista de feira da família"
            }
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("OPEN_GROCERY", true)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            3,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val message = "$familyMemberName anotou na lista de feira: $itemsSummary"

        val builder = NotificationCompat.Builder(context, GROCERY_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_agenda)
            .setContentTitle("Lista de Feira Atualizada")
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        notificationManager.notify(GROCERY_NOTIFICATION_ID, builder.build())
    }

    fun scheduleHydrationReminders(context: Context) {
        try {
            val constraints = androidx.work.Constraints.Builder()
                .build()

            val hydrationWork = androidx.work.PeriodicWorkRequestBuilder<br.com.bragasaude.data.remote.sync.HydrationReminderWorker>(
                2, java.util.concurrent.TimeUnit.HOURS
            ).setConstraints(constraints).build()

            androidx.work.WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "braga_hydration_reminders",
                androidx.work.ExistingPeriodicWorkPolicy.UPDATE,
                hydrationWork
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun cancelHydrationReminders(context: Context) {
        try {
            androidx.work.WorkManager.getInstance(context).cancelUniqueWork("braga_hydration_reminders")
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Agenda lembretes de consulta para 24h e 1h antes (doc 10 §2.4).
     * WorkManager one-time com atraso — sobrevive a reinicialização do app.
     * Lembretes cujo horário já passou são ignorados.
     */
    fun scheduleConsultationReminders(context: Context, consultationId: String, title: String, scheduledDateMillis: Long) {
        try {
            val now = System.currentTimeMillis()
            val data = androidx.work.Data.Builder()
                .putString("title", title)
                .putLong("scheduledAt", scheduledDateMillis)
                .build()

            for ((tag, hoursBefore) in listOf("24h" to 24L, "1h" to 1L)) {
                val delay = scheduledDateMillis - hoursBefore * 3_600_000 - now
                if (delay <= 0) continue
                val work = androidx.work.OneTimeWorkRequestBuilder<br.com.bragasaude.data.remote.sync.ConsultationReminderWorker>()
                    .setInitialDelay(delay, java.util.concurrent.TimeUnit.MILLISECONDS)
                    .setInputData(data)
                    .build()
                androidx.work.WorkManager.getInstance(context).enqueueUniqueWork(
                    "braga_consultation_reminder_${consultationId}_$tag",
                    androidx.work.ExistingWorkPolicy.REPLACE,
                    work
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /** Cancela lembretes pendentes de uma consulta (ex.: foi cancelada/recusada). */
    fun cancelConsultationReminders(context: Context, consultationId: String) {
        try {
            val wm = androidx.work.WorkManager.getInstance(context)
            wm.cancelUniqueWork("braga_consultation_reminder_${consultationId}_24h")
            wm.cancelUniqueWork("braga_consultation_reminder_${consultationId}_1h")
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

