package br.com.bragasaude.ai

enum class InputChannel { VOICE, TEXT }

/** Vocabulário de reparação separado por modalidade, inclusive para respostas externas. */
object BragaInputLanguage {
    private val last = mutableMapOf<InputChannel, String>()
    private val auditoryRepair = Regex(
        """nao (consegui|consigo|deu para) (te |lhe )?(ouvir|escutar)|nao (te |lhe )?ouvi|fal[ea] (um pouco )?mais alto|repita.{0,20}(alto|volume)|audio.{0,20}(baixo|inaudivel)|nao entendi.{0,20}(fala|audio)"""
    )
    // A nuvem pode devolver o endereço cru de discagem no texto. O chat sempre
    // mostra o card nativo com os botões; o texto deve trazer apenas o número,
    // que o balão também transforma em discagem com um toque (ACTION_DIAL).
    private val rawDialUri = Regex("""\btel:\s*(\d{2,6})\b""", RegexOption.IGNORE_CASE)

    fun withoutRawDialUris(text: String): String = rawDialUri.replace(text, "$1")

    @Synchronized
    fun clarification(channel: InputChannel): String {
        val options = when (channel) {
            InputChannel.TEXT -> listOf(
                "Não compreendi bem o que você digitou. Poderia reformular ou detalhar um pouco mais?",
                "Fiquei em dúvida sobre sua mensagem. Você pode explicar com mais detalhes?",
                "Pode escrever de outra forma o que você precisa? Quero entender melhor sua dúvida.",
                "Preciso de um pouco mais de contexto sobre o texto. Você pode detalhar sua pergunta?"
            )
            InputChannel.VOICE -> listOf(
                "Não compreendi bem sua fala. Você pode repetir com um pouco mais de detalhes?",
                "Fiquei em dúvida sobre o que você disse. Pode explicar um pouco mais?",
                "Pode repetir sua pergunta com calma? Quero entender melhor o que você precisa.",
                "Preciso de um pouco mais de contexto. Você pode me contar sua dúvida de outra forma?"
            )
        }
        return options.filter { it != last[channel] }.random().also { last[channel] = it }
    }

    fun forChannel(text: String, channel: InputChannel): String {
        val sanitized = withoutRawDialUris(text)
        if (channel == InputChannel.VOICE) return br.com.bragasaude.util.PortuguesePhoneticHelper.cleanTextForTts(sanitized)
        val normalized = java.text.Normalizer.normalize(sanitized.lowercase(java.util.Locale.ROOT),
            java.text.Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
        return if (auditoryRepair.containsMatchIn(normalized)) clarification(channel) else sanitized
    }
}
