package br.com.bragasaude.ai

/** Escopo do produto aplicado antes de consultas, ações e fallback remoto. */
internal object BragaProductScope {
    const val RETIRED = "ajuda_recurso_descontinuado"
    const val DOSES = "ajuda_conferir_doses"
    const val GUIDANCE = "O Braga Saúde foca no cuidado diário: medicamentos, sinais vitais, hidratação, organização local de exames e família. Não oferecemos planos alimentares, contagem de calorias, cardápios, compras ou atendimento via WhatsApp. Para suporte, abra um ticket no app ou escreva para contato@bragasaude.online. Posso ajudar com seus sinais vitais ou remédios?"
    const val PROMPT = "O escopo ativo inclui sinais vitais, medicamentos, hidratação, organização local de exames, família e cuidadores. Planos alimentares, dietas, calorias, cardápios, compras e atendimento via WhatsApp foram descontinuados. Não ofereça esses recursos nem links para eles. Suporte: tickets no app ou contato@bragasaude.online. Confirmações de medicamentos exigem revisão no painel de doses por horário; nunca crie medicamentos a partir de relatos de consumo."
    private val retired = Regex("""\b(dietas?|calorias?|cardapios?|whats\s*app|zap|zapzap|planos? alimentares?|plano alimentar|planos? (?:de )?alimentacao|receitas? (?:da|de) (?:minha )?lista|lista de compras|despensa)\b""")
    private val foodAction = Regex("""\b(registr\w*|anot\w*|adicion\w*|mont\w*|abr\w*)\b.{0,60}\b(refeic\w*|aliment\w*|receitas? culinarias|compras)\b""")
    private val taken = Regex("""\b(tomei|tomamos)\b.{0,35}\b(remedios?|medicamentos?|comprimidos?|medicacao)\b""")

    fun answer(text: String): NluOutput? {
        val input = BragaRoutingPolicy.normalize(text)
        if (retired.containsMatchIn(input) || foodAction.containsMatchIn(input))
            return NluOutput(RETIRED, GUIDANCE)
        if (!text.contains('?') && !Regex("""\b(nao|nunca|jamais|sera|acho|talvez|ontem)\b""").containsMatchIn(input) && taken.containsMatchIn(input)) {
            val period = Regex("""\b(manha|tarde|noite)\b""").find(input)?.value
            val label = when (period) { "manha" -> "da manhã"; "tarde" -> "da tarde"; "noite" -> "da noite"; else -> "do horário informado" }
            return NluOutput(DOSES, "Confira as pendências $label no painel Medicamentos por horário em Lembretes. Selecione apenas as doses que tomou e confirme na tela. Nenhuma dose foi marcada por esta conversa.")
        }
        return null
    }
}
