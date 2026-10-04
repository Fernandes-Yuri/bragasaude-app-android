package br.com.bragasaude.ai

import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class InputChannelTest {
    @Test fun `texto usa quatro variacoes sem reparacao auditiva`() {
        var previous = ""
        repeat(20) {
            val response = BragaNluEngine.analisar("", InputChannel.TEXT).respostaLocal!!
            assertNotEquals(previous, response)
            assertFalse(response.contains("ouvir"))
            assertFalse(response.contains("fala"))
            previous = response
        }
        assertTrue(BragaNluEngine.analisar("", InputChannel.VOICE).respostaLocal!!.isNotBlank())
    }
    @Test fun `reparacao de audio remoto nao vaza por fragmentos no canal texto`() = runTest {
        val hybrid = BragaHybridOrchestrator(GroqStreamSource { _, _ ->
            flowOf("Não consegui ", "te ouvir. Fale mais alto.")
        }, GroqDynamicPrompt())
        val events = hybrid.respond("Qual a diferença entre apneia e alterações hormonais?", emptyList(), InputChannel.TEXT).toList()
        assertFalse((events.first() as BragaHybridEvent.Delta).text.contains("ouvir"))
        assertFalse((events.last() as BragaHybridEvent.Completed).text.contains("ouvir"))
        assertEquals((events.first() as BragaHybridEvent.Delta).text, (events.last() as BragaHybridEvent.Completed).text)
    }
    @Test fun `modalidade segue no contexto sem bloquear conversas sobre musica`() {
        val text = GroqDynamicPrompt().build(emptyList(), InputChannel.TEXT)
        val voice = GroqDynamicPrompt().build(emptyList(), InputChannel.VOICE)
        assertTrue(text.contains("TEXT"))
        assertTrue(voice.contains("VOICE"))
        assertEquals("Gosto de ouvir música.", BragaInputLanguage.forChannel("Gosto de ouvir música.", InputChannel.TEXT))
    }
}
