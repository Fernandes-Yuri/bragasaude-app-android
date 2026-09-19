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
import br.com.bragasaude.util.BragaConstants

/**
 * ViewModel especializado para o Dashboard do Cuidador na Ponte Familiar.
 *
 * Responsável por:
 * - Listagem dos pacientes acompanhados pelo cuidador
 * - Observação em tempo real (Firebase Data Connect -> Room -> UI) dos sinais vitais,
 *   hidratação e passos do paciente selecionado
 * - Gerenciamento de lista de compras / necessidades do paciente
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
    private val groceryRepository: GroceryRepository,
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

    private val _groceryItems = MutableStateFlow<List<GroceryListItemEntity>>(emptyList())
    val groceryItems: StateFlow<List<GroceryListItemEntity>> = _groceryItems.asStateFlow()

    private val _quickTemplates = MutableStateFlow(familyRepository.getQuickMessageTemplates())
    val quickTemplates: StateFlow<Map<String, MessageTemplate>> = _quickTemplates.asStateFlow()

    private var dashboardJob: Job? = null
    private var realtimeSubscriptionJob: Job? = null
    private var bindingsJob: Job? = null
    private var authListener: FirebaseAuth.AuthStateListener? = null

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

    private fun loadData(caregiverUid: String = currentUserId) {
        if (caregiverUid.isBlank() || caregiverUid.startsWith("00000000")) return

        bindingsJob?.cancel()
        bindingsJob = viewModelScope.launch {
            // 1. Baixar vínculos atualizados da nuvem (evita tela vazia se o cache local foi limpo)
            launch {
                try {
                    familyRepository.syncBindingsForCaregiver(caregiverUid)
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
                    dashboardJob?.cancel()
                    realtimeSubscriptionJob?.cancel()
                    _dashboard.value = CaregiverDashboardState()
                }
            }
        }
    }

    fun selectPatient(binding: FamilyBindingEntity) {
        dashboardJob?.cancel()
        realtimeSubscriptionJob?.cancel()

        viewModelScope.launch {
            try {
                vitalSignDao.deduplicateVitals()
            } catch (_: Exception) {}
        }

        // 1. Iniciar subscrição Realtime no Firebase Data Connect para atualizar Room em background
        realtimeSubscriptionJob = viewModelScope.launch {
            launch {
                try {
                    familyRepository.subscribePatientVitalSignsRealtime(binding.patientUserId).collect {
                        // Inserções no Room são feitas internamente pelo flow do repositório
                    }
                } catch (e: Exception) {
                    android.util.Log.w("CaregiverDashboardVM", "Subscrição realtime vitals falhou, mantendo offline: ${e.message}")
                }
            }
            launch {
                try {
                    familyRepository.subscribeFamilyMessagesRealtime(binding.patientUserId).collect {
                        // Inserções no Room são feitas internamente pelo flow do repositório
                    }
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

            // Disparar sincronização remota inicial dos dados mais recentes do paciente
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
        val uid = currentUserId
        if (uid.isNotBlank() && !uid.startsWith("00000000")) {
            viewModelScope.launch {
                try {
                    familyRepository.syncBindingsForCaregiver(uid)
                } catch (e: Exception) {
                    android.util.Log.e("CaregiverDashboardVM", "Erro ao sincronizar vínculos no refresh: ${e.message}")
                }
            }
        }
        val currentPatientId = _dashboard.value.patientUserId
        if (currentPatientId.isBlank()) return
        viewModelScope.launch {
            try {
                familyRepository.syncPatientDataForCaregiver(currentPatientId)
            } catch (e: Exception) {
                android.util.Log.e("CaregiverDashboardVM", "Erro ao sincronizar dados do paciente: ${e.message}")
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
        bindingsJob?.cancel()
        dashboardJob?.cancel()
        realtimeSubscriptionJob?.cancel()
    }
}
