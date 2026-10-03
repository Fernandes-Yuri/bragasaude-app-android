package br.com.bragasaude.data.local.slm

import br.com.bragasaude.domain.VoiceHealthIntent
import br.com.bragasaude.domain.VoiceHealthParser
import javax.inject.Inject
import javax.inject.Singleton

enum class BragaIntent { REGISTRO_AGUA, REGISTRO_PRESSAO, REGISTRO_GLICOSE, REGISTRO_MEDICAMENTO, CONSULTA_HISTORICO, EMERGENCIA, CONVERSA_LIVRE }
enum class BragaMetric { AGUA, PRESSAO, GLICOSE, MEDICAMENTO }
data class BragaRequest(
    val type: BragaIntent,
    val draft: VoiceHealthIntent? = null,
    val metric: BragaMetric? = null,
    val dayOffset: Long? = null,
    val medicationQuery: String? = null,
    val otherPerson: Boolean = false,
    val unsupportedPeriod: Boolean = false,
    val dayPart: String? = null
)

/** Classifica antes da inferência. Perguntas e negações nunca viram registros. */
@Singleton
class BragaIntentRouter @Inject constructor(private val parser: VoiceHealthParser,
    private val clinical: ClinicalTriageEngine = ClinicalTriageEngine()) {
    fun route(input: String): BragaRequest {
        val text = parser.normalize(input)
        val other = Regex("\\b(minha mae|meu pai|meu familiar|paciente)\\b").containsMatchIn(text)
        val severity = clinical.classify(input).severity
        if (severity == TriageSeverity.EMERGENCIA || severity == TriageSeverity.URGENCIA)
            return BragaRequest(BragaIntent.EMERGENCIA, otherPerson = other)
        val medicationQuestion = Regex("\\btomei\\b").containsMatchIn(text) &&
            !Regex("\\b(agua|copo|ml|litro|cafe|leite|suco|cha|sol)\\b").containsMatchIn(text) &&
            (input.trim().endsWith("?") || text.startsWith("eu ja") || text.startsWith("sera"))
        val metric = when {
            medicationQuestion || Regex("\\b(?:remedio|medicamento)\\b").containsMatchIn(text) && Regex("\\b(?:qual|quais|historico|ja tomei)\\b").containsMatchIn(text) -> BragaMetric.MEDICAMENTO
            Regex("\\b(pressao)\\b").containsMatchIn(text) -> BragaMetric.PRESSAO
            Regex("\\b(glicose|glicemia|acucar no sangue)\\b").containsMatchIn(text) -> BragaMetric.GLICOSE
            Regex("\\b(agua|hidratacao)\\b").containsMatchIn(text) -> BragaMetric.AGUA
            else -> null
        }
        val question = Regex("\\b(quanto|quanta|como esta|como ficou|qual|historico|ultima|ultimo|mostre|consulte)\\b").containsMatchIn(text)
        val personalHistory = Regex("\\b(minha|meu|bebi|tomei|hoje|ontem|anteontem|ultima|ultimo|historico|mae|pai|paciente)\\b").containsMatchIn(text)
        val educational = Regex("\\b(normal|ideal|significa|devo|posso|por que|recomendad|o que e)\\b").containsMatchIn(text)
        if (metric != null && (question || medicationQuestion) && personalHistory && !educational) return BragaRequest(BragaIntent.CONSULTA_HISTORICO,
            metric = metric, medicationQuery = if (metric == BragaMetric.MEDICAMENTO) medicationName(text) else null, dayOffset = when { "anteontem" in text -> 2; "ontem" in text -> 1; "hoje" in text -> 0; else -> null }, otherPerson = other,
            dayPart = when { "manha" in text -> "MANHA"; "tarde" in text -> "TARDE"; "noite" in text -> "NOITE"; else -> null },
            unsupportedPeriod = Regex("\\b(semana|mes|ano|segunda|terca|quarta|quinta|sexta|sabado|domingo|dia [0-9]+)\\b").containsMatchIn(text))
        // Não transforma relato negado, futuro, hipótese ou pergunta de orientação em adesão.
        if (Regex("\\b(nao|nunca|vou|preciso|devo|posso|se eu|amanha)\\b").containsMatchIn(text) || input.trim().endsWith("?"))
            return BragaRequest(BragaIntent.CONVERSA_LIVRE)
        if (Regex("\\b(tomei|ja tomei)\\b").containsMatchIn(text) &&
            !Regex("\\b(agua|copo|ml|litro|cafe|leite|suco|cha|sol)\\b").containsMatchIn(text)) {
            val query = medicationName(text)
            return BragaRequest(BragaIntent.REGISTRO_MEDICAMENTO, medicationQuery = query, otherPerson = other)
        }
        val parsed = parser.parse(input)
        val type = when {
            metric == BragaMetric.PRESSAO && parsed is VoiceHealthIntent.BloodPressure -> BragaIntent.REGISTRO_PRESSAO
            metric == BragaMetric.GLICOSE && parsed is VoiceHealthIntent.Glucose -> BragaIntent.REGISTRO_GLICOSE
            metric == BragaMetric.AGUA && Regex("\\b(bebi|tomei|bebido|registre|anote)\\b").containsMatchIn(text) && parsed is VoiceHealthIntent.Hydration -> BragaIntent.REGISTRO_AGUA
            else -> BragaIntent.CONVERSA_LIVRE
        }
        return BragaRequest(type, draft = parsed.takeUnless { type == BragaIntent.CONVERSA_LIVRE }, otherPerson = other)
    }
    private fun medicationName(text: String): String = text.substringAfter("tomei", "").trim()
        .replace(Regex("^(?:o |a |meu |minha |um |uma )+"), "")
        .replace(Regex("\\b(remedio|medicamento|comprimido|agora|hoje|ja)\\b"), "")
        .trim()

}
