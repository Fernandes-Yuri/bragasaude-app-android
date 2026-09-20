package br.com.bragasaude.data.remote.repository

import br.com.bragasaude.data.local.DailyMetricsDao
import br.com.bragasaude.data.local.DailyMetricsEntity
import br.com.bragasaude.data.remote.api.BragaApiClient
import br.com.bragasaude.data.remote.model.RemoteUserStats
import br.com.bragasaude.data.remote.sync.SyncScheduler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
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
        // AUD-AN40: antes retornava `null` INCONDICIONAL — o chamador
        // (StepsViewModel) tratava o null e seguia, mas a média semanal/mensal
        // da tela de passos ficava sempre zerada. Agora computa dos 30 dias
        // locais (que sao a fonte usada pelos graficos da mesma tela).
        return try {
            val days = dailyMetricsDao.getRecent30Days(userId).firstOrNull()
                ?: return null
            if (days.isEmpty()) return null

            val total = days.sumOf { it.steps }
            val avg = total / days.size

            // "Mensal" aqui e a media dos 30 dias; "semanal", a media dos
            // ultimos 7 (a lista vem ordenada DESC por data).
            val weekly = days.take(7)
            val weeklyAvg = if (weekly.isNotEmpty()) weekly.sumOf { it.steps } / weekly.size else 0

            val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            val stepsToday = days.firstOrNull { it.date == today }?.steps ?: 0

            RemoteUserStats(
                userId = userId,
                weeklyAvgSteps = weeklyAvg,
                monthlyAvgSteps = avg,
                stepsToday = stepsToday,
            )
        } catch (e: Exception) {
            null
        }
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
