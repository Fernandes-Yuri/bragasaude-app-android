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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Calendar
import br.com.bragasaude.domain.MedicationSchedule

/**
 * MÓDULO 08 — Receiver para alarmes de medicação.
 * Usa AlarmManager.setExactAndAllowWhileIdle para garantir disparos precisos
 * mesmo em modo Doze (Android 6+).
 *
 * Ações da notificação:
 * - ACTION_TAKEN: registra dose tomada + concede XP (via goAsync, sem travar UI)
 * - ACTION_SNOOZE: adia 15 min (re-agenda o alarme)
 */
class MedicationAlarmReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_MED_REMINDER = "br.com.bragasaude.MED_REMINDER"
        const val ACTION_TAKEN = "br.com.bragasaude.MED_TAKEN"
        const val ACTION_SNOOZE = "br.com.bragasaude.MED_SNOOZE"
        const val CHANNEL_ID = "medication_channel"
        const val EXTRA_MED_ID = "med_id"
        const val EXTRA_MED_NAME = "med_name"
        const val EXTRA_DOSAGE = "med_dosage"
        const val EXTRA_USER_ID = "user_id"

        /**
         * Agenda um alarme preciso para o horário do remédio.
         * Deve ser chamado quando o usuário cadastra/edita um medicamento.
         */
        fun scheduleAlarm(
            context: Context,
            medId: String,
            medName: String,
            dosage: String?,
            userId: String,
            timeHour: Int,
            timeMinute: Int
        ) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

            val intent = Intent(context, MedicationAlarmReceiver::class.java).apply {
                action = getUniqueAction("$medId:${String.format(java.util.Locale.ROOT, "%02d:%02d", timeHour, timeMinute)}")
                putExtra(EXTRA_MED_ID, medId)
                putExtra(EXTRA_MED_NAME, medName)
                putExtra(EXTRA_DOSAGE, dosage ?: "")
                putExtra(EXTRA_USER_ID, userId)
                putExtra("schedule_time", String.format(java.util.Locale.ROOT, "%02d:%02d", timeHour, timeMinute))
            }

            val requestCode = intent.action.hashCode()
            // Calcular o próximo horário do alarme
            val calendar = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, timeHour)
                set(Calendar.MINUTE, timeMinute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                // Se o horário já passou hoje, agenda para amanhã
                if (timeInMillis <= System.currentTimeMillis()) {
                    add(Calendar.DAY_OF_YEAR, 1)
                }
            }

            intent.putExtra("scheduled_for", java.time.Instant.ofEpochMilli(calendar.timeInMillis).atZone(java.time.ZoneId.systemDefault()).toLocalDate().toString() + "T" + intent.getStringExtra("schedule_time"))
            // Atualiza também a identidade da ocorrência utilizada pela ação de confirmação.
            val datedPending = PendingIntent.getBroadcast(context, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            try {
                // setExactAndAllowWhileIdle dispara mesmo em Doze (com restrição de 1/min)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        calendar.timeInMillis,
                        datedPending
                    )
                } else {
                    alarmManager.setExact(
                        AlarmManager.RTC_WAKEUP,
                        calendar.timeInMillis,
                        datedPending
                    )
                }
                Log.d("MedAlarm", "Alarme agendado para ${medName} às ${timeHour}:${timeMinute}")
            } catch (e: SecurityException) {
                // Android 12+ pode exigir SCHEDULE_EXACT_ALARM permission
                Log.e("MedAlarm", "Sem permissão para alarme exato: ${e.message}")
            }
        }

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
         * Agenda alarmes para todos os medicamentos de um usuário.
         * Canala alarmes antigos antes de agendar os novos (via FLAG_UPDATE_CURRENT).
         */
        fun scheduleAllAlarms(
            context: Context,
            userId: String,
            medications: List<MedicationEntity>
        ) {
            val filtered = medications.filter { it.userId == userId }
            for (med in filtered) {
                for (time in MedicationSchedule.times(med.scheduleTimes, med.scheduleTime)) {
                    val parts = time.split(":")
                    scheduleAlarm(context, med.id, med.name, med.dosage ?: med.dosageMg?.let { "${it}mg" },
                        userId, parts[0].toInt(), parts[1].toInt())
                }
            }
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
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "Lembretes de Medicação",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Lembretes precisos de horários de remédio"
                    enableVibration(true)
                }
                val manager = context.getSystemService(NotificationManager::class.java)
                manager.createNotificationChannel(channel)
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        when {
            action == ACTION_MED_REMINDER || action?.startsWith(MEDICATION_ALARM_ACTION_PREFIX) == true ->
                showMedicationNotification(context, intent)
            action == ACTION_TAKEN -> handleDoseTaken(context, intent)
            action == ACTION_SNOOZE -> handleSnooze(context, intent)
        }
    }

    private fun showMedicationNotification(context: Context, intent: Intent) {
        val medName = intent.getStringExtra(EXTRA_MED_NAME) ?: "Medicamento"
        val dosage = intent.getStringExtra(EXTRA_DOSAGE) ?: ""
        val medId = intent.getStringExtra(EXTRA_MED_ID) ?: ""
        val userId = intent.getStringExtra(EXTRA_USER_ID) ?: ""

        val doseKey = intent.getStringExtra("scheduled_for") ?: return
        val time = intent.getStringExtra("schedule_time") ?: return
        val notificationId = "$medId:$doseKey".hashCode()
        val parts = time.split(":")
        if (parts.size == 2) scheduleAlarm(context, medId, medName, dosage, userId, parts[0].toInt(), parts[1].toInt())
        createChannel(context)

        // Intent para "Tomar agora"
        val takenIntent = Intent(context, MedicationAlarmReceiver::class.java).apply {
            action = ACTION_TAKEN
            data = android.net.Uri.parse("bragasaude://dose/$medId/$doseKey")
            putExtra("scheduled_for", doseKey)
            putExtra("schedule_time", time)
            putExtra(EXTRA_MED_ID, medId)
            putExtra(EXTRA_MED_NAME, medName)
            putExtra(EXTRA_USER_ID, userId)
        }
        val takenPending = PendingIntent.getBroadcast(
            context, notificationId, takenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Intent para abrir o app
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val openPending = PendingIntent.getActivity(
            context, 0, openIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Hora do remédio: $medName")
            .setContentText(if (dosage.isNotBlank()) "Dose: $dosage" else "Toque para registrar")
            .setSmallIcon(android.R.drawable.ic_menu_my_calendar)
            .setContentIntent(openPending)
            .addAction(android.R.drawable.ic_menu_send, "Tomar agora", takenPending)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(notificationId, notification)
    }

    /**
     * TASK-MED-02: registra a dose tomada + concede XP sem travar a UI.
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
                // Cancelar a notificação
                val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                manager.cancel("$medId:$doseKey".hashCode())

                // Registrar dose no Room via Hilt EntryPoint
                val appContext = context.applicationContext
                val entryPoint = dagger.hilt.android.EntryPointAccessors.fromApplication(
                    appContext,
                    MedicationAlarmEntryPoint::class.java
                )
                val repository = entryPoint.medicationRepository()
                val xpService = entryPoint.xpGrantService()

                // Execução assíncrona suspensa direta em Dispatchers.IO (sem travar a main thread e prevenindo ANR)
                val date = java.time.LocalDate.parse(doseKey.substringBefore("T"))
                if (!repository.takeMedication(userId, medId, time, date)) return@launch
                xpService.grantXp(
                    userId = userId,
                    action = br.com.bragasaude.domain.GamificationActionType.MEDICATION_TAKEN_ON_TIME,
                    isActionValid = date == java.time.LocalDate.now() && br.com.bragasaude.domain.GamificationEngine.isMedicationOnTime(time, Calendar.getInstance().get(Calendar.HOUR_OF_DAY), Calendar.getInstance().get(Calendar.MINUTE))
                )

                // Mostrar confirmação rápida
                val confirmNotification = NotificationCompat.Builder(context, CHANNEL_ID)
                    .setContentTitle("Dose registrada")
                    .setContentText("$medName registrado às ${java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date())}")
                    .setSmallIcon(android.R.drawable.ic_menu_my_calendar)
                    .setPriority(NotificationCompat.PRIORITY_LOW)
                    .setAutoCancel(true)
                    .build()
                manager.notify(medId.hashCode() + 1, confirmNotification)

                Log.d("MedAlarm", "Dose registrada; pontuação avaliada pela janela de horário")
            } catch (e: Exception) {
                Log.e("MedAlarm", "Erro ao processar dose: ${e.message}", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun handleSnooze(context: Context, intent: Intent) {
        val medId = intent.getStringExtra(EXTRA_MED_ID) ?: return
        val medName = intent.getStringExtra(EXTRA_MED_NAME) ?: ""
        val dosage = intent.getStringExtra(EXTRA_DOSAGE) ?: ""
        val userId = intent.getStringExtra(EXTRA_USER_ID) ?: ""

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancel(medId.hashCode())

        // Re-agendar daqui a 15 minutos
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val snoozeIntent = Intent(context, MedicationAlarmReceiver::class.java).apply {
            action = getUniqueAction(medId)
            putExtra(EXTRA_MED_ID, medId)
            putExtra(EXTRA_MED_NAME, medName)
            putExtra(EXTRA_DOSAGE, dosage)
            putExtra(EXTRA_USER_ID, userId)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context, medId.hashCode(), snoozeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val snoozeTime = System.currentTimeMillis() + (15 * 60 * 1000)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, snoozeTime, pendingIntent)
        } else {
            alarmManager.setExact(AlarmManager.RTC_WAKEUP, snoozeTime, pendingIntent)
        }
    }
}

/**
 * Hilt EntryPoint para acessar repositórios no BroadcastReceiver.
 */
@dagger.hilt.EntryPoint
@dagger.hilt.InstallIn(dagger.hilt.components.SingletonComponent::class)
interface MedicationAlarmEntryPoint {
    fun medicationRepository(): br.com.bragasaude.data.remote.repository.MedicationRepository
    fun xpGrantService(): br.com.bragasaude.domain.XpGrantService
}
