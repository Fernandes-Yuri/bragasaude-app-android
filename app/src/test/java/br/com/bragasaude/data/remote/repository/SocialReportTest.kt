package br.com.bragasaude.data.remote.repository

import android.content.Context
import br.com.bragasaude.data.local.FeedbackDao
import br.com.bragasaude.data.local.FeedbackEntity
import br.com.bragasaude.data.local.SocialPostEntity
import br.com.bragasaude.data.remote.api.BragaApiClient
import br.com.bragasaude.data.remote.sync.SyncScheduler
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class SocialReportTest {
    private val context = mockk<Context>()
    private val dao = mockk<FeedbackDao>(relaxed = true)
    private val auth = mockk<FirebaseAuth>()
    private val user = mockk<FirebaseUser>()
    private val scheduler = mockk<SyncScheduler>(relaxed = true)
    private val api = mockk<BragaApiClient>()
    private val post = mockk<SocialPostEntity>()
    private val saved = slot<FeedbackEntity>()
    init {
        every { auth.currentUser } returns user
        every { user.uid } returns "reporter"
        every { post.id } returns "post-id"
        every { post.userId } returns "author-id"
        coEvery { dao.insert(capture(saved)) } just Runs
    }
    private fun repository() = FeedbackRepository(context, dao, auth, scheduler, api)

    @Test fun `offline report is queued with identifiers and no diagnostic data`() = runBlocking {
        coEvery { api.syncFeedback(any()) } returns null
        val message = repository().reportSocialPost("reporter", post, "Assédio")
        assertTrue(message.contains("guardada"))
        assertEquals("reporter", saved.captured.userId)
        assertEquals("denuncia_social", saved.captured.category)
        assertTrue(saved.captured.message.contains("post-id"))
        assertTrue(saved.captured.message.contains("author-id"))
        assertTrue(saved.captured.pendingSync)
        assertNull(saved.captured.deviceInfo)
        coVerify(exactly = 0) { dao.markAsSynced(any()) }
        verify { scheduler.scheduleSync() }
    }
    @Test fun `confirmed report is marked delivered`() = runBlocking {
        coEvery { api.syncFeedback(any()) } answers { firstArg<org.json.JSONObject>().getString("id") }
        assertTrue(repository().reportSocialPost("reporter", post, "Spam").contains("enviada"))
        coVerify { dao.markAsSynced(saved.captured.id) }
    }
    @Test fun `account change prevents saving report under another user`() = runBlocking {
        assertTrue(runCatching { repository().reportSocialPost("other-account", post, "Spam") }.isFailure)
        coVerify(exactly = 0) { dao.insert(any()) }
        coVerify(exactly = 0) { api.syncFeedback(any()) }
    }
}
