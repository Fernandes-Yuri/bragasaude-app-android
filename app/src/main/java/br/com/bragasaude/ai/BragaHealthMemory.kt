package br.com.bragasaude.ai

import br.com.bragasaude.data.local.ProfileDao
import br.com.bragasaude.data.local.VitalSignDao
import br.com.bragasaude.data.local.VitalSignEntity
import br.com.bragasaude.data.local.BiometryDao
import br.com.bragasaude.data.local.MedicationDao
import br.com.bragasaude.data.local.MedicationLogDao
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
    private val zone: ZoneId,
    private val biometry: BiometryDao? = null,
    private val medications: MedicationDao? = null,
    private val medicationLogs: MedicationLogDao? = null
) {
    @Inject constructor(vitals: VitalSignDao, profiles: ProfileDao, biometry: BiometryDao,
                        medications: MedicationDao, medicationLogs: MedicationLogDao) : this(
        vitals, profiles, { Instant.now() }, ZoneId.systemDefault(), biometry, medications, medicationLogs
    )

    companion object {
        const val PRESSURE = "consulta_historico_pressao"
        const val GLUCOSE = "consulta_historico_glicemia"
        const val WATER = "consulta_historico_hidratacao"
        const val HEART_RATE = "consulta_historico_batimentos"
        const val OXYGEN = "consulta_historico_saturacao"
        const val WEIGHT = "consulta_historico_peso"
        const val MEDICATION_STOCK = "consulta_estoque_medicamento"
        const val MEDICATION_DOSES = "consulta_doses_medicamento"
        fun supports(intent: String) = intent in setOf(PRESSURE, GLUCOSE, WATER, HEART_RATE, OXYGEN, WEIGHT, MEDICATION_STOCK, MEDICATION_DOSES)
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
                HealthMetric.HEART_RATE, HealthMetric.OXYGEN -> auxiliary(query, userId, instant)
                HealthMetric.WEIGHT -> weight(query, userId, instant)
                HealthMetric.MEDICATION_STOCK, HealthMetric.MEDICATION_DOSES -> medication(query, userId, instant)
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
            HealthMetric.HEART_RATE -> "batimentos"
            HealthMetric.OXYGEN -> "saturação"
            HealthMetric.WEIGHT -> "peso"
            HealthMetric.MEDICATION_STOCK, HealthMetric.MEDICATION_DOSES -> "medicamento"
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
        if (query.operation == HealthOperation.COMPARE_YESTERDAY) return compareYesterday(query, userId, instant)
        val scope = interval(query, instant)
        val last = vitals.getLatestPressureForMemory(userId, scope.startInclusiveMillis, scope.endExclusiveMillis)
            ?.takeIf { valid(it, userId, scope) && (it.systolicPressure ?: 0) > 0 && (it.diastolicPressure ?: 0) > 0 }
        if (query.operation in setOf(HealthOperation.LAST, HealthOperation.AGE)) {
            return if (last == null) absent(query, userId) else HealthMemoryResult(
                "${introduction(userId, query)} sua última pressão ${periodLabel(query.period)} foi " +
                    "${BloodPressureParser.normalizePressure(last.systolicPressure!!)} por ${BloodPressureParser.normalizePressure(last.diastolicPressure!!)} mmHg, " +
                    "em ${time(last)}, ${elapsed(last, instant)}.${ageSuffix(query, last, instant)}", true, last.measuredAt.time, zone.id)
        }
        if (query.operation == HealthOperation.EXTREMES) {
            val rows = vitals.getPressureIntervalForMemory(userId, scope.startInclusiveMillis, scope.endExclusiveMillis)
                .filter { valid(it, userId, scope) && (it.systolicPressure ?: 0) > 0 && (it.diastolicPressure ?: 0) > 0 }
            if (rows.isEmpty()) return absent(query, userId)
            val systolic = rows.map { BloodPressureParser.normalizePressure(it.systolicPressure!!) }
            val diastolic = rows.map { BloodPressureParser.normalizePressure(it.diastolicPressure!!) }
            return HealthMemoryResult("Nos ${rows.size} registros de pressão ${periodLabel(query.period)}, a sistólica variou de ${systolic.min()} a ${systolic.max()} mmHg e a diastólica de ${diastolic.min()} a ${diastolic.max()} mmHg. Os limites são independentes e não formam uma medição única. Isso não determina diagnóstico.", true)
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
        if (query.operation == HealthOperation.COMPARE_YESTERDAY) return compareYesterday(query, userId, instant)
        val scope = interval(query, instant)
        val types = aliases(query.glucoseType)
        fun matches(record: VitalSignEntity?, interval: HealthQueryInterval): Boolean = valid(record, userId, interval) &&
            (record?.glucoseLevel ?: 0) > 0 && (query.glucoseType == null || canonicalType(record?.glucoseType) == canonicalType(query.glucoseType))
        val last = vitals.getLatestGlucoseForMemory(userId, scope.startInclusiveMillis, scope.endExclusiveMillis,
            query.glucoseType != null, types)?.takeIf { matches(it, scope) }
        if (query.operation in setOf(HealthOperation.LAST, HealthOperation.AGE)) {
            return if (last == null) absent(query, userId) else HealthMemoryResult(
                "${introduction(userId, query)} sua última glicemia ${periodLabel(query.period)} foi de ${last.glucoseLevel} mg/dL, " +
                    "${typeLabel(last.glucoseType)}, em ${time(last)}, ${elapsed(last, instant)}.${ageSuffix(query, last, instant)}", true, last.measuredAt.time, zone.id)
        }
        if (query.operation == HealthOperation.EXTREMES) {
            val rows = vitals.getGlucoseIntervalForMemory(userId, scope.startInclusiveMillis, scope.endExclusiveMillis, query.glucoseType != null, types).filter { matches(it, scope) }
            if (rows.isEmpty()) return absent(query, userId)
            return HealthMemoryResult("Nos ${rows.size} registros de glicemia ${periodLabel(query.period)}, o menor valor foi ${rows.minOf { it.glucoseLevel!! }} mg/dL e o maior foi ${rows.maxOf { it.glucoseLevel!! }} mg/dL. ${if (query.glucoseType == null) "Tipos de medição podem ser diferentes." else typeLabel(query.glucoseType)} Isso descreve as anotações, não um diagnóstico.", true)
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
        if (query.operation !in setOf(HealthOperation.LAST, HealthOperation.SUMMARY, HealthOperation.AVERAGE)) return unsupported("Para água, posso consultar o último registro, o total ou a média do período. Qual dessas consultas você deseja?")
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

    private fun ageSuffix(query: HealthQuery, record: VitalSignEntity, instant: Instant): String =
        if (query.operation != HealthOperation.AGE) "" else
            " Passaram ${Duration.between(record.measuredAt.toInstant(), instant).toDays()} dias completos desde esse registro. Isso descreve as anotações no app, não medições feitas fora dele."

    private fun unsupported(reply: String) = HealthMemoryResult(reply, false)

    private suspend fun auxiliary(query: HealthQuery, user: String, instant: Instant): HealthMemoryResult {
        if (query.operation !in setOf(HealthOperation.LAST, HealthOperation.AGE)) return unsupported("Para batimentos e saturação, esta consulta local apresenta a última leitura do período. Peça a última leitura e informe o período desejado.")
        val scope = interval(query, instant)
        val last = vitals.getLatestAuxiliaryForMemory(user, query.metric.name, scope.startInclusiveMillis, scope.endExclusiveMillis)
            ?.takeIf { valid(it, user, scope) && if (query.metric == HealthMetric.HEART_RATE) (it.heartRate ?: 0) > 0 else (it.oxygenSaturation ?: 0) in 1..100 }
            ?: return absent(query, user)
        val value = if (query.metric == HealthMetric.HEART_RATE) "${last.heartRate} bpm" else "${last.oxygenSaturation}%"
        val metric = if (query.metric == HealthMetric.HEART_RATE) "frequência cardíaca" else "saturação de oxigênio"
        return HealthMemoryResult("Sua última leitura de $metric ${periodLabel(query.period)} foi $value, em ${time(last)}, ${elapsed(last, instant)}.${ageSuffix(query, last, instant)} Essa leitura registrada não determina diagnóstico.", true, last.measuredAt.time, zone.id)
    }

    private suspend fun weight(query: HealthQuery, user: String, instant: Instant): HealthMemoryResult {
        if (query.operation != HealthOperation.LAST) return unsupported("Esta consulta de peso apresenta a última medição registrada no período. Peça seu último peso e informe o período desejado.")
        val dao = biometry ?: return unsupported("Não foi possível consultar as medições locais de peso agora.")
        val scope = interval(query, instant)
        val last = dao.getLatestWeightForMemory(user, scope.startInclusiveMillis, scope.endExclusiveMillis)
            ?.takeIf { it.userId == user && it.weight.isFinite() && it.weight > 0 && it.measuredAt.time >= scope.startInclusiveMillis && it.measuredAt.time < scope.endExclusiveMillis }
            ?: return absent(query, user)
        val date = DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm", Locale("pt", "BR")).format(last.measuredAt.toInstant().atZone(zone))
        return HealthMemoryResult("Seu último peso registrado ${periodLabel(query.period)} foi ${last.weight} kg, em $date. Esse valor vem de uma medição salva, não de uma estimativa pela conversa.", true, last.measuredAt.time, zone.id)
    }

    private suspend fun medication(query: HealthQuery, user: String, instant: Instant): HealthMemoryResult {
        if (query.operation !in setOf(HealthOperation.LAST, HealthOperation.SUMMARY)) return unsupported("Para medicamentos, posso consultar o estoque atual ou os registros de dose do período. Qual dessas consultas você deseja?")
        val name = query.medicationName ?: return unsupported("Qual é o nome exato do medicamento cadastrado em Remédios?")
        fun exact(value: String) = java.text.Normalizer.normalize(value.lowercase(Locale.ROOT), java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "").replace(Regex("\\s+"), " ").trim()
        val dao = medications ?: return unsupported("Não foi possível consultar seus medicamentos locais agora.")
        val matches = dao.getAllSync(user).filter { it.userId == user && exact(it.name) == exact(name) }
        if (matches.isEmpty()) return unsupported("Não encontrei um medicamento com esse nome exato na sua conta. Confira o nome cadastrado em Remédios; não escolho nomes por aproximação.")
        if (matches.size != 1) return unsupported("Há mais de um cadastro com esse nome. Confira o medicamento e a dosagem em Remédios; não vou escolher um deles automaticamente.")
        val medicine = matches.single()
        if (query.metric == HealthMetric.MEDICATION_STOCK) {
            if (query.period != HealthPeriod.ALL) return unsupported("O estoque local informa a quantidade atualmente cadastrada, não um histórico por dia. Você quer conferir o estoque atual?")
            if (medicine.currentUnits < 0 || (medicine.currentUnits == 0 && medicine.totalUnits == 0 && medicine.lastRestockDate == null))
                return unsupported("Não encontrei uma quantidade de estoque válida cadastrada para ${medicine.name}. Confira a tela de estoque; não posso afirmar que o medicamento acabou.")
            return HealthMemoryResult("O estoque cadastrado de ${medicine.name} está em ${medicine.currentUnits} unidades. É o valor anotado no app; confira também a embalagem. Não alterei estoque nem registrei dose.", true)
        }
        val logs = medicationLogs ?: return unsupported("Não foi possível consultar os registros locais de dose agora.")
        val scope = interval(query, instant)
        val rows = logs.getForMemory(user, medicine.id, scope.startInclusiveMillis, scope.endExclusiveMillis)
            .filter { it.userId == user && it.medicationId == medicine.id && it.unitsTaken > 0 && it.takenAt.time >= scope.startInclusiveMillis && it.takenAt.time < scope.endExclusiveMillis }
            .distinctBy { it.id }
        if (rows.isEmpty()) return unsupported("Não encontrei registros de dose de ${medicine.name} ${periodLabel(query.period)}. A ausência de anotação não confirma que você não tomou o medicamento. Confira os registros e sua receita antes de decidir sobre uma dose.")
        val last = rows.maxBy { it.takenAt.time }
        val date = DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm", Locale("pt", "BR")).format(last.takenAt.toInstant().atZone(zone))
        return HealthMemoryResult("Encontrei ${rows.size} ${if (rows.size == 1) "registro" else "registros"} de dose de ${medicine.name} ${periodLabel(query.period)}. O mais recente foi anotado em $date. Isso não confirma que todas as doses previstas foram tomadas. Não registrei uma nova dose.", true)
    }

    private suspend fun compareYesterday(query: HealthQuery, user: String, instant: Instant): HealthMemoryResult {
        if (query.period !in setOf(HealthPeriod.TODAY, HealthPeriod.ALL)) return unsupported("Esta comparação usa as últimas medições de hoje e ontem. Você deseja essa comparação ou a última medição do período informado?")
        suspend fun read(period: HealthPeriod): VitalSignEntity? {
            val scope = interval(query.copy(period = period), instant)
            return (if (query.metric == HealthMetric.PRESSURE) vitals.getLatestPressureForMemory(user, scope.startInclusiveMillis, scope.endExclusiveMillis)
                else vitals.getLatestGlucoseForMemory(user, scope.startInclusiveMillis, scope.endExclusiveMillis, query.glucoseType != null, aliases(query.glucoseType)))
                ?.takeIf { valid(it, user, scope) && if (query.metric == HealthMetric.PRESSURE)
                    (it.systolicPressure ?: 0) > 0 && (it.diastolicPressure ?: 0) > 0
                    else (it.glucoseLevel ?: 0) > 0 && (query.glucoseType == null || canonicalType(it.glucoseType) == canonicalType(query.glucoseType)) }
        }
        val today = read(HealthPeriod.TODAY)
        val yesterday = read(HealthPeriod.YESTERDAY)
        if (today == null || yesterday == null) return unsupported("Não consigo comparar as últimas medições de hoje e ontem: não encontrei registros válidos ${if (today == null && yesterday == null) "nos dois dias" else if (today == null) "hoje" else "ontem"}. Ausência de anotação não equivale a valor zero.")
        fun signed(value: Int) = if (value > 0) "+$value" else "$value"
        val text = if (query.metric == HealthMetric.PRESSURE) {
            val sys = BloodPressureParser.normalizePressure(today.systolicPressure!!)
            val dia = BloodPressureParser.normalizePressure(today.diastolicPressure!!)
            val oldSys = BloodPressureParser.normalizePressure(yesterday.systolicPressure!!)
            val oldDia = BloodPressureParser.normalizePressure(yesterday.diastolicPressure!!)
            "Hoje: $sys por $dia mmHg, em ${time(today)}. Ontem: $oldSys por $oldDia mmHg, em ${time(yesterday)}. Diferença registrada: sistólica ${signed(sys - oldSys)} mmHg; diastólica ${signed(dia - oldDia)} mmHg."
        } else {
            val values = "Hoje: ${today.glucoseLevel} mg/dL (${typeLabel(today.glucoseType)}), em ${time(today)}. Ontem: ${yesterday.glucoseLevel} mg/dL (${typeLabel(yesterday.glucoseType)}), em ${time(yesterday)}."
            if (canonicalType(today.glucoseType) !in setOf("fasting", "post_prandial", "random") || canonicalType(today.glucoseType) != canonicalType(yesterday.glucoseType))
                "$values Os contextos são diferentes ou não estão identificados; não comparo os valores automaticamente."
            else "$values Diferença registrada: ${signed(today.glucoseLevel!! - yesterday.glucoseLevel!!)} mg/dL."
        }
        return HealthMemoryResult("$text Essa comparação descreve registros, não identifica causa nem determina diagnóstico.", true)
    }
}
