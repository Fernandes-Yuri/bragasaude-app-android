package br.com.bragasaude.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.compose.rememberNavController
import br.com.bragasaude.MainActivity
import br.com.bragasaude.domain.XpGrantService
import br.com.bragasaude.ui.auth.AppSessionStatus
import br.com.bragasaude.ui.auth.AuthViewModel
import br.com.bragasaude.ui.auth.LoginScreen
import br.com.bragasaude.ui.components.RiskNotificationDialog
import br.com.bragasaude.ui.legal.PrivacyPolicyScreen
import br.com.bragasaude.ui.legal.TermsOfUseScreen
import br.com.bragasaude.ui.navigation.MainScaffold
import br.com.bragasaude.ui.profile.ConsentScreen
import br.com.bragasaude.ui.profile.ProfileScreen
import br.com.bragasaude.ui.registration.CaregiverModeSelectionScreen
import br.com.bragasaude.ui.registration.CaregiverRegistrationScreen
import br.com.bragasaude.ui.registration.RoleSelectionScreen
import br.com.bragasaude.ui.security.DeveloperModeBlockedScreen
import br.com.bragasaude.ui.util.NotificationHelper
import br.com.bragasaude.ui.util.Screen
import br.com.bragasaude.util.DeveloperModeDetector
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.launch

/**
 * Conteúdo principal do app após o setContent da MainActivity.
 * Responsável pela máquina de estados de sessão (auth, consent, perfil, tutorial)
 * e por orquestrar os efeitos de sistema (permissões, serviço de passos, notificações).
 *
 * Extraído da MainActivity para mantê-la abaixo de 60 linhas.
 */
