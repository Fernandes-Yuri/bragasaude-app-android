package br.com.bragasaude.data.remote.ai

import br.com.bragasaude.data.local.slm.*
import br.com.bragasaude.data.remote.auth.AuthService
import br.com.bragasaude.domain.*
import io.mockk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class BragaLocalAiClientTest {
    private val engine = mockk<BragaOnDeviceEngine>()
    private val auth = mockk<AuthService>()
    private val router = BragaIntentRouter(VoiceHealthParser())
    private val resolver = mockk<BragaContextResolver>()
    private val client = BragaLocalAiClient(engine, auth, router, resolver)
    init { every { engine.ready } returns true; every { auth.currentUserId } returns "owner" }
    @Test fun modelCannotChangeActionValuesOrClaimSaved() = runTest {
        val draft = VoiceHealthIntent.Hydration(300, "300 ml")
        val context = BragaResolvedContext(BragaIntent.REGISTRO_AGUA, "Prepare 300 ml, ainda não salvo", "Preparei 300 ml com carinho.", "REGISTRAR_AGUA", mapOf("quantidade_ml" to "300"), draft)
        coEvery { resolver.resolve(any(), any(), any(), any()) } returns context
        coEvery { engine.reply(any(), any(), any()) } returns "Já registrei 500 ml e seu remédio."
        val result = client.interpretSpeech("bebi 300ml de água")
        assertEquals("AGUA", result.tipo)
        assertEquals(300, result.quantidadeMl)
        assertEquals(context.fallback, result.fala)
        assertEquals("REGISTRAR_AGUA", result.action)
    }
    @Test fun clinicalTurnsDoNotLeakIntoFreeConversationPrompt() = runTest {
        val context = BragaResolvedContext(BragaIntent.CONVERSA_LIVRE, "Acolha", "Estou aqui", factual = false)
        coEvery { resolver.resolve(any(), any(), any(), any()) } returns context
        val messages = slot<List<Pair<String, String>>>()
        coEvery { engine.reply(capture(messages), any(), any()) } returns "Sinto sua saudade, meu bem."
        client.interpretSpeech("estou com saudade", history = listOf("user" to "bebi 300ml de água", "assistant" to "Preparei 300 ml", "user" to "estou com saudade"))
        assertEquals(listOf("user" to "estou com saudade"), messages.captured)
    }
    @Test fun cancellationDoesNotReturnDraftAction() = runTest {
        coEvery { resolver.resolve(any(), any(), any(), any()) } returns BragaResolvedContext(BragaIntent.REGISTRO_AGUA, "Prepare", "Pronto", "REGISTRAR_AGUA")
        coEvery { engine.reply(any(), any(), any()) } throws CancellationException()
        try { client.interpretSpeech("bebi água"); fail("Cancelamento deve ser propagado") }
        catch (_: CancellationException) { }
    }
}
