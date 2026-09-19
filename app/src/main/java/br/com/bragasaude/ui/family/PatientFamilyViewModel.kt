package br.com.bragasaude.ui.family

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.bragasaude.data.local.FamilyBindingEntity
import br.com.bragasaude.data.local.FamilyMessageEntity
import br.com.bragasaude.data.remote.repository.FamilyBridgeRepository
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject
import br.com.bragasaude.util.BragaConstants

/**
 * ViewModel especializado para a perspectiva do Paciente (Titular/Idoso) na Ponte Familiar.
 *
 * Responsável por:
 * - Geração e compartilhamento de códigos de conexão (invites)
 * - Listagem e revogação de cuidadores ativos
 * - Histórico de convites
 * - Recebimento e leitura de bilhetes de carinho da família
 */
@HiltViewModel
class PatientFamilyViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val familyRepository: FamilyBridgeRepository,
    private val auth: FirebaseAuth
) : ViewModel() {

    private val _uiEvents = MutableSharedFlow<FamilyUiEvent>()
    val uiEvents: SharedFlow<FamilyUiEvent> = _uiEvents.asSharedFlow()

    val currentUserId: String
        get() = auth.currentUser?.uid ?: BragaConstants.GUEST_UID

    private val _connectionCode = MutableStateFlow<String?>(null)
    val connectionCode: StateFlow<String?> = _connectionCode.asStateFlow()

    private val _activeCaregivers = MutableStateFlow<List<FamilyBindingEntity>>(emptyList())
    val activeCaregivers: StateFlow<List<FamilyBindingEntity>> = _activeCaregivers.asStateFlow()

    private val _inviteHistory = MutableStateFlow<List<FamilyBindingEntity>>(emptyList())
    val inviteHistory: StateFlow<List<FamilyBindingEntity>> = _inviteHistory.asStateFlow()

    private val _familyMessages = MutableStateFlow<List<FamilyMessageEntity>>(emptyList())
    val familyMessages: StateFlow<List<FamilyMessageEntity>> = _familyMessages.asStateFlow()

    private val _unreadMessageCount = MutableStateFlow(0)
    val unreadMessageCount: StateFlow<Int> = _unreadMessageCount.asStateFlow()

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

    private fun loadData(patientUid: String = currentUserId) {
        if (patientUid.isBlank() || patientUid.startsWith("00000000")) return

        dataLoadJob?.cancel()
        dataLoadJob = viewModelScope.launch {
            // Sincronizar vínculos do paciente da nuvem para o Room
            launch {
                try {
                    familyRepository.syncBindingsForPatient(patientUid)
                } catch (e: Exception) {
                    android.util.Log.w("PatientFamilyVM", "Falha ao sincronizar vínculos remotos do paciente: ${e.message}")
                }
            }

            // Observar cuidadores conectados ao paciente atual
            launch {
                familyRepository.getActiveBindingsForPatient(patientUid).collectLatest { bindings ->
                    _activeCaregivers.value = bindings
                }
            }

            // Observar mensagens da família no dispositivo do paciente
            launch {
                familyRepository.getRecentMessagesForPatient(patientUid, Int.MAX_VALUE).collectLatest { messages ->
                    _familyMessages.value = messages
                }
            }

            // Observar mensagens não lidas
            launch {
                familyRepository.getUnreadMessagesForPatient(patientUid).collectLatest { unread ->
                    _unreadMessageCount.value = unread.size
                }
            }

            // Observar histórico de códigos gerados
            launch {
                familyRepository.getGeneratedCodesForPatient(patientUid).collectLatest { codes ->
                    _inviteHistory.value = codes
                }
            }

            // Limpeza de códigos expirados em background
            launch {
                familyRepository.cleanExpiredCodes()
            }

            // D47: purga local das mensagens familiares que completaram 24h
            launch {
                try {
                    familyRepository.purgeExpiredFamilyMessages()
                } catch (e: Exception) {
                    android.util.Log.w("PatientFamilyVM", "Falha na purga de mensagens expiradas: ${e.message}")
                }
            }
        }
    }

    fun loadExistingCode() {
        val uid = currentUserId
        if (uid.isBlank() || uid.startsWith("00000000")) return

        viewModelScope.launch {
            try {
                familyRepository.syncBindingsForPatient(uid)
            } catch (_: Exception) {}

            familyRepository.getGeneratedCodesForPatient(uid).firstOrNull()?.let { codes ->
                // Filtra códigos já aceitos por algum cuidador (status ACTIVE) para não exibir código já em uso
                val activeCodes = codes.filter { it.status == "ACTIVE" }.map { it.connectionCode }.toSet()
                val activePending = codes.firstOrNull {
                    it.status == "PENDING" &&
                    it.expiresAt > System.currentTimeMillis() &&
                    it.connectionCode !in activeCodes
                }
                if (activePending != null) {
                    _connectionCode.value = activePending.connectionCode
                } else {
                    generateInvite()
                }
            } ?: run {
                generateInvite()
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
                // Invalida convites pendentes anteriores para evitar acúmulo de códigos órfãos
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
                android.util.Log.e("PatientFamilyVM", "Erro ao marcar mensagem como lida: ${e.message}")
            }
        }
    }

    fun markAllMessagesAsRead() {
        viewModelScope.launch {
            try {
                familyRepository.markAllMessagesAsRead(currentUserId)
            } catch (e: Exception) {
                android.util.Log.e("PatientFamilyVM", "Erro ao marcar todas as mensagens como lidas: ${e.message}")
            }
        }
    }

    /** D47 — Apaga a própria mensagem do paciente (janela de 24h). */
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

                val recipientId = binding.caregiverUserId
                val senderName = auth.currentUser?.displayName ?: "Familiar"

                familyRepository.sendMessageBidirectional(
                    bindingId = bindingId,
                    recipientId = recipientId,
                    senderName = senderName,
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

    override fun onCleared() {
        super.onCleared()
        authListener?.let { auth.removeAuthStateListener(it) }
        dataLoadJob?.cancel()
    }
}
