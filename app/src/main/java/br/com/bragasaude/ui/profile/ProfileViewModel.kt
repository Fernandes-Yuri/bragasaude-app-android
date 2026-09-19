package br.com.bragasaude.ui.profile

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.bragasaude.data.local.BragaDatabase
import br.com.bragasaude.data.remote.model.RemoteProfile
import br.com.bragasaude.data.remote.repository.FeedbackRepository
import br.com.bragasaude.data.remote.repository.ProfileRepository
import br.com.bragasaude.data.remote.api.BragaApiClient
import br.com.bragasaude.data.util.WhatsAppLinkTotp
import br.com.bragasaude.data.util.toEntity
import br.com.bragasaude.data.util.toRemote
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import javax.inject.Inject
import br.com.bragasaude.util.BragaConstants

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val repository: ProfileRepository,
    private val feedbackRepository: FeedbackRepository,
    private val auth: FirebaseAuth,
    private val database: BragaDatabase,
    private val apiClient: BragaApiClient
) : ViewModel() {

    private val _profile = MutableStateFlow<RemoteProfile?>(null)
    val profile = _profile.asStateFlow()

    private val _userFeedbacks = MutableStateFlow<List<br.com.bragasaude.data.local.FeedbackEntity>>(emptyList())
    val userFeedbacks = _userFeedbacks.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()
    private val _saveError = MutableStateFlow<String?>(null)
    val saveError = _saveError.asStateFlow()

    // Foto customizada escolhida da galeria (persistida localmente pelo DAO)
    private val _customPhotoUri = MutableStateFlow<String?>(null)
    val customPhotoUri = _customPhotoUri.asStateFlow()

    init {
        val userId = auth.currentUser?.uid ?: BragaConstants.GUEST_UID
        
        viewModelScope.launch {
            repository.getProfile(userId).collectLatest { entity ->
                _profile.value = entity?.toRemote()
                _customPhotoUri.value = entity?.customPhotoUri
            }
        }

        viewModelScope.launch {
            feedbackRepository.getUserFeedbacks(userId).collectLatest { list ->
                _userFeedbacks.value = list
            }
        }
    }

    fun loadProfile() {
        val userId = auth.currentUser?.uid ?: BragaConstants.GUEST_UID
        viewModelScope.launch {
            // Puxa o perfil do backend: e dele que vem o segredo TOTP do vínculo
            // de WhatsApp (a fonte da verdade, D55). Sem isso, uma conta já
            // logada que tinha segredo nulo no Room continua sem ele — o botão
            // "Vincular meu WhatsApp" abre a conversa.
            try {
                repository.syncProfile(userId)
            } catch (_: Exception) { }

            repository.getProfile(userId).collectLatest { entity ->
                _profile.value = entity?.toRemote()
                _customPhotoUri.value = entity?.customPhotoUri
            }
        }
    }

    fun startSelfCareSetup(onReady: () -> Unit) {
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            val base = repository.getProfileOneShotLocal(uid)?.toRemote() ?: return@launch
            if (base.userRole == "CAREGIVER" && base.caregiverMode == "VIEWER_ONLY") {
                repository.saveProfile(base.copy(selfCareSetupPending = true))
                onReady()
            }
        }
    }

    fun saveProfile(
        input: ProfileInput,
        onSaved: () -> Unit = {},
        onComplete: () -> Unit
    ) {
        saveProfile(
            name = input.name,
            birthDate = input.birthDate,
            gender = input.gender,
            height = input.height,
            weight = input.weight,
            isSmoker = input.isSmoker,
            hasDiabetes = input.hasDiabetes,
            hasHypertension = input.hasHypertension,
            hasThyroid = input.hasThyroid,
            hasRenal = input.hasRenal,
            hasBone = input.hasBone,
            hasMuscular = input.hasMuscular,
            hydrationTargetMl = input.hydrationTargetMl,
            dailyCalorieTarget = input.dailyCalorieTarget,
            stepGoal = input.stepGoal,
            weightGoal = input.weightGoal,
            sleepStart = input.sleepStart,
            sleepEnd = input.sleepEnd,
            emergencyName = input.emergencyName,
            emergencyRelation = input.emergencyRelation,
            emergencyPhone = input.emergencyPhone,
            notificationsEnabled = input.notificationsEnabled,
            locationEnabled = input.locationEnabled,
            activityLevel = input.activityLevel,
            diabetesType = input.diabetesType,
            foodAllergies = input.foodAllergies,
            customFoodRestrictions = input.customFoodRestrictions,
            onSaved = onSaved,
            onComplete = onComplete
        )
    }

    fun saveProfile(
        name: String,
        birthDate: String?,
        gender: String?,
        height: Double?,
        weight: Double?,
        isSmoker: Boolean = false,
        hasDiabetes: Boolean = false,
        hasHypertension: Boolean = false,
        hasThyroid: Boolean = false,
        hasRenal: Boolean? = null,
        hasBone: Boolean? = null,
        hasMuscular: Boolean? = null,
        hydrationTargetMl: Int? = null,
        dailyCalorieTarget: Double? = null,
        stepGoal: Int? = null,
        weightGoal: Double? = null,
        sleepStart: String? = "22:00",
        sleepEnd: String? = "06:00",
        emergencyName: String? = null,
        emergencyRelation: String? = null,
        emergencyPhone: String? = null,
        notificationsEnabled: Boolean? = null,
        locationEnabled: Boolean? = null,
        activityLevel: String? = null,
        diabetesType: String? = null,
        foodAllergies: List<String> = emptyList(),
        customFoodRestrictions: String? = null,
        onSaved: () -> Unit = {},
        onComplete: () -> Unit
    ) {
        val userId = auth.currentUser?.uid ?: return
        
        viewModelScope.launch {
            _isLoading.value = true
            _saveError.value = null
            try {
                val base = repository.getProfileOneShotLocal(userId)?.toRemote() ?: RemoteProfile(id = userId)
                val currentConsent = base.consentAcceptedAt ?: java.time.Instant.now().toString()
                val currentUserRole = base.userRole ?: "PATIENT"
                val enableSelfCare = currentUserRole == "PATIENT" || base.caregiverMode == "HYBRID" || base.selfCareSetupPending
                require(name.isNotBlank()) { "Informe seu nome." }
                require(!enableSelfCare || (weight != null && weight in 20.0..350.0 && height != null && height in 50.0..250.0)) { "Informe peso e altura para configurar seu autocuidado." }
                val currentCaregiverMode = if (base.selfCareSetupPending) "HYBRID" else base.caregiverMode
                val newProfile = base.copy(
                    fullName = name,
                    birthDate = birthDate?.takeIf { it.isNotBlank() }?.let {
                        if ('/' in it) java.time.LocalDate.parse(it, java.time.format.DateTimeFormatter.ofPattern("dd/MM/uuuu")).toString() else it
                    },
                    gender = gender,
                    height = height,
                    weight = weight,
                    isSmoker = isSmoker,
                    hasDiabetes = hasDiabetes,
                    hasHypertension = hasHypertension,
                    hasThyroidIssue = hasThyroid,
                    hasRenalIssue = hasRenal ?: base.hasRenalIssue,
                    hasBoneIssue = hasBone ?: base.hasBoneIssue,
                    hasMuscularIssue = hasMuscular ?: base.hasMuscularIssue,
                    hydrationTargetMl = hydrationTargetMl ?: base.hydrationTargetMl,
                    dailyCalorieTarget = dailyCalorieTarget ?: base.dailyCalorieTarget,
                    stepGoal = stepGoal ?: base.stepGoal ?: 8000,
                    weightGoal = weightGoal ?: base.weightGoal,
                    sleepStartTime = sleepStart ?: "22:00",
                    sleepEndTime = sleepEnd ?: "06:00",
                    emergencyContactName = emergencyName,
                    emergencyContactRelation = emergencyRelation,
                    emergencyContactPhone = emergencyPhone,
                    consentAcceptedAt = currentConsent,
                    notificationsEnabled = notificationsEnabled ?: base.notificationsEnabled,
                    locationEnabled = locationEnabled ?: base.locationEnabled,
                    activityLevel = activityLevel ?: base.activityLevel,
                    diabetesType = diabetesType,
                    foodAllergies = foodAllergies,
                    customFoodRestrictions = customFoodRestrictions,
                    userRole = currentUserRole,
                    caregiverMode = currentCaregiverMode,
                    basicProfileComplete = true,
                    selfCareComplete = enableSelfCare || base.selfCareComplete,
                    selfCareSetupPending = false
                )
                repository.saveProfile(newProfile)
                onSaved()
                onComplete()
            } catch (e: Exception) {
                _saveError.value = e.message ?: "Não foi possível salvar seu perfil. Tente novamente."
                e.printStackTrace()
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun saveUserRole(userRole: String, onComplete: () -> Unit) {
        viewModelScope.launch {
            try {
                repository.saveUserRole(userRole)
            } catch (e: Exception) {
                android.util.Log.e("ProfileVM", "Erro ao salvar papel do usuário: ${e.message}")
            }
            onComplete()
        }
    }

    /**
     * Persiste o perfil COMPLETO do cuidador ao concluir o cadastro:
     * fullName + userRole = "CAREGIVER" + caregiverMode + consentAcceptedAt.
     * Isso garante que o AuthViewModel avalie _isProfileComplete = true e o
     * MainActivity siga direto para o painel do familiar, sem voltar ao cadastro.
     */
    fun saveCaregiverProfile(fullName: String, caregiverMode: String, onComplete: (Boolean) -> Unit) {
        val userId = auth.currentUser?.uid
        if (userId == null) {
            onComplete(false)
            return
        }
        viewModelScope.launch {
            try {
                val local = repository.getProfileOneShotLocal(userId)
                val base = local?.toRemote() ?: RemoteProfile(id = userId)
                val nowIso = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", java.util.Locale.getDefault()).format(java.util.Date())
                val profile = base.copy(
                    fullName = fullName,
                    userRole = "CAREGIVER",
                    caregiverMode = br.com.bragasaude.domain.ProfileOnboarding.modeAfterAddingCaregiving(base.selfCareComplete, caregiverMode),
                    basicProfileComplete = true,
                    consentAcceptedAt = base.consentAcceptedAt ?: nowIso
                )
                repository.saveProfile(profile)
                _profile.value = profile
                onComplete(true)
            } catch (e: Exception) {
                e.printStackTrace()
                onComplete(false)
            }
        }
    }

    fun updateAvatar(avatarId: String?, photoUri: String? = null) {
        val userId = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            try {
                repository.updateAvatar(userId, avatarId, photoUri)
                _profile.value = _profile.value?.copy(
                    avatarIdentifier = avatarId
                )
                _customPhotoUri.value = photoUri
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun saveConsent(onAccepted: () -> Unit) {
        val userId = auth.currentUser?.uid
        // Se ainda não autenticado (fluxo pré-login de consentimento LGPD), avança imediatamente
        if (userId == null) {
            onAccepted()
            return
        }
        val current = _profile.value ?: RemoteProfile(id = userId)
        val nowIso = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US).format(java.util.Date())
        val updated = current.copy(consentAcceptedAt = nowIso)
        
        viewModelScope.launch {
            _isLoading.value = true
            try {
                repository.saveProfile(updated)
                _profile.value = updated
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isLoading.value = false
                onAccepted() // Garante o avanço mesmo se a sincronização remota estiver offline
            }
        }
    }

    /**
     * MÓDULO 5 (LGPD — Direito ao Esquecimento): exclusão completa.
     * Ordem: nuvem (Postgres) → Room → Firebase Auth. Se o delete remoto falhar,
     * o usuário ainda sai do app mas os dados no banco ficam (logado pra retry futuro).
     */
    fun deleteProfile(onComplete: () -> Unit) {
        val user = auth.currentUser
        viewModelScope.launch {
            _isLoading.value = true
            try {
                if (user != null) {
                    val userId = user.uid
                    // 1. NUVEM PRIMEIRO
                    val remoteDeleted = repository.deleteProfileRemotely(userId)
                    if (!remoteDeleted) {
                        android.util.Log.w("ProfileViewModel", "Delete remoto falhou para $userId.")
                    }
                }

                // 2. ROOM LOCAL
                withContext(Dispatchers.IO) {
                    database.clearAllTables()
                }

                // 3. FIREBASE AUTH
                try {
                    user?.delete()?.await()
                } catch (e: Exception) {
                    android.util.Log.e("ProfileViewModel", "Erro ao excluir conta no Firebase Auth: ${e.message}")
                    auth.signOut()
                }

                _profile.value = null
                onComplete()
            } catch (e: Exception) {
                e.printStackTrace()
                onComplete()
            } finally {
                _isLoading.value = false
            }
        }
    }

    // Alias retrocompatível
    fun resetProfile(onComplete: () -> Unit) = deleteProfile(onComplete)

    // --- Vínculo de WhatsApp (fluxo TOTP temporário) ---
    // O fluxo antigo de OTP por texto foi removido: a Meta não aprova template
    // e o código nunca chegava. Agora é só abrir o WhatsApp com o código atual.

    private val _openWhatsAppLink = MutableStateFlow<String?>(null)
    val openWhatsAppLinkEvent = _openWhatsAppLink.asStateFlow()

    // Mensagem de feedback para o usuário quando o botão não consegue abrir o
    // link (antes o botão era mudo — "sem ação alguma").
    private val _whatsappLinkError = MutableStateFlow<String?>(null)
    val whatsappLinkError = _whatsappLinkError.asStateFlow()

    /**
     * Abre o WhatsApp com a mensagem de vínculo pronta (código TOTP atual).
     *
     * Se o segredo ainda não chegou (perfil do backend não sincronizou), não
     * fica mudo: avisa o usuário e dispara a sincronização — o retry fica fácil
     * (basta tocar de novo).
     */
    fun openWhatsAppLink(businessPhone: String = "5511967808252") {
        val profile = _profile.value
        val secret = profile?.whatsappTotpSecret
        if (secret.isNullOrBlank()) {
            _whatsappLinkError.value =
                "Seu vínculo ainda não está pronto. Aguarde um segundo e toque novamente."
            // Dispara a sincronização que traz o segredo do backend.
            val userId = auth.currentUser?.uid
                ?: BragaConstants.GUEST_UID
            viewModelScope.launch {
                try {
                    repository.syncProfile(userId)
                } catch (_: Exception) { }
            }
            return
        }
        _whatsappLinkError.value = null
        val code = WhatsAppLinkTotp.currentCode(secret)
        val text = Uri.encode("Olá Braga, eu desejo vincular meu número a minha conta. Esta é minha credencial: $code")
        _openWhatsAppLink.value = "https://wa.me/$businessPhone?text=$text"
    }

    /** Consome o evento (a tela já abriu o link). */
    fun consumeOpenWhatsAppLink() {
        _openWhatsAppLink.value = null
    }

    /** Consome a mensagem de erro (a tela já mostrou). */
    fun consumeWhatsappLinkError() {
        _whatsappLinkError.value = null
    }
}
