package br.com.bragasaude.data.remote.repository

import br.com.bragasaude.data.local.*
import br.com.bragasaude.data.local.security.ExamFileStore
import br.com.bragasaude.data.remote.api.BragaApiClient
import br.com.bragasaude.data.remote.model.RemoteExam
import br.com.bragasaude.data.remote.model.RemoteExamItem
import br.com.bragasaude.data.remote.sync.SyncScheduler
import br.com.bragasaude.data.util.*
import br.com.bragasaude.domain.ExamCloudState
import br.com.bragasaude.domain.ExamStorageTerms
import br.com.bragasaude.util.BragaConstants
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.Date
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExamsRepository @Inject constructor(
    private val apiClient: BragaApiClient,
    private val examDao: ExamDao,
    private val examItemDao: ExamItemDao,
    private val clinicalReferenceDao: ClinicalReferenceDao,
    private val syncScheduler: SyncScheduler,
    private val fileStore: ExamFileStore,
    private val auth: FirebaseAuth
) {
    private val mutation = Mutex()
    fun getExams(userId: String): Flow<List<ExamEntity>> = examDao.getAll(userId)
    fun getExamItems(userId: String): Flow<List<ExamItemEntity>> = examItemDao.getAll(userId)
    fun getItemsForExam(examId: String) = examItemDao.getByExam(examId)

    /** Login e UUID nunca autorizam envio. A transação termina antes de qualquer rede. */
    suspend fun saveExam(exam: RemoteExam, items: List<RemoteExamItem> = emptyList()) = mutation.withLock {
        require(exam.userId == (auth.currentUser?.uid ?: BragaConstants.GUEST_UID)) { "Conta diferente da proprietária do exame." }
        val id = exam.id ?: UUID.randomUUID().toString()
        val existing = examDao.getLatestByExamId(id)
        require(existing == null || existing.userId == exam.userId)
        val entity = exam.copy(id = id).toEntity().copy(
            localId = existing?.localId ?: 0,
            fileUrl = existing?.fileUrl?.takeIf { it.startsWith("http") },
            localFilePath = exam.localFilePath ?: exam.fileUrl?.takeUnless { it.startsWith("http") } ?: existing?.localFilePath,
            cloudState = existing?.cloudState ?: ExamCloudState.LOCAL_ONLY,
            cloudConsentAccepted = existing?.cloudConsentAccepted ?: false,
            cloudConsentVersion = existing?.cloudConsentVersion,
            cloudConsentAt = existing?.cloudConsentAt,
            hasCloudCopy = existing?.hasCloudCopy ?: false,
            lastCloudSyncAt = existing?.lastCloudSyncAt,
            createdAt = existing?.createdAt ?: Date(),
            pendingSync = existing?.cloudConsentAccepted == true
        )
        val storedItems = items.map { it.copy(id = it.id ?: UUID.randomUUID().toString(), examId = id, userId = exam.userId, status = "confirmed").toEntity().copy(pendingSync = false) }
        examDao.saveWithItems(entity.copy(cloudState = if (entity.pendingSync) ExamCloudState.PENDING else entity.cloudState), storedItems)
        if (entity.pendingSync) syncScheduler.scheduleSync()
    }

    suspend fun authorizeCloud(examId: String, userId: String) = mutation.withLock {
        require(userId != BragaConstants.GUEST_UID && auth.currentUser?.uid == userId) { "Entre na sua conta para salvar na nuvem." }
        val exam = requireNotNull(examDao.getLatestByExamId(examId))
        require(exam.userId == userId)
        examDao.insert(exam.copy(cloudConsentAccepted = true, cloudConsentVersion = ExamStorageTerms.VERSION,
            cloudConsentAt = System.currentTimeMillis(), cloudState = ExamCloudState.PENDING, pendingSync = true))
        syncScheduler.scheduleSync()
    }

    suspend fun pauseCloud(examId: String, userId: String) = mutation.withLock {
        val exam = requireNotNull(examDao.getLatestByExamId(examId))
        require(exam.userId == userId)
        examDao.insert(exam.copy(cloudConsentAccepted = false, pendingSync = false,
            cloudState = if (exam.hasCloudCopy || exam.cloudState == ExamCloudState.ERROR) ExamCloudState.UNKNOWN else ExamCloudState.LOCAL_ONLY))
    }

    suspend fun syncAuthorizedExams() {
        for (pending in examDao.getPendingSync()) syncAuthorizedExam(requireNotNull(pending.remoteId))
    }

    /** A mesma trava protege envio, edição e exclusão contra respostas antigas. */
    suspend fun syncAuthorizedExam(examId: String) = mutation.withLock {
        var exam = examDao.getLatestByExamId(examId) ?: return@withLock
        if (!exam.cloudConsentAccepted || !exam.pendingSync || auth.currentUser?.uid != exam.userId) return@withLock
        try {
            check(exam.cloudConsentVersion == ExamStorageTerms.VERSION) { "Confirme os termos atuais antes de enviar." }
            check(apiClient.supportsExamSnapshots()) { "O serviço de nuvem precisa ser atualizado. Seu exame continua neste aparelho." }
            val items = examItemDao.getByExamLocal(examId)
            val local = exam.localFilePath
            if (local != null) {
                check(auth.currentUser?.uid == exam.userId) { "Conta alterada durante o envio." }
                val bytes = fileStore.readBytes(local)
                val name = java.io.File(local).name.removeSuffix(".enc")
                val result = apiClient.uploadExamContract(examId, exam.title, exam.category ?: "Geral",
                    java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(exam.examDate), "UNSTRUCTURED_DOCUMENT", true,
                    requireNotNull(exam.cloudConsentVersion), name, bytes)
                check(result?.success == true && !result.fileUrl.isNullOrBlank()) { "Envio do original não concluído." }
                exam = exam.copy(fileUrl = result.fileUrl, hasCloudCopy = true)
                examDao.insert(exam)
            }
            check(auth.currentUser?.uid == exam.userId) { "Conta alterada durante o envio." }
            check(apiClient.syncManualExam(examId, exam.title, exam.category ?: "Laboratorial",
                java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(exam.examDate), items.map { it.toRemote() })) { "Envio dos resultados não concluído." }
            exam = exam.copy(hasCloudCopy = true)
            examDao.insert(exam.copy(pendingSync = false, cloudState = ExamCloudState.SYNCED, lastCloudSyncAt = System.currentTimeMillis()))
        } catch (e: CancellationException) { throw e
        } catch (e: Exception) {
            examDao.insert(exam.copy(cloudState = ExamCloudState.ERROR))
            throw e
        }
    }

    suspend fun removeCloudCopy(examId: String, userId: String) = mutation.withLock {
        var exam = requireNotNull(examDao.getLatestByExamId(examId))
        require(exam.userId == userId && auth.currentUser?.uid == userId)
        // Recuperar original antes de remover a única cópia, se ele só existir remotamente.
        if (exam.localFilePath == null && exam.fileUrl != null) {
            val url = requireNotNull(exam.fileUrl)
            val path = fileStore.store(apiClient.downloadExamOriginal(url), java.net.URI(url).path.substringAfterLast('.', "pdf"))
            exam = exam.copy(localFilePath = path)
            examDao.insert(exam)
        } else if (exam.localFilePath != null) {
            fileStore.readBytes(requireNotNull(exam.localFilePath))
        }
        check(auth.currentUser?.uid == userId && apiClient.deleteExam(examId)) { "Remoção remota não confirmada." }
        examDao.insert(exam.copy(fileUrl = null, hasCloudCopy = false, cloudConsentAccepted = false,
            pendingSync = false, cloudState = ExamCloudState.LOCAL_ONLY, lastCloudSyncAt = null))
    }

    suspend fun deleteExamAtomically(examId: String, userId: String): Boolean = mutation.withLock {
        val exam = examDao.getLatestByExamId(examId) ?: return@withLock true
        require(exam.userId == userId)
        if (exam.hasCloudCopy || exam.cloudState == ExamCloudState.UNKNOWN) {
            check(auth.currentUser?.uid == userId && apiClient.deleteExam(examId)) { "Não foi possível excluir a cópia remota. O exame continua disponível." }
        }
        examDao.deleteWithItems(examId)
        fileStore.delete(exam.localFilePath)
        true
    }

    suspend fun confirmExamItem(itemId: String) {
        val item = itemId.toLongOrNull()?.let { examItemDao.getById(it) } ?: examItemDao.getByRemoteId(itemId)
        if (item != null) examItemDao.insert(item.copy(status = "confirmed", pendingSync = false))
    }

    suspend fun confirmAllItemsForExam(examId: String) {
        val exam = examDao.getLatestByExamId(examId) ?: return
        saveExam(exam.toRemote().copy(status = "confirmed"), examItemDao.getByExamLocal(examId).map { it.toRemote() })
    }
    suspend fun getClinicalReference(key: String) = clinicalReferenceDao.getByKey(key)
    suspend fun syncClinicalReferences() = Unit

    suspend fun pullAndMergeExams(userId: String) = mutation.withLock {
        if (userId == BragaConstants.GUEST_UID) return@withLock
        for (remote in apiClient.getExams(userId)) {
            val id = remote.id ?: continue
            // Não sobrescrever conteúdo/política locais nem limpar fila por um pull antigo.
            if (examDao.getLatestByExamId(id) == null) {
                examDao.saveWithItems(remote.toEntity().copy(hasCloudCopy = true, cloudState = ExamCloudState.SYNCED),
                    remote.labItems.map { it.toEntity().copy(pendingSync = false) })
            }
        }
    }
    suspend fun downloadExamOriginal(fileUrl: String) = apiClient.downloadExamOriginal(fileUrl)
}
