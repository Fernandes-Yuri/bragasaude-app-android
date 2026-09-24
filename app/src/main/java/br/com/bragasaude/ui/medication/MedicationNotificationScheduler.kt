package br.com.bragasaude.ui.medication

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import br.com.bragasaude.MainActivity
import br.com.bragasaude.data.local.MedicationEntity
import br.com.bragasaude.domain.MedicationSchedule
import br.com.bragasaude.ui.util.MedicationAlarmReceiver
import java.util.Calendar

/**
 * Motor Próprio de Notificações Inteligentes e Afetuosas de Medicamentos.
 *
 * Dispara dois momentos para cada dose configurada:
 * 1) 15 minutos ANTES do horário da dose (lembrete afetuoso preventivo).
 * 2) No HORÁRIO EXATO da dose (com ações imediatas "Já Tomei" e "Lembrar em 10 min").
 *
 * Utiliza AlarmManager.setExactAndAllowWhileIdle e canal de alta prioridade.
 * Conformidade com AUD-AN02: ZERO emojis em strings de UI.
 */
object MedicationNotificationScheduler {

    private const val TAG = "MedNotifScheduler"

    const val CHANNEL_ID = "channel_medications"
    const val CHANNEL_NAME = "Lembretes de Medicamentos"
    const val CHANNEL_DESC = "Avisos prévios e alarmes na hora certa para medicação"

    const val PRE_ALERT_MINUTES = 15
    const val SNOOZE_MINUTES = 10

