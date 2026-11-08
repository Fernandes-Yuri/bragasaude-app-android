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
import androidx.annotation.VisibleForTesting
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.Date
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * DT-053: janela de recência para notificação local de mensagem recebida.
 * Mensagens com `sentAt` mais antigo que isso NÃO geram alerta local — após
 * wipe do app/troca de conta o primeiro sync baixa o histórico inteiro e,
 * sem este filtro, o app notificava mensagens antigas já lidas ("fantasmas").
 */
private const val RECENT_MESSAGE_NOTIFY_WINDOW_MS = 300_000L // 5 minutos

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
    private val e2eeManager: FamilyE2eeManager,
    private val notificationDao: br.com.bragasaude.data.local.NotificationDao? = null
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
        appContext: Context,
        e2eeManager: FamilyE2eeManager
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
        e2eeManager = e2eeManager,
        notificationDao = null
    )
    private val clinicalSyncLock = Mutex()

    suspend fun syncVitalChanges(patientId: String) = clinicalSyncLock.withLock {
        val owner = auth.currentUser?.uid ?: error("Sessão encerrada")
        var cursor = vitalSignDao.getCursor(owner, patientId) ?: 0L
        do {
            val page = apiClient.getVitalChanges(patientId, cursor) ?: throw java.io.IOException("Histórico clínico indisponível")
            check(auth.currentUser?.uid == owner) { "Sessão alterada" }
            vitalSignDao.cachePage(owner, patientId, page.records, page.nextCursor)
            cursor = page.nextCursor
        } while (page.hasMore)
    }

    private fun reconcileReminder(c: ConsultationEntity) {
        val owner = auth.currentUser?.uid ?: return
        if (c.status in setOf("SUGGESTED", "ACCEPTED") && c.scheduledDate.time > System.currentTimeMillis() &&
            (c.remoteConfirmed || c.pendingSync)) {
            NotificationHelper.scheduleConsultationReminders(appContext, c.id, c.title, c.scheduledDate.time, owner, c.userId)
        } else NotificationHelper.cancelConsultationReminders(appContext, c.id, owner)
    }

    suspend fun syncConsultations(patientId: String) {
        val owner = auth.currentUser?.uid ?: error("Sessão encerrada")
        val records = apiClient.getConsultations(patientId) ?: throw java.io.IOException("Agenda indisponível")
        check(auth.currentUser?.uid == owner)
        for (record in records) {
            val before = familyDao.getConsultationById(record.id)
            familyDao.cacheConsultation(record)
            val after = familyDao.getConsultationById(record.id)
            if (after != null && after != before) reconcileReminder(after)
        }
    }

    suspend fun flushConsultations() {
        val owner = auth.currentUser?.uid ?: return
        for (c in familyDao.getPendingConsultations()) {
            if (auth.currentUser?.uid != owner) throw kotlinx.coroutines.CancellationException("Sessão alterada")
            if (c.pendingActorId != owner) continue
            var remote = if (!c.remoteConfirmed) apiClient.createConsultationRecord(c)
                ?: throw java.io.IOException("Consulta aguardando envio") else c
            if (c.pendingAction != null && c.pendingAction != "CREATE") {
                remote = apiClient.changeConsultation(c.id, c.pendingAction)
                    ?: throw java.io.IOException("Alteração de consulta pendente")
            }
            check(auth.currentUser?.uid == owner)
            familyDao.acknowledgeConsultation(c, remote)
            familyDao.getConsultationById(c.id)?.let { reconcileReminder(it) }
        }
    }

    suspend fun flushReadReceipts() {
        val owner = auth.currentUser?.uid ?: return
        for ((patient, receipts) in familyDao.pendingReceipts(owner).groupBy { it.patientId }) {
            requireConversationAccess(patient)
            for (batch in receipts.chunked(100)) {
                if (!apiClient.markFamilyMessagesRead(patient, batch.map { it.messageId })) throw java.io.IOException("Recibo pendente")
                check(auth.currentUser?.uid == owner)
                batch.forEach { familyDao.acknowledgeReceipt(owner, it.messageId) }
            }
        }
    }

    /**
     * Construtor reservado exclusivamente a testes unitários JVM: nesse contexto
     * o Hilt não está ativo e o E2EE usa [InMemoryE2eeKeyStoreProvider]. Em
     * produção o Hilt injeta o @Singleton FamilyE2eeManager (AndroidKeyStore).
     */
    @VisibleForTesting(otherwise = VisibleForTesting.NONE)
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

    // Etapa 5 (item 3): a purga LGPD dispara apenas na TRANSIÇÃO ACTIVE -> não-ACTIVE.
    // Defesa extra: guarda em memória dos patientIds já purgados nesta sessão, para
    // nunca reemitir os mesmos DELETEs (locks Room/JNI) a cada ciclo de sync. É
    // limpa quando o vínculo volta a ACTIVE (acesso legítimo recuperado).
    private val purgedPatientIds: MutableSet<String> = java.util.concurrent.ConcurrentHashMap.newKeySet()

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
            // AUD-CHAT1: publicação IMEDIATA da chave pública E2EE do paciente na
            // criação do convite, além da inclusão no payload de sync. Assim o
            // cuidador consegue decifrar mensagens no primeiro ciclo, mesmo que o
            // novo convite chegue ao cuidador antes do próximo sync de vínculos.
            runCatching {
                val patientPubKey = e2eeManager.getOrCreatePublicKeyBase64(patientUserId)
                if (remoteId != null && patientPubKey.isNotBlank()) {
                    apiClient.publishFamilyE2eePublicKey(
                        bindingId = remoteId,
                        patientId = patientUserId,
                        publicKey = patientPubKey
                    )
                }
            }.onFailure {
                android.util.Log.w("FamilyBridgeRepo", "Chave E2EE do convite será sincronizada depois: ${it.message}")
            }
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
        purgedPatientIds.remove(accepted.patientUserId)
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
        if (accepted.status == "ACTIVE" && accepted.patientUserId.isNotBlank()) {
            runCatching { syncConsultations(accepted.patientUserId) }
            runCatching { syncPatientProfileIfMissing(accepted.patientUserId) }
        }
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
        if (patientUserId.isBlank()) return
        // Defesa extra: nada a deletar se este paciente já foi purgado nesta sessão.
        if (purgedPatientIds.contains(patientUserId)) return
        try {
            e2eeManager.clearKeysForPatient(patientUserId)
            vitalSignDao.deleteByUserId(patientUserId)
            dailyMetricsDao.deleteByUserId(patientUserId)
            familyDao.deleteMessagesForPatient(patientUserId)
            val owner = auth.currentUser?.uid
            if (owner != null) {
                familyDao.consultationsForCleanup(patientUserId).forEach { NotificationHelper.cancelConsultationReminders(appContext, it.id, owner) }
                familyDao.deleteReceiptsForPatient(owner, patientUserId)
                vitalSignDao.clearCursor(owner, patientUserId)
                notificationDao?.deleteForRevokedPatient(owner, patientUserId)
            }
            familyDao.deleteAllConsultationsForUser(patientUserId)
            profileDao.deleteProfile(patientUserId)
            purgedPatientIds.add(patientUserId)
            // Log apenas quando houve purga de fato.
            android.util.Log.d("FamilyBridgeRepo", "Purga LGPD concluída com sucesso para dados do paciente")
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
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

    suspend fun syncBindingsForPatient(patientUserId: String, strict: Boolean = false) {
        if (patientUserId.isBlank() || patientUserId == br.com.bragasaude.util.BragaConstants.GUEST_UID) return
        try {
            val list = apiClient.getFamilyBindings(patientUserId)
            val activePatientIds = list.filter { it.status == "ACTIVE" }.map { it.patientUserId }.toSet()
            list.forEach { item ->
                if (item.status == "ACTIVE") {
                    // Vínculo reativado: libera a guarda para que uma futura revogação purge de novo.
                    purgedPatientIds.remove(item.patientUserId)
                    familyDao.deletePendingBindingsByCode(item.connectionCode)
                }
                // Estado local é capturado ANTES do insert: a purga LGPD só pode
                // disparar na transição ACTIVE -> não-ACTIVE, nunca em PENDING/
                // EXPIRED/REVOKED já vistos em ciclos anteriores, e nunca quando há
                // outro vínculo ACTIVE para o mesmo paciente após refazer o vínculo.
                val local = familyDao.getBindingById(item.id)
                val wasActive = local?.status == "ACTIVE"
                if (local?.pendingSync != true) familyDao.insertBinding(item)
                if (wasActive && item.status != "ACTIVE" && item.patientUserId !in activePatientIds &&
                    item.caregiverUserId == auth.currentUser?.uid && item.patientUserId != auth.currentUser?.uid) {
                    purgePatientDataFromCache(item.patientUserId)
                }
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            if (strict) throw e
            android.util.Log.e("FamilyBridgeRepo", "Falha ao baixar vínculos do paciente: ${e.message}")
        }
    }

    suspend fun syncBindingsForCaregiver(caregiverUserId: String, strict: Boolean = false) {
        if (caregiverUserId.isBlank() || caregiverUserId == br.com.bragasaude.util.BragaConstants.GUEST_UID) return
        try {
            val list = apiClient.getFamilyBindings(caregiverUserId)
            val activePatientIds = list.filter { it.status == "ACTIVE" }.map { it.patientUserId }.toSet()
            val newlyActivatedPatients = linkedSetOf<String>()
            list.forEach { item ->
                if (item.status == "ACTIVE") {
                    // Vínculo reativado: libera a guarda para que uma futura revogação purge de novo.
                    purgedPatientIds.remove(item.patientUserId)
                }
                // Estado local é capturado ANTES do insert: purga apenas na transição
                // ACTIVE -> não-ACTIVE (evita DELETEs repetidos a cada ciclo de sync) e
                // somente se não houver outro vínculo ACTIVE vigente para o mesmo paciente.
                val local = familyDao.getBindingById(item.id)
                val wasActive = local?.status == "ACTIVE"
                if (local?.pendingSync != true) familyDao.insertBinding(item)
                if (!wasActive && item.status == "ACTIVE" &&
                    item.caregiverUserId == auth.currentUser?.uid && item.patientUserId != auth.currentUser?.uid) {
                    newlyActivatedPatients.add(item.patientUserId)
                }
                if (wasActive && item.status != "ACTIVE" && item.patientUserId !in activePatientIds &&
                    item.caregiverUserId == auth.currentUser?.uid && item.patientUserId != auth.currentUser?.uid) {
                    purgePatientDataFromCache(item.patientUserId)
                }
            }
            for (patientId in newlyActivatedPatients) {
                runCatching { syncConsultations(patientId) }
                runCatching { syncPatientProfileIfMissing(patientId) }
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            if (strict) throw e
            android.util.Log.e("FamilyBridgeRepo", "Falha ao baixar vínculos do cuidador: ${e.message}")
        }
    }

    suspend fun syncPatientProfileIfMissing(patientUserId: String) {
        if (patientUserId.isBlank() || patientUserId == br.com.bragasaude.util.BragaConstants.GUEST_UID) return
        val sessionOwner = auth.currentUser?.uid ?: return
        val existing = profileDao.getProfileOneShot(patientUserId)
        if (!existing?.fullName.isNullOrBlank()) return
        val p = apiClient.getProfile(patientUserId) ?: return
        if (auth.currentUser?.uid != sessionOwner) return
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
                plaintext = cleanText,
                messageId = localId
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

    private val undecipherableWarnings = object : LinkedHashMap<String, Boolean>() {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Boolean>?) = size > 256
    }

    private fun decodeMessageForLocalStorage(
        currentUserId: String,
        patientUserId: String,
        msg: FamilyMessageEntity
    ): FamilyMessageEntity {
        if (!FamilyE2eeManager.isEncryptedEnvelope(msg.messageText)) return msg
        return try {
            msg.copy(messageText = e2eeManager.decryptForConversation(
                currentUserId = currentUserId, patientId = patientUserId,
                rawPayload = msg.messageText, senderUserId = msg.senderUserId, messageId = msg.id
            ).text, awaitingDecryption = false)
        } catch (e: java.security.GeneralSecurityException) {
            val reference = "$currentUserId:$patientUserId:${msg.id}:${msg.messageText.hashCode()}"
            val first = synchronized(undecipherableWarnings) { undecipherableWarnings.put(reference, true) == null }
            if (first) android.util.Log.w("FamilyBridgeRepo", "Mensagem indisponível nesta instalação; aguardando atualização das chaves.")
            msg.copy(messageText = "", awaitingDecryption = true)
        }
    }

    private fun preserveReadableCopy(remote: FamilyMessageEntity, local: FamilyMessageEntity?): FamilyMessageEntity {
        val readable = if (remote.awaitingDecryption && local != null &&
            !local.awaitingDecryption && local.messageText.isNotBlank()) {
            remote.copy(messageText = local.messageText, awaitingDecryption = false)
        } else remote
        return readable.copy(isRead = readable.isRead || local?.isRead == true)
    }

    private val publicationLock = Mutex()
    private val publishedKeys = mutableMapOf<String, Pair<String, Long>>()
    private suspend fun publishSessionKey(userId: String, room: String, key: String) = publicationLock.withLock {
        check(auth.currentUser?.uid == userId) { "Sua conta mudou." }
        val cacheKey = "$userId:$room"
        val now = System.nanoTime() / 1_000_000
        val previous = publishedKeys[cacheKey]
        if (previous?.first == key && now - previous.second < 300_000) return@withLock
        check(apiClient.publishFamilyE2eePublicKey(patientId = room, publicKey = key)) {
            "Não foi possível preparar as mensagens desta instalação. Tente novamente."
        }
        check(auth.currentUser?.uid == userId) { "Sua conta mudou." }
        if (publishedKeys.size >= 64) publishedKeys.clear()
        publishedKeys[cacheKey] = key to now
    }

    /** Publica a identidade da instalação antes dos demais downloads da sessão autenticada. */
    suspend fun prepareSessionKeys(userId: String) {
        check(auth.currentUser?.uid == userId) { "Sua conta mudou." }
        val bindings = apiClient.getFamilyBindings(userId)
        check(auth.currentUser?.uid == userId) { "Sua conta mudou." }
        val rooms = bindings.filter { it.status == "ACTIVE" &&
            (it.patientUserId == userId || it.caregiverUserId == userId) }.map { it.patientUserId }.distinct()
        if (rooms.isEmpty()) return
        val publicKey = e2eeManager.getOrCreatePublicKeyBase64(userId)
        for (room in rooms) {
            check(auth.currentUser?.uid == userId) { "Sua conta mudou." }
            publishSessionKey(userId, room, publicKey)
        }
        check(auth.currentUser?.uid == userId) { "Sua conta mudou." }
        apiClient.getFamilyBindings(userId)
    }

    suspend fun prepareConversation(patientId: String) {
        val userId = requireConversationAccess(patientId)
        val publicKey = e2eeManager.getOrCreatePublicKeyBase64(userId)
        check(apiClient.publishFamilyE2eePublicKey(patientId = patientId, publicKey = publicKey)) {
            "Não foi possível preparar a conversa. Tente novamente."
        }
        check(requireConversationAccess(patientId) == userId) { "Sua conta mudou." }
        apiClient.getFamilyBindings(userId)
    }

    suspend fun sendCareMessage(
        patientUserId: String,
        senderName: String,
        messageText: String,
        iconType: String = "LOVE"
    ): FamilyMessageEntity = createFamilyMessage(patientUserId, senderName, messageText, iconType)

    /**
     * Recupera as chaves ativas antes de tentar novamente a decifragem do histórico.
     * Uma resposta idêntica/vazia é sucesso; falhas são propagadas.
     */
    suspend fun chatCycleSnapshot(userId: String, patientId: String): ChatCycleSnapshot {
        check(auth.currentUser?.uid == userId) { "Sua conta mudou. Abra a conversa novamente." }
        apiClient.getFamilyBindings(userId)
        check(auth.currentUser?.uid == userId) { "Sua conta mudou." }
        // Uma mensagem pendente por falta de handshake é reenviada assim que a chave chega.
        for (pending in familyDao.getPendingSyncMessages().filter {
            it.patientUserId == patientId && it.senderUserId == userId
        }) {
            check(requireConversationAccess(patientId) == userId) { "Sua conta mudou." }
            val remoteId = apiClient.syncFamilyMessage(pending) ?: continue
            check(requireConversationAccess(patientId) == userId) { "Sua conta mudou." }
            familyDao.markMessageSynced(pending.id, remoteId)
        }
        return ChatCycleSnapshot(messagesFingerprint = syncFamilyMessages(patientId).getOrThrow())
    }

    suspend fun syncFamilyMessages(patientUserId: String): Result<String> = runCatching {
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
                    if (local?.pendingSync != true && local?.deletedAt == null) {
                        val messageToPersist = preserveReadableCopy(decodedMsg, local)
                        familyDao.insertMessage(messageToPersist)
                    }
                    // Fallback (doc 10 §1A.2): mensagem NOVA vinda do servidor e que
                    // não foi enviada por este aparelho → notificação local. É a rota
                    // que cobre quem não recebeu (ou não pode receber) o push FCM.
                    // DT-053: só notifica mensagem RECENTE (≤ 5 min) e NÃO LIDA. Sem
                    // isso, após wipe/troca de conta (Room vazio) o primeiro sync
                    // baixava o histórico antigo e disparava alertas de mensagens já
                    // visualizadas dias antes.
                    val isRecent = System.currentTimeMillis() - decodedMsg.sentAt < RECENT_MESSAGE_NOTIFY_WINDOW_MS
                    if (local == null && decodedMsg.senderUserId != auth.currentUser?.uid && decodedMsg.messageText.isNotBlank() && isRecent && !decodedMsg.isRead) {
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
                familyDao.purgeReceipts(now)
        messagesFingerprint(remote)
    }.onFailure {
        // Sair da tela ou trocar de conta deve interromper a consulta, sem alerta.
        if (it is kotlinx.coroutines.CancellationException) throw it
    }

    /**
     * Processa mensagem de família recebida em tempo real via WebSocket (/ws/family).
     * Decodifica E2EE, atualiza o Room DB e emite notificação para mensagens de terceiros.
     */
    suspend fun receiveRealtimeMessage(msg: FamilyMessageEntity) {
        val currentUid = auth.currentUser?.uid ?: return
        val now = System.currentTimeMillis()
        if (msg.expiresAt in 1..now) {
            familyDao.deleteMessage(msg.id)
            return
        }
        if (msg.deletedAt != null) {
            familyDao.insertMessage(msg.copy(messageText = "", senderName = "", pendingSync = false))
            return
        }

        val decodedMsg = decodeMessageForLocalStorage(currentUid, msg.patientUserId, msg)
        val local = familyDao.getMessageById(decodedMsg.id)
        if (local?.pendingSync != true && local?.deletedAt == null) {
            val messageToPersist = preserveReadableCopy(decodedMsg, local)
            familyDao.insertMessage(messageToPersist)
        }

        if (local == null && decodedMsg.senderUserId != currentUid && decodedMsg.messageText.isNotBlank()) {
            try {
                FamilyNotificationService.notifyPatientMessage(
                    appContext,
                    decodedMsg.senderName.ifBlank { "Familiar" },
                    decodedMsg.messageText
                )
            } catch (e: Exception) {
                android.util.Log.w("FamilyBridgeRepo", "Aviso ao notificar mensagem recebida via WebSocket: ${e.message}")
            }
        }
    }

    /**
     * Processa exclusão (tombstone) recebida em tempo real via WebSocket (/ws/family).
     */
    suspend fun receiveRealtimeMessageDeletion(messageId: String) {
        val local = familyDao.getMessageById(messageId)
        if (local != null) {
            familyDao.insertMessage(local.copy(messageText = "", senderName = "", deletedAt = System.currentTimeMillis(), pendingSync = false))
        } else {
            familyDao.deleteMessage(messageId)
        }
    }

    /**
     * Assinatura barata do estado atual das mensagens no servidor. Não é
     * criptográfica e não representa o sucesso ou a falha da consulta.
     */
    private fun messagesFingerprint(messages: List<FamilyMessageEntity>): String =
        messages.joinToString(separator = "|") { "${it.id}:${it.sentAt}:${it.deletedAt}" }
            .hashCode()
            .toString()

    suspend fun syncPatientDataForCaregiver(patientUserId: String): Result<Unit> = runCatching {
        purgedPatientIds.remove(patientUserId)
        val sessionOwner = auth.currentUser?.uid
            ?: throw IllegalStateException("Sessão encerrada")
        fun ensureSession() {
            if (auth.currentUser?.uid != sessionOwner) throw kotlinx.coroutines.CancellationException("Sessão alterada")
        }
        var downloadError: Exception? = null
        // 1. Sincronizar perfil
        try {
            val p = apiClient.getProfile(patientUserId)
                ?: throw java.io.IOException("Não foi possível baixar o perfil do familiar")
            ensureSession()
            run {
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
            downloadError = e
            android.util.Log.w("FamilyBridgeRepo", "Aviso ao sincronizar perfil do paciente: ${e.message}")
        }

        // 2. Sincronizar sinais vitais
        try {
            // DT-052: getVitalSigns devolve null em falha de rede — pular o
            // download de vitais sem interromper o sync do restante do paciente.
            syncVitalChanges(patientUserId)
            syncConsultations(patientUserId)

        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            downloadError = e
            android.util.Log.w("FamilyBridgeRepo", "Aviso ao sincronizar vitais do paciente: ${e.message}")
        }

        // Métricas também alimentam o painel; falha parcial não é atualização confirmada.
        try {
            val metrics = apiClient.getDailyMetrics(patientUserId)
                ?: throw java.io.IOException("Não foi possível baixar as métricas do familiar")
            ensureSession()
            metrics.forEach { metric ->
                val local = dailyMetricsDao.getByDate(patientUserId, metric.date)
                dailyMetricsDao.cacheRemote(metric)
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            downloadError = e
        }
        downloadError?.let { throw it }
        ensureSession()
        syncFamilyMessages(patientUserId).getOrThrow()
        Unit
    }.onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }

    fun getUnreadMessagesForPatient(patientUserId: String): Flow<List<FamilyMessageEntity>> {
        return liveMessages(familyDao.getUnreadMessagesForPatient(patientUserId)).map { messages ->
            val owner = auth.currentUser?.uid
            messages.filter { it.senderUserId != owner && !it.awaitingDecryption && it.messageText.isNotBlank() }
        }
    }

    fun getRecentMessagesForPatient(patientUserId: String, limit: Int = 10): Flow<List<FamilyMessageEntity>> {
        return liveMessages(familyDao.getRecentMessagesForPatient(patientUserId, limit))
    }

    /** Todas as mensagens familiares ativas ordenadas por data para o inbox multi-conversas (D47). */
    fun getAllActiveMessages(): Flow<List<FamilyMessageEntity>> {
        return liveMessages(familyDao.getAllActiveMessages())
    }

    suspend fun countUnreadMessages(patientUserId: String): Int {
        return getUnreadMessagesForPatient(patientUserId).first().size
    }

    suspend fun markMessageAsRead(messageId: String) {
        val message = familyDao.getMessageById(messageId) ?: return
        markVisibleMessagesAsRead(message.patientUserId, listOf(messageId))
    }

    suspend fun markVisibleMessagesAsRead(patientUserId: String, messageIds: List<String>) {
        val userId = requireConversationAccess(patientUserId)
        val eligible = messageIds.distinct().take(100).mapNotNull { familyDao.getMessageById(it) }
            .filter { it.patientUserId == patientUserId && it.senderUserId != userId &&
                !it.isRead && !it.awaitingDecryption && !it.pendingSync && it.deletedAt == null &&
                it.expiresAt > System.currentTimeMillis() }
        if (eligible.isEmpty()) return
        familyDao.markSeen(userId, eligible)
        val remainingUnread = familyDao.getUnreadMessagesForPatient(patientUserId).first()
            .any { it.senderUserId != userId && !it.awaitingDecryption && it.deletedAt == null && it.expiresAt > System.currentTimeMillis() }
        if (!remainingUnread) {
            notificationDao?.reconcileChatConversationReadLocal(userId, patientUserId, System.currentTimeMillis())
        }
        syncScheduler.scheduleSync()
        flushReadReceipts()
    }

    // Ação explícita “marcar todas”: limita-se às cópias locais legíveis.
    suspend fun markAllMessagesAsRead(patientUserId: String) {
        val unread = familyDao.getUnreadMessagesForPatient(patientUserId).first()
        unread.map { it.id }.chunked(100).forEach { markVisibleMessagesAsRead(patientUserId, it) }
    }

    suspend fun receiveRealtimeMessagesRead(patientUserId: String, readerId: String, messageIds: List<String>) {
        val userId = requireConversationAccess(patientUserId)
        if (readerId.isBlank() || readerId == userId) return
        for (id in messageIds.distinct()) {
            val local = familyDao.getMessageById(id) ?: familyDao.getMessageByRemoteId(id) ?: continue
            if (local.patientUserId == patientUserId && local.senderUserId == userId && !local.pendingSync) {
                familyDao.markMessageAsRead(local.id)
            }
        }
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
                familyDao.purgeReceipts(now)
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
        familyDao.purgeReceipts()
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
        val owner = auth.currentUser?.uid ?: error("Sessão encerrada")
        require(title.isNotBlank() && scheduledDate > System.currentTimeMillis())
        val consultation = ConsultationEntity(id = UUID.randomUUID().toString(), userId = userId,
            caregiverUserId = caregiverUserId, caregiverName = caregiverName, caregiverRelation = caregiverRelation,
            title = title.trim(), scheduledDate = Date(scheduledDate),
            status = if (owner == userId) "SUGGESTED" else "ACCEPTED",
            pendingSync = true, pendingAction = "CREATE", pendingActorId = owner, createdBy = owner)
        familyDao.insertConsultation(consultation)
        reconcileReminder(consultation)
        syncScheduler.scheduleSync()
        try { flushConsultations() } catch (e: kotlinx.coroutines.CancellationException) { throw e
        } catch (_: Exception) { /* Comando já persistido para retry. */ }
        return familyDao.getConsultationById(consultation.id) ?: consultation
    }

    suspend fun confirmLocalConsultation(id: String) {
        val c = familyDao.getConsultationById(id) ?: return
        val owner = requireConversationAccess(c.userId)
        check(!c.remoteConfirmed && !c.pendingSync && c.status in setOf("SUGGESTED", "ACCEPTED"))
        val queued = c.copy(createdBy = owner, pendingActorId = owner, pendingAction = "CREATE", pendingSync = true)
        familyDao.insertConsultation(queued)
        reconcileReminder(queued)
        syncScheduler.scheduleSync()
        flushConsultations()
    }

    private suspend fun queueConsultationStatus(id: String, status: String): Boolean {
        val current = familyDao.getConsultationById(id) ?: return false
        requireConversationAccess(current.userId)
        val queued = current.copy(status = status, updatedAt = System.currentTimeMillis(),
            pendingActorId = auth.currentUser?.uid,
            pendingSync = current.remoteConfirmed || current.pendingSync,
            pendingAction = if (current.remoteConfirmed || current.pendingSync) status else null)
        familyDao.insertConsultation(queued)
        reconcileReminder(queued)
        syncScheduler.scheduleSync()
        try { flushConsultations() } catch (e: kotlinx.coroutines.CancellationException) { throw e
        } catch (_: Exception) { }
        return true
    }

    suspend fun acceptConsultation(consultationId: String, caregiverUserId: String): Boolean = queueConsultationStatus(consultationId, "ACCEPTED")
    suspend fun rejectConsultation(consultationId: String, userId: String): Boolean = queueConsultationStatus(consultationId, "REJECTED")
    suspend fun completeConsultation(consultationId: String, userId: String): Boolean = queueConsultationStatus(consultationId, "COMPLETED")
    suspend fun cancelConsultation(consultationId: String, userId: String): Boolean = queueConsultationStatus(consultationId, "CANCELLED")

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
 * Snapshot de uma consulta bem-sucedida, inclusive quando não há mensagens novas.
 */
data class ChatCycleSnapshot(
    val messagesFingerprint: String
)
