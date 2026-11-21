package br.com.bragasaude.ui.b2b

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.bragasaude.data.local.BragaDatabase
import br.com.bragasaude.data.remote.api.BragaApiClient
import br.com.bragasaude.data.remote.b2b.ActiveContextStore
import br.com.bragasaude.data.remote.b2b.B2BContextRepository
import br.com.bragasaude.domain.b2b.AppAccountMode
import br.com.bragasaude.domain.b2b.AppContextMode
import br.com.bragasaude.domain.b2b.B2BContextDescriptor
import br.com.bragasaude.domain.b2b.B2BOnboardingValidator
import br.com.bragasaude.domain.b2b.CreateProgramReminderRequest
import br.com.bragasaude.domain.b2b.DirectChatThreadPageDto
import br.com.bragasaude.domain.b2b.EnrollmentAppointmentsFeedDto
import br.com.bragasaude.domain.b2b.EnrollmentRemindersFeedDto
import br.com.bragasaude.domain.b2b.ParticipantRoutineSummaryDto
import br.com.bragasaude.domain.b2b.PartnerParticipantItemDto
import br.com.bragasaude.domain.b2b.PartnerPreConsultationPdfGenerator
import br.com.bragasaude.domain.b2b.PartnerPreConsultationPdfInput
import br.com.bragasaude.domain.b2b.PartnerProgramDto
import br.com.bragasaude.domain.b2b.ProgramAppointmentDto
import br.com.bragasaude.domain.b2b.ProgramExperienceConfigDto
import br.com.bragasaude.domain.b2b.ProgramReminderDto
import br.com.bragasaude.domain.b2b.ReportProgramAbuseRequest
import br.com.bragasaude.domain.b2b.TemplateCatalogItemDto
import br.com.bragasaude.domain.b2b.UpdateExperienceRequest
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.ensureActive
import br.com.bragasaude.domain.b2b.PartnerWalletKpisDto
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

    private var workspaceJob = SupervisorJob(viewModelScope.coroutineContext[Job])
    private val workspaceScope get() = CoroutineScope(viewModelScope.coroutineContext + workspaceJob)
    private val removeResetListener = activeContextStore.addSessionResetListener(::clearSensitiveBusinessMemory)

    private val _walletKpis = MutableStateFlow<PartnerWalletKpisDto?>(null)
    val walletKpis = _walletKpis.asStateFlow()

    val activeContext: StateFlow<B2BContextDescriptor> = activeContextStore.activeContext
    val availableContexts: StateFlow<List<B2BContextDescriptor>> = activeContextStore.availableContexts
    val accountMode: StateFlow<AppAccountMode> = activeContextStore.accountMode
    val isBusinessSession: StateFlow<Boolean> = activeContextStore.isBusinessSession

    private val _isUpdatingInvitePolicy = MutableStateFlow(false)
    val isUpdatingInvitePolicy: StateFlow<Boolean> = _isUpdatingInvitePolicy.asStateFlow()

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

    private val _ownerReminders = MutableStateFlow<List<ProgramReminderDto>>(emptyList())
    val ownerReminders: StateFlow<List<ProgramReminderDto>> = _ownerReminders.asStateFlow()

    private val _isSendingReminder = MutableStateFlow(false)
    val isSendingReminder: StateFlow<Boolean> = _isSendingReminder.asStateFlow()

    private val _enrollmentRemindersFeed = MutableStateFlow<EnrollmentRemindersFeedDto?>(null)
    val enrollmentRemindersFeed: StateFlow<EnrollmentRemindersFeedDto?> = _enrollmentRemindersFeed.asStateFlow()

    private val _acknowledgingReminderId = MutableStateFlow<String?>(null)
    val acknowledgingReminderId: StateFlow<String?> = _acknowledgingReminderId.asStateFlow()

    private val _isUpdatingMute = MutableStateFlow(false)
    val isUpdatingMute: StateFlow<Boolean> = _isUpdatingMute.asStateFlow()

    private val _isReportingProgram = MutableStateFlow(false)
    val isReportingProgram: StateFlow<Boolean> = _isReportingProgram.asStateFlow()

    private val _ownerAppointments = MutableStateFlow<List<ProgramAppointmentDto>>(emptyList())
    val ownerAppointments: StateFlow<List<ProgramAppointmentDto>> = _ownerAppointments.asStateFlow()

    private val _enrollmentAppointmentsFeed = MutableStateFlow<EnrollmentAppointmentsFeedDto?>(null)
    val enrollmentAppointmentsFeed: StateFlow<EnrollmentAppointmentsFeedDto?> = _enrollmentAppointmentsFeed.asStateFlow()

    private val _isSubmittingAppointment = MutableStateFlow(false)
    val isSubmittingAppointment: StateFlow<Boolean> = _isSubmittingAppointment.asStateFlow()

    private val _updatingAppointmentId = MutableStateFlow<String?>(null)
    val updatingAppointmentId: StateFlow<String?> = _updatingAppointmentId.asStateFlow()

    private val _activeDirectChatPage = MutableStateFlow<DirectChatThreadPageDto?>(null)
    val activeDirectChatPage: StateFlow<DirectChatThreadPageDto?> = _activeDirectChatPage.asStateFlow()

    private val _directChatIsOwnerView = MutableStateFlow(false)
    val directChatIsOwnerView: StateFlow<Boolean> = _directChatIsOwnerView.asStateFlow()

    private val _directChatCounterpartTitle = MutableStateFlow("")
    val directChatCounterpartTitle: StateFlow<String> = _directChatCounterpartTitle.asStateFlow()

    private val _directChatTargetPatientId = MutableStateFlow<String?>(null)

    private val _isLoadingDirectChat = MutableStateFlow(false)
    val isLoadingDirectChat: StateFlow<Boolean> = _isLoadingDirectChat.asStateFlow()

    private val _isSendingDirectMessage = MutableStateFlow(false)
    val isSendingDirectMessage: StateFlow<Boolean> = _isSendingDirectMessage.asStateFlow()

    private val _isUpdatingHandoff = MutableStateFlow(false)
    val isUpdatingHandoff: StateFlow<Boolean> = _isUpdatingHandoff.asStateFlow()

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

    private var summaryJob: Job? = null

    fun collapseClinicalDetails() {
        summaryJob?.cancel()
        _isLoadingSummary.value = false
        _selectedSummary.value = _selectedSummary.value?.copy(detailCategory = null,
            medicationsSection = null, authorizedRecordsSection = null)
    }

    fun clearSelectedSummary() {
        summaryJob?.cancel()
        _isLoadingSummary.value = false
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
        walletFilterJob = workspaceScope.launch {
            if (debounceMs > 0L) delay(debounceMs)
            _isFilteringParticipants.value = true
            val page = fetchParticipantsPage(cursor = null)
            if (page != null) {
                page.program?.let { _programInfo.value = it }
                _walletKpis.value = page.kpis
                _participants.value = page.participants
                _nextCursor.value = page.nextCursor
                _hasMore.value = page.hasMore
            } else {
                _walletKpis.value = null
                _participants.value = emptyList()
                clearSelectedSummary()
                _nextCursor.value = null
                _hasMore.value = false
                _statusMessage.value = "Não foi possível consultar a carteira."
            }
            _isFilteringParticipants.value = false
        }
    }

    fun refreshWorkspace() {
        val uid = auth.currentUser?.uid ?: return
        clearSensitiveBusinessMemory()
        workspaceScope.launch {
            _isLoading.value = true
            b2bContextRepository.hydrateFromCache(uid)
            val contexts = b2bContextRepository.syncContexts(uid)
            coroutineContext.ensureActive()
            val current = activeContextStore.activeContext.value
            val businessOnlyOrActive = activeContextStore.isBusinessSession.value ||
                activeContextStore.accountMode.value == AppAccountMode.BUSINESS_ONLY
            if (businessOnlyOrActive) {
                val ownerCtx = contexts.firstOrNull {
                    it.parsedMode == AppContextMode.PARTNER_OWNER && it.approvalStatus == "APPROVED"
                } ?: contexts.firstOrNull { it.parsedMode == AppContextMode.PARTNER_OWNER }
                if (ownerCtx != null && current.cacheKey() != ownerCtx.cacheKey()) {
                    b2bContextRepository.selectContext(uid, ownerCtx)
                }
            } else if (!current.isPartnerContext) {
                val firstPartner = contexts.firstOrNull {
                    it.parsedMode == AppContextMode.PARTNER_PARTICIPANT
                }
                if (firstPartner != null) {
                    b2bContextRepository.selectContext(uid, firstPartner)
                }
            }
            reloadModeData()
            _isLoading.value = false
        }
    }

    fun clearSensitiveBusinessMemory() {
        workspaceJob.cancel()
        workspaceJob = SupervisorJob(viewModelScope.coroutineContext[Job])
        walletFilterJob?.cancel()
        walletFilterJob = null
        _walletKpis.value = null
        _isUpdatingInvitePolicy.value = false
        _isLoading.value = false
        _isLoadingMore.value = false
        _programInfo.value = null
        _templates.value = emptyList()
        _isSavingExperience.value = false
        _participants.value = emptyList()
        _walletQuery.value = ""
        _walletCategoryFilter.value = null
        _walletAttentionOnly.value = false
        _isFilteringParticipants.value = false
        _nextCursor.value = null
        _hasMore.value = false
        _selectedParticipantItem.value = null
        _selectedSummary.value = null
        _isLoadingSummary.value = false
        _isRemovingParticipant.value = false
        _selectedPeriodDays.value = 30
        _ownerReminders.value = emptyList()
        _isSendingReminder.value = false
        _enrollmentRemindersFeed.value = null
        _acknowledgingReminderId.value = null
        _isUpdatingMute.value = false
        _isReportingProgram.value = false
        _ownerAppointments.value = emptyList()
        _enrollmentAppointmentsFeed.value = null
        _isSubmittingAppointment.value = false
        _updatingAppointmentId.value = null
        _activeDirectChatPage.value = null
        _directChatIsOwnerView.value = false
        _directChatCounterpartTitle.value = ""
        _directChatTargetPatientId.value = null
        _isLoadingDirectChat.value = false
        _isSendingDirectMessage.value = false
        _isUpdatingHandoff.value = false
        _isGeneratingPdf.value = false
        _generatedPdf.value = null
        _statusMessage.value = null
    }

    override fun onCleared() {
        removeResetListener()
        clearSensitiveBusinessMemory()
        workspaceJob.cancel()
        super.onCleared()
    }

    fun togglePartnerInvitePaused() {
        val currentProgram = _programInfo.value ?: return
        if (_isUpdatingInvitePolicy.value) return
        workspaceScope.launch {
            _isUpdatingInvitePolicy.value = true
            val updated = apiClient.setPartnerInvitePaused(!currentProgram.invitePaused)
            _isUpdatingInvitePolicy.value = false
            if (updated != null) {
                _programInfo.value = updated
                auth.currentUser?.uid?.let { b2bContextRepository.syncContexts(it) }
                _statusMessage.value = if (updated.invitePaused) {
                    "Novas entradas pelo código foram pausadas."
                } else {
                    "Entrada de novos participantes reativada."
                }
            } else {
                _statusMessage.value = "Não foi possível atualizar a entrada pelo código agora."
            }
        }
    }

    fun regenerateInviteCode() {
        if (_isUpdatingInvitePolicy.value) return
        _programInfo.value ?: return
        workspaceScope.launch {
            _isUpdatingInvitePolicy.value = true
            val updated = apiClient.regeneratePartnerInviteCode()
            _isUpdatingInvitePolicy.value = false
            if (updated != null) {
                _programInfo.value = updated
                auth.currentUser?.uid?.let { b2bContextRepository.syncContexts(it) }
                _statusMessage.value = "Novo código gerado. O código anterior deixou de funcionar."
            } else {
                _statusMessage.value = "Não foi possível gerar um novo código agora."
            }
        }
    }

    fun selectContext(target: B2BContextDescriptor) {
        val uid = auth.currentUser?.uid ?: return
        if (target.parsedMode == AppContextMode.PARTNER_OWNER && !activeContextStore.isBusinessSession.value) return
        clearSensitiveBusinessMemory()
        workspaceScope.launch {
            _isLoading.value = true
            walletFilterJob?.cancel()
            _walletQuery.value = ""
            _walletCategoryFilter.value = null
            _walletAttentionOnly.value = false
            clearSelectedSummary()
            _activeDirectChatPage.value = null
            _directChatTargetPatientId.value = null
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
        val isOwnerWorkspace = activeContextStore.accountMode.value != AppAccountMode.PERSONAL &&
            activeContextStore.isBusinessSession.value
        if (isOwnerWorkspace) {
            _enrollmentRemindersFeed.value = null
            _enrollmentAppointmentsFeed.value = null
            _programInfo.value = apiClient.getMyPartnerProgram()
            if (_templates.value.isEmpty()) {
                _templates.value = apiClient.getProgramTemplates()
            }
            val program = _programInfo.value
            if (program?.approvalStatus == "APPROVED" || active.approvalStatus == "APPROVED") {
                val page = fetchParticipantsPage(cursor = null)
                if (page != null) {
                    page.program?.let { _programInfo.value = it }
                    _walletKpis.value = page.kpis
                    _participants.value = page.participants
                    _nextCursor.value = page.nextCursor
                    _hasMore.value = page.hasMore
                } else {
                    _walletKpis.value = null
                    _participants.value = emptyList()
                    clearSelectedSummary()
                    _nextCursor.value = null
                    _hasMore.value = false
                }
                val resolvedCfg = _programInfo.value?.experienceConfig ?: active.resolvedExperienceConfig()
                _ownerReminders.value = if (resolvedCfg.features.partnerRemindersEnabled) {
                    apiClient.getMyPartnerSentReminders(limit = 20)
                } else {
                    emptyList()
                }
                _ownerAppointments.value = if (resolvedCfg.features.appointmentsEnabled) {
                    apiClient.getOwnerProgramAppointments(limit = 50)?.appointments.orEmpty()
                } else {
                    emptyList()
                }
            } else {
                _participants.value = emptyList()
                _ownerReminders.value = emptyList()
                _ownerAppointments.value = emptyList()
                _walletKpis.value = null
                _nextCursor.value = null
                _hasMore.value = false
            }
        } else {
            _participants.value = emptyList()
            _ownerReminders.value = emptyList()
            _ownerAppointments.value = emptyList()
            _walletKpis.value = null
            _nextCursor.value = null
            _hasMore.value = false
            val pid = active.programId?.takeIf { it.isNotBlank() }
            val cfg = active.resolvedExperienceConfig()
            if (active.parsedMode == AppContextMode.PARTNER_PARTICIPANT &&
                pid != null &&
                cfg.features.partnerRemindersEnabled
            ) {
                _enrollmentRemindersFeed.value = apiClient.getEnrollmentReminders(pid, limit = 20)
            } else {
                _enrollmentRemindersFeed.value = null
            }
            if (active.parsedMode == AppContextMode.PARTNER_PARTICIPANT &&
                pid != null &&
                cfg.features.appointmentsEnabled
            ) {
                _enrollmentAppointmentsFeed.value = apiClient.getEnrollmentAppointments(pid)
            } else {
                _enrollmentAppointmentsFeed.value = null
            }
        }
    }

    fun sendPartnerReminder(
        targetPatientId: String?,
        targetCategory: String?,
        reminderType: String,
        title: String,
        body: String,
        onSuccess: () -> Unit
    ) {
        if (_isSendingReminder.value) return
        workspaceScope.launch {
            _isSendingReminder.value = true
            val result = apiClient.sendPartnerReminder(
                CreateProgramReminderRequest(
                    targetPatientId = targetPatientId,
                    targetCategory = targetCategory,
                    reminderType = reminderType,
                    title = title,
                    body = body
                )
            )
            _isSendingReminder.value = false
            if (result.isSuccess && result.reminder != null) {
                _ownerReminders.value = listOf(result.reminder) + _ownerReminders.value
                _statusMessage.value = if (targetPatientId.isNullOrBlank()) {
                    "Lembrete enviado em lote para ${B2BTextFormatters.countLabel(result.recipientCount, "participante", "participantes")}."
                } else {
                    "Lembrete enviado para o participante."
                }
                onSuccess()
            } else {
                _statusMessage.value = result.errorDetail
                    ?: "Não foi possível enviar o lembrete agora."
            }
        }
    }

    fun acknowledgeEnrollmentReminder(programId: String, reminderId: String) {
        if (_acknowledgingReminderId.value != null) return
        workspaceScope.launch {
            _acknowledgingReminderId.value = reminderId
            val ackAt = apiClient.acknowledgeEnrollmentReminder(programId, reminderId)
            _acknowledgingReminderId.value = null
            if (ackAt != null) {
                _enrollmentRemindersFeed.value = _enrollmentRemindersFeed.value?.let { feed ->
                    feed.copy(
                        reminders = feed.reminders.map { rem ->
                            if (rem.reminderId == reminderId) rem.copy(acknowledgedAt = ackAt) else rem
                        }
                    )
                }
                _statusMessage.value = "Leitura confirmada neste lembrete."
            } else {
                _statusMessage.value = "Não foi possível confirmar leitura agora."
            }
        }
    }

    fun toggleEnrollmentMuted(programId: String, muted: Boolean) {
        if (_isUpdatingMute.value) return
        workspaceScope.launch {
            _isUpdatingMute.value = true
            val updated = apiClient.setEnrollmentMuted(programId, muted)
            _isUpdatingMute.value = false
            if (updated != null) {
                _enrollmentRemindersFeed.value = _enrollmentRemindersFeed.value?.copy(muted = updated)
                _statusMessage.value = if (updated) {
                    "Alertas push deste programa foram silenciados."
                } else {
                    "Alertas push deste programa foram reativados."
                }
            } else {
                _statusMessage.value = "Não foi possível atualizar as notificações deste programa."
            }
        }
    }

    fun reportProgramAbuse(
        programId: String,
        reason: String,
        details: String?,
        muteAlso: Boolean,
        onSuccess: () -> Unit
    ) {
        if (_isReportingProgram.value) return
        workspaceScope.launch {
            _isReportingProgram.value = true
            val result = apiClient.reportPartnerProgramAbuse(
                programId = programId,
                request = ReportProgramAbuseRequest(
                    reason = reason,
                    details = details,
                    muteAlso = muteAlso
                )
            )
            _isReportingProgram.value = false
            if (result.isSuccess) {
                if (result.muted) {
                    _enrollmentRemindersFeed.value = _enrollmentRemindersFeed.value?.copy(muted = true)
                }
                _statusMessage.value = if (result.muted) {
                    "Denúncia registrada para moderação Braga e avisos silenciados."
                } else {
                    "Denúncia registrada para moderação Braga."
                }
                onSuccess()
            } else {
                _statusMessage.value = result.errorDetail
                    ?: "Não foi possível registrar sua denúncia agora."
            }
        }
    }

    fun requestEnrollmentAppointment(
        programId: String,
        modality: String,
        requestedSlotText: String,
        patientNote: String?,
        onSuccess: () -> Unit
    ) {
        if (_isSubmittingAppointment.value) return
        val validationErr = B2BOnboardingValidator.findAppointmentRequestError(requestedSlotText, patientNote)
        if (validationErr != null) {
            _statusMessage.value = validationErr
            return
        }
        workspaceScope.launch {
            _isSubmittingAppointment.value = true
            val result = apiClient.requestEnrollmentAppointment(
                programId = programId,
                modality = modality,
                requestedSlotText = requestedSlotText,
                patientNote = patientNote
            )
            _isSubmittingAppointment.value = false
            val created = result.appointment
            if (created != null) {
                val currentFeed = _enrollmentAppointmentsFeed.value
                if (currentFeed != null) {
                    val updatedList = listOf(created) + currentFeed.appointments
                    _enrollmentAppointmentsFeed.value = currentFeed.copy(
                        appointments = updatedList,
                        pendingCount = updatedList.count { it.status == "PENDING" }
                    )
                } else {
                    _enrollmentAppointmentsFeed.value = apiClient.getEnrollmentAppointments(programId)
                }
                _statusMessage.value = "Solicitação de horário enviada ao programa."
                onSuccess()
            } else {
                _statusMessage.value = result.errorDetail
                    ?: "Não foi possível enviar sua solicitação de horário."
            }
        }
    }

    fun cancelEnrollmentAppointment(programId: String, appointmentId: String) {
        if (_updatingAppointmentId.value != null) return
        workspaceScope.launch {
            _updatingAppointmentId.value = appointmentId
            val result = apiClient.cancelEnrollmentAppointment(programId, appointmentId)
            _updatingAppointmentId.value = null
            val updated = result.appointment
            if (updated != null) {
                _enrollmentAppointmentsFeed.value = _enrollmentAppointmentsFeed.value?.let { feed ->
                    val nextList = feed.appointments.map { item ->
                        if (item.appointmentId == appointmentId) updated else item
                    }
                    feed.copy(
                        appointments = nextList,
                        pendingCount = nextList.count { it.status == "PENDING" }
                    )
                }
                _statusMessage.value = "Agendamento cancelado."
            } else {
                _statusMessage.value = result.errorDetail
                    ?: "Não foi possível cancelar este agendamento."
            }
        }
    }

    fun updateOwnerAppointment(
        appointmentId: String,
        status: String,
        scheduledAtIso: String?,
        partnerNote: String?,
        allowOverlap: Boolean,
        onSuccess: () -> Unit
    ) {
        if (_updatingAppointmentId.value != null) return
        workspaceScope.launch {
            _updatingAppointmentId.value = appointmentId
            val result = apiClient.updateOwnerAppointment(
                appointmentId = appointmentId,
                status = status,
                scheduledAtIso = scheduledAtIso,
                partnerNote = partnerNote,
                allowOverlap = allowOverlap
            )
            _updatingAppointmentId.value = null
            val updated = result.appointment
            if (updated != null) {
                val refreshed = _ownerAppointments.value.map { item ->
                    if (item.appointmentId == appointmentId) updated.copy(patientName = item.patientName ?: updated.patientName) else item
                }
                _ownerAppointments.value = refreshed
                val pendingForPatient = refreshed.count {
                    it.patientUserId == updated.patientUserId && it.status == "PENDING"
                }
                _participants.value = _participants.value.map { p ->
                    if (p.patientUserId == updated.patientUserId) {
                        p.copy(pendingAppointmentsCount = pendingForPatient)
                    } else {
                        p
                    }
                }
                _statusMessage.value = "Agendamento atualizado para ${updated.parsedStatus.labelPtBr.lowercase()}."
                onSuccess()
            } else {
                _statusMessage.value = result.errorDetail
                    ?: "Não foi possível atualizar este agendamento."
            }
        }
    }

    fun loadMoreParticipants() {
        if (_isLoadingMore.value || !_hasMore.value) return
        val cursor = _nextCursor.value ?: return
        workspaceScope.launch {
            _isLoadingMore.value = true
            val page = fetchParticipantsPage(cursor = cursor)
            if (page != null) {
                _walletKpis.value = page.kpis
                _participants.value = (_participants.value + page.participants)
                    .distinctBy { it.patientUserId }
                _nextCursor.value = page.nextCursor
                _hasMore.value = page.hasMore
            }
            _isLoadingMore.value = false
        }
    }

    fun openParticipantSummary(patientId: String, days: Int = _selectedPeriodDays.value, detailCategory: String? = null) {
        val clamped = days.coerceIn(1, 90)
        val current = _selectedSummary.value
        if (detailCategory != null &&
            (current == null || current.patientUserId != patientId || detailCategory !in current.availableDetails)
        ) return
        if (current?.patientUserId == patientId) collapseClinicalDetails() else clearSelectedSummary()
        _selectedParticipantItem.value = _participants.value.firstOrNull { it.patientUserId == patientId }
        _isLoadingSummary.value = true
        summaryJob = workspaceScope.launch {
            val summary = apiClient.getPartnerParticipantSummary(patientId, clamped, detailCategory)
            _isLoadingSummary.value = false
            if (summary != null) {
                _selectedSummary.value = summary
                _selectedPeriodDays.value = summary.periodDays
            } else {
                clearSelectedSummary()
                _statusMessage.value = "Não foi possível carregar os registros autorizados deste participante."
            }
        }
    }

    fun removeParticipant(patientId: String) {
        val cleanId = patientId.trim()
        if (cleanId.isEmpty() || _isRemovingParticipant.value) return
        workspaceScope.launch {
            _isRemovingParticipant.value = true
            val result = apiClient.removePartnerParticipant(cleanId)
            _isRemovingParticipant.value = false
            if (result.isSuccess) {
                _participants.value = _participants.value.filterNot { it.patientUserId == cleanId }
                _programInfo.value = result.program ?: _programInfo.value?.let { current ->
                    current.copy(activeParticipants = (current.activeParticipants - 1).coerceAtLeast(0))
                }
                if (_selectedSummary.value?.patientUserId == cleanId) {
                    clearSelectedSummary()
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
        workspaceScope.launch {
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
        workspaceScope.launch {
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

    fun closeDirectChat() {
        _activeDirectChatPage.value = null
        _directChatTargetPatientId.value = null
        _isLoadingDirectChat.value = false
    }

    fun openEnrollmentDirectChat(programId: String, programName: String) {
        if (_isLoadingDirectChat.value) return
        workspaceScope.launch {
            _isLoadingDirectChat.value = true
            _directChatIsOwnerView.value = false
            _directChatTargetPatientId.value = null
            _directChatCounterpartTitle.value = programName.ifBlank { "Programa Parceiro" }
            val uid = auth.currentUser?.uid.orEmpty()
            val page = apiClient.getEnrollmentDirectMessages(programId, limit = 30)
            _isLoadingDirectChat.value = false
            if (page != null) {
                val mergedPage = b2bContextRepository.cacheAndMergeDirectChatPage(
                    userId = uid,
                    page = page,
                    targetPatientId = uid,
                    primaryColor = activeContext.value.primaryColor
                )
                _activeDirectChatPage.value = mergedPage
                val marked = apiClient.markEnrollmentDirectMessagesRead(programId)
                if (marked > 0) {
                    _activeDirectChatPage.value = _activeDirectChatPage.value
                }
            } else {
                val offline = b2bContextRepository.loadOfflineDirectChatPage(
                    userId = uid,
                    programId = programId,
                    programName = programName,
                    patientUserId = uid
                )
                if (offline != null) {
                    _activeDirectChatPage.value = offline
                } else {
                    _statusMessage.value = "Não foi possível abrir o chat direto agora."
                }
            }
        }
    }

    fun openOwnerDirectChat(patientId: String, patientName: String) {
        if (_isLoadingDirectChat.value) return
        workspaceScope.launch {
            _isLoadingDirectChat.value = true
            _directChatIsOwnerView.value = true
            _directChatTargetPatientId.value = patientId
            _directChatCounterpartTitle.value = patientName.ifBlank { "Participante" }
            val uid = auth.currentUser?.uid.orEmpty()
            val progId = _programInfo.value?.programId ?: activeContext.value.programId.orEmpty()
            val progColor = _programInfo.value?.primaryColor ?: activeContext.value.primaryColor
            val progName = _programInfo.value?.programName ?: activeContext.value.programName.orEmpty()
            val page = apiClient.getOwnerDirectMessages(patientId, limit = 30)
            _isLoadingDirectChat.value = false
            if (page != null) {
                val mergedPage = b2bContextRepository.cacheAndMergeDirectChatPage(
                    userId = uid,
                    page = page,
                    targetPatientId = patientId,
                    primaryColor = progColor,
                    patientName = patientName
                )
                _activeDirectChatPage.value = mergedPage
                apiClient.markOwnerDirectMessagesRead(patientId)
                _participants.value = _participants.value.map { item ->
                    if (item.patientUserId == patientId) item.copy(unreadDirectMessagesCount = 0) else item
                }
            } else {
                val offline = if (progId.isNotBlank()) {
                    b2bContextRepository.loadOfflineDirectChatPage(
                        userId = uid,
                        programId = progId,
                        programName = progName,
                        patientUserId = patientId
                    )
                } else null
                if (offline != null) {
                    _activeDirectChatPage.value = offline
                } else {
                    _statusMessage.value = "Não foi possível abrir a conversa com este participante."
                }
            }
        }
    }

    fun loadMoreDirectMessages() {
        val current = _activeDirectChatPage.value ?: return
        val cursor = current.nextCursor ?: return
        if (!current.hasMore || _isLoadingDirectChat.value) return
        workspaceScope.launch {
            _isLoadingDirectChat.value = true
            val older = if (_directChatIsOwnerView.value) {
                val pid = _directChatTargetPatientId.value
                if (pid != null) apiClient.getOwnerDirectMessages(pid, cursor = cursor, limit = 30) else null
            } else {
                apiClient.getEnrollmentDirectMessages(current.programId, cursor = cursor, limit = 30)
            }
            _isLoadingDirectChat.value = false
            if (older != null) {
                val uid = auth.currentUser?.uid.orEmpty()
                b2bContextRepository.cacheDirectMessages(
                    userId = uid,
                    messages = older.messages,
                    programName = current.programName,
                    primaryColor = activeContext.value.primaryColor,
                    patientName = _directChatCounterpartTitle.value.takeIf { _directChatIsOwnerView.value }
                )
                val merged = B2BContextRepository.mergeDirectMessages(current.messages, older.messages)
                _activeDirectChatPage.value = current.copy(
                    messages = merged,
                    nextCursor = older.nextCursor,
                    hasMore = older.hasMore
                )
            }
        }
    }

    fun sendDirectMessage(body: String) {
        val current = _activeDirectChatPage.value ?: return
        if (_isSendingDirectMessage.value) return
        val validationErr = B2BOnboardingValidator.findDirectMessageValidationError(body)
        if (validationErr != null) {
            _statusMessage.value = validationErr
            return
        }
        workspaceScope.launch {
            _isSendingDirectMessage.value = true
            val res = if (_directChatIsOwnerView.value) {
                val patientId = _directChatTargetPatientId.value
                if (patientId != null) {
                    apiClient.sendOwnerDirectMessage(patientId, body)
                } else {
                    br.com.bragasaude.domain.b2b.SendDirectMessageResult(errorDetail = "Participante inválido.")
                }
            } else {
                apiClient.sendEnrollmentDirectMessage(current.programId, body)
            }
            _isSendingDirectMessage.value = false
            val sent = res.message
            if (sent != null) {
                val appended = buildList {
                    res.aiReply?.let { add(it) }
                    add(sent)
                }
                val uid = auth.currentUser?.uid.orEmpty()
                b2bContextRepository.cacheDirectMessages(
                    userId = uid,
                    messages = appended,
                    programName = current.programName,
                    primaryColor = activeContext.value.primaryColor,
                    patientName = _directChatCounterpartTitle.value.takeIf { _directChatIsOwnerView.value }
                )
                _activeDirectChatPage.value = current.copy(
                    thread = res.thread ?: current.thread,
                    messages = B2BContextRepository.mergeDirectMessages(appended, current.messages)
                )
                if (res.sentOffHours) {
                    _statusMessage.value = "Enviada fora do horário — aguardando retorno."
                }
            } else {
                _statusMessage.value = res.errorDetail
                    ?: "Não foi possível enviar a mensagem agora."
            }
        }
    }

    fun handoffDirectChat(mode: String, note: String? = null) {
        val current = _activeDirectChatPage.value ?: return
        val patientId = _directChatTargetPatientId.value ?: return
        if (!_directChatIsOwnerView.value || _isUpdatingHandoff.value) return
        workspaceScope.launch {
            _isUpdatingHandoff.value = true
            val res = apiClient.handoffOwnerDirectChat(patientId, mode, note)
            _isUpdatingHandoff.value = false
            val updated = res.thread
            if (updated != null) {
                _activeDirectChatPage.value = current.copy(thread = updated)
                _statusMessage.value = if (mode == "HUMAN_ACTIVE") {
                    "Você assumiu este chat direto."
                } else {
                    "Conversa devolvida para a Recepcionista IA."
                }
            } else {
                _statusMessage.value = res.errorDetail
                    ?: "Não foi possível atualizar o controle desta conversa."
            }
        }
    }

    fun requestHumanHandoff(note: String? = null) {
        val current = _activeDirectChatPage.value ?: return
        if (_directChatIsOwnerView.value || _isUpdatingHandoff.value) return
        workspaceScope.launch {
            _isUpdatingHandoff.value = true
            val res = apiClient.requestHumanInDirectChat(current.programId, note)
            _isUpdatingHandoff.value = false
            val updated = res.thread
            if (updated != null) {
                _activeDirectChatPage.value = current.copy(thread = updated)
                _statusMessage.value = "Equipe notificada: seu pedido de atendimento humano foi registrado."
            } else {
                _statusMessage.value = res.errorDetail
                    ?: "Não foi possível encaminhar para atendente humano agora."
            }
        }
    }
}
