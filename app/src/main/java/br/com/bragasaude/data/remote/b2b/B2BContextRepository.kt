package br.com.bragasaude.data.remote.b2b

import br.com.bragasaude.data.local.b2b.B2BContextCacheEntity
import br.com.bragasaude.data.local.b2b.B2BContextDao
import br.com.bragasaude.data.local.b2b.B2BDirectMessageEntity
import br.com.bragasaude.data.remote.api.BragaApiClient
import br.com.bragasaude.domain.b2b.AppAccountMode
import br.com.bragasaude.domain.b2b.B2BContextDescriptor
import br.com.bragasaude.domain.b2b.DirectChatMessageDto
import br.com.bragasaude.domain.b2b.DirectChatThreadPageDto
import br.com.bragasaude.domain.b2b.UpdateAccountModeResult
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
            val envelope = apiClient.getB2BContextsEnvelope()
                ?: return@withContext activeContextStore.availableContexts.value
            activeContextStore.updateFromEnvelope(cleanUid, envelope)
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

    suspend fun updateAccountMode(
        userId: String,
        target: AppAccountMode,
        enterBusinessSession: Boolean? = null
    ): UpdateAccountModeResult = withContext(Dispatchers.IO) {
        val cleanUid = userId.trim()
        val result = apiClient.updateAccountMode(target)
        if (result.isSuccess) {
            val resolved = AppAccountMode.fromWire(result.accountMode)
            activeContextStore.updateAccountMode(resolved, enterBusinessSession)
            if (cleanUid.isNotEmpty()) {
                syncContexts(cleanUid)
            }
        }
        result
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

    suspend fun cacheDirectMessages(
        userId: String,
        messages: List<DirectChatMessageDto>,
        programName: String,
        primaryColor: String?,
        patientName: String? = null,
        isClosedProgram: Boolean = false
    ) = withContext(Dispatchers.IO) {
        val cleanUid = userId.trim()
        if (cleanUid.isEmpty() || messages.isEmpty()) return@withContext
        val entities = messages.map { msg ->
            B2BDirectMessageEntity.fromDto(
                ownerUserId = cleanUid,
                dto = msg,
                programName = programName,
                primaryColor = primaryColor,
                patientName = patientName,
                isClosedProgram = isClosedProgram
            )
        }
        b2bContextDao.upsertDirectMessages(entities)
    }

    suspend fun cacheAndMergeDirectChatPage(
        userId: String,
        page: DirectChatThreadPageDto,
        targetPatientId: String,
        primaryColor: String?,
        patientName: String? = null
    ): DirectChatThreadPageDto = withContext(Dispatchers.IO) {
        val cleanUid = userId.trim()
        val effectivePatientId = page.thread?.patientUserId?.takeIf { it.isNotBlank() }
            ?: page.messages.firstOrNull()?.patientUserId?.takeIf { it.isNotBlank() }
            ?: targetPatientId.trim()
        if (cleanUid.isEmpty() || effectivePatientId.isEmpty()) return@withContext page

        if (page.messages.isNotEmpty()) {
            cacheDirectMessages(
                userId = cleanUid,
                messages = page.messages,
                programName = page.programName,
                primaryColor = primaryColor,
                patientName = patientName
            )
        }
        val cached = b2bContextDao.getDirectMessagesForThread(
            ownerUserId = cleanUid,
            programId = page.programId,
            patientUserId = effectivePatientId
        ).map { it.toDto() }

        page.copy(messages = mergeDirectMessages(page.messages, cached))
    }

    suspend fun loadOfflineDirectChatPage(
        userId: String,
        programId: String,
        programName: String,
        patientUserId: String
    ): DirectChatThreadPageDto? = withContext(Dispatchers.IO) {
        val cleanUid = userId.trim()
        val cleanProg = programId.trim()
        val cleanPatient = patientUserId.trim()
        if (cleanUid.isEmpty() || cleanProg.isEmpty() || cleanPatient.isEmpty()) return@withContext null
        val rows = b2bContextDao.getDirectMessagesForThread(cleanUid, cleanProg, cleanPatient)
        if (rows.isEmpty()) return@withContext null
        DirectChatThreadPageDto(
            programId = cleanProg,
            programName = rows.firstOrNull()?.programName?.takeIf { it.isNotBlank() } ?: programName,
            messages = rows.map { it.toDto() },
            hasMore = false
        )
    }

    suspend fun getAllDirectMessagesForBackup(userId: String): List<B2BDirectMessageEntity> =
        withContext(Dispatchers.IO) {
            val cleanUid = userId.trim()
            if (cleanUid.isEmpty()) return@withContext emptyList()
            b2bContextDao.getAllDirectMessagesForUser(cleanUid)
        }

    suspend fun restoreDirectMessagesFromBackup(entities: List<B2BDirectMessageEntity>) =
        withContext(Dispatchers.IO) {
            if (entities.isNotEmpty()) b2bContextDao.upsertDirectMessages(entities)
        }

    suspend fun clearOnSignOut() = withContext(Dispatchers.IO) {
        activeContextStore.resetToPersonal()
        b2bContextDao.clearAll()
        b2bContextDao.clearAllDirectMessages()
    }

    companion object {
        fun mergeDirectMessages(
            serverMessages: List<DirectChatMessageDto>,
            cachedMessages: List<DirectChatMessageDto>
        ): List<DirectChatMessageDto> {
            if (serverMessages.isEmpty()) return cachedMessages
            if (cachedMessages.isEmpty()) return serverMessages
            val byId = LinkedHashMap<String, DirectChatMessageDto>()
            cachedMessages.forEach { byId[it.messageId] = it }
            serverMessages.forEach { byId[it.messageId] = it }
            return byId.values.sortedWith(
                compareByDescending<DirectChatMessageDto> {
                    B2BDirectMessageEntity.parseIsoMillisOrNow(it.createdAt)
                }.thenByDescending { it.messageId }
            )
        }
    }
}
