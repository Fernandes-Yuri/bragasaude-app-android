package br.com.bragasaude.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.bragasaude.data.remote.model.RemoteProfile
import br.com.bragasaude.data.remote.repository.ProfileRepository
import br.com.bragasaude.data.util.toRemote
import android.content.Context
import androidx.work.WorkManager
import br.com.bragasaude.data.local.BragaDatabase
import br.com.bragasaude.data.remote.repository.MedicationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val profileRepository: ProfileRepository,
    private val auth: FirebaseAuth,
    private val database: BragaDatabase,
    private val movementManager: br.com.bragasaude.data.util.MovementManager,
    private val medicationRepository: MedicationRepository,
    private val apiClient: br.com.bragasaude.data.remote.api.BragaApiClient,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    private val _profile = MutableStateFlow<RemoteProfile?>(null)
    val profile = _profile.asStateFlow()

    private val _phoneLinkSent = MutableStateFlow(false)
    val phoneLinkSent = _phoneLinkSent.asStateFlow()

    private val _phoneLinkWa = MutableStateFlow<String?>(null)
    val phoneLinkWa = _phoneLinkWa.asStateFlow()

    private val _phoneLinkLoading = MutableStateFlow(false)
    val phoneLinkLoading = _phoneLinkLoading.asStateFlow()

    private val _phoneLinkError = MutableStateFlow<String?>(null)
    val phoneLinkError = _phoneLinkError.asStateFlow()

    private val _phoneLinkSuccess = MutableStateFlow(false)
    val phoneLinkSuccess = _phoneLinkSuccess.asStateFlow()

    private val _isSaving = MutableStateFlow(false)
    val isSaving = _isSaving.asStateFlow()

    // TASK-UI-06: preferências da assistente de voz (AppPreferences)
    private val _voiceAssistantEnabled = MutableStateFlow(
        br.com.bragasaude.util.AppPreferences.isVoiceAssistantEnabled(appContext)
    )
    val voiceAssistantEnabled = _voiceAssistantEnabled.asStateFlow()

    private val _voiceConfirmationEnabled = MutableStateFlow(
        br.com.bragasaude.util.AppPreferences.isVoiceConfirmationEnabled(appContext)
    )
    val voiceConfirmationEnabled = _voiceConfirmationEnabled.asStateFlow()

    init {
        val userId = auth.currentUser?.uid ?: "00000000-0000-0000-0000-000000000000"
        viewModelScope.launch {
            profileRepository.getProfile(userId).collectLatest { entity ->
                _profile.value = entity?.toRemote()
            }
        }
    }

    fun updateVoiceAssistant(enabled: Boolean) {
        _voiceAssistantEnabled.value = enabled
        br.com.bragasaude.util.AppPreferences.setVoiceAssistantEnabled(appContext, enabled)
    }

    fun updateVoiceConfirmation(enabled: Boolean) {
        _voiceConfirmationEnabled.value = enabled
        br.com.bragasaude.util.AppPreferences.setVoiceConfirmationEnabled(appContext, enabled)
    }

    fun updateNotifications(enabled: Boolean) {
        val current = _profile.value ?: return
        saveProfile(current.copy(notificationsEnabled = enabled))
    }

    fun updateLocation(enabled: Boolean) {
        val current = _profile.value ?: return
        saveProfile(current.copy(locationEnabled = enabled))
    }

    fun updateEmergencyContact(name: String, relation: String, phone: String) {
        val current = _profile.value ?: return
        saveProfile(current.copy(
            emergencyContactName = name,
            emergencyContactRelation = relation,
            emergencyContactPhone = phone
        ))
    }

    fun updateStepGoal(goal: Int) {
        val current = _profile.value ?: return
        val sanitized = goal.coerceIn(500, 50000)
        saveProfile(current.copy(stepGoal = sanitized))
    }

    fun resetPhoneLinkState() {
        _phoneLinkSent.value = false
        _phoneLinkWa.value = null
        _phoneLinkLoading.value = false
        _phoneLinkError.value = null
        _phoneLinkSuccess.value = false
    }

    fun sendPhoneLinkOtp(phoneNumber: String) {
        viewModelScope.launch {
            _phoneLinkLoading.value = true
            _phoneLinkError.value = null
            _phoneLinkSent.value = false
            _phoneLinkWa.value = null
            val result = apiClient.sendOtp(phoneNumber, purpose = "PHONE_LINKING", channel = "WHATSAPP")
            _phoneLinkLoading.value = false
            result.fold(
                onSuccess = {
                    _phoneLinkSent.value = true
                    _phoneLinkWa.value = it.waLink
                },
                onFailure = {
                    _phoneLinkError.value = it.message ?: "Erro ao enviar código via WhatsApp"
                }
            )
        }
    }

    fun verifyPhoneLinkOtp(phoneNumber: String, code: String) {
        viewModelScope.launch {
            _phoneLinkLoading.value = true
            _phoneLinkError.value = null
            val result = apiClient.verifyOtp(phoneNumber, code, purpose = "PHONE_LINKING")
            _phoneLinkLoading.value = false
            result.fold(
                onSuccess = {
                    val current = _profile.value
                    if (current != null) {
                        saveProfile(current.copy(phone = phoneNumber))
                    }
                    _phoneLinkSuccess.value = true
                },
                onFailure = {
                    _phoneLinkError.value = it.message ?: "Código de verificação incorreto ou expirado"
                }
            )
        }
    }

    private fun saveProfile(updatedProfile: RemoteProfile) {
        viewModelScope.launch {
            _isSaving.value = true
            try {
                profileRepository.saveProfile(updatedProfile)
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isSaving.value = false
            }
        }
    }

    fun logout(onComplete: () -> Unit) {
        viewModelScope.launch {
            try {
                val userId = auth.currentUser?.uid
                if (userId != null) {
                    try {
                        medicationRepository.cancelAllUserAlarms(userId)
                    } catch (e: Exception) {
                        android.util.Log.w("SettingsViewModel", "Erro ao cancelar alarmes: ${e.message}")
                    }
                }

                withContext(Dispatchers.IO) {
                    try {
                        WorkManager.getInstance(appContext).cancelAllWork()
                    } catch (e: Exception) {
                        android.util.Log.w("SettingsViewModel", "Erro ao cancelar workers: ${e.message}")
                    }
                    database.clearAllTables()
                    try {
                        appContext.deleteDatabase(BragaDatabase.DATABASE_NAME)
                    } catch (e: Exception) {
                        android.util.Log.w("SettingsViewModel", "Erro ao apagar DB Room: ${e.message}")
                    }
                    try {
                        appContext.getSharedPreferences("braga_prefs", Context.MODE_PRIVATE)
                            .edit().clear().apply()
                    } catch (e: Exception) {
                        android.util.Log.w("SettingsViewModel", "Erro ao limpar prefs: ${e.message}")
                    }
                }

                auth.signOut()
                _profile.value = null
                onComplete()
            } catch (e: Exception) {
                e.printStackTrace()
                onComplete()
            }
        }
    }

    /**
     * MÓDULO 5 (LGPD): exclusão completa — nuvem → Room → Firebase Auth.
     * Mesma ordem usada em AuthViewModel.deleteAccount().
     */
    fun deleteAccount(onComplete: () -> Unit) {
        viewModelScope.launch {
            try {
                val user = auth.currentUser ?: run { onComplete(); return@launch }
                val userId = user.uid
                movementManager.flush()

                try {
                    medicationRepository.cancelAllUserAlarms(userId)
                } catch (e: Exception) {
                    android.util.Log.w("SettingsViewModel", "Erro ao cancelar alarmes no deleteAccount: ${e.message}")
                }

                val remoteDeleted = profileRepository.deleteProfileRemotely(userId)
                if (!remoteDeleted) {
                    android.util.Log.w("SettingsViewModel", "Delete remoto falhou para $userId.")
                }

                withContext(Dispatchers.IO) {
                    try {
                        WorkManager.getInstance(appContext).cancelAllWork()
                    } catch (e: Exception) {
                        android.util.Log.w("SettingsViewModel", "Erro ao cancelar workers no deleteAccount: ${e.message}")
                    }
                    database.clearAllTables()
                    try {
                        appContext.deleteDatabase(BragaDatabase.DATABASE_NAME)
                    } catch (e: Exception) {
                        android.util.Log.w("SettingsViewModel", "Erro ao apagar DB no deleteAccount: ${e.message}")
                    }
                    try {
                        appContext.getSharedPreferences("braga_prefs", Context.MODE_PRIVATE)
                            .edit().clear().apply()
                    } catch (e: Exception) {
                        android.util.Log.w("SettingsViewModel", "Erro ao limpar prefs no deleteAccount: ${e.message}")
                    }
                }
                try {
                    user.delete().await()
                } catch (e: Exception) {
                    android.util.Log.e("SettingsViewModel", "Erro ao excluir conta Auth: ${e.message}")
                    auth.signOut()
                }
                _profile.value = null
                onComplete()
            } catch (e: Exception) {
                e.printStackTrace()
                onComplete()
            }
        }
    }
}
