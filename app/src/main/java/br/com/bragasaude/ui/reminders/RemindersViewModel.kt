package br.com.bragasaude.ui.reminders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.bragasaude.data.remote.model.RemoteMedication
import br.com.bragasaude.data.remote.repository.MedicationRepository
import br.com.bragasaude.data.util.toRemote
import dagger.hilt.android.lifecycle.HiltViewModel
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import br.com.bragasaude.domain.MedicationSchedule
import java.time.LocalDate
import java.time.LocalDateTime

data class MedicationDoseState(val time: String, val taken: Boolean, val available: Boolean)
data class MedicationReminder(val first: RemoteMedication, val second: Boolean, val third: Boolean, val doses: List<MedicationDoseState>)

@HiltViewModel
class RemindersViewModel @Inject constructor(
    private val repository: MedicationRepository,
    private val auth: FirebaseAuth
) : ViewModel() {

    /**
     * #12: carregamento honesto da Rotina de Medicamentos.
     *
     * Começa em `true` e só vira `false` quando o primeiro `combine` de
     * [medications] emite um valor real do Room. Antes, com `false` e um
     * stateIn inicial `emptyList()`, a tela piscava o empty-state/"Verificando
     * acesso aos horários" antes de o banco responder.
     */
    private val _firstLoadPending = MutableStateFlow(true)

    /** Gravação em andamento; não bloqueia o carregamento inicial (nem vice-versa). */
    private val _isSaving = MutableStateFlow(false)

    /** `true` enquanto o primeiro dado não chegou ou enquanto um save está em andamento. */
    val isLoading: StateFlow<Boolean> =
        combine(_firstLoadPending, _isSaving) { pending, saving -> pending || saving }
            .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    /**
     * #12: patientId reativo capturado uma vez da sessão no momento da criação do
     * ViewModel. Substitui o getter `auth.currentUser?.uid`, que consultava o
     * Firebase a cada recomposição.
     */
    private val _patientId = MutableStateFlow(auth.currentUser?.uid)
    val patientId: StateFlow<String?> = _patientId.asStateFlow()

    private val userId = auth.currentUser?.uid ?: br.com.bragasaude.util.BragaConstants.GUEST_UID

    /**
     * Emite um timestamp imediatamente e a cada virada de minuto exata do relógio,
     * evitando drift e garantindo renderização instantânea da lista.
     */
    private val minuteTicker = flow {
        emit(System.currentTimeMillis() / 60_000L)
        while (true) {
            val now = System.currentTimeMillis()
            val delayUntilNextMinute = 60_000L - (now % 60_000L)
            kotlinx.coroutines.delay(delayUntilNextMinute)
            emit(System.currentTimeMillis() / 60_000L)
        }
    }.distinctUntilChanged()

    init {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            repository.syncMedicationsFromServer(userId)
        }
    }

    val medications = combine(
        repository.getMedications(userId),
        repository.getLogsForToday(userId),
        minuteTicker
    ) { meds, logs, _ ->
        val now = LocalDateTime.now()
        val today = now.toLocalDate()
        meds.map { entity ->
            val times = MedicationSchedule.times(entity.scheduleTimes, entity.scheduleTime)
            val doses = times.map { time ->
                val key = MedicationSchedule.key(today, time)
                val taken = logs.any { log ->
                    log.medicationId == entity.id && (
                        log.scheduledFor == key ||
                        (log.scheduledFor != null && log.scheduledFor.startsWith(key)) ||
                        (log.careOsScheduledFor != null && log.careOsScheduledFor.startsWith(key)) ||
                        (log.scheduledFor == null && time == times.firstOrNull() &&
                            log.takenAt.toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDate() == today)
                    )
                }
                MedicationDoseState(time, taken, !taken && MedicationSchedule.available(today, time, now))
            }
            MedicationReminder(entity.toRemote(), doses.isNotEmpty() && doses.all { it.taken }, doses.any { it.available }, doses)
        }
    }.distinctUntilChanged()
        .onEach { _firstLoadPending.value = false }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _saveError = MutableStateFlow<String?>(null)
    val saveError = _saveError.asStateFlow()

    fun saveMedication(name: String, dosage: String, dosageMg: Double?, pillQuantity: Int?, time: String,
                       existing: RemoteMedication? = null, onSaved: () -> Unit = {}) {
        if (_isSaving.value) return
        val times = MedicationSchedule.parse(time)
        if ((dosageMg != null && (!dosageMg.isFinite() || dosageMg <= 0)) || (pillQuantity != null && pillQuantity <= 0) || name.isBlank() || times == null) {
            _saveError.value = "Informe o nome e um horário válido, como 08:00."
            return
        }
        _isSaving.value = true
        _saveError.value = null
        viewModelScope.launch {
            try {
                val med = RemoteMedication(
                    id = existing?.id,
                    notes = existing?.notes,
                    userId = userId,
                    name = name.trim(),
                    dosage = dosage,
                    dosageMg = dosageMg,
                    pillQuantity = pillQuantity,
                    scheduleTime = times!!.first(),
                    scheduleTimes = times.joinToString(",")
                )
                repository.saveMedication(med)
                onSaved()
            } catch (e: Exception) {
                _saveError.value = "Não foi possível salvar. Seus dados continuam aqui para tentar novamente."
            } finally {
                _isSaving.value = false
            }
        }
    }

    fun takeMedication(medId: String, time: String) {
        viewModelScope.launch {
            try {
                val res = repository.takeDose(userId, medId, time, actorId = userId)
                if (res is br.com.bragasaude.data.remote.api.BragaApiClient.TakeMedicationResult.Failure) return@launch
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun deleteMedication(medId: String) {
        viewModelScope.launch {
            repository.deleteMedication(userId, medId)
        }
    }
}
