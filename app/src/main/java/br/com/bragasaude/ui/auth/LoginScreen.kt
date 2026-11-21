package br.com.bragasaude.ui.auth

import br.com.bragasaude.ui.components.BragaAlertDialog

import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import androidx.hilt.navigation.compose.hiltViewModel
import br.com.bragasaude.BuildConfig
import br.com.bragasaude.R
import br.com.bragasaude.ui.theme.BragasaudeTheme
import br.com.bragasaude.ui.b2b.PartnerTechnologyCredit
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.launch

@OptIn(ExperimentalComposeUiApi::class, ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    viewModel: AuthViewModel = hiltViewModel(),
    onLoginSuccess: () -> Unit
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val isKeyboardVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    val credentialManager = CredentialManager.create(context)
    val authState by viewModel.authState.collectAsState()
    val blockExternalLinksInTestLab = remember {
        BuildConfig.DEBUG && Settings.System.getString(context.contentResolver, "firebase.test.lab") == "true"
    }


    var emailMode by rememberSaveable { mutableStateOf("login") } // "login", "signup" ou "forgot"
    var forgotStep by rememberSaveable { mutableStateOf(1) } // 1: e-mail, 2: código OTP, 3: nova senha
    var signupPurpose by rememberSaveable { mutableStateOf("personal") } // "personal" ou "company" (B2B-06)
    var showInviteDialog by rememberSaveable { mutableStateOf(false) }
    var inviteCodeDraft by rememberSaveable { mutableStateOf("") }
    var hasPendingInvite by rememberSaveable { mutableStateOf(false) }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    var confirmPasswordVisible by rememberSaveable { mutableStateOf(false) }
    var newPasswordVisible by rememberSaveable { mutableStateOf(false) }
    var forgotNotice by rememberSaveable { mutableStateOf<String?>(null) }
    var forgotError by rememberSaveable { mutableStateOf<String?>(null) }
    var loginNotice by rememberSaveable { mutableStateOf<String?>(null) }
    var requestEmailFocus by remember { mutableStateOf(false) }
    val emailFocusRequester = remember { FocusRequester() }
    val prefs = remember {
        context.getSharedPreferences("braga_prefs", android.content.Context.MODE_PRIVATE)
    }
    val pendingDeepLinkInvite by br.com.bragasaude.ui.navigation.PartnerInviteNavigation.pendingInviteCode.collectAsState()
    LaunchedEffect(pendingDeepLinkInvite) {
        val code = pendingDeepLinkInvite
            ?: prefs.getString("pending_b2b_invite_code", "").orEmpty().trim()
        if (br.com.bragasaude.domain.b2b.B2BOnboardingValidator.isValidInviteCode(code)) {
            hasPendingInvite = true
            signupPurpose = "personal"
            prefs.edit().putBoolean("pending_b2b_company_onboarding", false)
                .putString("pending_b2b_invite_code", code).apply()
            br.com.bragasaude.ui.navigation.PartnerInviteNavigation.consumePending()
        }
    }
    // Testes devem injetar uma identidade descartável pelo harness autenticado.
    // Nunca selecionar automaticamente uma conta compartilhada em instalação nova.
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var confirmPassword by rememberSaveable { mutableStateOf("") }
    var otpCode by rememberSaveable { mutableStateOf("") }
    var newPassword by rememberSaveable { mutableStateOf("") }
    var confirmNewPassword by rememberSaveable { mutableStateOf("") }
    var signupOtpCode by rememberSaveable { mutableStateOf("") }
    var googleAccountConflictMessage by remember { mutableStateOf<String?>(null) }

    // A tela de login abre sem teclado para preservar o bloco legal na primeira dobra.
    // O foco vai para o primeiro campo quando o usuário escolhe criar conta ou recuperar senha.
    LaunchedEffect(emailMode, forgotStep, requestEmailFocus, hasPendingInvite) {
        if (requestEmailFocus || (emailMode == "forgot" && forgotStep == 1) ||
            (emailMode == "signup" && hasPendingInvite)
        ) {
            runCatching { emailFocusRequester.requestFocus() }
            requestEmailFocus = false
        }
    }

    // Observador de Erros e Mudanças de Estado
    LaunchedEffect(authState) {
        when (val state = authState) {
            is AuthViewModel.AuthState.Error -> {
                val message = friendlyAuthMessage(state.message)
                if (emailMode == "forgot") {
                    // Erro contextual no próprio fluxo, anunciado por leitor de tela.
                    val contextPrefix = when (forgotStep) {
                        1 -> "Não foi possível enviar o código."
                        2 -> "Não foi possível verificar o código."
                        else -> "Não foi possível redefinir a senha."
                    }
                    forgotError = if (message.startsWith("Não foi possível") || message.startsWith("Código")) {
                        message
                    } else {
                        "$contextPrefix $message"
                    }
                    forgotNotice = null
                } else {
                    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                }
                viewModel.clearAuthState()
            }
            is AuthViewModel.AuthState.AccountNotFound -> {
                Toast.makeText(
                    context,
                    "Esta conta Google ainda não tem cadastro. Use 'Criar conta'.",
                    Toast.LENGTH_LONG
                ).show()
                viewModel.clearAuthState()
            }
            is AuthViewModel.AuthState.AccountAlreadyExists -> {
                Toast.makeText(
                    context,
                    "Esta conta já foi cadastrada. Faça login com suas credenciais ou use o Google.",
                    Toast.LENGTH_LONG
                ).show()
                viewModel.clearAuthState()
            }
            is AuthViewModel.AuthState.GoogleAccountExists -> {
                googleAccountConflictMessage = state.message
                viewModel.clearAuthState()
            }
            is AuthViewModel.AuthState.RegistrationSuccess -> {
                Toast.makeText(
                    context,
                    "Cadastro realizado com sucesso!",
                    Toast.LENGTH_LONG
                ).show()
                viewModel.clearAuthState()
            }
            is AuthViewModel.AuthState.EmailVerificationSent -> {
                Toast.makeText(
                    context,
                    "Conta criada! Verifique seu e-mail e confirme antes de entrar.",
                    Toast.LENGTH_LONG
                ).show()
                viewModel.clearAuthState()
            }
            is AuthViewModel.AuthState.EmailNotVerified -> {
                Toast.makeText(
                    context,
                    "E-mail ainda não verificado. Cheque sua caixa de entrada.",
                    Toast.LENGTH_LONG
                ).show()
                viewModel.clearAuthState()
            }
            is AuthViewModel.AuthState.PasswordResetSent -> {
                Toast.makeText(
                    context,
                    "Link de recuperação enviado para seu e-mail.",
                    Toast.LENGTH_LONG
                ).show()
                viewModel.clearAuthState()
            }
            is AuthViewModel.AuthState.OtpSent -> {
                if (emailMode == "forgot") {
                    forgotError = null
                    forgotNotice = "Código enviado para ${email.trim()}. Confira a caixa de entrada e o spam."
                    forgotStep = 2
                    viewModel.clearAuthState()
                } else {
                    Toast.makeText(
                        context,
                        "Código de verificação enviado para seu e-mail!",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            is AuthViewModel.AuthState.OtpVerified -> {
                if (emailMode == "forgot") {
                    forgotError = null
                    forgotNotice = "Código verificado. Defina sua nova senha."
                    forgotStep = 3
                } else {
                    Toast.makeText(
                        context,
                        "Código verificado com sucesso!",
                        Toast.LENGTH_SHORT
                    ).show()
                    emailMode = "login"
                }
                viewModel.clearAuthState()
            }
            is AuthViewModel.AuthState.PasswordReset -> {
                loginNotice = "Senha redefinida com sucesso. Entre com a nova senha."
                emailMode = "login"
                forgotStep = 1
                forgotNotice = null
                forgotError = null
                otpCode = ""
                newPassword = ""
                confirmNewPassword = ""
                viewModel.clearAuthState()
            }
            else -> {}
        }
    }

    val returnToLogin: () -> Unit = {
        focusManager.clearFocus(force = true)
        keyboardController?.hide()
        emailMode = "login"
        forgotStep = 1
        otpCode = ""
        newPassword = ""
        confirmNewPassword = ""
        forgotNotice = null
        forgotError = null
        loginNotice = null
        viewModel.clearPendingReset()
        viewModel.clearAuthState()
    }

    // Voltar do sistema também retorna ao login em vez de sair do app no meio da recuperação.
    BackHandler(enabled = emailMode == "forgot", onBack = returnToLogin)

    BragasaudeTheme() {
        Scaffold(
            topBar = {
                if (emailMode == "forgot") {
                    TopAppBar(
                        title = {
                            Text(
                                "Recuperação de senha",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                        },
                        navigationIcon = {
                            IconButton(onClick = returnToLogin) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Voltar ao login"
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.background
                        )
                    )
                }
            },
            bottomBar = {
                if (emailMode != "forgot") {
                    // O bloco legal fica ancorado acima do crédito, sempre visível na 1ª dobra.
                    LoginLegalFooter(
                        onOpenTerms = {
                            if (!blockExternalLinksInTestLab) {
                                uriHandler.openUri("https://bragasaude.online/termos")
                            }
                        },
                        onOpenPrivacy = {
                            if (!blockExternalLinksInTestLab) {
                                uriHandler.openUri("https://bragasaude.online/privacidade")
                            }
                        }
                    )
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .semantics { testTagsAsResourceId = true },
            containerColor = MaterialTheme.colorScheme.background
        ) { innerPadding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .consumeWindowInsets(innerPadding)
                    .testTag("login_content")
                    .imePadding()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(if (isKeyboardVisible) 8.dp else 12.dp)
            ) {
                item {
                    LoginIdentity(
                        showIntroduction = !isKeyboardVisible && emailMode != "forgot" &&
                            authState !is AuthViewModel.AuthState.OtpSent
                    )
                }

                item {
                    if (emailMode == "forgot") {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Text(
                                text = when (forgotStep) {
                                    1 -> "Informe o e-mail da sua conta. Enviaremos um código de 6 dígitos para confirmar que é você."
                                    2 -> "Enviamos um código para ${email.trim()}. Ele expira em alguns minutos."
                                    else -> "Crie uma nova senha com pelo menos 6 caracteres."
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                            )
                            forgotNotice?.let { notice ->
                                Text(
                                    text = notice,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .semantics { liveRegion = LiveRegionMode.Polite }
                                        .testTag("forgot_notice")
                                )
                            }
                            forgotError?.let { error ->
                                Text(
                                    text = error,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .semantics { liveRegion = LiveRegionMode.Assertive }
                                        .testTag("forgot_error")
                                )
                            }

                            when (forgotStep) {
                                1 -> {
                                    Text(
                                        "Digite o e-mail cadastrado para receber o código de verificação:",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    OutlinedTextField(
                                        value = email,
                                        onValueChange = { email = it },
                                        label = { Text("Seu e-mail") },
                                        singleLine = true,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .focusRequester(emailFocusRequester),
                                        shape = RoundedCornerShape(14.dp),
                                        keyboardOptions = KeyboardOptions(
                                            keyboardType = KeyboardType.Email,
                                            imeAction = ImeAction.Done
                                        ),
                                        keyboardActions = KeyboardActions(onDone = {
                                            if (email.isNotBlank()) {
                                                focusManager.clearFocus(force = true)
                                                keyboardController?.hide()
                                                viewModel.sendOtpCode(email.trim())
                                            }
                                        })
                                    )
                                    val isSending = authState is AuthViewModel.AuthState.Loading
                                    Button(
                                        onClick = {
                                            if (email.isNotBlank()) {
                                                focusManager.clearFocus(force = true)
                                                keyboardController?.hide()
                                                forgotError = null
                                                forgotNotice = null
                                                viewModel.sendOtpCode(email.trim())
                                            }
                                        },
                                        enabled = email.isNotBlank() && !isSending,
                                        modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp),
                                        shape = RoundedCornerShape(14.dp),
                                        colors = ButtonDefaults.buttonColors()
                                    ) {
                                        if (isSending) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(22.dp),
                                                color = MaterialTheme.colorScheme.onPrimary,
                                                strokeWidth = 2.dp
                                            )
                                        } else {
                                            Text("Enviar código", fontWeight = FontWeight.Bold)
                                        }
                                    }
                                    if (isSending) {
                                        Text(
                                            text = "Enviando código de verificação...",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                                        )
                                    }
                                }
                                2 -> {
                                    Text(
                                        "Digite o código de 6 dígitos enviado para $email:",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    OutlinedTextField(
                                        value = otpCode,
                                        onValueChange = { if (it.length <= 6) otpCode = it.filter { c -> c.isDigit() } },
                                        label = { Text("Código de 6 dígitos") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(14.dp),
                                        keyboardOptions = KeyboardOptions(
                                            keyboardType = KeyboardType.NumberPassword,
                                            imeAction = ImeAction.Done
                                        ),
                                        keyboardActions = KeyboardActions(onDone = {
                                            if (otpCode.length == 6) {
                                                focusManager.clearFocus(force = true)
                                                keyboardController?.hide()
                                                viewModel.verifyOtpCode(email.trim(), otpCode.trim())
                                            }
                                        })
                                    )
                                    val isVerifying = authState is AuthViewModel.AuthState.Loading
                                    Button(
                                        onClick = {
                                            if (otpCode.length == 6) {
                                                focusManager.clearFocus(force = true)
                                                keyboardController?.hide()
                                                viewModel.verifyOtpCode(email.trim(), otpCode.trim())
                                            }
                                        },
                                        enabled = otpCode.length == 6 && !isVerifying,
                                        modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp),
                                        shape = RoundedCornerShape(14.dp),
                                        colors = ButtonDefaults.buttonColors()
                                    ) {
                                        if (isVerifying) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(22.dp),
                                                color = MaterialTheme.colorScheme.onPrimary,
                                                strokeWidth = 2.dp
                                            )
                                        } else {
                                            Text("Verificar código", fontWeight = FontWeight.Bold)
                                        }
                                    }
                                    if (isVerifying) {
                                        Text(
                                            text = "Verificando código...",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                                        )
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        TextButton(
                                            onClick = {
                                                focusManager.clearFocus(force = true)
                                                keyboardController?.hide()
                                                viewModel.sendOtpCode(email.trim())
                                            },
                                            enabled = !isVerifying
                                        ) {
                                            Text("Reenviar código", color = MaterialTheme.colorScheme.primary)
                                        }
                                        TextButton(
                                            onClick = {
                                                forgotStep = 1
                                                otpCode = ""
                                                forgotNotice = null
                                                forgotError = null
                                            },
                                            enabled = !isVerifying
                                        ) {
                                            Text("Alterar e-mail", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                                3 -> {
                                    Text(
                                        "Defina sua nova senha:",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    val passwordsMismatch = confirmNewPassword.isNotEmpty() &&
                                        newPassword != confirmNewPassword
                                    OutlinedTextField(
                                        value = newPassword,
                                        onValueChange = { newPassword = it },
                                        label = { Text("Nova senha (mínimo 6 caracteres)") },
                                        singleLine = true,
                                        visualTransformation = if (newPasswordVisible) {
                                            VisualTransformation.None
                                        } else {
                                            PasswordVisualTransformation()
                                        },
                                        trailingIcon = {
                                            IconButton(onClick = { newPasswordVisible = !newPasswordVisible }) {
                                                Icon(
                                                    imageVector = if (newPasswordVisible) {
                                                        Icons.Filled.VisibilityOff
                                                    } else {
                                                        Icons.Filled.Visibility
                                                    },
                                                    contentDescription = if (newPasswordVisible) {
                                                        "Ocultar nova senha"
                                                    } else {
                                                        "Mostrar nova senha"
                                                    }
                                                )
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(14.dp),
                                        keyboardOptions = KeyboardOptions(
                                            keyboardType = KeyboardType.Password,
                                            imeAction = ImeAction.Next
                                        ),
                                        keyboardActions = KeyboardActions(
                                            onNext = { focusManager.moveFocus(FocusDirection.Down) }
                                        )
                                    )
                                    OutlinedTextField(
                                        value = confirmNewPassword,
                                        onValueChange = { confirmNewPassword = it },
                                        label = { Text("Confirmar nova senha") },
                                        singleLine = true,
                                        isError = passwordsMismatch,
                                        supportingText = if (passwordsMismatch) {
                                            {
                                                Text(
                                                    "As senhas não coincidem.",
                                                    modifier = Modifier.semantics {
                                                        liveRegion = LiveRegionMode.Polite
                                                    }
                                                )
                                            }
                                        } else {
                                            null
                                        },
                                        visualTransformation = if (newPasswordVisible) {
                                            VisualTransformation.None
                                        } else {
                                            PasswordVisualTransformation()
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(14.dp),
                                        keyboardOptions = KeyboardOptions(
                                            keyboardType = KeyboardType.Password,
                                            imeAction = ImeAction.Done
                                        ),
                                        keyboardActions = KeyboardActions(onDone = {
                                            focusManager.clearFocus(force = true)
                                            keyboardController?.hide()
                                            if (newPassword.isNotBlank() &&
                                                newPassword == confirmNewPassword &&
                                                newPassword.length >= 6
                                            ) {
                                                viewModel.resetPasswordWithOtp(newPassword)
                                            }
                                        })
                                    )
                                    val isResetting = authState is AuthViewModel.AuthState.Loading
                                    val isPasswordValid = newPassword.isNotBlank() &&
                                        confirmNewPassword.isNotBlank() &&
                                        newPassword == confirmNewPassword &&
                                        newPassword.length >= 6
                                    Button(
                                        onClick = {
                                            if (isPasswordValid) {
                                                focusManager.clearFocus(force = true)
                                                keyboardController?.hide()
                                                viewModel.resetPasswordWithOtp(newPassword)
                                            }
                                        },
                                        enabled = isPasswordValid && !isResetting,
                                        modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp),
                                        shape = RoundedCornerShape(14.dp),
                                        colors = ButtonDefaults.buttonColors()
                                    ) {
                                        if (isResetting) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(22.dp),
                                                color = MaterialTheme.colorScheme.onPrimary,
                                                strokeWidth = 2.dp
                                            )
                                        } else {
                                            Text("Redefinir senha", fontWeight = FontWeight.Bold)
                                        }
                                    }
                                    if (isResetting) {
                                        Text(
                                            text = "Redefinindo senha...",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                                        )
                                    }
                                }
                            }
                        }
                    } else if (authState is AuthViewModel.AuthState.Loading) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp)
                        ) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.height(12.dp))
                            Text(
                                text = "Acessando sua conta com segurança...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        loginNotice?.let { notice ->
                            Text(
                                text = notice,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 4.dp)
                                    .semantics { liveRegion = LiveRegionMode.Polite }
                                    .testTag("login_notice")
                            )
                        }
                        // SELETOR DE MODO: ENTRAR VS CRIAR CONTA
                        TabRow(
                            selectedTabIndex = if (emailMode == "signup") 1 else 0,
                            containerColor = Color.Transparent,
                            contentColor = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)
                        ) {
                            Tab(
                                selected = emailMode == "login",
                                onClick = {
                                    emailMode = "login"
                                    requestEmailFocus = false
                                    loginNotice = null
                                    viewModel.clearAuthState()
                                },
                                modifier = Modifier.testTag("login_tab"),
                                text = {
                                    // Quebra em duas linhas em vez de encolher/truncar a fonte ampliada.
                                    Text(
                                        "Entrar",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 2,
                                        softWrap = true,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            )
                            Tab(
                                selected = emailMode == "signup",
                                onClick = {
                                    emailMode = "signup"
                                    requestEmailFocus = false
                                    loginNotice = null
                                    viewModel.clearAuthState()
                                },
                                modifier = Modifier.testTag("signup_tab"),
                                text = {
                                    Text(
                                        "Criar conta",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 2,
                                        softWrap = true,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            )
                        }

                        // FORMULÁRIO PRINCIPAL
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            if (emailMode == "signup" && authState is AuthViewModel.AuthState.OtpSent) {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                                    ),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                                ) {
                                    Column(
                                        modifier = Modifier.padding(18.dp),
                                        verticalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Text(
                                            text = "Confirme seu e-mail",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            text = "Digite o código de 6 dígitos enviado para seu e-mail ($email):",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        OutlinedTextField(
                                            value = signupOtpCode,
                                            onValueChange = { if (it.length <= 6) signupOtpCode = it.filter { c -> c.isDigit() } },
                                            label = { Text("Código de 6 dígitos") },
                                            singleLine = true,
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(14.dp),
                                            keyboardOptions = KeyboardOptions(
                                                keyboardType = KeyboardType.NumberPassword,
                                                imeAction = ImeAction.Done
                                            ),
                                            keyboardActions = KeyboardActions(onDone = {
                                                if (signupOtpCode.length == 6) {
                                                    viewModel.verifyRegistrationOtp(signupOtpCode)
                                                }
                                            })
                                        )
                                        Button(
                                            onClick = {
                                                if (signupOtpCode.length == 6) {
                                                    viewModel.verifyRegistrationOtp(signupOtpCode)
                                                }
                                            },
                                            enabled = signupOtpCode.length == 6,
                                            modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp),
                                            shape = RoundedCornerShape(14.dp),
                                            colors = ButtonDefaults.buttonColors()
                                        ) {
                                            Text("Confirmar e Ativar Conta", fontWeight = FontWeight.Bold)
                                        }
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            TextButton(
                                                onClick = {
                                                    viewModel.signUpWithEmail(
                                                        email.trim(),
                                                        password,
                                                        businessAccount = !hasPendingInvite && signupPurpose == "company"
                                                    )
                                                }
                                            ) {
                                                Text("Reenviar código", color = MaterialTheme.colorScheme.primary)
                                            }
                                            TextButton(
                                                onClick = {
                                                    viewModel.clearAuthState()
                                                    signupOtpCode = ""
                                                }
                                            ) {
                                                Text("Voltar e editar", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                        }
                                    }
                                }
                            } else {
                                if (emailMode == "signup" && !hasPendingInvite) {
                                    Text(
                                        text = "Como você quer usar o aplicativo?",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        val isPersonal = signupPurpose == "personal"
                                        val isCompany = signupPurpose == "company"
                                        OutlinedCard(
                                            onClick = {
                                                signupPurpose = "personal"
                                                requestEmailFocus = true
                                                prefs.edit().putBoolean("pending_b2b_company_onboarding", false).apply()
                                            },
                                            modifier = Modifier.weight(1f).testTag("purpose_personal_button"),
                                            shape = RoundedCornerShape(14.dp),
                                            border = BorderStroke(
                                                width = if (isPersonal) 2.dp else 1.dp,
                                                color = if (isPersonal) MaterialTheme.colorScheme.primary
                                                else MaterialTheme.colorScheme.outlineVariant
                                            ),
                                            colors = CardDefaults.outlinedCardColors(
                                                containerColor = if (isPersonal) {
                                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                                                } else Color.Transparent
                                            )
                                        ) {
                                            Column(modifier = Modifier.padding(12.dp)) {
                                                Text(
                                                    text = "Para mim",
                                                    style = MaterialTheme.typography.titleSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                                Text(
                                                    text = "Cuidar da minha saúde",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }

                                        OutlinedCard(
                                            onClick = {
                                                signupPurpose = "company"
                                                requestEmailFocus = true
                                                prefs.edit().putBoolean("pending_b2b_company_onboarding", true).apply()
                                            },
                                            modifier = Modifier.weight(1f).testTag("purpose_company_button"),
                                            shape = RoundedCornerShape(14.dp),
                                            border = BorderStroke(
                                                width = if (isCompany) 2.dp else 1.dp,
                                                color = if (isCompany) MaterialTheme.colorScheme.primary
                                                else MaterialTheme.colorScheme.outlineVariant
                                            ),
                                            colors = CardDefaults.outlinedCardColors(
                                                containerColor = if (isCompany) {
                                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                                                } else Color.Transparent
                                            )
                                        ) {
                                            Column(modifier = Modifier.padding(12.dp)) {
                                                Text(
                                                    text = "Para minha empresa",
                                                    style = MaterialTheme.typography.titleSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                                Text(
                                                    text = "Criar um programa",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                    if (signupPurpose == "company") {
                                        Text(
                                            text = "Criar acesso → Preparar programa → Aprovação",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }

                                val isSignupValid = emailMode == "signup" &&
                                    email.isNotBlank() &&
                                    password.isNotBlank() &&
                                    password.length >= 6 &&
                                    password == confirmPassword

                                val isLoginValid = emailMode == "login" && email.isNotBlank() && password.isNotBlank()

                                val submitCredentials: () -> Unit = {
                                    focusManager.clearFocus(force = true)
                                    keyboardController?.hide()
                                    loginNotice = null
                                    if (emailMode == "login" && isLoginValid) {
                                        viewModel.signInWithEmail(email.trim(), password)
                                    } else if (emailMode == "signup" && isSignupValid) {
                                        viewModel.signUpWithEmail(
                                            email.trim(),
                                            password,
                                            businessAccount = !hasPendingInvite && signupPurpose == "company"
                                        )
                                    }
                                }

                                // Compose UI 1.7.x ainda mantém o ContentType de autofill como
                                // interno; KeyboardType.Email/Password alimenta as heurísticas
                                // do serviço de autofill do Android (e-mail e senha).
                                OutlinedTextField(
                                    value = email,
                                    onValueChange = { email = it },
                                    label = { Text("E-mail") },
                                    singleLine = true,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("email_input")
                                        .focusRequester(emailFocusRequester),
                                    shape = RoundedCornerShape(14.dp),
                                    keyboardOptions = KeyboardOptions(
                                        keyboardType = KeyboardType.Email,
                                        imeAction = ImeAction.Next
                                    ),
                                    keyboardActions = KeyboardActions(
                                        onNext = { focusManager.moveFocus(FocusDirection.Down) }
                                    )
                                )

                                OutlinedTextField(
                                    value = password,
                                    onValueChange = { password = it },
                                    label = { Text("Senha") },
                                    singleLine = true,
                                    isError = emailMode == "signup" && password.isNotEmpty() && password.length < 6,
                                    supportingText = if (emailMode == "signup" && password.isNotEmpty() && password.length < 6) {
                                        {
                                            Text(
                                                "A senha precisa ter pelo menos 6 caracteres.",
                                                modifier = Modifier.semantics {
                                                    liveRegion = LiveRegionMode.Polite
                                                }
                                            )
                                        }
                                    } else {
                                        null
                                    },
                                    visualTransformation = if (passwordVisible) {
                                        VisualTransformation.None
                                    } else {
                                        PasswordVisualTransformation()
                                    },
                                    trailingIcon = {
                                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                            Icon(
                                                imageVector = if (passwordVisible) {
                                                    Icons.Filled.VisibilityOff
                                                } else {
                                                    Icons.Filled.Visibility
                                                },
                                                contentDescription = if (passwordVisible) {
                                                    "Ocultar senha"
                                                } else {
                                                    "Mostrar senha"
                                                }
                                            )
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth().testTag("password_input"),
                                    shape = RoundedCornerShape(14.dp),
                                    keyboardOptions = KeyboardOptions(
                                        keyboardType = KeyboardType.Password,
                                        imeAction = if (emailMode == "signup") ImeAction.Next else ImeAction.Done
                                    ),
                                    keyboardActions = KeyboardActions(
                                        onNext = { focusManager.moveFocus(FocusDirection.Down) },
                                        onDone = { submitCredentials() }
                                    )
                                )

                                if (emailMode == "signup") {
                                    val passwordsMismatch = confirmPassword.isNotEmpty() && password != confirmPassword
                                    OutlinedTextField(
                                        value = confirmPassword,
                                        onValueChange = { confirmPassword = it },
                                        label = { Text("Confirmar senha") },
                                        singleLine = true,
                                        isError = passwordsMismatch,
                                        supportingText = if (passwordsMismatch) {
                                            {
                                                Text(
                                                    "As senhas não coincidem.",
                                                    modifier = Modifier.semantics {
                                                        liveRegion = LiveRegionMode.Polite
                                                    }
                                                )
                                            }
                                        } else {
                                            null
                                        },
                                        visualTransformation = if (confirmPasswordVisible) {
                                            VisualTransformation.None
                                        } else {
                                            PasswordVisualTransformation()
                                        },
                                        trailingIcon = {
                                            IconButton(onClick = { confirmPasswordVisible = !confirmPasswordVisible }) {
                                                Icon(
                                                    imageVector = if (confirmPasswordVisible) {
                                                        Icons.Filled.VisibilityOff
                                                    } else {
                                                        Icons.Filled.Visibility
                                                    },
                                                    contentDescription = if (confirmPasswordVisible) {
                                                        "Ocultar confirmação de senha"
                                                    } else {
                                                        "Mostrar confirmação de senha"
                                                    }
                                                )
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(14.dp),
                                        keyboardOptions = KeyboardOptions(
                                            keyboardType = KeyboardType.Password,
                                            imeAction = ImeAction.Done
                                        ),
                                        keyboardActions = KeyboardActions(onDone = { submitCredentials() })
                                    )

                                    // CARD DE VALIDAÇÃO DE SEGURANÇA (1 OU OUTRO)
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(16.dp),
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                                        ),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(14.dp),
                                            verticalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Text(
                                                text = "Confirme seu e-mail",
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            Text(
                                                text = "Enviaremos um código de 6 dígitos para confirmar seu e-mail e ativar a conta.",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }

                                if (emailMode == "login") {
                                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                                        TextButton(
                                            onClick = {
                                                emailMode = "forgot"
                                                forgotStep = 1
                                                forgotNotice = null
                                                forgotError = null
                                                loginNotice = null
                                                otpCode = ""
                                                newPassword = ""
                                                confirmNewPassword = ""
                                                viewModel.clearAuthState()
                                            },
                                            modifier = Modifier.heightIn(min = 48.dp),
                                            contentPadding = PaddingValues(horizontal = 12.dp)
                                        ) {
                                            Text(
                                                "Esqueci minha senha",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.primary,
                                                textDecoration = TextDecoration.Underline
                                            )
                                        }
                                    }
                                }

                                // Botão Principal (ENTRAR ou RECEBER CÓDIGO)
                                Button(
                                    onClick = submitCredentials,
                                    enabled = if (emailMode == "login") isLoginValid else isSignupValid,
                                    modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp).testTag("login_button"),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = ButtonDefaults.buttonColors()
                                ) {
                                    Text(
                                        if (emailMode == "login") "Entrar" else "Receber código",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            // Divisor "ou continuar com"
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant)
                                Text("ou continuar com", modifier = Modifier.padding(horizontal = 12.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                                HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant)
                            }

                            // Botão Secundário: GOOGLE. O logo G é decorativo: o texto do botão
                            // já nomeia a ação para leitores de tela.
                            OutlinedButton(
                                onClick = {
                                    focusManager.clearFocus(force = true)
                                    keyboardController?.hide()
                                    loginNotice = null
                                    triggerGoogleFlow(context, scope, credentialManager) { idToken ->
                                        viewModel.signInWithGoogle(
                                            idToken = idToken,
                                            businessAccount = !hasPendingInvite && emailMode == "signup" && signupPurpose == "company"
                                        )
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp),
                                shape = RoundedCornerShape(16.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_google),
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp),
                                        tint = Color.Unspecified
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Text(
                                        "Continuar com o Google",
                                        fontWeight = FontWeight.SemiBold,
                                        style = MaterialTheme.typography.bodyLarge,
                                        maxLines = 2,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }

                            // Links de ação usam sublinhado + semântica de link para não
                            // dependerem apenas da cor teal.
                            TextButton(
                                modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                                onClick = {
                                    emailMode = if (emailMode == "login") "signup" else "login"
                                    if (emailMode == "login") signupPurpose = "personal"
                                    loginNotice = null
                                    viewModel.clearAuthState()
                                    signupOtpCode = ""
                                    requestEmailFocus = false
                                }
                            ) {
                                val prefix = if (emailMode == "login") "Não tem uma conta? " else "Já possui uma conta? "
                                val action = if (emailMode == "login") "Criar conta" else "Entrar"
                                val neutralColor = MaterialTheme.colorScheme.onSurfaceVariant
                                val actionColor = MaterialTheme.colorScheme.primary
                                Text(
                                    text = buildAnnotatedString {
                                        withStyle(SpanStyle(color = neutralColor)) {
                                            append(prefix)
                                        }
                                        withStyle(
                                            SpanStyle(
                                                color = actionColor,
                                                fontWeight = FontWeight.SemiBold,
                                                textDecoration = TextDecoration.Underline
                                            )
                                        ) {
                                            append(action)
                                        }
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    textAlign = TextAlign.Center
                                )
                            }

                            if (emailMode == "login" && !hasPendingInvite) {
                                OutlinedCard(
                                    onClick = {
                                        emailMode = "signup"
                                        signupPurpose = "company"
                                        prefs.edit().putBoolean("pending_b2b_company_onboarding", true).apply()
                                        loginNotice = null
                                        viewModel.clearAuthState()
                                    },
                                    modifier = Modifier.fillMaxWidth().testTag("company_shortcut_button"),
                                    shape = RoundedCornerShape(14.dp),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                                ) {
                                    Column(
                                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                                        verticalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        Text(
                                            text = "Um programa com a sua marca",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            text = "Consultórios, convênios e outras empresas",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                TextButton(
                                    onClick = {
                                        inviteCodeDraft = ""
                                        showInviteDialog = true
                                    },
                                    modifier = Modifier
                                        .align(Alignment.CenterHorizontally)
                                        .testTag("invite_shortcut_button")
                                ) {
                                    Text(
                                        text = "Recebi um convite ou código",
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.primary,
                                        textDecoration = TextDecoration.Underline
                                    )
                                }
                            }
                        }
                    }
                }
            }

            val inviteCodeValid =
                br.com.bragasaude.domain.b2b.B2BOnboardingValidator.isValidInviteCode(inviteCodeDraft)
            if (showInviteDialog) {
                BragaAlertDialog(
                    onDismissRequest = { showInviteDialog = false },
                    title = {
                        Text("Encontre seu programa", fontWeight = FontWeight.Bold)
                    },
                    text = {
                        // Corpo limitado + rolável: com fonte ampliada/teclado o conteúdo
                        // não sobrepõe os botões de ação do diálogo.
                        Column(
                            modifier = Modifier
                                .heightIn(max = 320.dp)
                                .verticalScroll(rememberScrollState())
                                .imePadding()
                                .testTag("invite_dialog_content"),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "Guarde o código recebido. Após entrar, você poderá conferir o programa e autorizar sua participação.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            OutlinedTextField(
                                value = inviteCodeDraft,
                                onValueChange = { if (it.length <= 32) inviteCodeDraft = it.uppercase() },
                                label = { Text("Código do programa") },
                                singleLine = true,
                                isError = inviteCodeDraft.isNotBlank() && !inviteCodeValid,
                                supportingText = {
                                    Text(
                                        text = when {
                                            inviteCodeDraft.isBlank() ->
                                                "Digite o código recebido para habilitar \"Guardar código\"."
                                            !inviteCodeValid ->
                                                "Código inválido: use de 3 a 32 letras, números, hífen ou sublinhado."
                                            else ->
                                                "Código válido. Você poderá conferir o programa após entrar."
                                        },
                                        modifier = Modifier
                                            .semantics { liveRegion = LiveRegionMode.Polite }
                                            .testTag("invite_code_helper")
                                    )
                                },
                                modifier = Modifier.fillMaxWidth().testTag("invite_code_input"),
                                shape = RoundedCornerShape(14.dp)
                            )
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                val code = inviteCodeDraft.trim()
                                if (!inviteCodeValid) return@Button
                                hasPendingInvite = true
                                signupPurpose = "personal"
                                prefs.edit().putBoolean("pending_b2b_company_onboarding", false)
                                    .putString("pending_b2b_invite_code", code).apply()
                                showInviteDialog = false
                            },
                            enabled = inviteCodeValid
                        ) {
                            Text("Guardar código", fontWeight = FontWeight.Bold)
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = { showInviteDialog = false },
                            enabled = true
                        ) {
                            Text("Cancelar")
                        }
                    }
                )
            }

            // Diálogo de Conta Google Já Existente (Bifurcação de segurança)
            if (googleAccountConflictMessage != null) {
                BragaAlertDialog(
                    onDismissRequest = { googleAccountConflictMessage = null },
                    title = {
                        Text("Conta vinculada ao Google", fontWeight = FontWeight.Bold)
                    },
                    text = {
                        Text(googleAccountConflictMessage ?: "Esta conta foi criada com o Google. Por segurança, continue usando sua conta Google.")
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                googleAccountConflictMessage = null
                                focusManager.clearFocus(force = true)
                                keyboardController?.hide()
                                triggerGoogleFlow(context, scope, credentialManager) { idToken ->
                                    viewModel.signInWithGoogle(idToken)
                                }
                            },
                            colors = ButtonDefaults.buttonColors()
                        ) {
                            Text("Entrar com o Google", fontWeight = FontWeight.Bold)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { googleAccountConflictMessage = null }) {
                            Text("Cancelar")
                        }
                    }
                )
            }
        }
    }
}

private fun triggerGoogleFlow(
    context: android.content.Context,
    scope: kotlinx.coroutines.CoroutineScope,
    credentialManager: CredentialManager,
    onTokenReceived: (String) -> Unit
) {
    scope.launch {
        try {
            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false) // Permite escolher qualquer conta no dispositivo
                .setServerClientId("626274351085-b4q2acgsfuql6pcan2d9q13msiu7go24.apps.googleusercontent.com")
                .setAutoSelectEnabled(false)
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()

            val result = credentialManager.getCredential(context, request)
            val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(result.credential.data)
            val googleIdToken = googleIdTokenCredential.idToken

            if (googleIdToken != null) {
                onTokenReceived(googleIdToken)
            }
        } catch (e: GetCredentialCancellationException) {
            // Cancelar o seletor é uma escolha do usuário: não gera erro de UI nem log de erro.
            android.util.Log.d("LoginScreen", "Seleção de conta Google cancelada pelo usuário.")
        } catch (e: NoCredentialException) {
            android.util.Log.i("LoginScreen", "Nenhuma credencial Google disponível no aparelho.")
            Toast.makeText(
                context,
                "Nenhuma conta Google encontrada neste aparelho. Entre com e-mail e senha ou adicione uma conta Google nos ajustes do celular.",
                Toast.LENGTH_LONG
            ).show()
        } catch (e: Exception) {
            android.util.Log.w("LoginScreen", "Falha no fluxo Google: ${e::class.simpleName}")
            Toast.makeText(
                context,
                "Não foi possível entrar com o Google. Tente novamente ou use e-mail e senha.",
                Toast.LENGTH_LONG
            ).show()
        }
    }
}

/**
 * Bloco legal ancorado acima do crédito de tecnologia, sempre visível na primeira dobra.
 * Os links usam [Role.Button], sublinhado e o token `primary` (AA sobre o fundo claro/escuro),
 * com alvo de toque mínimo de 48dp.
 */
@Composable
private fun LoginLegalFooter(
    onOpenTerms: () -> Unit,
    onOpenPrivacy: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .imePadding()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 20.dp, vertical = 2.dp)
            .testTag("login_legal_footer"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Ao continuar, você concorda com nossos",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            LegalLink(label = "Termos de Uso", onClick = onOpenTerms)
            Text(
                " e ",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            LegalLink(label = "Privacidade", onClick = onOpenPrivacy)
        }
        PartnerTechnologyCredit()
    }
}

@Composable
private fun LegalLink(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .defaultMinSize(minHeight = 48.dp)
            .clickable(
                onClickLabel = "Abrir $label",
                role = Role.Button,
                onClick = onClick
            )
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            textDecoration = TextDecoration.Underline,
            fontWeight = FontWeight.Medium,
            maxLines = 1
        )
    }
}

/** Troca mensagens genéricas do ViewModel por instruções acionáveis para o usuário. */
private fun friendlyAuthMessage(raw: String?): String {
    val message = raw?.trim().orEmpty()
    return when {
        message.isBlank() ->
            "Não foi possível concluir agora. Verifique sua conexão e tente novamente."
        message.equals("Erro ao entrar", ignoreCase = true) ->
            "Não foi possível entrar. Confira o e-mail e a senha e tente novamente."
        message.equals("Código inválido", ignoreCase = true) ->
            "Código inválido ou expirado. Peça um novo código."
        message.equals("Erro ao enviar código", ignoreCase = true) ->
            "Não foi possível enviar o código. Confira o e-mail e tente novamente."
        else -> message
    }
}
