package br.com.bragasaude.data.remote.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import br.com.bragasaude.data.local.ProfileDao
import br.com.bragasaude.data.local.VitalSignDao
import br.com.bragasaude.data.local.VitalSignEntity
import br.com.bragasaude.ui.util.NotificationHelper
import com.google.firebase.auth.FirebaseAuth
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.firstOrNull

@HiltWorker
class HydrationReminderWorker @AssistedInject constructor(
    @Assisted private val appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val vitalSignDao: VitalSignDao,
    private val profileDao: ProfileDao,
    private val auth: FirebaseAuth
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        try {
            val userId = auth.currentUser?.uid ?: return Result.success()

            val profile = profileDao.getProfileOneShot(userId) ?: return Result.success()

            // (issue #7) Trava de silêncio noturno: não dispara lembretes entre 20h00 e 08h00,
            // independentemente do agendador, garantindo que não acorde o usuário à noite.
            val horaAtual = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
            if (horaAtual >= 20 || horaAtual < 8) {
                return Result.success()
            }

            // (issue #7) Não dispara lembretes enquanto o onboarding/tutorial não foi concluído.
            val tutorialConcluido = appContext.getSharedPreferences("braga_prefs", android.content.Context.MODE_PRIVATE)
                .getBoolean("tutorial_seen", false)
            if (!tutorialConcluido) {
                return Result.success()
            }

            // 1. O Cuidador em modo VIEWER_ONLY NÃO deve receber lembretes de autocuidado de hidratação pessoal
            if (profile.caregiverMode == "VIEWER_ONLY") {
                NotificationHelper.cancelHydrationReminders(appContext)
                return Result.success()
            }

            // 2. Respeita preferências de notificação do usuário
            if (!profile.notificationsEnabled) {
                return Result.success()
            }

            // 3. Verifica horário de sono para não incomodar durante a noite
            val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
            val sleepStartHour = profile.sleepStartTime?.split(":")?.getOrNull(0)?.toIntOrNull() ?: 22
            val sleepEndHour = profile.sleepEndTime?.split(":")?.getOrNull(0)?.toIntOrNull() ?: 6

            val isSleeping = if (sleepStartHour > sleepEndHour) {
                currentHour >= sleepStartHour || currentHour < sleepEndHour
            } else {
                currentHour in sleepStartHour until sleepEndHour
            }

            if (isSleeping) {
                return Result.success()
            }

            // Calcula total de água ingerido hoje
            val vitals: List<VitalSignEntity> = vitalSignDao.getAll(userId).firstOrNull() ?: emptyList()
            val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            val currentMl = vitals
                .filter { it.hydrationMl != null && it.hydrationMl > 0 }
                .filter {
                    val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(it.measuredAt)
                    dateStr == todayStr
                }
                .sumOf { it.hydrationMl ?: 0 }

            val targetMl = profile?.hydrationTargetMl ?: 2000

            // Se ainda não atingiu a meta, envia o lembrete
            if (currentMl < targetMl) {
                NotificationHelper.sendHydrationNotification(appContext, currentMl, targetMl)
            }

            return Result.success()
        } catch (e: Exception) {
            android.util.Log.e("HydrationWorker", "Erro no lembrete de hidratação: ${e.message}", e)
            return Result.failure()
        }
    }
}
