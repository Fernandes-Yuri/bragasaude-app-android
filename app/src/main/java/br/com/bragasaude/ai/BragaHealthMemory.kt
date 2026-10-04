package br.com.bragasaude.ai

import br.com.bragasaude.data.local.ProfileDao
import br.com.bragasaude.data.local.VitalSignDao
import br.com.bragasaude.data.local.VitalSignEntity
import br.com.bragasaude.domain.util.BloodPressureParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import kotlin.math.roundToInt

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
    @Synchronized private fun introduction(userId: String, intent: String): String {
        if (owner != userId) { previous.clear(); owner = userId }
        val choices = listOf("Nos seus registros locais,", "Consultando suas anotações,",
            "Pelo histórico salvo no aparelho,", "Com base nos seus registros no aplicativo,")
        return choices.filter { it != previous[intent] }.random().also { previous[intent] = it }
    }

    suspend fun answer(intent: String, userId: String): String = withContext(Dispatchers.IO) {
        require(supports(intent))
        val intro = introduction(userId, intent)
        if (userId.isBlank() || userId == "anonymous")
            return@withContext "$intro entre na sua conta para consultar seus dados de saúde."
        val instant = now()
        val current = instant.toEpochMilli()
        val since = instant.atZone(zone).minusDays(30).toInstant().toEpochMilli()
        fun valid(records: List<VitalSignEntity>, start: Long = Long.MIN_VALUE) = records.filter {
            it.userId == userId && it.measuredAt.time in start..current
        }.sortedByDescending { it.measuredAt.time }
        fun time(record: VitalSignEntity) = DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm", Locale("pt", "BR"))
            .format(record.measuredAt.toInstant().atZone(zone))
        fun pressure(records: List<VitalSignEntity>, start: Long = Long.MIN_VALUE) = valid(records, start).filter {
            (it.systolicPressure ?: 0) > 0 && (it.diastolicPressure ?: 0) > 0
        }
        try {
            when (intent) {
                PRESSURE -> {
                    val last = pressure(vitals.getBloodPressureRecords(userId).first()).firstOrNull()
                        ?: return@withContext "$intro ainda não há uma medição completa de pressão. Você pode registrar os valores na tela de pressão."
                    val recent = pressure(vitals.getBloodPressureRecent30Days(userId, since).first(), since)
                    val systolic = BloodPressureParser.normalizePressure(last.systolicPressure!!)
                    val diastolic = BloodPressureParser.normalizePressure(last.diastolicPressure!!)
                    val average = if (recent.isEmpty()) "Não há medições completas nos últimos 30 dias para calcular a média."
                    else "A média de ${recent.size} registros nos últimos 30 dias é " +
                        "${recent.map { BloodPressureParser.normalizePressure(it.systolicPressure!!).toDouble() }.average().roundToInt()} por " +
                        "${recent.map { BloodPressureParser.normalizePressure(it.diastolicPressure!!).toDouble() }.average().roundToInt()} milímetros de mercúrio."
                    "$intro seu último registro de pressão foi $systolic por $diastolic milímetros de mercúrio em ${time(last)}. $average"
                }
                GLUCOSE -> {
                    val last = valid(vitals.getGlucoseRecords(userId).first()).firstOrNull { (it.glucoseLevel ?: 0) > 0 }
                        ?: return@withContext "$intro ainda não há um valor de glicemia registrado. Você pode registrar uma medição na tela de glicemia."
                    val recent = valid(vitals.getGlucoseRecent30Days(userId, since).first(), since).filter { (it.glucoseLevel ?: 0) > 0 }
                    val average = if (recent.isEmpty()) "Não há valores nos últimos 30 dias para calcular a média."
                    else "A média de ${recent.size} registros nos últimos 30 dias é ${recent.map { it.glucoseLevel!!.toDouble() }.average().roundToInt()} miligramas por decilitro."
                    "$intro sua última glicemia foi ${last.glucoseLevel} miligramas por decilitro em ${time(last)}. $average Os registros podem incluir horários e condições diferentes; a média não é uma avaliação clínica."
                }
                else -> {
                    val dayStart = instant.atZone(zone).toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()
                    val consumed = valid(vitals.getHydrationRecent30Days(userId, dayStart).first(), dayStart)
                        .sumOf { (it.hydrationMl ?: 0).coerceAtLeast(0).toLong() }
                    val profile = profiles.getProfileOneShot(userId)
                    val configured = profile?.hydrationTargetMl?.takeIf { it > 0 }
                    val goal = configured ?: 2000
                    val goalLabel = if (configured == null) "referência padrão do aplicativo de" else "meta cadastrada de"
                    val progress = consumed * 100 / goal
                    val remaining = (goal.toLong() - consumed).coerceAtLeast(0)
                    "$intro hoje você registrou $consumed ml de água: $progress% da $goalLabel $goal ml. Faltam $remaining ml para esse valor. Respeite a orientação do seu profissional sobre líquidos."
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            "$intro não consegui consultar o banco local agora. Tente novamente ou consulte seus registros na tela correspondente."
        }
    }
}
