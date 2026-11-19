package br.com.bragasaude.ui.family

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.bragasaude.data.local.*
import br.com.bragasaude.data.remote.repository.FamilyBridgeRepository
import br.com.bragasaude.data.remote.repository.MessageTemplate
import br.com.bragasaude.domain.b2b.FamilyProgramOverviewItemDto
import br.com.bragasaude.ui.util.FamilyNotificationService
import br.com.bragasaude.ui.util.NotificationHelper
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt
import javax.inject.Inject
import br.com.bragasaude.util.BragaConstants

/**
 * ViewModel especializado para o Dashboard do Cuidador na Ponte Familiar.
 *
 * Responsável por:
 * - Listagem dos pacientes acompanhados pelo cuidador
 * - Observação em tempo real (Firebase Data Connect -> Room -> UI) dos sinais vitais,
 *   hidratação e passos do paciente selecionado
 * - Envio de bilhetes de carinho e lembretes com modelos rápidos
 * - Compartilhamento de conquistas familiares no Feed Social Intergeracional
 * - Aceitação de novos vínculos através de código de conexão
 */
@HiltViewModel
class CaregiverDashboardViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val familyRepository: FamilyBridgeRepository,
    private val profileDao: ProfileDao,
    private val vitalSignDao: VitalSignDao,
    private val dailyMetricsDao: DailyMetricsDao,
    private val auth: FirebaseAuth
) : ViewModel() {

    private val _uiEvents = MutableSharedFlow<FamilyUiEvent>()
    val uiEvents: SharedFlow<FamilyUiEvent> = _uiEvents.asSharedFlow()

    val currentUserId: String
        get() = auth.currentUser?.uid ?: BragaConstants.GUEST_UID

    private val _watchedPatients = MutableStateFlow<List<FamilyBindingEntity>>(emptyList())
    val watchedPatients: StateFlow<List<FamilyBindingEntity>> = _watchedPatients.asStateFlow()

    private val _dashboard = MutableStateFlow(CaregiverDashboardState())
    val dashboard: StateFlow<CaregiverDashboardState> = _dashboard.asStateFlow()

    private val _upcomingConsultations = MutableStateFlow<List<ConsultationEntity>>(emptyList())
    val upcomingConsultations: StateFlow<List<ConsultationEntity>> = _upcomingConsultations.asStateFlow()

    private val _partnerPrograms = MutableStateFlow<List<FamilyProgramOverviewItemDto>>(emptyList())
    val partnerPrograms = _partnerPrograms.asStateFlow()
    private val _partnerProgramsError = MutableStateFlow<String?>(null)
    val partnerProgramsError = _partnerProgramsError.asStateFlow()
    private val _acknowledgingPartnerReminder = MutableStateFlow(false)
    val acknowledgingPartnerReminder = _acknowledgingPartnerReminder.asStateFlow()
    private var patientSelectionVersion = 0L
    private var programRequestVersion = 0L
    private var programAcknowledgementJob: Job? = null

    internal fun observeConsultationReminders(consultation: ConsultationEntity): Flow<ConsultationReminderStatus> = flow {
        val owner = auth.currentUser?.uid
        if (owner == null || owner == BragaConstants.GUEST_UID) {
            emit(ConsultationReminderStatus(loading = false))
            return@flow
        }
        emitAll(combine(
            profileDao.getProfile(owner),
            NotificationHelper.observeConsultationReminderTags(context, consultation.id, owner, consultation.scheduledDate.time)
        ) { profile, tags ->
            val active = auth.currentUser?.uid == owner && consultation.status in setOf("SUGGESTED", "ACCEPTED")
            ConsultationReminderStatus(
                scheduledTags = if (active) tags else emptySet(),
                appEnabled = profile?.notificationsEnabled != false,
                deviceEnabled = NotificationHelper.consultationNotificationsAllowed(context),
                loading = false
            )
        })
    }.catch { emit(ConsultationReminderStatus(loading = false, unavailable = true)) }

    private val _quickTemplates = MutableStateFlow(familyRepository.getQuickMessageTemplates())
    val quickTemplates: StateFlow<Map<String, MessageTemplate>> = _quickTemplates.asStateFlow()

    private val _lastCloudSync = MutableStateFlow<Long?>(null)
    val lastCloudSync = _lastCloudSync.asStateFlow()
    private val refreshLock = kotlinx.coroutines.sync.Mutex()

    private var dashboardJob: Job? = null
    private var realtimeSubscriptionJob: Job? = null
    private var consultationsJob: Job? = null
    private var bindingsJob: Job? = null
    private var dataOwner: String? = null
    private var authListener: FirebaseAuth.AuthStateListener? = null

    init {
        setupAuthAndLoadData()
    }

    private fun setupAuthAndLoadData() {
        authListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            val uid = firebaseAuth.currentUser?.uid
            if (uid != null && uid != br.com.bragasaude.util.BragaConstants.GUEST_UID) {
                loadData(uid)
            } else {
                bindingsJob?.cancel()
                dataOwner = null
                clearPatient()
                _watchedPatients.value = emptyList()
            }
        }
        authListener?.let { auth.addAuthStateListener(it) }

        val initialUid = auth.currentUser?.uid
        if (initialUid != null && initialUid != br.com.bragasaude.util.BragaConstants.GUEST_UID) {
            loadData(initialUid)
        }
    }

    private fun loadData(caregiverUid: String = currentUserId) {
        if (caregiverUid.isBlank() || caregiverUid == br.com.bragasaude.util.BragaConstants.GUEST_UID) return

        if (dataOwner != caregiverUid) {
            clearPatient()
            _watchedPatients.value = emptyList()
            dataOwner = caregiverUid
        }
        bindingsJob?.cancel()
        bindingsJob = viewModelScope.launch {
            // 1. Baixar vínculos atualizados da nuvem (evita tela vazia se o cache local foi limpo)
            launch {
                try {
                    familyRepository.syncBindingsForCaregiver(caregiverUid)
                } catch (e: kotlinx.coroutines.CancellationException) {
                    // Troca de conta/tela não é falha de sync: propaga o cancelamento
                    // para não gerar o falso aviso "StandaloneCoroutine was cancelled".
                    throw e
                } catch (e: Exception) {
                    android.util.Log.w("CaregiverDashboardVM", "Falha ao sincronizar vínculos do cuidador: ${e.message}")
                }
            }

            // 2. Observar pacientes acompanhados por este cuidador no Room (SSOT)
            familyRepository.getActiveBindingsForCaregiver(caregiverUid).collectLatest { bindings ->
                _watchedPatients.value = bindings
                if (bindings.isNotEmpty() && bindings.none { it.patientUserId == _dashboard.value.patientUserId }) {
                    selectPatient(bindings.first())
                } else if (bindings.isEmpty()) {
                    clearPatient()
                }
            }
        }
    }

    fun recentMessages(patientId: String): Flow<List<FamilyMessageEntity>> =
        if (patientId.isBlank()) flowOf(emptyList()) else familyRepository.getRecentMessagesForPatient(patientId)

    private fun clearPatient() {
        patientSelectionVersion++
        programRequestVersion++
        programAcknowledgementJob?.cancel()
        _partnerPrograms.value = emptyList()
        _partnerProgramsError.value = null
        _acknowledgingPartnerReminder.value = false
        dashboardJob?.cancel()
        realtimeSubscriptionJob?.cancel()
        consultationsJob?.cancel()
        _upcomingConsultations.value = emptyList()
        _dashboard.value = CaregiverDashboardState()
        _lastCloudSync.value = null
    }

    fun selectPatient(binding: FamilyBindingEntity) {
        clearPatient()

        viewModelScope.launch {
            try {
                vitalSignDao.deduplicateVitals()
            } catch (_: Exception) {}
        }

        // Observar consultas ativas do paciente selecionado
        consultationsJob = viewModelScope.launch {
            familyRepository.getActiveConsultationsForUser(binding.patientUserId).collectLatest { list ->
                _upcomingConsultations.value = list
            }
        }

        // 1. Iniciar subscrição Realtime no Firebase Data Connect para atualizar Room em background
        realtimeSubscriptionJob = viewModelScope.launch {
            launch {
                try {
                    familyRepository.subscribePatientVitalSignsRealtime(binding.patientUserId).collect {
                        // Inserções no Room são feitas internamente pelo flow do repositório
                    }
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    android.util.Log.w("CaregiverDashboardVM", "Subscrição realtime vitals falhou, mantendo offline: ${e.message}")
                }
            }
            launch {
                try {
                    familyRepository.subscribeFamilyMessagesRealtime(binding.patientUserId).collect {
                        // Inserções no Room são feitas internamente pelo flow do repositório
                    }
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    android.util.Log.w("CaregiverDashboardVM", "Subscrição realtime mensagens falhou, mantendo offline: ${e.message}")
                }
            }
        }

        // 2. Observar Room (Single Source of Truth) para compor o estado da UI
        dashboardJob = viewModelScope.launch {
            _dashboard.value = CaregiverDashboardState(
                patientUserId = binding.patientUserId,
                caregiverRelation = binding.caregiverRelation,
                caregiverName = binding.caregiverName,
                isLoading = true
            )


            launch { syncSelectedPatient() }

            try {
                combine(
                    profileDao.getProfile(binding.patientUserId),
                    vitalSignDao.getAll(binding.patientUserId),
                    dailyMetricsDao.getRecent30Days(binding.patientUserId)
                ) { profile, vitalsList, dailyMetricsList ->
                    val patientName = profile?.fullName?.takeIf { it.isNotBlank() } ?: binding.caregiverName
                    val hasDiabetes = profile?.hasDiabetes ?: false

                    val latestBP = vitalsList.firstOrNull { it.systolicPressure != null && it.diastolicPressure != null }
                    val latestGlucose = vitalsList.firstOrNull { it.glucoseLevel != null }

                    val startOfDay = Calendar.getInstance().apply {
                        set(Calendar.HOUR_OF_DAY, 0)
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }.time

                    val hydrationMlToday = vitalsList
                        .filter { it.hydrationMl != null && it.measuredAt != null && it.measuredAt >= startOfDay && it.measuredAt.toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDate() == java.time.LocalDate.now() }
                        .sumOf { it.hydrationMl ?: 0 }

                    val weight = profile?.weight?.toDouble() ?: 70.0
                    val hydrationTargetMl = profile?.hydrationTargetMl ?: (weight * 35.0).roundToInt()

                    val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                    val stepsToday = dailyMetricsList.firstOrNull { it.date == todayStr }?.steps ?: 0
                    val stepGoal = profile?.stepGoal ?: 8000

                    val mostRecentTime = listOfNotNull(
                        latestBP?.measuredAt,
                        latestGlucose?.measuredAt,
                        vitalsList.firstOrNull { it.measuredAt != null }?.measuredAt
                    ).maxOrNull()

                    CaregiverDashboardState(
                        patientUserId = binding.patientUserId,
                        patientName = patientName,
                        caregiverRelation = binding.caregiverRelation,
                        caregiverName = binding.caregiverName,
                        systolic = latestBP?.systolicPressure,
                        pressureDateLabel = latestBP?.measuredAt?.let { SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(it) },
                        glucoseDateLabel = latestGlucose?.measuredAt?.let { SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(it) },
                        diastolic = latestBP?.diastolicPressure,
                        glucose = if (hasDiabetes) latestGlucose?.glucoseLevel else null,
                        hasDiabetes = hasDiabetes,
                        hydrationMl = hydrationMlToday,
                        hydrationTargetMl = hydrationTargetMl,
                        steps = stepsToday,
                        stepGoal = stepGoal,
                        lastMeasurementLabel = mostRecentTime?.let {
                            SimpleDateFormat("HH:mm", Locale.getDefault()).format(it)
                        },
                        isLoading = false
                    )
                }.collectLatest { state ->
                    _dashboard.value = state
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _dashboard.value = _dashboard.value.copy(isLoading = false)
            }
        }


    }



    fun sendQuickMessage(templateKey: String) {
        val template = _quickTemplates.value[templateKey] ?: return
        sendCareMessage(template.text, template.type)
    }

    fun sendCareMessage(text: String, iconType: String = "LOVE") {
        val targetPatientId = _dashboard.value.patientUserId
        if (targetPatientId.isBlank()) {
            viewModelScope.launch {
                _uiEvents.emit(FamilyUiEvent.Error("Selecione um familiar para enviar a mensagem."))
            }
            return
        }

        viewModelScope.launch {
            try {
                val senderName = _dashboard.value.caregiverName.ifBlank {
                    auth.currentUser?.displayName ?: "Familiar"
                }
                familyRepository.sendCareMessage(
                    patientUserId = targetPatientId,
                    senderName = senderName,
                    messageText = text,
                    iconType = iconType
                )
                _uiEvents.emit(FamilyUiEvent.Notice("Recado enviado com carinho!"))

                // O destinatário é notificado pelo push do servidor (doc 10 §1A) e,
                // como fallback, pela notificação local no momento do pull.
            } catch (e: Exception) {
                _uiEvents.emit(FamilyUiEvent.Error("Erro ao enviar: ${e.message}"))
            }
        }
    }

    fun sendMedicationReminder(text: String) {
        val msg = if (text.isNotBlank()) "Lembrete de medicação: $text" else "Lembre de tomar sua medicação no horário!"
        sendCareMessage(msg, "MED")
    }

    fun sendAppointmentReminder(text: String) {
        val msg = if (text.isNotBlank()) "Consulta agendada: $text" else "Não esqueça da sua consulta agendada!"
        sendCareMessage(msg, "CALENDAR")
    }

    /**
     * Agenda consulta médica durável no banco Room e programa lembretes locais no WorkManager (24h e 1h antes).
     */
    fun scheduleConsultation(specialty: String, scheduledDateMillis: Long) {
        val targetPatientId = _dashboard.value.patientUserId
        if (targetPatientId.isBlank()) {
            viewModelScope.launch {
                _uiEvents.emit(FamilyUiEvent.Error("Selecione um familiar para agendar a consulta."))
            }
            return
        }

        viewModelScope.launch {
            try {
                val caregiverName = _dashboard.value.caregiverName.ifBlank {
                    auth.currentUser?.displayName ?: "Familiar"
                }
                val caregiverRelation = _dashboard.value.caregiverRelation

                familyRepository.createConsultation(
                    userId = targetPatientId,
                    title = specialty.trim(),
                    scheduledDate = scheduledDateMillis,
                    caregiverUserId = currentUserId,
                    caregiverName = caregiverName,
                    caregiverRelation = caregiverRelation
                )

                val sdf = SimpleDateFormat("dd/MM/yyyy 'às' HH:mm", Locale.getDefault())
                val formattedDate = sdf.format(Date(scheduledDateMillis))
                val msg = "Consulta agendada: ${specialty.trim()} - $formattedDate"
                sendCareMessage(msg, "CALENDAR")

                _uiEvents.emit(FamilyUiEvent.Notice("Consulta salva. Confira o estado de sincronização na agenda."))
            } catch (e: Exception) {
                _uiEvents.emit(FamilyUiEvent.Error("Erro ao agendar consulta: ${e.message}"))
            }
        }
    }

    /**
     * Cancela uma consulta agendada e desativa seus lembretes programados no WorkManager.
     */
    fun cancelConsultation(consultationId: String) {
        viewModelScope.launch {
            try {
                familyRepository.cancelConsultation(consultationId, currentUserId)
                _uiEvents.emit(FamilyUiEvent.Notice("Consulta cancelada e lembretes desativados."))
            } catch (e: Exception) {
                _uiEvents.emit(FamilyUiEvent.Error("Erro ao cancelar consulta: ${e.message}"))
            }
        }
    }

    fun publishFamilyPride(title: String, description: String) {
        val targetPatientId = _dashboard.value.patientUserId
        if (targetPatientId.isBlank()) return

        viewModelScope.launch {
            try {
                val caregiverName = _dashboard.value.caregiverName.ifBlank {
                    auth.currentUser?.displayName ?: "Familiar"
                }
                val patientName = _dashboard.value.patientName.ifBlank { "Meu familiar" }

                familyRepository.createFamilyPost(
                    caregiverUserId = currentUserId,
                    caregiverName = caregiverName,
                    patientUserId = targetPatientId,
                    patientName = patientName,
                    title = title,
                    description = description
                )
                _uiEvents.emit(FamilyUiEvent.Notice("Publicação compartilhada com a comunidade!"))
            } catch (e: Exception) {
                _uiEvents.emit(FamilyUiEvent.Error("Erro ao publicar: ${e.message}"))
            }
        }
    }

    fun refreshPatientData() {
        viewModelScope.launch { syncSelectedPatient() }
    }

    private fun isCurrentPatient(owner: String, patientId: String, selectionVersion: Long): Boolean =
        auth.currentUser?.uid == owner && _dashboard.value.patientUserId == patientId &&
            patientSelectionVersion == selectionVersion &&
            _watchedPatients.value.any { it.patientUserId == patientId && it.status == "ACTIVE" }

    private suspend fun refreshPartnerPrograms(owner: String, patientId: String, selectionVersion: Long) {
        if (!isCurrentPatient(owner, patientId, selectionVersion)) return
        val requestVersion = ++programRequestVersion
        try {
            val overview = familyRepository.getFamilyProgramOverview(patientId)
            if (isCurrentPatient(owner, patientId, selectionVersion) && requestVersion == programRequestVersion) {
                check(overview.patientUserId == patientId)
                _partnerPrograms.value = overview.programs.filter { it.familyShareEnabled }
                _partnerProgramsError.value = null
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: Exception) {
            if (isCurrentPatient(owner, patientId, selectionVersion) && requestVersion == programRequestVersion) {
                // Nunca conservar dados de um consentimento que pode ter sido revogado.
                _partnerPrograms.value = emptyList()
                _partnerProgramsError.value = "Não foi possível consultar os programas compartilhados. Atualize para tentar novamente."
            }
        }
    }

    fun acknowledgePartnerReminder(programId: String, reminderId: String) {
        val owner = auth.currentUser?.uid ?: return
        val patientId = _dashboard.value.patientUserId
        val selectionVersion = patientSelectionVersion
        if (!isCurrentPatient(owner, patientId, selectionVersion) || _acknowledgingPartnerReminder.value) return
        val program = _partnerPrograms.value.firstOrNull { it.programId == programId && it.familyShareEnabled }
            ?: return
        if (!program.remindersEnabled || program.reminders.none {
                it.reminderId == reminderId && it.acknowledgedAt == null
            }) return
        _acknowledgingPartnerReminder.value = true
        programAcknowledgementJob = viewModelScope.launch {
            try {
                familyRepository.acknowledgeFamilyProgramReminder(patientId, programId, reminderId)
                if (isCurrentPatient(owner, patientId, selectionVersion)) {
                    refreshPartnerPrograms(owner, patientId, selectionVersion)
                    if (isCurrentPatient(owner, patientId, selectionVersion)) {
                        _uiEvents.emit(FamilyUiEvent.Notice("Ciência do lembrete registrada."))
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) {
                if (isCurrentPatient(owner, patientId, selectionVersion)) {
                    programRequestVersion++
                    _partnerPrograms.value = emptyList()
                    _partnerProgramsError.value = "Não foi possível confirmar o lembrete. Atualize os programas compartilhados."
                    _uiEvents.emit(FamilyUiEvent.Error("Não foi possível confirmar o lembrete."))
                }
            } finally {
                if (isCurrentPatient(owner, patientId, selectionVersion)) {
                    _acknowledgingPartnerReminder.value = false
                }
            }
        }
    }

    /** A tela controla a duração do ciclo; cada download termina antes do próximo. */
    suspend fun pollPatientData() {
        while (true) {
            syncSelectedPatient()
            kotlinx.coroutines.delay(15_000L)
        }
    }

    private suspend fun syncSelectedPatient() {
        if (!refreshLock.tryLock()) return
        val owner = auth.currentUser?.uid
        val patientId = _dashboard.value.patientUserId
        val selectionVersion = patientSelectionVersion
        var familyAccessChecked = false
        try {
            if (owner == null || patientId.isBlank()) return
            familyRepository.syncBindingsForCaregiver(owner, strict = true)
            if (!isCurrentPatient(owner, patientId, selectionVersion)) return
            val bindings = familyRepository.getActiveBindingsForCaregiver(owner).first()
            if (bindings.none { it.patientUserId == patientId && it.status == "ACTIVE" }) {
                clearPatient()
                return
            }
            // Independente da permissão de sinais vitais pessoais do familiar.
            refreshPartnerPrograms(owner, patientId, selectionVersion)
            familyAccessChecked = true
            if (!isCurrentPatient(owner, patientId, selectionVersion)) return
            familyRepository.syncPatientDataForCaregiver(patientId).getOrThrow()
            if (isCurrentPatient(owner, patientId, selectionVersion)) {
                _lastCloudSync.value = System.currentTimeMillis()
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            if (!familyAccessChecked && owner != null && isCurrentPatient(owner, patientId, selectionVersion)) {
                programRequestVersion++
                _partnerPrograms.value = emptyList()
                _partnerProgramsError.value = "Não foi possível atualizar o acesso familiar. Atualize para tentar novamente."
            }
            android.util.Log.w("CaregiverDashboardVM", "Falha no download do familiar: ${e.message}")
        } finally {
            refreshLock.unlock()
        }
    }

    suspend fun connectWithCode(code: String, name: String, relation: String): Result<Unit> {
        return try {
            val cleanCode = code.trim().uppercase()
            familyRepository.findAndAcceptBinding(
                code = cleanCode,
                caregiverUserId = currentUserId,
                caregiverName = name,
                caregiverRelation = relation
            )
            _uiEvents.emit(FamilyUiEvent.Notice("Vínculo estabelecido com sucesso!"))
            Result.success(Unit)
        } catch (e: Exception) {
            _uiEvents.emit(FamilyUiEvent.Error(e.message ?: "Erro ao conectar."))
            Result.failure(e)
        }
    }

    fun getBPHistory(userId: String): Flow<List<VitalSignEntity>> {
        return if (userId.isNotBlank()) vitalSignDao.getBloodPressureRecords(userId) else flowOf(emptyList())
    }

    fun getGlucoseHistory(userId: String): Flow<List<VitalSignEntity>> {
        return if (userId.isNotBlank()) vitalSignDao.getGlucoseRecords(userId) else flowOf(emptyList())
    }

    fun getHydrationHistory(userId: String): Flow<List<VitalSignEntity>> {
        return if (userId.isNotBlank()) vitalSignDao.getHydrationRecords(userId) else flowOf(emptyList())
    }

    fun getDailyMetricsHistory(userId: String): Flow<List<DailyMetricsEntity>> {
        return if (userId.isNotBlank()) dailyMetricsDao.getRecent30Days(userId) else flowOf(emptyList())
    }

    override fun onCleared() {
        super.onCleared()
        authListener?.let { auth.removeAuthStateListener(it) }
        bindingsJob?.cancel()
        dashboardJob?.cancel()
        consultationsJob?.cancel()
        realtimeSubscriptionJob?.cancel()
        programAcknowledgementJob?.cancel()
    }
}
