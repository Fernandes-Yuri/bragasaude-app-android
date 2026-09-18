package br.com.bragasaude.ui.nutrition

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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import br.com.bragasaude.R
import br.com.bragasaude.data.remote.model.RemoteFood
import br.com.bragasaude.domain.HealthCalculators
import br.com.bragasaude.ui.components.EmeraldHeaderBanner
import br.com.bragasaude.ui.home.HomeViewModel
import br.com.bragasaude.ui.hydration.HydrationViewModel
import br.com.bragasaude.ui.theme.BragaBackground
import br.com.bragasaude.ui.theme.BragaCardBorder
import br.com.bragasaude.ui.theme.BragaCardSurface
import br.com.bragasaude.ui.theme.BragaEmerald
import br.com.bragasaude.ui.theme.BragaMint
import br.com.bragasaude.ui.theme.BragaMintBorder
import br.com.bragasaude.ui.theme.BragaTextPrimary
import br.com.bragasaude.ui.theme.BragaTextSecondary
import br.com.bragasaude.ui.theme.Success
import br.com.bragasaude.ui.theme.TealLight
import br.com.bragasaude.ui.theme.TealPrimary
import br.com.bragasaude.ui.theme.TealSurface
import br.com.bragasaude.ui.theme.Warning

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NutritionScreen(
    searchFood: String? = null,
    openGroceryList: Boolean = false,
    suggestedGroceryItems: List<String> = emptyList(),
    viewModel: NutritionViewModel = hiltViewModel(),
    homeViewModel: HomeViewModel = hiltViewModel(),
    hydrationViewModel: HydrationViewModel = hiltViewModel(),
    onNavigateToPantryRecipes: () -> Unit = {}
) {
    val dailyCal by viewModel.dailyCalories.collectAsState()
    val consumedCal by viewModel.totalCaloriesConsumed.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val currentHydration by hydrationViewModel.currentHydration.collectAsState()
    val targetHydration by hydrationViewModel.targetHydration.collectAsState()
    val activeAdjustments by viewModel.activeAdjustments.collectAsState()
    val scoreBreakdown by homeViewModel.scoreBreakdown.collectAsState()
    val recommendations = remember(isLoading, activeAdjustments) { viewModel.getRecommendations() }
    val todayLoggedMeals by viewModel.todayLoggedMeals.collectAsState()
    val suggestionGroups by viewModel.functionalSuggestionGroups.collectAsState()
    val selectedMealTab by viewModel.selectedMealTab.collectAsState()
    val groceryList by viewModel.groceryList.collectAsState()

    val searchQuery by viewModel.searchQuery.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()

    val mealTabs = listOf("Café da Manhã", "Almoço", "Lanche da Tarde", "Jantar")

    var showFoodSelectorDialog by remember { mutableStateOf(false) }
    var showCustomFoodDialog by remember { mutableStateOf(false) }
    var showGroceryBottomSheet by remember { mutableStateOf(false) }
    // Agente B1: itens sugeridos pelo chat; o usuário confirma via chip.
    var pendingSuggestions by remember(suggestedGroceryItems) { mutableStateOf(suggestedGroceryItems) }

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
                // Card de Resumo Calórico do Dia
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(0.5.dp, Color.LightGray.copy(alpha = 0.3f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Consumo de Hoje", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
                                    Text(
                                        "${consumedCal.toInt()} / ${dailyCal.toInt()} kcal",
                                        style = MaterialTheme.typography.headlineMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                val progress = if (dailyCal > 0) (consumedCal / dailyCal).toFloat().coerceIn(0f, 1f) else 0f
                                CircularProgressIndicator(
                                    progress = { progress },
                                    modifier = Modifier.size(54.dp),
                                    color = if (consumedCal > dailyCal) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                    strokeWidth = 6.dp
                                )
                            }
                        }
                    }
                }

                // Card de Progresso Hídrico (padrão 111652)
                item {
                    HydrationProgressCard(
                        currentMl = currentHydration.toInt(),
                        targetMl = targetHydration.toInt(),
                        onQuickAdd = { ml -> hydrationViewModel.addWater(ml) }
                    )
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
                                if (groceryList.isEmpty()) {
                                    viewModel.generateWeeklyGroceryList()
                                }
                                showGroceryBottomSheet = true
                            },
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9)),
                        border = BorderStroke(1.dp, Color(0xFFA5D6A7))
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
                                    color = Color(0xFF2E7D32),
                                    modifier = Modifier.size(44.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            Icons.Default.ShoppingCart,
                                            contentDescription = null,
                                            tint = Color.White,
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
                                        color = Color(0xFF1B5E20)
                                    )
                                    Text(
                                        if (groceryList.isEmpty()) "Toque para gerar a lista"
                                        else "R$ ${String.format(java.util.Locale.getDefault(), "%.2f", totalEstimated)} • $checkedPantry/${groceryList.size} na despensa",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFF388E3C)
                                    )
                                }
                            }
                            Icon(
                                Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint = Color(0xFF2E7D32)
                            )
                        }
                    }
                }

                // Ajustes Clínicos, Gatilhos e Avisos de Perfil
                if (activeAdjustments.isNotEmpty() || scoreBreakdown.negativeFactors.isNotEmpty()) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Tune,
                                    contentDescription = null,
                                    tint = TealPrimary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    "Ajustes baseados no seu perfil e últimos exames:",
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
                                            color = TealLight
                                        ) {
                                            Text(
                                                "Ajustado para: $adjustment",
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                                style = MaterialTheme.typography.labelMedium,
                                                color = TealPrimary,
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
                    ScrollableTabRow(
                        selectedTabIndex = mealTabs.indexOf(selectedMealTab),
                        edgePadding = 0.dp,
                        containerColor = Color.Transparent,
                        divider = {}
                    ) {
                        mealTabs.forEach { tab ->
                            Tab(
                                selected = selectedMealTab == tab,
                                onClick = { viewModel.selectMealTab(tab) },
                                text = {
                                    Text(
                                        tab,
                                        fontWeight = if (selectedMealTab == tab) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            )
                        }
                    }
                }

                // Ações da Refeição Selecionada
                item {
                    val mealsForTab = todayLoggedMeals.filter { it.mealType == selectedMealTab }
                    val totalTabKcal = mealsForTab.sumOf { it.kcal }

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                        shape = RoundedCornerShape(16.dp)
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
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    "${totalTabKcal.toInt()} kcal",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }

                            if (mealsForTab.isEmpty()) {
                                Text(
                                    "Nenhum alimento registrado para esta refeição.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            } else {
                                mealsForTab.forEach { meal ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(meal.foodName, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                                            Text("${meal.portionGrams}g • ${meal.kcal.toInt()} kcal", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                        }
                                        IconButton(onClick = { viewModel.removeLoggedMeal(meal.id) }) {
                                            Icon(Icons.Default.Delete, contentDescription = "Remover", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                                        }
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = { showFoodSelectorDialog = true },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Alimento")
                                }

                                OutlinedButton(
                                    onClick = { showCustomFoodDialog = true },
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Personalizado")
                                }
                            }
                        }
                    }
                }

                // Banner de Transparência e Disclaimer de Autocuidado
                item {
                    Surface(
                        color = TealSurface,
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
                                tint = TealPrimary,
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

                // Sugestões de Bem-Estar do Cérebro Nutricional
                if (suggestionGroups.isNotEmpty()) {
                    item {
                        Column(modifier = Modifier.padding(top = 4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = TealPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "Sugestões de Bem-Estar para o $selectedMealTab",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                            }
                            Text(
                                "Opções naturais e funcionais ideais para o seu $selectedMealTab",
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
        AlertDialog(
            onDismissRequest = { 
                showFoodSelectorDialog = false
                selectedFoodForPortion = null
            },
            title = { Text("Buscar Alimento para $selectedMealTab", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { viewModel.onSearchQueryChanged(it) },
                        placeholder = { Text("Buscar (ex: Arroz, Frango, Banana...)") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        singleLine = true,
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
                                HorizontalDivider(color = Color.LightGray.copy(alpha = 0.2f))
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
                        Text("Adicionar à Refeição")
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
        AlertDialog(
            onDismissRequest = { showCustomFoodDialog = false },
            title = { Text("Cadastrar Alimento", fontWeight = FontWeight.Bold) },
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
                    Text("Salvar Alimento")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCustomFoodDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    if (showGroceryBottomSheet) {
        val context = LocalContext.current
        GroceryListBottomSheet(
            groceryList = groceryList,
            onDismiss = { showGroceryBottomSheet = false },
            onToggleItem = { id, isChecked -> viewModel.togglePantryItem(id, isChecked) },
            onGenerateList = { viewModel.generateWeeklyGroceryList() },
            onExportPdf = { viewModel.exportAndShareGroceryPdf(context) },
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
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(0.5.dp, Color.LightGray.copy(alpha = 0.3f))
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
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.Restaurant,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(mealName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            "${recommendation.calories.avg.toInt()} kcal recomendadas",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
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
        border = BorderStroke(1.dp, TealPrimary.copy(alpha = 0.25f)),
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
                        color = TealPrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
                ) {
                    Text(
                        group.observedMarker,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
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
                    fontSize = 11.sp,
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
        modifier = Modifier.width(220.dp).height(240.dp),
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
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    color = BragaTextSecondary,
                    maxLines = 3
                )
                Text(
                    option.portionTip,
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 12.sp,
                    color = BragaEmerald,
                    maxLines = 2
                )
            }
            
            Button(
                onClick = onAdd,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = BragaEmerald),
                contentPadding = PaddingValues(horizontal = 8.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text(option.servingUnit, fontSize = 14.sp, fontWeight = FontWeight.Bold)
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
                        "Sugestões da Minha Despensa",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = BragaTextPrimary
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Ideias saudáveis com os alimentos que você tem em casa",
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

/**
 * Card de progresso hídrico com círculo de progresso e botões rápidos de adição.
 * Padrão visual do wireframe 111652 (Registro Rápido de Água).
 */
@Composable
fun HydrationProgressCard(
    currentMl: Int,
    targetMl: Int,
    onQuickAdd: (Int) -> Unit
) {
    val safeTarget = if (targetMl > 0) targetMl else 2000
    val progress = (currentMl.toFloat() / safeTarget).coerceIn(0f, 1f)
    val percent = (progress * 100).toInt()
    var showInfoDialog by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = BragaCardSurface),
        border = BorderStroke(1.dp, BragaMintBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = BragaMint,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.WaterDrop,
                                    contentDescription = null,
                                    tint = BragaEmerald,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "Hidratação de Hoje",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = BragaTextPrimary
                                )
                                IconButton(
                                    onClick = { showInfoDialog = true },
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(
                                        Icons.Outlined.Info,
                                        contentDescription = "Informações sobre cálculo de hidratação",
                                        tint = BragaEmerald,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                            Text(
                                "$currentMl / $safeTarget ml • $percent%",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = BragaEmerald
                            )
                        }
                    }
                }
                CircularProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.size(56.dp),
                    color = if (progress >= 1f) Success else BragaEmerald,
                    trackColor = BragaMint,
                    strokeWidth = 7.dp
                )
            }

            Spacer(Modifier.height(16.dp))

            // Botões rápidos de adição
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                HydrationQuickButton(label = "+200 ml", sub = "copo", modifier = Modifier.weight(1f), onClick = { onQuickAdd(200) })
                HydrationQuickButton(label = "+300 ml", sub = "copo grande", modifier = Modifier.weight(1f), onClick = { onQuickAdd(300) })
                HydrationQuickButton(label = "+500 ml", sub = "garrafa", modifier = Modifier.weight(1f), onClick = { onQuickAdd(500) })
            }
        }
    }

    // Diálogo informativo sobre o cálculo de hidratação
    if (showInfoDialog) {
        AlertDialog(
            onDismissRequest = { showInfoDialog = false },
            title = {
                Text(
                    "Cálculo de Hidratação Personalizado",
                    fontWeight = FontWeight.Bold,
                    color = BragaTextPrimary
                )
            },
            text = {
                Text(
                    "A recomendação de saúde para adultos e idosos é de 35 ml de água por quilo de peso corporal ao dia (ex: 70 kg × 35 ml = 2.450 ml).\n\nEssa meta garante hidratação celular, bom funcionamento renal e auxilia na estabilidade da pressão arterial.",
                    color = BragaTextSecondary,
                    lineHeight = 20.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = { showInfoDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = BragaEmerald)
                ) {
                    Text("Entendido")
                }
            },
            shape = RoundedCornerShape(16.dp),
            containerColor = BragaCardSurface
        )
    }
}

@Composable
private fun HydrationQuickButton(
    label: String,
    sub: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = BragaMint,
        modifier = modifier.clickable { onClick() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                label,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = BragaEmerald
            )
            Text(
                sub,
                style = MaterialTheme.typography.labelSmall,
                color = BragaTextSecondary
            )
        }
    }
}
