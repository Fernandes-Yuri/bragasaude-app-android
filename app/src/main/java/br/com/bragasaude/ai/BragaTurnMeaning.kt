package br.com.bragasaude.ai

/** Entidades e modalidade da entrada; não autoriza persistência. */
internal object BragaTurnMeaning {
    data class Beverage(val entity: String?, val quantityMl: Int?, val negated: Boolean, val modality: String)
    private val clauses = Regex("""\b(mas|porem|contudo)\b|(?<!que )\be\b|[;!?]|\.(?!\d)|,\s*(?=(?:eu|ele|ela|meu|minha|nao|estou|tenho|sinto|bebi|tomei|agora)\b)""")
    private val verb = Regex("""\b(bebi|bebeu|tomei|tomou|bebendo|tomando|anot\w*|registr\w*|adicion\w*)\b""")
    private val drink = Regex("""\b(agua|h2o|suco|cha|cafe|leite|refrigerante|cerveja|vinho|bebida)\b""")
    private val quantity = Regex("""(\d+(?:[.,]\d+)?)\s*(mililitros?|ml|litros?|l|copos?|garrafinhas?|garrafas?|xicaras?|canecas?)\b""")
    private val denial = Regex("""\b(nao|sem|nego|negou|nunca|jamais)\b(?:\W+\w+){0,6}\W*$""")
    private val question = Regex("""\b(o que|por que|porque|quanto|quantos|posso|devo|como|explique|explica)\b""")

    fun beverages(text: String): List<Beverage> = clauses.split(BragaRoutingPolicy.normalize(text)).mapNotNull { clause ->
        val action = verb.find(clause) ?: return@mapNotNull null
        val amount = quantity.find(clause)
        val entities = drink.findAll(clause).toList()
        if (amount == null && entities.isEmpty()) return@mapNotNull null
        val denied = denial.containsMatchIn(clause.take(action.range.first))
        val request = Regex("anot|registr|adicion").containsMatchIn(action.value)
        val mode = when {
            question.containsMatchIn(clause) -> "educational"
            denied && request -> "cancellation"
            denied -> "denial"
            request -> "request"
            else -> "report"
        }
        val ml = amount?.let {
            val per = when (it.groupValues[2].removeSuffix("s")) {
                "ml", "mililitro" -> 1
                "litro", "l", "garrafa" -> 1000
                "copo" -> 250
                "garrafinha" -> 500
                "xicara" -> 200
                "caneca" -> 350
                else -> 0
            }
            (it.groupValues[1].replace(',', '.').toDoubleOrNull()?.times(per))
                ?.takeIf { value -> value in 50.0..8000.0 }?.toInt()
        }
        Beverage(entities.singleOrNull()?.value?.let { if (it == "h2o") "agua" else it }, ml, denied, mode)
    }

    fun localReply(text: String): String? {
        if (BragaRoutingPolicy.complexHealthQuestion(BragaRoutingPolicy.normalize(text))) return null
        val mentions = beverages(text)
        if (mentions.isEmpty()) return null
        if (mentions.size > 1) return "Você mencionou mais de uma informação sobre bebidas. Qual delas deseja tratar primeiro?"
        val mention = mentions.single()
        if (mention.modality == "educational") return null
        if (mention.modality == "cancellation") return "Entendi o pedido de não registrar essa bebida. Não vou propor um registro de consumo."
        if (mention.negated) return "Entendi: você negou esse consumo. Não vou considerar essa quantidade como bebida consumida."
        if (mention.entity == null) return "Qual bebida você está mencionando? A quantidade sozinha não identifica água."
        if (mention.entity == "agua") return null
        val shown = when (mention.entity) { "cha" -> "chá"; "cafe" -> "café"; else -> mention.entity }
        val amount = mention.quantityMl?.let { "$it ml de " }.orEmpty()
        return "Você mencionou $amount$shown. Não vou converter essa bebida em um registro de água."
    }
}
