package br.com.bragasaude.ui.devices

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.bragasaude.data.local.BragaDatabase
import br.com.bragasaude.data.util.HealthConnectManager
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class WearablesViewModel @Inject constructor(
    val manager: HealthConnectManager,
    database: BragaDatabase,
    auth: FirebaseAuth
) : ViewModel() {
    private val uid = auth.currentUser?.uid.orEmpty()
    val heartReadings = database.wearableReadingDao().observeRecent(uid, "HEART_RATE")
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val oxygenReadings = database.wearableReadingDao().observeRecent(uid, "OXYGEN_SATURATION")
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val syncState = manager.syncState
    private val _granted = MutableStateFlow<Set<String>>(emptySet())
    val granted = _granted.asStateFlow()
    private val _availability = MutableStateFlow(manager.availability())
    val availability = _availability.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            try {
                _availability.value = manager.availability()
                _granted.value = manager.grantedPermissions()
                _error.value = null
            } catch (e: CancellationException) { throw e } catch (_: Exception) {
                _error.value = "Não foi possível consultar as permissões. Tente novamente."
            }
        }
    }

    fun sync() {
        if (syncState.value.running) return
        viewModelScope.launch {
            manager.syncHealthConnectData(force = true)
            refresh()
        }
    }
}
