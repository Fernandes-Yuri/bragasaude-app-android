package br.com.bragasaude.ui.nutrition

import br.com.bragasaude.ui.components.BragaFormSheet

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import br.com.bragasaude.domain.groceryNameKey
import kotlinx.coroutines.delay
import br.com.bragasaude.data.remote.model.RemoteFood
import br.com.bragasaude.ui.theme.BragaCardBorder
import br.com.bragasaude.ui.theme.BragaTextPrimary
import br.com.bragasaude.ui.theme.Warning

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NutritionScreen(
    searchFood: String? = null,
    openGroceryList: Boolean = false,
    suggestedGroceryItems: List<String> = emptyList(),
    viewModel: NutritionViewModel = hiltViewModel(),
    onNavigateToPantryRecipes: () -> Unit = {}
) {
    val dailyCal by viewModel.dailyCalories.collectAsState()
    val consumedCal by viewModel.totalCaloriesConsumed.collectAsState()
    val userWeight by viewModel.userWeight.collectAsState()
    val isCustomCalorieTarget by viewModel.isCustomCalorieTarget.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val activeAdjustments by viewModel.activeAdjustments.collectAsState()
    val recommendations by viewModel.recommendations.collectAsState()
    val todayLoggedMeals by viewModel.todayLoggedMeals.collectAsState()
    val suggestionGroups by viewModel.functionalSuggestionGroups.collectAsState()
    val selectedMealTab by viewModel.selectedMealTab.collectAsState()
    val groceryList by viewModel.groceryList.collectAsState()
    val manualIngredients by viewModel.manualIngredients.collectAsState()
    val groceryMessage by viewModel.groceryMessage.collectAsState()
    val groceryPlan by viewModel.groceryPlanResult.collectAsState()
    val pantryStock by viewModel.pantryStock.collectAsState()
    val weeklyPreferences by viewModel.weeklyPreferences.collectAsState()

    val searchQuery by viewModel.searchQuery.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()

    var showFoodSelectorDialog by remember { mutableStateOf(false) }
    var showCustomFoodDialog by remember { mutableStateOf(false) }
    var showCalorieTargetDialog by remember { mutableStateOf(false) }
    val goalSetup by viewModel.goalSetup.collectAsState()
    val userProfile by viewModel.profile.collectAsState()
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

    if (isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    } else {
        NutritionContent(
            state = NutritionContentState(
                selectedMeal = selectedMealTab,
                meals = todayLoggedMeals,
                consumedCalories = consumedCal,
                targetCalories = dailyCal,
                customTarget = isCustomCalorieTarget,
                adjustments = activeAdjustments,
                recommendations = recommendations,
                suggestions = suggestionGroups
            ),
            onSelectMeal = viewModel::selectMealTab,
            onRegister = { showFoodSelectorDialog = true },
            onRemoveMeal = viewModel::removeLoggedMeal,
            onEditGoal = { showCalorieTargetDialog = true },
            onOpenRecipes = onNavigateToPantryRecipes,
            onOpenGrocery = {
                viewModel.prepareGroceryData()
                if (groceryList.isEmpty()) viewModel.generateWeeklyGroceryList()
                showGroceryBottomSheet = true
            },
            onAddSuggestion = { option ->
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
            onDismissSuggestion = { viewModel.dismissOrSwapSuggestion(it.name) }
        )
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
            title = { Text("Registrar alimento", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    Text(selectedMealTab, style = MaterialTheme.typography.bodyMedium)
                    if (selectedFoodForPortion == null) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { viewModel.onSearchQueryChanged(it) },
                            placeholder = { Text("Buscar alimento", maxLines = 1) },
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

                    }

                    val selectedFood = selectedFoodForPortion
                    if (selectedFood != null) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(selectedFood.name, fontWeight = FontWeight.Bold)
                                Text("${(selectedFood.kcal ?: 0.0).toInt()} kcal / 100g • Sugerido: ${selectedFood.servingUnit}", style = MaterialTheme.typography.bodySmall)
                                selectedFood.clinicalWarning?.let { warning ->
                                    Spacer(Modifier.height(6.dp))
                                    Surface(
                                        color = Warning.copy(alpha = 0.12f),
                                        shape = RoundedCornerShape(8.dp),
                                        border = BorderStroke(1.dp, Warning.copy(alpha = 0.35f))
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                Icons.Default.Info,
                                                contentDescription = null,
                                                tint = Warning,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Spacer(Modifier.width(6.dp))
                                            Text(
                                                text = warning,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = BragaTextPrimary,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }
                                }
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
                        TextButton(onClick = {
                            showFoodSelectorDialog = false
                            showCustomFoodDialog = true
                            keyboardController?.hide()
                            focusManager.clearFocus()
                        }) { Text("Não encontrou? Cadastrar alimento") }
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
                                        food.clinicalWarning?.let { warning ->
                                            Spacer(Modifier.height(2.dp))
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    Icons.Default.Info,
                                                    contentDescription = null,
                                                    tint = Warning,
                                                    modifier = Modifier.size(12.dp)
                                                )
                                                Spacer(Modifier.width(4.dp))
                                                Text(
                                                    text = warning,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = Warning,
                                                    fontWeight = FontWeight.Medium
                                                )
                                            }
                                        }
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
                        Text("Registrar porção")
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
            title = { Text("Cadastrar alimento", fontWeight = FontWeight.Bold) },
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
                        viewModel.onSearchQueryChanged(customFoodName)
                        customFoodName = ""
                        customFoodKcal = ""
                        customFoodCarbs = ""
                        customFoodProtein = ""
                        customFoodFat = ""
                        showCustomFoodDialog = false
                        showFoodSelectorDialog = true
                    }
                }) {
                    Text("Salvar alimento")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCustomFoodDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    // Wizard Clínico de Meta Calórica e Planejamento Semanal (Mifflin-St Jeor + Rotina + Orçamento)
    if (showCalorieTargetDialog) {
        NutritionGoalWizardSheet(
            initialSetup = goalSetup,
            userWeight = userWeight,
            userHeight = userProfile?.height,
            userBirthDate = userProfile?.birthDate,
            userGender = userProfile?.gender,
            onDismissRequest = { showCalorieTargetDialog = false },
            onApplySetup = { newSetup ->
                viewModel.applyNutritionGoalSetup(newSetup)
                showCalorieTargetDialog = false
            }
        )
    }

    if (showGroceryBottomSheet) {
        val context = LocalContext.current
        LaunchedEffect(Unit) { viewModel.refreshRetailCards() }
        val retailCards by viewModel.retailCards.collectAsState()
        val weeklyGoal by viewModel.weeklyGroceryGoal.collectAsState()
        val retailLoading by viewModel.retailLoading.collectAsState()
        val retailMessage by viewModel.retailMessage.collectAsState()
        val basketComparison by viewModel.basketComparison.collectAsState()
        GroceryListBottomSheet(
            groceryList = groceryList,
            retailCards = retailCards,
            weeklyGoal = weeklyGoal,
            onPlanningDaysChange = viewModel::changePlanningDays,
            retailLoading = retailLoading,
            retailMessage = retailMessage,
            onRefreshRetail = { viewModel.refreshRetailCards() },
            basketComparison = basketComparison,
            onCompareMarkets = viewModel::compareGroceryBasket,
            onAddCard = viewModel::addOrIncrementCard,
            onDecrementCard = viewModel::decrementCard,
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
