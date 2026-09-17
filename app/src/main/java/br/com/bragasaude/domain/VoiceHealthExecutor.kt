package br.com.bragasaude.domain

import br.com.bragasaude.data.local.ProfileDao
import br.com.bragasaude.data.remote.model.RemoteBiometry
import br.com.bragasaude.data.remote.model.RemoteMedication
import br.com.bragasaude.data.remote.model.RemoteVitalSign
import br.com.bragasaude.data.remote.repository.BiometryRepository
import br.com.bragasaude.data.remote.repository.MedicationRepository
import br.com.bragasaude.data.remote.repository.VitalsRepository
import br.com.bragasaude.data.local.VitalSignDao
import br.com.bragasaude.data.remote.repository.FamilyBridgeRepository
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Executor das intenções de voz.
 *
 * Persiste registros de sinais vitais quando acionado.
 */
@Singleton
class VoiceHealthExecutor @Inject constructor(
    private val vitalsRepository: VitalsRepository,
    private val biometryRepository: BiometryRepository,
    private val medicationRepository: MedicationRepository,
    private val profileDao: ProfileDao,
    private val familyRepository: FamilyBridgeRepository,
    private val vitalSignDao: VitalSignDao,
    private val auth: FirebaseAuth
) {

    private val currentUserId: String
        get() = auth.currentUser?.uid ?: br.com.bragasaude.util.BragaConstants.GUEST_UID

    /**
     * Persiste a intenção e retorna uma descrição curta do que foi registrado.
     *
     * Regra de ouro (D4 / DECISOES.md): NUNCA prescreve, sugere ou altera
     * medicação — apenas marca adesão informada pelo idoso.
     */
    suspend fun execute(intent: VoiceHealthIntent): String {
        val userId = currentUserId
        val nowIso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(Date())

        return when (intent) {

            is VoiceHealthIntent.Hydration -> {
                val vital = RemoteVitalSign(
                    id = UUID.randomUUID().toString(),
                    userId = userId,
                    hydrationMl = intent.amountMl,
                    measuredAt = nowIso,
                    status = "recorded"
                )
                vitalsRepository.saveVitalSigns(listOf(vital))
                "Registro de hidratacao: ${intent.description} registrado!"
            }

            is VoiceHealthIntent.BloodPressure -> {
                val vital = RemoteVitalSign(
                    id = UUID.randomUUID().toString(),
                    userId = userId,
                    systolicPressure = intent.systolic,
                    diastolicPressure = intent.diastolic,
                    measuredAt = nowIso,
                    status = "recorded"
                )
                vitalsRepository.saveVitalSigns(listOf(vital))
                "Registro de pressao: ${intent.systolic}/${intent.diastolic} mmHg registrado!"
            }

            is VoiceHealthIntent.Glucose -> {
                val vital = RemoteVitalSign(
                    id = UUID.randomUUID().toString(),
                    userId = userId,
                    glucoseLevel = intent.glucoseMgDl,
                    glucoseType = "fingerstick",
                    measuredAt = nowIso,
                    status = "recorded"
                )
                vitalsRepository.saveVitalSigns(listOf(vital))
                "🩸 Glicemia ${intent.glucoseMgDl} mg/dL registrada!"
            }

            is VoiceHealthIntent.Weight -> {
                val profile = profileDao.getProfileOneShot(userId)
                val height = profile?.height?.toFloat() ?: 1.65f
                val imc = HealthCalculators.calculateIMC(intent.weightKg.toFloat(), height)
                val biometry = RemoteBiometry(
                    id = UUID.randomUUID().toString(),
                    userId = userId,
                    weight = intent.weightKg.toFloat(),
                    height = height,
                    imc = imc,
                    measuredAt = nowIso
                )
                biometryRepository.saveBiometry(biometry)
                "⚖️ Peso %.1f kg registrado!".format(intent.weightKg)
            }

            is VoiceHealthIntent.HeartRate -> {
                val vital = RemoteVitalSign(
                    id = UUID.randomUUID().toString(),
                    userId = userId,
                    heartRate = intent.heartRateBpm,
                    measuredAt = nowIso,
                    status = "recorded"
                )
                vitalsRepository.saveVitalSigns(listOf(vital))
                "❤️ Batimentos: ${intent.heartRateBpm} bpm registrado!"
            }

            is VoiceHealthIntent.OxygenSaturation -> {
                val vital = RemoteVitalSign(
                    id = UUID.randomUUID().toString(),
                    userId = userId,
                    oxygenSaturation = intent.oxygenPercent,
                    measuredAt = nowIso,
                    status = "recorded"
                )
                vitalsRepository.saveVitalSigns(listOf(vital))
                "🫁 Saturação: ${intent.oxygenPercent}% registrado!"
            }

            is VoiceHealthIntent.Medication -> {
                val meds = medicationRepository.getMedications(userId).first()
                val match = meds.firstOrNull { med ->
                    med.name.contains(intent.query, ignoreCase = true) ||
                        intent.query.contains(med.name, ignoreCase = true)
                }
                if (match != null) {
                    medicationRepository.takeMedication(userId, match.id)
                    "Medicacao: ${match.name} marcado como tomado!"
                } else {
                    val newMed = RemoteMedication(
                        id = UUID.randomUUID().toString(),
                        userId = userId,
                        name = intent.query.replaceFirstChar {
                            if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString()
                        },
                        scheduleTime = intent.scheduleTimeHint
                    )
                    medicationRepository.saveMedication(newMed)
                    "Medicacao: \"${newMed.name}\" registrado!"
                }
            }

            is VoiceHealthIntent.Meal,
            is VoiceHealthIntent.Grocery,
            is VoiceHealthIntent.SuggestFood -> {
                "O assistente de voz não realiza anotação de refeições ou compras. Utilize a tela de Alimentação."
            }

            is VoiceHealthIntent.ClarificationRequired -> {
                intent.questionPrompt
            }

            is VoiceHealthIntent.ConversationalReply -> {
                intent.message
            }

            is VoiceHealthIntent.FamilyMedicationReminder -> {
                val bindings = familyRepository.getActiveBindingsForCaregiver(userId).first()
                val targetPatientId = bindings.firstOrNull()?.patientUserId
                if (targetPatientId != null) {
                    val reminderText = intent.text + if (!intent.timeHint.isNullOrBlank()) " às ${intent.timeHint}" else ""
                    familyRepository.sendCareMessage(
                        patientUserId = targetPatientId,
                        senderName = auth.currentUser?.displayName ?: "Familiar",
                        messageText = "Lembrete: $reminderText",
                        iconType = "MED"
                    )
                    "Lembrete de medicação enviado para seu familiar!"
                } else {
                    "Nenhum familiar vinculado encontrado para enviar o lembrete."
                }
            }

            is VoiceHealthIntent.FamilyAppointment -> {
                val bindings = familyRepository.getActiveBindingsForCaregiver(userId).first()
                val targetPatientId = bindings.firstOrNull()?.patientUserId
                if (targetPatientId != null) {
                    val apptText = intent.title + if (!intent.dateTimeHint.isNullOrBlank()) " (${intent.dateTimeHint})" else ""
                    familyRepository.sendCareMessage(
                        patientUserId = targetPatientId,
                        senderName = auth.currentUser?.displayName ?: "Familiar",
                        messageText = "Consulta: $apptText",
                        iconType = "CALENDAR"
                    )
                    "Consulta agendada enviada para seu familiar!"
                } else {
                    "Nenhum familiar vinculado encontrado para agendar."
                }
            }

            is VoiceHealthIntent.QueryPatientStatus -> {
                val bindings = familyRepository.getActiveBindingsForCaregiver(userId).first()
                val targetPatientId = bindings.firstOrNull()?.patientUserId
                if (targetPatientId != null) {
                    val vitals = vitalSignDao.getAllOneShot(targetPatientId)
                    val metricLower = intent.metric.lowercase()
                    when {
                        metricLower.contains("glic") -> {
                            val gluc = vitals.firstOrNull { it.glucoseLevel != null }
                            if (gluc != null) {
                                val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(gluc.measuredAt)
                                "A última glicemia registrada do seu familiar foi ${gluc.glucoseLevel} mg/dL, às $time."
                            } else {
                                "Não encontrei registros recentes de glicemia do seu familiar hoje."
                            }
                        }
                        metricLower.contains("agua") || metricLower.contains("água") || metricLower.contains("hidrata") -> {
                            val todayStart = java.util.Calendar.getInstance().apply {
                                set(java.util.Calendar.HOUR_OF_DAY, 0)
                                set(java.util.Calendar.MINUTE, 0)
                                set(java.util.Calendar.SECOND, 0)
                                set(java.util.Calendar.MILLISECOND, 0)
                            }.timeInMillis
                            val water = vitals.filter { it.hydrationMl != null && it.hydrationMl > 0 && it.measuredAt.time >= todayStart }
                                .sumOf { it.hydrationMl ?: 0 }
                            "Seu familiar registrou $water ml de água hoje."
                        }
                        else -> {
                            val bp = vitals.firstOrNull { it.systolicPressure != null && it.diastolicPressure != null }
                            if (bp != null) {
                                val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(bp.measuredAt)
                                "A última pressão registrada do seu familiar foi ${bp.systolicPressure} por ${bp.diastolicPressure} mmHg, às $time."
                            } else {
                                "Não encontrei medições recentes de pressão do seu familiar hoje."
                            }
                        }
                    }
                } else {
                    "Nenhum familiar vinculado para consulta no momento."
                }
            }

            is VoiceHealthIntent.Unknown -> {
                "Não consegui entender. Toque no microfone novamente e tente de outra forma."
            }
        }
    }
}
