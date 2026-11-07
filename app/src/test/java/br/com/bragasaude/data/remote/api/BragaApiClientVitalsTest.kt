package br.com.bragasaude.data.remote.api

import br.com.bragasaude.data.local.security.FamilyE2eeManager
import br.com.bragasaude.data.remote.auth.AuthService
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test

class BragaApiClientVitalsTest {
    private fun client(server: MockWebServer): BragaApiClient {
        val auth = mockk<AuthService>()
        every { auth.getTokenBlocking(any(), any()) } returns "test-session"
        return BragaApiClient(mockk(relaxed = true), server.url("/").toString().trimEnd('/'), auth,
            OkHttpClient(), FamilyE2eeManager())
    }

    private fun page(count: Int, offset: Int = 0): String = (offset until offset + count).joinToString(
        prefix = "[", postfix = "]"
    ) { id -> """{"id":"vital-$id","user_id":"patient","hydration_ml":250,"measured_at":"2026-11-07T12:00:00Z"}""" }

    @Test
    fun `restore reads beyond the first page and keeps same-timestamp measurements`() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(MockResponse().setBody(page(200)))
            server.enqueue(MockResponse().setBody(page(1, 200)))
            val readings = client(server).getVitalSigns("patient")
            assertEquals(201, readings?.size)
            assertEquals("/api/vitals/patient?limit=200&offset=0", server.takeRequest().path)
            assertEquals("/api/vitals/patient?limit=200&offset=200", server.takeRequest().path)
        }
    }

    @Test
    fun `a failed later page does not look like a complete history`() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(MockResponse().setBody(page(200)))
            server.enqueue(MockResponse().setResponseCode(503))
            assertNull(client(server).getVitalSigns("patient"))
        }
    }
}
