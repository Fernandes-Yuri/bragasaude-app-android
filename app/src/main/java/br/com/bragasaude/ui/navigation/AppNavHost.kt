package br.com.bragasaude.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import br.com.bragasaude.ui.util.Screen
import br.com.bragasaude.ui.home.HomeScreen
import br.com.bragasaude.ui.home.NotificationsScreen
import br.com.bragasaude.ui.vitals.VitalSignsScreen
import br.com.bragasaude.ui.biometry.BiometryScreen
import br.com.bragasaude.ui.hydration.HydrationScreen
import br.com.bragasaude.ui.steps.StepsScreen
import br.com.bragasaude.ui.nutrition.NutritionScreen
import br.com.bragasaude.ui.nutrition.PantryRecipesScreen
import br.com.bragasaude.ui.exams.ExamsScreen
import br.com.bragasaude.ui.social.SocialFeedScreen
import br.com.bragasaude.ui.profile.ProfileScreen
import br.com.bragasaude.ui.profile.ProfileDetailScreen
import br.com.bragasaude.ui.profile.SettingsScreen
import br.com.bragasaude.ui.profile.ConsentScreen
import br.com.bragasaude.ui.reminders.RemindersScreen
import br.com.bragasaude.ui.family.CaregiverDashboardScreen
import br.com.bragasaude.ui.family.FamilyChatScreen
import br.com.bragasaude.ui.family.FamilyConnectScreen
import br.com.bragasaude.ui.report.MedicalReportScreen
import br.com.bragasaude.ui.milestones.MilestonesScreen
import br.com.bragasaude.ui.modules.CareModulesScreen
import br.com.bragasaude.ui.feedback.FeedbackScreen
import br.com.bragasaude.ui.registration.RegistrationSelectorScreen
import br.com.bragasaude.ui.registration.RoleSelectionScreen
import br.com.bragasaude.ui.registration.CaregiverRegistrationScreen
import br.com.bragasaude.ui.registration.CaregiverModeSelectionScreen
import br.com.bragasaude.ui.legal.TermsOfUseScreen
import br.com.bragasaude.ui.legal.PrivacyPolicyScreen
import br.com.bragasaude.ui.onboarding.OnboardingTourScreen
import br.com.bragasaude.ui.devices.WearablesScreen
import br.com.bragasaude.ui.devices.HealthReadingsScreen
import br.com.bragasaude.ui.util.NotificationHelper
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import br.com.bragasaude.ui.care.CareOsViewModel
import br.com.bragasaude.ui.care.MorningCheckInSheet

/**
 * Declara todas as rotas de navegação da aplicação.
 * Extraído da MainActivity para reduzir o tamanho da Activity e permitir
 * preview isolado das telas.
 */
