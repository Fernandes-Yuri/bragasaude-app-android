package br.com.bragasaude.data.remote.repository

import br.com.bragasaude.data.local.FamilyDao
import br.com.bragasaude.data.local.MedicationDao
import br.com.bragasaude.data.local.NotificationDao
import br.com.bragasaude.data.local.NotificationItemEntity
import br.com.bragasaude.data.local.NotificationSyncStateEntity
import br.com.bragasaude.data.local.ProfileDao
import br.com.bragasaude.data.local.SocialFeedDao
import br.com.bragasaude.data.remote.api.BragaApiClient
import br.com.bragasaude.ui.util.Screen
import br.com.bragasaude.util.BragaConstants
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

enum class NotificationCategoryFilter(val apiValue: String, val label: String) {
    ALL("", "Todas"),
    FAMILY("FAMILY", "Família"),
    AGENDA("AGENDA", "Agenda"),
    COMMUNITY("COMMUNITY", "Comunidade"),
    CARE("CARE", "Cuidado")
}

sealed interface NotificationNavigationResult {
    data class Navigate(val screen: Screen) : NotificationNavigationResult
    data class Unavailable(val message: String) : NotificationNavigationResult
}

data class NotificationDisplayItem(
    val id: String,
    val revision: Long,
    val kind: String,
    val category: String,
    val categoryLabel: String,
    val title: String,
    val body: String,
    val personLabel: String?,
    val occurredAt: Long,
    val count: Int,
    val unreadCount: Int,
    val isRead: Boolean,
    val patientId: String?,
    val resourceType: String,
    val resourceId: String,
    val isLocalAlert: Boolean = false,
    val rawLocalAlertKey: String? = null
)

