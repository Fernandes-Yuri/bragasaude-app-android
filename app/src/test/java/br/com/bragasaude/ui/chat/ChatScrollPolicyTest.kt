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

    @Test fun `controle conta resposta parcial uma vez e nao duplica mensagem final`() {
        val messages = listOf(ChatScrollMessage("assistant-0", false), ChatScrollMessage("user-1", true))
        val initial = base.copy(messageCount = messages.size, messages = messages)
        policy.update(initial)
        assertTrue(policy.update(initial.copy(dragging = true, atBottom = false)).showReturnToLatest)
        assertEquals(0, policy.update(initial.copy(atBottom = false)).unreadMessages)
        val streaming = initial.copy(atBottom = false, streaming = true, partialLength = 10)
        assertEquals(1, policy.update(streaming).unreadMessages)
        repeat(10) { assertEquals(1, policy.update(streaming.copy(partialLength = 20 + it)).unreadMessages) }
        val completed = initial.copy(atBottom = false, messageCount = 3,
            messages = messages + ChatScrollMessage("assistant-1", false))
        assertEquals(1, policy.update(completed).unreadMessages)
        assertEquals(2, policy.update(completed.copy(messageCount = 4,
            messages = completed.messages + ChatScrollMessage("assistant-2", false))).unreadMessages)
    }

    @Test fun `resposta parcial vista antes de ler historico nao vira nova mensagem`() {
        val initial = base.copy(messages = listOf(ChatScrollMessage("user-1", true)), messageCount = 1,
            streaming = true, partialLength = 20)
        policy.update(initial)
        policy.update(initial.copy(dragging = true, atBottom = false))
        assertEquals(0, policy.update(initial.copy(atBottom = false, partialLength = 80)).unreadMessages)
        assertEquals(0, policy.update(initial.copy(atBottom = false, streaming = false, partialLength = 0,
            messageCount = 2, messages = initial.messages + ChatScrollMessage("assistant-1", false))).unreadMessages)
    }

    @Test fun `retorno explicito limpa contador e retoma acompanhamento`() {
        val initial = base.copy(messages = listOf(ChatScrollMessage("user-1", true)), messageCount = 1)
        policy.update(initial)
        policy.update(initial.copy(dragging = true, atBottom = false))
        val incoming = initial.copy(atBottom = false, messageCount = 2,
            messages = initial.messages + ChatScrollMessage("assistant-1", false))
        assertEquals(1, policy.update(incoming).unreadMessages)
        val resumed = policy.update(incoming.copy(returnRequest = 1))
        assertTrue(resumed.shouldFollow)
        assertFalse(resumed.showReturnToLatest)
        assertEquals(0, resumed.unreadMessages)
        assertTrue(policy.update(incoming.copy(returnRequest = 1, partialLength = 100)).shouldFollow)
    }

    @Test fun `outra conversa mensagem propria e exclusao nao herdam contador`() {
        val initial = base.copy(messages = listOf(ChatScrollMessage("user-1", true)), messageCount = 1)
        policy.update(initial)
        policy.update(initial.copy(dragging = true, atBottom = false))
        val incoming = initial.copy(atBottom = false, messageCount = 2,
            messages = initial.messages + ChatScrollMessage("assistant-1", false))
        assertEquals(1, policy.update(incoming).unreadMessages)
        assertEquals(0, policy.update(initial.copy(atBottom = false)).unreadMessages)
        assertEquals(1, policy.update(incoming).unreadMessages)
        val sent = policy.update(incoming.copy(latestUser = "user-2",
            messages = incoming.messages + ChatScrollMessage("user-2", true), messageCount = 3))
        assertTrue(sent.shouldFollow)
        assertEquals(0, sent.unreadMessages)
        policy.update(incoming.copy(history = true))
        val opened = policy.update(initial.copy(firstMessage = "another", latestUser = "another-user", atBottom = false))
        assertTrue(opened.shouldFollow)
        assertEquals(0, opened.unreadMessages)
    }

    @Test fun `reducao do viewport nao retoma acompanhamento durante leitura manual`() {
        policy.update(base)
        policy.update(base.copy(dragging = true, atBottom = false))
        val keyboard = policy.update(base.copy(atBottom = false, viewportEnd = 300, partialLength = 120))
        assertFalse(keyboard.shouldFollow)
        assertTrue(keyboard.showReturnToLatest)
    }
}
