package br.com.bragasaude.ui.util

import kotlinx.serialization.Serializable

@Serializable sealed interface Screen {
    @Serializable data object Home : Screen
    @Serializable data object OrbChat : Screen
    @Serializable data object Report : Screen
    @Serializable data object Modules : Screen
    @Serializable data object Milestones : Screen
    @Serializable data class Hydration(val initialMl: Int? = null, val autoOpenDialog: Boolean = false) : Screen
    @Serializable data object Steps : Screen
    @Serializable data object Wearables : Screen
    @Serializable data class HealthReadings(val metric: String, val initialValue: String? = null) : Screen
    @Serializable data object Reminders : Screen
    @Serializable data object AddData : Screen
    @Serializable data class Nutrition(val searchFood: String? = null, val openGroceryList: Boolean = false, val groceryItems: List<String> = emptyList()) : Screen
    @Serializable data object Profile : Screen
    @Serializable data object ProfileEdit : Screen
    
    @Serializable data class Vitals(val type: String? = null, val initialValue: String? = null) : Screen
    @Serializable data object Biometry : Screen
    @Serializable data object Exams : Screen
    @Serializable data object Notifications : Screen
    @Serializable data object Settings : Screen
    @Serializable data object Feedback : Screen
    @Serializable data object League : Screen
    @Serializable data object SocialFeed : Screen
    
    // Telas Legais e Onboarding Guiado (Agente D)
    @Serializable data object TermsOfUse : Screen
    @Serializable data object PrivacyPolicy : Screen
    @Serializable data object OnboardingTour : Screen
    
    // Novas Rotas (Ponte Familiar, Despensa e Papéis)
    @Serializable data object FamilyConnect : Screen
    @Serializable data object FamilyChat : Screen
    @Serializable data object CaregiverDashboard : Screen
    @Serializable data object PantryRecipes : Screen
    @Serializable data object RoleSelection : Screen
    @Serializable data object CaregiverRegistration : Screen
}
