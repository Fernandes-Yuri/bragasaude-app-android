package br.com.bragasaude.ai

/** Normaliza apenas introduções conversacionais, preservando valores e nomes. */
internal object BragaDialogueRequest {
    private val acknowledgement = Regex("""^(?:(?:entendi|compreendi|entendido|ok|legal|certo|beleza|ta bom|(?:muito )?obrigad[oa]|valeu)[\s,!.;]+)+(?:(?:mas|mais)\s+|so que\s+)?""")
    private val introduction = Regex("""^(?:quero saber|gostaria de saber)\s+""")

    fun main(text: String): String = BragaRoutingPolicy.normalize(text)
        .trimEnd('.', '!', '?', ' ').replace(acknowledgement, "")
        .replace(introduction, "")
}

