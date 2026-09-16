package br.com.bragasaude.domain

import org.junit.Assert.*
import org.junit.Test

class VoiceConversationMemoryTest {
    @Test fun `session remembers real user and assistant turns in order`() {
        val memory = VoiceConversationMemory()
        memory.selectUser("a")
        memory.recordUser("Meu jardim tem rosas")
        memory.recordAssistant("De que cor são?")
        memory.recordUser("São vermelhas")
        assertEquals(listOf("user" to "Meu jardim tem rosas", "assistant" to "De que cor são?", "user" to "São vermelhas"), memory.snapshot())
        assertEquals("De que cor são?", memory.lastResponse)
    }

    @Test fun `closing or switching account removes context and last response`() {
        val memory = VoiceConversationMemory()
        memory.selectUser("a")
        memory.recordAssistant("Resposta privada")
        memory.selectUser("b")
        assertTrue(memory.snapshot().isEmpty())
        assertEquals("", memory.lastResponse)
        memory.recordAssistant("Outra resposta")
        memory.clear()
        assertTrue(memory.snapshot().isEmpty())
        assertEquals("", memory.lastResponse)
    }

    @Test fun `history is bounded and snapshots remain stable`() {
        val memory = VoiceConversationMemory()
        repeat(21) { memory.recordUser("Pergunta $it") }
        val snapshot = memory.snapshot()
        assertEquals(20, snapshot.size)
        assertEquals("Pergunta 1", snapshot.first().second)
        memory.recordAssistant("Resposta")
        assertEquals("Pergunta 20", snapshot.last().second)
    }
}
