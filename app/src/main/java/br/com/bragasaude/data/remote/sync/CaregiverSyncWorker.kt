package br.com.bragasaude.data.remote.sync

import android.content.Context
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.ForegroundInfo
import br.com.bragasaude.R
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import br.com.bragasaude.data.remote.repository.FamilyBridgeRepository
import com.google.firebase.auth.FirebaseAuth
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/** O push só agenda trabalho durável; rede não depende da vida do serviço FCM. */
@HiltWorker
class CaregiverSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val familyRepository: FamilyBridgeRepository,
    private val auth: FirebaseAuth
) : CoroutineWorker(context, params) {
    // WorkManager usa um serviço em primeiro plano para expedited work antes do Android 12.
    override suspend fun getForegroundInfo(): ForegroundInfo {
        val channel = "bragasaude_caregiver_sync"
        applicationContext.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(channel, "Atualização dos dados familiares", NotificationManager.IMPORTANCE_LOW)
        )
        val notification = NotificationCompat.Builder(applicationContext, channel)
            .setSmallIcon(R.drawable.ic_shield_ecg)
            .setContentTitle("Atualizando dados do familiar")
            .setOngoing(true)
            .build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(2020, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else ForegroundInfo(2020, notification)
    }

    override suspend fun doWork(): Result {
        val owner = inputData.getString("owner") ?: return Result.failure()
        val patient = inputData.getString("patient") ?: return Result.failure()
        if (auth.currentUser?.uid != owner) return Result.success()
        return try {
            familyRepository.syncBindingsForCaregiver(owner, strict = true)
            if (auth.currentUser?.uid != owner) return Result.success()
            val bindings = familyRepository.getActiveBindingsForCaregiver(owner).first()
            if (bindings.none { it.patientUserId == patient }) return Result.success()
            familyRepository.syncPatientDataForCaregiver(patient).getOrThrow()
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    companion object {
        fun enqueue(context: Context, owner: String, patient: String) {
            val request = OneTimeWorkRequestBuilder<CaregiverSyncWorker>()
                .setInputData(workDataOf("owner" to owner, "patient" to patient))
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                "caregiver-sync-$owner-$patient", ExistingWorkPolicy.APPEND_OR_REPLACE, request
            )
        }
    }
}
