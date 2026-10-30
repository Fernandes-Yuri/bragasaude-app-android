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
            assertFalse(api.removePostReaction("post", "owner"))
            server.takeRequest()
            server.enqueue(MockResponse().setBody("""{"status":"success"}"""))
            assertTrue(api.removePostReaction("post", "owner"))
            val request = server.takeRequest()
            assertEquals("DELETE", request.method)
            assertEquals("/api/social/posts/post/reaction?expected_user_id=owner", request.path)
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

    @Test fun reportAndBlockUseAuthenticatedAccountBoundContracts() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            val api = client(server)
            server.enqueue(MockResponse().setResponseCode(201).setBody("""{"status":"reported"}"""))
            api.reportSocialPost("post", "reader", "misinformation", "  Detalhes  ")
            val report = server.takeRequest()
            assertEquals("POST", report.method)
            assertEquals("/api/social/posts/post/report?expected_user_id=reader", report.path)
            assertEquals("Bearer test-session", report.getHeader("Authorization"))
            val body = org.json.JSONObject(report.body.readUtf8())
            assertEquals("misinformation", body.getString("reason"))
            assertEquals("Detalhes", body.getString("details"))
            assertFalse(body.has("author_id"))
            server.enqueue(MockResponse().setBody("""{"status":"offline_mode"}"""))
            assertFalse(api.blockSocialUser("author", "reader"))
            server.takeRequest()
            server.enqueue(MockResponse().setBody("""{"status":"blocked"}"""))
            assertTrue(api.blockSocialUser("author", "reader"))
            assertEquals("/api/social/users/author/block?expected_user_id=reader", server.takeRequest().path)
            server.enqueue(MockResponse().setBody("""{"hidden_user_ids":["author"]}"""))
            assertEquals(setOf("author"), api.getSocialHiddenUsers("reader"))
            assertEquals("/api/social/blocks?expected_user_id=reader", server.takeRequest().path)
        }
    }

    @Test fun rejectedReportAndUnavailableBlocksNeverBecomeSuccess() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            val api = client(server)
            server.enqueue(MockResponse().setResponseCode(403).setBody("""{"detail":"Publicação própria."}"""))
            try { api.reportSocialPost("post", "reader", "spam", ""); fail("Denúncia recusada deve falhar") }
            catch (e: java.io.IOException) { assertEquals("Publicação própria.", e.message) }
            server.enqueue(MockResponse().setResponseCode(503))
            try { api.getSocialHiddenUsers("reader"); fail("Falha não pode apagar bloqueios locais") }
            catch (_: java.io.IOException) { }
        }
    }
}
