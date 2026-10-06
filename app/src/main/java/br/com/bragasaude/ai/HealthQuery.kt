package br.com.bragasaude.ai

import java.text.Normalizer
import java.time.Instant
import java.time.ZoneId

enum class HealthMetric { PRESSURE, GLUCOSE, WATER, HEART_RATE, OXYGEN, WEIGHT, MEDICATION_STOCK, MEDICATION_DOSES }
enum class HealthPeriod { TODAY, YESTERDAY, LAST_7_DAYS, LAST_30_DAYS, ALL }
enum class HealthOperation { LAST, SUMMARY, AVERAGE, EXTREMES, COMPARE_YESTERDAY, AGE }

/** Valores vêm sempre do Room; este objeto guarda somente a intenção da consulta. */
data class HealthQuery(
    val metric: HealthMetric,
    val period: HealthPeriod = HealthPeriod.ALL,
    val operation: HealthOperation = HealthOperation.LAST,
    val glucoseType: String? = null,
    val medicationName: String? = null
) {
    val intent: String get() = when (metric) {
        HealthMetric.PRESSURE -> BragaHealthMemory.PRESSURE
        HealthMetric.GLUCOSE -> BragaHealthMemory.GLUCOSE
        HealthMetric.WATER -> BragaHealthMemory.WATER
        HealthMetric.HEART_RATE -> BragaHealthMemory.HEART_RATE
        HealthMetric.OXYGEN -> BragaHealthMemory.OXYGEN
        HealthMetric.WEIGHT -> BragaHealthMemory.WEIGHT
        HealthMetric.MEDICATION_STOCK -> BragaHealthMemory.MEDICATION_STOCK
        HealthMetric.MEDICATION_DOSES -> BragaHealthMemory.MEDICATION_DOSES
    }

    companion object {
        fun forIntent(intent: String): HealthQuery = when (intent) {
            BragaHealthMemory.PRESSURE -> HealthQuery(HealthMetric.PRESSURE, operation = HealthOperation.SUMMARY)
            BragaHealthMemory.GLUCOSE -> HealthQuery(HealthMetric.GLUCOSE, operation = HealthOperation.SUMMARY)
            BragaHealthMemory.WATER -> HealthQuery(HealthMetric.WATER, HealthPeriod.TODAY, HealthOperation.SUMMARY)
            BragaHealthMemory.HEART_RATE -> HealthQuery(HealthMetric.HEART_RATE)
            BragaHealthMemory.OXYGEN -> HealthQuery(HealthMetric.OXYGEN)
            BragaHealthMemory.WEIGHT -> HealthQuery(HealthMetric.WEIGHT)
            BragaHealthMemory.MEDICATION_STOCK -> HealthQuery(HealthMetric.MEDICATION_STOCK)
            BragaHealthMemory.MEDICATION_DOSES -> HealthQuery(HealthMetric.MEDICATION_DOSES, HealthPeriod.TODAY)
            else -> throw IllegalArgumentException("Intenção sem consulta de saúde local")
        }
    }
}

/** Início inclusivo, fim exclusivo. O teto em agora impede ler registros futuros. */
data class HealthQueryInterval(val startInclusiveMillis: Long, val endExclusiveMillis: Long) {
    companion object {
        fun forPeriod(period: HealthPeriod, now: Instant, zone: ZoneId): HealthQueryInterval {
            val today = now.atZone(zone).toLocalDate()
            val nowExclusive = now.toEpochMilli() + 1
            val start = when (period) {
                HealthPeriod.TODAY -> today
                HealthPeriod.YESTERDAY -> today.minusDays(1)
                HealthPeriod.LAST_7_DAYS -> today.minusDays(6)
                HealthPeriod.LAST_30_DAYS -> today.minusDays(29)
                HealthPeriod.ALL -> null
            }?.atStartOfDay(zone)?.toInstant()?.toEpochMilli() ?: Long.MIN_VALUE
            val end = if (period == HealthPeriod.YESTERDAY)
                today.atStartOfDay(zone).toInstant().toEpochMilli() else nowExclusive
            return HealthQueryInterval(start, end)
        }
    }
}

