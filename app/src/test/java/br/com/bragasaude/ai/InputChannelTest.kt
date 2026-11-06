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
    @Test fun `uri tel crua da nuvem vira numero discavel sem perder o texto`() {
        assertEquals("Ligue para 192 agora.", BragaInputLanguage.forChannel("Ligue para tel:192 agora.", InputChannel.TEXT))
        assertEquals("Apoio no 188.", BragaInputLanguage.forChannel("Apoio no tel: 188.", InputChannel.TEXT))
        assertFalse(BragaInputLanguage.forChannel("Ligue para tel:192 agora.", InputChannel.TEXT).contains("tel:"))
        assertTrue(BragaInputLanguage.withoutRawDialUris("tel:188 e tel:192").contains("188"))
        // Formatos longos também não podem vazar o esquema tel: no balão.
        assertEquals("Fale no +5511987654321.", BragaInputLanguage.forChannel("Fale no tel:+5511987654321.", InputChannel.TEXT))
        assertEquals("Suporte 08001234567.", BragaInputLanguage.forChannel("Suporte tel:08001234567.", InputChannel.TEXT))
        assertEquals("Ligue 11987654321 hoje.", BragaInputLanguage.forChannel("Ligue tel:11987654321 hoje.", InputChannel.TEXT))
        assertFalse(BragaInputLanguage.withoutRawDialUris("tel:+5511987654321 e tel:08001234567").contains("tel:"))
    }
}
