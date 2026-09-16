package br.com.bragasaude

import android.os.Bundle
import kotlinx.coroutines.launch
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import br.com.bragasaude.ui.MainViewModel
import br.com.bragasaude.ui.SplashScreen
import br.com.bragasaude.ui.components.RiskNotificationDialog
import br.com.bragasaude.ui.util.NotificationHelper
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import br.com.bragasaude.ui.auth.AuthViewModel
import br.com.bragasaude.ui.auth.LoginScreen
import br.com.bragasaude.ui.biometry.BiometryScreen
import br.com.bragasaude.ui.components.DraggableAiAssistantFab
import br.com.bragasaude.ui.components.RedCrossIcon
import br.com.bragasaude.ui.components.WhiteHeartIcon
import br.com.bragasaude.ui.components.XpToastHost
import br.com.bragasaude.domain.XpGrantService
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import br.com.bragasaude.ui.exams.ExamsScreen
import br.com.bragasaude.ui.feedback.FeedbackScreen
import br.com.bragasaude.ui.home.HomeScreen
import br.com.bragasaude.ui.home.NotificationsScreen
import br.com.bragasaude.ui.league.LeagueScreen
import br.com.bragasaude.ui.social.SocialFeedScreen
import br.com.bragasaude.ui.profile.SettingsScreen
import br.com.bragasaude.ui.hydration.HydrationScreen
import br.com.bragasaude.ui.milestones.MilestonesScreen
import br.com.bragasaude.ui.reminders.RemindersScreen
import br.com.bragasaude.ui.steps.StepsScreen
import br.com.bragasaude.ui.modules.CareModulesScreen
import br.com.bragasaude.ui.nutrition.NutritionScreen
import br.com.bragasaude.ui.profile.ProfileDetailScreen
import br.com.bragasaude.ui.profile.ProfileScreen
import br.com.bragasaude.ui.registration.RegistrationSelectorScreen
import br.com.bragasaude.ui.report.MedicalReportScreen
import br.com.bragasaude.ui.theme.BragasaudeTheme
import br.com.bragasaude.ui.util.Screen
import br.com.bragasaude.ui.vitals.VitalSignsScreen
import br.com.bragasaude.ui.family.FamilyConnectScreen
import br.com.bragasaude.ui.family.FamilyChatScreen
import br.com.bragasaude.ui.family.CaregiverDashboardScreen
import br.com.bragasaude.ui.nutrition.PantryRecipesScreen
import br.com.bragasaude.ui.registration.RoleSelectionScreen
import br.com.bragasaude.ui.registration.CaregiverRegistrationScreen
import br.com.bragasaude.ui.registration.CaregiverModeSelectionScreen
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import br.com.bragasaude.ui.auth.AppSessionStatus

import br.com.bragasaude.ui.profile.ConsentScreen
import br.com.bragasaude.ui.legal.TermsOfUseScreen
import br.com.bragasaude.ui.legal.PrivacyPolicyScreen
import br.com.bragasaude.ui.onboarding.OnboardingTourScreen
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat

