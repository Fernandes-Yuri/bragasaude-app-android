package br.com.bragasaude.data.remote.ai

import br.com.bragasaude.data.remote.auth.AuthService
import io.mockk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.Assert.*
import java.io.IOException

class OrbChatGatewayTest {
    @Test fun unresolvedRepeatDoesNotReturnContextPromptAsLocalAnswer() = runTest {
        val rest = mockk<BragaLocalAiClient>()
        val history = listOf("assistant" to "Resumo interno para a API", "user" to "repete")
        coEvery { rest.interpretSpeech(any(), false, any(), any(), any(), any()) } returns
            BragaAiResult("CONVERSA", "Qual informação você deseja retomar?")
        val gateway = OrbChatGateway(mockk(), rest, mockk())
        val reply = gateway.sendRemote(history, onPartial = {})
        assertFalse(reply.content.contains("Resumo interno"))
        assertTrue(reply.content.contains("Qual informação"))
        coVerify(exactly = 1) { rest.interpretSpeech("repete", false, history, any(), any(), any()) }
    }

    @Test fun transportFailureUsesRestOnce() = runTest {
        val session = mockk<OrbWebSocket.Session>(relaxed = true)
        every { session.state } returns MutableStateFlow(OrbConnectionState.CONNECTED)
        coEvery { session.chat(any(), any(), any(), any()) } throws IOException("Offline")
        val socket = mockk<OrbWebSocket>()
        every { socket.openSession(any(), any(), any()) } returns session
        val rest = mockk<BragaLocalAiClient>()
        every { rest.serverBaseUrl } returns "http://localhost"
        coEvery { rest.interpretSpeech(any(), false, any(), any(), any(), any()) } returns BragaAiResult("CONVERSA", "Resposta REST")
        val authService = mockk<AuthService>()
        every { authService.currentUserId } returns "owner"
        val gateway = OrbChatGateway(socket, rest, authService)
        gateway.open(backgroundScope)
        try {
            val reply = gateway.send(listOf("user" to "Oi"), onPartial = {})
            assertTrue(reply.content.contains("Resposta REST"))
            coVerify(exactly = 1) { rest.interpretSpeech("Oi", false, any(), any(), any(), any()) }
        } finally { gateway.close() }
    }

    @Test fun safetyAndCancellationNeverUseRest() = runTest {
        for (failure in listOf(OrbRejectedException("Bloqueado"), CancellationException("Cancelado"))) {
            val session = mockk<OrbWebSocket.Session>(relaxed = true)
            every { session.state } returns MutableStateFlow(OrbConnectionState.CONNECTED)
            coEvery { session.chat(any(), any(), any(), any()) } throws failure
            val socket = mockk<OrbWebSocket>()
            every { socket.openSession(any(), any(), any()) } returns session
            val rest = mockk<BragaLocalAiClient>()
            every { rest.serverBaseUrl } returns "http://localhost"
            val authService = mockk<AuthService>()
            every { authService.currentUserId } returns "owner"
            val gateway = OrbChatGateway(socket, rest, authService)
            gateway.open(backgroundScope)
            try {
                try { gateway.send(listOf("user" to "Oi"), onPartial = {}); fail("Expected failure") }
                catch (e: Exception) { assertSame(failure, e) }
                coVerify(exactly = 0) { rest.interpretSpeech(any(), any(), any(), any()) }
            } finally { gateway.close() }
        }
    }
    @Test fun bothTransportsRejectEducationalEmergencyAndSuppressUnvalidatedPartials() = runTest {
        for (useSocket in listOf(true, false)) {
            val received = """{"fala":"Já abri as opções de socorro.","acao":"EMERGENCIA","parametros":{}}"""
            val rest = mockk<BragaLocalAiClient>()
            every { rest.serverBaseUrl } returns "http://localhost"
            coEvery { rest.interpretSpeech(any(), false, any(), any(), any(), any()) } returns
                BragaAiResult("EMERGENCIA", "Já abri as opções de socorro.", rawResponse = received)
            val socket = mockk<OrbWebSocket>()
            val session = mockk<OrbWebSocket.Session>(relaxed = true)
            every { session.state } returns MutableStateFlow(OrbConnectionState.CONNECTED)
            coEvery { session.chat(any(), any(), any(), any()) } coAnswers {
                arg<(String) -> Unit>(3)("Já abri as opções de socorro.")
                OrbReply(received)
            }
            every { socket.openSession(any(), any(), any()) } returns session
            val auth = mockk<AuthService>()
            every { auth.currentUserId } returns "owner"
            val gateway = OrbChatGateway(socket, rest, auth)
            val partials = mutableListOf<String>()
            if (useSocket) gateway.open(backgroundScope)
            try {
                val reply = gateway.sendRemote(listOf("user" to "o que significa dor no peito?"), onPartial = { partials.add(it) })
                assertTrue(reply.content.contains("CONVERSA"))
                assertFalse(reply.content.contains("Já abri"))
                assertTrue(partials.all { it.isBlank() })
            } finally { gateway.close() }
        }
    }
}
