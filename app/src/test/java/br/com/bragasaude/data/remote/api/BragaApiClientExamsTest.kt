package br.com.bragasaude.data.remote.api

import br.com.bragasaude.data.remote.auth.AuthService
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*

class BragaApiClientExamsTest {
    private lateinit var server: MockWebServer
    private lateinit var api: BragaApiClient
    @Before fun setup() {
        server = MockWebServer().also { it.start() }
        val auth = mockk<AuthService>()
        every { auth.getTokenBlocking(any(), any()) } returns "token-de-teste"
        api = BragaApiClient(mockk(relaxed = true), server.url("/").toString().trimEnd('/'), auth)
    }
    @After fun cleanup() { server.shutdown() }

    @Test fun `somente ausencia confirmada do exame torna exclusao idempotente`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(404).setBody("""{"detail":"Exame não encontrado."}"""))
        assertTrue(api.deleteExam("exame"))
        assertEquals("DELETE", server.takeRequest().method)
        server.enqueue(MockResponse().setResponseCode(404).setBody("""{"detail":"Not Found"}"""))
        assertFalse(api.deleteExam("exame"))
        server.enqueue(MockResponse().setResponseCode(403).setBody("""{"detail":"Outra conta"}"""))
        assertFalse(api.deleteExam("exame"))
    }

    @Test fun `contrato de resultados exige confirmacao da atualizacao completa`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(201).setBody("""{"success":true,"snapshot_applied":true}"""))
        assertTrue(api.syncManualExam("exame", "Hemograma", "Laboratorial", "2026-10-05", emptyList()))
        val request = server.takeRequest()
        val payload = JSONObject(request.body.readUtf8())
        assertTrue(payload.getBoolean("replace_items"))
        assertTrue(payload.getBoolean("cloud_consent"))
        assertEquals("Bearer token-de-teste", request.getHeader("Authorization"))
        server.enqueue(MockResponse().setResponseCode(201).setBody("""{"success":true}"""))
        assertFalse(api.syncManualExam("exame", "Hemograma", "Laboratorial", "2026-10-05", emptyList()))
    }

    @Test fun `descoberta de capacidade nao transmite conteudo do exame`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"snapshot_updates":true}"""))
        assertTrue(api.supportsExamSnapshots())
        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/api/exams-sync/capabilities", request.path)
        assertEquals(0L, request.bodySize)
    }
}
