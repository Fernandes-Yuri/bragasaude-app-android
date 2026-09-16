package br.com.bragasaude.data.remote.repository

import br.com.bragasaude.data.local.DailyMetricsDao
import br.com.bragasaude.data.local.DailyMetricsEntity
import br.com.bragasaude.data.remote.api.BragaApiClient
import br.com.bragasaude.data.remote.model.RemoteUserStats
import br.com.bragasaude.data.remote.sync.SyncScheduler
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ActivityRepository @Inject constructor(
    private val apiClient: BragaApiClient,
    private val dailyMetricsDao: DailyMetricsDao,
    private val syncScheduler: SyncScheduler
) {
    fun getRecent30Days(userId: String): Flow<List<DailyMetricsEntity>> = dailyMetricsDao.getRecent30Days(userId)

    suspend fun getUserStatsSummary(userId: String): RemoteUserStats? {
        return null
    }

    suspend fun saveDailyMetrics(metrics: DailyMetricsEntity) {
        dailyMetricsDao.insert(metrics.copy(pendingSync = false))
        
        try {
            val serverId = apiClient.syncDailyMetric(metrics)
            if (serverId == null) {
                dailyMetricsDao.insert(metrics.copy(pendingSync = true))
                triggerSync()
            }
        } catch (e: Exception) {
            dailyMetricsDao.insert(metrics.copy(pendingSync = true))
            triggerSync()
        }
    }

    private fun triggerSync() {
        syncScheduler.scheduleSync()
    }
}
