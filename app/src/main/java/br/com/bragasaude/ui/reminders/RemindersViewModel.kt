package br.com.bragasaude.ui.reminders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.bragasaude.data.remote.model.RemoteMedication
import br.com.bragasaude.data.remote.repository.MedicationRepository
import br.com.bragasaude.data.util.toRemote
import br.com.bragasaude.domain.GamificationActionType
import br.com.bragasaude.domain.GamificationEngine
import br.com.bragasaude.domain.XpGrantService
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
    private val xpGrantService: XpGrantService,
    private val auth: FirebaseAuth
) : ViewModel() {

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

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
                    log.medicationId == entity.id && (log.scheduledFor == key ||
                        (log.scheduledFor == null && time == times.firstOrNull() &&
                        log.takenAt.toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDate() == today))
                }
                MedicationDoseState(time, taken, !taken && MedicationSchedule.available(today, time, now))
            }
            MedicationReminder(entity.toRemote(), doses.isNotEmpty() && doses.all { it.taken }, doses.any { it.available }, doses)
        }
    }.distinctUntilChanged().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _saveError = MutableStateFlow<String?>(null)
    val saveError = _saveError.asStateFlow()

    fun saveMedication(name: String, dosage: String, dosageMg: Double?, pillQuantity: Int?, time: String,
                       existing: RemoteMedication? = null, onSaved: () -> Unit = {}) {
        if (_isLoading.value) return
        val times = MedicationSchedule.parse(time)
        if ((dosageMg != null && (!dosageMg.isFinite() || dosageMg <= 0)) || (pillQuantity != null && pillQuantity <= 0) || name.isBlank() || times == null) {
            _saveError.value = "Informe o nome e um horário válido, como 08:00."
            return
        }
        _isLoading.value = true
        _saveError.value = null
        viewModelScope.launch {
            _isLoading.value = true
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
                _isLoading.value = false
            }
        }
    }

    fun takeMedication(medId: String, time: String) {
        viewModelScope.launch {
            try {
                val res = repository.takeDose(userId, medId, time, actorId = userId)
                if (res is br.com.bragasaude.data.remote.api.BragaApiClient.TakeMedicationResult.Failure) return@launch

                // FASE 3 — XP quando a medicação é tomada dentro da janela de tolerância (±60 min)
                val med = medications.value.firstOrNull { it.first.id == medId }?.first
                val scheduleTime = time
                val now = Calendar.getInstance()
                val onTime = GamificationEngine.isMedicationOnTime(
                    scheduledTimeStr = scheduleTime,
                    takenHour = now.get(Calendar.HOUR_OF_DAY),
                    takenMinute = now.get(Calendar.MINUTE)
                )
                xpGrantService.grantXp(
                    userId = userId,
                    action = GamificationActionType.MEDICATION_TAKEN_ON_TIME,
                    isActionValid = onTime,
                    invalidReason = "Remédio tomado fora da janela de 1 hora — registrado mesmo assim."
                )
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
