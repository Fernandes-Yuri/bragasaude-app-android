package br.com.bragasaude.data.remote.repository

import br.com.bragasaude.data.local.MilestoneDao
import br.com.bragasaude.data.local.MilestoneEntity
import br.com.bragasaude.data.remote.model.RemoteMilestone
import br.com.bragasaude.data.remote.sync.SyncScheduler
import br.com.bragasaude.data.util.toEntity
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import br.com.bragasaude.util.BragaConstants

@Singleton
class MilestonesRepository @Inject constructor(
    private val milestoneDao: MilestoneDao,
    private val syncScheduler: SyncScheduler
) {
    private val guestId = BragaConstants.GUEST_UID

    fun getMilestones(userId: String): Flow<List<MilestoneEntity>> = milestoneDao.getAll(userId)

    suspend fun saveMilestone(milestone: RemoteMilestone) {
        val remoteWithId = if (milestone.id == null) milestone.copy(id = UUID.randomUUID().toString()) else milestone
        val entity = remoteWithId.toEntity().copy(pendingSync = false)
        milestoneDao.insert(entity)
    }

    /** Remove também marcos antigos com IDs aleatórios e eventuais duplicatas. */
    suspend fun deleteMilestonesForDay(userId: String, badgeType: String, day: LocalDate) {
        val zone = ZoneId.systemDefault()
        milestoneDao.deleteForDay(
            userId, badgeType,
            day.atStartOfDay(zone).toInstant().toEpochMilli(),
            day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        )
    }

    private fun triggerSync() {
        syncScheduler.scheduleSync()
    }
}
