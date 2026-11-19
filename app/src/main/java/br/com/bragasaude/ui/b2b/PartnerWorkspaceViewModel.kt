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
import br.com.bragasaude.domain.b2b.ProgramExperienceConfigDto
import br.com.bragasaude.domain.b2b.TemplateCatalogItemDto
import br.com.bragasaude.domain.b2b.UpdateExperienceRequest
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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

    private val _templates = MutableStateFlow<List<TemplateCatalogItemDto>>(emptyList())
    val templates: StateFlow<List<TemplateCatalogItemDto>> = _templates.asStateFlow()

    private val _isSavingExperience = MutableStateFlow(false)
    val isSavingExperience: StateFlow<Boolean> = _isSavingExperience.asStateFlow()

    private val _participants = MutableStateFlow<List<PartnerParticipantItemDto>>(emptyList())
    val participants: StateFlow<List<PartnerParticipantItemDto>> = _participants.asStateFlow()

    private val _walletQuery = MutableStateFlow("")
    val walletQuery: StateFlow<String> = _walletQuery.asStateFlow()

    private val _walletCategoryFilter = MutableStateFlow<String?>(null)
    val walletCategoryFilter: StateFlow<String?> = _walletCategoryFilter.asStateFlow()

    private val _walletAttentionOnly = MutableStateFlow(false)
    val walletAttentionOnly: StateFlow<Boolean> = _walletAttentionOnly.asStateFlow()

    private val _isFilteringParticipants = MutableStateFlow(false)
    val isFilteringParticipants: StateFlow<Boolean> = _isFilteringParticipants.asStateFlow()

    private var walletFilterJob: Job? = null

    private val _nextCursor = MutableStateFlow<String?>(null)
    private val _hasMore = MutableStateFlow(false)
    val hasMore: StateFlow<Boolean> = _hasMore.asStateFlow()

    private val _selectedParticipantItem = MutableStateFlow<PartnerParticipantItemDto?>(null)
    val selectedParticipantItem: StateFlow<PartnerParticipantItemDto?> = _selectedParticipantItem.asStateFlow()

    private val _selectedSummary = MutableStateFlow<ParticipantRoutineSummaryDto?>(null)
    val selectedSummary: StateFlow<ParticipantRoutineSummaryDto?> = _selectedSummary.asStateFlow()

    private val _isLoadingSummary = MutableStateFlow(false)
    val isLoadingSummary: StateFlow<Boolean> = _isLoadingSummary.asStateFlow()

    private val _isRemovingParticipant = MutableStateFlow(false)
    val isRemovingParticipant: StateFlow<Boolean> = _isRemovingParticipant.asStateFlow()

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
        _selectedParticipantItem.value = null
    }

    fun updateWalletQuery(query: String) {
        val clipped = query.take(80)
        if (_walletQuery.value == clipped) return
        _walletQuery.value = clipped
        scheduleWalletReload(debounceMs = 280L)
    }

    fun selectWalletCategoryFilter(category: String?) {
        val normalized = category?.trim()?.takeIf { it.isNotBlank() }
        val next = if (_walletCategoryFilter.value == normalized) null else normalized
        _walletCategoryFilter.value = next
        scheduleWalletReload(debounceMs = 0L)
    }

    fun toggleWalletAttentionOnly() {
        _walletAttentionOnly.value = !_walletAttentionOnly.value
        scheduleWalletReload(debounceMs = 0L)
    }

    fun clearWalletFilters() {
        if (_walletQuery.value.isEmpty() && _walletCategoryFilter.value == null && !_walletAttentionOnly.value) {
            return
        }
        _walletQuery.value = ""
        _walletCategoryFilter.value = null
        _walletAttentionOnly.value = false
        scheduleWalletReload(debounceMs = 0L)
    }

    private fun scheduleWalletReload(debounceMs: Long) {
        walletFilterJob?.cancel()
        walletFilterJob = viewModelScope.launch {
            if (debounceMs > 0L) delay(debounceMs)
            _isFilteringParticipants.value = true
            val page = fetchParticipantsPage(cursor = null)
            if (page != null) {
                page.program?.let { _programInfo.value = it }
                _participants.value = page.participants
                _nextCursor.value = page.nextCursor
                _hasMore.value = page.hasMore
            }
            _isFilteringParticipants.value = false
        }
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
            walletFilterJob?.cancel()
            _walletQuery.value = ""
            _walletCategoryFilter.value = null
            _walletAttentionOnly.value = false
            _selectedSummary.value = null
            _selectedParticipantItem.value = null
            val selected = b2bContextRepository.selectContext(uid, target)
            if (selected == null) {
                _statusMessage.value = "Não foi possível alternar de contexto agora."
            } else {
                reloadModeData()
            }
            _isLoading.value = false
        }
    }

    private suspend fun fetchParticipantsPage(cursor: String?) = apiClient.getMyPartnerParticipants(
        cursor = cursor,
        limit = 20,
        query = _walletQuery.value,
        category = _walletCategoryFilter.value,
        attentionOnly = _walletAttentionOnly.value
    )

    private suspend fun reloadModeData() {
        val active = activeContextStore.activeContext.value
        if (active.parsedMode == AppContextMode.PARTNER_OWNER) {
            _programInfo.value = apiClient.getMyPartnerProgram()
            if (_templates.value.isEmpty()) {
                _templates.value = apiClient.getProgramTemplates()
            }
            if (active.approvalStatus == "APPROVED" || _programInfo.value?.approvalStatus == "APPROVED") {
                val page = fetchParticipantsPage(cursor = null)
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
            val page = fetchParticipantsPage(cursor = cursor)
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
        _participants.value.firstOrNull { it.patientUserId == patientId }?.let { matched ->
            _selectedParticipantItem.value = matched
        }
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

    fun removeParticipant(patientId: String) {
        val cleanId = patientId.trim()
        if (cleanId.isEmpty() || _isRemovingParticipant.value) return
        viewModelScope.launch {
            _isRemovingParticipant.value = true
            val result = apiClient.removePartnerParticipant(cleanId)
            _isRemovingParticipant.value = false
            if (result.isSuccess) {
                _participants.value = _participants.value.filterNot { it.patientUserId == cleanId }
                _programInfo.value = result.program ?: _programInfo.value?.let { current ->
                    current.copy(activeParticipants = (current.activeParticipants - 1).coerceAtLeast(0))
                }
                if (_selectedSummary.value?.patientUserId == cleanId) {
                    _selectedSummary.value = null
                    _selectedParticipantItem.value = null
                }
                _statusMessage.value = "Vínculo encerrado. A vaga foi liberada na sua franquia."
            } else {
                _statusMessage.value = result.errorDetail
                    ?: "Não foi possível encerrar o vínculo deste participante agora."
            }
        }
    }

    fun saveExperienceConfig(
        templateKey: String,
        applyTemplateDefaults: Boolean,
        experienceConfig: ProgramExperienceConfigDto?
    ) {
        if (_isSavingExperience.value) return
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            _isSavingExperience.value = true
            val res = apiClient.updateProgramExperience(
                UpdateExperienceRequest(
                    templateKey = templateKey,
                    applyTemplateDefaults = applyTemplateDefaults,
                    experienceConfig = experienceConfig
                )
            )
            _isSavingExperience.value = false
            if (res.isSuccess && res.program != null) {
                val prev = _programInfo.value
                _programInfo.value = if (prev != null) {
                    res.program.copy(
                        responsibleName = res.program.responsibleName ?: prev.responsibleName,
                        responsibleCpfMasked = res.program.responsibleCpfMasked ?: prev.responsibleCpfMasked,
                        responsibleRole = res.program.responsibleRole ?: prev.responsibleRole,
                        responsiblePhone = res.program.responsiblePhone ?: prev.responsiblePhone,
                        councilType = res.program.councilType ?: prev.councilType,
                        councilNumber = res.program.councilNumber ?: prev.councilNumber,
                        councilUf = res.program.councilUf ?: prev.councilUf,
                        city = res.program.city ?: prev.city,
                        state = res.program.state ?: prev.state,
                        termsAcceptedAt = res.program.termsAcceptedAt ?: prev.termsAcceptedAt,
                        termsVersion = res.program.termsVersion ?: prev.termsVersion,
                        autoKybStatus = res.program.autoKybStatus ?: prev.autoKybStatus,
                        autoKybFlags = res.program.autoKybFlags.ifEmpty { prev.autoKybFlags },
                        proofDocuments = res.program.proofDocuments.ifEmpty { prev.proofDocuments }
                    )
                } else {
                    res.program
                }
                b2bContextRepository.syncContexts(uid)
                _statusMessage.value = "Estúdio atualizado! A prévia e os participantes já refletem as mudanças."
            } else {
                _statusMessage.value = res.errorDetail
                    ?: "Não foi possível aplicar as alterações no programa."
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
