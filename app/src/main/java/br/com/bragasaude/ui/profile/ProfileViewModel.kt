package br.com.bragasaude.ui.profile

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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

    // Upload de avatar via gateway (com moderação server-side, igual ao PWA)
    private val _avatarUploading = MutableStateFlow(false)
    val avatarUploading = _avatarUploading.asStateFlow()
    private val _avatarMessage = MutableStateFlow<String?>(null)
    val avatarMessage = _avatarMessage.asStateFlow()
    fun consumeAvatarMessage() { _avatarMessage.value = null }

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

    private val _communityNickname = MutableStateFlow("")
    val communityNickname = _communityNickname.asStateFlow()
    private val _communitySaving = MutableStateFlow(false)
    val communitySaving = _communitySaving.asStateFlow()
    private val _communityError = MutableStateFlow<String?>(null)
    val communityError = _communityError.asStateFlow()

    fun loadCommunityNickname(onLoaded: (String) -> Unit = {}) {
        if (_communitySaving.value) return
        _communitySaving.value = true
        _communityError.value = null
        viewModelScope.launch {
            try {
                val nickname = apiClient.getCommunityNickname()
                _communityNickname.value = nickname
                onLoaded(nickname)
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (_: Exception) { _communityError.value = "Não foi possível carregar o nome comunitário. Tente novamente." }
            finally { _communitySaving.value = false }
        }
    }

    fun saveCommunityNickname(nickname: String, onSaved: () -> Unit) {
        if (_communitySaving.value) return
        _communitySaving.value = true
        _communityError.value = null
        viewModelScope.launch {
            try {
                val name = nickname.trim()
                require(name.isEmpty() || (name.length in 2..24 && name.any { it.isLetterOrDigit() }
                    && name.all { it.isLetterOrDigit() || it in " .'-" })) {
                    "Use de 2 a 24 caracteres: letras, números, espaços, ponto, apóstrofo ou hífen."
                }
                _communityNickname.value = apiClient.saveCommunityNickname(name)
                onSaved()
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) { _communityError.value = e.message ?: "Não foi possível salvar." }
            finally { _communitySaving.value = false }
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

    // Redimensiona para máx. 512px em JPEG 85 — espelha resizeAvatarFile do PWA.
    private fun resizeAvatar(context: Context, uri: Uri): Pair<ByteArray?, String> {
        val mime = context.contentResolver.getType(uri) ?: "image/jpeg"
        if (mime != "image/jpeg" && mime != "image/png" && mime != "image/webp") {
            return null to mime
        }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        val maxDim = maxOf(bounds.outWidth, bounds.outHeight).coerceAtLeast(1)
        var sample = 1
        while (maxDim / sample > 512) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val bmp = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, opts)
        } ?: return null to mime
        val scale = minOf(1f, 512f / maxOf(bmp.width, bmp.height).coerceAtLeast(1))
        val resized = if (scale < 1f) {
            Bitmap.createScaledBitmap(
                bmp,
                (bmp.width * scale).toInt().coerceAtLeast(1),
                (bmp.height * scale).toInt().coerceAtLeast(1),
                true
            )
        } else bmp
        val out = java.io.ByteArrayOutputStream()
        resized.compress(Bitmap.CompressFormat.JPEG, 85, out)
        if (resized !== bmp) bmp.recycle()
        return out.toByteArray() to "image/jpeg"
    }

    fun uploadAvatarPhoto(context: Context, uri: Uri) {
        val userId = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            _avatarUploading.value = true
            try {
                val (bytes, mime) = withContext(Dispatchers.IO) { resizeAvatar(context, uri) }
                if (bytes == null) {
                    _avatarMessage.value = "Formato não suportado — use JPEG, PNG ou WebP."
                    return@launch
                }
                val result = withContext(Dispatchers.IO) {
                    repository.uploadAvatarPhoto(userId, "avatar.jpg", mime, bytes)
                }
                val url = result.photoUrl
                if (!url.isNullOrBlank()) {
                    repository.persistServerAvatar(userId, url)
                    _profile.value = _profile.value?.copy(avatarIdentifier = null)
                    _customPhotoUri.value = url
                    _avatarMessage.value = "Foto de perfil atualizada."
                } else {
                    _avatarMessage.value = when (result.code) {
                        422 -> result.detail ?: "Foto não aprovada. Escolha outra imagem."
                        503 -> "Não foi possível verificar a foto agora. Tente novamente em instantes."
                        413 -> "A foto excede o limite de 5 MB."
                        415 -> "Formato não suportado — use JPEG, PNG ou WebP."
                        0 -> "Sem conexão. Tente novamente."
                        else -> result.detail ?: "Não foi possível enviar a foto de perfil."
                    }
                }
            } catch (e: Exception) {
                _avatarMessage.value = "Não foi possível enviar a foto de perfil."
            } finally {
                _avatarUploading.value = false
            }
        }
    }

    fun removeAvatarPhoto() {
        val userId = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            _avatarUploading.value = true
            try {
                if (withContext(Dispatchers.IO) { repository.removeAvatarPhoto(userId) }) {
                    _customPhotoUri.value = null
                    _avatarMessage.value = "Foto removida."
                } else {
                    _avatarMessage.value = "Não foi possível remover a foto."
                }
            } catch (e: Exception) {
                _avatarMessage.value = "Não foi possível remover a foto."
            } finally {
                _avatarUploading.value = false
            }
        }
    }

    fun refreshServerPhoto() {
        val userId = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            try {
                val remote = withContext(Dispatchers.IO) { repository.refreshPhotoFromServer(userId) }
                if (!remote.isNullOrBlank() && _customPhotoUri.value.isNullOrBlank()) {
                    _customPhotoUri.value = remote
                }
            } catch (_: Exception) { }
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
