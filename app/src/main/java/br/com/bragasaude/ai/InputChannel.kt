package br.com.bragasaude.ai

enum class InputChannel { VOICE, TEXT }

/** Vocabulário de reparação separado por modalidade, inclusive para respostas externas. */
object BragaInputLanguage {
    private val last = mutableMapOf<InputChannel, String>()
    private val auditoryRepair = Regex(
        """nao (consegui|consigo|deu para) (te |lhe )?(ouvir|escutar)|nao (te |lhe )?ouvi|fal[ea] (um pouco )?mais alto|repita.{0,20}(alto|volume)|audio.{0,20}(baixo|inaudivel)|nao entendi.{0,20}(fala|audio)"""
    )

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
        if (channel == InputChannel.VOICE) return br.com.bragasaude.util.PortuguesePhoneticHelper.cleanTextForTts(text)
        val normalized = java.text.Normalizer.normalize(text.lowercase(java.util.Locale.ROOT),
            java.text.Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
        return if (auditoryRepair.containsMatchIn(normalized)) clarification(channel) else text
    }
}
