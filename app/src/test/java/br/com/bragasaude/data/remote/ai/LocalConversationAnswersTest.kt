package br.com.bragasaude.data.remote.ai

import br.com.bragasaude.domain.VoiceSessionCommand
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class LocalConversationAnswersTest {
    @Test fun `natural repeat requests include last message variations`() {
        listOf("repete a última mensagem", "você pode repetir a última mensagem que você falou?", "repita a última mensagem que você havia falado", "pode repetir o que você disse", "repete").forEach {
            assertEquals(it, VoiceSessionCommand.REPEAT, VoiceSessionCommand.parse(it))
        }
        assertNull(VoiceSessionCommand.parse("não precisa repetir a última mensagem"))
    }

    @Test fun `repeat extracts speech without executing old action`() = runTest {
        val socket = mockk<OrbWebSocket>()
        val rest = mockk<BragaLocalAiClient>()
        val gateway = OrbChatGateway(socket, rest, mockk())
        val history = listOf("assistant" to """{"fala":"Confira 500 ml na tela","acao":"REGISTRAR_AGUA","parametros":{"quantidade_ml":500}}""", "user" to "repete a última mensagem")
        val reply = JSONObject(gateway.send(history) {}.content)
        assertEquals("Confira 500 ml na tela", reply.getString("fala"))
        assertEquals("CONVERSA", reply.getString("acao"))
        assertEquals(0, reply.getJSONObject("parametros").length())
        coVerify(exactly = 0) { rest.interpretSpeech(any(), any(), any(), any()) }
    }

    @Test fun `calendar answer works without opening network session`() = runTest {
        val rest = mockk<BragaLocalAiClient>()
        val gateway = OrbChatGateway(mockk(), rest, mockk())
        val reply = JSONObject(gateway.send(listOf("user" to "em que ano estamos")) {}.content)
        assertEquals("Estamos em ${java.time.Year.now().value}.", reply.getString("fala"))
        coVerify(exactly = 0) { rest.interpretSpeech(any(), any(), any(), any()) }
    }

    @Test fun `absent session history is stated without inventing a reply`() {
        assertEquals("Ainda não tenho uma resposta nesta conversa para repetir.", LocalConversationAnswers.answer("repete a última mensagem", emptyList()))
    }
}
