package br.com.bragasaude.ui.devices

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.bragasaude.data.local.BragaDatabase
import br.com.bragasaude.data.local.VitalSignEntity
import br.com.bragasaude.data.remote.repository.VitalsRepository
import br.com.bragasaude.domain.HealthReadingHistory
import br.com.bragasaude.domain.HealthReadingInput
import br.com.bragasaude.domain.HealthReadingSaveResult
import br.com.bragasaude.domain.HealthReadingSaveStatus
import br.com.bragasaude.util.BragaConstants
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Date
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class HealthReadingsViewModel @Inject constructor(
    private val database: BragaDatabase,
    private val auth: FirebaseAuth,
    private val vitalsRepository: VitalsRepository?
) : ViewModel() {
    constructor(database: BragaDatabase, auth: FirebaseAuth) : this(database, auth, null)

    private val uid = auth.currentUser?.uid.orEmpty()
    private val _saving = MutableStateFlow(false)
    val saving = _saving.asStateFlow()
    private val _loaded = MutableStateFlow(false)
    val loaded = _loaded.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()
    private val _lastSaveResult = MutableStateFlow<HealthReadingSaveResult?>(null)
    val lastSaveResult = _lastSaveResult.asStateFlow()

    init {
        if (uid.isNotBlank()) {
            viewModelScope.launch {
                try {
                    if (vitalsRepository != null) {
                        vitalsRepository.migrateAndSyncLegacyManualReadings(uid) { auth.currentUser?.uid }
                    } else {
                        database.vitalSignDao().migrateLegacyManualReadings(uid)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {}
            }
        }
    }

    private fun readingHistory(metric: String) = combine(
        database.wearableReadingDao().observeRecent(uid, metric),
        database.vitalSignDao().getAll(uid)
    ) { wearable, vitals ->
        val activeUser = auth.currentUser?.uid.orEmpty()
        if (uid.isBlank() || activeUser != uid) {
            _loaded.value = false
            emptyList()
        } else {
            _loaded.value = true
            HealthReadingHistory.merge(uid, metric, wearable, vitals)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val heart = readingHistory(HealthReadingInput.HEART)
    val oxygen = readingHistory(HealthReadingInput.OXYGEN)

    fun save(metric: String, raw: String, fromVoice: Boolean, onSaved: () -> Unit) {
        saveWithResult(metric, raw, fromVoice) { result ->
            if (result.status == HealthReadingSaveStatus.SYNCED ||
                result.status == HealthReadingSaveStatus.SAVED_LOCALLY_PENDING_SYNC
            ) {
                onSaved()
            }
        }
    }

    fun saveWithResult(
        metric: String,
        raw: String,
        fromVoice: Boolean,
        onResult: (HealthReadingSaveResult) -> Unit = {}
    ) {
        if (_saving.value) return
        val value = HealthReadingInput.value(metric, raw)
        if (value == null) {
            val msg = "Confira o valor indicado no aparelho e tente novamente."
            _error.value = msg
            val invalid = HealthReadingSaveResult(HealthReadingSaveStatus.VALIDATION_ERROR, message = msg)
            _lastSaveResult.value = invalid
            onResult(invalid)
            return
        }
        val sessionOwner = uid
        if (sessionOwner.isBlank() || auth.currentUser?.uid != sessionOwner) {
            val msg = "Entre novamente na sua conta para salvar."
            _error.value = msg
            val unauth = HealthReadingSaveResult(HealthReadingSaveStatus.UNAUTHENTICATED, message = msg)
            _lastSaveResult.value = unauth
            onResult(unauth)
            return
        }
        _saving.value = true
        _error.value = null
        viewModelScope.launch {
            try {
                val result = if (vitalsRepository != null) {
                    vitalsRepository.saveManualHealthReading(
                        userId = sessionOwner,
                        metric = metric,
                        rawInput = raw,
                        fromVoice = fromVoice,
                        currentUserProvider = { auth.currentUser?.uid }
                    )
                } else {
                    val recordUuid = UUID.randomUUID().toString()
                    val origin = if (fromVoice) HealthReadingInput.VOICE else HealthReadingInput.MANUAL
                    val entity = VitalSignEntity(
                        remoteId = recordUuid,
                        userId = sessionOwner,
                        heartRate = if (metric == HealthReadingInput.HEART) value.toInt() else null,
                        oxygenSaturation = if (metric == HealthReadingInput.OXYGEN) value else null,
                        measuredAt = Date(),
                        status = "recorded",
                        pendingSync = sessionOwner != BragaConstants.GUEST_UID,
                        confirmedVia = origin
                    )
                    database.vitalSignDao().upsertManualReading(entity)
                    HealthReadingSaveResult(
                        status = HealthReadingSaveStatus.SAVED_LOCALLY_PENDING_SYNC,
                        remoteId = recordUuid,
                        message = "Leitura salva neste aparelho e aguardando envio."
                    )
                }
                if (auth.currentUser?.uid != sessionOwner) return@launch
                _lastSaveResult.value = result
                when (result.status) {
                    HealthReadingSaveStatus.SYNCED,
                    HealthReadingSaveStatus.SAVED_LOCALLY_PENDING_SYNC -> {
                        _error.value = null
                        onResult(result)
                    }
                    else -> {
                        _error.value = result.message ?: "Não foi possível salvar. Seu valor continua aqui para tentar novamente."
                        onResult(result)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                if (auth.currentUser?.uid == sessionOwner) {
                    val msg = "Não foi possível salvar. Seu valor continua aqui para tentar novamente."
                    _error.value = msg
                    val failure = HealthReadingSaveResult(HealthReadingSaveStatus.ERROR, message = msg)
                    _lastSaveResult.value = failure
                    onResult(failure)
                }
            } finally {
                _saving.value = false
            }
        }
    }
}
