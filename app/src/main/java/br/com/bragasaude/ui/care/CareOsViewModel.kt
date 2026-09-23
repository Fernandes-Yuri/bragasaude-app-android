package br.com.bragasaude.ui.care

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.bragasaude.data.local.BragaDatabase
import br.com.bragasaude.data.local.CareAuditEntity
import br.com.bragasaude.data.local.DailyMetricsDao
import br.com.bragasaude.data.local.MedicationEntity
import br.com.bragasaude.data.remote.api.BragaApiClient
import br.com.bragasaude.data.remote.model.BarcodeMedication
import br.com.bragasaude.data.remote.model.MedicalAccessGrant
import br.com.bragasaude.data.remote.repository.CareOsRepository
import br.com.bragasaude.data.remote.repository.MedicationRepository
import br.com.bragasaude.data.remote.repository.ProfileRepository
import br.com.bragasaude.data.remote.repository.VitalsRepository
import br.com.bragasaude.data.util.toRemote
import br.com.bragasaude.domain.CareCelebrationTts
import br.com.bragasaude.domain.MedicationSchedule
import br.com.bragasaude.domain.PdfReportGenerator
import br.com.bragasaude.util.BragaConstants
import br.com.bragasaude.util.BragaTime
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

/**
 * Care OS — D62. ViewModel único do módulo Care OS: estoque de medicamentos,
 * mural de cuidado, check-in matinal (C37), celebração por voz (C38) e modo
 * consulta ("Leva pro Doutor").
 */
