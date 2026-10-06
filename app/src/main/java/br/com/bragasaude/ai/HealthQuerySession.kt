package br.com.bragasaude.ai

import javax.inject.Inject

/** Contexto efêmero da sessão. A última resposta local não é persistida nem compartilhada. */
class HealthQuerySession internal constructor(private val nowMillis: () -> Long) {
    @Inject constructor() : this({ System.currentTimeMillis() })

    private data class Context(val query: HealthQuery, val rememberedAt: Long, val turn: Long)
    private var owner: String? = null
    private var conversation: String? = null
    private var turn = 0L
    private var context: Context? = null
    private data class Reply(val output: NluOutput, val rememberedAt: Long, val turn: Long)
    private var reply: Reply? = null

    /** Chamar uma vez por entrada, antes de resolver. Reanalisar não consome mais turnos. */
    @Synchronized fun advanceTurn(userId: String, conversationId: String): Long {
        bind(userId, conversationId)
        turn++
        expire()
        return turn
    }

    @Synchronized internal fun contextualAnswer(text: String, userId: String, conversationId: String): NluOutput? {
        bind(userId, conversationId)
        expire()
        val request = BragaContextualHelp.request(text) ?: return null
        return BragaContextualHelp.answer(request, reply?.output, nowMillis())
    }

    /** Somente após entregar uma resposta local no turno ainda ativo. */
    @Synchronized fun rememberReply(output: NluOutput, userId: String, conversationId: String, expectedTurn: Long) {
        if (owner != userId || conversation != conversationId || turn != expectedTurn ||
            userId.isBlank() || userId == "anonymous") return
        expire()
        if (output.intent in setOf("conversa_confirmacao_compreensao", "conversa_agradecimento")) return
        if (output.intent in setOf("ajuda_reformulacao_contextual", "ajuda_repeticao_contextual", "ajuda_data_registro_contextual")) {
            reply = reply?.let { it.copy(output = it.output.copy(respostaLocal = output.respostaLocal)) }
            return
        }
        if (output.isBloqueioSeguranca || output.isEmergencia || output.delegarParaNuvem || output.intent.startsWith("entrada_")) {
            forgetReferences(userId, conversationId, expectedTurn)
            return
        }
        if (!BragaHealthMemory.supports(output.intent)) context = null
        else if (output.hasLocalData != true) context = null
        reply = output.respostaLocal?.takeIf { it.isNotBlank() }?.let { Reply(output, nowMillis(), turn) }
    }

    @Synchronized fun forgetReferences(userId: String, conversationId: String, expectedTurn: Long) {
        if (owner == userId && conversation == conversationId && turn == expectedTurn) {
            context = null
            reply = null
        }
    }

    /** Resposta externa já entregue: apenas repetição literal, sem inferir ou executar seu conteúdo. */
    @Synchronized fun rememberExternalReply(text: String, userId: String, conversationId: String, expectedTurn: Long) {
        if (owner != userId || conversation != conversationId || turn != expectedTurn ||
            userId.isBlank() || userId == "anonymous") return
        context = null
        reply = text.takeIf { it.isNotBlank() }?.let {
            Reply(NluOutput("resposta_externa_entregue", it), nowMillis(), turn)
        }
    }

    @Synchronized internal fun recentTopic(userId: String, conversationId: String): HealthQuery? {
        bind(userId, conversationId)
        expire()
        return context?.query ?: when (reply?.output?.intent) {
            "explicacao_variacao_pressao" -> HealthQuery(HealthMetric.PRESSURE)
            "explicacao_variacao_glicemia" -> HealthQuery(HealthMetric.GLUCOSE)
            else -> null
        }
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
    @Synchronized fun remember(query: HealthQuery, userId: String, conversationId: String, expectedTurn: Long? = null) {
        if (userId.isBlank() || userId == "anonymous") return
        // Uma resposta antiga não pode recolocar o contexto de uma conta/conversa encerrada.
        if (owner != userId || conversation != conversationId || (expectedTurn != null && expectedTurn != turn)) return
        context = Context(query, nowMillis(), turn)
    }

    @Synchronized fun clear() {
        owner = null
        conversation = null
        turn = 0L
        context = null
        reply = null
    }

    private fun bind(userId: String, conversationId: String) {
        if (owner != userId || conversation != conversationId) {
            owner = userId
            conversation = conversationId
            turn = 0L
            context = null
            reply = null
        }
    }

    private fun expire() {
        fun expired(rememberedAt: Long, rememberedTurn: Long): Boolean {
            val age = nowMillis() - rememberedAt
            return age < 0 || age >= 5 * 60_000L || turn - rememberedTurn > 2
        }
        if (context?.let { expired(it.rememberedAt, it.turn) } == true) context = null
        if (reply?.let { expired(it.rememberedAt, it.turn) } == true) reply = null
    }
}
