package br.com.bragasaude.ui.auth

import br.com.bragasaude.domain.ProfileOnboarding
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.bragasaude.data.local.BragaDatabase
import br.com.bragasaude.data.remote.repository.ProfileRepository
import br.com.bragasaude.data.remote.model.RemoteProfile
import br.com.bragasaude.data.remote.sync.SyncManager
import br.com.bragasaude.data.util.toRemote
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import br.com.bragasaude.domain.LoginProfileResolution
import br.com.bragasaude.domain.resolveLoginProfile
import br.com.bragasaude.data.remote.model.ProfileLookup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import javax.inject.Inject
import android.content.Context

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val auth: FirebaseAuth,
    private val database: BragaDatabase,
    private val repository: ProfileRepository,
    private val syncManager: SyncManager,
    private val movementManager: br.com.bragasaude.data.util.MovementManager,
    private val medicationRepository: br.com.bragasaude.data.remote.repository.MedicationRepository,
    private val apiClient: br.com.bragasaude.data.remote.api.BragaApiClient,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    private val _sessionStatus = MutableStateFlow<AppSessionStatus>(AppSessionStatus.Initializing)
    val sessionStatus = _sessionStatus.asStateFlow()

    private val _isProfileComplete = MutableStateFlow<Boolean?>(null)
    val isProfileComplete = _isProfileComplete.asStateFlow()

    private val _hasAcceptedConsent = MutableStateFlow<Boolean?>(null)
    val hasAcceptedConsent = _hasAcceptedConsent.asStateFlow()

    private val _userRole = MutableStateFlow<String?>(null)
    val userRole = _userRole.asStateFlow()

    private val _needsSelfCare = MutableStateFlow(false)
    val needsSelfCare = _needsSelfCare.asStateFlow()
    private var profileJob: kotlinx.coroutines.Job? = null
    private var profileOwner: String? = null
    private val _profileError = MutableStateFlow<String?>(null)
    val profileError = _profileError.asStateFlow()

    private val _caregiverMode = MutableStateFlow<String?>(null)
    val caregiverMode = _caregiverMode.asStateFlow()

    private val _suggestPhoneLink = MutableStateFlow(false)
    val suggestPhoneLink = _suggestPhoneLink.asStateFlow()

    // Vínculo WhatsApp por TOTP: segredo "sob o capo" + número já vinculado.
    // O app calcula o código no ato do clique; ver wa_link_totp.py no gateway.
    private val _whatsappTotpSecret = MutableStateFlow<String?>(null)
    val whatsappTotpSecret = _whatsappTotpSecret.asStateFlow()
    private val _whatsappPhone = MutableStateFlow<String?>(null)
    val whatsappPhone = _whatsappPhone.asStateFlow()

    // Plano B OTP (sem template): código só existe após sucesso real.
    private val _phoneLinkSent = MutableStateFlow(false)
    val phoneLinkSent = _phoneLinkSent.asStateFlow()
    private val _phoneLinkWa = MutableStateFlow<String?>(null)
    val phoneLinkWa = _phoneLinkWa.asStateFlow()

    data class PendingRegistration(
        val email: String,
        val password: String,
        val phoneNumber: String,
        val channel: String
    )
    private var _pendingRegistration: PendingRegistration? = null

    private val _authState = MutableStateFlow<AuthState>(AuthState.Idle)
    val authState = _authState.asStateFlow()

    sealed class AuthState {
        object Idle : AuthState()
        object Loading : AuthState()
        data class Error(val message: String) : AuthState()
        object AccountNotFound : AuthState()
        object AccountAlreadyExists : AuthState()
        data class GoogleAccountExists(val message: String) : AuthState()
        object EmailVerificationSent : AuthState()
        object EmailNotVerified : AuthState()
        object PasswordResetSent : AuthState()
        data class OtpSent(val channel: String = "EMAIL", val waLink: String? = null) : AuthState()
        object OtpVerified : AuthState()
        object RegistrationSuccess : AuthState()
        object PasswordReset : AuthState()
        object PhoneLinkedSuccess : AuthState()
    }

    private val authStateListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
        val user = firebaseAuth.currentUser
        if (user != null) {
            _sessionStatus.value = AppSessionStatus.Authenticated(user.uid)
            viewModelScope.launch {
                syncManager.syncUserData(user.uid, force = false)
            }
            checkProfile(user.uid)
        } else {
            profileJob?.cancel()
            profileOwner = null
            _profileError.value = null
            _userRole.value = null
            _caregiverMode.value = null
            _needsSelfCare.value = false
            _sessionStatus.value = AppSessionStatus.NotAuthenticated
            _isProfileComplete.value = null
            _hasAcceptedConsent.value = null
        }
    }

    init {
        auth.addAuthStateListener(authStateListener)
        val initialUser = auth.currentUser
        if (initialUser != null) {
            _sessionStatus.value = AppSessionStatus.Authenticated(initialUser.uid)
            checkProfile(initialUser.uid)
        } else {
            _sessionStatus.value = AppSessionStatus.NotAuthenticated
        }
    }

    override fun onCleared() {
        super.onCleared()
        auth.removeAuthStateListener(authStateListener)
    }

    fun retryProfile() { auth.currentUser?.uid?.let { checkProfile(it) } }

    private fun checkProfile(userId: String) {
        profileJob?.cancel()
        if (profileOwner != userId) {
            _isProfileComplete.value = null
            _hasAcceptedConsent.value = null
            _userRole.value = null
            _caregiverMode.value = null
            _needsSelfCare.value = false
        }
        profileOwner = userId
        _profileError.value = null
        profileJob = viewModelScope.launch {
            var remote: ProfileLookup? = null
            val renderLock = Mutex()
            suspend fun render(update: ProfileLookup? = null) = renderLock.withLock {
                if (update != null) remote = update
                currentCoroutineContext().ensureActive()
                if (auth.currentUser?.uid != userId) return@withLock
                // Read the current row instead of applying an older Flow emission.
                val local = repository.getProfileOneShotLocal(userId)
                currentCoroutineContext().ensureActive()
                if (auth.currentUser?.uid != userId) return@withLock
                // Segredo TOTP e número vinculado (vínculo WhatsApp temporário).
                _whatsappTotpSecret.value = local?.whatsappTotpSecret
                _whatsappPhone.value = local?.whatsappPhone
                when (val resolution = resolveLoginProfile(local, remote)) {
                    LoginProfileResolution.Loading -> {
                        _isProfileComplete.value = null
                        _hasAcceptedConsent.value = null
                        _profileError.value = null
                    }
                    is LoginProfileResolution.Unavailable -> {
                        _isProfileComplete.value = null
                        _hasAcceptedConsent.value = local?.consentAcceptedAt?.let { true }
                        _profileError.value = resolution.message
                    }
                    is LoginProfileResolution.Ready -> {
                        val profile = resolution.profile
                        _hasAcceptedConsent.value = profile?.consentAcceptedAt != null
                        _userRole.value = profile?.userRole
                        _caregiverMode.value = profile?.caregiverMode
                        _needsSelfCare.value = ProfileOnboarding.needsSelfCare(profile?.userRole,
                            profile?.caregiverMode, profile?.basicProfileComplete == true, profile?.selfCareComplete == true)
                        _isProfileComplete.value = ProfileOnboarding.isComplete(profile?.userRole,
                            profile?.caregiverMode, profile?.basicProfileComplete == true, profile?.selfCareComplete == true)
                        _profileError.value = null
                    }
                }
            }
            try {
                // Room observation stays alive when the network fails; it never cancels the refresh.
                launch {
                    try { repository.getProfile(userId).collect { render() } }
                    catch (e: CancellationException) { throw e }
                    catch (_: Exception) {
                        if (auth.currentUser?.uid == userId) _profileError.value = "Não foi possível ler seu perfil. Tente novamente."
                    }
                }
                render()
                val result = try {
                    repository.refreshProfileForLogin(userId)
                } catch (e: CancellationException) { throw e }
                catch (_: Exception) { ProfileLookup.Unavailable() }
                render(result)
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) {
                if (auth.currentUser?.uid == userId) {
                    _profileError.value = "Não foi possível ler seu perfil. Tente novamente."
                }
            }
        }
    }

    fun setConsentAccepted() {
        _hasAcceptedConsent.value = true
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            val base = repository.getProfileOneShotLocal(uid)?.toRemote() ?: RemoteProfile(id = uid)
            repository.saveProfile(base.copy(consentAcceptedAt = base.consentAcceptedAt ?: java.time.Instant.now().toString()))
        }
    }

    fun setUserRole(role: String) {
        val userId = auth.currentUser?.uid ?: return
        _userRole.value = role

        viewModelScope.launch {
            try {
                // Blindagem: usar o perfil local como base. Se ainda não existir perfil
                // (onboarding), cria entidade mínima com o papel — SEM nome placeholder,
                // para _isProfileComplete continuar false até o cadastro real.
                val local = repository.getProfileOneShotLocal(userId)
                if (local != null) {
                    repository.saveProfile(local.toRemote().copy(userRole = role))
                } else {
                    val consent = if (_hasAcceptedConsent.value == true) java.util.Date() else null
                    repository.saveUserRole(role, consentAcceptedAt = consent)
                }
            } catch (e: Exception) {
                android.util.Log.e("AuthViewModel", "Erro ao salvar userRole: ${e.message}")
            }
        }
    }

    /**
     * MÓDULO 6 (Fase D): define o modo do cuidador.
     * "HYBRID" = acompanha familiar + monitora própria saúde (fluxo completo)
     * "VIEWER_ONLY" = só acompanha familiar (sem telas de saúde pessoal)
     *
     * Blindagem: o perfil criado/atualizado contém explicitamente
     * userRole = "CAREGIVER" e caregiverMode = mode, sem nome placeholder —
     * assim o papel nunca fica nulo no banco e o cadastro do cuidador
     * (nome + código) continua pendente até ser concluído.
     */
    fun setCaregiverMode(mode: String) {
        val userId = auth.currentUser?.uid ?: return
        _caregiverMode.value = mode
        _userRole.value = "CAREGIVER"

        viewModelScope.launch {
            try {
                val local = repository.getProfileOneShotLocal(userId)
                if (local != null) {
                    repository.saveProfile(
                        local.toRemote().copy(userRole = "CAREGIVER", caregiverMode = mode)
                    )
                } else {
                    val consent = if (_hasAcceptedConsent.value == true) java.util.Date() else null
                    repository.saveCaregiverModeLocal(userId, mode, consentAcceptedAt = consent)
                }
            } catch (e: Exception) {
                android.util.Log.e("AuthViewModel", "Erro ao salvar caregiverMode: ${e.message}")
            }
        }
    }

    /**
     * Volta da tela de dados do cuidador para a escolha de modo:
     * limpa o modo (memória + banco) sem tocar no nome do usuário.
     */
    fun clearCaregiverMode() {
        _caregiverMode.value = null
        val userId = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            try {
                repository.saveCaregiverModeLocal(userId, null)
            } catch (e: Exception) {
                android.util.Log.e("AuthViewModel", "Erro ao limpar caregiverMode: ${e.message}")
            }
        }
    }

    /**
     * Volta da escolha de modo/papel para a seleção de papel: limpa papel e modo
     * de um perfil ainda incompleto (sem nome).
     */
    fun clearOnboardingRole() {
        _userRole.value = ""
        _caregiverMode.value = null
        val userId = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            try {
                repository.clearRoleFromStubProfile(userId)
            } catch (e: Exception) {
                android.util.Log.e("AuthViewModel", "Erro ao limpar papel do onboarding: ${e.message}")
            }
        }
    }

    /**
     * Extrai o e-mail do payload (parte central) de um Google ID Token (JWT).
     * O token tem formato `header.payload.signature`, tudo em base64url.
     * Retorna `null` se não for possível decodificar.
     */
    private fun extractEmailFromIdToken(idToken: String): String? {
        return try {
            val parts = idToken.split(".")
            if (parts.size < 2) return null
            val payloadJson = String(
                android.util.Base64.decode(parts[1], android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING or android.util.Base64.NO_WRAP),
                Charsets.UTF_8
            )
            org.json.JSONObject(payloadJson).optString("email", "").takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            android.util.Log.e("AuthViewModel", "Falha ao decodificar idToken: ${e.message}")
            null
        }
    }

    /**
     * Verifica se uma conta Firebase Auth JÁ EXISTE para aquele e-mail.
     * Retorna true se existir, false caso contrário.
     */
    private suspend fun accountExists(email: String): Boolean {
        return try {
            val result = auth.fetchSignInMethodsForEmail(email).await()
            !result.signInMethods.isNullOrEmpty()
        } catch (e: Exception) {
            android.util.Log.e("AuthViewModel", "Erro ao checar existência da conta: ${e.message}")
            false
        }
    }

    /**
     * Login / Cadastro fluido com Google: autentica diretamente sem bloqueio prévio.
     * Se for o primeiro acesso, o Firebase cria a conta automaticamente.
     * Se já existir, faz o login e sincroniza os dados do usuário.
     */
    /**
     * Login / Cadastro com Google: autentica diretamente pelo Firebase Auth.
     * Envia e-mail de boas-vindas e sugere vincular telefone (opcional).
     * Sem OTP obrigatório para entrar.
     */
    fun signInWithGoogle(idToken: String) {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            try {
                val credential = GoogleAuthProvider.getCredential(idToken, null)
                val result = auth.signInWithCredential(credential).await()
                val user = result.user
                val userId = user?.uid
                if (userId != null) {
                    syncManager.downloadAllUserData(userId)

                    // 1. Envio de e-mail de boas-vindas assíncrono para a conta Google
                    val email = user.email
                    val displayName = user.displayName
                    if (!email.isNullOrBlank()) {
                        apiClient.sendWelcomeEmail(email, displayName)
                    }

                    // 2. Verifica se o usuário tem telefone cadastrado; se não tiver, sugere WhatsApp (opcional)
                    val profile = repository.getProfileOneShotLocal(userId)
                    val hasPhone = !profile?.phone.isNullOrBlank()
                    if (!hasPhone) {
                        _suggestPhoneLink.value = true
                    }

                    _authState.value = AuthState.Idle
                } else {
                    _authState.value = AuthState.Error("Não foi possível autenticar com o Google.")
                }
            } catch (e: Exception) {
                _authState.value = AuthState.Error(e.message ?: "Erro ao autenticar com o Google")
            }
        }
    }

    fun dismissPhoneLinkSuggestion() {
        _suggestPhoneLink.value = false
    }

    /**
     * Envia OTP via WhatsApp para vincular telefone à conta Google (opcional).
     */
    fun sendPhoneLinkOtp(phoneNumber: String) {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            _phoneLinkSent.value = false
            _phoneLinkWa.value = null
            val result = apiClient.sendOtp(phoneNumber, purpose = "PHONE_LINKING", channel = "WHATSAPP")
            result.fold(
                onSuccess = {
                    _phoneLinkSent.value = true
                    _phoneLinkWa.value = it.waLink
                    _authState.value = AuthState.OtpSent("WHATSAPP", it.waLink)
                },
                onFailure = { _authState.value = AuthState.Error(it.message ?: "Erro ao enviar código WhatsApp") }
            )
        }
    }

    /**
     * Valida OTP e salva o telefone vinculado ao perfil do usuário Google.
     */
    fun verifyPhoneLinkOtp(phoneNumber: String, code: String) {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            val result = apiClient.verifyOtp(phoneNumber, code, purpose = "PHONE_LINKING")
            result.fold(
                onSuccess = {
                    val userId = auth.currentUser?.uid
                    if (userId != null) {
                        val current = repository.getProfileOneShotLocal(userId)
                        if (current != null) {
                            repository.saveProfile(current.toRemote().copy(phone = phoneNumber))
                        }
                    }
                    _suggestPhoneLink.value = false
                    _authState.value = AuthState.PhoneLinkedSuccess
                },
                onFailure = { _authState.value = AuthState.Error(it.message ?: "Código de verificação incorreto") }
            )
        }
    }

    fun signUpWithGoogle(idToken: String) {
        signInWithGoogle(idToken)
    }

    fun clearAuthState() {
        _authState.value = AuthState.Idle
    }

    // ==============================================================
    // CADASTRO DIRETO COM OTP OBRIGATÓRIO (WHATSAPP OU EMAIL)
    // ==============================================================

    /**
     * Inicia cadastro direto: valida conflitos de conta (Google vs Direto vs Deletada)
     * e dispara OTP obrigatório via canal escolhido (WHATSAPP ou EMAIL).
     */
    fun signUpWithEmail(email: String, password: String, phoneNumber: String = "", channel: String = "EMAIL") {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            try {
                // 1. Validação inteligente de contas no backend
                val checkRes = apiClient.checkAccount(email)
                if (checkRes.isSuccess) {
                    val accountInfo = checkRes.getOrNull()
                    if (accountInfo != null) {
                        if (!accountInfo.canCreateDirect && accountInfo.provider == "GOOGLE") {
                            _authState.value = AuthState.GoogleAccountExists(accountInfo.message)
                            return@launch
                        }
                        if (!accountInfo.canCreateDirect && accountInfo.provider == "DIRECT") {
                            _authState.value = AuthState.AccountAlreadyExists
                            return@launch
                        }
                    }
                } else {
                    // Fallback
                    if (accountExists(email)) {
                        _authState.value = AuthState.AccountAlreadyExists
                        return@launch
                    }
                }

                if (channel == "WHATSAPP" && phoneNumber.isBlank()) {
                    _authState.value = AuthState.Error("Informe seu número de WhatsApp para receber o código de validação.")
                    return@launch
                }

                // Salva estado pendente
                _pendingRegistration = PendingRegistration(email, password, phoneNumber, channel)

                // 2. Dispara OTP obrigatório pelo canal escolhido
                val identifier = if (channel == "WHATSAPP") phoneNumber else email
                val otpRes = apiClient.sendOtp(identifier, purpose = "REGISTRATION", channel = channel)
                otpRes.fold(
                    onSuccess = { _authState.value = AuthState.OtpSent(channel, it.waLink) },
                    onFailure = { _authState.value = AuthState.Error(it.message ?: "Erro ao enviar código de verificação") }
                )
            } catch (e: Exception) {
                _authState.value = AuthState.Error(e.message ?: "Erro ao iniciar cadastro")
            }
        }
    }

    /**
     * Valida o OTP obrigatório e consolida a criação da conta direta no sistema.
     */
    fun verifyRegistrationOtp(code: String) {
        val pending = _pendingRegistration
        if (pending == null) {
            _authState.value = AuthState.Error("Sessão de cadastro expirada. Preencha os dados novamente.")
            return
        }

        viewModelScope.launch {
            _authState.value = AuthState.Loading
            try {
                val identifier = if (pending.channel == "WHATSAPP") pending.phoneNumber else pending.email
                val verifyRes = apiClient.verifyOtp(identifier, code, purpose = "REGISTRATION")
                if (verifyRes.isFailure) {
                    _authState.value = AuthState.Error(verifyRes.exceptionOrNull()?.message ?: "Código de verificação incorreto")
                    return@launch
                }

                // OTP válido! Cria conta oficial no Firebase Auth
                val result = auth.createUserWithEmailAndPassword(pending.email, pending.password).await()
                val user = result.user
                val userId = user?.uid

                if (userId != null) {
                    val profile = RemoteProfile(
                        id = userId,
                        fullName = "",
                        phone = if (pending.phoneNumber.isNotBlank()) pending.phoneNumber else null
                    )
                    repository.saveProfile(profile)
                    syncManager.downloadAllUserData(userId)

                    // Envia e-mail de boas-vindas
                    apiClient.sendWelcomeEmail(pending.email)

                    _pendingRegistration = null
                    _authState.value = AuthState.RegistrationSuccess
                } else {
                    _authState.value = AuthState.Error("Conta validada, mas falhou ao iniciar sessão. Faça login.")
                }
            } catch (e: Exception) {
                _authState.value = AuthState.Error(e.message ?: "Erro ao concluir cadastro")
            }
        }
    }


    /**
     * Login por e-mail/senha direto e sem bloqueio.
     */
    fun signInWithEmail(email: String, password: String) {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            try {
                val result = auth.signInWithEmailAndPassword(email, password).await()
                val user = result.user
                if (user != null) {
                    val userId = user.uid
                    syncManager.downloadAllUserData(userId)
                    _authState.value = AuthState.Idle
                }
            } catch (e: Exception) {
                _authState.value = AuthState.Error(e.message ?: "Erro ao entrar")
            }
        }
    }

    /**
     * Reenvia o e-mail de verificação para o endereço fornecido.
     * Exige que o usuário faça login temporário (senha) para receber o link.
     */
    fun resendEmailVerification(email: String, password: String) {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            try {
                val result = auth.signInWithEmailAndPassword(email, password).await()
                result.user?.sendEmailVerification()?.await()
                auth.signOut()
                _authState.value = AuthState.EmailVerificationSent
            } catch (e: Exception) {
                _authState.value = AuthState.Error(e.message ?: "Erro ao reenviar verificação")
            }
        }
    }

    /**
     * Envia código OTP de 6 dígitos para o e-mail ou telefone fornecido.
     * Suporta canais EMAIL e WHATSAPP.
     */
    fun sendOtpCode(identifier: String, channel: String = "EMAIL") {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            val result = apiClient.sendOtp(identifier, purpose = "PASSWORD_RESET", channel = channel)
            result.fold(
                onSuccess = { _authState.value = AuthState.OtpSent(channel, it.waLink) },
                onFailure = { _authState.value = AuthState.Error(it.message ?: "Erro ao enviar código") }
            )
        }
    }

    /**
     * Verifica o código OTP de 6 dígitos digitado pelo usuário.
     * Retorna o resetToken para uso no próximo passo.
     */
    fun verifyOtpCode(identifier: String, code: String) {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            val result = apiClient.verifyOtp(identifier, code, purpose = "PASSWORD_RESET")
            result.fold(
                onSuccess = { resetToken ->
                    if (resetToken != null) {
                        _pendingResetIdentifier = identifier
                        _pendingResetToken = resetToken
                        _authState.value = AuthState.OtpVerified
                    } else {
                        _authState.value = AuthState.Error("Código inválido")
                    }
                },
                onFailure = { _authState.value = AuthState.Error(it.message ?: "Código inválido") }
            )
        }
    }

    private var _pendingResetIdentifier: String? = null
    private var _pendingResetToken: String? = null

    /**
     * Redefine a senha usando o resetToken obtido na verificação OTP.
     * Também atualiza no Firebase Auth se a conta existir.
     */
    fun resetPasswordWithOtp(newPassword: String) {
        val identifier = _pendingResetIdentifier
        val resetToken = _pendingResetToken
        if (identifier == null || resetToken == null) {
            _authState.value = AuthState.Error("Sessão de redefinição expirada. Tente novamente.")
            return
        }
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            val result = apiClient.resetPassword(identifier, resetToken, newPassword)
            result.fold(
                onSuccess = {
                    _pendingResetIdentifier = null
                    _pendingResetToken = null
                    _authState.value = AuthState.PasswordReset
                },
                onFailure = { _authState.value = AuthState.Error(it.message ?: "Erro ao redefinir senha") }
            )
        }
    }

    /**
     * Limpa tokens pendentes de redefinição.
     */
    fun clearPendingReset() {
        _pendingResetIdentifier = null
        _pendingResetToken = null
    }

    fun signInAsGuest() {
        val guestUid = "00000000-0000-0000-0000-000000000000"
        viewModelScope.launch {
            val prefs = appContext.getSharedPreferences("braga_prefs", Context.MODE_PRIVATE)
            val acceptedBeforeAuth = prefs.getBoolean("terms_accepted_before_auth", false)
            val consentDate = if (acceptedBeforeAuth) {
                java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", java.util.Locale.getDefault()).format(java.util.Date())
            } else {
                null
            }
            repository.saveProfile(
                br.com.bragasaude.data.remote.model.RemoteProfile(
                    id = guestUid,
                    fullName = "Visitante",
                    currentLevel = 2,
                    currentStreak = 3,
                    currentXp = 45,
                    totalXp = 120,
                    stepGoal = 8000,
                    hydrationTargetMl = 2000,
                    consentAcceptedAt = consentDate
                )
            )
            _hasAcceptedConsent.value = acceptedBeforeAuth
            _isProfileComplete.value = true
            _sessionStatus.value = AppSessionStatus.Authenticated(guestUid)
        }
    }

    fun setProfileComplete() {
        auth.currentUser?.uid?.let { checkProfile(it) }
    }

    fun setProfileIncomplete() {
        _isProfileComplete.value = false
    }

    fun getUserId(): String? {
        return auth.currentUser?.uid
    }

    suspend fun signOut() {
        movementManager.flush()
        
        // Cancelar todos os alarmes de medicação antes do logout (TASK-MED-02)
        val userId = auth.currentUser?.uid
        if (userId != null) {
            try {
                medicationRepository.cancelAllUserAlarms(userId)
            } catch (e: Exception) {
                android.util.Log.w("AuthViewModel", "Erro ao cancelar alarmes no signOut: ${e.message}")
            }
        }

        // Cancelar todos os workers pendentes (SyncWorker, HydrationReminderWorker)
        withContext(Dispatchers.IO) {
            try {
                androidx.work.WorkManager.getInstance(appContext).cancelAllWork()
            } catch (e: Exception) {
                android.util.Log.w("AuthViewModel", "Erro ao cancelar workers: ${e.message}")
            }
            
            // Limpar todas as tabelas locais (exceto catálogo de alimentos persistido)
            database.clearAllTables()
            
            // Apagar o arquivo inteiro do banco Room para garantir limpeza total
            try {
                appContext.deleteDatabase(BragaDatabase.DATABASE_NAME)
            } catch (e: Exception) {
                android.util.Log.w("AuthViewModel", "Erro ao apagar DB Room: ${e.message}")
            }
            
            // Limpar SharedPreferences com dados de sessão
            try {
                appContext.getSharedPreferences("braga_prefs", Context.MODE_PRIVATE)
                    .edit().clear().apply()
            } catch (e: Exception) {
                android.util.Log.w("AuthViewModel", "Erro ao limpar prefs: ${e.message}")
            }
        }
        
        auth.signOut()
        
        _sessionStatus.value = AppSessionStatus.NotAuthenticated
        _isProfileComplete.value = null
        _hasAcceptedConsent.value = null
        _userRole.value = null
        _caregiverMode.value = null
    }

    /**
     * MÓDULO 5 (LGPD — Direito ao Esquecimento): exclusão completa da conta.
     *
     * Ordem é crítica:
     *   1. `repository.deleteProfileRemotely(userId)` — apaga Postgres ENQUANTO o
     *      token de Auth ainda é válido (as mutations têm `@auth(expr: "auth.uid == userId")`).
     *      Falha de rede aqui é tolerada (log + retry via SyncWorker), pois LGPD não
     *      pode ficar travada por conectividade — mas o usuário precisa saber que
     *      ainda resta apagar remoto.
     *   2. `database.clearAllTables()` — apaga Room local.
     *   3. `user.delete()` — apaga Firebase Auth. DEPOIS disso não dá mais para chamar
     *      as mutations remotas (perdemos a sessão).
     */
    suspend fun deleteAccount() {
        val user = auth.currentUser ?: return
        val userId = user.uid
        movementManager.flush()

        // Cancelar alarmes de medicação antes de deletar conta (TASK-MED-02)
        try {
            medicationRepository.cancelAllUserAlarms(userId)
        } catch (e: Exception) {
            android.util.Log.w("AuthViewModel", "Erro ao cancelar alarmes no deleteAccount: ${e.message}")
        }

        // 1. NUVEM PRIMEIRO (LGPD)
        val remoteDeleted = repository.deleteProfileRemotely(userId)
        if (!remoteDeleted) {
            android.util.Log.w(
                "AuthViewModel",
                "Delete remoto falhou para $userId. Dados Postgres podem persistir até novo retry manual."
            )
        }

        // 2. ROOM LOCAL + WORKERS + PREFERENCES
        withContext(Dispatchers.IO) {
            try {
                androidx.work.WorkManager.getInstance(appContext).cancelAllWork()
            } catch (e: Exception) {
                android.util.Log.w("AuthViewModel", "Erro ao cancelar workers no deleteAccount: ${e.message}")
            }
            database.clearAllTables()
            try {
                appContext.deleteDatabase(BragaDatabase.DATABASE_NAME)
            } catch (e: Exception) {
                android.util.Log.w("AuthViewModel", "Erro ao apagar DB no deleteAccount: ${e.message}")
            }
            try {
                appContext.getSharedPreferences("braga_prefs", Context.MODE_PRIVATE)
                    .edit().clear().apply()
            } catch (e: Exception) {
                android.util.Log.w("AuthViewModel", "Erro ao limpar prefs no deleteAccount: ${e.message}")
            }
        }

        // 3. FIREBASE AUTH (inválida sessão)
        try {
            user.delete().await()
        } catch (e: Exception) {
            android.util.Log.e("AuthViewModel", "Erro ao excluir conta Auth: ${e.message}")
            auth.signOut()
        }

        _sessionStatus.value = AppSessionStatus.NotAuthenticated
        _isProfileComplete.value = null
        _hasAcceptedConsent.value = null
        _userRole.value = null
    }
}