@Composable
fun AppContent(activity: MainActivity) {
    val authViewModel: AuthViewModel = hiltViewModel()
    val mainViewModel: MainViewModel = hiltViewModel()
    val sessionScope = rememberCoroutineScope()

    val sessionStatus by authViewModel.sessionStatus.collectAsState()
    val isProfileComplete by authViewModel.isProfileComplete.collectAsState()
    val profileError by authViewModel.profileError.collectAsState()
    val hasAcceptedConsent by authViewModel.hasAcceptedConsent.collectAsState()
    val activeRisk by mainViewModel.activeRisk.collectAsState()
    val userRole by authViewModel.userRole.collectAsState()
    val caregiverMode by authViewModel.caregiverMode.collectAsState()
    val needsSelfCare by authViewModel.needsSelfCare.collectAsState()
    val suggestPhoneLink by authViewModel.suggestPhoneLink.collectAsState()
    val whatsappTotpSecret by authViewModel.whatsappTotpSecret.collectAsState()
    val whatsappPhone by authViewModel.whatsappPhone.collectAsState()
    var tempRole by rememberSaveable { mutableStateOf<String?>(null) }

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val appContext = context.applicationContext

    var isDevModeBlocked by remember {
        mutableStateOf(DeveloperModeDetector.isDeveloperModeEnabled(context))
    }
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                isDevModeBlocked = DeveloperModeDetector.isDeveloperModeEnabled(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing),
        color = MaterialTheme.colorScheme.background
    ) {
        if (isDevModeBlocked) {
            DeveloperModeBlockedScreen(
                onExit = { activity.finish() },
                onOpenSettings = { DeveloperModeDetector.openDeveloperSettings(context) },
                onRetry = { isDevModeBlocked = DeveloperModeDetector.isDeveloperModeEnabled(context) }
            )
        } else {
            Box(modifier = Modifier.fillMaxSize()) {
                var legalSubView by remember { mutableStateOf<String?>(null) }
                androidx.activity.compose.BackHandler(enabled = legalSubView != null) { legalSubView = null }

                LaunchedEffect(userRole, caregiverMode) {
                    if (caregiverMode == "VIEWER_ONLY") {
                        NotificationHelper.cancelHydrationReminders(appContext)
                    } else if (userRole != null) {
                        NotificationHelper.scheduleHydrationReminders(appContext)
                    }
                }

                val prefs = context.getSharedPreferences("braga_prefs", android.content.Context.MODE_PRIVATE)
                var hasSeenTutorial by remember(userRole) {
                    val seen = prefs.getBoolean("tutorial_seen", false)
                    val seenFor = prefs.getString("tutorial_seen_for_role", null)
                    mutableStateOf(seen && (seenFor == null || seenFor == userRole))
                }
                var hasAcceptedTermsBeforeAuth by remember {
                    mutableStateOf(prefs.getBoolean("terms_accepted_before_auth", false))
                }

                var showSplash by remember { mutableStateOf(true) }
                if (showSplash) {
                    br.com.bragasaude.ui.SplashScreen(onTimeout = { showSplash = false })
                } else {
                    when (val status = sessionStatus) {
                        is AppSessionStatus.Authenticated -> {
                            when {
                                profileError != null -> ProfileErrorState(
                                    error = profileError,
                                    onRetry = authViewModel::retryProfile,
                                    onSignOut = { sessionScope.launch { authViewModel.signOut() } }
                                )
                                hasAcceptedConsent == null || isProfileComplete == null ->
                                    LoadingOverlay(onExit = { sessionScope.launch { authViewModel.signOut() } })
                                hasAcceptedConsent == false -> ConsentFlow(
                                    legalSubView = legalSubView,
                                    onSetLegalSubView = { legalSubView = it },
                                    onConsentAccepted = { authViewModel.setConsentAccepted() },
                                    isPostLogin = true
                                )
                                isProfileComplete == false -> {
                                    val effectiveRole = tempRole ?: userRole
                                    when {
                                        effectiveRole.isNullOrEmpty() -> RoleSelectionScreen(
                                            onBack = { sessionScope.launch { authViewModel.signOut() } },
                                            onRoleSelected = { role ->
                                                tempRole = role
                                                authViewModel.setUserRole(role)
                                            }
                                        )
                                        needsSelfCare -> ProfileScreen(
                                            onBack = {
                                                if (effectiveRole == "CAREGIVER") authViewModel.clearCaregiverMode()
                                                else { tempRole = null; authViewModel.clearOnboardingRole() }
                                            },
                                            onProfileSaved = { authViewModel.setProfileComplete() }
                                        )
                                        effectiveRole == "CAREGIVER" -> {
                                            if (caregiverMode.isNullOrBlank()) {
                                                CaregiverModeSelectionScreen(
                                                    onModeSelected = { authViewModel.setCaregiverMode(it) },
                                                    onBack = { tempRole = null; authViewModel.clearOnboardingRole() }
                                                )
                                            } else {
                                                CaregiverRegistrationScreen(
                                                    onRegistrationComplete = {
                                                        tempRole = null
                                                        activity.requestNotificationPermission()
                                                    },
                                                    onBack = { authViewModel.clearCaregiverMode() }
                                                )
                                            }
                                        }
                                        else -> ProfileScreen(
                                            onBack = { tempRole = null; authViewModel.clearOnboardingRole() },
                                            onProfileSaved = {
                                                authViewModel.setProfileComplete()
                                                activity.requestNotificationPermission()
                                            }
                                        )
                                    }
                                }
                                !hasSeenTutorial -> br.com.bragasaude.ui.onboarding.OnboardingTourScreen(
                                    userRole = userRole,
                                    totpSecret = whatsappTotpSecret,
                                    whatsappPhone = whatsappPhone,
                                    onFinishTour = { hasSeenTutorial = true }
                                )
                                isProfileComplete == true -> {
                                    // Permissões e serviço de rastreamento
                                    LaunchedEffect(Unit) {
                                        mainViewModel.permissionRequestSignal.collect {
                                            val permissions = mutableListOf(
                                                Manifest.permission.ACCESS_FINE_LOCATION,
                                                Manifest.permission.ACCESS_COARSE_LOCATION
                                            ).also { list ->
                                                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q)
                                                    list.add(Manifest.permission.ACTIVITY_RECOGNITION)
                                            }
                                            val allGranted = permissions.all {
                                                ContextCompat.checkSelfPermission(activity, it) == PackageManager.PERMISSION_GRANTED
                                            }
                                            if (!allGranted) {
                                                val shouldShowRationale = permissions.any {
                                                    androidx.core.app.ActivityCompat.shouldShowRequestPermissionRationale(activity, it)
                                                }
                                                val askedBefore = prefs.getBoolean("asked_tracking_permissions", false)
                                                if (!shouldShowRationale && askedBefore) activity.openAppSettings()
                                                else {
                                                    prefs.edit().putBoolean("asked_tracking_permissions", true).apply()
                                                    activity.requestTrackingPermissions()
                                                }
                                            }
                                        }
                                    }
                                    LaunchedEffect(Unit) {
                                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                                            val hasNotif = ContextCompat.checkSelfPermission(activity, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
                                            if (!hasNotif) {
                                                val lastPrompt = prefs.getLong("last_notification_prompt", 0L)
                                                if (System.currentTimeMillis() - lastPrompt > 24 * 60 * 60 * 1000) {
                                                    activity.requestNotificationPermission()
                                                }
                                            }
                                        }
                                    }
                                    LaunchedEffect(activity.intent) {
                                        val hasLocation = ContextCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                                        val hasActivity = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                                            ContextCompat.checkSelfPermission(activity, Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED
                                        } else true
                                        if (hasLocation && hasActivity) activity.startTrackingService()
                                    }

                                    val openHydration = activity.intent.getBooleanExtra("OPEN_HYDRATION", false)
                                    val openFamilyMessages = activity.intent.getBooleanExtra("OPEN_FAMILY_MESSAGES", false)
                                    val initialScreen: Screen = when {
                                        openFamilyMessages -> Screen.FamilyChat
                                        openHydration -> Screen.Hydration()
                                        else -> Screen.Home
                                    }
                                    val startDestination = if (userRole == "CAREGIVER" && caregiverMode != "HYBRID") {
                                        Screen.CaregiverDashboard
                                    } else initialScreen

                                    val xpService = remember {
                                        EntryPointAccessors.fromApplication(appContext, XpToastEntryPoint::class.java)
                                            .xpGrantService()
                                    }
                                    val navController = rememberNavController()
                                    MainScaffold(
                                        navController = navController,
                                        startDestination = startDestination,
                                        userRole = userRole,
                                        caregiverMode = caregiverMode,
                                        xpGrantService = xpService
                                    )
                                }
                                else -> LoadingOverlay()
                            }
                        }
                        is AppSessionStatus.Initializing -> LoadingOverlay()
                        else -> {
                            if (!hasAcceptedTermsBeforeAuth) {
                                ConsentFlow(
                                    legalSubView = legalSubView,
                                    onSetLegalSubView = { legalSubView = it },
                                    onConsentAccepted = {
                                        appContext.getSharedPreferences("braga_prefs", android.content.Context.MODE_PRIVATE)
                                            .edit().putBoolean("terms_accepted_before_auth", true).apply()
                                        hasAcceptedTermsBeforeAuth = true
                                    },
                                    isPostLogin = false
                                )
                            } else {
                                LoginScreen(onLoginSuccess = {})
                            }
                        }
                    }
                }

                // Risco clínico global
                activeRisk?.let { risk ->
                    RiskNotificationDialog(
                        type = risk.type,
                        message = risk.message,
                        onDismiss = { mainViewModel.dismissRisk() }
                    )
                }

                // Sugestão de vinculação WhatsApp para usuários Google.
                // Fluxo TOTP: abre o WhatsApp com o código pronto (sem OTP por texto).
                if (suggestPhoneLink) {
                    val waLink by authViewModel.openWhatsAppLinkEvent.collectAsState()
                    LaunchedEffect(waLink) {
                        waLink?.let { url ->
                            runCatching {
                                context.startActivity(
                                    android.content.Intent(
                                        android.content.Intent.ACTION_VIEW,
                                        android.net.Uri.parse(url)
                                    ).apply { flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK }
                                )
                            }
                            authViewModel.consumeOpenWhatsAppLink()
                        }
                    }
                    AlertDialog(
                        onDismissRequest = { authViewModel.dismissPhoneLinkSuggestion() },
                        title = { Text("Vincular seu WhatsApp") },
                        text = {
                            Text(
                                "Para receber lembretes e avisos no WhatsApp, vamos abrir o " +
                                "WhatsApp com uma mensagem pronta. É só enviar."
                            )
                        },
                        confirmButton = {
                            TextButton(onClick = { authViewModel.openWhatsAppLink() }) {
                                Text("Abrir WhatsApp")
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { authViewModel.dismissPhoneLinkSuggestion() }) {
                                Text("Agora não")
                            }
                        }
                    )
                }
            }
        }
    }
}