/** Vocabulário de recuperação pessoal, separado de explicações clínicas e ações. */
object HealthQueryResolver {
    private fun normalized(text: String) = Normalizer.normalize(BragaLanguageRecovery.recognize(text), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "").replace(Regex("[^a-z0-9\\s]"), " ")
        .replace(Regex("\\s+"), " ").trim()

    private val otherPerson = Regex("\\b(?:(?:minha|meu|sua|seu) (?:mae|pai|avo|irma|irmao|filh[ao]|marido|esposa|paciente|vizinh[ao])|dele|dela|familiar|paciente)\\b")
    private val explanation = Regex("\\b(?:porque|por que|afeta|influencia|causa|causar|significa|interpretar|interpretacao|relacao|risco|tratamento|tratar|remedio|medicamento|diagnostico|normal|perigoso|preocupar|diferen[cç]a|ideal|recomendad[ao]|baixar|subir|aumentar|diminuir|controlar|melhorar)\\b")
    private val registration = Regex("\\b(?:registre|registrar|anote|anotar|salve|salvar|adicione|adicionar|cadastre|cadastrar)\\b")
    private val request = Regex("\\b(?:quanto|quanta|quantos|qual|como|mostre|mostrar|ver|veja|consulte|consultar|consulta|historico|media|resumo|ultimo|ultima)\\b")
    private val own = Regex("\\b(?:minha|minhas|meu|meus|bebi|bebo|tomei|ingeri|registrei|anotei)\\b")
    private fun metrics(text: String) = buildList {
        if (Regex("\\b(?:pressao|press[aã]o arterial)\\b").containsMatchIn(text)) add(HealthMetric.PRESSURE)
        if (Regex("\\b(?:glicemia|glicose|acucar no sangue)\\b").containsMatchIn(text)) add(HealthMetric.GLUCOSE)
        if (Regex("\\b(?:agua|hidratacao)\\b").containsMatchIn(text)) add(HealthMetric.WATER)
        if (Regex("""\b(batimentos?|frequencia cardiaca|pulso)\b""").containsMatchIn(text)) add(HealthMetric.HEART_RATE)
        if (Regex("""\b(saturacao|spo2|oxigenio)\b""").containsMatchIn(text)) add(HealthMetric.OXYGEN)
        if (Regex("""\bpeso\b""").containsMatchIn(text)) add(HealthMetric.WEIGHT)
    }
    private fun excluded(text: String) = otherPerson.containsMatchIn(text) ||
        explanation.containsMatchIn(text) || registration.containsMatchIn(text)

    /** Período solicitado que não cabe no contrato atual nunca vira ALL ou outro intervalo. */
    fun hasUnsupportedPeriod(text: String): Boolean {
        val input = normalized(text)
        val queryRequest = request.containsMatchIn(input) || input.startsWith("e ")
        val personalMetric = own.containsMatchIn(input) && metrics(input).isNotEmpty()
        if (excluded(input) || (!queryRequest && !personalMetric)) return false
        if (metrics(input).isEmpty() && operation(input) == null && !input.startsWith("e ")) return false
        if (Regex("""\b(entre|desde)\b|\b(ontem e hoje|hoje e ontem|semana retrasada|mes retrasado|semana anterior|mes anterior|ano anterior|de manha|pela manha|a tarde|a noite)\b""").containsMatchIn(input)) return true
        if (Regex("""\b(anteontem|antes de ontem|amanha|semana passada|mes passado|ano passado|semana que vem|mes que vem|janeiro|fevereiro|marco|abril|maio|junho|julho|agosto|setembro|outubro|novembro|dezembro|segunda(?: feira)?|terca(?: feira)?|quarta(?: feira)?|quinta(?: feira)?|sexta(?: feira)?|sabado|domingo)\b""").containsMatchIn(input)) return true
        if (Regex("""\b(?:dia\s+\d{1,2}|\d{1,2}\s+\d{1,2}\s+\d{4}|(?:em|de)\s+\d{4}\b(?!\s*(?:ml|mililitros?|mg|litros?))|(?:as|pelas)\s+\d{1,2}|ha\s+\d+\s+dias)\b""").containsMatchIn(input)) return true
        if (Regex("""\b(?:19|20)\d{2}\b(?!\s*(?:ml|mililitros?|mg|litros?))""").containsMatchIn(input)) return true
        // Datas numéricas são verificadas antes de perder a pontuação na normalização.
        if (queryRequest && Regex("""\b\d{1,2}[/.-]\d{1,2}(?:[/.-]\d{2,4})?\b""").containsMatchIn(text)) return true
        return Regex("""\b(?:ultimos?\s+)?(\d+|um|dois|tres|quatro|cinco|seis|sete|oito|nove|dez|quatorze|catorze|quinze|vinte|trinta)\s+dias\b""")
            .findAll(input).any { it.groupValues[1] !in setOf("7", "sete", "30", "trinta") }
    }

