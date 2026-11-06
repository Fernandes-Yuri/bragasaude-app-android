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

    @Test fun `persona acolhedora de cuidador sem travas de modulo removido`() {
        val prompt = GroqDynamicPrompt().build(emptyList())
        assertTrue(prompt.contains("cuidador"))
        assertTrue(prompt.contains("acolhedora"))
        assertTrue(prompt.contains("alimentação"))
        assertTrue(prompt.contains("sono"))
        assertTrue(prompt.contains("nutricionista"))
        assertTrue(prompt.contains("CVV 188"))
        assertTrue(prompt.contains("SAMU 192"))
        assertTrue(prompt.indexOf("CVV 188") < prompt.indexOf("SAMU 192"))
        assertFalse(prompt.lowercase().contains("descontinuado"))
        assertFalse(prompt.lowercase().contains("modulo desativado"))
        assertFalse(prompt.contains("O módulo de alimentação, compras, despensa e receitas"))
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