// ==================== Helpers locais ====================

@Composable
fun LoadingOverlay(onExit: (() -> Unit)? = null) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(strokeWidth = 3.dp)
            if (onExit != null) TextButton(onClick = onExit) { Text("Sair e tentar entrar novamente") }
        }
    }
}

@Composable
private fun ProfileErrorState(error: String?, onRetry: () -> Unit, onSignOut: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Não foi possível verificar seu cadastro", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(16.dp))
        Text(error.orEmpty())
        Spacer(Modifier.height(24.dp))
        Button(onClick = onRetry) { Text("Tentar novamente") }
        TextButton(onClick = onSignOut) { Text("Sair da conta") }
    }
}

@Composable
private fun ConsentFlow(
    legalSubView: String?,
    onSetLegalSubView: (String?) -> Unit,
    onConsentAccepted: () -> Unit,
    isPostLogin: Boolean
) {
    when (legalSubView) {
        "terms" -> TermsOfUseScreen(
            onBack = { onSetLegalSubView(null) },
            onAccept = if (isPostLogin) ({ onSetLegalSubView(null); onConsentAccepted() }) else null,
            isAcceptanceFlow = isPostLogin
        )
        "privacy" -> PrivacyPolicyScreen(onBack = { onSetLegalSubView(null) })
        else -> ConsentScreen(
            onAccepted = onConsentAccepted,
            onNavigateToTerms = { onSetLegalSubView("terms") },
            onNavigateToPrivacy = { onSetLegalSubView("privacy") }
        )
    }
}

/** Acesso ao XpGrantService a partir de composables sem ViewModel. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface XpToastEntryPoint {
    fun xpGrantService(): XpGrantService
}