    fun isAmbiguous(text: String): Boolean {
        val input = normalized(text)
        return !excluded(input) && metrics(input).size > 1 && request.containsMatchIn(input)
    }

    fun explicit(text: String): HealthQuery? {
        val input = normalized(text)
        if (otherPerson.containsMatchIn(input) || BragaLanguageRecovery.clarification(text) != null) return null
        // Medicamentos usam o nome original normalizado, sem aliases ou aproximação.
        val exact = Normalizer.normalize(text.lowercase(java.util.Locale.ROOT), Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "").replace(Regex("\\s+"), " ").trim().trimEnd('?', '.', '!')
        Regex("""^(?:quanto (?:de |do )?|quantas unidades de )(.+?) (?:eu )?ainda tenho$|^quanto tenho de (.+)$""")
            .matchEntire(exact)?.let { match ->
                val name = match.groupValues.drop(1).firstOrNull { it.isNotBlank() }
                if (name != null && name !in setOf("remedio", "medicamento", "agua"))
                    return HealthQuery(HealthMetric.MEDICATION_STOCK, medicationName = name)
            }
        Regex("""^(?:ja )?(?:registrei|anotei) (?:a |uma )?dose de (.+?) (hoje|ontem)$""").matchEntire(exact)?.let {
            return HealthQuery(HealthMetric.MEDICATION_DOSES,
                if (it.groupValues[2] == "hoje") HealthPeriod.TODAY else HealthPeriod.YESTERDAY,
                medicationName = it.groupValues[1])
        }
        Regex("""^(?:minha |a minha )(pressao|glicemia|glicose) (?:aumentou|subiu|baixou|caiu|diminuiu|mudou) (?:em relacao a|comparada com) ontem$""")
            .matchEntire(input)?.let {
                return HealthQuery(if (it.groupValues[1] == "pressao") HealthMetric.PRESSURE else HealthMetric.GLUCOSE,
                    HealthPeriod.TODAY, HealthOperation.COMPARE_YESTERDAY)
            }
        Regex("""^ha quantos dias (?:eu )?nao (?:registro|anoto) (?:minha |a )?(pressao|glicemia|glicose)$""")
            .matchEntire(input)?.let {
                return HealthQuery(if (it.groupValues[1] == "pressao") HealthMetric.PRESSURE else HealthMetric.GLUCOSE,
                    operation = HealthOperation.AGE)
            }
        if (Regex("""^(?:qual (?:e )?(?:a )?minha meta de (?:agua|hidratacao)(?: e quanto falta)?|quanto falta (?:de agua|para (?:a |minha |a minha )?meta de agua))(?: hoje)?$""").matches(input))
            return HealthQuery(HealthMetric.WATER, HealthPeriod.TODAY, HealthOperation.SUMMARY)
        if (excluded(input) || hasUnsupportedPeriod(text) || BragaLanguageRecovery.clarification(text) != null) return null
        val metric = metrics(input).singleOrNull() ?: return null
        val implicitHistory = period(input) != null && operation(input) != null &&
            Regex("""\b(?:foi|foram|registrad[oa]s?)\b""").containsMatchIn(input)
        if ((!own.containsMatchIn(input) && !implicitHistory) || (!request.containsMatchIn(input) &&
                period(input) == null && !isFollowUp(input))) return null
        val requestedPeriod = period(input) ?: when {
            metric == HealthMetric.WATER -> HealthPeriod.TODAY
            operation(input) == HealthOperation.EXTREMES -> HealthPeriod.LAST_30_DAYS
            else -> HealthPeriod.ALL
        }
        val operation = operation(input) ?: if ((requestedPeriod == HealthPeriod.ALL && metric in setOf(HealthMetric.PRESSURE, HealthMetric.GLUCOSE)) || metric == HealthMetric.WATER)
            HealthOperation.SUMMARY else HealthOperation.LAST
        return HealthQuery(metric, requestedPeriod, operation, if (metric == HealthMetric.GLUCOSE) glucoseType(input) else null)
    }

