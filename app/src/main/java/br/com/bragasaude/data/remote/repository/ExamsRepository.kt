package br.com.bragasaude.data.remote.repository

import br.com.bragasaude.data.local.*
import br.com.bragasaude.data.remote.api.BragaApiClient
import br.com.bragasaude.data.remote.model.RemoteExam
import br.com.bragasaude.data.remote.model.RemoteExamItem
import br.com.bragasaude.data.remote.sync.SyncScheduler
import br.com.bragasaude.data.util.toEntity
import br.com.bragasaude.data.util.toRemote
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExamsRepository @Inject constructor(
    private val apiClient: BragaApiClient,
    private val examDao: ExamDao,
    private val examItemDao: ExamItemDao,
    private val clinicalReferenceDao: ClinicalReferenceDao,
    private val syncScheduler: SyncScheduler
) {
    private val guestId = "00000000-0000-0000-0000-000000000000"

    fun getExams(userId: String): Flow<List<ExamEntity>> = examDao.getAll(userId)

    suspend fun saveExam(exam: RemoteExam, items: List<RemoteExamItem> = emptyList()) {
        val examWithId = if (exam.id == null) exam.copy(id = UUID.randomUUID().toString()) else exam
        val examEntity = examWithId.toEntity().copy(pendingSync = true)
        val localRowId = examDao.insert(examEntity)
        
        val itemsWithIds = items.map { it.copy(id = it.id ?: UUID.randomUUID().toString(), examId = examWithId.id!!) }
        examItemDao.insertAll(itemsWithIds.map { it.toEntity().copy(pendingSync = true) })

        if (exam.userId == guestId) return
        
        try {
            examDao.insert(examEntity.copy(localId = localRowId, pendingSync = true))
        } catch (e: Exception) {
            examDao.insert(examEntity.copy(localId = localRowId, pendingSync = true))
            examItemDao.insertAll(itemsWithIds.map { it.toEntity().copy(pendingSync = true) })
            triggerSync()
        }
    }

    suspend fun confirmExamItem(itemId: String) {
        val item = itemId.toLongOrNull()?.let { examItemDao.getById(it) } ?: examItemDao.getByRemoteId(itemId)
        if (item != null) {
            val confirmedItem = item.copy(status = "confirmed", pendingSync = false)
            examItemDao.insert(confirmedItem)
        }
    }

    suspend fun confirmAllItemsForExam(examId: String) {
        val items = examItemDao.getByExamLocal(examId).filter { it.status == "analyzed" }
        for (item in items) {
            val confirmedItem = item.copy(status = "confirmed", pendingSync = false)
            examItemDao.insert(confirmedItem)
        }
        val exam = examDao.getLatestByExamId(examId)
        if (exam != null && items.isNotEmpty()) {
            examDao.insert(exam.copy(status = "confirmed", pendingSync = false))
        }
    }

    fun getExamItems(userId: String): Flow<List<ExamItemEntity>> = examItemDao.getAll(userId)
    
    suspend fun getClinicalReference(key: String) = clinicalReferenceDao.getByKey(key)

    suspend fun syncClinicalReferences() {
        // As referências clínicas são estáticas e carregadas via Room/Asset
    }

    suspend fun pullAndMergeExams(userId: String) {
        if (userId == guestId) return
        try {
            val remoteExams = apiClient.getExams(userId)
            for (remote in remoteExams) {
                val existing = remote.id?.let { examDao.getLatestByExamId(it) }
                if (existing == null) {
                    val entity = remote.toEntity().copy(pendingSync = false)
                    examDao.insert(entity)
                } else if (existing.pendingSync) {
                    examDao.insert(existing.copy(pendingSync = false))
                }
            }
        } catch (e: Exception) {
            android.util.Log.w("ExamsRepo", "Falha ao puxar exames do servidor: ${e.message}")
        }
    }

    suspend fun uploadExamFile(userId: String, fileName: String, byteArray: ByteArray): String? {
        if (userId == guestId) return null
        return apiClient.uploadExamFile(userId, fileName, byteArray)
    }

    private fun triggerSync() {
        syncScheduler.scheduleSync()
    }
}
