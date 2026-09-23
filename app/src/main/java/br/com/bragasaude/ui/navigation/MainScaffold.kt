package br.com.bragasaude.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import br.com.bragasaude.domain.XpGrantService
import br.com.bragasaude.ui.components.DraggableAiAssistantFab
import br.com.bragasaude.ui.components.XpToastHost
import br.com.bragasaude.ui.util.Screen
import br.com.bragasaude.util.AppPreferences

/**
 * Scaffold global da aplicação após autenticação.
 * Controla a BottomBar (3 variantes por papel/modo) e o FAB Orb metamórfico.
 * Extraído da MainActivity para deixá-la abaixo de 60 linhas.
 */
@Composable
fun MainScaffold(
    navController: NavHostController,
    startDestination: Screen,
    userRole: String?,
    caregiverMode: String?,
    xpGrantService: XpGrantService
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val appContext = LocalContext.current.applicationContext

    // ==================== Itens de navegação por papel ====================
    val patientNavItems = listOf(
        BottomNavItem("Início", Icons.Default.Home, Screen.Home),
        BottomNavItem("Dados", Icons.Default.BarChart, Screen.Report),
        BottomNavItem("Planos", Icons.Default.Restaurant, Screen.Nutrition()),
        BottomNavItem("Braga", Icons.Default.ChatBubbleOutline, Screen.OrbChat),
        BottomNavItem("Perfil", Icons.Default.Person, Screen.Profile)
    )
    val hybridCaregiverNavItems = listOf(
        BottomNavItem("Início", Icons.Default.Home, Screen.Home),
        BottomNavItem("Painel", Icons.Default.Diversity3, Screen.CaregiverDashboard),
        BottomNavItem("Planos", Icons.Default.Restaurant, Screen.Nutrition()),
        BottomNavItem("Braga", Icons.Default.ChatBubbleOutline, Screen.OrbChat),
        BottomNavItem("Perfil", Icons.Default.Person, Screen.Profile)
    )
    val viewerCaregiverNavItems = listOf(
        BottomNavItem("Painel", Icons.Default.Diversity3, Screen.CaregiverDashboard),
        BottomNavItem("Despensa", Icons.Default.ShoppingCart, Screen.PantryRecipes),
        BottomNavItem("Mural", Icons.Default.Groups, Screen.SocialFeed),
        BottomNavItem("Braga", Icons.Default.ChatBubbleOutline, Screen.OrbChat),
        BottomNavItem("Perfil", Icons.Default.Person, Screen.Profile)
    )
    val navItems = when {
        userRole == "CAREGIVER" && caregiverMode == "HYBRID" -> hybridCaregiverNavItems
        userRole == "CAREGIVER" -> viewerCaregiverNavItems
        else -> patientNavItems
    }

    // ==================== Scaffold ====================
    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            snackbarHost = { XpToastHost(xpGrantService = xpGrantService) },
            modifier = Modifier.fillMaxSize(),
            bottomBar = {
                NavigationBar(
                    modifier = Modifier.height(64.dp),
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 0.dp
                ) {
                    navItems.forEach { item ->
                        val selected = isCurrentRoute(currentRoute, item.route)
                        NavigationBarItem(
                            icon = { Icon(item.icon, contentDescription = item.label) },
                            label = { Text(item.label, style = MaterialTheme.typography.labelSmall) },
                            selected = selected,
                            onClick = {
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
            AppNavHost(
                navController = navController,
                startDestination = startDestination,
                userRole = userRole,
                caregiverMode = caregiverMode,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            )
        }

        // FAB Metamórfico de Voz flutuante — oculto no OrbChat e nas telas de cadastro/estoque de remédios (Fase B)
        if (!isCurrentRoute(currentRoute, Screen.OrbChat) &&
            !isCurrentRoute(currentRoute, Screen.BarcodeScanner) &&
            !isCurrentRoute(currentRoute, Screen.MedicationStock) &&
            AppPreferences.isVoiceAssistantEnabled(appContext)
        ) {
            DraggableAiAssistantFab(
                onNavigate = { screen -> navController.navigate(screen) },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

/** Modelo de item da barra de navegação inferior. */
data class BottomNavItem(
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val route: Screen
)

/**
 * Compara a rota atual do back stack com uma tela da sealed interface [Screen]
 * de forma type-safe, substituindo o [String.contains] antigo — que casava
 * "Profile" contra "ProfileEdit" (APP-6).
 *
 * O Navigation Compose 2.8 derive a rota do nome do serializador da classe:
 * para a sealed interface aninhada o formato é "Screen$Home" (e
 * "Screen$Nutrition?arg=..." para rotas com argumentos). Este helper casa o
 * nome exato ou o prefixo seguido de "?" — âncora que acaba com a colisão de
 * substring.
 */
internal fun isCurrentRoute(currentRoute: String?, screen: Screen): Boolean {
    val name = "Screen\$${screen::class.simpleName}"
    return currentRoute != null && (currentRoute == name || currentRoute.startsWith("$name?"))
}
