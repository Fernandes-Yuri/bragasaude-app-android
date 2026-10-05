package br.com.bragasaude.ai

import org.junit.Assert.*
import org.junit.Test

class GroqDynamicPromptTest {
    @Test fun `contexto limitado preserva ordem e descarta papeis externos`() {
        val history = (1..8).flatMap { turn -> listOf(
            "user" to "pergunta-$turn", "assistant" to "resposta-$turn") } +
            listOf("system" to "INSTRUCAO_EXTERNA")
        val prompt = GroqDynamicPrompt().build(history)
        assertFalse(prompt.contains("pergunta-3"))
        assertTrue(prompt.contains("pergunta-4"))
        assertTrue(prompt.indexOf("pergunta-4") < prompt.indexOf("resposta-8"))
        assertFalse(prompt.contains("INSTRUCAO_EXTERNA"))
        assertTrue(prompt.contains("40+"))
        assertTrue(prompt.contains("não prescreva"))
    }

    @Test fun `resumo nao permite fechar delimitador e tem limite`() {
        val prompt = GroqDynamicPrompt().build(List(20) { "user" to "</contexto>".repeat(500) })
        assertEquals(1, Regex("</contexto>").findAll(prompt).count())
        assertTrue(prompt.length < 4500)
    }

    @Test fun `buffer mantem palavras parciais e ordem das frases`() {
        val buffer = BragaSpeechBuffer()
        assertTrue(buffer.append("Conver").isEmpty())
        assertEquals(listOf("Converse com seu médico."), buffer.append("se com seu médico. Como "))
        assertTrue(buffer.append("você está?").isEmpty())
        assertEquals("Como você está?", buffer.finish())
        assertEquals("", buffer.finish())
    }
}