@Composable
fun AppNavHost(
    navController: NavHostController,
    startDestination: Screen,
    userRole: String?,
    caregiverMode: String?,
    modifier: Modifier = Modifier
) {
    val appContext = LocalContext.current.applicationContext
    val globalCareViewModel: CareOsViewModel = hiltViewModel()
    val careState by globalCareViewModel.ui.collectAsState()
    var dismissedCheckInFor by remember { mutableStateOf<String?>(null) }

    val navigateBack: () -> Unit = {
        if (navController.previousBackStackEntry != null) {
            navController.popBackStack()
        } else {
            navController.navigate(
                if (userRole == "CAREGIVER" && caregiverMode != "HYBRID") Screen.Profile else Screen.Home
            ) { launchSingleTop = true }
        }
    }

    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier
    ) {
        composable<Screen.OrbChat> {
            br.com.bragasaude.ui.chat.OrbChatScreen(
                onBack = navigateBack,
                onNavigate = { navController.navigate(it) }
            )
        }
        composable<Screen.Home> {
            HomeScreen(
                onNavigateToNotifications = { navController.navigate(Screen.Notifications) },
                onNavigateToScreen = { navController.navigate(it) }
            )
        }
        composable<Screen.Vitals> { entry ->
            val vitals: Screen.Vitals = entry.toRoute()
            VitalSignsScreen(
                initialType = vitals.type,
                initialValue = vitals.initialValue,
                onBack = navigateBack
            )
        }
        composable<Screen.Biometry> {
            BiometryScreen(onBack = navigateBack)
        }
        composable<Screen.Modules> {
            CareModulesScreen(
                onMedicationStock = { navController.navigate(Screen.MedicationStock) },
                onCareWall = { navController.navigate(Screen.CareWall) },
                onDoctorMode = { navController.navigate(Screen.DoctorMode) }
            )
        }
        composable<Screen.Milestones> { MilestonesScreen() }
        composable<Screen.Hydration> { entry ->
            val hydration: Screen.Hydration = entry.toRoute()
            HydrationScreen(
                initialMl = hydration.initialMl,
                autoOpenDialog = hydration.autoOpenDialog,
                onBack = navigateBack
            )
        }
        composable<Screen.Wearables> {
            WearablesScreen(onBack = navigateBack)
        }
        composable<Screen.HealthReadings> { entry ->
            val route: Screen.HealthReadings = entry.toRoute()
            HealthReadingsScreen(
                route.metric,
                route.initialValue,
                navigateBack,
                { navController.navigate(Screen.Wearables) }
            )
        }
        composable<Screen.Steps> {
            StepsScreen(onBack = navigateBack, onConnectWatch = { navController.navigate(Screen.Wearables) })
        }
        composable<Screen.Reminders> {
            RemindersScreen(onBack = navigateBack)
        }
        composable<Screen.Exams> {
            ExamsScreen(
                onBack = navigateBack,
                onNavigateToEvolution = { navController.navigate(Screen.Report) }
            )
        }
        composable<Screen.Notifications> {
            NotificationsScreen(onBack = navigateBack)
        }
        composable<Screen.Settings> {
            SettingsScreen(
                onBack = navigateBack,
                onLogout = {
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
            TermsOfUseScreen(onBack = navigateBack)
        }
        composable<Screen.PrivacyPolicy> {
            PrivacyPolicyScreen(onBack = navigateBack)
        }
        composable<Screen.OnboardingTour> {
            OnboardingTourScreen(onFinishTour = navigateBack)
        }
        composable<Screen.Feedback> {
            FeedbackScreen(onBack = navigateBack)
        }
        composable<Screen.Nutrition> { entry ->
            val nutrition: Screen.Nutrition = entry.toRoute()
            NutritionScreen(
                searchFood = nutrition.searchFood,
                openGroceryList = nutrition.openGroceryList,
                suggestedGroceryItems = nutrition.groceryItems,
                onNavigateToPantryRecipes = { navController.navigate(Screen.PantryRecipes) }
            )
        }
        composable<Screen.Report> {
            MedicalReportScreen(onBack = navigateBack)
        }
        composable<Screen.AddData> {
            RegistrationSelectorScreen(
                onNavigate = { navController.navigate(it) },
                onClose = navigateBack
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
            ProfileScreen(onBack = navigateBack, onProfileSaved = navigateBack)
        }
        composable<Screen.League> {
            SocialFeedScreen(onBack = navigateBack)
        }
        composable<Screen.SocialFeed> {
            SocialFeedScreen(onBack = navigateBack)
        }
        composable<Screen.FamilyConnect> {
            FamilyConnectScreen(onClose = navigateBack)
        }
        composable<Screen.FamilyChat> {
            FamilyChatScreen(
                onBack = navigateBack,
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
            PantryRecipesScreen(onBack = navigateBack)
        }
        composable<Screen.RoleSelection> {
            RoleSelectionScreen(
                onBack = navigateBack,
                onRoleSelected = { role ->
                    if (role == "CAREGIVER") {
                        navController.navigate(Screen.CaregiverRegistration) {
                            popUpTo(Screen.RoleSelection) { inclusive = true }
                        }
                    } else {
                        navController.navigate(Screen.ProfileEdit) {
                            popUpTo(Screen.RoleSelection) { inclusive = true }
                        }
                    }
                }
            )
        }
        composable<Screen.CaregiverRegistration> {
            CaregiverRegistrationScreen(
                onRegistrationComplete = {
                    navController.navigate(Screen.CaregiverDashboard) {
                        popUpTo(Screen.CaregiverRegistration) { inclusive = true }
                        launchSingleTop = true
                    }
                },
                onBack = navigateBack
            )
        }

        // ==================== CARE OS — D62 (First Contract) ====================
        composable<Screen.MedicationStock> {
            br.com.bragasaude.ui.medication.MedicationStockScreen(
                onBack = navigateBack,
                onScanNew = { navController.navigate(Screen.BarcodeScanner) }
            )
        }
        composable<Screen.BarcodeScanner> {
            br.com.bragasaude.ui.medication.BarcodeScannerScreen(
                onBack = navigateBack,
                onSaved = { navController.popBackStack() }
            )
        }
        composable<Screen.CareWall> {
            br.com.bragasaude.ui.care.CareWallScreen(onBack = navigateBack)
        }
        composable<Screen.DoctorMode> {
            br.com.bragasaude.ui.care.DoctorModeScreen(onBack = navigateBack)
        }
    }

    // C37 pertence à abertura geral do app, não à tela de medicamentos.
    val selfCareEnabled = userRole == "PATIENT" || (userRole == "CAREGIVER" && caregiverMode == "HYBRID")
    val selected = careState.selectedPatientId
    if (selfCareEnabled && careState.isAuthenticated && careState.patients.isNotEmpty() &&
        selected != null && careState.selectedPatient.role == "PATIENT" &&
        !careState.checkInDoneToday && dismissedCheckInFor != selected
    ) {
        MorningCheckInSheet(
            onDismiss = { dismissedCheckInFor = selected },
            viewModel = globalCareViewModel
        )
    }
}
