package br.com.bragasaude.ui.nutrition

import br.com.bragasaude.ui.components.BragaAlertDialog
import br.com.bragasaude.ui.components.BragaFormSheet

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.RestaurantMenu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import br.com.bragasaude.domain.groceryNameKey
import kotlinx.coroutines.delay
import br.com.bragasaude.R
import br.com.bragasaude.data.remote.model.RemoteFood
import br.com.bragasaude.domain.HealthCalculators
import br.com.bragasaude.ui.components.EmeraldHeaderBanner
import br.com.bragasaude.ui.home.HomeViewModel
import br.com.bragasaude.ui.theme.BragaBackground
import br.com.bragasaude.ui.theme.BragaCardBorder
import br.com.bragasaude.ui.theme.BragaCardSurface
import br.com.bragasaude.ui.theme.BragaEmerald
import br.com.bragasaude.ui.theme.BragaEmeraldDark
import br.com.bragasaude.ui.theme.BragaMint
import br.com.bragasaude.ui.theme.BragaMintBorder
import br.com.bragasaude.ui.theme.BragaMintSurface
import br.com.bragasaude.ui.theme.BragaTextPrimary
import br.com.bragasaude.ui.theme.BragaTextSecondary
import br.com.bragasaude.ui.theme.Success
import br.com.bragasaude.ui.theme.Warning

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NutritionScreen(
    searchFood: String? = null,
    openGroceryList: Boolean = false,
    suggestedGroceryItems: List<String> = emptyList(),
    viewModel: NutritionViewModel = hiltViewModel(),
    homeViewModel: HomeViewModel = hiltViewModel(),
    onNavigateToPantryRecipes: () -> Unit = {}
) {
    val dailyCal by viewModel.dailyCalories.collectAsState()
    val consumedCal by viewModel.totalCaloriesConsumed.collectAsState()
    val userWeight by viewModel.userWeight.collectAsState()
    val autoRecommendedCalories by viewModel.autoRecommendedCalories.collectAsState()
    val isCustomCalorieTarget by viewModel.isCustomCalorieTarget.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val activeAdjustments by viewModel.activeAdjustments.collectAsState()
    val recommendations = remember(isLoading, activeAdjustments) { viewModel.getRecommendations() }
    val todayLoggedMeals by viewModel.todayLoggedMeals.collectAsState()
    val suggestionGroups by viewModel.functionalSuggestionGroups.collectAsState()
    val selectedMealTab by viewModel.selectedMealTab.collectAsState()
    val groceryList by viewModel.groceryList.collectAsState()
    val manualIngredients by viewModel.manualIngredients.collectAsState()
    val groceryMessage by viewModel.groceryMessage.collectAsState()
    val groceryPlan by viewModel.groceryPlanResult.collectAsState()
    val pantryStock by viewModel.pantryStock.collectAsState()
    val showResizeDialog by viewModel.showResizeDialog.collectAsState()
    val weeklyPreferences by viewModel.weeklyPreferences.collectAsState()

    val searchQuery by viewModel.searchQuery.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()

    val mealTabs = listOf("Café da Manhã", "Almoço", "Lanche da Tarde", "Jantar")

    var showFoodSelectorDialog by remember { mutableStateOf(false) }
    var showCustomFoodDialog by remember { mutableStateOf(false) }
    var showCalorieTargetDialog by remember { mutableStateOf(false) }
    var customCalorieInputText by remember { mutableStateOf("") }
    val contributionState by viewModel.contributionState.collectAsState()
    val communityPrices by viewModel.communityPrices.collectAsState()
    var showGroceryBottomSheet by remember { mutableStateOf(false) }
    // Agente B1: itens sugeridos pelo chat; o usuário confirma via chip.
    var pendingSuggestions by remember(suggestedGroceryItems) { mutableStateOf(suggestedGroceryItems) }

    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    // Custom Food Form
    var customFoodName by remember { mutableStateOf("") }
    var customFoodCategory by remember { mutableStateOf("Geral") }
    var customFoodKcal by remember { mutableStateOf("") }
    var customFoodCarbs by remember { mutableStateOf("") }
    var customFoodProtein by remember { mutableStateOf("") }
    var customFoodFat by remember { mutableStateOf("") }

    // Log Portion Form
    var selectedFoodForPortion by remember { mutableStateOf<RemoteFood?>(null) }
    var portionGramsText by remember { mutableStateOf("100") }

    LaunchedEffect(searchFood, openGroceryList) {
        if (openGroceryList) {
            viewModel.prepareGroceryData()
            if (groceryList.isEmpty()) {
                viewModel.generateWeeklyGroceryList()
            }
            showGroceryBottomSheet = true
        } else if (!searchFood.isNullOrBlank()) {
            viewModel.onSearchQueryChanged(searchFood)
            showFoodSelectorDialog = true
        }
    }

    Scaffold(
        containerColor = BragaBackground,
        topBar = {
            EmeraldHeaderBanner(
                title = "Plano Alimentar",
                subtitle = "Opções funcionais e equilíbrio para o seu dia"
            )
        }
    ) { innerPadding ->
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Card de Resumo Calórico do Dia (Sincronizado ao Peso e Editável)
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                customCalorieInputText = dailyCal.toInt().toString()
                                showCalorieTargetDialog = true
                            },
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = BragaMintSurface),
                        border = BorderStroke(1.dp, BragaMintBorder)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("Consumo de Hoje", style = MaterialTheme.typography.labelMedium, color = BragaTextSecondary)
                                        Spacer(Modifier.width(4.dp))
                                        IconButton(
                                            onClick = {
                                                customCalorieInputText = dailyCal.toInt().toString()
                                                showCalorieTargetDialog = true
                                            },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.Info,
                                                contentDescription = "Informações sobre a meta calórica",
                                                tint = BragaEmerald,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            "${consumedCal.toInt()} / ${dailyCal.toInt()} kcal",
                                            style = MaterialTheme.typography.headlineMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = BragaTextPrimary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        IconButton(
                                            onClick = {
                                                customCalorieInputText = dailyCal.toInt().toString()
                                                showCalorieTargetDialog = true
                                            },
                                            modifier = Modifier.size(26.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.Edit,
                                                contentDescription = "Ajustar meta calórica",
                                                tint = BragaEmerald,
                                                modifier = Modifier.size(15.dp)
                                            )
                                        }
                                    }
                                    if (isCustomCalorieTarget) {
                                        Text(
                                            "Meta personalizada",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = BragaEmerald,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    } else {
                                        val weightText = userWeight?.let { "${it.toInt()}kg" } ?: "estimado"
                                        Text(
                                            "Sincronizada ao peso ($weightText)",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = BragaTextSecondary
                                        )
                                    }
                                }
                                val progress = if (dailyCal > 0) (consumedCal / dailyCal).toFloat().coerceIn(0f, 1f) else 0f
                                CircularProgressIndicator(
                                    progress = { progress },
                                    modifier = Modifier.size(54.dp),
                                    color = if (consumedCal > dailyCal) MaterialTheme.colorScheme.error else BragaEmerald,
                                    trackColor = BragaMint,
                                    strokeWidth = 7.dp
                                )
                            }
                        }
                    }
                }

                // 🍳 Card: O Que Cozinhar Hoje? — Receitas da Minha Despensa
                item {
                    PantryRecipesCard(
                        onClick = onNavigateToPantryRecipes
                    )
                }

                // Card de Acesso à Lista Semanal de Compras Inteligente
                item {
                    val totalEstimated = groceryList.sumOf { it.estimatedPriceBrl }
                    val checkedPantry = groceryList.count { it.isCheckedInPantry }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                viewModel.prepareGroceryData()
                                if (groceryList.isEmpty()) {
                                    viewModel.generateWeeklyGroceryList()
                                }
                                showGroceryBottomSheet = true
                            },
                        shape = RoundedCornerShape(22.dp),
                        colors = CardDefaults.cardColors(containerColor = BragaMintSurface),
                        border = BorderStroke(1.dp, BragaMintBorder)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = BragaMint,
                                    modifier = Modifier.size(44.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            Icons.Default.ShoppingCart,
                                            contentDescription = null,
                                            tint = BragaEmerald,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        "Lista Semanal de Compras",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = BragaEmeraldDark
                                    )
                                    Text(
                                        if (groceryList.isEmpty()) "Toque para gerar a lista"
                                        else "Estimado: R$ ${String.format(java.util.Locale.getDefault(), "%.2f", totalEstimated)} • $checkedPantry/${groceryList.size} na despensa",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = BragaTextSecondary
                                    )
                                }
                            }
                            Icon(
                                Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint = BragaEmerald
                            )
                        }
                    }
                }

                // Ajustes Clínicos, Gatilhos e Avisos de Perfil
                if (activeAdjustments.isNotEmpty()) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Tune,
                                    contentDescription = null,
                                    tint = BragaEmerald,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    "Ajustes baseados no seu perfil:",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (activeAdjustments.isNotEmpty()) {
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    items(activeAdjustments) { adjustment ->
                                        Surface(
                                            shape = RoundedCornerShape(20.dp),
                                            color = BragaMint
                                        ) {
                                            Text(
                                                "Ajustado para: $adjustment",
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                                style = MaterialTheme.typography.labelMedium,
                                                color = BragaEmerald,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Seletor de Refeições (Tabs)
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(mealTabs) { tab ->
                            val isSelected = selectedMealTab == tab
                            Surface(
                                onClick = { viewModel.selectMealTab(tab) },
                                shape = RoundedCornerShape(50),
                                color = if (isSelected) BragaEmerald else BragaCardSurface,
                                border = BorderStroke(
                                    1.dp,
                                    if (isSelected) BragaEmerald else BragaCardBorder
                                ),
                                shadowElevation = if (isSelected) 2.dp else 0.dp,
                                modifier = Modifier.heightIn(min = 48.dp)
                            ) {
                                Text(
                                    text = tab,
                                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) Color.White else BragaTextSecondary
                                )
                            }
                        }
                    }
                }

                // Ações da Refeição Selecionada
                item {
                    val mealsForTab = todayLoggedMeals.filter { it.mealType == selectedMealTab }
                    val totalTabKcal = mealsForTab.sumOf { it.kcal }

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = BragaMintSurface),
                        shape = RoundedCornerShape(22.dp),
                        border = BorderStroke(1.dp, BragaMintBorder)
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    selectedMealTab,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = BragaTextPrimary
                                )
                                Text(
                                    "${totalTabKcal.toInt()} kcal",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = BragaEmerald
                                )
                            }

                            if (mealsForTab.isEmpty()) {
                                Text(
                                    "Nenhum alimento registrado para esta refeição.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = BragaTextSecondary
                                )
                            } else {
                                mealsForTab.forEach { meal ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(meal.foodName, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium, color = BragaTextPrimary)
                                            Text("${meal.portionGrams}g • ${meal.kcal.toInt()} kcal", style = MaterialTheme.typography.labelSmall, color = BragaTextSecondary)
                                        }
                                        IconButton(onClick = { viewModel.removeLoggedMeal(meal.id) }) {
                                            Icon(Icons.Default.Delete, contentDescription = "Remover", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                                        }
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Button(
                                    onClick = { showFoodSelectorDialog = true },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(54.dp),
                                    shape = RoundedCornerShape(14.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = BragaEmerald),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                                ) {
                                    Icon(Icons.Default.Restaurant, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        "Registrar\nConsumo",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        lineHeight = 15.sp,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Start
                                    )
                                }

                                OutlinedButton(
                                    onClick = { showCustomFoodDialog = true },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(54.dp),
                                    shape = RoundedCornerShape(14.dp),
                                    border = BorderStroke(1.dp, BragaMintBorder),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                                ) {
                                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp), tint = BragaEmeraldDark)
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        "Criar\nManual",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = BragaEmeraldDark,
                                        lineHeight = 15.sp,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Start
                                    )
                                }
                            }
                        }
                    }
                }

                // Banner de Transparência e Disclaimer de Autocuidado
                item {
                    Surface(
                        color = BragaMintSurface,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Info,
                                contentDescription = null,
                                tint = BragaEmerald,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = stringResource(R.string.nutrition_disclaimer),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 17.sp
                            )
                        }
                    }
                }

                // Sugestões do Plano Alimentar Ancoradas Exclusivamente na Lista de Compras
                if (groceryList.isEmpty()) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = BragaMintSurface),
                            border = BorderStroke(1.dp, BragaMintBorder)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = BragaMint,
                                    modifier = Modifier.size(54.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            Icons.Default.ShoppingCart,
                                            contentDescription = null,
                                            tint = BragaEmerald,
                                            modifier = Modifier.size(26.dp)
                                        )
                                    }
                                }
                                Spacer(Modifier.height(12.dp))
                                Text(
                                    text = "Monte sua lista para ver seu plano alimentar",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = BragaTextPrimary,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    text = "O seu plano alimentar é montado exclusivamente a partir dos alimentos que você planejou na sua lista de compras ou tem na sua despensa.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = BragaTextSecondary,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                                Spacer(Modifier.height(16.dp))
                                Button(
                                    onClick = {
                                        viewModel.prepareGroceryData()
                                        if (groceryList.isEmpty()) {
                                            viewModel.generateWeeklyGroceryList()
                                        }
                                        showGroceryBottomSheet = true
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = BragaEmerald),
                                    modifier = Modifier.heightIn(min = 48.dp)
                                ) {
                                    Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("Montar ou Sugerir Lista com IA", fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                } else if (suggestionGroups.isEmpty()) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = BragaCardSurface),
                            border = BorderStroke(1.dp, BragaCardBorder)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = BragaMint,
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            Icons.Default.Info,
                                            contentDescription = null,
                                            tint = BragaEmerald,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        "Sem opções na lista para o $selectedMealTab",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = BragaTextPrimary
                                    )
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        "Os alimentos da sua lista atual atendem a outras refeições. Que tal adicionar opções para o seu $selectedMealTab?",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = BragaTextSecondary
                                    )
                                }
                                Spacer(Modifier.width(8.dp))
                                TextButton(
                                    onClick = {
                                        viewModel.prepareGroceryData()
                                        showGroceryBottomSheet = true
                                    }
                                ) {
                                    Text("Ver Lista", color = BragaEmerald, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                } else {
                    item {
                        Column(modifier = Modifier.padding(top = 4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = BragaEmerald,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "Sugestões da sua Lista para o $selectedMealTab",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                            }
                            Text(
                                "Alimentos planejados na sua lista de compras ou presentes na sua despensa",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }

                    items(suggestionGroups, key = { it.id }) { group ->
                        FunctionalSuggestionGroupCard(
                            group = group,
                            selectedMealTab = selectedMealTab,
                            onAddOption = { option ->
                                viewModel.logMeal(
                                    mealType = selectedMealTab,
                                    food = RemoteFood(
                                        id = option.foodId,
                                        name = option.name,
                                        category = option.category,
                                        kcal = option.kcal,
                                        isDiabetesSafe = option.isDiabetesSafe,
                                        isHypertensionSafe = option.isHypertensionSafe,
                                        servingSizeGrams = option.servingSizeGrams,
                                        servingUnit = option.servingUnit,
                                        minServingGrams = option.minServingGrams,
                                        maxServingGrams = option.maxServingGrams
                                    ),
                                    portionGrams = option.servingSizeGrams
                                )
                            },
                            onDismissOption = { option ->
                                viewModel.dismissOrSwapSuggestion(option.name)
                            }
                        )
                    }
                }

                // Recomendações e Proporções Inteligentes
                item {
                    Text(
                        "Distribuição Recomendada",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                items(recommendations) { (name, rec) ->
                    MealCard(name, rec)
                }

                item { Spacer(Modifier.height(40.dp)) }
            }
        }
    }

    // Diálogo de Seleção e Busca Dinâmica de Alimentos
    if (showFoodSelectorDialog) {
        LaunchedEffect(searchQuery, searchResults) {
            val clean = groceryNameKey(searchQuery)
            if (clean.length >= 2 && searchQuery.endsWith(" ") && searchResults.isNotEmpty()) {
                keyboardController?.hide()
                focusManager.clearFocus()
            } else if (clean.length >= 3 && searchResults.isNotEmpty()) {
                val isFullMatch = searchResults.any { food ->
                    val fullName = groceryNameKey(food.name)
                    fullName == clean || fullName.split(" ", "-", "/").any { it == clean }
                }
                if (isFullMatch) {
                    delay(400)
                    keyboardController?.hide()
                    focusManager.clearFocus()
                }
            }
        }

        BragaFormSheet(
            onDismissRequest = { 
                showFoodSelectorDialog = false
                selectedFoodForPortion = null
                keyboardController?.hide()
                focusManager.clearFocus()
            },
            title = { Text("O que você consumiu no $selectedMealTab?", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { viewModel.onSearchQueryChanged(it) },
                        placeholder = { Text("Buscar (ex: Arroz, Frango, Banana...)") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = {
                                    viewModel.onSearchQueryChanged("")
                                    keyboardController?.hide()
                                    focusManager.clearFocus()
                                }) {
                                    Icon(Icons.Default.Close, contentDescription = "Limpar busca")
                                }
                            }
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(
                            onSearch = {
                                keyboardController?.hide()
                                focusManager.clearFocus()
                            }
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    val selectedFood = selectedFoodForPortion
                    if (selectedFood != null) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(selectedFood.name, fontWeight = FontWeight.Bold)
                                Text("${(selectedFood.kcal ?: 0.0).toInt()} kcal / 100g • Sugerido: ${selectedFood.servingUnit}", style = MaterialTheme.typography.bodySmall)
                                Spacer(Modifier.height(8.dp))
                                OutlinedTextField(
                                    value = portionGramsText,
                                    onValueChange = { portionGramsText = it.filter { ch -> ch.isDigit() } },
                                    label = { Text("Porção em gramas (g)") },
                                    supportingText = { Text("Padrão: ${selectedFood.servingUnit} (Mín ${selectedFood.minServingGrams}g / Máx ${selectedFood.maxServingGrams}g)") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    } else {
                        LazyColumn(modifier = Modifier.heightIn(max = 240.dp)) {
                            items(searchResults) { food ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { 
                                            keyboardController?.hide()
                                            focusManager.clearFocus()
                                            selectedFoodForPortion = food 
                                            portionGramsText = food.servingSizeGrams.toString()
                                        }
                                        .padding(vertical = 8.dp, horizontal = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(food.name, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                                        Text("${food.category ?: ""} • ${food.servingUnit}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                    }
                                    Text("${(food.kcal ?: 0.0).toInt()} kcal/100g", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                }
                                HorizontalDivider(color = BragaCardBorder)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                if (selectedFoodForPortion != null) {
                    Button(onClick = {
                        val parsed = portionGramsText.toIntOrNull() ?: selectedFoodForPortion!!.servingSizeGrams
                        val safeGrams = parsed.coerceIn(selectedFoodForPortion!!.minServingGrams, selectedFoodForPortion!!.maxServingGrams)
                        viewModel.logMeal(selectedMealTab, selectedFoodForPortion!!, safeGrams)
                        selectedFoodForPortion = null
                        showFoodSelectorDialog = false
                    }) {
                        Text("Confirmar Consumo")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { 
                    if (selectedFoodForPortion != null) {
                        selectedFoodForPortion = null
                    } else {
                        showFoodSelectorDialog = false
                    }
                }) {
                    Text(if (selectedFoodForPortion != null) "Voltar à Busca" else "Fechar")
                }
            }
        )
    }

    // Diálogo de Cadastrar Alimento Customizado
    if (showCustomFoodDialog) {
        BragaFormSheet(
            onDismissRequest = { showCustomFoodDialog = false },
            title = { Text("Registrar Alimento Manual", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = customFoodName,
                        onValueChange = { customFoodName = it },
                        label = { Text("Nome do Alimento") },
                        placeholder = { Text("Ex: Tapioca com Queijo") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = customFoodKcal,
                        onValueChange = { customFoodKcal = it.filter { ch -> ch.isDigit() || ch == '.' } },
                        label = { Text("Calorias por 100g (kcal)") },
                        placeholder = { Text("Ex: 180") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedTextField(
                            value = customFoodCarbs,
                            onValueChange = { customFoodCarbs = it },
                            label = { Text("Carb (g)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = customFoodProtein,
                            onValueChange = { customFoodProtein = it },
                            label = { Text("Prot (g)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = customFoodFat,
                            onValueChange = { customFoodFat = it },
                            label = { Text("Gord (g)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    val kcal = customFoodKcal.toDoubleOrNull()
                    if (customFoodName.isNotBlank() && kcal != null) {
                        viewModel.addCustomFood(
                            name = customFoodName,
                            category = customFoodCategory,
                            kcalPer100g = kcal,
                            carbsG = customFoodCarbs.toDoubleOrNull(),
                            proteinG = customFoodProtein.toDoubleOrNull(),
                            fatG = customFoodFat.toDoubleOrNull()
                        )
                        customFoodName = ""
                        customFoodKcal = ""
                        customFoodCarbs = ""
                        customFoodProtein = ""
                        customFoodFat = ""
                        showCustomFoodDialog = false
                    }
                }) {
                    Text("Salvar e Registrar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCustomFoodDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    // Diálogo de Ajuste da Meta Calórica Diária (Diretrizes FAO/OMS + Peso)
    if (showCalorieTargetDialog) {
        val weightText = if (userWeight != null) "${userWeight!!.toInt()} kg" else "não informado (estimativa de 70 kg)"
        BragaFormSheet(
            onDismissRequest = { showCalorieTargetDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = null,
                        tint = BragaEmerald,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Meta Calórica Diária", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    Surface(
                        color = BragaMintSurface,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, BragaMintBorder)
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                "Diretrizes FAO/OMS & Ministério da Saúde",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = BragaEmeraldDark
                            )
                            Text(
                                "O cálculo automático de referência para manutenção saudável considera ~28 kcal por kg de peso corporal. Com seu peso de $weightText, sua meta sugerida é de $autoRecommendedCalories kcal/dia.",
                                style = MaterialTheme.typography.bodySmall,
                                color = BragaTextSecondary,
                                lineHeight = 18.sp
                            )
                            Text(
                                "Você tem total liberdade para seguir a recomendação ou definir a meta personalizada recomendada pelo seu médico ou nutricionista.",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = BragaTextPrimary,
                                lineHeight = 17.sp
                            )
                        }
                    }

                    OutlinedTextField(
                        value = customCalorieInputText,
                        onValueChange = { customCalorieInputText = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Sua Meta Calórica (kcal/dia)") },
                        placeholder = { Text("Ex: $autoRecommendedCalories") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (isCustomCalorieTarget) {
                        OutlinedButton(
                            onClick = {
                                viewModel.resetCalorieTargetToRecommended()
                                showCalorieTargetDialog = false
                            },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, BragaEmerald)
                        ) {
                            Text("Restaurar Recomendação ($autoRecommendedCalories kcal)", color = BragaEmerald, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val parsed = customCalorieInputText.toDoubleOrNull()
                        if (parsed != null && parsed > 500) {
                            viewModel.setCustomCalorieTarget(parsed)
                            showCalorieTargetDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BragaEmerald)
                ) {
                    Text("Salvar Meta")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCalorieTargetDialog = false }) {
                    Text("Fechar")
                }
            }
        )
    }

    if (showResizeDialog) {
        BragaAlertDialog(
            onDismissRequest = { viewModel.keepCurrentWeeklyList() },
            title = { Text("Meta alimentar alterada") },
            text = { Text("Deseja redimensionar a lista semanal pela nova meta? Seus itens manuais serão preservados. Manter a lista atual deixa a cobertura invalidada.") },
            confirmButton = { TextButton(onClick = { viewModel.resizeWeeklyList() }) { Text("Redimensionar agora") } },
            dismissButton = { TextButton(onClick = { viewModel.keepCurrentWeeklyList() }) { Text("Manter lista atual") } }
        )
    }
    if (showGroceryBottomSheet) {
        val context = LocalContext.current
        GroceryListBottomSheet(
            groceryList = groceryList,
            manualIngredients = manualIngredients,
            onDismiss = { showGroceryBottomSheet = false },
            onToggleItem = { id, isChecked -> viewModel.togglePantryItem(id, isChecked) },
            onGenerateList = { preserveManual -> viewModel.generateWeeklyGroceryList(preserveManual) },
            weeklyPreferences = weeklyPreferences,
            onGenerateListWithPreferences = { preserveManual, prefs ->
                viewModel.generateWeeklyGroceryList(preserveManual, prefs)
            },
            onSaveManualItem = { slug, amount, repId -> viewModel.saveManualItem(slug, amount, repId) },
            onRemoveItem = { id -> viewModel.removeGroceryItem(id) },
            onClearList = { viewModel.clearGroceryList() },
            groceryMessage = groceryMessage,
            plan = groceryPlan,
            pantryStock = pantryStock,
            onResize = { viewModel.resizeWeeklyList() },
            isLoading = isLoading,
            onExportPdf = { viewModel.exportAndShareGroceryPdf(context) },
            contributionState = contributionState,
            communityPrices = communityPrices,
            onStartContribution = { viewModel.resetPriceContribution() },
            onContribute = { item, amount, quantity, unit, state, date -> viewModel.contributeGroceryPrice(item, amount, quantity, unit, state, date) },
            suggestedItems = pendingSuggestions,
            onAddSuggested = { items ->
                viewModel.addSuggestedItems(items)
                pendingSuggestions = emptyList()
            }
        )
    }
}

@Composable
fun MealCard(mealName: String, recommendation: HealthCalculators.MealRecommendation) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = BragaCardSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, BragaCardBorder)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = CircleShape,
                        color = BragaMint,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.Restaurant,
                                contentDescription = null,
                                tint = BragaEmerald,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(mealName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = BragaTextPrimary)
                        Text(
                            "${recommendation.calories.avg.toInt()} kcal recomendadas",
                            style = MaterialTheme.typography.bodySmall,
                            color = BragaTextSecondary
                        )
                    }
                }
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(
                        if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = "Expandir"
                    )
                }
            }

            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(top = 16.dp)) {
                    Text("Distribuição de Macronutrientes:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        MacroItem("Carboidratos", "${recommendation.carbs.avg.toInt()}g", MaterialTheme.colorScheme.primary)
                        MacroItem("Proteínas", "${recommendation.protein.avg.toInt()}g", Success)
                        MacroItem("Gorduras", "${recommendation.fat.avg.toInt()}g", Warning)
                    }
                }
            }
        }
    }
}

@Composable
private fun MacroItem(name: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontWeight = FontWeight.Bold, color = color, style = MaterialTheme.typography.titleMedium)
        Text(name, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
    }
}

@Composable
fun FunctionalSuggestionGroupCard(
    group: br.com.bragasaude.domain.NutritionalSuggestionGroup,
    selectedMealTab: String,
    onAddOption: (br.com.bragasaude.domain.SuggestedFoodOption) -> Unit,
    onDismissOption: (br.com.bragasaude.domain.SuggestedFoodOption) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, BragaMintBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        group.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        group.subtitle,
                        style = MaterialTheme.typography.labelMedium,
                        color = BragaEmerald,
                        fontWeight = FontWeight.SemiBold
                    )
                }

            }

            Text(
                group.whyItMatters,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 18.sp
            )

            Text(
                "Opções para você escolher o que mais combina com seu gosto:",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.outline
            )

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(vertical = 4.dp)
            ) {
                items(group.options, key = { it.foodId.ifBlank { it.name } }) { option ->
                    SuggestedOptionCard(
                        option = option,
                        selectedMealTab = selectedMealTab,
                        onAdd = { onAddOption(option) },
                        onDismiss = { onDismissOption(option) }
                    )
                }
            }

            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = group.disclaimer,
                    modifier = Modifier.padding(8.dp),
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.outline,
                    lineHeight = 14.sp
                )
            }
        }
    }
}

@Composable
private fun SuggestedOptionCard(
    option: br.com.bragasaude.domain.SuggestedFoodOption,
    selectedMealTab: String,
    onAdd: () -> Unit,
    onDismiss: () -> Unit
) {
    Card(
        modifier = Modifier.width(220.dp).height(265.dp),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        option.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Dispensar", modifier = Modifier.size(16.dp))
                    }
                }
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = BragaEmerald.copy(alpha = 0.12f)
                ) {
                    Text(
                        option.category,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = BragaEmerald,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    option.functionalBenefit,
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 14.sp,
                    lineHeight = 16.sp,
                    color = BragaTextSecondary,
                    maxLines = 3
                )
                Text(
                    option.portionTip,
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 14.sp,
                    color = BragaEmerald,
                    maxLines = 2
                )
            }
            
            Button(
                onClick = onAdd,
                modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = BragaEmerald),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(
                    text = option.servingUnit,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    lineHeight = 14.sp,
                    maxLines = 2,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }
        }
    }
}


/**
 * Card de destaque para o módulo "O Que Cozinhar Hoje?".
 *
 * Exibe um botão chamativo que leva à tela de receitas da despensa.
 */
@Composable
fun PantryRecipesCard(onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
        ),
        border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CircleShape,
                    color = BragaMint,
                    modifier = Modifier.size(48.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Outlined.RestaurantMenu,
                            contentDescription = null,
                            tint = BragaEmerald,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Receitas da Minha Lista",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = BragaTextPrimary
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Receitas com todos os ingredientes na sua lista de compras",
                        style = MaterialTheme.typography.bodySmall,
                        color = BragaTextSecondary
                    )
                }
            }
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = "Ver receitas",
                tint = BragaEmerald,
                modifier = Modifier.size(32.dp)
            )
        }
    }
}
