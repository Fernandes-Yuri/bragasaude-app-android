package br.com.bragasaude.ai

/**
 * Escopo do produto aplicado antes de consultas, ações e fallback remoto.
 *
 * A voz do Braga é a de um cuidador geriátrico maduro, acolhedor e seguro para
 * o público 40+. Dúvidas sobre alimentação, sono, hidratação e hábitos são
 * bem-vindas e respondidas com carinho; o limite é técnico (não há registro de
 * refeições nem plano alimentar individual no aplicativo), nunca uma recusa
 * burocrática de software.
 */
internal object BragaProductScope {
    const val APP_LIMITS = "ajuda_limites_do_app"
    const val DOSES = "ajuda_conferir_doses"

    const val GUIDANCE = "Que bom que você está cuidando de você. Eu gosto de conversar sobre alimentação, sono, hidratação e hábitos do dia a dia, sempre com ideias simples e carinhosas. O que não consigo fazer por aqui é montar planos alimentares, contar calorias ou registrar refeições e listas de compras; para algo personalizado, vale conversar com nutricionista ou médico. Se precisar de suporte do app, abra um ticket ou escreva para contato@bragasaude.online. Quer conversar sobre uma refeição ou sobre outro cuidado?"

    const val PROMPT = "O Braga Saúde acompanha sinais vitais, medicamentos, hidratação, exames locais, família e cuidadores. Você é um cuidador experiente e acolhedor: converse sobre alimentação cotidiana, sono, hidratação, hábitos e bem-estar das pessoas de 40+, com orientações gerais e afetuosas. Para plano alimentar individual, calorias ou dieta, oriente com carinho nutricionista ou médico, sem recusas secas nem jargão de sistema. Não prometa registrar refeições, listas de compras, despensa ou receitas: esses registros não existem no app. Suporte: tickets no app ou contato@bragasaude.online. Confirmações de medicamentos exigem revisão no painel de doses por horário; nunca crie medicamentos a partir de relatos de consumo."

    private val limits = Regex("""\b(dietas?|calorias?|cardapios?|whats\s*app|zap|zapzap|planos? alimentares?|plano alimentar|planos? (?:de )?alimentacao|receitas? (?:da|de) (?:minha )?lista|lista de compras|despensa)\b""")
    private val foodAction = Regex("""\b(registr\w*|anot\w*|adicion\w*|mont\w*|abr\w*)\b.{0,60}\b(refeic\w*|aliment\w*|receitas? culinarias|compras)\b""")
    private val taken = Regex("""\b(tomei|tomamos)\b.{0,35}\b(remedios?|medicamentos?|comprimidos?|medicacao)\b""")

    fun answer(text: String): NluOutput? {
        val input = BragaRoutingPolicy.normalize(text)
        if (limits.containsMatchIn(input) || foodAction.containsMatchIn(input))
            return NluOutput(APP_LIMITS, GUIDANCE)
        if (!text.contains('?') && !Regex("""\b(nao|nunca|jamais|sera|acho|talvez|ontem)\b""").containsMatchIn(input) && taken.containsMatchIn(input)) {
            val period = Regex("""\b(manha|tarde|noite)\b""").find(input)?.value
            val label = when (period) { "manha" -> "da manhã"; "tarde" -> "da tarde"; "noite" -> "da noite"; else -> "do horário informado" }
            return NluOutput(DOSES, "Confira as pendências $label no painel Medicamentos por horário em Lembretes. Selecione apenas as doses que tomou e confirme na tela. Nenhuma dose foi marcada por esta conversa.")
        }
        return null
    }
}
