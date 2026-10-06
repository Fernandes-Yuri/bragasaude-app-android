package br.com.bragasaude.ai

import br.com.bragasaude.domain.VoiceHealthIntent
import br.com.bragasaude.domain.VoiceHealthParser

/** Decisão compartilhada pelos canais antes de preparar rascunhos ou cards. Não autoriza persistência. */
internal object BragaActionGate {
    private val question = Regex("""\b(como|qual|quais|quanto|quanta|quantos|quantas|onde|quando|por que|porque|o que|posso|devo)\b""")
    private val negation = Regex("""\b(nao|nunca|jamais)\b""")

    fun canParse(text: String, output: NluOutput): Boolean {
        if (output.isBloqueioSeguranca || output.isEmergencia || output.delegarParaNuvem ||
            BragaHealthMemory.supports(output.intent) || output.intent.startsWith("ajuda_") ||
            (output.intent.startsWith("entrada_") && output.intent != "entrada_sem_clareza") ||
            output.intent == "orientacao_cadastro_medicamento" || output.intent == "sintoma_contextual") return false
        val normalized = BragaRoutingPolicy.normalize(text).trimEnd('.', '!', '?', ' ')
        // Respostas curtas pertencem à conversa de hidratação pendente; não contêm um novo registro.
        if (normalized in setOf("nao", "nao sei", "nao lembro", "nao quero", "nao quero mais")) return true
        return !text.contains('?') && !question.containsMatchIn(normalized) && !negation.containsMatchIn(normalized)
    }

    /** Preserva as consultas legadas do parser, sem liberar sua saída de registro para perguntas. */
    fun readOnlyQuery(text: String, output: NluOutput, parser: VoiceHealthParser,
                      role: String? = null, caregiverMode: String? = null): VoiceHealthIntent.QueryPatientStatus? {
        if (output.intent !in setOf("entrada_sem_clareza", "duvida_valor_pressao",
                "duvida_valor_glicemia", "duvida_hidratacao_agua") ||
            negation.containsMatchIn(BragaRoutingPolicy.normalize(text))) return null
        return parser.parse(text, role, caregiverMode) as? VoiceHealthIntent.QueryPatientStatus
    }
}
