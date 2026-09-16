package br.com.bragasaude.ui.family

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.bragasaude.data.local.*
import br.com.bragasaude.data.remote.repository.FamilyBridgeRepository
import br.com.bragasaude.data.remote.repository.GroceryRepository
import br.com.bragasaude.data.remote.repository.MessageTemplate
import br.com.bragasaude.ui.util.FamilyNotificationService
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

data class CaregiverDashboardState(
    val patientUserId: String = "",
    val patientName: String = "",
    val caregiverRelation: String = "Filho",
    val caregiverName: String = "",
    val systolic: Int? = null,
    val diastolic: Int? = null,
    val glucose: Int? = null,
    val hasDiabetes: Boolean = false,
    val hydrationMl: Int = 0,
    val hydrationTargetMl: Int = 2000,
    val steps: Int = 0,
    val stepGoal: Int = 8000,
    val lastMeasurementLabel: String? = null,
    val isLoading: Boolean = false
)

sealed interface FamilyUiEvent {
    data class Notice(val text: String) : FamilyUiEvent
    data class Error(val text: String) : FamilyUiEvent
}

@HiltViewModel
class FamilyViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val familyRepository: FamilyBridgeRepository,
    private val profileDao: ProfileDao,
    private val vitalSignDao: VitalSignDao,
    private val dailyMetricsDao: DailyMetricsDao,
    private val groceryRepository: GroceryRepository,
    private val auth: FirebaseAuth
) : ViewModel() {

    private val _uiEvents = MutableSharedFlow<FamilyUiEvent>()
    val uiEvents: SharedFlow<FamilyUiEvent> = _uiEvents.asSharedFlow()

    val currentUserId: String
        get() = auth.currentUser?.uid ?: "00000000-0000-0000-0000-000000000000"

    // ==================== LADO DO PACIENTE (IDOSO / TITULAR) ====================

    private val _connectionCode = MutableStateFlow<String?>(null)
    val connectionCode: StateFlow<String?> = _connectionCode.asStateFlow()

    private val _activeCaregivers = MutableStateFlow<List<FamilyBindingEntity>>(emptyList())
    val activeCaregivers: StateFlow<List<FamilyBindingEntity>> = _activeCaregivers.asStateFlow()

    private val _watchedPatients = MutableStateFlow<List<FamilyBindingEntity>>(emptyList())
    val watchedPatients: StateFlow<List<FamilyBindingEntity>> = _watchedPatients.asStateFlow()

    /** Vínculos ativos do usuário (paciente ou cuidador) */
    val activeBindingsForCurrentUser: StateFlow<List<FamilyBindingEntity>> = combine(
        _activeCaregivers,
        _watchedPatients
    ) { caregivers, watched ->
        (caregivers + watched).distinctBy { it.id }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _inviteHistory = MutableStateFlow<List<FamilyBindingEntity>>(emptyList())
    val inviteHistory: StateFlow<List<FamilyBindingEntity>> = _inviteHistory.asStateFlow()

    private val _familyMessages = MutableStateFlow<List<FamilyMessageEntity>>(emptyList())
    val familyMessages: StateFlow<List<FamilyMessageEntity>> = _familyMessages.asStateFlow()

    private val _unreadMessageCount = MutableStateFlow(0)
    val unreadMessageCount: StateFlow<Int> = _unreadMessageCount.asStateFlow()

    // ==================== LADO DO CUIDADOR ====================

    private val _dashboard = MutableStateFlow(CaregiverDashboardState())
    val dashboard: StateFlow<CaregiverDashboardState> = _dashboard.asStateFlow()

    private val _groceryItems = MutableStateFlow<List<GroceryListItemEntity>>(emptyList())
    val groceryItems: StateFlow<List<GroceryListItemEntity>> = _groceryItems.asStateFlow()

    private val _quickTemplates = MutableStateFlow(familyRepository.getQuickMessageTemplates())
    val quickTemplates: StateFlow<Map<String, MessageTemplate>> = _quickTemplates.asStateFlow()

    private var dashboardJob: Job? = null
    private var authListener: FirebaseAuth.AuthStateListener? = null
    private var dataLoadJob: Job? = null

    init {
        setupAuthAndLoadData()
    }

    private fun setupAuthAndLoadData() {
        authListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            val uid = firebaseAuth.currentUser?.uid
            if (uid != null && !uid.startsWith("00000000")) {
                loadData(uid)
            }
        }
        authListener?.let { auth.addAuthStateListener(it) }

        val initialUid = auth.currentUser?.uid
        if (initialUid != null && !initialUid.startsWith("00000000")) {
            loadData(initialUid)
        }
    }

    private fun loadData(userId: String = currentUserId) {
        if (userId.isBlank() || userId.startsWith("00000000")) return

        dataLoadJob?.cancel()
        dataLoadJob = viewModelScope.launch {
            // Sincronizar vínculos da nuvem em background
            launch {
                try {
                    familyRepository.syncBindingsForPatient(userId)
                    familyRepository.syncBindingsForCaregiver(userId)
                } catch (e: Exception) {
                    android.util.Log.w("FamilyVM", "Falha ao sincronizar vínculos remotos: ${e.message}")
                }
            }

            // Observar cuidadores conectados ao paciente atual
            launch {
                familyRepository.getActiveBindingsForPatient(userId).collectLatest { bindings ->
                    _activeCaregivers.value = bindings
                }
            }

            // Observar mensagens da família no celular do paciente ou cuidador
            launch {
                combine(_activeCaregivers, _watchedPatients) { caregivers, watched ->
                    caregivers.firstOrNull()?.patientUserId
                        ?: watched.firstOrNull()?.patientUserId
                        ?: userId
                }.distinctUntilChanged().collectLatest { targetPatientId ->
                    familyRepository.getRecentMessagesForPatient(targetPatientId, 50).collectLatest { messages ->
                        _familyMessages.value = messages
                    }
                }
            }

            // Observar mensagens não lidas
            launch {
                familyRepository.getUnreadMessagesForPatient(userId).collectLatest { unread ->
                    _unreadMessageCount.value = unread.size
                }
            }

            // Observar histórico de códigos gerados
            launch {
                familyRepository.getGeneratedCodesForPatient(userId).collectLatest { codes ->
                    _inviteHistory.value = codes
                }
            }

            // Limpar códigos expirados ao iniciar
            launch {
                familyRepository.cleanExpiredCodes()
            }

            // Observar pacientes que este cuidador acompanha
            launch {
                familyRepository.getActiveBindingsForCaregiver(userId).collectLatest { bindings ->
                    _watchedPatients.value = bindings
                    if (bindings.isNotEmpty() && _dashboard.value.patientUserId.isEmpty()) {
                        selectPatient(bindings.first())
                    } else if (bindings.isEmpty()) {
                        _dashboard.value = CaregiverDashboardState()
                    }
                }
            }
        }
    }

    // ==================== AÇÕES DO IDOSO (PACIENTE) ====================

    fun loadExistingCode() {
        val uid = currentUserId
        if (uid.isBlank() || uid.startsWith("00000000")) return

        viewModelScope.launch {
            try {
                familyRepository.syncBindingsForPatient(uid)
            } catch (_: Exception) {}

            familyRepository.getGeneratedCodesForPatient(uid).firstOrNull()?.let { codes ->
                val activeCodes = codes.filter { it.status == "ACTIVE" }.map { it.connectionCode }.toSet()
                val activePending = codes.firstOrNull {
                    it.status == "PENDING" &&
                    it.expiresAt > System.currentTimeMillis() &&
                    it.connectionCode !in activeCodes
                }
                if (activePending != null) {
                    _connectionCode.value = activePending.connectionCode
                }
            }
        }
    }

    fun regenerateInvite(caregiverName: String = "Familiar", caregiverRelation: String = "Filho") {
        generateInvite(caregiverName, caregiverRelation)
    }

    fun generateInvite(caregiverName: String = "Familiar", caregiverRelation: String = "Filho") {
        val uid = currentUserId
        if (uid.isBlank() || uid.startsWith("00000000")) return

        viewModelScope.launch {
            try {
                // Invalida convites pendentes anteriores para liberar códigos órfãos
                familyRepository.expireOldPendingBindings(uid)

                val binding = familyRepository.createPendingBinding(
                    patientUserId = uid,
                    caregiverName = caregiverName,
                    caregiverRelation = caregiverRelation
                )
                _connectionCode.value = binding.connectionCode
                _uiEvents.emit(FamilyUiEvent.Notice("Código ${binding.connectionCode} gerado com sucesso!"))
            } catch (e: Exception) {
                _uiEvents.emit(FamilyUiEvent.Error("Erro ao gerar convite: ${e.message}"))
            }
        }
    }

    fun revokeCaregiver(bindingId: String) {
        viewModelScope.launch {
            try {
                familyRepository.revokeBinding(bindingId)
                _uiEvents.emit(FamilyUiEvent.Notice("Acesso do familiar revogado com sucesso."))
            } catch (e: Exception) {
                _uiEvents.emit(FamilyUiEvent.Error("Erro ao revogar: ${e.message}"))
            }
        }
    }

    fun markMessageAsRead(messageId: String) {
        viewModelScope.launch {
            try {
                familyRepository.markMessageAsRead(messageId)
            } catch (e: Exception) {
                android.util.Log.e("FamilyVM", "Erro ao marcar mensagem como lida: ${e.message}")
            }
        }
    }

    fun markAllMessagesAsRead() {
        viewModelScope.launch {
            try {
                familyRepository.markAllMessagesAsRead(currentUserId)
            } catch (e: Exception) {
                android.util.Log.e("FamilyVM", "Erro ao marcar todas as mensagens como lidas: ${e.message}")
            }
        }
    }

    // ==================== AÇÕES DO CUIDADOR ====================

    fun selectPatient(binding: FamilyBindingEntity) {
        dashboardJob?.cancel()
        dashboardJob = viewModelScope.launch {
            _dashboard.value = _dashboard.value.copy(
                patientUserId = binding.patientUserId,
                caregiverRelation = binding.caregiverRelation,
                caregiverName = binding.caregiverName,
                isLoading = true
            )

            // Disparar sincronização remota dos dados mais recentes do paciente
            launch {
                familyRepository.syncPatientDataForCaregiver(binding.patientUserId)
            }

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
                        .filter { it.hydrationMl != null && it.measuredAt != null && it.measuredAt >= startOfDay }
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
            } catch (e: Exception) {
                _dashboard.value = _dashboard.value.copy(isLoading = false)
            }
        }

        // Observar lista de compras do paciente
        viewModelScope.launch {
            groceryRepository.getGroceryList(binding.patientUserId).collectLatest { items ->
                _groceryItems.value = items
            }
        }

        // Observar mensagens do paciente selecionado
        viewModelScope.launch {
            familyRepository.getRecentMessagesForPatient(binding.patientUserId, 50).collectLatest { messages ->
                _familyMessages.value = messages
            }
        }
    }

    fun toggleGroceryItem(itemId: String, isChecked: Boolean) {
        viewModelScope.launch {
            try {
                groceryRepository.updateCheckedStatus(itemId, isChecked)
            } catch (e: Exception) {
                _uiEvents.emit(FamilyUiEvent.Error("Erro ao atualizar lista: ${e.message}"))
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

                // Notificar dispositivo do paciente
                FamilyNotificationService.notifyPatientMessage(context, senderName, text)
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
        val currentPatientId = _dashboard.value.patientUserId
        if (currentPatientId.isBlank()) return
        viewModelScope.launch {
            try {
                familyRepository.syncPatientDataForCaregiver(currentPatientId)
            } catch (e: Exception) {
                android.util.Log.e("FamilyVM", "Erro ao sincronizar dados do paciente: ${e.message}")
            }
        }
    }

    fun markAllMessagesAsRead(patientUserId: String? = null) {
        viewModelScope.launch {
            try {
                val targetId = patientUserId ?: currentUserId
                familyRepository.markAllMessagesAsRead(targetId)
            } catch (e: Exception) {
                android.util.Log.e("FamilyVM", "Erro ao marcar mensagens como lidas: ${e.message}")
            }
        }
    }

    fun sendMessageToFamily(bindingId: String, text: String, iconType: String = "CUSTOM", onComplete: (() -> Unit)? = null) {
        if (text.isBlank()) {
            onComplete?.invoke()
            return
        }

        viewModelScope.launch {
            try {
                val binding = familyRepository.getBindingById(bindingId) ?: run {
                    onComplete?.invoke()
                    return@launch
                }

                val isCaregiver = binding.caregiverUserId == currentUserId
                val recipientId = if (isCaregiver) binding.patientUserId else binding.caregiverUserId
                val actualSenderName = if (isCaregiver) {
                    binding.caregiverName.ifBlank { auth.currentUser?.displayName ?: "Familiar" }
                } else {
                    auth.currentUser?.displayName ?: "Familiar"
                }

                familyRepository.sendMessageBidirectional(
                    bindingId = bindingId,
                    recipientId = recipientId,
                    senderName = actualSenderName,
                    messageText = text,
                    iconType = iconType
                )

                _uiEvents.emit(FamilyUiEvent.Notice("Mensagem enviada!"))
            } catch (e: Exception) {
                _uiEvents.emit(FamilyUiEvent.Error("Erro ao enviar: ${e.message}"))
            } finally {
                onComplete?.invoke()
            }
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
        dataLoadJob?.cancel()
        dashboardJob?.cancel()
    }
}

