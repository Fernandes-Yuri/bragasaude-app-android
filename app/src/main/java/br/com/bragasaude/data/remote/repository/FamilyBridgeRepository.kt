package br.com.bragasaude.data.remote.repository

import br.com.bragasaude.data.local.ConsultationEntity
import br.com.bragasaude.data.local.DailyMetricsDao
import br.com.bragasaude.data.local.DailyMetricsEntity
import br.com.bragasaude.data.local.FamilyBindingEntity
import br.com.bragasaude.data.local.FamilyDao
import br.com.bragasaude.data.local.FamilyMessageEntity
import br.com.bragasaude.data.local.ProfileDao
import br.com.bragasaude.data.local.ProfileEntity
import br.com.bragasaude.data.local.SocialPostEntity
import br.com.bragasaude.data.local.SocialFeedDao
import br.com.bragasaude.data.local.VitalSignDao
import br.com.bragasaude.data.local.VitalSignEntity
import br.com.bragasaude.data.local.security.FamilyE2eeManager
import br.com.bragasaude.data.remote.api.BragaApiClient
import br.com.bragasaude.data.remote.sync.SyncScheduler
import br.com.bragasaude.data.util.parseDate
import br.com.bragasaude.ui.util.FamilyNotificationService
import br.com.bragasaude.ui.util.NotificationHelper
import com.google.firebase.auth.FirebaseAuth
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.Dispatchers
import java.util.Date
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository do Módulo Ponte Familiar & Modo Cuidador.
 * Gerencia vínculos familiares, códigos de conexão, mensagens e sincronização via REST/PostgreSQL.
 */
