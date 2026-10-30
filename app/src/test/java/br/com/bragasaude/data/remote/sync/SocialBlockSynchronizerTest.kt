package br.com.bragasaude.data.remote.sync

import br.com.bragasaude.data.local.BlockedUserEntity
import br.com.bragasaude.data.local.SocialFeedDao
import br.com.bragasaude.data.remote.api.BragaApiClient
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class SocialBlockSynchronizerTest {
    private val dao = mockk<SocialFeedDao>(relaxed = true)
    private val api = mockk<BragaApiClient>()
    private val auth = mockk<FirebaseAuth>()
    private val user = mockk<FirebaseUser>()
    private val sync = SocialBlockSynchronizer(dao, api, auth)

    private fun session() {
        every { auth.currentUser } returns user
        every { user.uid } returns "reader"
        coEvery { dao.getBlocks("reader") } returns listOf(BlockedUserEntity("reader", "author", pendingSync = true))
    }

    @Test fun networkFailureRemainsPendingUntilAnAcknowledgedRetry() = runBlocking {
        session()
        coEvery { api.blockSocialUser("author", "reader") } returnsMany listOf(false, true)
        assertFalse(sync.syncPending("reader"))
        coVerify(exactly = 0) { dao.acknowledgeBlock(any(), any()) }
        assertTrue(sync.syncPending("reader"))
        coVerify(exactly = 1) { dao.acknowledgeBlock("reader", "author") }
    }

    @Test fun accountSwitchDoesNotSendOrAcknowledgeAnothersBlock() = runBlocking {
        session()
        every { user.uid } returns "other"
        try { sync.syncPending("reader"); fail("Conta diferente aceita") } catch (_: IllegalStateException) { }
        coVerify(exactly = 0) { api.blockSocialUser(any(), any()) }
        every { user.uid } returns "reader"
        coEvery { api.blockSocialUser("author", "reader") } answers { every { user.uid } returns "other"; true }
        try { sync.syncPending("reader"); fail("Resposta de outra sessão aceita") } catch (_: IllegalStateException) { }
        coVerify(exactly = 0) { dao.acknowledgeBlock(any(), any()) }
    }

    @Test fun failedBlockRefreshDoesNotEraseLocalProtection() = runBlocking {
        session()
        coEvery { api.blockSocialUser("author", "reader") } returns false
        coEvery { api.getSocialHiddenUsers("reader") } throws java.io.IOException("offline")
        try { sync.refresh("reader"); fail("Falha de rede ignorada") } catch (_: java.io.IOException) { }
        coVerify(exactly = 0) { dao.reconcileBlocks(any(), any()) }
    }
}
