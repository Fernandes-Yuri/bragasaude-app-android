package br.com.bragasaude.data.remote.sync

import androidx.work.*
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Agendador unificado de sincronização do Braga Saúde.
 *
 * Centraliza o disparo e controle de workers de sincronização com o Firebase Data Connect,
 * garantindo idempotência (enqueueUniqueWork), backoff exponencial e suporte a sync periódico.
 */
@Singleton
class SyncScheduler @Inject constructor(
    private val workManager: WorkManager
) {

    companion object {
        const val UNIQUE_ONE_TIME_SYNC_WORK_NAME = "braga_sync_work"
        const val UNIQUE_PERIODIC_SYNC_WORK_NAME = "braga_periodic_sync_work"
    }

    /**
     * Agenda sincronização imediata (OneTime) respeitando política de duplicidade.
     *
     * @param policy ExistingWorkPolicy padrão é KEEP para reaproveitar execução em andamento.
     */
    fun scheduleSync(policy: ExistingWorkPolicy = ExistingWorkPolicy.KEEP) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)
            .build()

        workManager.enqueueUniqueWork(UNIQUE_ONE_TIME_SYNC_WORK_NAME, policy, request)
    }

    /**
     * Força sincronização imediata (substitui qualquer trabalho pendente).
     */
    fun scheduleExpeditedSync() {
        scheduleSync(ExistingWorkPolicy.REPLACE)
    }

    /**
     * Agenda sincronização periódica de recuperação para evitar dados órfãos com pendingSync=true.
     */
    fun schedulePeriodicRecoverySync() {
        workManager.enqueueUniquePeriodicWork(
            "family_retention", ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<FamilyRetentionWorker>(15, TimeUnit.MINUTES).build()
        )
        workManager.enqueueUniqueWork(
            "family_retention_startup", ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<FamilyRetentionWorker>().build()
        )
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val periodicRequest = PeriodicWorkRequestBuilder<SyncWorker>(6, TimeUnit.HOURS)
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()

        workManager.enqueueUniquePeriodicWork(
            UNIQUE_PERIODIC_SYNC_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            periodicRequest
        )
    }

    /**
     * Cancela trabalhos de sincronização pendentes.
     */
    fun cancelSync() {
        workManager.cancelUniqueWork(UNIQUE_ONE_TIME_SYNC_WORK_NAME)
    }
}
