package br.com.bragasaude.data.local.slm

import br.com.bragasaude.data.local.*
import br.com.bragasaude.data.remote.auth.AuthService
import br.com.bragasaude.data.remote.repository.FamilyBridgeRepository
import br.com.bragasaude.domain.MedicationSchedule
import br.com.bragasaude.domain.VoiceHealthIntent
import br.com.bragasaude.domain.VoiceHealthParser
import kotlinx.coroutines.flow.first
import java.time.*
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

data class BragaResolvedContext(
    val type: BragaIntent,
    val instruction: String,
    val fallback: String,
    val action: String? = null,
    val parameters: Map<String, String> = emptyMap(),
    val draft: VoiceHealthIntent? = null,
    val factual: Boolean = true
)

/** Room é a memória clínica; o modelo recebe apenas fatos e um rascunho já validado. */
@Singleton
class BragaContextResolver @Inject constructor(
    private val router: BragaIntentRouter,
    private val parser: VoiceHealthParser,
    private val vitals: VitalSignDao,
    private val medications: MedicationDao,
    private val logs: MedicationLogDao,
    private val profiles: ProfileDao,
    private val auth: AuthService,
    private val family: FamilyBridgeRepository
) {
    suspend fun resolve(text: String, actingAs: String? = null, patientId: String? = null,
                        now: ZonedDateTime = ZonedDateTime.now()): BragaResolvedContext {
        val request = router.route(text)
        if (request.type == BragaIntent.EMERGENCIA) return BragaResolvedContext(request.type,
            "Há uma emergência. Oriente ligar para o SAMU 192 e procurar socorro agora.",
            "Ligue para o SAMU 192 e procure socorro agora. As opções de ajuda estão aqui.", "EMERGENCIA")
        if (request.type == BragaIntent.CONVERSA_LIVRE) return BragaResolvedContext(request.type,
            "Converse sobre o assunto do usuário e acolha seus sentimentos. Nenhum registro foi solicitado ou salvo. Não invente histórico, diagnóstico ou dose de remédio.",
            "Estou aqui com você, meu bem. Pode me contar um pouco mais?", factual = false)
        val owner = auth.currentUserId ?: return unavailable(request.type, "Entre na sua conta para consultar ou preparar seus registros.")
        var target = owner
        if (request.otherPerson) {
            if (request.type != BragaIntent.CONSULTA_HISTORICO || actingAs != "caregiver" || patientId.isNullOrBlank())
                return unavailable(request.type, "Preciso que você selecione o familiar na área de acompanhamento para consultar os dados dele.")
            val activeBindings = family.getActiveBindingsForCaregiver(owner).first().filter { it.status.equals("ACTIVE", true) }
            val authorized = activeBindings.size == 1 && activeBindings.any {
                it.patientUserId == patientId && it.status.equals("ACTIVE", true)
            }
            if (!authorized) return unavailable(request.type, "Não encontrei um vínculo ativo com esse familiar.")
            target = patientId
        }
        val profile = profiles.getProfileOneShot(owner)
        if (request.type != BragaIntent.CONSULTA_HISTORICO && profile?.userRole == "CAREGIVER" && profile.caregiverMode != "HYBRID")
            return unavailable(request.type, "O registro pessoal está desativado no modo Acompanhante.")
        val resolved = when (request.type) {
            BragaIntent.CONSULTA_HISTORICO -> history(request, target, now)
            BragaIntent.REGISTRO_MEDICAMENTO -> medication(request, target, now)
            else -> draft(request)
        }
        check(auth.currentUserId == owner) { "A conta mudou. Abra novamente a conversa." }
        return resolved
    }

    private fun unavailable(type: BragaIntent, message: String) = BragaResolvedContext(type,
        "Responda somente esta informação: $message Nenhum registro foi salvo.", message)

    private fun draft(request: BragaRequest): BragaResolvedContext = when (val intent = request.draft) {
        is VoiceHealthIntent.Hydration -> prepared(request, "${intent.amountMl} mililitros de água", "REGISTRAR_AGUA", mapOf("quantidade_ml" to intent.amountMl.toString()))
        is VoiceHealthIntent.BloodPressure -> prepared(request, "pressão ${intent.systolic} por ${intent.diastolic}", "REGISTRAR_PRESSAO", mapOf("sistolica" to intent.systolic.toString(), "diastolica" to intent.diastolic.toString()))
        is VoiceHealthIntent.Glucose -> prepared(request, "glicose ${intent.glucoseMgDl} mg/dL", "REGISTRAR_GLICEMIA", mapOf("glicemia" to intent.glucoseMgDl.toString()))
        else -> unavailable(request.type, "Preciso que você me diga o valor da medida para preparar a anotação.")
    }
    private fun prepared(request: BragaRequest, value: String, action: String, params: Map<String, String>) =
        BragaResolvedContext(request.type, "O Kotlin preparou $value. A gravação ainda está pendente. Diga com carinho que a anotação está pronta. Não diga que já salvou.",
            "Deixei a anotação de $value pronta para você, meu bem.", action, params, request.draft)

    private suspend fun history(request: BragaRequest, target: String, now: ZonedDateTime): BragaResolvedContext {
        if (request.unsupportedPeriod) return unavailable(request.type,
            "Posso consultar hoje, ontem, anteontem ou a última medição. Qual período você prefere?")
        val date = now.toLocalDate().minusDays(request.dayOffset ?: 0)
        val start = if (request.dayOffset == null && request.metric != BragaMetric.AGUA) 0L else date.atStartOfDay(now.zone).toInstant().toEpochMilli()
        val end = date.plusDays(1).atStartOfDay(now.zone).toInstant().toEpochMilli()
        val period = when (request.dayOffset) { 1L -> "ontem"; 2L -> "anteontem"; else -> "hoje" }
        val fact = when (request.metric) {
            BragaMetric.AGUA -> {
                val amount = vitals.hydrationInRange(target, start, end)
                if (amount == 0L) "Não encontrei água registrada $period." else "O total de água registrado $period é $amount mililitros."
            }
            BragaMetric.PRESSAO -> vitals.latestPressureInRange(target, start, end)?.let {
                "A pressão registrada foi ${it.systolicPressure} por ${it.diastolicPressure}, ${timeOf(it.measuredAt.time, now.zone)}."
            } ?: "Não encontrei pressão registrada nesse período."
            BragaMetric.GLICOSE -> vitals.latestGlucoseInRange(target, start, end)?.let {
                "A glicose registrada foi ${it.glucoseLevel} mg/dL, ${timeOf(it.measuredAt.time, now.zone)}."
            } ?: "Não encontrei glicose registrada nesse período."
            null -> "Qual histórico você quer consultar?"
        }
        return BragaResolvedContext(request.type, "O banco local informa: $fact Responda com carinho usando somente esses fatos, sem avaliar a medida.", fact)
    }
    private fun timeOf(millis: Long, zone: ZoneId): String = Instant.ofEpochMilli(millis).atZone(zone)
        .format(DateTimeFormatter.ofPattern("'em' dd/MM/yyyy 'às' HH:mm"))

    private suspend fun medication(request: BragaRequest, owner: String, now: ZonedDateTime): BragaResolvedContext {
        val date = now.toLocalDate()
        val start = date.atStartOfDay(now.zone).toInstant().toEpochMilli()
        val end = date.plusDays(1).atStartOfDay(now.zone).toInstant().toEpochMilli()
        val taken = logs.logsInRange(owner, start, end)
        val query = request.medicationQuery.orEmpty()
        val candidates = medications.getAllSync(owner).filter { med ->
            query.isBlank() || parser.normalize(med.name) == query ||
                Regex("\\b${Regex.escape(parser.normalize(med.name))}\\b").containsMatchIn(query)
        }.flatMap { med -> MedicationSchedule.times(med.scheduleTimes, med.scheduleTime).mapNotNull { time ->
            val distance = java.time.Duration.between(date.atTime(LocalTime.parse(time)), now.toLocalDateTime()).toMinutes()
            val recorded = taken.any { it.medicationId == med.id && (it.scheduledFor == MedicationSchedule.key(date, time) || it.scheduledFor == null) }
            if (distance in -120..120 && !recorded) med to time else null
        } }
        if (candidates.size != 1) return unavailable(request.type, if (candidates.isEmpty())
            "Não encontrei uma dose pendente desse remédio neste horário. Qual remédio e horário você quer marcar?"
            else "Há mais de uma dose pendente. Qual remédio e horário você tomou?")
        val (med, time) = candidates.single()
        val dose = med.dosage?.takeIf { it.isNotBlank() } ?: med.dosageMg?.let { "$it mg" } ?: "dose não informada no cadastro"
        val name = med.name
        val intent = VoiceHealthIntent.Medication(name, time, med.id, date.toString())
        return BragaResolvedContext(request.type,
            "O Kotlin encontrou $name, $dose, horário $time. A marcação como tomado está preparada, ainda não salva. Diga isso com afeto, sem sugerir tomar outra dose.",
            "Deixei pronta a marcação de $name, $dose, das $time, meu bem.", "REGISTRAR_MEDICAMENTO",
            mapOf("medication_id" to med.id, "medicamento" to name, "dose" to dose, "horario" to time, "data" to date.toString()), intent)
    }
}
