package br.com.bragasaude.data.remote.repository

import br.com.bragasaude.data.local.MilestoneDao
import br.com.bragasaude.data.local.MilestoneEntity
import br.com.bragasaude.data.remote.model.RemoteMilestone
import br.com.bragasaude.data.remote.sync.SyncScheduler
import br.com.bragasaude.data.util.toEntity
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MilestonesRepository @Inject constructor(
    private val milestoneDao: MilestoneDao,
    private val syncScheduler: SyncScheduler
) {
    private val guestId = "00000000-0000-0000-0000-000000000000"

    fun getMilestones(userId: String): Flow<List<MilestoneEntity>> = milestoneDao.getAll(userId)

    suspend fun saveMilestone(milestone: RemoteMilestone) {
        val remoteWithId = if (milestone.id == null) milestone.copy(id = UUID.randomUUID().toString()) else milestone
        val entity = remoteWithId.toEntity().copy(pendingSync = false)
        milestoneDao.insert(entity)
    }

    private fun triggerSync() {
        syncScheduler.scheduleSync()
    }
}
