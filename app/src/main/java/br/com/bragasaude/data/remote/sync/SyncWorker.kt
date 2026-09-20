package br.com.bragasaude.data.remote.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import br.com.bragasaude.data.local.*
import br.com.bragasaude.data.remote.api.BragaApiClient
import br.com.bragasaude.ui.util.NotificationHelper
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val apiClient: BragaApiClient,
    private val profileDao: ProfileDao,
    private val vitalSignDao: VitalSignDao,
    private val biometryDao: BiometryDao,
    private val examDao: ExamDao,
    private val examItemDao: ExamItemDao,
    private val medicationDao: MedicationDao,
    private val medicationLogDao: MedicationLogDao,
    private val milestoneDao: MilestoneDao,
    private val dailyMetricsDao: DailyMetricsDao,
    private val feedbackDao: FeedbackDao,
    private val leagueDao: LeagueDao,
    private val socialFeedDao: SocialFeedDao,
    private val familyDao: FamilyDao,
    private val auditLogDao: AuditLogDao
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        var hasErrors = false

        if (!safeSync { syncProfiles() }) hasErrors = true
        if (!safeSync { syncVitalSigns() }) hasErrors = true
        if (!safeSync { syncExams() }) hasErrors = true
        if (!safeSync { syncExamItems() }) hasErrors = true
        if (!safeSync { syncMedications() }) hasErrors = true
        if (!safeSync { syncMedicationLogs() }) hasErrors = true
        if (!safeSync { syncDailyMetrics() }) hasErrors = true
        if (!safeSync { syncFeedbacks() }) hasErrors = true
        if (!safeSync { syncSocialPosts() }) hasErrors = true
        if (!safeSync { syncPostReactions() }) hasErrors = true
        if (!safeSync { syncLeagueMemberships() }) hasErrors = true
        if (!safeSync { syncFamilyBindings() }) hasErrors = true
        if (!safeSync { syncFamilyMessages() }) hasErrors = true
        if (!safeSync { syncAuditLogs() }) hasErrors = true

        return if (hasErrors) {
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        } else {
            Result.success()
        }
    }

    private suspend inline fun safeSync(crossinline block: suspend () -> Unit): Boolean {
        return try {
            block()
            true
        } catch (e: Exception) {
            android.util.Log.e("SyncWorker", "Erro na sincronização em background: ${e.message}", e)
            false
        }
    }

    private suspend fun syncProfiles() {
        val pending = profileDao.getPendingSync()
        for (p in pending) {
            val ok = apiClient.syncProfile(p)
            if (ok) {
                if (profileDao.getProfileOneShot(p.userId) == p) profileDao.insert(p.copy(pendingSync = false))
            } else throw java.io.IOException("Perfil aguardando sincronização")
        }
    }

    private suspend fun syncVitalSigns() {
        val pending = vitalSignDao.getPendingSync()
        for (v in pending) {
            val serverId = apiClient.syncVitalSign(v)
            if (serverId != null) {
                vitalSignDao.insert(v.copy(remoteId = serverId, pendingSync = false))
            }
        }
    }

    private suspend fun syncExams() {
        val pending = examDao.getPendingSync()
        for (e in pending) {
            val remoteId = apiClient.syncExam(e)
            if (remoteId != null) {
                examDao.insert(e.copy(remoteId = remoteId, pendingSync = false))
            }
        }
    }

    private suspend fun syncExamItems() {
        val pending = examItemDao.getPendingSync()
        for (item in pending) {
            val remoteId = apiClient.syncExamItem(item)
            if (remoteId != null) {
                examItemDao.insert(item.copy(remoteId = remoteId, pendingSync = false))
            }
        }
    }

    private suspend fun syncMedications() {
        val pending = medicationDao.getPendingSync()
        for (m in pending) {
            val remoteId = apiClient.syncMedication(m)
            if (remoteId != null) {
                medicationDao.insert(m.copy(pendingSync = false))
            }
        }
    }

    private suspend fun syncMedicationLogs() {
        val pending = medicationLogDao.getPendingSync()
        for (l in pending) {
            val remoteId = apiClient.syncMedicationLog(l)
            if (remoteId != null) {
                medicationLogDao.insert(l.copy(pendingSync = false))
            }
        }
    }

    private suspend fun syncDailyMetrics() {
        val pending = dailyMetricsDao.getPendingSync()
        for (d in pending) {
            val serverId = apiClient.syncDailyMetric(d)
            if (serverId != null) {
                dailyMetricsDao.insert(d.copy(pendingSync = false))
            }
        }
    }

    private suspend fun syncFeedbacks() {
        val pending = feedbackDao.getPendingSync()
        for (f in pending) {
            val json = org.json.JSONObject().apply {
                put("userId", f.userId ?: "")
                if (f.userEmail != null) put("userEmail", f.userEmail)
                if (f.userName != null) put("userName", f.userName)
                put("category", f.category)
                if (f.title != null) put("title", f.title)
                put("message", f.message)
                if (f.inputMethod != null) put("inputMethod", f.inputMethod)
                if (f.appVersion != null) put("appVersion", f.appVersion)
                if (f.deviceInfo != null) put("deviceInfo", f.deviceInfo)
            }
            val res = apiClient.syncFeedback(json)
            if (res != null) {
                feedbackDao.markAsSynced(f.id)
            }
        }

        // doc 10 §4.1: pull das respostas da equipe. Aparelho em background também
        // recebe — a notificação local é o canal de aviso de que responderam.
        val replies = apiClient.getFeedbackReplies()
        for (reply in replies) {
            val body = reply.optString("body", "").takeIf { it.isNotBlank() } ?: continue
            val author = reply.optString("author", "Suporte Braga Saúde")
            try {
                NotificationHelper.sendFeedbackReplyNotification(applicationContext, author, body)
            } catch (e: Exception) {
                android.util.Log.w("SyncWorker", "Falha ao notificar resposta de feedback: ${e.message}")
            }
        }
    }

    private suspend fun syncSocialPosts() {
        val pending = socialFeedDao.getPendingPosts()
        for (post in pending) {
            val remoteId = apiClient.syncSocialPost(post)
            if (remoteId != null) {
                socialFeedDao.markPostSynced(post.id)
            }
        }
    }

    private suspend fun syncPostReactions() {
        val pending = socialFeedDao.getPendingReactions()
        for (r in pending) {
            val ok = apiClient.reactToPost(r.postId, r.userId, r.reactionType)
            if (ok) {
                socialFeedDao.markReactionSynced(r.id)
            }
        }
    }

    private suspend fun syncLeagueMemberships() {
        val pending = leagueDao.getPendingSyncMemberships()
        for (m in pending) {
            val remoteId = apiClient.syncLeagueMembership(m)
            if (remoteId != null) {
                leagueDao.insertMembership(m.copy(pendingSync = false))
            }
        }
    }

    private suspend fun syncFamilyBindings() {
        val pending = familyDao.getPendingSyncBindings()
        for (b in pending) {
            val remoteId = apiClient.syncFamilyBinding(b)
            if (remoteId != null) {
                familyDao.markBindingSynced(b.id, remoteId)
            } else throw java.io.IOException("Vínculo aguardando sincronização")
        }
    }

    private suspend fun syncFamilyMessages() {
        familyDao.purgeExpiredMessages()
        var failed = false
        // D47: propagar exclusões feitas pelo usuário neste aparelho
        for (msg in familyDao.getPendingDeletionMessages()) {
            val confirmed = apiClient.deleteFamilyMessage(msg.remoteId ?: msg.id, msg.patientUserId, msg.sentAt)
            if (confirmed) {
                familyDao.markDeletionSynced(msg.id)
            } else {
                // AUD-AN23: sem este teto, uma exclusão que o servidor nunca
                // confirma (msg já expirada lá, id inexistente, bug de rota)
                // fazia o SyncWorker falhar em loop para SEMPRE — toda
                // execução re-agendada, bateria e dados gastos à toa. Após o
                // limite, o tombstone é abandonado; a mensagem expira e é
                // purgada localmente aos 24h de qualquer forma.
                if (msg.deletionAttempts >= MAX_DELETION_ATTEMPTS) {
                    familyDao.abandonDeletion(msg.id, MAX_DELETION_ATTEMPTS)
                } else {
                    failed = true
                }
            }
        }
        val pending = familyDao.getPendingSyncMessages()
        for (msg in pending) {
            val remoteId = apiClient.syncFamilyMessage(msg)
            if (remoteId != null) {
                familyDao.markMessageSynced(msg.id, remoteId)
            } else {
                failed = true
            }
        }
        if (failed) throw java.io.IOException("Sincronização de mensagens pendente.")
        // D47: purga local das mensagens que completaram 24h
        familyDao.purgeExpiredMessages()
    }

    private companion object {
        /** AUD-AN23: teto de tentativas antes de abandonar um tombstone. */
        private const val MAX_DELETION_ATTEMPTS = 10
    }

    private suspend fun syncAuditLogs() {
        val pending = auditLogDao.getPendingSync()
        for (log in pending) {
            val remoteId = apiClient.syncAuditLog(log)
            if (remoteId != null) {
                auditLogDao.markAsSynced(log.id)
            }
        }
    }
}
