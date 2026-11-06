package br.com.bragasaude.ui.util

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import br.com.bragasaude.MainActivity
import br.com.bragasaude.R
import br.com.bragasaude.data.local.MedicationEntity
import br.com.bragasaude.domain.MedicationSchedule
import kotlinx.coroutines.launch

/**
 * Receiver legado de alarmes de medicação por medicamento.
 *
 * #21: o agendamento concorrente está formalmente DESATIVADO. A fonte única de
 * verdade é o CareOS [br.com.bragasaude.ui.medication.doses.DoseAlarmScheduler]
 * (grupos por ator/paciente/horário, canal medication_dose_groups). Este receiver
 * permanece apenas para:
 * - atender PendingIntents já persistidos no SO por versões antigas, exibindo a
 *   notificação sem reagendar nada;
 * - registrar a dose tomada (ACTION_TAKEN, via goAsync);
 * - encerrar notificações antigas e limpar alarmes órfãos (cancelAlarm).
 *
 * Nenhum caminho deste arquivo cria novos alarmes no AlarmManager.
 * Ações: ACTION_TAKEN (registra) e ACTION_SNOOZE (apenas encerra a notificação).
 */
class MedicationAlarmReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_MED_REMINDER = "br.com.bragasaude.MED_REMINDER"
        const val ACTION_MED_PRE_REMINDER = "br.com.bragasaude.MED_PRE_REMINDER"
        const val ACTION_TAKEN = "br.com.bragasaude.MED_TAKEN"
        const val ACTION_SNOOZE = "br.com.bragasaude.MED_SNOOZE"
        const val CHANNEL_ID = "channel_medications"
        const val LEGACY_CHANNEL_ID = "medication_channel"
        const val EXTRA_MED_ID = "med_id"
        const val EXTRA_MED_NAME = "med_name"
        const val EXTRA_DOSAGE = "med_dosage"
        const val EXTRA_USER_ID = "user_id"

        private const val MEDICATION_ALARM_ACTION_PREFIX = "medication_alarm_"

        fun getUniqueAction(medId: String) = "${MEDICATION_ALARM_ACTION_PREFIX}$medId"

        fun cancelAlarm(context: Context, medId: String) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, MedicationAlarmReceiver::class.java).apply {
                action = getUniqueAction(medId)
            }
            val requestCode = getUniqueAction(medId).hashCode()
            val pendingIntent = PendingIntent.getBroadcast(
                context, requestCode, intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            pendingIntent?.let { alarmManager.cancel(it); it.cancel() }
            PendingIntent.getBroadcast(context, medId.hashCode(), intent, PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)?.let { alarmManager.cancel(it); it.cancel() }
        }

        /**
         * Cancela alarmes de todos os medicamentos de um usuário.
         * Útil em logout ou exclusão de conta.
         */
        fun cancelAllAlarms(
            context: Context,
            medications: List<MedicationEntity>
        ) {
            for (med in medications) {
                cancelAlarm(context, med.id) // cancela também o formato legado
                MedicationSchedule.times(med.scheduleTimes, med.scheduleTime).forEach { cancelAlarm(context, "${med.id}:$it") }
            }
        }

        fun createChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val manager = context.getSystemService(NotificationManager::class.java) ?: return
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "Lembretes de Medicamentos",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Avisos prévios e alarmes na hora certa para medicação"
                    enableVibration(true)
                }
                manager.createNotificationChannel(channel)

                val legacyChannel = NotificationChannel(
                    LEGACY_CHANNEL_ID,
                    "Lembretes de Medicação",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Lembretes precisos de horários de remédio"
                    enableVibration(true)
                }
                manager.createNotificationChannel(legacyChannel)
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        when {
            action == ACTION_MED_PRE_REMINDER -> handlePreReminder(context, intent)
            action == ACTION_MED_REMINDER || action?.startsWith(MEDICATION_ALARM_ACTION_PREFIX) == true ->
                showMedicationNotification(context, intent)
            action == ACTION_TAKEN -> handleDoseTaken(context, intent)
            action == ACTION_SNOOZE -> handleSnooze(context, intent)
        }
    }

    private fun handlePreReminder(context: Context, intent: Intent) {
        val medName = intent.getStringExtra(EXTRA_MED_NAME) ?: "Medicamento"
        val medId = intent.getStringExtra(EXTRA_MED_ID) ?: ""
        val time = intent.getStringExtra("schedule_time") ?: ""
        br.com.bragasaude.ui.medication.MedicationNotificationScheduler.showPreDoseNotification(
            context = context,
            medId = medId,
            medName = medName,
            time = time
        )
    }

    private fun showMedicationNotification(context: Context, intent: Intent) {
        val medName = intent.getStringExtra(EXTRA_MED_NAME) ?: "Medicamento"
        val dosage = intent.getStringExtra(EXTRA_DOSAGE) ?: ""
        val medId = intent.getStringExtra(EXTRA_MED_ID) ?: ""
        val userId = intent.getStringExtra(EXTRA_USER_ID) ?: ""

        val doseKey = intent.getStringExtra("scheduled_for") ?: return
        val time = intent.getStringExtra("schedule_time") ?: return

        // #21: PendingIntents antigos continuam exibindo a notificação, mas o
        // receiver NÃO se reagenda mais. O agendamento canônico e único é o
        // DoseAlarmScheduler (CareOS), reconciliado por MedicationDoseRepository.
        br.com.bragasaude.ui.medication.MedicationNotificationScheduler.showExactDoseNotification(
            context = context,
            medId = medId,
            medName = medName,
            dosage = dosage,
            userId = userId,
            time = time,
            doseKey = doseKey
        )
    }

    /**
     * TASK-MED-02: registra a dose tomada sem travar a UI.
     * Usa goAsync() para executar em background.
     */
    private fun handleDoseTaken(context: Context, intent: Intent) {
        val medId = intent.getStringExtra(EXTRA_MED_ID) ?: return
        val medName = intent.getStringExtra(EXTRA_MED_NAME) ?: ""
        val userId = intent.getStringExtra(EXTRA_USER_ID) ?: ""

        val doseKey = intent.getStringExtra("scheduled_for") ?: return
        val time = intent.getStringExtra("schedule_time") ?: return
        val pendingResult = goAsync()
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            try {
                val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

                // Registrar dose no Room via Hilt EntryPoint
                val appContext = context.applicationContext
                val entryPoint = dagger.hilt.android.EntryPointAccessors.fromApplication(
                    appContext,
                    MedicationAlarmEntryPoint::class.java
                )
                val repository = entryPoint.medicationRepository()

                // Execução assíncrona suspensa direta em Dispatchers.IO (sem travar a main thread e prevenindo ANR)
                val date = java.time.LocalDate.parse(doseKey.substringBefore("T"))
                if (!repository.takeMedication(userId, medId, time, date)) return@launch

                // Só encerra o lembrete quando o registro local tiver sido concluído.
                br.com.bragasaude.ui.medication.MedicationNotificationScheduler.dismissDoseNotification(
                    context, medId, time, doseKey
                )

                // Mostrar confirmação rápida
                val confirmationId = "$medId:confirmed:$doseKey".hashCode()
                val openConfirmation = PendingIntent.getActivity(
                    context, confirmationId,
                    Intent(context, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_SINGLE_TOP },
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                val confirmNotification = NotificationCompat.Builder(context, CHANNEL_ID)
                    .setContentTitle("Dose registrada")
                    .setContentText("$medName registrado às ${java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date())}")
                    .setSmallIcon(android.R.drawable.ic_menu_my_calendar)
                    .setContentIntent(openConfirmation)
                    .setPriority(NotificationCompat.PRIORITY_LOW)
                    .setSilent(true)
                    .setAutoCancel(true)
                    .setTimeoutAfter(30_000L)
                    .build()
                manager.notify(confirmationId, confirmNotification)
            } catch (e: Exception) {
                Log.e("MedAlarm", "Erro ao processar dose: ${e.message}", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun handleSnooze(context: Context, intent: Intent) {
        val medId = intent.getStringExtra(EXTRA_MED_ID) ?: return
        val time = intent.getStringExtra("schedule_time") ?: ""

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancel(medId.hashCode())
        if (time.isNotBlank()) {
            manager.cancel("$medId:exact:$time".hashCode())
        }

        // #21: nenhum reagendamento via AlarmManager. O snooze canônico é
        // DoseAlarmScheduler.snooze (chave por ator/grupo de horário); esta ação
        // legada apenas encerra notificações antigas já persistidas no SO.
        Log.d("MedAlarm", "Snooze legado de $medId em $time encerrado sem novo alarme (canônico: DoseAlarmScheduler)")
    }
}

/**
 * Hilt EntryPoint para acessar repositórios no BroadcastReceiver.
 */
@dagger.hilt.EntryPoint
@dagger.hilt.InstallIn(dagger.hilt.components.SingletonComponent::class)
interface MedicationAlarmEntryPoint {
    fun medicationRepository(): br.com.bragasaude.data.remote.repository.MedicationRepository
}
