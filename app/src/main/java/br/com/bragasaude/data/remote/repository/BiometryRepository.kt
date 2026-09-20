package br.com.bragasaude.data.remote.repository

import br.com.bragasaude.data.local.BiometryDao
import br.com.bragasaude.data.local.BiometryEntity
import br.com.bragasaude.data.remote.model.RemoteBiometry
import br.com.bragasaude.data.remote.sync.SyncScheduler
import br.com.bragasaude.data.util.toEntity
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import br.com.bragasaude.util.BragaConstants

@Singleton
class BiometryRepository @Inject constructor(
    private val biometryDao: BiometryDao,
    private val syncScheduler: SyncScheduler
) {
    private val guestId = BragaConstants.GUEST_UID

    fun getBiometry(userId: String): Flow<List<BiometryEntity>> = biometryDao.getAll(userId)

    suspend fun saveBiometry(biometry: RemoteBiometry) {
        val biometryWithId = if (biometry.id == null) biometry.copy(id = UUID.randomUUID().toString()) else biometry
        // AUD-AN39: era pendingSync = false — a biometria era gravada como
        // "sincronizada" sem nunca subir ao Postgres (BiometryDao.getPendingSync()
        // e syncBiometry sequer existem). Agora entra na fila do SyncWorker;
        // remoteId continua null até o servidor confirmar (antes um UUID local
        // era usado como remoteId, fingindo sincronização).
        val entity = biometryWithId.toEntity().copy(pendingSync = true)
        biometryDao.insert(entity)
        triggerSync()
    }

    private fun triggerSync() {
        syncScheduler.scheduleSync()
    }
}