@HiltViewModel
class CareOsViewModel @Inject constructor(
    private val medicationRepository: MedicationRepository,
    private val careOsRepository: CareOsRepository,
    private val celebrationTts: CareCelebrationTts,
    private val profileRepository: ProfileRepository,
    private val vitalsRepository: VitalsRepository,
    private val dailyMetricsDao: DailyMetricsDao,
    private val database: BragaDatabase,
    private val pdfGenerator: PdfReportGenerator,
    private val auth: FirebaseAuth
) : ViewModel() {

    private val userId: String
        get() = auth.currentUser?.uid ?: BragaConstants.GUEST_UID

    private val _ui = MutableStateFlow(CareOsUiState())
    val ui: StateFlow<CareOsUiState> = _ui.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                medicationRepository.getMedications(userId),
                careOsRepository.getCareWall(userId)
            ) { meds, wall ->
                CareOsUiState(
                    medications = meds,
                    wall = wall,
                    checkInDoneToday = careOsRepository.hasCheckInForToday(userId)
                )
            }.collect { _ui.value = _ui.value.copy(
                medications = it.medications,
                wall = it.wall,
                checkInDoneToday = it.checkInDoneToday
            ) }
        }
    }

    // ==================== ESTOQUE + DOSES ====================

    /**
     * Registra a toma e celebra (C38). O 409 é tratado graciosamente: avisa
     * "já registrado" sem duplicar decremento.
     */
    fun takeDose(medId: String, time: String? = null) {
        viewModelScope.launch {
            _ui.value = _ui.value.copy(loading = true, message = null)
            val name = _ui.value.medications.firstOrNull { it.id == medId }?.name
            when (val res = medicationRepository.takeDose(userId, medId, time)) {
                is BragaApiClient.TakeMedicationResult.Success -> {
                    _ui.value = _ui.value.copy(loading = false, isCelebrating = true)
                    celebrate(name)
                }
                is BragaApiClient.TakeMedicationResult.AlreadyTaken -> {
                    _ui.value = _ui.value.copy(
                        loading = false,
                        message = "Esta dose já havia sido registrada por outro cuidador. Tudo certo — nada foi descontado duas vezes."
                    )
                }
                is BragaApiClient.TakeMedicationResult.Failure -> {
                    _ui.value = _ui.value.copy(loading = false, message = res.message)
                }
            }
        }
    }

    fun restock(medId: String, units: Int) {
        viewModelScope.launch {
            medicationRepository.restock(medId, units)
            _ui.value = _ui.value.copy(message = "Estoque reabastecido.")
        }
    }

    /** Dias restantes de um medicamento (nunca arredondado para cima). */
    fun daysRemaining(med: MedicationEntity): Double {
        val dosesPerDay = MedicationSchedule.times(med.scheduleTimes, med.scheduleTime)
            .takeIf { it.isNotEmpty() }?.size?.toDouble() ?: 1.0
        return BragaTime.daysRemaining(med.currentUnits, dosesPerDay)
    }

    /** Alerta de reposição conforme alertThresholdDays do cadastro. */
    fun isStockCritical(med: MedicationEntity): Boolean {
        val dosesPerDay = MedicationSchedule.times(med.scheduleTimes, med.scheduleTime)
            .takeIf { it.isNotEmpty() }?.size?.toDouble() ?: 1.0
        return daysRemaining(med) <= med.alertThresholdDays
    }

    // ==================== CENA C38 — CELEBRAÇÃO ====================

    private suspend fun celebrate(medName: String?) {
        try {
            val profile = profileRepository.getProfile(userId).firstOrNull()?.toRemote()
            celebrationTts.celebrate(profile?.fullName)
        } catch (e: Exception) {
            celebrationTts.celebrate(null)
        } finally {
            _ui.value = _ui.value.copy(isCelebrating = false)
        }
    }

    // ==================== SCANNER EAN-13 + CADASTRO ====================

    fun lookupBarcode(ean: String, onResult: (BarcodeMedication?) -> Unit) {
        viewModelScope.launch {
            val result = if (ean.matches(Regex("^\\d{13}$"))) {
                medicationRepository.lookupBarcode(ean)
            } else null
            onResult(result)
            if (result == null) {
                _ui.value = _ui.value.copy(message = "Medicamento não encontrado no catálogo. Preencha manualmente.")
            }
        }
    }

    fun createMedication(
        name: String,
        totalUnits: Int,
        scheduleTimes: List<String>,
        confirmedWithPrescription: Boolean,
        barcode: BarcodeMedication? = null,
        dosageMg: Double? = null,
        photoReferenceUrl: String? = null,
        onDone: (Boolean) -> Unit
    ) {
        viewModelScope.launch {
            _ui.value = _ui.value.copy(loading = true, message = null)
            val id = try {
                medicationRepository.createCareOsMedication(
                    patientId = userId,
                    name = name,
                    totalUnits = totalUnits,
                    scheduleTimes = scheduleTimes,
                    confirmedWithPrescription = confirmedWithPrescription,
                    barcode = barcode,
                    dosageMg = dosageMg,
                    photoReferenceUrl = photoReferenceUrl
                )
            } catch (e: IllegalArgumentException) {
                _ui.value = _ui.value.copy(loading = false, message = e.message)
                null
            }
            _ui.value = _ui.value.copy(
                loading = false,
                message = if (id != null) "Medicamento cadastrado com sucesso." else "Não foi possível cadastrar. Verifique sua conexão."
            )
            onDone(id != null)
        }
    }

    // ==================== CENA C37 — CHECK-IN MATINAL ====================

    fun submitCheckIn(symptomsText: String, sleepQuality: Int?, disposition: Int?, inputMethod: String = "VOICE") {
        viewModelScope.launch {
            if (symptomsText.isBlank()) {
                _ui.value = _ui.value.copy(message = "Conte como você está se sentindo.")
                return@launch
            }
            _ui.value = _ui.value.copy(loading = true, message = null)
            val ok = careOsRepository.submitCheckIn(
                patientId = userId,
                symptomsText = symptomsText,
                sleepQuality = sleepQuality,
                disposition = disposition,
                inputMethod = inputMethod
            )
            _ui.value = _ui.value.copy(
                loading = false,
                checkInDoneToday = true,
                message = if (ok) "Check-in registrado. Obrigado por compartilhar." else "Check-in salvo no aparelho; enviamos assim que a internet voltar."
            )
        }
    }

    // ==================== MURAL ====================

    fun refreshWall() {
        viewModelScope.launch { careOsRepository.syncCareWall(userId) }
    }

    // ==================== MODO CONSULTA — "LEVA PRO DOUTOR" ====================

    fun generateMedicalAccess() {
        viewModelScope.launch {
            _ui.value = _ui.value.copy(isGeneratingAccess = true, message = null)
            val grant = careOsRepository.generateMedicalAccess(userId)
            _ui.value = _ui.value.copy(
                isGeneratingAccess = false,
                medicalAccess = grant,
                message = if (grant == null) "Não foi possível gerar o acesso. Tente novamente." else null
            )
        }
    }

    fun generateDoctorReport() {
        viewModelScope.launch {
            _ui.value = _ui.value.copy(isGeneratingReport = true, message = null)
            val file = careOsRepository.getDoctorReport(userId, days = 30) {
                generateLocalReportFallback()
            }
            _ui.value = _ui.value.copy(
                isGeneratingReport = false,
                reportFile = file,
                message = if (file == null) "Não foi possível gerar o relatório." else null
            )
        }
    }

    /** Fallback on-device (PdfReportGenerator) se o servidor não devolver PDF. */
    private suspend fun generateLocalReportFallback(): File? = try {
        val profile = profileRepository.getProfile(userId).firstOrNull()?.toRemote()
            ?: br.com.bragasaude.data.remote.model.RemoteProfile(id = userId, fullName = "Visitante")
        val vitals = vitalsRepository.getVitalSignsSync(userId)
        val metrics = dailyMetricsDao.getRecent30Days(userId).firstOrNull() ?: emptyList()
        val wearables = database.wearableReadingDao()
            .getForReport(userId, System.currentTimeMillis() - 30L * 86400_000L)
        pdfGenerator.generateReport(profile, vitals, metrics, wearables)
    } catch (e: Exception) {
        null
    }

    // ==================== FICHA DE EMERGÊNCIA ====================

    fun generateEmergencyAccess(onResult: (br.com.bragasaude.data.remote.model.EmergencyTokenGrant?) -> Unit = {}) {
        viewModelScope.launch {
            val grant = careOsRepository.generateEmergencyAccess(userId)
            _ui.value = _ui.value.copy(
                message = if (grant == null) "Não foi possível gerar a ficha de emergência." else "Ficha de emergência gerada."
            )
            onResult(grant)
        }
    }

    fun consumeMessage() {
        _ui.value = _ui.value.copy(message = null)
    }

    override fun onCleared() {
        super.onCleared()
        celebrationTts.shutdown()
    }
}

data class CareOsUiState(
    val loading: Boolean = false,
    val medications: List<MedicationEntity> = emptyList(),
    val wall: List<CareAuditEntity> = emptyList(),
    val checkInDoneToday: Boolean = false,
    val message: String? = null,
    val isCelebrating: Boolean = false,
    val medicalAccess: MedicalAccessGrant? = null,
    val reportFile: File? = null,
    val isGeneratingAccess: Boolean = false,
    val isGeneratingReport: Boolean = false
)
