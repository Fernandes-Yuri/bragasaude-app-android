package br.com.bragasaude.data.remote.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import br.com.bragasaude.data.local.FamilyDao
import br.com.bragasaude.domain.FamilyConversationPdfExporter
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/** Local cleanup must also run without network connectivity. Android may defer background work. */
@HiltWorker
class FamilyRetentionWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val familyDao: FamilyDao
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = try {
        familyDao.purgeExpiredMessages()
        FamilyConversationPdfExporter.cleanupTemporaryExports(applicationContext)
        Result.success()
    } catch (_: Exception) { Result.retry() }
}
