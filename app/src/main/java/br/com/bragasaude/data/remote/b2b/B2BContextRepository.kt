package br.com.bragasaude.data.remote.b2b

import br.com.bragasaude.data.local.b2b.B2BContextCacheEntity
import br.com.bragasaude.data.local.b2b.B2BContextDao
import br.com.bragasaude.data.remote.api.BragaApiClient
import br.com.bragasaude.domain.b2b.B2BContextDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Orquestra os contextos B2B entre o cache isolado SQLCipher ([B2BContextDao]),
 * o estado reativo em memoria ([ActiveContextStore]) e a API Rust do gateway.
 */
@Singleton
class B2BContextRepository @Inject constructor(
    private val apiClient: BragaApiClient,
    private val activeContextStore: ActiveContextStore,
    private val b2bContextDao: B2BContextDao
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun hydrateFromCache(userId: String) = withContext(Dispatchers.IO) {
        val cleanUid = userId.trim()
        if (cleanUid.isEmpty()) return@withContext
        val cached = b2bContextDao.getContextsForUser(cleanUid)
        if (cached.isNotEmpty()) {
            val descriptors = cached.map { it.toDescriptor(json) }
            activeContextStore.updateAvailableContexts(cleanUid, descriptors)
            val activeEntity = cached.firstOrNull { it.isActive }
            if (activeEntity != null) {
                activeContextStore.selectContext(cleanUid, activeEntity.toDescriptor(json))
            }
        }
    }

    suspend fun syncContexts(userId: String): List<B2BContextDescriptor> =
        withContext(Dispatchers.IO) {
            val cleanUid = userId.trim()
            if (cleanUid.isEmpty()) return@withContext emptyList()
            val remote = apiClient.getB2BContexts()
            if (remote.isEmpty()) {
                return@withContext activeContextStore.availableContexts.value
            }
            activeContextStore.updateAvailableContexts(cleanUid, remote)
            val currentActiveKey = activeContextStore.activeContext.value.cacheKey()
            val entities = activeContextStore.availableContexts.value.map { descriptor ->
                B2BContextCacheEntity.fromDescriptor(
                    ownerUserId = cleanUid,
                    descriptor = descriptor,
                    isActive = descriptor.cacheKey() == currentActiveKey,
                    json = json
                )
            }
            b2bContextDao.replaceUserContexts(cleanUid, entities)
            activeContextStore.availableContexts.value
        }

    suspend fun selectContext(
        userId: String,
        target: B2BContextDescriptor
    ): B2BContextDescriptor? = withContext(Dispatchers.IO) {
        val cleanUid = userId.trim()
        if (cleanUid.isEmpty()) return@withContext null
        val authoritative = apiClient.selectB2BContext(target.parsedMode, target.programId)
            ?: return@withContext null
        val syncedList = buildList {
            addAll(activeContextStore.availableContexts.value.filter {
                it.cacheKey() != authoritative.cacheKey()
            })
            if (authoritative.isPartnerContext) add(authoritative)
        }
        activeContextStore.updateAvailableContexts(cleanUid, syncedList)
        activeContextStore.selectContext(cleanUid, authoritative)

        b2bContextDao.clearActiveFlags(cleanUid)
        b2bContextDao.markActive(cleanUid, authoritative.cacheKey())
        authoritative
    }

    suspend fun clearOnSignOut() = withContext(Dispatchers.IO) {
        activeContextStore.resetToPersonal()
        b2bContextDao.clearAll()
    }
}
