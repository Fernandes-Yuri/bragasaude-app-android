package br.com.bragasaude.ui.chat

import org.junit.Assert.*
import org.junit.Test

class ChatScrollPolicyTest {
    private val policy = ChatScrollPolicy()
    private val base = ChatScrollSnapshot("first", "user-1", 10)

    @Test fun `acompanha mensagem nova e crescimento do streaming`() {
        assertTrue(policy.follow(base))
        assertTrue(policy.follow(base.copy(atBottom = false, partialLength = 500, streaming = true)))
        assertTrue(policy.follow(base.copy(atBottom = false, messageCount = 11)))
    }
    @Test fun `leitura manual nao sofre saltos por mensagens remotas ou tokens`() {
        policy.follow(base)
        assertFalse(policy.follow(base.copy(dragging = true, atBottom = false)))
        assertFalse(policy.follow(base.copy(atBottom = false, partialLength = 100)))
        assertFalse(policy.follow(base.copy(atBottom = false, messageCount = 11)))
    }
    @Test fun `retorno ao fim retoma acompanhamento`() {
        policy.follow(base)
        policy.follow(base.copy(dragging = true, atBottom = false))
        assertTrue(policy.follow(base.copy(atBottom = true)))
        assertTrue(policy.follow(base.copy(atBottom = false, partialLength = 100)))
    }
    @Test fun `mensagem propria nova e conversa aberta voltam ao fim`() {
        policy.follow(base)
        policy.follow(base.copy(dragging = true, atBottom = false))
        assertTrue(policy.follow(base.copy(latestUser = "user-2", atBottom = false)))
        assertFalse(policy.follow(base.copy(history = true)))
        assertTrue(policy.follow(base.copy(firstMessage = "another", atBottom = false)))
    }
}
