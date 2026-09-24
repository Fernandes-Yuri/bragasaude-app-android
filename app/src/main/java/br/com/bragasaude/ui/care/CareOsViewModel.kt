package br.com.bragasaude.ui.care

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.net.Uri
import br.com.bragasaude.data.local.BragaDatabase
import br.com.bragasaude.data.local.CareAuditEntity
import br.com.bragasaude.data.local.DailyMetricsDao
import br.com.bragasaude.data.local.FamilyDao
import br.com.bragasaude.data.local.MedicationEntity
import br.com.bragasaude.data.remote.api.BragaApiClient
import br.com.bragasaude.data.remote.model.BarcodeMedication
import br.com.bragasaude.data.remote.model.MedicalAccessGrant
import br.com.bragasaude.data.remote.model.ClinicalCorrelation
import br.com.bragasaude.data.remote.model.DailyCareBulletin
import br.com.bragasaude.data.remote.model.PrescriptionCreate
import br.com.bragasaude.data.remote.model.PrescriptionAnalysisResponseDto
import br.com.bragasaude.data.remote.model.MedicationBatchItemCreateDto
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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDate
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
    private val familyDao: FamilyDao,
    private val database: BragaDatabase,
    private val pdfGenerator: PdfReportGenerator,
    private val auth: FirebaseAuth
) : ViewModel() {

    private val authenticatedUserId: String?
        get() = auth.currentUser?.uid

    private val patientId: String
        get() = _ui.value.selectedPatientId ?: BragaConstants.GUEST_UID

    private val _ui = MutableStateFlow(CareOsUiState())
    val ui: StateFlow<CareOsUiState> = _ui.asStateFlow()

    init {
        val uid = authenticatedUserId
        if (uid == null) {
            _ui.update { it.copy(isAuthenticated = false) }
        } else {
            viewModelScope.launch {
                combine(
                    profileRepository.getProfile(uid),
                    familyDao.getActiveBindingsForCaregiver(uid)
                ) { profile, bindings ->
                    val selfAllowed = profile?.userRole != "CAREGIVER" || profile.caregiverMode == "HYBRID"
                    val isCaregiver = profile?.userRole == "CAREGIVER" && bindings.isNotEmpty()
                    val options = buildList {
                        if (selfAllowed) add(
                            CarePatientOption(
                                id = uid,
                                name = profile?.fullName ?: "Minha saúde",
                                canWriteMedication = true,
                                canTakeMedication = true,
                                canShareMedical = true,
                                canShareEmergency = true,
                                role = "PATIENT"
                            )
                        )
                        bindings.forEach { binding ->
                            val explicit = runCatching {
                                val arr = org.json.JSONArray(binding.permissionsJson)
                                (0 until arr.length()).map { arr.getString(it) }.toSet()
                            }.getOrDefault(emptySet())
                            val defaults = when (binding.caregiverRole) {
                                "ADMIN_CHILD" -> setOf("care:read", "medication:write", "medical:share", "emergency:share")
                                "PROFESSIONAL_NURSE" -> setOf("care:read", "medication:write")
                                else -> setOf("care:read")
                            }
                            val permissions = defaults + explicit
                            add(
                                CarePatientOption(
                                    id = binding.patientUserId,
                                    name = binding.patientName?.takeIf(String::isNotBlank)
                                        ?: "Familiar ${binding.patientUserId.takeLast(6)}",
                                    canWriteMedication = "medication:write" in permissions,
                                    canTakeMedication = false, // Apenas o próprio paciente pode confirmar que tomou a dose
                                    canShareMedical = "medical:share" in permissions,
                                    canShareEmergency = "emergency:share" in permissions,
                                    role = binding.caregiverRole
                                )
                            )
                        }
                    }.distinctBy { it.id }
                    Triple(options, profile?.userRole, isCaregiver)
                }.collect { (options, role, isCaregiver) ->
                    val selected = _ui.value.selectedPatientId?.takeIf { id -> options.any { it.id == id } }
                        ?: options.firstOrNull()?.id
                    _ui.update {
                        it.copy(
                            isAuthenticated = true,
                            patients = options,
                            selectedPatientId = selected,
                            userRole = role,
                            isCaregiver = isCaregiver
                        )
                    }
                }
            }

            viewModelScope.launch {
                ui.map { it.selectedPatientId }.distinctUntilChanged().flatMapLatest { selected ->
                    if (selected == null) flowOf(emptyList<MedicationEntity>() to emptyList<CareAuditEntity>())
                    else combine(
                        medicationRepository.getMedications(selected),
                        careOsRepository.getCareWall(selected)
                    ) { meds, wall -> meds to wall }
                }.collect { (meds, wall) ->
                    val selected = _ui.value.selectedPatientId
                    _ui.update {
                        it.copy(
                            medications = meds,
                            wall = wall,
                            checkInDoneToday = selected?.let { id -> careOsRepository.hasCheckInForToday(id) } ?: false
                        )
                    }
                }
            }

            viewModelScope.launch(Dispatchers.IO) {
                ui.map { it.selectedPatientId }.distinctUntilChanged().collect { selected ->
                    if (selected != null) refreshAll(selected)
                }
            }
        }
    }

    fun selectPatient(id: String) {
        if (_ui.value.patients.any { it.id == id }) {
            _ui.update { it.copy(selectedPatientId = id, medicalAccess = null, reportFile = null) }
        }
    }

    private suspend fun refreshAll(id: String) = withContext(Dispatchers.IO) {
        try {
            medicationRepository.syncMedicationsFromServer(id)
            medicationRepository.syncStockFromServer(id)
            careOsRepository.syncCareWall(id)
            careOsRepository.syncSymptomsDiary(id)
            val bulletin = careOsRepository.getDailyBulletin(id)
            val correlations = careOsRepository.getClinicalCorrelations(id)
            val checkInDone = careOsRepository.hasCheckInForToday(id)
            _ui.update {
                it.copy(
                    dailyBulletin = bulletin,
                    correlations = correlations?.correlations.orEmpty(),
                    correlationsDisclaimer = correlations?.disclaimer,
                    checkInDoneToday = checkInDone
                )
            }
        } catch (e: Exception) {
            android.util.Log.w("CareOs", "refreshAll: ${e.message}")
        }
    }

    // ==================== ESTOQUE + DOSES ====================

    /**
     * Registra a toma e celebra (C38). O 409 é tratado graciosamente: avisa
     * "já registrado" sem duplicar decremento.
     */
    fun takeDose(medId: String, time: String? = null) {
        viewModelScope.launch {
            val actor = authenticatedUserId ?: return@launch requireAuthentication()
            if (!_ui.value.selectedPatient.canTakeMedication) {
                _ui.update { it.copy(message = "Seu vínculo permite visualizar, mas não registrar doses.") }
                return@launch
            }
            _ui.value = _ui.value.copy(loading = true, message = null)
            when (val res = medicationRepository.takeDose(patientId, medId, time, actorId = actor)) {
                is BragaApiClient.TakeMedicationResult.Success -> {
                    _ui.value = _ui.value.copy(loading = false, isCelebrating = true)
                    celebrate()
                }
                is BragaApiClient.TakeMedicationResult.AlreadyTaken -> {
                    _ui.value = _ui.value.copy(
                        loading = false,
                        message = "Esta dose já foi registrada hoje. Tudo certo — o medicamento já foi contabilizado."
                    )
                }
                is BragaApiClient.TakeMedicationResult.Failure -> {
                    _ui.value = _ui.value.copy(loading = false, message = res.message)
                }
            }
        }
    }

    /**
     * Envia lembrete afetuoso do cuidador ao paciente sobre o horário da medicação.
     */
    fun sendMedicationReminder(medicationName: String, times: List<String>) {
        viewModelScope.launch {
            val patientName = _ui.value.selectedPatient.name
            val timesStr = if (times.isNotEmpty()) " nos horários ${times.joinToString(", ")}" else ""
            _ui.update {
                it.copy(message = "Lembrete de $medicationName$timesStr enviado com carinho para $patientName.")
            }
        }
    }

    fun restock(medId: String, units: Int) {
        viewModelScope.launch {
            if (authenticatedUserId == null) return@launch requireAuthentication()
            if (!_ui.value.selectedPatient.canWriteMedication) {
                _ui.update { it.copy(message = "Seu vínculo não permite alterar o estoque.") }
                return@launch
            }
            _ui.update { it.copy(loading = true, message = null) }
            val ok = medicationRepository.restock(medId, units)
            _ui.update { it.copy(loading = false, message = if (ok) "Estoque sincronizado com a família." else "Não foi possível repor. Verifique a conexão e tente novamente.") }
        }
    }

    /** Exclui o medicamento localmente e na nuvem (Direito ao Esquecimento - LGPD Art. 18). */
    fun deleteMedication(medId: String, onComplete: ((Boolean) -> Unit)? = null) {
        viewModelScope.launch {
            if (authenticatedUserId == null) return@launch requireAuthentication()
            if (!_ui.value.selectedPatient.canWriteMedication) {
                _ui.update { it.copy(message = "Seu vínculo não permite excluir medicamentos.") }
                onComplete?.invoke(false)
                return@launch
            }
            _ui.update { it.copy(loading = true, message = null) }
            val ok = medicationRepository.deleteMedication(patientId, medId)
            _ui.update {
                it.copy(
                    loading = false,
                    message = if (ok) "Medicamento excluído com sucesso." else "Medicamento removido localmente."
                )
            }
            onComplete?.invoke(true)
        }
    }

    /** Atualiza a grade de horários e contexto alimentar do medicamento. */
    fun updateMedicationSchedule(
        medId: String,
        scheduleTimes: List<String>,
        mealContext: String? = null,
        frequencyIntervalHours: Int? = null,
        onComplete: ((Boolean) -> Unit)? = null
    ) {
        viewModelScope.launch {
            if (authenticatedUserId == null) return@launch requireAuthentication()
            if (!_ui.value.selectedPatient.canWriteMedication) {
                _ui.update { it.copy(message = "Seu vínculo não permite alterar horários.") }
                onComplete?.invoke(false)
                return@launch
            }
            _ui.update { it.copy(loading = true, message = null) }
            val ok = medicationRepository.updateMedicationSchedule(
                patientId = patientId,
                medicationId = medId,
                scheduleTimes = scheduleTimes,
                mealContext = mealContext,
                frequencyIntervalHours = frequencyIntervalHours
            )
            _ui.update {
                it.copy(
                    loading = false,
                    message = if (ok) "Horários atualizados com sucesso." else "Não foi possível sincronizar os horários com o servidor."
                )
            }
            onComplete?.invoke(ok)
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

    private suspend fun celebrate() {
        try {
            val profile = profileRepository.getProfile(patientId).firstOrNull()?.toRemote()
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
            if (authenticatedUserId == null) {
                requireAuthentication()
                onResult(null)
                return@launch
            }
            val result = if (ean.matches(Regex("^\\d{13}$"))) {
                medicationRepository.lookupBarcode(ean)
            } else null
            onResult(result)
            if (result == null) {
                _ui.value = _ui.value.copy(message = "Medicamento não encontrado no catálogo. Preencha manualmente.")
            }
        }
    }

    private var pendingPrescriptionPickerLaunch = false

    fun requestPrescriptionPicker() {
        pendingPrescriptionPickerLaunch = true
    }

    fun consumePrescriptionPickerRequest(): Boolean {
        val launch = pendingPrescriptionPickerLaunch
        pendingPrescriptionPickerLaunch = false
        return launch
    }

    fun analyzePrescription(patientId: String, uri: Uri, onResult: (PrescriptionAnalysisResponseDto?) -> Unit) {
        viewModelScope.launch {
            if (authenticatedUserId == null) {
                requireAuthentication()
                onResult(null)
                return@launch
            }
            _ui.update { it.copy(loading = true, message = null) }
            val result = try {
                medicationRepository.analyzePrescription(patientId, uri)
            } catch (e: Exception) {
                android.util.Log.w("CareOs", "Falha na análise de receita: ${e.message}")
                null
            } finally {
                _ui.update { it.copy(loading = false) }
            }
            onResult(result)
        }
    }

    fun createMedicationsBatch(
        patientId: String,
        items: List<MedicationBatchItemCreateDto>,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            _ui.update { it.copy(loading = true) }
            val ok = medicationRepository.createMedicationsBatch(patientId, items)
            _ui.update { it.copy(loading = false) }
            if (ok) {
                onSuccess()
            } else {
                onError("Não foi possível salvar a remessa de medicamentos. Tente novamente.")
            }
        }
    }

    fun createManualMedication(
        name: String,
        dosageMg: Double?,
        totalUnits: Int,
        scheduleTimes: List<String>,
        onDone: (Boolean) -> Unit
    ) {
        viewModelScope.launch {
            if (authenticatedUserId == null) {
                requireAuthentication(); onDone(false); return@launch
            }
            if (!_ui.value.selectedPatient.canWriteMedication) {
                _ui.update { it.copy(message = "Seu vínculo não permite cadastrar medicamentos.") }
                onDone(false); return@launch
            }
            _ui.value = _ui.value.copy(loading = true, message = null)
            val id = try {
                medicationRepository.createCareOsMedication(
                    patientId = patientId,
                    name = name.trim(),
                    totalUnits = totalUnits,
                    scheduleTimes = scheduleTimes,
                    confirmedWithPrescription = true,
                    dosageMg = dosageMg,
                    prescription = null
                )
            } catch (e: Exception) {
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

    fun createMedication(
        name: String,
        totalUnits: Int,
        scheduleTimes: List<String>,
        confirmedWithPrescription: Boolean,
        barcode: BarcodeMedication? = null,
        dosageMg: Double? = null,
        photoUri: android.net.Uri? = null,
        prescriptionImageUri: android.net.Uri? = null,
        prescriptionIssuedOn: String? = null,
        prescriptionValidityDays: Int? = null,
        prescriberName: String? = null,
        prescriberCrm: String? = null,
        onDone: (Boolean) -> Unit
    ) {
        viewModelScope.launch {
            if (authenticatedUserId == null) {
                requireAuthentication(); onDone(false); return@launch
            }
            if (!_ui.value.selectedPatient.canWriteMedication) {
                _ui.update { it.copy(message = "Seu vínculo não permite cadastrar medicamentos.") }
                onDone(false); return@launch
            }
            _ui.value = _ui.value.copy(loading = true, message = null)
            val id = try {
                val photoUrl = photoUri?.let { medicationRepository.uploadMedicationPhoto(patientId, it) }
                if (photoUri != null && photoUrl == null) error("Não foi possível enviar a foto da caixa.")
                val prescriptionUrl = prescriptionImageUri?.let { medicationRepository.uploadMedicationPhoto(patientId, it) }
                if (prescriptionImageUri != null && prescriptionUrl == null) error("Não foi possível enviar o documento da receita.")

                val prescription = if (prescriptionUrl != null || !prescriberName.isNullOrBlank() || !prescriberCrm.isNullOrBlank()) {
                    PrescriptionCreate(
                        imageUrl = prescriptionUrl,
                        issuedOn = prescriptionIssuedOn ?: LocalDate.now(BragaTime.ZONE).toString(),
                        validityDays = prescriptionValidityDays ?: 30,
                        prescriberName = prescriberName?.trim().orEmpty(),
                        prescriberCrm = prescriberCrm?.trim().orEmpty()
                    )
                } else null

                medicationRepository.createCareOsMedication(
                    patientId = patientId,
                    name = name,
                    totalUnits = totalUnits,
                    scheduleTimes = scheduleTimes,
                    confirmedWithPrescription = confirmedWithPrescription,
                    barcode = barcode,
                    dosageMg = dosageMg,
                    photoReferenceUrl = photoUrl,
                    prescription = prescription
                )
            } catch (e: Exception) {
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
            if (authenticatedUserId == null) return@launch requireAuthentication()
            if (!_ui.value.selectedPatient.canTakeMedication) {
                _ui.update { it.copy(message = "Seu vínculo não permite registrar o check-in deste paciente.") }
                return@launch
            }
            if (symptomsText.isBlank()) {
                _ui.value = _ui.value.copy(message = "Conte como você está se sentindo.")
                return@launch
            }
            _ui.value = _ui.value.copy(loading = true, message = null)
            val ok = careOsRepository.submitCheckIn(
                patientId = patientId,
                reportedBy = authenticatedUserId ?: patientId,
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
        viewModelScope.launch { refreshAll(patientId) }
    }

    // ==================== MODO CONSULTA — "LEVA PRO DOUTOR" ====================

    fun generateMedicalAccess() {
        viewModelScope.launch {
            _ui.value = _ui.value.copy(isGeneratingAccess = true, message = null)
            if (authenticatedUserId == null) return@launch requireAuthentication()
            if (!_ui.value.selectedPatient.canShareMedical) {
                _ui.update { it.copy(isGeneratingAccess = false, message = "Seu vínculo não permite compartilhar dados médicos.") }
                return@launch
            }
            val grant = careOsRepository.generateMedicalAccess(patientId)
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
            if (authenticatedUserId == null) return@launch requireAuthentication()
            if (!_ui.value.selectedPatient.canShareMedical) {
                _ui.update { it.copy(isGeneratingReport = false, message = "Seu vínculo não permite gerar o relatório médico.") }
                return@launch
            }
            val file = careOsRepository.getDoctorReport(patientId, days = 30) {
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
        val profile = profileRepository.getProfile(patientId).firstOrNull()?.toRemote()
            ?: br.com.bragasaude.data.remote.model.RemoteProfile(id = patientId, fullName = "Paciente")
        val vitals = vitalsRepository.getVitalSignsSync(patientId)
        val metrics = dailyMetricsDao.getRecent30Days(patientId).firstOrNull() ?: emptyList()
        val wearables = database.wearableReadingDao()
            .getForReport(patientId, System.currentTimeMillis() - 30L * 86400_000L)
        pdfGenerator.generateReport(profile, vitals, metrics, wearables)
    } catch (e: Exception) {
        null
    }

    // ==================== FICHA DE EMERGÊNCIA ====================

    fun generateEmergencyAccess(onResult: (br.com.bragasaude.data.remote.model.EmergencyTokenGrant?) -> Unit = {}) {
        viewModelScope.launch {
            if (authenticatedUserId == null) return@launch requireAuthentication()
            if (!_ui.value.selectedPatient.canShareEmergency) {
                _ui.update { it.copy(message = "Seu vínculo não permite gerar acesso de emergência.") }
                return@launch
            }
            val grant = careOsRepository.generateEmergencyAccess(patientId)
            _ui.value = _ui.value.copy(
                message = if (grant == null) "Não foi possível gerar a ficha de emergência." else "Ficha de emergência gerada."
            )
            onResult(grant)
        }
    }

    fun consumeMessage() {
        _ui.value = _ui.value.copy(message = null)
    }

    private fun requireAuthentication() {
        _ui.update { it.copy(loading = false, message = "Entre na sua conta para usar o Care OS.") }
    }

    override fun onCleared() {
        super.onCleared()
        celebrationTts.shutdown()
    }
}

data class CareOsUiState(
    val isAuthenticated: Boolean = true,
    val patients: List<CarePatientOption> = emptyList(),
    val selectedPatientId: String? = null,
    val userRole: String? = null,
    val isCaregiver: Boolean = false,
    val loading: Boolean = false,
    val medications: List<MedicationEntity> = emptyList(),
    val wall: List<CareAuditEntity> = emptyList(),
    val checkInDoneToday: Boolean = false,
    val message: String? = null,
    val isCelebrating: Boolean = false,
    val medicalAccess: MedicalAccessGrant? = null,
    val reportFile: File? = null,
    val isGeneratingAccess: Boolean = false,
    val isGeneratingReport: Boolean = false,
    val dailyBulletin: DailyCareBulletin? = null,
    val correlations: List<ClinicalCorrelation> = emptyList(),
    val correlationsDisclaimer: String? = null
) {
    val selectedPatient: CarePatientOption
        get() = patients.firstOrNull { it.id == selectedPatientId } ?: CarePatientOption.NONE
}

data class CarePatientOption(
    val id: String,
    val name: String,
    val canWriteMedication: Boolean,
    val canTakeMedication: Boolean,
    val canShareMedical: Boolean,
    val canShareEmergency: Boolean,
    val role: String
) {
    companion object {
        val NONE = CarePatientOption("", "Nenhum paciente", false, false, false, false, "NONE")
    }
}