    fun isFollowUp(text: String): Boolean {
        val input = normalized(text)
        if (excluded(input) || hasUnsupportedPeriod(text) || BragaLanguageRecovery.clarification(text) != null || metrics(input).size > 1 || input.length > 100) return false
        val hasQueryPart = period(input) != null || operation(input) != null || metrics(input).isNotEmpty() || glucoseType(input) != null
        if (!hasQueryPart) return false
        val remainder = input.replace(Regex("\\b(?:e|a|o|as|os|da|do|das|dos|de|em|no|na|nos|nas|minha|meu|minhas|meus|foi|foram|quanto|quanta|qual|como|ficou|deu|ta|esta|estao|media|resumo|ultimo|ultima|registro|registros|medicao|medicoes|pressao|arterial|glicemia|glicose|acucar|sangue|agua|hidratacao|hoje|ontem|semana|mes|ultimos|ultimas|dias|7|sete|30|trinta|jejum|apos|depois|refeicao|almoco|jantar|pos|prandial|sensor|continuo|aleatoria|casual|capilar|batimento|batimentos|frequencia|cardiaca|pulso|saturacao|spo2|oxigenio|peso|maior|menor|maxima|minima|maximo|minimo)\\b"), "")
            .replace(" ", "")
        return remainder.isEmpty() && (input.startsWith("e ") || metrics(input).isEmpty() || !request.containsMatchIn(input))
    }

    fun followUp(text: String, previous: HealthQuery): HealthQuery? {
        if (!isFollowUp(text)) return null
        val input = normalized(text)
        val metric = metrics(input).singleOrNull() ?: previous.metric
        val type = if (metric != HealthMetric.GLUCOSE) null else glucoseType(input)
            ?: previous.glucoseType.takeIf { previous.metric == HealthMetric.GLUCOSE }
        return HealthQuery(metric, period(input) ?: previous.period, operation(input) ?: previous.operation, type,
            previous.medicationName.takeIf { metric == previous.metric })
    }

    private fun period(text: String): HealthPeriod? = when {
        Regex("\\bontem\\b").containsMatchIn(text) -> HealthPeriod.YESTERDAY
        Regex("\\b(?:semana|(?:7|sete) dias)\\b").containsMatchIn(text) -> HealthPeriod.LAST_7_DAYS
        Regex("\\b(?:mes|(?:30|trinta) dias)\\b").containsMatchIn(text) -> HealthPeriod.LAST_30_DAYS
        Regex("\\bhoje\\b").containsMatchIn(text) -> HealthPeriod.TODAY
        Regex("\\b(?:todo|completo) historico\\b").containsMatchIn(text) -> HealthPeriod.ALL
        else -> null
    }
    private fun operation(text: String): HealthOperation? = when {
        Regex("""\b(maior|menor|maxima|minima|maximo|minimo)\b""").containsMatchIn(text) -> HealthOperation.EXTREMES
        Regex("\\bmedia\\b").containsMatchIn(text) -> HealthOperation.AVERAGE
        Regex("\\b(?:ultimo|ultima)\\b").containsMatchIn(text) -> HealthOperation.LAST
        Regex("\\b(?:resumo|historico)\\b").containsMatchIn(text) -> HealthOperation.SUMMARY
        else -> null
    }
    private fun glucoseType(text: String): String? = when {
        Regex("\\bjejum\\b").containsMatchIn(text) -> "fasting"
        Regex("\\b(?:(?:apos|depois) (?:a |o |da |do )?(?:refeicao|almoco|jantar)|pos prandial)\\b").containsMatchIn(text) -> "post_prandial"
        Regex("\\b(?:sensor|continuo)\\b").containsMatchIn(text) -> "continuous"
        Regex("\\b(?:aleatoria|casual)\\b").containsMatchIn(text) -> "random"
        Regex("\\bcapilar\\b").containsMatchIn(text) -> "fingerstick"
        else -> null
    }
}
