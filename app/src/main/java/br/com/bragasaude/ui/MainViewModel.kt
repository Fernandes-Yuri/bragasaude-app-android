package br.com.bragasaude.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.bragasaude.domain.RiskManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RiskAlert(
    val type: String,
    val message: String
)

@HiltViewModel
class MainViewModel @Inject constructor(
    private val riskManager: RiskManager
) : ViewModel() {

    private val _activeRisk = MutableStateFlow<RiskAlert?>(null)
    val activeRisk = _activeRisk.asStateFlow()

    private val _permissionRequestSignal = MutableSharedFlow<Unit>()
    val permissionRequestSignal = _permissionRequestSignal.asSharedFlow()

    private val _notificationPromptSignal = MutableSharedFlow<Unit>()
    val notificationPromptSignal = _notificationPromptSignal.asSharedFlow()

    private val shownRisks = mutableSetOf<String>()

    init {
        viewModelScope.launch {
            riskManager.riskEvents.collectLatest { event ->
                // Só mostra se for um novo problema ou se a mensagem mudou
                val riskKey = "${event.type}_${event.message}"
                if (!shownRisks.contains(riskKey)) {
                    _activeRisk.value = RiskAlert(event.type, event.message)
                    shownRisks.add(riskKey)
                }
            }
        }
    }

    fun requestPermissions() {
        viewModelScope.launch {
            _permissionRequestSignal.emit(Unit)
        }
    }

    fun triggerNotificationPrompt() {
        viewModelScope.launch {
            _notificationPromptSignal.emit(Unit)
        }
    }

    fun triggerRisk(type: String, message: String) {
        val riskKey = "${type}_$message"
        if (!shownRisks.contains(riskKey)) {
            _activeRisk.value = RiskAlert(type, message)
            shownRisks.add(riskKey)
        }
    }

    fun dismissRisk() {
        _activeRisk.value = null
    }
}
