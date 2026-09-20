package br.com.bragasaude.ui.auth

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
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
import br.com.bragasaude.R
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(
    viewModel: AuthViewModel = hiltViewModel(),
    onLoginSuccess: () -> Unit
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val scope = rememberCoroutineScope()
    val credentialManager = CredentialManager.create(context)
    val authState by viewModel.authState.collectAsState()
    var emailMode by remember { mutableStateOf("login") } // "login", "signup" ou "forgot"
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
            }
            is AuthViewModel.AuthState.OtpVerified -> {
                Toast.makeText(
                    context,
                    "Código verificado com sucesso!",
                    Toast.LENGTH_SHORT
                ).show()
                emailMode = "login"
                viewModel.clearAuthState()
            }
            is AuthViewModel.AuthState.PasswordReset -> {
                Toast.makeText(
                    context,
                    "Senha redefinida com sucesso! Faça login.",
                    Toast.LENGTH_LONG
                ).show()
                emailMode = "login"
                viewModel.clearAuthState()
            }
            else -> {}
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing),
        containerColor = br.com.bragasaude.ui.theme.Background
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(32.dp)
        ) {
            item {
                Spacer(Modifier.height(40.dp))
                // Logo Branding - Escudo com Ritmo Cardíaco
                br.com.bragasaude.ui.components.ShieldEcgIcon(
                    sizeDp = 120.dp
                )
            }

            item {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = stringResource(R.string.app_name_new),
                        style = MaterialTheme.typography.displaySmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.slogan),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.Gray
                    )
                }
            }

            item {
                var email by remember { mutableStateOf("") }
                var password by remember { mutableStateOf("") }
                var confirmPassword by remember { mutableStateOf("") }
                var signupOtpCode by remember { mutableStateOf("") }
                var signupOtpVerified by remember { mutableStateOf(false) }

                Spacer(Modifier.height(16.dp))

                if (authState is AuthViewModel.AuthState.Loading) {
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
                } else if (emailMode == "forgot") {
                    var forgotStep by remember { mutableStateOf(1) }
                    var otpCode by remember { mutableStateOf("") }
                    var newPassword by remember { mutableStateOf("") }
                    var confirmNewPassword by remember { mutableStateOf("") }

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
                                Button(
                                    onClick = {
                                        if (email.isNotBlank()) {
                                            viewModel.sendOtpCode(email)
                                            forgotStep = 2
                                        }
                                    },
                                    enabled = email.isNotBlank(),
                                    modifier = Modifier.fillMaxWidth().height(52.dp),
                                    shape = RoundedCornerShape(14.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                ) {
                                    Text("Enviar código", fontWeight = FontWeight.Bold)
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
                                Button(
                                    onClick = {
                                        if (otpCode.length == 6) {
                                            viewModel.verifyOtpCode(email, otpCode)
                                            forgotStep = 3
                                        }
                                    },
                                    enabled = otpCode.length == 6,
                                    modifier = Modifier.fillMaxWidth().height(52.dp),
                                    shape = RoundedCornerShape(14.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                ) {
                                    Text("Verificar código", fontWeight = FontWeight.Bold)
                                }
                                TextButton(
                                    onClick = { viewModel.sendOtpCode(email) },
                                    modifier = Modifier.align(Alignment.CenterHorizontally)
                                ) {
                                    Text("Reenviar código", color = Color.Gray)
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
                                    label = { Text("Nova senha") },
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
                                Button(
                                    onClick = {
                                        if (newPassword == confirmNewPassword && newPassword.length >= 6) {
                                            viewModel.resetPasswordWithOtp(newPassword)
                                        }
                                    },
                                    enabled = newPassword.isNotBlank() && confirmNewPassword.isNotBlank() &&
                                        newPassword == confirmNewPassword && newPassword.length >= 6,
                                    modifier = Modifier.fillMaxWidth().height(52.dp),
                                    shape = RoundedCornerShape(14.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                ) {
                                    Text("Redefinir senha", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                        TextButton(
                            onClick = {
                                emailMode = "login"
                                viewModel.clearPendingReset()
                            },
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        ) {
                            Text("Voltar ao login", color = Color.Gray)
                        }
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
                                                viewModel.signUpWithEmail(email.trim(), password)
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
                            OutlinedTextField(
                                value = email,
                                onValueChange = { email = it },
                                label = { Text("E-mail") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
                            )

                            OutlinedTextField(
                                value = password,
                                onValueChange = { password = it },
                                label = { Text("Senha") },
                                singleLine = true,
                                visualTransformation = PasswordVisualTransformation(),
                                modifier = Modifier.fillMaxWidth(),
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
                                            text = "Validação Obrigatória de Segurança",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            text = "Vamos enviar um código de 6 dígitos para o seu e-mail para ativar a conta:",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = "O código de 6 dígitos será enviado para o e-mail digitado acima.",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }

                            if (emailMode == "login") {
                                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                                    TextButton(
                                        onClick = { emailMode = "forgot" },
                                        contentPadding = PaddingValues(0.dp)
                                    ) {
                                        Text("Esqueci minha senha", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
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
                                        viewModel.signUpWithEmail(email.trim(), password)
                                    }
                                },
                                enabled = if (emailMode == "login") isLoginValid else isSignupValid,
                                modifier = Modifier.fillMaxWidth().height(54.dp),
                                shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                            ) {
                                Text(
                                    if (emailMode == "login") "Entrar" else "Receber Código e Ativar Conta",
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
                                triggerGoogleFlow(context, scope, credentialManager) { idToken ->
                                    viewModel.signInWithGoogle(idToken)
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
                            onClick = { uriHandler.openUri("https://braga-saude.web.app/terms") },
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                        ) {
                            Text("Termos de Uso", style = MaterialTheme.typography.labelSmall)
                        }
                        Text("e", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        TextButton(
                            onClick = { uriHandler.openUri("https://braga-saude.web.app/privacy") },
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                        ) {
                            Text("Privacidade", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }

        // Diálogo de Conta Google Já Existente (Bifurcação de segurança)
        if (googleAccountConflictMessage != null) {
            AlertDialog(
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
