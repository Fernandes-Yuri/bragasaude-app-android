package br.com.bragasaude.data.remote.ai

import br.com.bragasaude.data.local.slm.BragaOnDeviceEngine
import io.mockk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.Assert.*

class OrbChatGatewayTest {
    @Test fun missingModelBlocksChatBeforeInference() = runTest {
        val engine = mockk<BragaOnDeviceEngine>()
        every { engine.ready } returns false
        val client = mockk<BragaLocalAiClient>()
        val gateway = OrbChatGateway(client, engine)
        try { gateway.send(listOf("user" to "Olá")) {}; fail("Deveria bloquear") }
        catch (e: IllegalStateException) { assertTrue(e.message!!.contains("Instale o Braga")) }
        coVerify(exactly = 0) { client.interpretSpeech(any(), any(), any(), any(), any(), any()) }
    }
    @Test fun inferenceFailureAndCancellationHaveNoRemoteFallback() = runTest {
        for (failure in listOf(IllegalStateException("Falha local"), CancellationException("Cancelado"))) {
            val engine = mockk<BragaOnDeviceEngine>()
            every { engine.ready } returns true
            val client = mockk<BragaLocalAiClient>()
            coEvery { client.interpretSpeech(any(), any(), any(), any(), any(), any()) } throws failure
            val gateway = OrbChatGateway(client, engine)
            try { gateway.send(listOf("user" to "Estou sozinho")) {}; fail("Deveria falhar") }
            catch (e: Exception) { assertSame(failure, e) }
            coVerify(exactly = 1) { client.interpretSpeech(any(), any(), any(), any(), any(), any()) }
        }
    }
}
