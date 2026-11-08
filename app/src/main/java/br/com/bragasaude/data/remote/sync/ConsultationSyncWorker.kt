package br.com.bragasaude.data.remote.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.*
import br.com.bragasaude.data.remote.repository.FamilyBridgeRepository
import com.google.firebase.auth.FirebaseAuth
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class ConsultationSyncWorker @AssistedInject constructor(
    @Assisted context: Context, @Assisted parameters: WorkerParameters,
    private val repository: FamilyBridgeRepository, private val auth: FirebaseAuth
) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val owner = inputData.getString("owner") ?: return Result.failure()
        val patient = inputData.getString("patient") ?: return Result.failure()
        if (auth.currentUser?.uid != owner) return Result.success()
        return try {
            repository.syncConsultations(patient)
            Result.success()
        } catch (e: kotlinx.coroutines.CancellationException) { throw e
        } catch (_: Exception) { Result.retry() }
    }
    companion object {
        fun enqueue(context: Context, owner: String, patient: String) {
            val request = OneTimeWorkRequestBuilder<ConsultationSyncWorker>()
                .setInputData(workDataOf("owner" to owner, "patient" to patient))
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build()
            WorkManager.getInstance(context).enqueueUniqueWork("consultation-sync-$owner-$patient", ExistingWorkPolicy.APPEND_OR_REPLACE, request)
        }
    }
}