@Singleton
class NotificationCenterRepository(
    private val notificationDao: NotificationDao,
    private val familyDao: FamilyDao,
    private val profileDao: ProfileDao,
    private val socialFeedDao: SocialFeedDao,
    private val medicationDao: MedicationDao,
    private val apiClient: BragaApiClient,
    private val auth: FirebaseAuth,
    private val clock: () -> Long = { System.currentTimeMillis() }
) {
    @Inject
    constructor(
        notificationDao: NotificationDao,
        familyDao: FamilyDao,
        profileDao: ProfileDao,
        socialFeedDao: SocialFeedDao,
        medicationDao: MedicationDao,
        apiClient: BragaApiClient,
        auth: FirebaseAuth
    ) : this(
        notificationDao = notificationDao,
        familyDao = familyDao,
        profileDao = profileDao,
        socialFeedDao = socialFeedDao,
        medicationDao = medicationDao,
        apiClient = apiClient,
        auth = auth,
        clock = { System.currentTimeMillis() }
    )

    companion object {
        const val DEFAULT_PAGE_SIZE = 20

        fun formatBadgeCount(unreadCount: Int): String? = when {
            unreadCount <= 0 -> null
            unreadCount > 99 -> "99+"
            else -> unreadCount.toString()
        }

        fun badgeContentDescription(unreadCount: Int): String = when {
            unreadCount <= 0 -> "Notificações, sem avisos não lidos"
            unreadCount == 1 -> "Notificações, 1 aviso não lido"
            unreadCount > 99 -> "Notificações, mais de 99 avisos não lidos"
            else -> "Notificações, $unreadCount avisos não lidos"
        }

        fun categoryDisplayLabel(category: String): String = when (category.uppercase()) {
            "FAMILY" -> "Família"
            "AGENDA" -> "Agenda"
            "COMMUNITY" -> "Comunidade"
            "CARE" -> "Cuidado"
            else -> "Cuidado"
        }
    }

    private val syncLock = Mutex()

    private fun currentAuthenticatedUserId(): String? =
        auth.currentUser?.uid?.takeIf { it.isNotBlank() && it != BragaConstants.GUEST_UID }

    fun observeActiveItems(
        recipientId: String,
        filter: NotificationCategoryFilter = NotificationCategoryFilter.ALL
    ): Flow<List<NotificationItemEntity>> {
        if (recipientId.isBlank() || recipientId == BragaConstants.GUEST_UID) {
            return flowOf(emptyList())
        }
        return notificationDao.observeActiveItems(
            recipientId = recipientId,
            category = filter.apiValue,
            nowMillis = 0L
        )
    }

    fun observeUnreadGroupCount(recipientId: String): Flow<Int> {
        if (recipientId.isBlank() || recipientId == BragaConstants.GUEST_UID) {
            return flowOf(0)
        }
        return notificationDao.observeUnreadGroupCount(recipientId, nowMillis = 0L)
    }

    fun observeSyncState(recipientId: String): Flow<NotificationSyncStateEntity?> {
        if (recipientId.isBlank() || recipientId == BragaConstants.GUEST_UID) {
            return flowOf(null)
        }
        return notificationDao.observeSyncState(recipientId)
    }

    /**
     * Sincroniza a caixa persistente do usuário autenticado:
     * 1. Expurga itens vencidos.
     * 2. Envia confirmações pendentes de leitura/arquivamento (offline retry).
     * 3. Na primeira sincronização (ou quando forçado sem cursor), baixa o snapshot inicial.
     * 4. Nas sincronizações seguintes, aplica o feed incremental `/api/notifications/changes`.
     */
    suspend fun syncNotifications(forceSnapshotIfEmpty: Boolean = false): Result<Unit> = runCatching {
        val userId = currentAuthenticatedUserId() ?: return@runCatching
        syncLock.withLock {
            if (currentAuthenticatedUserId() != userId) {
                throw CancellationException("Sessão alterada antes de sincronizar notificações.")
            }
            val now = clock()
            notificationDao.purgeExpired(now)
            flushPendingAcksInternal(userId)

            val stateBefore = notificationDao.getSyncState(userId)
            val localItems = notificationDao.getActiveItemsOneShot(userId, now)
            val needInitialSnapshot = stateBefore == null ||
                stateBefore.cursorRevision <= 0L ||
                (forceSnapshotIfEmpty && localItems.isEmpty())

            if (needInitialSnapshot) {
                val page = apiClient.getNotifications(
                    recipientId = userId,
                    cursor = null,
                    limit = DEFAULT_PAGE_SIZE
                ) ?: throw java.io.IOException("Não foi possível atualizar a central de notificações.")
                check(currentAuthenticatedUserId() == userId) { "Sessão alterada durante sincronização." }

                val currentState = notificationDao.getSyncState(userId)
                val pendingMarkAll = currentState?.pendingMarkAllReadThroughRevision
                for (item in page.items) {
                    if (item.expiresAt > now) {
                        notificationDao.applyRemoteItem(item, pendingMarkAll)
                    }
                }
                val newCursorRev = maxOf(
                    currentState?.cursorRevision ?: 0L,
                    page.snapshotRevision,
                    page.latestRevision
                )
                notificationDao.upsertSyncState(
                    NotificationSyncStateEntity(
                        recipientId = userId,
                        cursorRevision = newCursorRev,
                        latestRevision = maxOf(currentState?.latestRevision ?: 0L, page.latestRevision),
                        nextPageCursor = page.nextCursor,
                        hasMorePages = page.hasMore,
                        pendingMarkAllReadThroughRevision = pendingMarkAll,
                        lastSyncedAt = clock()
                    )
                )
            } else {
                var cursor = stateBefore.cursorRevision
                var latestRev = stateBefore.latestRevision
                var safetyPages = 0
                while (safetyPages < 10) {
                    safetyPages++
                    val delta = apiClient.getNotificationChanges(
                        recipientId = userId,
                        afterRevision = cursor,
                        limit = 100
                    ) ?: throw java.io.IOException("Não foi possível sincronizar alterações de notificações.")
                    check(currentAuthenticatedUserId() == userId) { "Sessão alterada durante sincronização incremental." }

                    val currentState = notificationDao.getSyncState(userId)
                    val pendingMarkAll = currentState?.pendingMarkAllReadThroughRevision
                    for (change in delta.changes) {
                        if (change.item.deleted) {
                            notificationDao.applyRemoteItem(change.item, pendingMarkAll)
                        } else if (change.item.expiresAt > now) {
                            notificationDao.applyRemoteItem(change.item, pendingMarkAll)
                        }
                    }
                    cursor = maxOf(cursor, delta.nextCursor)
                    latestRev = maxOf(latestRev, delta.latestRevision, cursor)
                    notificationDao.upsertSyncState(
                        (currentState ?: stateBefore).copy(
                            cursorRevision = cursor,
                            latestRevision = latestRev,
                            lastSyncedAt = clock()
                        )
                    )
                    if (!delta.hasMore || delta.changes.isEmpty()) break
                }
            }
        }
    }.onFailure { if (it is CancellationException) throw it }

    /**
     * Carrega explicitamente a próxima página por cursor sem duplicar itens já existentes.
     */
    suspend fun loadMore(filter: NotificationCategoryFilter = NotificationCategoryFilter.ALL): Result<Boolean> = runCatching {
        val userId = currentAuthenticatedUserId() ?: return@runCatching false
        syncLock.withLock {
            val state = notificationDao.getSyncState(userId) ?: return@withLock false
            val cursor = state.nextPageCursor?.takeIf { it.isNotBlank() } ?: return@withLock false
            val page = apiClient.getNotifications(
                recipientId = userId,
                cursor = cursor,
                limit = DEFAULT_PAGE_SIZE,
                category = filter.apiValue.takeIf { it.isNotBlank() }
            ) ?: throw java.io.IOException("Não foi possível carregar mais avisos.")
            check(currentAuthenticatedUserId() == userId) { "Sessão alterada durante paginação." }

            val now = clock()
            for (item in page.items) {
                if (item.expiresAt > now) {
                    notificationDao.applyRemoteItem(item, state.pendingMarkAllReadThroughRevision)
                }
            }
            notificationDao.upsertSyncState(
                state.copy(
                    latestRevision = maxOf(state.latestRevision, page.latestRevision),
                    nextPageCursor = page.nextCursor,
                    hasMorePages = page.hasMore,
                    lastSyncedAt = now
                )
            )
            page.hasMore
        }
    }.onFailure { if (it is CancellationException) throw it }

    /**
     * Marca um item/grupo da central como lido.
     * IMPORTANTE (§10.222): Ler um aviso na central NUNCA emite recibo de leitura de mensagem do chat.
     */
    suspend fun markItemRead(itemId: String): Boolean {
        val userId = currentAuthenticatedUserId() ?: return false
        val now = clock()
        val updated = notificationDao.markItemReadLocal(userId, itemId, now) ?: return false
        val ackRev = updated.pendingReadThroughRevision ?: updated.revision
        val remote = apiClient.markNotificationRead(
            itemId = itemId,
            throughRevision = updated.revision.takeIf { it > 0L },
            archive = false
        )
        if (remote != null && currentAuthenticatedUserId() == userId) {
            notificationDao.clearPendingReadAck(userId, itemId, ackRev)
            val state = notificationDao.getSyncState(userId)
            if (state != null) {
                notificationDao.upsertSyncState(
                    state.copy(latestRevision = maxOf(state.latestRevision, remote.latestRevision))
                )
            }
            return true
        }
        return false
    }

    /**
     * Marca como lidas todas as notificações até a revisão atual (`throughRevision`).
     * Eventos que chegarem com revisão maior permanecem não lidos (§10.221, §10.241).
     */
    suspend fun markAllRead(explicitThroughRevision: Long? = null): Boolean {
        val userId = currentAuthenticatedUserId() ?: return false
        val now = clock()
        val state = notificationDao.getSyncState(userId)
        val maxLocalRev = notificationDao.getMaxLocalRevision(userId)
        val watermark = explicitThroughRevision
            ?: maxOf(state?.latestRevision ?: 0L, state?.cursorRevision ?: 0L, maxLocalRev)
        if (watermark <= 0L) return true

        notificationDao.markAllReadThroughRevisionLocal(userId, watermark, now)
        val remote = apiClient.markAllNotificationsRead(watermark)
        if (remote != null && currentAuthenticatedUserId() == userId) {
            val latestState = notificationDao.getSyncState(userId)
            if (latestState != null && latestState.pendingMarkAllReadThroughRevision == watermark) {
                notificationDao.upsertSyncState(
                    latestState.copy(
                        latestRevision = maxOf(latestState.latestRevision, remote.latestRevision),
                        pendingMarkAllReadThroughRevision = null
                    )
                )
            }
            return true
        }
        return false
    }

    /**
     * Arquiva um aviso da central sem apagar consulta, publicação ou histórico clínico (§10.219).
     */
    suspend fun archiveItem(itemId: String): Boolean {
        val userId = currentAuthenticatedUserId() ?: return false
        val now = clock()
        val updated = notificationDao.markItemArchivedLocal(userId, itemId, now) ?: return false
        val ackRev = updated.pendingArchiveThroughRevision ?: updated.revision
        val remote = apiClient.archiveNotification(
            itemId = itemId,
            throughRevision = updated.revision.takeIf { it > 0L }
        )
        if (remote != null && currentAuthenticatedUserId() == userId) {
            notificationDao.clearPendingArchiveAck(userId, itemId, ackRev)
            notificationDao.clearPendingReadAck(userId, itemId, updated.pendingReadThroughRevision ?: ackRev)
            return true
        }
        return false
    }

    /**
     * Reconcilia o aviso de mensagem familiar quando o chat real já confirmou leitura das mensagens
     * visíveis, evitando que o contador do sininho fique preso (§10.222, §10.239).
     */
    suspend fun reconcileRealChatRead(patientUserId: String) {
        val userId = currentAuthenticatedUserId() ?: return
        if (patientUserId.isBlank()) return
        notificationDao.reconcileChatConversationReadLocal(userId, patientUserId, clock())
    }

    /**
     * Remove imediatamente avisos clínicos/agenda/chat de um paciente cujo vínculo foi revogado,
     * preservando apenas o eventual aviso informativo de revogação (§10.204, §10.223).
     */
    suspend fun purgeRevokedPatientNotifications(patientUserId: String) {
        val userId = currentAuthenticatedUserId() ?: return
        if (patientUserId.isBlank()) return
        notificationDao.deleteForRevokedPatient(userId, patientUserId)
    }

    /**
     * Remove avisos originados por um usuário bloqueado no mural (§10.203, §10.240).
     */
    suspend fun purgeBlockedUserNotifications(blockedUserId: String) {
        val userId = currentAuthenticatedUserId() ?: return
        if (blockedUserId.isBlank()) return
        notificationDao.deleteForBlockedActor(userId, blockedUserId)
    }

    /**
     * Envia confirmações de leitura/arquivamento feitas enquanto o aparelho estava offline.
     */
    suspend fun flushPendingAcks() {
        val userId = currentAuthenticatedUserId() ?: return
        syncLock.withLock {
            flushPendingAcksInternal(userId)
        }
    }

    private suspend fun flushPendingAcksInternal(userId: String) {
        if (currentAuthenticatedUserId() != userId) return
        val state = notificationDao.getSyncState(userId)
        val pendingMarkAll = state?.pendingMarkAllReadThroughRevision
        if (pendingMarkAll != null && pendingMarkAll > 0L) {
            val res = apiClient.markAllNotificationsRead(pendingMarkAll)
            if (res != null && currentAuthenticatedUserId() == userId) {
                val current = notificationDao.getSyncState(userId)
                if (current != null && current.pendingMarkAllReadThroughRevision == pendingMarkAll) {
                    notificationDao.upsertSyncState(
                        current.copy(
                            latestRevision = maxOf(current.latestRevision, res.latestRevision),
                            pendingMarkAllReadThroughRevision = null
                        )
                    )
                }
            }
        }

        val pendingItems = notificationDao.getPendingAckItems(userId)
        for (item in pendingItems) {
            if (currentAuthenticatedUserId() != userId) return
            val archiveRev = item.pendingArchiveThroughRevision
            val readRev = item.pendingReadThroughRevision
            if (archiveRev != null) {
                val res = apiClient.archiveNotification(
                    itemId = item.id,
                    throughRevision = archiveRev.takeIf { it > 0L && it != Long.MAX_VALUE }
                )
                if (res != null && currentAuthenticatedUserId() == userId) {
                    notificationDao.clearPendingArchiveAck(userId, item.id, archiveRev)
                    if (readRev != null) {
                        notificationDao.clearPendingReadAck(userId, item.id, readRev)
                    }
                }
            } else if (readRev != null) {
                val res = apiClient.markNotificationRead(
                    itemId = item.id,
                    throughRevision = readRev.takeIf { it > 0L && it != Long.MAX_VALUE },
                    archive = false
                )
                if (res != null && currentAuthenticatedUserId() == userId) {
                    notificationDao.clearPendingReadAck(userId, item.id, readRev)
                }
            }
        }
    }

    /**
     * Ao tocar em um item da central:
     * 1. Marca o item como lido.
     * 2. Revalida o acesso atual à conversa, consulta, publicação ou medicamento (§10.231).
     * 3. Impede que um aviso antigo conceda acesso a dados de vínculo revogado ou post removido.
     */
    suspend fun openItemAndResolveNavigation(item: NotificationItemEntity): NotificationNavigationResult {
        val userId = currentAuthenticatedUserId()
            ?: return NotificationNavigationResult.Unavailable("Entre na sua conta para abrir este aviso.")
        if (item.recipientId != userId) {
            return NotificationNavigationResult.Unavailable("Este aviso pertence a outra sessão.")
        }

        val now = clock()
        if (item.expiresAt <= now || item.deleted) {
            return NotificationNavigationResult.Unavailable("Este aviso expirou e não está mais disponível.")
        }

        markItemRead(item.id)

        val targetPatientId = item.patientId?.takeIf { it.isNotBlank() }
        val requiresFamilyAccess = targetPatientId != null && targetPatientId != userId

        if (item.kind == "FAMILY_BINDING_REVOKED") {
            if (targetPatientId != null && targetPatientId != userId) {
                notificationDao.deleteForRevokedPatient(userId, targetPatientId)
            }
            return NotificationNavigationResult.Unavailable(
                "O vínculo familiar foi encerrado. Os dados clínicos desta pessoa não estão mais disponíveis."
            )
        }

        if (requiresFamilyAccess && targetPatientId != null) {
            val activeLink = familyDao.existsActiveLink(targetPatientId, userId)
            if (activeLink == null) {
                notificationDao.deleteForRevokedPatient(userId, targetPatientId)
                return NotificationNavigationResult.Unavailable(
                    "Você não tem mais acesso ativo aos dados deste familiar."
                )
            }
        }

        return when (item.kind) {
            "FAMILY_MESSAGE" -> {
                val chatPatientId = targetPatientId ?: item.resourceId.takeIf { it.isNotBlank() } ?: userId
                if (chatPatientId != userId && familyDao.existsActiveLink(chatPatientId, userId) == null) {
                    notificationDao.deleteForRevokedPatient(userId, chatPatientId)
                    NotificationNavigationResult.Unavailable("O vínculo familiar desta conversa não está mais ativo.")
                } else {
                    NotificationNavigationResult.Navigate(Screen.FamilyChat(patientId = chatPatientId))
                }
            }
            "CONSULTATION_CREATED",
            "CONSULTATION_ACCEPTED",
            "CONSULTATION_REJECTED",
            "CONSULTATION_CANCELLED",
            "CONSULTATION_COMPLETED",
            "CONSULTATION_UPDATED" -> {
                val navPatientId = if (targetPatientId == null || targetPatientId == userId) "" else targetPatientId
                NotificationNavigationResult.Navigate(
                    Screen.Consultations(
                        patientId = navPatientId,
                        consultationId = item.resourceId
                    )
                )
            }
            "POST_REACTION" -> {
                val data = runCatching { JSONObject(item.dataJson) }.getOrNull()
                if (data?.optBoolean("postDeleted", false) == true) {
                    return NotificationNavigationResult.Unavailable("Esta publicação foi removida e não está mais disponível.")
                }
                val actorId = item.actorId
                if (!actorId.isNullOrBlank() && socialFeedDao.getBlocks(userId).any { it.blockedUserId == actorId }) {
                    return NotificationNavigationResult.Unavailable("O autor desta interação está bloqueado.")
                }
                val localPost = socialFeedDao.getPostById(item.resourceId)
                if (localPost != null && (!localPost.isVisible || localPost.pendingMutation == "DELETE")) {
                    NotificationNavigationResult.Unavailable("Esta publicação foi removida e não está mais disponível.")
                } else {
                    NotificationNavigationResult.Navigate(Screen.SocialFeed)
                }
            }
            "FAMILY_BINDING_ACCEPTED" -> {
                if (targetPatientId != null && targetPatientId != userId) {
                    NotificationNavigationResult.Navigate(Screen.CaregiverDashboard)
                } else {
                    NotificationNavigationResult.Navigate(Screen.FamilyConnect)
                }
            }
            "MEDICATION_LOW_STOCK" -> {
                NotificationNavigationResult.Navigate(Screen.MedicationStock)
            }
            "MEDICATION_REMINDER" -> {
                NotificationNavigationResult.Navigate(Screen.Reminders)
            }
            else -> {
                NotificationNavigationResult.Navigate(Screen.Vitals())
            }
        }
    }

    /**
     * Formata um item persistido para apresentação clara em português, resolvendo nomes locais
     * de titulares/atores sem persistir conteúdo clínico sensível ou texto de chat cifrado.
     */
    suspend fun toDisplayItem(item: NotificationItemEntity): NotificationDisplayItem {
        val userId = currentAuthenticatedUserId() ?: item.recipientId
        val data = runCatching { JSONObject(item.dataJson) }.getOrNull() ?: JSONObject()

        val patientName = resolvePersonName(item.patientId, userId, data.optString("patientName"))
        val actorName = resolvePersonName(item.actorId, userId, data.optString("actorName"))
        val consultationTitle = familyDao.getConsultationById(item.resourceId)?.title
            ?: data.optString("title").takeIf { it.isNotBlank() }

        val holderContext = when {
            item.patientId.isNullOrBlank() -> null
            item.patientId == userId -> "Você"
            !patientName.isNullOrBlank() -> patientName
            else -> "Familiar acompanhado"
        }

        val (title, body) = when (item.kind) {
            "FAMILY_MESSAGE" -> {
                val msgTitle = if (item.count > 1) {
                    "${item.count} mensagens da família"
                } else {
                    if (!actorName.isNullOrBlank() && actorName != "Você") {
                        "Nova mensagem de $actorName"
                    } else {
                        "Nova mensagem da família"
                    }
                }
                val msgBody = if (holderContext != null && holderContext != "Você") {
                    "Conversa de cuidado de $holderContext. Toque para abrir."
                } else {
                    "Toque para abrir a conversa da família."
                }
                msgTitle to msgBody
            }
            "CONSULTATION_CREATED" -> {
                val who = if (holderContext != null && holderContext != "Você") " para $holderContext" else " na sua agenda"
                val t = "Nova consulta agendada$who"
                val b = buildString {
                    if (!consultationTitle.isNullOrBlank()) {
                        append(consultationTitle)
                        append(" · ")
                    }
                    append("Toque para abrir os detalhes na agenda.")
                }
                t to b
            }
            "CONSULTATION_ACCEPTED" -> {
                val who = holderContext?.let { " ($it)" }.orEmpty()
                "Consulta confirmada$who" to (consultationTitle ?: "O compromisso foi aceito. Toque para ver na agenda.")
            }
            "CONSULTATION_REJECTED" -> {
                val who = holderContext?.let { " ($it)" }.orEmpty()
                "Consulta recusada$who" to (consultationTitle ?: "O compromisso não pôde ser confirmado. Toque para revisar.")
            }
            "CONSULTATION_CANCELLED" -> {
                val who = holderContext?.let { " ($it)" }.orEmpty()
                "Consulta cancelada$who" to (consultationTitle ?: "O compromisso foi cancelado. Toque para ver a agenda.")
            }
            "CONSULTATION_COMPLETED" -> {
                val who = holderContext?.let { " ($it)" }.orEmpty()
                "Consulta concluída$who" to (consultationTitle ?: "O compromisso foi marcado como realizado.")
            }
            "CONSULTATION_UPDATED" -> {
                val who = holderContext?.let { " ($it)" }.orEmpty()
                "Compromisso atualizado$who" to (consultationTitle ?: "Data, horário ou detalhes da consulta foram atualizados.")
            }
            "POST_REACTION" -> {
                val reactionRaw = data.optString("reaction").lowercase()
                val reactionLabel = when (reactionRaw) {
                    "palmas", "clap" -> "palmas"
                    "forca", "força", "strength" -> "força"
                    "coracao", "coração", "heart", "love" -> "carinho"
                    else -> "apoio"
                }
                val t = if (item.count > 1) {
                    "${item.count} reações na sua publicação"
                } else if (!actorName.isNullOrBlank() && actorName != "Você") {
                    "$actorName enviou $reactionLabel na sua publicação"
                } else {
                    "Sua publicação recebeu $reactionLabel no mural"
                }
                t to "Toque para abrir a sua publicação no mural da comunidade."
            }
            "FAMILY_BINDING_ACCEPTED" -> {
                val person = holderContext?.takeIf { it != "Você" } ?: actorName
                val t = if (!person.isNullOrBlank()) "Vínculo familiar ativo com $person" else "Vínculo familiar conectado"
                t to "O compartilhamento autorizado da família está ativo."
            }
            "FAMILY_BINDING_REVOKED" -> {
                "Vínculo familiar encerrado" to "O acesso compartilhado deste vínculo foi revogado."
            }
            "MEDICATION_LOW_STOCK" -> {
                val medName = medicationDao.getById(item.resourceId)?.name
                    ?: data.optString("medicationName").takeIf { it.isNotBlank() }
                val units = data.optInt("currentUnits", -1)
                val t = if (!medName.isNullOrBlank()) "Estoque baixo: $medName" else "Estoque de medicamento baixo"
                val b = if (units >= 0) {
                    "Restam $units unidades. Toque para conferir e registrar reposição."
                } else {
                    "Restam poucas unidades. Toque para conferir o estoque."
                }
                t to b
            }
            "MEDICATION_REMINDER" -> {
                val medName = medicationDao.getById(item.resourceId)?.name
                    ?: data.optString("medicationName").takeIf { it.isNotBlank() }
                val t = if (!medName.isNullOrBlank()) "Dose de $medName" else "Lembrete de medicamento"
                t to "Toque para conferir os horários e registrar a dose."
            }
            else -> {
                val customTitle = data.optString("title").takeIf { it.isNotBlank() } ?: "Aviso de cuidado"
                val customBody = data.optString("summary").takeIf { it.isNotBlank() }
                    ?: "Toque para conferir os detalhes do seu acompanhamento."
                customTitle to customBody
            }
        }

        return NotificationDisplayItem(
            id = item.id,
            revision = item.revision,
            kind = item.kind,
            category = item.category,
            categoryLabel = categoryDisplayLabel(item.category),
            title = title,
            body = body,
            personLabel = holderContext,
            occurredAt = item.occurredAt,
            count = item.count,
            unreadCount = item.unreadCount,
            isRead = item.isRead,
            patientId = item.patientId,
            resourceType = item.resourceType,
            resourceId = item.resourceId,
            isLocalAlert = false
        )
    }

    private suspend fun resolvePersonName(
        targetUserId: String?,
        currentUserId: String,
        fallbackFromData: String?
    ): String? {
        if (targetUserId.isNullOrBlank()) return fallbackFromData?.takeIf { it.isNotBlank() }
        if (targetUserId == currentUserId) return "Você"
        val profile = profileDao.getProfileOneShot(targetUserId)
        if (!profile?.fullName.isNullOrBlank()) return profile?.fullName
        if (!fallbackFromData.isNullOrBlank()) return fallbackFromData
        return null
    }
}