import br.com.bragasaude.ui.util.StepTrackingService
import android.content.Intent
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import br.com.bragasaude.util.DeveloperModeDetector
import br.com.bragasaude.ui.security.DeveloperModeBlockedScreen

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        // Se as permissões forem concedidas e o perfil estiver completo, inicia o serviço
        val locationGranted = results[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val activityGranted = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            results[Manifest.permission.ACTIVITY_RECOGNITION] ?: false
        } else true

        if (locationGranted && activityGranted) {
            startTrackingService()
        }
    }

    private fun startTrackingService() {
        val intent = Intent(this, StepTrackingService::class.java)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun requestNotificationPermission() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
                
                // Atualiza o timer do "uma vez por dia"
                val prefs = getSharedPreferences("braga_prefs", MODE_PRIVATE)
                prefs.edit().putLong("last_notification_prompt", System.currentTimeMillis()).apply()
            }
        }
    }

    private fun openAppSettings() {
        val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = android.net.Uri.fromParts("package", packageName, null)
        }
        startActivity(intent)
    }

    private fun requestTrackingPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            permissions.add(Manifest.permission.ACTIVITY_RECOGNITION)
        }
        
        val toRequest = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (toRequest.isNotEmpty()) {
            requestPermissionLauncher.launch(toRequest.toTypedArray())
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        setContent {
            BragasaudeTheme {
                val authViewModel: AuthViewModel = hiltViewModel()
                val sessionScope = rememberCoroutineScope()
                val mainViewModel: MainViewModel = hiltViewModel()
                
                val sessionStatus by authViewModel.sessionStatus.collectAsState()
                val isProfileComplete by authViewModel.isProfileComplete.collectAsState()
                val profileError by authViewModel.profileError.collectAsState()
                val hasAcceptedConsent by authViewModel.hasAcceptedConsent.collectAsState()
                val activeRisk by mainViewModel.activeRisk.collectAsState()
                val userRole by authViewModel.userRole.collectAsState()
                val caregiverMode by authViewModel.caregiverMode.collectAsState()
                val needsSelfCare by authViewModel.needsSelfCare.collectAsState()
                val suggestPhoneLink by authViewModel.suggestPhoneLink.collectAsState()                
                var tempRole by rememberSaveable { mutableStateOf<String?>(null) }

                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(WindowInsets.safeDrawing),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val context = LocalContext.current
                    val lifecycleOwner = LocalLifecycleOwner.current

                    var isDevModeBlocked by remember {
                        mutableStateOf(DeveloperModeDetector.isDeveloperModeEnabled(context))
                    }
                    DisposableEffect(lifecycleOwner) {
                        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                                isDevModeBlocked =
                                    DeveloperModeDetector.isDeveloperModeEnabled(context)
                            }
                        }
                        lifecycleOwner.lifecycle.addObserver(observer)
                        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
                    }
                    if (isDevModeBlocked) {
                        DeveloperModeBlockedScreen(
                            onExit = { finish() },
                            onOpenSettings = {
                                DeveloperModeDetector.openDeveloperSettings(context)
                            },
                            onRetry = {
                                isDevModeBlocked =
                                    DeveloperModeDetector.isDeveloperModeEnabled(context)
                            }
                        )
                    } else {
                        Box(modifier = Modifier.fillMaxSize()) {
                        var legalSubView by remember { mutableStateOf<String?>(null) }
                        androidx.activity.compose.BackHandler(enabled = legalSubView != null) { legalSubView = null }
                        val appContext = LocalContext.current.applicationContext

                        // Agendar lembretes de hidratação para autocuidado e modo híbrido (cancela apenas se for cuidador exclusivo)
                        LaunchedEffect(userRole, caregiverMode) {
                            if (caregiverMode == "VIEWER_ONLY") {
                                NotificationHelper.cancelHydrationReminders(appContext)
                            } else if (userRole != null) {
                                NotificationHelper.scheduleHydrationReminders(appContext)
                            }
                        }

                        // MÓDULO 5: tutorial específico por papel — resetar se userRole mudou
                        val prefs = LocalContext.current.getSharedPreferences("braga_prefs", android.content.Context.MODE_PRIVATE)
                        var hasSeenTutorial by remember(userRole) {
                            val seen = prefs.getBoolean("tutorial_seen", false)
                            val seenFor = prefs.getString("tutorial_seen_for_role", null)
                            mutableStateOf(seen && (seenFor == null || seenFor == userRole))
                        }

                        // MÓDULO 5 (LGPD): consentimento pré-autenticação
                        var hasAcceptedTermsBeforeAuth by remember {
                            mutableStateOf(prefs.getBoolean("terms_accepted_before_auth", false))
                        }

                        // Splash screen with 1.2s timeout
                        var showSplash by remember { mutableStateOf(true) }
                        
                        if (showSplash) {
                            SplashScreen(
                                onTimeout = {
                                    showSplash = false
                                }
                            )
                        } else {
                            when (val status = sessionStatus) {
                                is AppSessionStatus.Authenticated -> {
                                    when {
                                        profileError != null -> {
                                            Column(Modifier.fillMaxSize().padding(24.dp),
                                                verticalArrangement = Arrangement.Center,
                                                horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                                                Text("Não foi possível verificar seu cadastro", style = MaterialTheme.typography.titleLarge)
                                                Spacer(Modifier.height(16.dp))
                                                Text(profileError.orEmpty())
                                                Spacer(Modifier.height(24.dp))
                                                Button(onClick = authViewModel::retryProfile) { Text("Tentar novamente") }
                                                TextButton(onClick = { sessionScope.launch { authViewModel.signOut() } }) { Text("Sair da conta") }
                                            }
                                        }
                                        // 1. Enquanto os estados assíncronos do perfil estão carregando, mantém overlay (evita qualquer flash de tela)
                                        hasAcceptedConsent == null || isProfileComplete == null -> {
                                            LoadingOverlay(onExit = { sessionScope.launch { authViewModel.signOut() } })
                                        }
                                        // 2. Termo de consentimento pós-login (caso ainda não aceito)
                                        hasAcceptedConsent == false -> {
                                            when (legalSubView) {
                                                "terms" -> TermsOfUseScreen(
                                                    onBack = { legalSubView = null },
                                                    onAccept = {
                                                        legalSubView = null
                                                        authViewModel.setConsentAccepted()
                                                    },
                                                    isAcceptanceFlow = true
                                                )
                                                "privacy" -> PrivacyPolicyScreen(
                                                    onBack = { legalSubView = null }
                                                )
                                                else -> ConsentScreen(
                                                    onAccepted = {
                                                        authViewModel.setConsentAccepted()
                                                    },
                                                    onNavigateToTerms = { legalSubView = "terms" },
                                                    onNavigateToPrivacy = { legalSubView = "privacy" }
                                                )
                                            }
                                        }
                                        // 3. Perfil incompleto: bifurcação clara por papel
                                        isProfileComplete == false -> {
                                            val effectiveRole = tempRole ?: userRole
                                            when {
                                                effectiveRole.isNullOrEmpty() -> {
                                                    RoleSelectionScreen(
                                                        onBack = { sessionScope.launch { authViewModel.signOut() } },
                                                        onRoleSelected = { selectedRole ->
                                                            tempRole = selectedRole
                                                            authViewModel.setUserRole(selectedRole)
                                                        }
                                                    )
                                                }
                                                needsSelfCare -> {
                                                    ProfileScreen(onBack = {
                                                        if (effectiveRole == "CAREGIVER") authViewModel.clearCaregiverMode()
                                                        else { tempRole = null; authViewModel.clearOnboardingRole() }
                                                    }, onProfileSaved = { authViewModel.setProfileComplete() })
                                                }
                                                effectiveRole == "CAREGIVER" -> {
                                                    // Fluxo ordenado: papel → escolha de modo → dados + conexão.
                                                    // A escolha de modo vem ANTES do cadastro/QR Code.
                                                    if (caregiverMode.isNullOrBlank()) {
                                                        CaregiverModeSelectionScreen(
                                                            onModeSelected = { mode ->
                                                                authViewModel.setCaregiverMode(mode)
                                                            },
                                                            onBack = {
                                                                tempRole = null
                                                                authViewModel.clearOnboardingRole()
                                                            }
                                                        )
                                                    } else {
                                                        CaregiverRegistrationScreen(
                                                            onRegistrationComplete = {
                                                                // Perfil completo persistido e conexão OK.
                                                                // Segue direto para o painel do familiar.
                                                                tempRole = null
                                                                requestNotificationPermission()
                                                            },
                                                            onBack = {
                                                                // Volta para a escolha de modo
                                                                authViewModel.clearCaregiverMode()
                                                            }
                                                        )
                                                    }
                                                }
                                                else -> {
                                                    // PATIENT
                                                    ProfileScreen(
                                                        onBack = { tempRole = null; authViewModel.clearOnboardingRole() },
                                                        onProfileSaved = {
                                                            authViewModel.setProfileComplete()
                                                            requestNotificationPermission()
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                        // 4. Perfil completo, mas ainda não viu o tutorial: exibe tutorial com calma e sem interrupções
                                        !hasSeenTutorial -> {
                                            OnboardingTourScreen(
                                                userRole = userRole,
                                                onFinishTour = {
                                                    hasSeenTutorial = true
                                                }
                                            )
                                        }
                                        // 5. Perfil completo e tutorial concluído: entra na aplicação principal
                                        isProfileComplete == true -> {
                                            LaunchedEffect(Unit) {
                                                mainViewModel.permissionRequestSignal.collect {
                                                    val permissions = mutableListOf(
                                                        Manifest.permission.ACCESS_FINE_LOCATION,
                                                        Manifest.permission.ACCESS_COARSE_LOCATION
                                                    )
                                                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                                                        permissions.add(Manifest.permission.ACTIVITY_RECOGNITION)
                                                    }
                                                    val allGranted = permissions.all {
                                                        ContextCompat.checkSelfPermission(this@MainActivity, it) == PackageManager.PERMISSION_GRANTED
                                                    }
                                                    if (!allGranted) {
                                                        val shouldShowRationale = permissions.any {
                                                            androidx.core.app.ActivityCompat.shouldShowRequestPermissionRationale(this@MainActivity, it)
                                                        }
                                                        if (!shouldShowRationale) {
                                                            val prefs = getSharedPreferences("braga_prefs", MODE_PRIVATE)
                                                            val askedBefore = prefs.getBoolean("asked_tracking_permissions", false)
                                                            if (askedBefore) {
                                                                openAppSettings()
                                                            } else {
                                                                prefs.edit().putBoolean("asked_tracking_permissions", true).apply()
                                                                requestTrackingPermissions()
                                                            }
                                                        } else {
                                                            requestTrackingPermissions()
                                                        }
                                                    }
                                                }
                                            }
                                            LaunchedEffect(Unit) {
                                                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                                                    val hasNotifications = ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
                                                    if (!hasNotifications) {
                                                        val prefs = getSharedPreferences("braga_prefs", MODE_PRIVATE)
                                                        val lastPrompt = prefs.getLong("last_notification_prompt", 0L)
                                                        val now = System.currentTimeMillis()
                                                        if (now - lastPrompt > 24 * 60 * 60 * 1000) {
                                                            requestNotificationPermission()
                                                        }
                                                    }
                                                }
                                            }
                                            LaunchedEffect(intent) {
                                                val hasLocation = ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                                                val hasActivity = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                                                    ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED
                                                } else true
                                                if (hasLocation && hasActivity) {
                                                    startTrackingService()
                                                }
                                            }
                                            val openHydration = intent.getBooleanExtra("OPEN_HYDRATION", false)
                                            val openFamilyMessages = intent.getBooleanExtra("OPEN_FAMILY_MESSAGES", false)
                                            BragasaudeApp(
                                                initialScreen = when {
                                                    openFamilyMessages -> Screen.FamilyChat
                                                    openHydration -> Screen.Hydration()
                                                    else -> Screen.Home
                                                },
                                                userRole = userRole,
                                                caregiverMode = caregiverMode
                                            )
                                        }
                                        else -> LoadingOverlay()
                                    }
                                }
                                is AppSessionStatus.Initializing -> {
                                    LoadingOverlay()
                                }
                                else -> {
                                    // MÓDULO 5 (LGPD): consentimento vem ANTES da autenticação.
                                    // Usuário lê e aceita Termos+Privacidade sem ainda termos
                                    // coletado nenhum dado pessoal.
                                    if (!hasAcceptedTermsBeforeAuth) {
                                        when (legalSubView) {
                                            "terms" -> TermsOfUseScreen(
                                                onBack = { legalSubView = null }
                                            )
                                            "privacy" -> PrivacyPolicyScreen(
                                                onBack = { legalSubView = null }
                                            )
                                            else -> ConsentScreen(
                                                onAccepted = {
                                                    appContext.getSharedPreferences("braga_prefs", android.content.Context.MODE_PRIVATE)
                                                        .edit()
                                                        .putBoolean("terms_accepted_before_auth", true)
                                                        .apply()
                                                    hasAcceptedTermsBeforeAuth = true
                                                },
                                                onNavigateToTerms = { legalSubView = "terms" },
                                                onNavigateToPrivacy = { legalSubView = "privacy" }
                                            )
                                        }
                                    } else {
                                        LoginScreen(onLoginSuccess = {})
                                    }
                                }
                            }
                        }
                    }
                        
                    // Exibição Global de Risco
                    activeRisk?.let { risk ->
                        RiskNotificationDialog(
                            type = risk.type,
                            message = risk.message,
                            onDismiss = { mainViewModel.dismissRisk() }
                        )
                    }

                    // Sugestão Não-Obrigatória de WhatsApp para Usuários Google
                    if (suggestPhoneLink) {
                        val phoneLinkSent by authViewModel.phoneLinkSent.collectAsState()
                        val phoneLinkWa by authViewModel.phoneLinkWa.collectAsState()
                        br.com.bragasaude.ui.components.PhoneLinkDialog(
                            onDismiss = { authViewModel.dismissPhoneLinkSuggestion() },
                            onSendOtp = { phone -> authViewModel.sendPhoneLinkOtp(phone) },
                            onVerifyOtp = { phone, code -> authViewModel.verifyPhoneLinkOtp(phone, code) },
                            codeSent = phoneLinkSent,
                            waLink = phoneLinkWa,
                            onOpenWhatsApp = { link ->
                                try {
                                    context.startActivity(android.content.Intent(
                                        android.content.Intent.ACTION_VIEW,
                                        android.net.Uri.parse(link)
                                    ).apply { flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK })
                                } catch (_: Exception) { }
                            }
                        )
                    }
                }
            }
        }
    }
}
}