    /**
     * Cria e registra o canal de notificações de medicamentos no sistema Android.
     */
    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = CHANNEL_DESC
                enableVibration(true)
                setShowBadge(true)
            }
            val manager = context.getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    /**
     * Agenda as notificações de um medicamento (15 min antes + hora exata) para todos os horários cadastrados.
     */
    fun scheduleMedicationNotifications(context: Context, medication: MedicationEntity) {
        createNotificationChannel(context)
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return

        val times = MedicationSchedule.times(medication.scheduleTimes, medication.scheduleTime)
        val now = System.currentTimeMillis()
        val dosage = medication.dosage ?: medication.dosageMg?.let { "${it}mg" } ?: ""

        for (time in times) {
            val parts = time.split(":")
            if (parts.size != 2) continue
            val hour = parts[0].toIntOrNull() ?: continue
            val minute = parts[1].toIntOrNull() ?: continue

            // 1) Próximo horário exato da dose
            val doseCal = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                if (timeInMillis <= now) {
                    add(Calendar.DAY_OF_YEAR, 1)
                }
            }
            val doseTimeMillis = doseCal.timeInMillis
            val scheduledDate = java.time.Instant.ofEpochMilli(doseTimeMillis)
                .atZone(java.time.ZoneId.systemDefault())
                .toLocalDate()
            val doseKey = "${scheduledDate}T$time"

            // Alarme exato da dose
            val exactIntent = Intent(context, MedicationAlarmReceiver::class.java).apply {
                action = MedicationAlarmReceiver.ACTION_MED_REMINDER
                putExtra(MedicationAlarmReceiver.EXTRA_MED_ID, medication.id)
                putExtra(MedicationAlarmReceiver.EXTRA_MED_NAME, medication.name)
                putExtra(MedicationAlarmReceiver.EXTRA_DOSAGE, dosage)
                putExtra(MedicationAlarmReceiver.EXTRA_USER_ID, medication.userId)
                putExtra("schedule_time", time)
                putExtra("scheduled_for", doseKey)
            }
            val exactCode = "${medication.id}_exact_$time".hashCode()
            val exactPending = PendingIntent.getBroadcast(
                context,
                exactCode,
                exactIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            scheduleExact(alarmManager, doseTimeMillis, exactPending)

            // 2) Alarme preventivo afetuoso: 15 minutos antes da dose
            val preTimeMillis = doseTimeMillis - (PRE_ALERT_MINUTES * 60 * 1000)
            if (preTimeMillis > now) {
                val preIntent = Intent(context, MedicationAlarmReceiver::class.java).apply {
                    action = MedicationAlarmReceiver.ACTION_MED_PRE_REMINDER
                    putExtra(MedicationAlarmReceiver.EXTRA_MED_ID, medication.id)
                    putExtra(MedicationAlarmReceiver.EXTRA_MED_NAME, medication.name)
                    putExtra(MedicationAlarmReceiver.EXTRA_DOSAGE, dosage)
                    putExtra(MedicationAlarmReceiver.EXTRA_USER_ID, medication.userId)
                    putExtra("schedule_time", time)
                    putExtra("scheduled_for", doseKey)
                }
                val preCode = "${medication.id}_pre_$time".hashCode()
                val prePending = PendingIntent.getBroadcast(
                    context,
                    preCode,
                    preIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                scheduleExact(alarmManager, preTimeMillis, prePending)
            }

            Log.d(TAG, "Notificações agendadas para ${medication.name} às $time (dose e pré-aviso 15m)")
        }
    }

    /**
     * Cancela todos os alarmes e notificações ativas de um medicamento específico.
     */
    fun cancelMedicationAlarms(context: Context, medicationId: String) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager

        // Cancelar notificações visíveis
        notificationManager?.cancel(medicationId.hashCode())

        // Cancelar alarmes pelo ID base
        val baseIntent = Intent(context, MedicationAlarmReceiver::class.java)
        PendingIntent.getBroadcast(
            context,
            medicationId.hashCode(),
            baseIntent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )?.let {
            alarmManager.cancel(it)
            it.cancel()
        }

        // Cancelar alarmes legados via MedicationAlarmReceiver
        MedicationAlarmReceiver.cancelAlarm(context, medicationId)

        // Limpeza abrangente de todos os slots de horário possíveis (passos de 5 min)
        for (h in 0..23) {
            for (m in 0..59 step 5) {
                val t = String.format(java.util.Locale.ROOT, "%02d:%02d", h, m)
                val exactCode = "${medicationId}_exact_$t".hashCode()
                PendingIntent.getBroadcast(
                    context, exactCode, baseIntent,
                    PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
                )?.let {
                    alarmManager.cancel(it)
                    it.cancel()
                }
                val preCode = "${medicationId}_pre_$t".hashCode()
                PendingIntent.getBroadcast(
                    context, preCode, baseIntent,
                    PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
                )?.let {
                    alarmManager.cancel(it)
                    it.cancel()
                }
                notificationManager?.cancel("$medicationId:$t".hashCode())
                notificationManager?.cancel("$medicationId:pre:$t".hashCode())
                notificationManager?.cancel("$medicationId:exact:$t".hashCode())
            }
        }
    }

    /**
     * Resincroniza todos os alarmes de uma lista de medicamentos.
     */
    fun rescheduleAll(context: Context, medications: List<MedicationEntity>) {
        createNotificationChannel(context)
        for (med in medications) {
            cancelMedicationAlarms(context, med.id)
            scheduleMedicationNotifications(context, med)
        }
    }

    /**
     * Exibe a notificação afetuosa de 15 minutos antes da dose.
     */
    fun showPreDoseNotification(
        context: Context,
        medId: String,
        medName: String,
        time: String
    ) {
        createNotificationChannel(context)
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val openPending = PendingIntent.getActivity(
            context,
            0,
            openIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Braga Saúde • Daqui a pouco!")
            .setContentText("Oi! Passando com carinho para lembrar que em 15 minutinhos é a hora do seu $medName. Já deixe um copo d'água por perto!")
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    "Oi! Passando com carinho para lembrar que em 15 minutinhos é a hora do seu $medName. Já deixe um copo d'água por perto!"
                )
            )
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(openPending)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        val notifId = "$medId:pre:$time".hashCode()
        notificationManager?.notify(notifId, notification)
    }

    /**
     * Exibe a notificação no horário exato da dose, com ações "Já Tomei" e "Lembrar em 10 min".
     */
    fun showExactDoseNotification(
        context: Context,
        medId: String,
        medName: String,
        dosage: String,
        userId: String,
        time: String,
        doseKey: String
    ) {
        createNotificationChannel(context)
        val notifId = "$medId:exact:$time".hashCode()

        // 1) Ação: "Já Tomei"
        val takenIntent = Intent(context, MedicationAlarmReceiver::class.java).apply {
            action = MedicationAlarmReceiver.ACTION_TAKEN
            data = android.net.Uri.parse("bragasaude://dose/$medId/$doseKey")
            putExtra("scheduled_for", doseKey)
            putExtra("schedule_time", time)
            putExtra(MedicationAlarmReceiver.EXTRA_MED_ID, medId)
            putExtra(MedicationAlarmReceiver.EXTRA_MED_NAME, medName)
            putExtra(MedicationAlarmReceiver.EXTRA_USER_ID, userId)
        }
        val takenPending = PendingIntent.getBroadcast(
            context,
            notifId,
            takenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 2) Ação: "Lembrar em 10 min"
        val snoozeIntent = Intent(context, MedicationAlarmReceiver::class.java).apply {
            action = MedicationAlarmReceiver.ACTION_SNOOZE
            putExtra(MedicationAlarmReceiver.EXTRA_MED_ID, medId)
            putExtra(MedicationAlarmReceiver.EXTRA_MED_NAME, medName)
            putExtra(MedicationAlarmReceiver.EXTRA_DOSAGE, dosage)
            putExtra(MedicationAlarmReceiver.EXTRA_USER_ID, userId)
            putExtra("schedule_time", time)
            putExtra("scheduled_for", doseKey)
        }
        val snoozePending = PendingIntent.getBroadcast(
            context,
            notifId + 100,
            snoozeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Ação padrão ao tocar na notificação: abrir o app
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val openPending = PendingIntent.getActivity(
            context,
            0,
            openIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val messageText = "Hora de cuidar da saúde! São $time, momento de tomar seu $medName. Já tomou?"
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Braga Saúde • Hora da sua dose")
            .setContentText(messageText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(messageText))
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(openPending)
            .addAction(android.R.drawable.ic_menu_send, "Já Tomei", takenPending)
            .addAction(android.R.drawable.ic_lock_idle_alarm, "Lembrar em 10 min", snoozePending)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        notificationManager?.notify(notifId, notification)
    }

    private fun scheduleExact(
        alarmManager: AlarmManager,
        triggerAtMillis: Long,
        pendingIntent: PendingIntent
    ) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
            } else {
                alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
            }
        } catch (_: SecurityException) {
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent
            )
        }
    }
}
