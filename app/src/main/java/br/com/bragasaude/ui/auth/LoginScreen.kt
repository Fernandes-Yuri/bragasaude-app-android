package br.com.bragasaude.ui.auth

import br.com.bragasaude.ui.components.BragaAlertDialog

import android.provider.Settings
import android.widget.Toast
import br.com.bragasaude.data.util.HealthFormatter
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import androidx.hilt.navigation.compose.hiltViewModel
import br.com.bragasaude.BuildConfig
import br.com.bragasaude.R
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalComposeUiApi::class)
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
    var isLookingUpInvite by remember { mutableStateOf(false) }
    var recognizedInvite by remember {
        mutableStateOf<br.com.bragasaude.domain.b2b.PartnerInvitePreviewDto?>(null)
    }
    val prefs = remember {
        context.getSharedPreferences("braga_prefs", android.content.Context.MODE_PRIVATE)
    }
    val pendingDeepLinkInvite by br.com.bragasaude.ui.navigation.PartnerInviteNavigation.pendingInviteCode.collectAsState()
    LaunchedEffect(pendingDeepLinkInvite) {
        val code = pendingDeepLinkInvite
            ?: prefs.getString("pending_b2b_invite_code", "").orEmpty().trim()
        if (br.com.bragasaude.domain.b2b.B2BOnboardingValidator.isValidInviteCode(code) &&
            recognizedInvite?.inviteCode?.equals(code, ignoreCase = true) != true
        ) {
            val preview = viewModel.lookupPartnerInvitePreview(code)
            if (preview != null) {
                recognizedInvite = preview
                prefs.edit().putString("pending_b2b_invite_code", preview.inviteCode).apply()
            }
            if (pendingDeepLinkInvite != null) {
                br.com.bragasaude.ui.navigation.PartnerInviteNavigation.consumePending()
            }
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

    // Observador de Erros e Mudanças de Estado
    LaunchedEffect(authState) {
        when (val state = authState) {
            is AuthViewModel.AuthState.Error -> {
                Toast.makeText(context, state.message, Toast.LENGTH_LONG).show()
                viewModel.clearAuthState()
            }
            is AuthViewModel.AuthState.AccountNotFound -> {
                Toast.makeText(
                    context,
                    "Esta conta Google ainda não tem cadastro no Braga Saúde. Use 'Criar nova conta'.",
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
                    "Cadastro realizado com sucesso! Bem-vindo(a) ao Braga Saúde.",
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
                Toast.makeText(
                    context,
                    "Código de verificação enviado para seu e-mail!",
                    Toast.LENGTH_SHORT
                ).show()
                if (emailMode == "forgot") {
                    forgotStep = 2
                    viewModel.clearAuthState()
                }
            }
            is AuthViewModel.AuthState.OtpVerified -> {
                Toast.makeText(
                    context,
                    "Código verificado com sucesso!",
                    Toast.LENGTH_SHORT
                ).show()
                if (emailMode == "forgot") {
                    forgotStep = 3
                } else {
                    emailMode = "login"
                }
                viewModel.clearAuthState()
            }
            is AuthViewModel.AuthState.PasswordReset -> {
                Toast.makeText(
                    context,
                    "Senha redefinida com sucesso! Faça login com a nova senha.",
                    Toast.LENGTH_LONG
                ).show()
                emailMode = "login"
                forgotStep = 1
                otpCode = ""
                newPassword = ""
                confirmNewPassword = ""
                viewModel.clearAuthState()
            }
            else -> {}
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .semantics { testTagsAsResourceId = true },
        containerColor = br.com.bragasaude.ui.theme.Background
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
                .imePadding()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(if (isKeyboardVisible) 10.dp else 16.dp)
        ) {
            item {
                Spacer(Modifier.height(if (isKeyboardVisible) 0.dp else 8.dp))
                val invite = recognizedInvite
                if (invite != null) {
                    val partnerColor = remember(invite.primaryColor) {
                        runCatching {
                            Color(android.graphics.Color.parseColor(invite.primaryColor))
                        }.getOrDefault(Color(0xFF1A6B5C))
                    }
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = partnerColor.copy(alpha = 0.10f)),
                        border = BorderStroke(1.dp, partnerColor.copy(alpha = 0.45f))
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = invite.programName,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = partnerColor
                                )
                                TextButton(
                                    onClick = { recognizedInvite = null },
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                                ) {
                                    Text("Trocar", style = MaterialTheme.typography.labelMedium)
                                }
                            }
                            Text(
                                text = "${invite.companyName} · Convite ${invite.inviteCode}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "Entre ou crie sua conta para participar deste programa.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                } else {
                    // B2B-06: Destaque reduzido da marca na entrada (priorizando o formulario)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        br.com.bragasaude.ui.components.ShieldEcgIcon(sizeDp = 36.dp)
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = stringResource(R.string.app_name_new),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            item {
                if (!isKeyboardVisible) {
                    val isCompanyMode = signupPurpose == "company"
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = if (isCompanyMode) "Sua empresa. A sua marca." else "Seu cuidado começa aqui.",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = if (isCompanyMode) {
                                br.com.bragasaude.domain.b2b.B2BOnboardingValidator.REFERENCE_SUBTITLE
                            } else {
                                "Acesse sua conta ou comece seu cadastro."
                            },
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            item {
                Spacer(Modifier.height(4.dp))

                if (emailMode == "forgot") {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            "Recuperação de Senha",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )

                        when (forgotStep) {
                            1 -> {
                                Text(
                                    "Digite o e-mail cadastrado para receber o código de verificação:",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.Gray
                                )
                                OutlinedTextField(
                                    value = email,
                                    onValueChange = { email = it },
                                    label = { Text("Seu e-mail") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(14.dp),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
                                )
                                val isSending = authState is AuthViewModel.AuthState.Loading
                                Button(
                                    onClick = {
                                        if (email.isNotBlank()) {
                                            focusManager.clearFocus(force = true)
                                            keyboardController?.hide()
                                            viewModel.sendOtpCode(email.trim())
                                        }
                                    },
                                    enabled = email.isNotBlank() && !isSending,
                                    modifier = Modifier.fillMaxWidth().height(52.dp),
                                    shape = RoundedCornerShape(14.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
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
                            }
                            2 -> {
                                Text(
                                    "Digite o código de 6 dígitos enviado para $email:",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.Gray
                                )
                                OutlinedTextField(
                                    value = otpCode,
                                    onValueChange = { if (it.length <= 6) otpCode = it.filter { c -> c.isDigit() } },
                                    label = { Text("Código de 6 dígitos") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(14.dp),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
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
                                    modifier = Modifier.fillMaxWidth().height(52.dp),
                                    shape = RoundedCornerShape(14.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
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
                                        },
                                        enabled = !isVerifying
                                    ) {
                                        Text("Alterar e-mail", color = Color.Gray)
                                    }
                                }
                            }
                            3 -> {
                                Text(
                                    "Defina sua nova senha:",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.Gray
                                )
                                OutlinedTextField(
                                    value = newPassword,
                                    onValueChange = { newPassword = it },
                                    label = { Text("Nova senha (mínimo 6 caracteres)") },
                                    singleLine = true,
                                    visualTransformation = PasswordVisualTransformation(),
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(14.dp)
                                )
                                OutlinedTextField(
                                    value = confirmNewPassword,
                                    onValueChange = { confirmNewPassword = it },
                                    label = { Text("Confirmar nova senha") },
                                    singleLine = true,
                                    visualTransformation = PasswordVisualTransformation(),
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(14.dp)
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
                                    modifier = Modifier.fillMaxWidth().height(52.dp),
                                    shape = RoundedCornerShape(14.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
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
                            }
                        }
                        TextButton(
                            onClick = {
                                emailMode = "login"
                                forgotStep = 1
                                otpCode = ""
                                newPassword = ""
                                confirmNewPassword = ""
                                viewModel.clearPendingReset()
                                viewModel.clearAuthState()
                            },
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        ) {
                            Text("Voltar ao login", color = Color.Gray)
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
                    // SELETOR DE MODO: ENTRAR VS CRIAR CONTA
                    TabRow(
                        selectedTabIndex = if (emailMode == "signup") 1 else 0,
                        containerColor = Color.Transparent,
                        contentColor = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                    ) {
                        Tab(
                            selected = emailMode == "login",
                            onClick = {
                                emailMode = "login"
                                viewModel.clearAuthState()
                            },
                            text = { Text("Entrar", fontWeight = FontWeight.Bold, fontSize = 16.sp) }
                        )
                        Tab(
                            selected = emailMode == "signup",
                            onClick = {
                                emailMode = "signup"
                                viewModel.clearAuthState()
                            },
                            text = { Text("Criar Nova Conta", fontWeight = FontWeight.Bold, fontSize = 16.sp) }
                        )
                    }

                    // FORMULÁRIO PRINCIPAL
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
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
                                        text = "Validação Obrigatória",
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
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                                    )
                                    Button(
                                        onClick = {
                                            if (signupOtpCode.length == 6) {
                                                viewModel.verifyRegistrationOtp(signupOtpCode)
                                            }
                                        },
                                        enabled = signupOtpCode.length == 6,
                                        modifier = Modifier.fillMaxWidth().height(52.dp),
                                        shape = RoundedCornerShape(14.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
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
                                                    businessAccount = signupPurpose == "company"
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
                                            Text("Voltar e editar", color = Color.Gray)
                                        }
                                    }
                                }
                            }
                        } else {
                            if (emailMode == "signup" && recognizedInvite == null) {
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
                                        text = "Criar acesso -> Preparar programa -> Aprovação da equipe Braga",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }

                            OutlinedTextField(
                                value = email,
                                onValueChange = { email = it },
                                label = { Text("E-mail") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().testTag("email_input"),
                                shape = RoundedCornerShape(14.dp),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
                            )

                            OutlinedTextField(
                                value = password,
                                onValueChange = { password = it },
                                label = { Text("Senha") },
                                singleLine = true,
                                visualTransformation = PasswordVisualTransformation(),
                                modifier = Modifier.fillMaxWidth().testTag("password_input"),
                                shape = RoundedCornerShape(14.dp)
                            )

                            if (emailMode == "signup") {
                                OutlinedTextField(
                                    value = confirmPassword,
                                    onValueChange = { confirmPassword = it },
                                    label = { Text("Confirmar senha") },
                                    singleLine = true,
                                    visualTransformation = PasswordVisualTransformation(),
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(14.dp)
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
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }

                            val isSignupValid = emailMode == "signup" &&
                                email.isNotBlank() &&
                                password.isNotBlank() &&
                                password.length >= 6 &&
                                password == confirmPassword

                            val isLoginValid = emailMode == "login" && email.isNotBlank() && password.isNotBlank()

                            // Botão Principal (ENTRAR ou CRIAR CONTA)
                            Button(
                                onClick = {
                                    if (emailMode == "login") {
                                        viewModel.signInWithEmail(email.trim(), password)
                                    } else {
                                        viewModel.signUpWithEmail(
                                            email.trim(),
                                            password,
                                            businessAccount = signupPurpose == "company"
                                        )
                                    }
                                },
                                enabled = if (emailMode == "login") isLoginValid else isSignupValid,
                                modifier = Modifier.fillMaxWidth().height(54.dp).testTag("login_button"),
                                shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
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
                            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant)
                            Text("ou continuar com", modifier = Modifier.padding(horizontal = 12.dp), color = Color.Gray, fontSize = 13.sp)
                            HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant)
                        }

                        // Botão Secundário: GOOGLE
                        OutlinedButton(
                            onClick = {
                                focusManager.clearFocus(force = true)
                                keyboardController?.hide()
                                triggerGoogleFlow(context, scope, credentialManager) { idToken ->
                                    viewModel.signInWithGoogle(
                                        idToken = idToken,
                                        businessAccount = emailMode == "signup" && signupPurpose == "company"
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(54.dp),
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)
                        ) {
                            Text("Continuar com o Google", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                        }


                        // Alternar entre Entrar e Cadastrar
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                if (emailMode == "login") "Não tem uma conta?" else "Já possui uma conta?",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.Gray
                            )
                            TextButton(
                                onClick = {
                                    emailMode = if (emailMode == "login") "signup" else "login"
                                    if (emailMode == "login") {
                                        signupPurpose = "personal"
                                    }
                                    viewModel.clearAuthState()
                                    signupOtpCode = ""
                                }
                            ) {
                                Text(
                                    if (emailMode == "login") "Cadastre-se" else "Entrar",
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        if (emailMode == "login" && recognizedInvite == null) {
                            OutlinedCard(
                                onClick = {
                                    emailMode = "signup"
                                    signupPurpose = "company"
                                    prefs.edit().putBoolean("pending_b2b_company_onboarding", true).apply()
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
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }

            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
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
                        TextButton(
                            onClick = {
                                if (!blockExternalLinksInTestLab) {
                                    uriHandler.openUri("https://bragasaude.online/termos")
                                }
                            },
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                        ) {
                            Text("Termos de Uso", style = MaterialTheme.typography.labelSmall)
                        }
                        Text("e", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        TextButton(
                            onClick = {
                                if (!blockExternalLinksInTestLab) {
                                    uriHandler.openUri("https://bragasaude.online/privacidade")
                                }
                            },
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                        ) {
                            Text("Privacidade", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    if (recognizedInvite != null || signupPurpose == "company") {
                        Text(
                            text = "Tecnologia Braga Saúde",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        if (showInviteDialog) {
            BragaAlertDialog(
                onDismissRequest = { if (!isLookingUpInvite) showInviteDialog = false },
                title = {
                    Text("Encontre seu programa", fontWeight = FontWeight.Bold)
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "Informe o código que recebeu. Você verá a marca do parceiro antes de entrar ou criar sua conta.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedTextField(
                            value = inviteCodeDraft,
                            onValueChange = { if (it.length <= 32) inviteCodeDraft = it.uppercase() },
                            label = { Text("Código do programa") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("invite_code_input"),
                            shape = RoundedCornerShape(14.dp)
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val code = inviteCodeDraft.trim()
                            if (code.isEmpty() || isLookingUpInvite) return@Button
                            isLookingUpInvite = true
                            scope.launch {
                                val preview = viewModel.lookupPartnerInvitePreview(code)
                                isLookingUpInvite = false
                                if (preview != null) {
                                    recognizedInvite = preview
                                    prefs.edit().putString("pending_b2b_invite_code", preview.inviteCode).apply()
                                    showInviteDialog = false
                                } else {
                                    Toast.makeText(
                                        context,
                                        "Programa não encontrado ou ainda não disponível para adesão.",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                        },
                        enabled = br.com.bragasaude.domain.b2b.B2BOnboardingValidator.isValidInviteCode(inviteCodeDraft) && !isLookingUpInvite
                    ) {
                        Text("Encontrar programa", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { showInviteDialog = false },
                        enabled = !isLookingUpInvite
                    ) {
                        Text("Voltar")
                    }
                }
            )
        }

        // Diálogo de Conta Google Já Existente (Bifurcação de segurança)
        if (googleAccountConflictMessage != null) {
            BragaAlertDialog(
                onDismissRequest = { googleAccountConflictMessage = null },
                title = {
                    Text("Conta Vinculada ao Google", fontWeight = FontWeight.Bold)
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
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
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
        } catch (e: Exception) {
            android.util.Log.e("LoginScreen", "Erro no fluxo Google: ${e::class.simpleName} - ${e.message}")
            val msg = when(e) {
                is androidx.credentials.exceptions.NoCredentialException -> "Nenhuma conta Google encontrada. Verifique se o SHA-1 deste computador está registrado no Google Cloud Console para este Client ID."
                is androidx.credentials.exceptions.GetCredentialCancellationException -> "Login cancelado."
                else -> "Erro: ${e.message}"
            }
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
        }
    }
}
