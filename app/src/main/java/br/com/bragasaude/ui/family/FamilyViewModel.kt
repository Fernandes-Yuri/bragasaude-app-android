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
import kotlinx.coroutines.delay
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt
import javax.inject.Inject
import br.com.bragasaude.util.BragaConstants

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

/**
 * Estado não-fatal do polling de mensagens da família. A UI mostra uma faixa
 * discreta; o histórico local continua legível.
 */
data class ChatSyncState(
    val isPolling: Boolean = false,
    val consecutiveFailures: Int = 0,
    /** Mensagem curta pronta para UI; null quando está saudável. */
    val warning: String? = null
) {
    val isHealthy: Boolean get() = consecutiveFailures == 0
    val isRecovering: Boolean get() = consecutiveFailures in 1..2
    val isFailing: Boolean get() = consecutiveFailures > 2
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
        get() = auth.currentUser?.uid ?: BragaConstants.GUEST_UID

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

    private val selectedGroup = MutableStateFlow<String?>(null)
    val chatPatientId = combine(activeBindingsForCurrentUser, selectedGroup) { bindings, selected ->
        resolveFamilyChatGroup(bindings, currentUserId, selected)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun selectChatGroup(patientId: String) {
        if (familyChatGroups(activeBindingsForCurrentUser.value, currentUserId).contains(patientId)) {
            selectedGroup.value = patientId
        }
    }

    fun groupName(patientId: String): Flow<String> = profileDao.getProfile(patientId).map {
        if (patientId == currentUserId) "Minha família"
        else it?.fullName?.takeIf(String::isNotBlank) ?: "Familiar ${patientId.takeLast(6)}"
    }

    private val _inviteHistory = MutableStateFlow<List<FamilyBindingEntity>>(emptyList())
    val inviteHistory: StateFlow<List<FamilyBindingEntity>> = _inviteHistory.asStateFlow()

    private val _familyMessages = MutableStateFlow<List<FamilyMessageEntity>>(emptyList())
    val familyMessages: StateFlow<List<FamilyMessageEntity>> = _familyMessages.asStateFlow()

    /**
     * Estado da sincronização periódica do chat da família. Não é fatal: a UI
     * usa para mostrar "tentando reconectar"/"falhou" sem interromper a leitura
     * do histórico local. Antes toda falha era silenciosa (só Log.e).
     */
    private val _chatSyncState = MutableStateFlow(ChatSyncState())
    val chatSyncState: StateFlow<ChatSyncState> = _chatSyncState.asStateFlow()

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

    // ==================== Polling resiliente do chat ====================

    /** Apenas a FamilyChatScreen ativa; evita polling em background. */
    private val isChatScreenVisible = MutableStateFlow(false)

    private companion object {
        /** Intervalo base do polling com a tela ativa e saudável. */
        const val POLL_INTERVAL_MS = 15_000L
        /** Teto do backoff exponencial em falhas consecutivas. */
        const val POLL_INTERVAL_MAX_MS = 60_000L
        /** Limite do shift (2^N) para evitar estouro do Long. */
        const val BACKOFF_SHIFT_CAP = 4
        /** Intervalo de checagem do gate de visibilidade (pausa barata). */
        const val VISIBILITY_GATE_INTERVAL_MS = 5_000L
    }

    /**
     * Marca a tela de chat como visível/oculta. Chamado a partir do ciclo de
     * vida da FamilyChatScreen (DisposableEffect) — é o liga/desliga do polling.
     */
    fun setChatScreenVisible(visible: Boolean) {
        isChatScreenVisible.value = visible
    }

    /**
     * Executa um ciclo de sincronização do chat e devolve true quando a
     * resposta do servidor mudou desde o último ciclo (e portanto vale a pena
     * reagir). Skip por fingerprint elimina o tráfego repetido quando a
     * conversa parou — a causa do ruído no log do gateway.
     */
    private suspend fun syncChatCycle(
        userId: String,
        patientId: String,
        lastHash: String?,
        onHashChanged: (String) -> Unit
    ): Boolean {
        val snapshot = familyRepository.chatCycleSnapshot(userId, patientId)
        val hash = snapshot.messagesFingerprint ?: return false
        if (hash == lastHash) return false
        onHashChanged(hash)
        return true
    }

    init {
        setupAuthAndLoadData()
    }

    private fun setupAuthAndLoadData() {
        authListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            val uid = firebaseAuth.currentUser?.uid
            if (uid != null && uid != br.com.bragasaude.util.BragaConstants.GUEST_UID) {
                loadData(uid)
            }
        }
        authListener?.let { auth.addAuthStateListener(it) }

        val initialUid = auth.currentUser?.uid
        if (initialUid != null && initialUid != br.com.bragasaude.util.BragaConstants.GUEST_UID) {
            loadData(initialUid)
        }
    }

    private fun loadData(userId: String = currentUserId) {
        if (userId.isBlank() || userId == br.com.bragasaude.util.BragaConstants.GUEST_UID) return

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
                chatPatientId.collectLatest { targetPatientId ->
                    _familyMessages.value = emptyList()
                    if (targetPatientId == null) return@collectLatest
                    coroutineScope {
                        launch {
                            familyRepository.getRecentMessagesForPatient(targetPatientId, Int.MAX_VALUE).collectLatest {
                                _familyMessages.value = it
                            }
                        }
                        launch {
                            // Polling resiliente: só corre com a tela em primeiro
                            // plano, usa backoff exponencial em falha e pula o
                            // ciclo quando a resposta não mudou (fim do ruído de
                            // 6 hits/15s no gateway). Antes era um while cego e
                            // ininterrupto, rodando até com o app em background.
                            var consecutiveFailures = 0
                            var lastPayloadHash: String? = null
                            while (isActive) {
                                if (!isChatScreenVisible.value) {
                                    _chatSyncState.value = _chatSyncState.value.copy(isPolling = false)
                                    // Pausa barata até a tela voltar (gate de ciclo).
                                    delay(VISIBILITY_GATE_INTERVAL_MS)
                                    continue
                                }
                                _chatSyncState.value = _chatSyncState.value.copy(isPolling = true)
                                val cycleFailed = try {
                                    val changed = syncChatCycle(userId, targetPatientId, lastPayloadHash) { newHash ->
                                        lastPayloadHash = newHash
                                    }
                                    !changed
                                } catch (e: Exception) {
                                    android.util.Log.w("FamilyVM", "Falha no ciclo de sync do chat: ${e.message}")
                                    true
                                }
                                consecutiveFailures = if (cycleFailed) consecutiveFailures + 1 else 0
                                _chatSyncState.value = ChatSyncState(
                                    isPolling = true,
                                    consecutiveFailures = consecutiveFailures,
                                    warning = if (consecutiveFailures == 0) null
                                        else if (consecutiveFailures in 1..2) "Tentando atualizar as mensagens…"
                                        else "Não foi possível atualizar as mensagens. Mostrando as salvas no aparelho."
                                )
                                // Backoff exponencial capped: 15s → 30s → 60s (teto).
                                val base = if (consecutiveFailures == 0) POLL_INTERVAL_MS
                                    else POLL_INTERVAL_MS * (1L shl consecutiveFailures.coerceAtMost(BACKOFF_SHIFT_CAP))
                                delay(base.coerceAtMost(POLL_INTERVAL_MAX_MS))
                            }
                            _chatSyncState.value = ChatSyncState(isPolling = false)
                        }
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

            // D47: purga local das mensagens familiares que completaram 24h
            launch {
                try {
                    familyRepository.purgeExpiredFamilyMessages()
                } catch (e: Exception) {
                    android.util.Log.w("FamilyVM", "Falha na purga de mensagens expiradas: ${e.message}")
                }
            }

            // Observar pacientes que este cuidador acompanha
            launch {
                familyRepository.getActiveBindingsForCaregiver(userId).collectLatest { bindings ->
                    _watchedPatients.value = bindings
                    if (bindings.size == 1 && _dashboard.value.patientUserId.isEmpty()) {
                        selectPatient(bindings.first(), selectChat = false)
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
        if (uid.isBlank() || uid == br.com.bragasaude.util.BragaConstants.GUEST_UID) return

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
        if (uid.isBlank() || uid == br.com.bragasaude.util.BragaConstants.GUEST_UID) return

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

    /**
     * D47 — Apaga a própria mensagem do usuário (janela de 24h).
     * A exclusão some da tela imediatamente e é propagada ao servidor/família.
     */
    fun deleteFamilyMessage(messageId: String) {
        viewModelScope.launch {
            try {
                familyRepository.deleteMessage(messageId)
                _uiEvents.emit(FamilyUiEvent.Notice("Mensagem apagada."))
            } catch (e: Exception) {
                _uiEvents.emit(FamilyUiEvent.Error("Não foi possível apagar a mensagem."))
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

    fun selectPatient(binding: FamilyBindingEntity, selectChat: Boolean = true) {
        if (selectChat) selectedGroup.value = binding.patientUserId
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

        // As mensagens têm um único observador, vinculado a chatPatientId.

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

    fun sendMessageToGroup(text: String, onComplete: (Boolean) -> Unit) {
        val patientId = chatPatientId.value
        val binding = activeBindingsForCurrentUser.value.firstOrNull {
            it.patientUserId == patientId && it.status == "ACTIVE"
        }
        if (binding == null || text.isBlank()) { onComplete(false); return }
        viewModelScope.launch {
            try {
                val currentBinding = familyRepository.getBindingById(binding.id)
                check(currentBinding?.status == "ACTIVE") { "Vínculo indisponível." }
                val message = familyRepository.createFamilyMessage(binding.patientUserId,
                    auth.currentUser?.displayName ?: "Familiar", text)
                onComplete(true)
                _uiEvents.emit(FamilyUiEvent.Notice(if (message.pendingSync)
                    "Mensagem salva no aparelho, aguardando sincronização." else "Mensagem enviada ao grupo."))
            } catch (e: Exception) {
                onComplete(false)
                _uiEvents.emit(FamilyUiEvent.Error("Não foi possível enviar a mensagem."))
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

                check(binding.status == "ACTIVE" &&
                    currentUserId in listOf(binding.patientUserId, binding.caregiverUserId)) {
                    "Vínculo indisponível. Atualize sua família."
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

