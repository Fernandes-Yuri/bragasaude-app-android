package br.com.bragasaude.ui.devices

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.bragasaude.data.local.BragaDatabase
import br.com.bragasaude.data.local.WearableReading
import br.com.bragasaude.domain.HealthReadingInput
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class HealthReadingsViewModel @Inject constructor(private val database: BragaDatabase, private val auth: FirebaseAuth) : ViewModel() {
    private val uid = auth.currentUser?.uid.orEmpty()
    private val _saving = MutableStateFlow(false)
    val saving = _saving.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()
    val heart = database.wearableReadingDao().observeRecent(uid, HealthReadingInput.HEART).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val oxygen = database.wearableReadingDao().observeRecent(uid, HealthReadingInput.OXYGEN).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    fun save(metric: String, raw: String, fromVoice: Boolean, onSaved: () -> Unit) {
        if (_saving.value) return
        val value = HealthReadingInput.value(metric, raw)
        if (value == null) { _error.value = "Confira o valor indicado no aparelho e tente novamente."; return }
        if (uid.isBlank() || auth.currentUser?.uid != uid) { _error.value = "Entre novamente na sua conta para salvar."; return }
        _saving.value = true
        _error.value = null
        viewModelScope.launch {
            try {
                database.wearableReadingDao().upsert(listOf(WearableReading(uid, "manual:" + UUID.randomUUID(), metric, value, System.currentTimeMillis(), if (fromVoice) HealthReadingInput.VOICE else HealthReadingInput.MANUAL, null)))
                onSaved()
            } catch (e: CancellationException) { throw e } catch (_: Exception) {
                _error.value = "Não foi possível salvar. Seu valor continua aqui para tentar novamente."
            } finally { _saving.value = false }
        }
    }
}
