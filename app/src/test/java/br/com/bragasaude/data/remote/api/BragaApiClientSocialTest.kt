package br.com.bragasaude.data.remote.api

import br.com.bragasaude.data.remote.auth.AuthService
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test

class BragaApiClientSocialTest {
    private fun client(server: MockWebServer): BragaApiClient {
        val auth = mockk<AuthService>()
        every { auth.getTokenBlocking(any(), any()) } returns "test-session"
        return BragaApiClient(mockk(relaxed = true), server.url("/").toString().trimEnd('/'), auth)
    }

    @Test fun offlineModeIsNotAcknowledgmentAndDeletionUsesAuthenticatedEndpoint() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            val api = client(server)
            server.enqueue(MockResponse().setBody("""{"status":"offline_mode"}"""))
            assertFalse(api.reactToPost("post", "owner"))
            server.takeRequest()
            server.enqueue(MockResponse().setResponseCode(503))
            assertFalse(api.removePostReaction("post"))
            server.takeRequest()
            server.enqueue(MockResponse().setBody("""{"status":"success"}"""))
            assertTrue(api.removePostReaction("post"))
            val request = server.takeRequest()
            assertEquals("DELETE", request.method)
            assertEquals("/api/social/posts/post/reaction", request.path)
            assertEquals("Bearer test-session", request.getHeader("Authorization"))
        }
    }

    @Test fun feedRetainsViewerSelection() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(MockResponse().setBody("""[{"id":"post","user_id":"author","post_type":"milestone","title":"Meta","created_at":"2026-10-27T12:00:00Z","has_user_reacted":true,"user_reaction_type":"palmas"}]"""))
            val post = client(server).getSocialFeed("reader").single()
            assertTrue(post.hasUserReacted)
            assertEquals("palmas", post.viewerReactionType)
        }
    }
}
