package br.com.bragasaude.ui.biometry

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.bragasaude.data.remote.model.RemoteBiometry
import br.com.bragasaude.data.remote.repository.BiometryRepository
import br.com.bragasaude.domain.HealthCalculators
import br.com.bragasaude.domain.HealthEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

import br.com.bragasaude.data.util.toRemote
import kotlinx.coroutines.flow.collectLatest

@HiltViewModel
class BiometryViewModel @Inject constructor(
    private val repository: BiometryRepository,
    private val healthEngine: HealthEngine,
    private val auth: FirebaseAuth
) : ViewModel() {

    private val _latestBiometry = MutableStateFlow<RemoteBiometry?>(null)
    val latestBiometry = _latestBiometry.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _alertMessage = MutableSharedFlow<String>()
    val alertMessage = _alertMessage.asSharedFlow()

    init {
        val userId = auth.currentUser?.uid ?: "00000000-0000-0000-0000-000000000000"
        viewModelScope.launch {
            repository.getBiometry(userId).collectLatest { entities ->
                _latestBiometry.value = entities.firstOrNull()?.toRemote()
            }
        }
    }

    fun loadLatestBiometry() {
        // Agora via Flow no init
    }

    fun saveBiometry(weight: Float, height: Float) {
        val userId = auth.currentUser?.uid ?: "00000000-0000-0000-0000-000000000000"
        val imc = HealthCalculators.calculateIMC(weight, height)
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val biometry = RemoteBiometry(
                    userId = userId,
                    weight = weight,
                    height = height,
                    imc = imc
                )
                repository.saveBiometry(biometry)
                
                // ANALISA BIOMETRIA (Sentinela)
                viewModelScope.launch {
                    val analysis = healthEngine.analyzeBiometry(userId, biometry)
                    analysis.alerts.firstOrNull()?.let { _alertMessage.emit(it) }
                }

                _latestBiometry.value = biometry
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isLoading.value = false
            }
        }
    }
}