@Singleton
class FamilyBridgeRepository @Inject constructor(
    private val familyDao: FamilyDao,
    private val vitalSignDao: VitalSignDao,
    private val profileDao: ProfileDao,
    private val dailyMetricsDao: DailyMetricsDao,
    private val socialFeedDao: SocialFeedDao,
    private val apiClient: BragaApiClient,
    private val auth: FirebaseAuth,
    private val syncScheduler: SyncScheduler,
    @ApplicationContext private val appContext: Context,
    private val e2eeManager: FamilyE2eeManager
) {
    constructor(
        familyDao: FamilyDao,
        vitalSignDao: VitalSignDao,
        profileDao: ProfileDao,
        dailyMetricsDao: DailyMetricsDao,
        socialFeedDao: SocialFeedDao,
        apiClient: BragaApiClient,
        auth: FirebaseAuth,
        syncScheduler: SyncScheduler,
        appContext: Context
    ) : this(
        familyDao = familyDao,
        vitalSignDao = vitalSignDao,
        profileDao = profileDao,
        dailyMetricsDao = dailyMetricsDao,
        socialFeedDao = socialFeedDao,
        apiClient = apiClient,
        auth = auth,
        syncScheduler = syncScheduler,
        appContext = appContext,
        e2eeManager = FamilyE2eeManager()
    )

    // AUD-AN22: escopo único para o ticker de purga compartilhado.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // ==================== VÍNCULOS FAMILIARES ====================

    fun generateConnectionCode(): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        return (0 until 8).map { chars[java.security.SecureRandom().nextInt(chars.length)] }.joinToString("")
    }

    suspend fun createPendingBinding(
        patientUserId: String,
        caregiverName: String,
        caregiverRelation: String,
        connectionCode: String? = null
    ): FamilyBindingEntity {
        var code: String
        if (connectionCode != null) {
            if (familyDao.getActiveOrPendingBindingByCode(connectionCode) != null) {
                throw IllegalArgumentException("Código de conexão já em uso")
            }
            code = connectionCode
        } else {
            do {
                code = generateConnectionCode()
            } while (familyDao.getActiveOrPendingBindingByCode(code) != null)
        }

        val createdAtMs = System.currentTimeMillis()
        val expiresAtMs = createdAtMs + (7 * 24 * 60 * 60 * 1000L)

        val binding = FamilyBindingEntity(
            id = UUID.randomUUID().toString(),
            patientUserId = patientUserId,
            caregiverUserId = "",
            caregiverName = caregiverName,
            caregiverRelation = caregiverRelation,
            connectionCode = code,
            status = "PENDING",
            createdAt = createdAtMs,
            expiresAt = expiresAtMs
        )

        return try {
            val remoteId = apiClient.syncFamilyBinding(binding)
            val synced = binding.copy(id = remoteId ?: binding.id, remoteId = remoteId, pendingSync = remoteId == null)
            familyDao.deletePendingBindingsByCode(code)
            familyDao.insertBinding(synced)
            if (remoteId == null) syncScheduler.scheduleSync()
            synced
        } catch (e: Exception) {
            android.util.Log.e("FamilyBridgeRepo", "Falha ao sincronizar vínculo, salvando local: ${e.message}")
            val offline = binding.copy(pendingSync = true)
            familyDao.insertBinding(offline)
            syncScheduler.scheduleSync()
            offline
        }
    }

    suspend fun getLatestPendingBinding(patientUserId: String): FamilyBindingEntity? {
        return familyDao.getLatestPendingBindingForPatient(patientUserId)
    }

    suspend fun expireOldPendingBindings(patientUserId: String) {
        familyDao.getPendingBindingsForPatient(patientUserId).forEach { revokeBinding(it.id) }
    }

    fun getActiveBindingsForPatient(patientUserId: String): Flow<List<FamilyBindingEntity>> {
        return familyDao.getActiveBindingsForPatient(patientUserId)
    }

    fun getActiveBindingsForCaregiver(caregiverUserId: String): Flow<List<FamilyBindingEntity>> {
        return familyDao.getActiveBindingsForCaregiver(caregiverUserId)
    }

    suspend fun findAndAcceptBinding(
        code: String,
        caregiverUserId: String,
        caregiverName: String,
        caregiverRelation: String
    ): FamilyBindingEntity {
        require(auth.currentUser?.uid == caregiverUserId) { "Entre na sua conta para conectar o familiar." }
        val accepted = apiClient.acceptFamilyInvitation(code, caregiverName, caregiverRelation)
        runCatching {
            val caregiverPubKey = e2eeManager.getOrCreatePublicKeyBase64(caregiverUserId)
            if (caregiverPubKey.isNotBlank()) {
                apiClient.publishFamilyE2eePublicKey(
                    bindingId = accepted.remoteId ?: accepted.id,
                    patientId = accepted.patientUserId,
                    publicKey = caregiverPubKey
                )
            }
        }
        familyDao.deletePendingBindingsByCode(accepted.connectionCode)
        familyDao.insertBinding(accepted)
        return accepted
    }

    suspend fun revokeBinding(bindingId: String) {
        val local = familyDao.getBindingById(bindingId) ?: return
        val revoked = local.copy(status = "REVOKED", pendingSync = true)
        familyDao.insertBinding(revoked)
        val remoteId = apiClient.syncFamilyBinding(revoked)
        if (remoteId != null) familyDao.markBindingSynced(bindingId, remoteId)
        else syncScheduler.scheduleSync()
        if (auth.currentUser?.uid != local.patientUserId) purgePatientDataFromCache(local.patientUserId)
        if (remoteId == null) throw java.io.IOException("Revogação salva neste aparelho, mas ainda não confirmada pelo servidor. Reconecte para concluir.")
    }

    suspend fun purgePatientDataFromCache(patientUserId: String) {
        try {
            e2eeManager.clearKeysForPatient(patientUserId)
            vitalSignDao.deleteByUserId(patientUserId)
            dailyMetricsDao.deleteByUserId(patientUserId)
            familyDao.deleteMessagesForPatient(patientUserId)
            profileDao.deleteProfile(patientUserId)
            android.util.Log.d("FamilyBridgeRepo", "Purga LGPD concluída com sucesso para dados do paciente")
        } catch (e: Exception) {
            android.util.Log.e("FamilyBridgeRepo", "Erro ao executar purga LGPD de dados médicos: ${e.message}")
        }
    }

    suspend fun deleteBinding(bindingId: String) {
        familyDao.deleteBinding(bindingId)
    }

    fun getGeneratedCodesForPatient(patientUserId: String): Flow<List<FamilyBindingEntity>> {
        return familyDao.getGeneratedCodesForPatient(patientUserId)
    }

    suspend fun cleanExpiredCodes() {
        familyDao.expireOldPendingBindings()
    }

    // ==================== SINCRONIZAÇÃO REMOTA ====================

    suspend fun syncBindingsForPatient(patientUserId: String) {
        if (patientUserId.isBlank() || patientUserId == br.com.bragasaude.util.BragaConstants.GUEST_UID) return
        try {
            val list = apiClient.getFamilyBindings(patientUserId)
            list.forEach { item ->
                if (item.status == "ACTIVE") {
                    familyDao.deletePendingBindingsByCode(item.connectionCode)
                }
                val local = familyDao.getBindingById(item.id)
                if (local?.pendingSync != true) familyDao.insertBinding(item)
                if (item.status != "ACTIVE" && item.caregiverUserId == auth.currentUser?.uid && item.patientUserId != auth.currentUser?.uid) {
                    purgePatientDataFromCache(item.patientUserId)
                }
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.e("FamilyBridgeRepo", "Falha ao baixar vínculos do paciente: ${e.message}")
        }
    }

    suspend fun syncBindingsForCaregiver(caregiverUserId: String) {
        if (caregiverUserId.isBlank() || caregiverUserId == br.com.bragasaude.util.BragaConstants.GUEST_UID) return
        try {
            val list = apiClient.getFamilyBindings(caregiverUserId)
            list.forEach { item ->
                val local = familyDao.getBindingById(item.id)
                if (local?.pendingSync != true) familyDao.insertBinding(item)
                if (item.status != "ACTIVE" && item.caregiverUserId == auth.currentUser?.uid && item.patientUserId != auth.currentUser?.uid) {
                    purgePatientDataFromCache(item.patientUserId)
                }
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.e("FamilyBridgeRepo", "Falha ao baixar vínculos do cuidador: ${e.message}")
        }
    }

    private suspend fun requireConversationAccess(patientUserId: String): String {
        val userId = auth.currentUser?.uid
        check(!userId.isNullOrBlank() && userId != br.com.bragasaude.util.BragaConstants.GUEST_UID) {
            "Entre na sua conta para conversar com a família."
        }
        check(patientUserId.isNotBlank() && (patientUserId == userId ||
            familyDao.existsActiveLink(patientUserId, userId) != null)) {
            "Vínculo indisponível. Atualize sua família."
        }
        check(auth.currentUser?.uid == userId) { "Sua conta mudou. Abra a conversa novamente." }
        return userId
    }

    suspend fun createFamilyMessage(
        patientUserId: String,
        senderName: String,
        messageText: String,
        iconType: String = "LOVE"
    ): FamilyMessageEntity {
        val userId = requireConversationAccess(patientUserId)
        val cleanText = messageText.trim()
        require(cleanText.isNotEmpty() && cleanText.length <= 2000) { "Escreva uma mensagem de até 2000 caracteres." }
        val localId = UUID.randomUUID().toString()
        val message = FamilyMessageEntity(
            id = localId,
            patientUserId = patientUserId,
            senderName = senderName,
            messageText = cleanText,
            iconType = iconType,
            isRead = false,
            sentAt = System.currentTimeMillis(),
            senderUserId = userId,
            remoteId = null,
            pendingSync = true
        )
        familyDao.insertMessage(message)

        try {
            val encryptedPayload = e2eeManager.encryptForConversation(
                senderUserId = userId,
                patientId = patientUserId,
                plaintext = cleanText
            )
            val wireMessage = message.copy(messageText = encryptedPayload)
            val remoteId = apiClient.syncFamilyMessage(wireMessage)
            check(auth.currentUser?.uid == userId) { "Sua conta mudou. Abra a conversa novamente." }
            if (remoteId != null) {
                familyDao.markMessageSynced(localId, remoteId)
                return message.copy(remoteId = remoteId, pendingSync = false)
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            check(auth.currentUser?.uid == userId) { "Sua conta mudou. Abra a conversa novamente." }
            android.util.Log.w("FamilyBridgeRepo", "Mensagem salva localmente, pendente de sync: ${e.message}")
            syncScheduler.scheduleSync()
        }
        syncScheduler.scheduleSync()
        return message
    }

    private fun decodeMessageForLocalStorage(
        currentUserId: String,
        patientUserId: String,
        msg: FamilyMessageEntity
    ): FamilyMessageEntity {
        if (!FamilyE2eeManager.isEncryptedEnvelope(msg.messageText)) return msg
        val clearText = runCatching {
            e2eeManager.decryptForConversation(
                currentUserId = currentUserId,
                patientId = patientUserId,
                rawPayload = msg.messageText,
                senderUserId = msg.senderUserId
            ).text
        }.getOrElse {
            android.util.Log.w("FamilyBridgeRepo", "Mensagem E2EE indecifrável no momento: ${it.message}")
            msg.messageText
        }
        return msg.copy(messageText = clearText)
    }

    suspend fun sendCareMessage(
        patientUserId: String,
        senderName: String,
        messageText: String,
        iconType: String = "LOVE"
    ): FamilyMessageEntity = createFamilyMessage(patientUserId, senderName, messageText, iconType)

    /**
     * Um ciclo completo de sincronização da ponte familiar (vínculos de
     * paciente + cuidador, e mensagens do chat). Devolve um snapshot imutável
     * que o ViewModel usa para detectar mudança entre ciclos — o polling só
     * reage quando algo realmente mudou, evitando tráfego e trabalho
     * redundantes quando a conversa está parada.
     *
     * Falhas individuais não abortam o ciclo: cada parte é independente e um
     * erro numa não pode impedir a outra (antes, uma exceção em bindings
     * cancelava a sincronização de mensagens inteira).
     */
    suspend fun chatCycleSnapshot(userId: String, patientId: String): ChatCycleSnapshot {
        // Vínculos: payload pequeno e barato; sincroniza a cada ciclo.
        runCatching { syncBindingsForPatient(userId) }
        runCatching { syncBindingsForCaregiver(userId) }
        // Mensagens: só há mudança real quando o fingerprint do payload muda.
        val messages = runCatching { syncFamilyMessages(patientId) }.getOrNull()
        return ChatCycleSnapshot(messagesFingerprint = messages?.getOrNull())
    }

    suspend fun syncFamilyMessages(patientUserId: String): Result<String?> = runCatching {
        val userId = requireConversationAccess(patientUserId)
        val now = System.currentTimeMillis()
        val remote = apiClient.getFamilyMessages(patientUserId)
        check(requireConversationAccess(patientUserId) == userId) { "Sua conta mudou." }
        check(remote.all { it.patientUserId == patientUserId }) { "Mensagens de outra conversa foram recusadas." }
        for (msg in remote) {
            when {
                // D47: tombstone propagado pelo outro aparelho — remover a cópia local
                msg.deletedAt != null -> familyDao.insertMessage(msg.copy(messageText = "", senderName = "", pendingSync = false))
                // D47: expirada no servidor — nunca reintroduzir; purgar cópia local se houver
                msg.expiresAt in 1..now -> familyDao.deleteMessage(msg.id)
                else -> {
                    val decodedMsg = decodeMessageForLocalStorage(userId, patientUserId, msg)
                    val local = familyDao.getMessageById(decodedMsg.id)
                    if (local?.pendingSync != true && local?.deletedAt == null) familyDao.insertMessage(decodedMsg)
                    // Fallback (doc 10 §1A.2): mensagem NOVA vinda do servidor e que
                    // não foi enviada por este aparelho → notificação local. É a rota
                    // que cobre quem não recebeu (ou não pode receber) o push FCM.
                    if (local == null && decodedMsg.senderUserId != auth.currentUser?.uid && decodedMsg.messageText.isNotBlank()) {
                        try {
                            FamilyNotificationService.notifyPatientMessage(
                                appContext,
                                decodedMsg.senderName.ifBlank { "Familiar" },
                                decodedMsg.messageText
                            )
                        } catch (e: Exception) {
                            android.util.Log.w("FamilyBridgeRepo", "Aviso ao notificar mensagem recebida: ${e.message}")
                        }
                    }
                }
            }
        }
        // D47: higiene local a cada ciclo de sincronização
        familyDao.purgeExpiredMessages(now)
        // Fingerprint do payload recebido, para o polling poder pular o próximo
        // ciclo quando a conversa não mudou (skip de ciclo idêntico).
        messagesFingerprint(remote)
    }

    /**
     * Assinatura barata do estado atual das mensagens no servidor. Não é
     * criptográfica — só precisa mudar quando o conteúdo muda. O ViewModel
     * compara com o ciclo anterior e só reage quando difere.
     */
    private fun messagesFingerprint(messages: List<FamilyMessageEntity>): String =
        messages.joinToString(separator = "|") { "${it.id}:${it.sentAt}:${it.deletedAt}" }
            .hashCode()
            .toString()

    suspend fun syncPatientDataForCaregiver(patientUserId: String): Result<Unit> = runCatching {
        // 1. Sincronizar perfil
        try {
            val p = apiClient.getProfile(patientUserId)
            if (p != null) {
                val existing = profileDao.getProfileOneShot(patientUserId)
                val updated = existing?.copy(
                    fullName = p.fullName,
                    hasDiabetes = p.hasDiabetes ?: existing.hasDiabetes,
                    hasHypertension = p.hasHypertension ?: existing.hasHypertension,
                    hydrationTargetMl = p.hydrationTargetMl,
                    stepGoal = p.stepGoal ?: existing.stepGoal,
                    weight = p.weight ?: existing.weight,
                    height = p.height ?: existing.height
                ) ?: ProfileEntity(
                    userId = p.id,
                    fullName = p.fullName,
                    hasDiabetes = p.hasDiabetes ?: false,
                    hasHypertension = p.hasHypertension ?: false,
                    hydrationTargetMl = p.hydrationTargetMl,
                    stepGoal = p.stepGoal,
                    weight = p.weight,
                    height = p.height,
                    userRole = "PATIENT"
                )
                profileDao.insert(updated)
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.w("FamilyBridgeRepo", "Aviso ao sincronizar perfil do paciente: ${e.message}")
        }

        // 2. Sincronizar sinais vitais
        try {
            val vitals = apiClient.getVitalSigns(patientUserId)
            vitals.forEach { v ->
                val measuredDate = v.measuredAt?.let { parseDate(it) } ?: Date()
                vitalSignDao.insert(
                    VitalSignEntity(
                        remoteId = v.id,
                        userId = patientUserId,
                        systolicPressure = v.systolicPressure,
                        diastolicPressure = v.diastolicPressure,
                        heartRate = v.heartRate,
                        oxygenSaturation = v.oxygenSaturation,
                        glucoseLevel = v.glucoseLevel,
                        glucoseType = v.glucoseType,
                        hydrationMl = v.hydrationMl,
                        measuredAt = measuredDate,
                        status = "recorded",
                        pendingSync = false
                    )
                )
            }
            vitalSignDao.deduplicateVitals()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.w("FamilyBridgeRepo", "Aviso ao sincronizar vitais do paciente: ${e.message}")
        }

        // 3. Sincronizar mensagens
        syncFamilyMessages(patientUserId)
    }

    fun getUnreadMessagesForPatient(patientUserId: String): Flow<List<FamilyMessageEntity>> {
        return liveMessages(familyDao.getUnreadMessagesForPatient(patientUserId))
    }

    fun getRecentMessagesForPatient(patientUserId: String, limit: Int = 10): Flow<List<FamilyMessageEntity>> {
        return liveMessages(familyDao.getRecentMessagesForPatient(patientUserId, limit))
    }

    /** Todas as mensagens familiares ativas ordenadas por data para o inbox multi-conversas (D47). */
    fun getAllActiveMessages(): Flow<List<FamilyMessageEntity>> {
        return liveMessages(familyDao.getAllActiveMessages())
    }

    suspend fun countUnreadMessages(patientUserId: String): Int {
        return liveMessages(familyDao.getUnreadMessagesForPatient(patientUserId)).first().size
    }

    suspend fun markMessageAsRead(messageId: String) {
        familyDao.markMessageAsRead(messageId)
    }

    suspend fun markAllMessagesAsRead(patientUserId: String) {
        familyDao.markAllMessagesAsRead(patientUserId)
    }

    /**
     * D47 — Exclusão da própria mensagem pelo usuário (LGPD Art. 18, VI).
     * - Nunca sincronizada (remoteId == null): remoção local definitiva.
     * - Já no servidor: tombstone imediato (some da UI na hora) + exclusão remota;
     *   em falha de rede o SyncWorker re-tenta até confirmar.
     */
    suspend fun deleteMessage(messageId: String) {
        val local = familyDao.getMessageById(messageId) ?: return
        val userId = auth.currentUser?.uid
        check(!userId.isNullOrBlank() && local.senderUserId == userId) { "Só quem enviou pode apagar a mensagem." }
        // Keep a content-free tombstone until expiry, including ambiguous/in-flight uploads.
        familyDao.softDeleteMessage(messageId)
        try {
            if (apiClient.deleteFamilyMessage(local.remoteId ?: local.id, local.patientUserId, local.sentAt)) {
                familyDao.markDeletionSynced(messageId)
            }
        } finally {
            syncScheduler.scheduleSync()
        }
    }

    /**
     * Flow de purga COMPARTILHADO — um único ticker global, não um por coletor.
     * AUD-AN22: antes cada liveMessages() instanciava seu próprio while(true)
     * com delay(1000) e chamava purgeExpiredMessages() a cada 30s POR COLETOR.
     * N coletores ativos = N timers + N writes no SQLCipher por ciclo, mesmo
     * com a app em background.
     */
    private val purgeTicker: Flow<Long> = flow {
        var lastPurge = 0L
        while (true) {
            val now = System.currentTimeMillis()
            if (now / 30_000 != lastPurge / 30_000) {
                familyDao.purgeExpiredMessages(now)
                lastPurge = now
            }
            emit(now)
            delay(1000)
        }
    }.shareIn(scope, SharingStarted.WhileSubscribed(), replay = 1)

    private fun liveMessages(source: Flow<List<FamilyMessageEntity>>): Flow<List<FamilyMessageEntity>> =
        source.combine(purgeTicker) { messages, now ->
            val currentUid = auth.currentUser?.uid
            messages
                .filter { it.deletedAt == null && it.expiresAt > now }
                .map { msg ->
                    decodeMessageForLocalStorage(
                        currentUserId = currentUid ?: msg.patientUserId,
                        patientUserId = msg.patientUserId,
                        msg = msg
                    )
                }
        }

    /** D47 — Purga local: remove definitivamente mensagens que completaram 24h. */
    suspend fun purgeExpiredFamilyMessages() {
        familyDao.purgeExpiredMessages()
    }

    suspend fun sendMessageBidirectional(
        bindingId: String,
        recipientId: String,
        senderName: String,
        messageText: String,
        iconType: String = "CUSTOM"
    ): FamilyMessageEntity {
        val binding = familyDao.getBindingById(bindingId) ?: error("Vínculo não encontrado")
        val userId = auth.currentUser?.uid
        check(binding.status == "ACTIVE" && userId != null &&
            userId in listOf(binding.patientUserId, binding.caregiverUserId)) { "Vínculo indisponível." }
        val expectedRecipient = if (userId == binding.patientUserId) binding.caregiverUserId else binding.patientUserId
        check(recipientId == expectedRecipient) { "Destinatário não corresponde à conversa." }
        // O contrato atual é um grupo por paciente, compartilhado pelos vínculos ativos.
        return createFamilyMessage(binding.patientUserId, senderName, messageText, iconType)
    }

    suspend fun sendPatientReplyMessage(bindingId: String, messageText: String): FamilyMessageEntity {
        val binding = familyDao.getBindingById(bindingId) ?: error("Vínculo não encontrado")
        check(binding.status == "ACTIVE" && auth.currentUser?.uid == binding.patientUserId) { "Vínculo indisponível." }
        return createFamilyMessage(binding.patientUserId,
            auth.currentUser?.displayName ?: "Paciente", messageText, "CUSTOM")
    }

    /** ---------- CONSULTAS MÉDICAS ---------- */

    suspend fun createConsultation(
        userId: String,
        title: String,
        scheduledDate: Long,
        caregiverUserId: String?,
        caregiverName: String?,
        caregiverRelation: String?
    ): ConsultationEntity? {
        val consultation = ConsultationEntity(
            id = UUID.randomUUID().toString(),
            userId = userId,
            caregiverUserId = caregiverUserId,
            caregiverName = caregiverName,
            caregiverRelation = caregiverRelation,
            title = title,
            scheduledDate = java.util.Date(scheduledDate),
            status = "SUGGESTED",
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        familyDao.insertConsultation(consultation)
        // Lembretes 24h e 1h antes (doc 10 §2.4).
        NotificationHelper.scheduleConsultationReminders(appContext, consultation.id, title, scheduledDate)
        // Registra no gateway: é de lá que sai o push para os cuidadores (doc 10 §2.2).
        try {
            val iso = java.time.Instant.ofEpochMilli(scheduledDate).toString()
            apiClient.createConsultation(title, iso)
        } catch (e: Exception) {
            android.util.Log.w("FamilyBridgeRepo", "Consulta local criada, mas falhou o registro no gateway: ${e.message}")
        }
        return consultation
    }

    suspend fun acceptConsultation(consultationId: String, caregiverUserId: String): Boolean {
        familyDao.updateConsultationStatus(consultationId, "ACCEPTED", System.currentTimeMillis())
        // Aceitar no gateway dispara o push ao paciente (doc 10 §2.2). Best-effort:
        // a ação local já foi registrada; falha de rede não desfaz o aceite.
        try {
            apiClient.acceptConsultation(consultationId, null, null)
        } catch (e: Exception) {
            android.util.Log.w("FamilyBridgeRepo", "Aceite local ok, mas falhou no gateway: ${e.message}")
        }
        return true
    }

    suspend fun rejectConsultation(consultationId: String, userId: String): Boolean {
        familyDao.updateConsultationStatus(consultationId, "REJECTED", System.currentTimeMillis())
        // Consulta recusada não deve mais lembrar (doc 10 §2.4).
        NotificationHelper.cancelConsultationReminders(appContext, consultationId)
        try {
            apiClient.rejectConsultation(consultationId)
        } catch (e: Exception) {
            android.util.Log.w("FamilyBridgeRepo", "Recusa local ok, mas falhou no gateway: ${e.message}")
        }
        return true
    }

    suspend fun completeConsultation(consultationId: String, userId: String): Boolean {
        familyDao.updateConsultationStatus(consultationId, "COMPLETED", System.currentTimeMillis())
        NotificationHelper.cancelConsultationReminders(appContext, consultationId)
        try {
            apiClient.completeConsultation(consultationId)
        } catch (e: Exception) {
            android.util.Log.w("FamilyBridgeRepo", "Conclusão local ok, mas falhou no gateway: ${e.message}")
        }
        return true
    }

    suspend fun cancelConsultation(consultationId: String, userId: String): Boolean {
        familyDao.updateConsultationStatus(consultationId, "CANCELLED", System.currentTimeMillis())
        NotificationHelper.cancelConsultationReminders(appContext, consultationId)
        try {
            apiClient.cancelConsultation(consultationId)
        } catch (e: Exception) {
            android.util.Log.w("FamilyBridgeRepo", "Cancelamento local ok, mas falhou no gateway: ${e.message}")
        }
        return true
    }

    fun getConsultationsForUser(userId: String): Flow<List<ConsultationEntity>> {
        return familyDao.getConsultationsForUser(userId)
    }

    fun getActiveConsultationsForUser(userId: String): Flow<List<ConsultationEntity>> {
        return familyDao.getActiveConsultationsForUser(userId)
    }

    suspend fun deleteConsultation(consultationId: String): Boolean {
        familyDao.deleteConsultation(consultationId)
        NotificationHelper.cancelConsultationReminders(appContext, consultationId)
        return true
    }

    fun getSuggestedConsultationsForUser(userId: String): Flow<List<ConsultationEntity>> {
        return familyDao.getSuggestedConsultationsForUser(userId)
    }

    fun getAcceptedConsultationsForUser(userId: String): Flow<List<ConsultationEntity>> {
        return familyDao.getAcceptedConsultationsForUser(userId)
    }

    fun getRejectedConsultationsForUser(userId: String): Flow<List<ConsultationEntity>> {
        return familyDao.getRejectedConsultationsForUser(userId)
    }

    suspend fun updateConsultationStatus(
        consultationId: String,
        status: String,
        updatedAt: Long
    ): Boolean {
        try {
            familyDao.updateConsultationStatus(consultationId, status, updatedAt)
            return true
        } catch (e: Exception) {
            android.util.Log.e("FamilyBridgeRepo", "Falha ao atualizar status da consulta: ${e.message}")
            return false
        }
    }

    suspend fun updateGoogleCalendarEventId(consultationId: String, googleCalendarEventId: String): Boolean {
        try {
            familyDao.updateGoogleCalendarEventId(consultationId, googleCalendarEventId)
            return true
        } catch (e: Exception) {
            android.util.Log.e("FamilyBridgeRepo", "Falha ao atualizar ID do evento Google Calendar: ${e.message}")
            return false
        }
    }

    suspend fun getNextConsultation(userId: String, nowMillis: Long = System.currentTimeMillis()): ConsultationEntity? {
        return familyDao.getNextConsultation(userId, nowMillis)
    }

    suspend fun deleteAllConsultationsForUser(userId: String): Boolean {
        try {
            familyDao.deleteAllConsultationsForUser(userId)
            return true
        } catch (e: Exception) {
            android.util.Log.e("FamilyBridgeRepo", "Falha ao excluir consultas do usuário: ${e.message}")
            return false
        }
    }

    suspend fun getBindingById(bindingId: String): FamilyBindingEntity? {
        return familyDao.getBindingById(bindingId)
    }

    suspend fun createFamilyPost(
        caregiverUserId: String,
        caregiverName: String,
        patientUserId: String,
        patientName: String,
        title: String,
        description: String
    ): SocialPostEntity {
        val post = SocialPostEntity(
            id = UUID.randomUUID().toString(),
            userId = caregiverUserId,
            userName = caregiverName,
            userLevel = 1,
            postType = "family",
            title = title,
            description = description,
            relatedMilestoneId = null,
            createdAt = java.util.Date(),
            isVisible = true,
            reactionCount = 0,
            hasUserReacted = false,
            pendingSync = false,
            isFamilyPost = true,
            caregiverName = caregiverName,
            patientName = patientName
        )
        socialFeedDao.insertPost(post)
        return post
    }

    fun getMessageIconConfig(iconType: String): Pair<String, String> {
        return when (iconType) {
            "WATER" -> Pair("AGUA", "#2196F3")
            "MED" -> Pair("MEDICAMENTO", "#FF9800")
            "LOVE" -> Pair("AMOR", "#E91E63")
            "WALK" -> Pair("PASSOS", "#4CAF50")
            else -> Pair("DESTAQUE", "#9C27B0")
        }
    }

    fun getQuickMessageTemplates(): Map<String, MessageTemplate> {
        return mapOf(
            "water" to MessageTemplate("AGUA", "Parabens pela meta de agua hoje! Continue assim!", "WATER"),
            "medication" to MessageTemplate("MEDICAMENTO", "Lembrete: Hora do seu remedio! Estou torcendo por voce!", "MED"),
            "walk" to MessageTemplate("PASSOS", "Que incrivel! Voce bateu a meta de passos! Muito orgulho!", "WALK"),
            "love" to MessageTemplate("AMOR", "Te amo muito! Voce e minha maior inspiracao!", "LOVE"),
            "general" to MessageTemplate("GENERIC", "Passando so pra dizer que penso em voce! Tudo bem?", "CUSTOM")
        )
    }

    fun subscribePatientVitalSignsRealtime(patientUserId: String): Flow<List<VitalSignEntity>> {
        return vitalSignDao.getAll(patientUserId)
    }

    fun subscribeFamilyMessagesRealtime(patientUserId: String): Flow<List<FamilyMessageEntity>> {
        return familyDao.getRecentMessagesForPatient(patientUserId, 50)
    }
}

data class MessageTemplate(
    val icon: String,
    val text: String,
    val type: String
)

/**
 * Resultado imutável de um ciclo de sincronização da ponte familiar. Serve de
 * "versão" para o polling: o ViewModel só reage quando o fingerprint muda.
 */
data class ChatCycleSnapshot(
    /** Fingerprint do payload de mensagens; null quando o ciclo falhou. */
    val messagesFingerprint: String?
)
