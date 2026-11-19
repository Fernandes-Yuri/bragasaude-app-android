package br.com.bragasaude.ui.b2b

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.bragasaude.data.local.BragaDatabase
import br.com.bragasaude.data.remote.api.BragaApiClient
import br.com.bragasaude.data.remote.b2b.ActiveContextStore
import br.com.bragasaude.data.remote.b2b.B2BContextRepository
import br.com.bragasaude.domain.b2b.AppContextMode
import br.com.bragasaude.domain.b2b.B2BContextDescriptor
import br.com.bragasaude.domain.b2b.ParticipantRoutineSummaryDto
import br.com.bragasaude.domain.b2b.PartnerParticipantItemDto
import br.com.bragasaude.domain.b2b.PartnerPreConsultationPdfGenerator
import br.com.bragasaude.domain.b2b.PartnerPreConsultationPdfInput
import br.com.bragasaude.domain.b2b.PartnerProgramDto
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class PartnerWorkspaceViewModel @Inject constructor(
    private val auth: FirebaseAuth,
    private val database: BragaDatabase,
    private val apiClient: BragaApiClient,
    private val activeContextStore: ActiveContextStore,
    private val b2bContextRepository: B2BContextRepository,
    private val partnerPdfGenerator: PartnerPreConsultationPdfGenerator
) : ViewModel() {

    val activeContext: StateFlow<B2BContextDescriptor> = activeContextStore.activeContext
    val availableContexts: StateFlow<List<B2BContextDescriptor>> = activeContextStore.availableContexts

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore.asStateFlow()

    private val _programInfo = MutableStateFlow<PartnerProgramDto?>(null)
    val programInfo: StateFlow<PartnerProgramDto?> = _programInfo.asStateFlow()

    private val _participants = MutableStateFlow<List<PartnerParticipantItemDto>>(emptyList())
    val participants: StateFlow<List<PartnerParticipantItemDto>> = _participants.asStateFlow()

    private val _nextCursor = MutableStateFlow<String?>(null)
    private val _hasMore = MutableStateFlow(false)
    val hasMore: StateFlow<Boolean> = _hasMore.asStateFlow()

    private val _selectedSummary = MutableStateFlow<ParticipantRoutineSummaryDto?>(null)
    val selectedSummary: StateFlow<ParticipantRoutineSummaryDto?> = _selectedSummary.asStateFlow()

    private val _isLoadingSummary = MutableStateFlow(false)
    val isLoadingSummary: StateFlow<Boolean> = _isLoadingSummary.asStateFlow()

    private val _selectedPeriodDays = MutableStateFlow(30)
    val selectedPeriodDays: StateFlow<Int> = _selectedPeriodDays.asStateFlow()

    private val _isGeneratingPdf = MutableStateFlow(false)
    val isGeneratingPdf: StateFlow<Boolean> = _isGeneratingPdf.asStateFlow()

    private val _generatedPdf = MutableStateFlow<File?>(null)
    val generatedPdf: StateFlow<File?> = _generatedPdf.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    fun partnerApiClient(): BragaApiClient = apiClient

    fun consumeStatusMessage() {
        _statusMessage.value = null
    }

    fun clearGeneratedPdf() {
        _generatedPdf.value = null
    }

    fun clearSelectedSummary() {
        _selectedSummary.value = null
    }

    fun refreshWorkspace() {
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            _isLoading.value = true
            b2bContextRepository.hydrateFromCache(uid)
            val contexts = b2bContextRepository.syncContexts(uid)
            val current = activeContextStore.activeContext.value
            if (!current.isPartnerContext) {
                val firstPartner = contexts.firstOrNull {
                    it.parsedMode == AppContextMode.PARTNER_PARTICIPANT
                } ?: contexts.firstOrNull {
                    it.parsedMode == AppContextMode.PARTNER_OWNER && it.approvalStatus == "APPROVED"
                } ?: contexts.firstOrNull { it.isPartnerContext }
                if (firstPartner != null) {
                    b2bContextRepository.selectContext(uid, firstPartner)
                }
            }
            reloadModeData()
            _isLoading.value = false
        }
    }

    fun selectContext(target: B2BContextDescriptor) {
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            _isLoading.value = true
            _selectedSummary.value = null
            val selected = b2bContextRepository.selectContext(uid, target)
            if (selected == null) {
                _statusMessage.value = "Não foi possível alternar de contexto agora."
            } else {
                reloadModeData()
            }
            _isLoading.value = false
        }
    }

    private suspend fun reloadModeData() {
        val active = activeContextStore.activeContext.value
        if (active.parsedMode == AppContextMode.PARTNER_OWNER) {
            _programInfo.value = apiClient.getMyPartnerProgram()
            if (active.approvalStatus == "APPROVED" || _programInfo.value?.approvalStatus == "APPROVED") {
                val page = apiClient.getMyPartnerParticipants(cursor = null, limit = 20)
                if (page != null) {
                    page.program?.let { _programInfo.value = it }
                    _participants.value = page.participants
                    _nextCursor.value = page.nextCursor
                    _hasMore.value = page.hasMore
                }
            } else {
                _participants.value = emptyList()
                _hasMore.value = false
            }
        } else {
            _participants.value = emptyList()
            _nextCursor.value = null
            _hasMore.value = false
        }
    }

    fun loadMoreParticipants() {
        if (_isLoadingMore.value || !_hasMore.value) return
        val cursor = _nextCursor.value ?: return
        viewModelScope.launch {
            _isLoadingMore.value = true
            val page = apiClient.getMyPartnerParticipants(cursor = cursor, limit = 20)
            if (page != null) {
                _participants.value = (_participants.value + page.participants)
                    .distinctBy { it.patientUserId }
                _nextCursor.value = page.nextCursor
                _hasMore.value = page.hasMore
            }
            _isLoadingMore.value = false
        }
    }

    fun openParticipantSummary(patientId: String, days: Int = _selectedPeriodDays.value) {
        val clamped = days.coerceIn(1, 90)
        _selectedPeriodDays.value = clamped
        viewModelScope.launch {
            _isLoadingSummary.value = true
            val summary = apiClient.getPartnerParticipantSummary(patientId, clamped)
            _isLoadingSummary.value = false
            if (summary != null) {
                _selectedSummary.value = summary
            } else {
                _statusMessage.value = "Não foi possível carregar os registros autorizados deste participante."
            }
        }
    }

    fun generatePreConsultationPdf(periodDays: Int, selectedCategories: Set<String>) {
        if (_isGeneratingPdf.value) return
        val uid = auth.currentUser?.uid ?: return
        val ctx = activeContextStore.activeContext.value
        if (selectedCategories.isEmpty()) {
            _statusMessage.value = "Selecione ao menos uma categoria de rotina antes de gerar o PDF."
            return
        }
        viewModelScope.launch {
            _isGeneratingPdf.value = true
            val file = withContext(Dispatchers.IO) {
                val clamped = periodDays.coerceIn(1, 90)
                val today = LocalDate.now()
                val startIso = today.minusDays((clamped - 1).toLong()).toString()
                val endIso = today.toString()

                val profile = database.profileDao().getProfile(uid).firstOrNull()
                val dailyMetrics = database.dailyMetricsDao()
                    .getDailyMetricsByDateRange(uid, startIso, endIso)
                val vitalSigns = database.vitalSignDao().getAllOneShot(uid)
                val medications = database.medicationDao().getAllSync(uid)
                val medLogs = database.medicationLogDao().getAllLogs(uid).firstOrNull() ?: emptyList()

                partnerPdfGenerator.generatePdf(
                    PartnerPreConsultationPdfInput(
                        programName = ctx.programName ?: "Braga Saúde Parceiro",
                        companyName = ctx.companyName ?: "Programa Parceiro",
                        primaryColorHex = ctx.primaryColor,
                        participantName = profile?.fullName?.takeIf { it.isNotBlank() } ?: "Participante",
                        periodDays = clamped,
                        selectedCategories = selectedCategories,
                        dailyMetrics = dailyMetrics,
                        vitalSigns = vitalSigns,
                        medications = medications,
                        medicationLogs = medLogs
                    )
                )
            }
            _isGeneratingPdf.value = false
            if (file != null) {
                _generatedPdf.value = file
                _statusMessage.value = "Resumo em PDF pronto para revisão."
            } else {
                _statusMessage.value = "Não foi possível gerar o resumo em PDF agora."
            }
        }
    }
}
