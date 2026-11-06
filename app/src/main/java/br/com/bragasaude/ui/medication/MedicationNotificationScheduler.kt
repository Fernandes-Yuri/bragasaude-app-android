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
import br.com.bragasaude.ui.util.MedicationAlarmReceiver

/**
 * APIs legadas de notificação de medicamentos por medicamento (canal [CHANNEL_ID]).
 *
 * #21: o AGENDAMENTO legado está formalmente desativado. A fonte única de verdade
 * de lembretes de medicação é o CareOS
 * [br.com.bragasaude.ui.medication.doses.DoseAlarmScheduler], que agrupa alarmes
 * por ator/paciente/horário (canal `medication_dose_groups`) e é reconciliado por
 * `MedicationDoseRepository.refresh`/`reschedule`.
 *
 * Nenhum caminho do app cria novos alarmes por medicamento: [scheduleMedicationNotifications]
 * e [rescheduleAll] são no-op (apenas log). O que permanece ativo aqui é somente o
 * necessário para encerrar/limpar resíduos de versões antigas e atender PendingIntents
 * já persistidos no SO:
 * - [cancelMedicationAlarms] (limpeza de alarmes/notificações órfãos);
 * - [dismissDoseNotification] e [exactNotificationId] (contrato de dismiss/testes);
 * - [createNotificationChannel], [showPreDoseNotification] e [showExactDoseNotification].
 *
 * Conformidade com AUD-AN02: ZERO emojis em strings de UI.
 */
object MedicationNotificationScheduler {

    private const val TAG = "MedNotifScheduler"

    const val CHANNEL_ID = "channel_medications"
    const val CHANNEL_NAME = "Lembretes de Medicamentos"
    const val CHANNEL_DESC = "Avisos prévios e alarmes na hora certa para medicação"

    // Constantes legadas mantidas apenas por compatibilidade; o pré-aviso canônico
    // agora é controlado pelo DoseAlarmScheduler (`dose_preferences/pre_alert`).
    const val PRE_ALERT_MINUTES = 15
    const val SNOOZE_MINUTES = 10

    fun exactNotificationId(medId: String, time: String): Int = "$medId:exact:$time".hashCode()

    /** Remove somente os lembretes desta dose, incluindo formatos anteriores. */
    fun dismissDoseNotification(context: Context, medId: String, time: String, doseKey: String) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        manager.cancel(exactNotificationId(medId, time))
        manager.cancel("$medId:pre:$time".hashCode())
        manager.cancel("$medId:$doseKey".hashCode())
        manager.cancel(medId.hashCode())
    }

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
     * Agendamento legado DESATIVADO (#21).
     *
     * No-op proposital: NÃO cria PendingIntent nem chama AlarmManager.setExact/
     * setExactAndAllowWhileIdle. O agendador canônico e único é o CareOS
     * [br.com.bragasaude.ui.medication.doses.DoseAlarmScheduler], reconciliado por
     * `MedicationDoseRepository.refresh`/`reschedule`.
     */
    @Deprecated("Agendamento legado desativado; use DoseAlarmScheduler (CareOS)")
    fun scheduleMedicationNotifications(context: Context, medication: MedicationEntity) {
        Log.w(TAG, "scheduleMedicationNotifications ignorado: agendamento legado desativado; use DoseAlarmScheduler (CareOS) [${medication.name}]")
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
     * Resincronização legada DESATIVADA (#21).
     *
     * No-op proposital: NÃO cancela nem cria alarmes por medicamento. A
     * reconciliação canônica é feita pelo CareOS
     * [br.com.bragasaude.ui.medication.doses.DoseAlarmScheduler]; a limpeza de
     * resíduos antigos acontece via [cancelMedicationAlarms].
     */
    @Deprecated("Agendamento legado desativado; use DoseAlarmScheduler (CareOS)")
    fun rescheduleAll(context: Context, medications: List<MedicationEntity>) {
        Log.w(TAG, "rescheduleAll ignorado: agendamento legado desativado; use DoseAlarmScheduler (CareOS) [${medications.size} medicamento(s)]")
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
        val notifId = exactNotificationId(medId, time)

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
}
