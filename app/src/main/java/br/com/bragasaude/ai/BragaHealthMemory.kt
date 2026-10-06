package br.com.bragasaude.ai

import br.com.bragasaude.data.local.ProfileDao
import br.com.bragasaude.data.local.VitalSignDao
import br.com.bragasaude.data.local.VitalSignEntity
import br.com.bragasaude.domain.util.BloodPressureParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import kotlin.math.roundToInt

data class HealthMemoryResult(val text: String, val hasData: Boolean,
    val referenceMeasuredAtMillis: Long? = null, val referenceZoneId: String? = null)

/** Consultas somente no Room. Ausência ou falha local nunca vira fallback para a nuvem. */
class BragaHealthMemory internal constructor(
    private val vitals: VitalSignDao,
    private val profiles: ProfileDao,
    private val now: () -> Instant,
    private val zone: ZoneId
) {
    @Inject constructor(vitals: VitalSignDao, profiles: ProfileDao) : this(
        vitals, profiles, { Instant.now() }, ZoneId.systemDefault()
    )

    companion object {
        const val PRESSURE = "consulta_historico_pressao"
        const val GLUCOSE = "consulta_historico_glicemia"
        const val WATER = "consulta_historico_hidratacao"
        fun supports(intent: String) = intent in setOf(PRESSURE, GLUCOSE, WATER)
    }

    private var owner: String? = null
    private val previous = mutableMapOf<String, String>()

    @Synchronized private fun variant(userId: String, key: String, choices: List<String>): String {
        if (owner != userId) { previous.clear(); owner = userId }
        return choices.filter { it != previous[key] }.random().also { previous[key] = it }
    }

    @Synchronized fun clearSession() { previous.clear(); owner = null }

    private fun introduction(userId: String, query: HealthQuery) = variant(userId, query.intent,
        listOf("Nos seus registros,", "Pelas suas anotações,", "No seu histórico,", "Ao consultar seus registros,"))

    suspend fun answer(intent: String, userId: String): String = answer(HealthQuery.forIntent(intent), userId)
    suspend fun answer(query: HealthQuery, userId: String): String = answerResult(query, userId).text

    suspend fun answerResult(query: HealthQuery, userId: String): HealthMemoryResult = withContext(Dispatchers.IO) {
        if (userId.isBlank() || userId == "anonymous") {
            return@withContext HealthMemoryResult(variant(userId, "login", listOf(
                "Entre na sua conta para consultar seus registros de saúde.",
                "Para mostrar seu histórico de saúde, preciso que você entre na sua conta.",
                "Seus dados ficam vinculados à sua conta. Entre nela para consultar as anotações.",
                "A consulta dos seus registros fica disponível depois de entrar na sua conta."
            )), false)
        }
        val instant = now()
        try {
            when (query.metric) {
                HealthMetric.PRESSURE -> pressure(query, userId, instant)
                HealthMetric.GLUCOSE -> glucose(query, userId, instant)
                HealthMetric.WATER -> water(query, userId, instant)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            HealthMemoryResult(variant(userId, "error", listOf(
                "Não consegui consultar seus registros agora. Você pode conferir os dados na tela do aplicativo.",
                "A consulta local não ficou disponível neste momento. Tente novamente ou confira a tela do aplicativo.",
                "Houve uma dificuldade ao ler suas anotações. Você pode tentar de novo ou abrir a tela desses dados.",
                "Não foi possível abrir seu histórico agora. Seus registros também podem ser conferidos na tela do aplicativo."
            )), false)
        }
    }

    private fun valid(record: VitalSignEntity?, user: String, interval: HealthQueryInterval): Boolean =
        record != null && record.userId == user && record.measuredAt.time >= interval.startInclusiveMillis &&
            record.measuredAt.time < interval.endExclusiveMillis

    private fun interval(query: HealthQuery, instant: Instant) = HealthQueryInterval.forPeriod(query.period, instant, zone)
    private fun averageInterval(query: HealthQuery, instant: Instant) = HealthQueryInterval.forPeriod(
        if (query.period == HealthPeriod.ALL) HealthPeriod.LAST_30_DAYS else query.period, instant, zone)

    private fun periodLabel(period: HealthPeriod) = when (period) {
        HealthPeriod.TODAY -> "hoje"
        HealthPeriod.YESTERDAY -> "ontem"
        HealthPeriod.LAST_7_DAYS -> "nos últimos 7 dias, incluindo hoje"
        HealthPeriod.LAST_30_DAYS -> "nos últimos 30 dias, incluindo hoje"
        HealthPeriod.ALL -> "no histórico disponível até agora"
    }

    private fun averagePeriod(query: HealthQuery) = if (query.period == HealthPeriod.ALL)
        "nos últimos 30 dias, incluindo hoje" else periodLabel(query.period)

    private fun time(record: VitalSignEntity): String = DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm", Locale("pt", "BR"))
        .format(record.measuredAt.toInstant().atZone(zone))

    private fun elapsed(record: VitalSignEntity, instant: Instant): String {
        val duration = Duration.between(record.measuredAt.toInstant(), instant)
        val days = duration.toDays()
        val hours = duration.toHours()
        val minutes = duration.toMinutes()
        return when {
            days > 0 -> "há $days ${if (days == 1L) "dia" else "dias"}"
            hours > 0 -> "há $hours ${if (hours == 1L) "hora" else "horas"}"
            minutes > 0 -> "há $minutes ${if (minutes == 1L) "minuto" else "minutos"}"
            else -> "há menos de 1 minuto"
        }
    }

    private fun absent(query: HealthQuery, userId: String): HealthMemoryResult {
        val metric = when (query.metric) {
            HealthMetric.PRESSURE -> "pressão"
            HealthMetric.GLUCOSE -> "glicemia${query.glucoseType?.let { " (${typeLabel(it)})" } ?: ""}"
            HealthMetric.WATER -> "água"
        }
        val period = periodLabel(query.period)
        val text = variant(userId, "absent:${query.intent}", listOf(
            "Não encontrei registros de $metric $period.",
            "Ainda não há anotações de $metric $period para mostrar.",
            "Seu histórico não apresenta registros de $metric $period.",
            "Ao consultar suas anotações, não apareceram registros de $metric $period."
        ))
        return HealthMemoryResult("$text Você pode conferir ou anotar na seção correspondente do aplicativo.", false)
    }

    private suspend fun pressure(query: HealthQuery, userId: String, instant: Instant): HealthMemoryResult {
        val scope = interval(query, instant)
        val last = vitals.getLatestPressureForMemory(userId, scope.startInclusiveMillis, scope.endExclusiveMillis)
            ?.takeIf { valid(it, userId, scope) && (it.systolicPressure ?: 0) > 0 && (it.diastolicPressure ?: 0) > 0 }
        if (query.operation == HealthOperation.LAST) {
            return if (last == null) absent(query, userId) else HealthMemoryResult(
                "${introduction(userId, query)} sua última pressão ${periodLabel(query.period)} foi " +
                    "${BloodPressureParser.normalizePressure(last.systolicPressure!!)} por ${BloodPressureParser.normalizePressure(last.diastolicPressure!!)} mmHg, " +
                    "em ${time(last)}, ${elapsed(last, instant)}.", true, last.measuredAt.time, zone.id)
        }
        val recentScope = averageInterval(query, instant)
        val recent = vitals.getPressureIntervalForMemory(userId, recentScope.startInclusiveMillis, recentScope.endExclusiveMillis)
            .filter { valid(it, userId, recentScope) && (it.systolicPressure ?: 0) > 0 && (it.diastolicPressure ?: 0) > 0 }
        if (recent.isEmpty() && (last == null || query.operation == HealthOperation.AVERAGE)) return absent(
            query.copy(period = if (query.period == HealthPeriod.ALL) HealthPeriod.LAST_30_DAYS else query.period), userId)
        val details = buildList {
            if (last != null && query.operation == HealthOperation.SUMMARY) add(
                "sua última pressão foi ${BloodPressureParser.normalizePressure(last.systolicPressure!!)} por " +
                    "${BloodPressureParser.normalizePressure(last.diastolicPressure!!)} mmHg, em ${time(last)}, ${elapsed(last, instant)}.")
            if (recent.isNotEmpty()) {
                val sys = recent.map { BloodPressureParser.normalizePressure(it.systolicPressure!!).toDouble() }.average().roundToInt()
                val dia = recent.map { BloodPressureParser.normalizePressure(it.diastolicPressure!!).toDouble() }.average().roundToInt()
                add("A média de ${recent.size} registros ${averagePeriod(query)} é $sys por $dia mmHg.")
            } else add("Não há registros nos últimos 30 dias para calcular uma média desse período.")
        }
        return HealthMemoryResult("${introduction(userId, query)} ${details.joinToString(" ")} " +
            "Essa média descreve os registros e não determina diagnóstico ou estabilidade clínica.", true)
    }

    private fun canonicalType(type: String?) = when (type?.lowercase(Locale.ROOT)?.trim()) {
        "jejum", "fasting", "fast" -> "fasting"
        "pos-prandial", "postprandial", "pos", "after_meal", "post_prandial", "pos_prandial" -> "post_prandial"
        "cgm", "continuous", "sensor" -> "continuous"
        "aleatoria", "random", "casual" -> "random"
        "fingerstick", "capilar" -> "fingerstick"
        null, "" -> null
        else -> type.orEmpty().lowercase(Locale.ROOT).trim()
    }

    private fun aliases(type: String?): List<String> = when (canonicalType(type)) {
        "fasting" -> listOf("jejum", "fasting", "fast")
        "post_prandial" -> listOf("pos-prandial", "postprandial", "pos", "after_meal", "post_prandial", "pos_prandial")
        "continuous" -> listOf("cgm", "continuous", "sensor")
        "random" -> listOf("aleatoria", "random", "casual")
        "fingerstick" -> listOf("fingerstick", "capilar")
        null -> listOf("")
        else -> listOf(canonicalType(type)!!)
    }

    private fun typeLabel(type: String?): String = when (canonicalType(type)) {
        "fasting" -> "em jejum"
        "post_prandial" -> "após refeição"
        "continuous" -> "sensor contínuo, contexto de refeição não informado"
        "random" -> "medição casual"
        "fingerstick" -> "medição capilar, contexto de refeição não informado"
        null -> "tipo não informado"
        else -> "tipo cadastrado: ${type.orEmpty().take(40)}, contexto de refeição não identificado"
    }

    private suspend fun glucose(query: HealthQuery, userId: String, instant: Instant): HealthMemoryResult {
        val scope = interval(query, instant)
        val types = aliases(query.glucoseType)
        fun matches(record: VitalSignEntity?, interval: HealthQueryInterval): Boolean = valid(record, userId, interval) &&
            (record?.glucoseLevel ?: 0) > 0 && (query.glucoseType == null || canonicalType(record?.glucoseType) == canonicalType(query.glucoseType))
        val last = vitals.getLatestGlucoseForMemory(userId, scope.startInclusiveMillis, scope.endExclusiveMillis,
            query.glucoseType != null, types)?.takeIf { matches(it, scope) }
        if (query.operation == HealthOperation.LAST) {
            return if (last == null) absent(query, userId) else HealthMemoryResult(
                "${introduction(userId, query)} sua última glicemia ${periodLabel(query.period)} foi de ${last.glucoseLevel} mg/dL, " +
                    "${typeLabel(last.glucoseType)}, em ${time(last)}, ${elapsed(last, instant)}.", true, last.measuredAt.time, zone.id)
        }
        val recentScope = averageInterval(query, instant)
        val recent = vitals.getGlucoseIntervalForMemory(userId, recentScope.startInclusiveMillis, recentScope.endExclusiveMillis,
            query.glucoseType != null, types).filter { matches(it, recentScope) }
        if (recent.isEmpty() && (last == null || query.operation == HealthOperation.AVERAGE)) return absent(
            query.copy(period = if (query.period == HealthPeriod.ALL) HealthPeriod.LAST_30_DAYS else query.period), userId)
        val details = buildList {
            if (last != null && query.operation == HealthOperation.SUMMARY) add(
                "sua última glicemia foi de ${last.glucoseLevel} mg/dL, ${typeLabel(last.glucoseType)}, " +
                    "em ${time(last)}, ${elapsed(last, instant)}.")
            recent.groupBy { canonicalType(it.glucoseType) }.toSortedMap(compareBy { it ?: "" }).forEach { (_, records) ->
                val average = records.map { it.glucoseLevel!!.toDouble() }.average().roundToInt()
                add("A média de ${records.size} registros ${averagePeriod(query)} é $average mg/dL; ${typeLabel(records.first().glucoseType)}.")
            }
            if (recent.isEmpty()) add("Não há registros nos últimos 30 dias para calcular uma média desse período.")
        }
        return HealthMemoryResult("${introduction(userId, query)} ${details.joinToString(" ")} " +
            "Tipos diferentes são apresentados separadamente; médias sem contexto de medição não permitem uma avaliação clínica.", true)
    }

    private suspend fun water(query: HealthQuery, userId: String, instant: Instant): HealthMemoryResult {
        val effective = if (query.operation == HealthOperation.AVERAGE && query.period == HealthPeriod.ALL)
            query.copy(period = HealthPeriod.LAST_30_DAYS) else query
        val scope = interval(effective, instant)
        if (query.operation == HealthOperation.LAST) {
            val last = vitals.getLatestHydrationForMemory(userId, scope.startInclusiveMillis, scope.endExclusiveMillis)
                ?.takeIf { valid(it, userId, scope) && (it.hydrationMl ?: 0) > 0 }
            return if (last == null) absent(query, userId) else HealthMemoryResult(
                "${introduction(userId, query)} seu último registro de água ${periodLabel(query.period)} foi de ${last.hydrationMl} ml, " +
                    "em ${time(last)}, ${elapsed(last, instant)}. Esse registro não representa necessariamente toda a água consumida.", true)
        }
        val aggregate = vitals.getHydrationSummaryForMemory(userId, scope.startInclusiveMillis, scope.endExclusiveMillis)
        val total = aggregate.totalMl.coerceAtLeast(0)
        if (aggregate.recordCount <= 0 || total <= 0) {
            val goal = if (query.period == HealthPeriod.TODAY) {
                val configured = profiles.getProfileOneShot(userId)?.takeIf { it.userId == userId }?.hydrationTargetMl?.takeIf { it > 0 }
                if (configured == null) " A referência padrão do aplicativo é de 2000 ml e não é uma recomendação individual de consumo."
                else " A meta cadastrada é de $configured ml e não substitui uma orientação individual."
            } else ""
            return absent(effective, userId).let { it.copy(text = it.text +
                " A ausência de anotações não significa que você não bebeu água; mostra apenas o que está registrado.$goal " +
                "Se você recebeu orientação para restringir líquidos, siga essa orientação.") }
        }
        val intro = introduction(userId, query)
        val count = aggregate.recordCount
        if (effective.period != HealthPeriod.TODAY) {
            val days = when (effective.period) {
                HealthPeriod.LAST_7_DAYS -> 7
                HealthPeriod.LAST_30_DAYS -> 30
                else -> 1
            }
            val average = if (query.operation == HealthOperation.AVERAGE && effective.period in setOf(HealthPeriod.LAST_7_DAYS, HealthPeriod.LAST_30_DAYS))
                " A média por dia nesse intervalo é ${total / days} ml, considerando os dias sem anotação como zero registrado." else ""
            return HealthMemoryResult("$intro você registrou $total ml de água ${periodLabel(effective.period)}, em $count registros.$average " +
                "Esses valores descrevem as anotações e podem não incluir toda a água que você bebeu.", true)
        }
        val configured = profiles.getProfileOneShot(userId)?.takeIf { it.userId == userId }?.hydrationTargetMl?.takeIf { it > 0 }
        val goal = configured ?: 2000
        val goalLabel = if (configured == null) "referência padrão do aplicativo de" else "meta cadastrada de"
        val progress = total * 100 / goal
        val remaining = (goal.toLong() - total).coerceAtLeast(0)
        val reference = if (configured == null) "Essa referência não é uma recomendação individual de consumo." else
            "A meta cadastrada não substitui uma orientação individual."
        return HealthMemoryResult("$intro você registrou $total ml de água hoje, em $count registros: $progress% da $goalLabel $goal ml. " +
            "Faltam $remaining ml para esse valor nas anotações. $reference " +
            "Se você recebeu orientação para restringir líquidos, siga essa orientação. O total registrado pode não incluir toda a água consumida.", true)
    }
}
