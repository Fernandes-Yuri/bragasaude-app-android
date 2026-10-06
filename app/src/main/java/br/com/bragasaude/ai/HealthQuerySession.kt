package br.com.bragasaude.ai

import javax.inject.Inject

/** Contexto só da sessão; não armazena leituras nem valores clínicos. */
class HealthQuerySession internal constructor(private val nowMillis: () -> Long) {
    @Inject constructor() : this({ System.currentTimeMillis() })

    private data class Context(val query: HealthQuery, val rememberedAt: Long, val turn: Long)
    private var owner: String? = null
    private var conversation: String? = null
    private var turn = 0L
    private var context: Context? = null

    /** Chamar uma vez por entrada, antes de resolver. Reanalisar não consome mais turnos. */
    @Synchronized fun advanceTurn(userId: String, conversationId: String) {
        bind(userId, conversationId)
        turn++
        expire()
    }

    @Synchronized fun resolve(text: String, userId: String, conversationId: String): HealthQuery? {
        bind(userId, conversationId)
        expire()
        return context?.let { HealthQueryResolver.followUp(text, it.query) }
            ?: HealthQueryResolver.explicit(text)
    }

    @Synchronized fun recentQuery(userId: String, conversationId: String): HealthQuery? {
        bind(userId, conversationId)
        expire()
        return context?.query
    }

    /** Chamar apenas se a consulta terminou com dados, no dono e na conversa ainda ativos. */
    @Synchronized fun remember(query: HealthQuery, userId: String, conversationId: String) {
        if (userId.isBlank() || userId == "anonymous") return
        // Uma resposta antiga não pode recolocar o contexto de uma conta/conversa encerrada.
        if (owner != userId || conversation != conversationId) return
        context = Context(query, nowMillis(), turn)
    }

    @Synchronized fun clear() {
        owner = null
        conversation = null
        turn = 0L
        context = null
    }

    private fun bind(userId: String, conversationId: String) {
        if (owner != userId || conversation != conversationId) {
            owner = userId
            conversation = conversationId
            turn = 0L
            context = null
        }
    }

    private fun expire() {
        val value = context ?: return
        val age = nowMillis() - value.rememberedAt
        if (age < 0 || age >= 5 * 60_000L || turn - value.turn > 2) context = null
    }
}
