package br.com.bragasaude.data.remote.ai

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import io.mockk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.Assert.*
import java.io.IOException

class OrbChatGatewayTest {
    @Test fun transportFailureUsesRestOnce() = runTest {
        val session = mockk<OrbWebSocket.Session>(relaxed = true)
        every { session.state } returns MutableStateFlow(OrbConnectionState.CONNECTED)
        coEvery { session.chat(any(), any(), any(), any()) } throws IOException("Offline")
        val socket = mockk<OrbWebSocket>()
        every { socket.openSession(any(), any(), any()) } returns session
        val rest = mockk<BragaLocalAiClient>()
        every { rest.serverBaseUrl } returns "http://localhost"
        coEvery { rest.interpretSpeech(any(), false, any(), any(), any(), any()) } returns BragaAiResult("CONVERSA", "Resposta REST")
        val auth = mockk<FirebaseAuth>()
        val user = mockk<FirebaseUser>()
        every { user.uid } returns "owner"
        every { auth.currentUser } returns user
        val gateway = OrbChatGateway(socket, rest, auth)
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
            val auth = mockk<FirebaseAuth>()
            val user = mockk<FirebaseUser>()
            every { user.uid } returns "owner"
            every { auth.currentUser } returns user
            val gateway = OrbChatGateway(socket, rest, auth)
            gateway.open(backgroundScope)
            try {
                try { gateway.send(listOf("user" to "Oi"), onPartial = {}); fail("Expected failure") }
                catch (e: Exception) { assertSame(failure, e) }
                coVerify(exactly = 0) { rest.interpretSpeech(any(), any(), any(), any()) }
            } finally { gateway.close() }
        }
    }
}
