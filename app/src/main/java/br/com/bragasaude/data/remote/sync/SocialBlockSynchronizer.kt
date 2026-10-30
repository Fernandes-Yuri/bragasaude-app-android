package br.com.bragasaude.data.remote.sync

import br.com.bragasaude.data.local.SocialFeedDao
import br.com.bragasaude.data.remote.api.BragaApiClient
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SocialBlockSynchronizer @Inject constructor(
    private val dao: SocialFeedDao,
    private val api: BragaApiClient,
    private val auth: FirebaseAuth
) {
    private val lock = Mutex()

    private suspend fun requireSession(userId: String) {
        currentCoroutineContext().ensureActive()
        check(auth.currentUser?.uid == userId) { "Sua conta mudou. Abra o mural novamente." }
    }

    suspend fun syncPending(userId: String): Boolean = lock.withLock { sendPending(userId) }

    suspend fun refresh(userId: String) = lock.withLock {
        sendPending(userId)
        requireSession(userId)
        val hidden = api.getSocialHiddenUsers(userId)
        requireSession(userId)
        dao.reconcileBlocks(userId, hidden)
    }

    private suspend fun sendPending(userId: String): Boolean {
        requireSession(userId)
        var success = true
        for (block in dao.getBlocks(userId).filter { it.pendingSync }) {
            requireSession(userId)
            val saved = api.blockSocialUser(block.blockedUserId, userId)
            requireSession(userId)
            if (saved) dao.acknowledgeBlock(userId, block.blockedUserId) else success = false
        }
        return success
    }
}
