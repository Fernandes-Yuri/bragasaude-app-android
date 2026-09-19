package br.com.bragasaude.data.remote.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import br.com.bragasaude.data.local.ProfileDao
import br.com.bragasaude.ui.util.NotificationHelper
import com.google.firebase.auth.FirebaseAuth
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Lembrete de consulta (doc 10 §2.4). Agendado pelo NotificationHelper para
 * disparar 24h e 1h antes da consulta. Não usa rede: apenas mostra a notificação
 * local, respeitando as preferências do usuário.
 */
@HiltWorker
class ConsultationReminderWorker @AssistedInject constructor(
    @Assisted private val appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val profileDao: ProfileDao,
    private val auth: FirebaseAuth
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val userId = auth.currentUser?.uid
            // Sem usuário logado ou preferência desligada: não incomoda.
            val profile = userId?.let { profileDao.getProfileOneShot(it) }
            if (profile != null && !profile.notificationsEnabled) {
                return Result.success()
            }

            val title = inputData.getString("title") ?: "Consulta"
            val scheduledAt = inputData.getLong("scheduledAt", 0L)

            val dateText = SimpleDateFormat("dd/MM", Locale.getDefault()).format(Date(scheduledAt))
            val timeText = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(scheduledAt))

            NotificationHelper.sendConsultationReminderNotification(appContext, title, dateText, timeText)
            Result.success()
        } catch (e: Exception) {
            android.util.Log.e("ConsultationWorker", "Erro no lembrete de consulta: ${e.message}", e)
            Result.failure()
        }
    }
}