@Composable
fun LoadingOverlay(onExit: (() -> Unit)? = null) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(strokeWidth = 3.dp)
            if (onExit != null) TextButton(onClick = onExit) { Text("Sair e tentar entrar novamente") }
        }
    }
}

@Composable
fun BragasaudeApp(
    initialScreen: Screen = Screen.Home,
    userRole: String? = null,
    caregiverMode: String? = null
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val navigateBack: () -> Unit = {
        if (navController.previousBackStackEntry != null) {
            navController.popBackStack()
        } else {
            navController.navigate(if (userRole == "CAREGIVER" && caregiverMode != "HYBRID") Screen.Profile else Screen.Home) {
                launchSingleTop = true
            }
        }
    }

    val appContext = LocalContext.current.applicationContext
    val xpService = remember {
        EntryPointAccessors.fromApplication(appContext, XpToastEntryPoint::class.java)
            .xpGrantService()
    }
    
    val patientNavItems = listOf(
        BottomNavItem("Início", Icons.Default.Home, Screen.Home),
        BottomNavItem("Dados", Icons.Default.BarChart, Screen.Report),
        BottomNavItem("Planos", Icons.Default.Restaurant, Screen.Nutrition()),
        BottomNavItem("Braga", Icons.Default.ChatBubbleOutline, Screen.OrbChat),
        BottomNavItem("Perfil", Icons.Default.Person, Screen.Profile)
    )
    
    // MÓDULO 6: cuidador HÍBRIDO tem experiência completa (saúde própria + painel do familiar)
    val hybridCaregiverNavItems = listOf(
        BottomNavItem("Início", Icons.Default.Home, Screen.Home),
        BottomNavItem("Painel", Icons.Default.Diversity3, Screen.CaregiverDashboard),
        BottomNavItem("Planos", Icons.Default.Restaurant, Screen.Nutrition()),
        BottomNavItem("Braga", Icons.Default.ChatBubbleOutline, Screen.OrbChat),
        BottomNavItem("Perfil", Icons.Default.Person, Screen.Profile)
    )
    
    // MÓDULO 6: cuidador VIEWER_ONLY só acompanha o familiar (sem saúde própria)
    val viewerCaregiverNavItems = listOf(
        BottomNavItem("Painel", Icons.Default.Diversity3, Screen.CaregiverDashboard),
        BottomNavItem("Despensa", Icons.Default.ShoppingCart, Screen.PantryRecipes),
        BottomNavItem("Mural", Icons.Default.Groups, Screen.SocialFeed),
        BottomNavItem("Braga", Icons.Default.ChatBubbleOutline, Screen.OrbChat),
        BottomNavItem("Perfil", Icons.Default.Person, Screen.Profile)
    )
    
    // MÓDULO 6: 3 variantes de navegação por papel + modo
    val navItems = when {
        userRole == "CAREGIVER" && caregiverMode == "HYBRID" -> hybridCaregiverNavItems
        userRole == "CAREGIVER" -> viewerCaregiverNavItems
        else -> patientNavItems
    }
    val startDestination = if (userRole == "CAREGIVER" && caregiverMode != "HYBRID") {
        Screen.CaregiverDashboard
    } else {
        initialScreen
    }

    Box(modifier = Modifier.fillMaxSize()) {
    Scaffold(
        snackbarHost = { XpToastHost(xpGrantService = xpService) },
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar(
                modifier = Modifier.height(64.dp),
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 0.dp
            ) {
                navItems.forEach { item ->
                    val selected = currentRoute?.contains(item.route::class.simpleName ?: "") == true
                    NavigationBarItem(
                        icon = { Icon(item.icon, contentDescription = item.label) },
                        label = { Text(item.label, style = MaterialTheme.typography.labelSmall) },
                        selected = selected,
                        onClick = {
                            // Limpa toda a pilha até a tela inicial para fechar menus do FAB
                            navController.navigate(item.route) {
                                popUpTo(navController.graph.startDestinationId) {
                                    inclusive = false
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            unselectedIconColor = MaterialTheme.colorScheme.outline,
                            unselectedTextColor = MaterialTheme.colorScheme.outline,
                            indicatorColor = androidx.compose.ui.graphics.Color.Transparent
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            composable<Screen.OrbChat> {
                br.com.bragasaude.ui.chat.OrbChatScreen(
                    onBack = navigateBack, onNavigate = { navController.navigate(it) })
            }
            composable<Screen.Home> { 
                HomeScreen(
                    onNavigateToNotifications = { navController.navigate(Screen.Notifications) },
                    onNavigateToScreen = { navController.navigate(it) }
                ) 
            }
            composable<Screen.Vitals> { backStackEntry ->
                val vitals: Screen.Vitals = backStackEntry.toRoute()
                VitalSignsScreen(
                    initialType = vitals.type,
                    initialValue = vitals.initialValue,
                    onBack = { navigateBack() }
                )
            }
            composable<Screen.Biometry> { 
                BiometryScreen(onBack = { navigateBack() }) 
            }
            composable<Screen.Modules> { CareModulesScreen() }
            composable<Screen.Milestones> { MilestonesScreen() }
            composable<Screen.Hydration> { backStackEntry ->
                val hydration: Screen.Hydration = backStackEntry.toRoute()
                HydrationScreen(
                    initialMl = hydration.initialMl,
                    autoOpenDialog = hydration.autoOpenDialog,
                    onBack = { navigateBack() }
                ) 
            }
            composable<Screen.Wearables> {
                br.com.bragasaude.ui.devices.WearablesScreen(onBack = navigateBack)
            }
            composable<Screen.HealthReadings> { entry ->
                val route: Screen.HealthReadings = entry.toRoute()
                br.com.bragasaude.ui.devices.HealthReadingsScreen(route.metric, route.initialValue, navigateBack, { navController.navigate(Screen.Wearables) })
            }
            composable<Screen.Steps> { 
                StepsScreen(onBack = navigateBack, onConnectWatch = { navController.navigate(Screen.Wearables) }) 
            }
            composable<Screen.Reminders> { 
                RemindersScreen(onBack = { navigateBack() }) 
            }
            composable<Screen.Exams> { 
                ExamsScreen(
                    onBack = { navigateBack() },
                    onNavigateToEvolution = { navController.navigate(Screen.Report) }
                ) 
            }
            composable<Screen.Notifications> { 
                NotificationsScreen(onBack = { navigateBack() }) 
            }
            composable<Screen.Settings> {
                SettingsScreen(
                    onBack = { navigateBack() },
                    onLogout = {
                        // Cancelar lembretes de hidratação pendentes
                        NotificationHelper.cancelHydrationReminders(appContext)
                        navController.navigate(Screen.RoleSelection) {
                            popUpTo(navController.graph.startDestinationId) { inclusive = true }
                        }
                    },
                    onNavigateToFeedback = { navController.navigate(Screen.Feedback) },
                    onNavigateToTerms = { navController.navigate(Screen.TermsOfUse) },
                    onNavigateToPrivacy = { navController.navigate(Screen.PrivacyPolicy) },
                    onNavigateToTutorial = { navController.navigate(Screen.OnboardingTour) }
                )
            }
            composable<Screen.TermsOfUse> {
                TermsOfUseScreen(onBack = { navigateBack() })
            }
            composable<Screen.PrivacyPolicy> {
                PrivacyPolicyScreen(onBack = { navigateBack() })
            }
            composable<Screen.OnboardingTour> {
                OnboardingTourScreen(onFinishTour = { navigateBack() })
            }
            composable<Screen.Feedback> {
                FeedbackScreen(onBack = { navigateBack() })
            }
            composable<Screen.Nutrition> { backStackEntry ->
                val nutrition: Screen.Nutrition = backStackEntry.toRoute()
                NutritionScreen(
                    searchFood = nutrition.searchFood,
                    openGroceryList = nutrition.openGroceryList,
                    suggestedGroceryItems = nutrition.groceryItems,
                    onNavigateToPantryRecipes = {
                        navController.navigate(Screen.PantryRecipes)
                    }
                )
            }
            composable<Screen.Report> { 
                MedicalReportScreen(onBack = { navigateBack() }) 
            }
            composable<Screen.AddData> {
                RegistrationSelectorScreen(
                    onNavigate = { dest -> 
                        // Mapeamento simples para o seletor antigo que ainda usa String ou adaptar o seletor
                        navController.navigate(dest) 
                    },
                    onClose = { navigateBack() }
                )
            }
            composable<Screen.Profile> {
                ProfileDetailScreen(
                    onEditProfile = { navController.navigate(Screen.ProfileEdit) },
                    onNavigateToSettings = { navController.navigate(Screen.Settings) },
                    onAddCaregiving = { navController.navigate(Screen.CaregiverRegistration) },
                    onNavigateToWearables = { navController.navigate(Screen.Wearables) }
                )
            }
            composable<Screen.ProfileEdit> {
                ProfileScreen(onBack = navigateBack, onProfileSaved = {
                    navigateBack()
                })
            }
            composable<Screen.League> {
                LeagueScreen(onBack = { navigateBack() })
            }
            composable<Screen.SocialFeed> {
                SocialFeedFeedBackWrapper(onBack = { navigateBack() })
            }
            composable<Screen.FamilyConnect> { 
                FamilyConnectScreen(onClose = { navigateBack() }) 
            }
            composable<Screen.FamilyChat> { 
                FamilyChatScreen(
                    onBack = { navigateBack() },
                    onNavigateToConnect = { navController.navigate(Screen.FamilyConnect) }
                ) 
            }
            composable<Screen.CaregiverDashboard> { 
                CaregiverDashboardScreen(
                    onBack = navigateBack, 
                    onConnectFamily = { navController.navigate(Screen.CaregiverRegistration) },
                    onOpenChat = { navController.navigate(Screen.FamilyChat) }
                ) 
            }
            composable<Screen.PantryRecipes> { 
                PantryRecipesScreen(onBack = { navigateBack() }) 
            }
            composable<Screen.RoleSelection> { 
                RoleSelectionScreen(onBack = navigateBack, onRoleSelected = { role ->
                    if (role == "CAREGIVER") {
                        navController.navigate(Screen.CaregiverRegistration) { 
                            popUpTo(Screen.RoleSelection) { inclusive = true } 
                        }
                    } else {
                        navController.navigate(Screen.ProfileEdit) { 
                            popUpTo(Screen.RoleSelection) { inclusive = true } 
                        }
                    }
                }) 
            }
            composable<Screen.CaregiverRegistration> { 
                CaregiverRegistrationScreen(
                    onRegistrationComplete = { 
                        navController.navigate(Screen.CaregiverDashboard) { 
                            popUpTo(Screen.CaregiverRegistration) { inclusive = true }
                            launchSingleTop = true 
                        }
                    }, 
                    onBack = { navigateBack() }
                ) 
            }
        }
    }

    // UI-V01 / D14: Copiloto de Voz Metamórfico flutuante e arrastável
    // (respeita o toggle "Assistente por voz" das Configurações)
    if (currentRoute?.contains("OrbChat") != true && br.com.bragasaude.util.AppPreferences.isVoiceAssistantEnabled(appContext)) {
        DraggableAiAssistantFab(
            onNavigate = { screen -> navController.navigate(screen) },
            modifier = Modifier.fillMaxSize()
        )
    }
    }
}

@Composable
private fun SocialFeedFeedBackWrapper(onBack: () -> Unit) {
    SocialFeedScreen(onBack = onBack)
}

/** Acesso ao XpGrantService a partir de composables sem ViewModel. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface XpToastEntryPoint {
    fun xpGrantService(): XpGrantService
}

data class BottomNavItem(
    val label: String,
    val icon: ImageVector,
    val route: Screen
)
