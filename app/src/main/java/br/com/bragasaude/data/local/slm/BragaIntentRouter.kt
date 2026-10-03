package br.com.bragasaude.data.local.slm

import br.com.bragasaude.domain.VoiceHealthIntent
import br.com.bragasaude.domain.VoiceHealthParser
import javax.inject.Inject
import javax.inject.Singleton

enum class BragaIntent { REGISTRO_AGUA, REGISTRO_PRESSAO, REGISTRO_GLICOSE, REGISTRO_MEDICAMENTO, CONSULTA_HISTORICO, EMERGENCIA, CONVERSA_LIVRE }
enum class BragaMetric { AGUA, PRESSAO, GLICOSE }
data class BragaRequest(
    val type: BragaIntent,
    val draft: VoiceHealthIntent? = null,
    val metric: BragaMetric? = null,
    val dayOffset: Long? = null,
    val medicationQuery: String? = null,
    val otherPerson: Boolean = false
)

/** Classifica antes da inferência. Perguntas e negações nunca viram registros. */
@Singleton
class BragaIntentRouter @Inject constructor(private val parser: VoiceHealthParser) {
    fun route(input: String): BragaRequest {
        val text = parser.normalize(input)
        val other = Regex("\\b(minha mae|meu pai|meu familiar|paciente)\\b").containsMatchIn(text)
        val danger = Regex("\\b(dor (?:forte )?no peito|aperto no peito|falta de ar|nao consigo respirar|desmaiei|desmaio|vomitando sangue|socorro|infarto|avc|derrame|caiu.{0,30}bateu (?:a )?cabeca)\\b")
        val active = text.split(Regex("[;]|\\bmas\\b")).any { clause ->
            danger.findAll(clause).any { m ->
                !Regex("\\b(nao (?:tenho|sinto|estou com)|sem|nunca tive)\\s*$").containsMatchIn(clause.take(m.range.first)) &&
                    !Regex("\\b(o que e|o que significa|li sobre|me explique)\\b").containsMatchIn(clause)
            }
        }
        if (active) return BragaRequest(BragaIntent.EMERGENCIA, otherPerson = other)
        val metric = when {
            Regex("\\b(pressao)\\b").containsMatchIn(text) -> BragaMetric.PRESSAO
            Regex("\\b(glicose|glicemia|acucar no sangue)\\b").containsMatchIn(text) -> BragaMetric.GLICOSE
            Regex("\\b(agua|hidratacao)\\b").containsMatchIn(text) -> BragaMetric.AGUA
            else -> null
        }
        val question = Regex("\\b(quanto|quanta|como esta|como ficou|qual|historico|ultima|ultimo|mostre|consulte)\\b").containsMatchIn(text)
        if (metric != null && question) return BragaRequest(BragaIntent.CONSULTA_HISTORICO,
            metric = metric, dayOffset = when { "anteontem" in text -> 2; "ontem" in text -> 1; "hoje" in text -> 0; else -> null }, otherPerson = other)
        // Não transforma relato negado, futuro, hipótese ou pergunta de orientação em adesão.
        if (Regex("\\b(nao|nunca|vou|preciso|devo|posso|se eu|amanha)\\b").containsMatchIn(text) || input.trim().endsWith("?"))
            return BragaRequest(BragaIntent.CONVERSA_LIVRE)
        if (Regex("\\b(tomei|tomo|ja tomei)\\b").containsMatchIn(text) &&
            !Regex("\\b(agua|copo|ml|litro)\\b").containsMatchIn(text)) {
            val query = text.substringAfter("tomei", "").replace(Regex("^(?:o |a |meu |minha |um |uma )+"), "")
                .replace(Regex("\\b(remedio|medicamento|comprimido|agora|hoje|ja)\\b"), "").trim()
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
}
