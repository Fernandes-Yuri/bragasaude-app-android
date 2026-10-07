package br.com.bragasaude.ai

/** Executado somente depois de esgotar respostas, consultas e ações locais. */
internal object BragaCloudFallback {
    private val unresolved = setOf("entrada_sem_clareza", "entrada_pedido_nao_resolvido",
        "entrada_explicacao_sem_referencia")
    fun afterLocal(text: String, output: NluOutput, handledLocally: Boolean = false): NluOutput {
        if (handledLocally || text.isBlank() || output.route != BragaRoute.CLARIFICATION ||
            output.intent !in unresolved) return output
        return output.copy(intent = "fallback_nlu_nao_resolvido", delegarParaNuvem = true,
            fallbackFromIntent = output.intent)
    }
}
