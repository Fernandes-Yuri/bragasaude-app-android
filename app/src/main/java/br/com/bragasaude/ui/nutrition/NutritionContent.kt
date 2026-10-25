package br.com.bragasaude.ui.nutrition

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import br.com.bragasaude.R
import androidx.compose.ui.res.stringResource
import br.com.bragasaude.domain.HealthCalculators
import br.com.bragasaude.domain.MealLogItem
import br.com.bragasaude.domain.NutritionalSuggestionGroup
import br.com.bragasaude.domain.SuggestedFoodOption
import br.com.bragasaude.ui.components.BragaFormSheet

internal data class NutritionContentState(
    val selectedMeal: String = "Café da Manhã",
    val meals: List<MealLogItem> = emptyList(),
    val consumedCalories: Double = 0.0,
    val targetCalories: Float = 1800f,
    val customTarget: Boolean = false,
    val adjustments: List<String> = emptyList(),
    val recommendations: List<Pair<String, HealthCalculators.MealRecommendation>> = emptyList(),
    val suggestions: List<NutritionalSuggestionGroup> = emptyList()
)

/** Conteúdo sem dependências de rede ou banco, compartilhado pela tela e sua revisão visual. */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
internal fun NutritionContent(
    state: NutritionContentState,
    onSelectMeal: (String) -> Unit,
    onRegister: () -> Unit,
    onRemoveMeal: (String) -> Unit,
    onEditGoal: () -> Unit,
    onOpenRecipes: () -> Unit,
    onOpenGrocery: () -> Unit,
    onAddSuggestion: (SuggestedFoodOption) -> Unit,
    onDismissSuggestion: (SuggestedFoodOption) -> Unit,
    modifier: Modifier = Modifier
) {
    var showSuggestions by rememberSaveable { mutableStateOf(false) }
    var showPlan by rememberSaveable { mutableStateOf(false) }
    val meals = state.meals.filter { it.mealType == state.selectedMeal }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Alimentação", fontWeight = FontWeight.SemiBold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        modifier = modifier
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).testTag("nutritionContent"),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            item(key = "mealSelector") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf("Café da Manhã" to "Café", "Almoço" to "Almoço",
                        "Lanche da Tarde" to "Lanche", "Jantar" to "Jantar").forEach { (meal, label) ->
                        FilterChip(
                            selected = state.selectedMeal == meal,
                            onClick = { onSelectMeal(meal) },
                            label = { Text(label) },
                            modifier = Modifier.semantics { contentDescription = meal }
                        )
                    }
                }
            }
            item(key = "registration") {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(state.selectedMeal, style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold)
                    // A ação permanece antes dos registros, mesmo em refeições com muitos alimentos.
                    Button(
                        onClick = onRegister,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("registerFood"),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Registrar alimento")
                    }
                    Text(
                        if (meals.isEmpty()) "Ainda sem registros" else "${meals.sumOf { it.kcal }.toInt()} kcal nesta refeição",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            items(meals, key = { "meal-${it.id}" }) { meal ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(meal.foodName, style = MaterialTheme.typography.bodyLarge)
                        Text("${meal.portionGrams} g · ${meal.kcal.toInt()} kcal",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { onRemoveMeal(meal.id) }) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = "Remover ${meal.foodName}",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            item(key = "dailySummary") {
                HorizontalDivider()
                Column(Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Total de hoje", style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${state.consumedCalories.toInt()} kcal", style = MaterialTheme.typography.titleLarge)
                            Text("Meta diária: ${state.targetCalories.toInt()} kcal",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = onEditGoal) {
                            Icon(Icons.Default.Edit, contentDescription = "Ajustar meta diária")
                        }
                    }
                    if (state.consumedCalories > 0) {
                        LinearProgressIndicator(
                            progress = { if (state.targetCalories > 0) (state.consumedCalories / state.targetCalories).toFloat().coerceIn(0f, 1f) else 0f },
                            modifier = Modifier.fillMaxWidth(),
                            color = if (state.consumedCalories > state.targetCalories) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
            item(key = "suggestionsToggle") {
                NutritionSectionToggle("Sugestões para esta refeição", showSuggestions,
                    onClick = { showSuggestions = !showSuggestions })
            }
            if (showSuggestions) {
                if (state.suggestions.isEmpty()) {
                    item(key = "noSuggestions") {
                        Text("Nenhuma sugestão para esta refeição com suas preferências atuais.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                items(state.suggestions, key = { "suggestions-${it.id}" }) { group ->
                    NutritionSuggestionGroup(group, state.selectedMeal, onAddSuggestion, onDismissSuggestion)
                }
            }
            item(key = "planningLinks") {
                Column {
                    HorizontalDivider()
                    ListItem(
                        headlineContent = { Text("Receitas") },
                        leadingContent = { Icon(Icons.Default.RestaurantMenu, contentDescription = null) },
                        trailingContent = { Icon(Icons.Default.ChevronRight, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth().nutritionLink(onOpenRecipes),
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background)
                    )
                    ListItem(
                        headlineContent = { Text("Lista de compras") },
                        leadingContent = { Icon(Icons.Default.ShoppingCart, contentDescription = null) },
                        trailingContent = { Icon(Icons.Default.ChevronRight, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth().nutritionLink(onOpenGrocery),
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background)
                    )
                }
            }
            item(key = "planToggle") {
                NutritionSectionToggle("Sobre seu plano", showPlan, onClick = { showPlan = !showPlan })
            }
            if (showPlan) {
                item(key = "planContext") {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(if (state.customTarget) "Meta personalizada" else "Meta calculada a partir do seu perfil",
                            style = MaterialTheme.typography.bodyMedium)
                        if (state.adjustments.isNotEmpty()) {
                            Text("Ajustes: ${state.adjustments.joinToString(", ")}",
                                style = MaterialTheme.typography.bodyMedium)
                        }
                        Text(stringResource(R.string.nutrition_disclaimer),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                items(state.recommendations, key = { "recommendation-${it.first}" }) { (meal, recommendation) ->
                    NutritionMealRecommendation(meal, recommendation)
                }
            }
        }
    }
}

@Composable
private fun NutritionSectionToggle(title: String, expanded: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().nutritionLink(onClick)
            .semantics { stateDescription = if (expanded) "Expandido" else "Recolhido" }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
            contentDescription = if (expanded) "Recolher $title" else "Expandir $title")
    }
}

// Um único alvo acessível por linha; não cria botões de ícone sobrepostos ao clique da linha.
private fun Modifier.nutritionLink(onClick: () -> Unit) =
    this.clickable(role = Role.Button, onClick = onClick)

@Composable
internal fun NutritionSuggestionGroup(
    group: NutritionalSuggestionGroup,
    selectedMeal: String,
    onAdd: (SuggestedFoodOption) -> Unit,
    onDismiss: (SuggestedFoodOption) -> Unit
) {
    var showExplanation by rememberSaveable(group.id) { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(group.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        group.options.forEach { option ->
            Column(
                Modifier.fillMaxWidth().testTag("suggestion-${group.id}-${option.foodId}"),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(option.name, style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.testTag("suggestionName-${group.id}-${option.foodId}"))
                        Text(option.servingUnit, style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { onDismiss(option) }) {
                        Icon(Icons.Default.Close, contentDescription = "Dispensar ${option.name}",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                OutlinedButton(
                    onClick = { onAdd(option) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                        .semantics { contentDescription = "Registrar ${option.servingUnit} de ${option.name} no $selectedMeal" },
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
                ) { Text("Registrar porção") }
                HorizontalDivider(Modifier.padding(top = 8.dp))
            }
        }
        TextButton(onClick = { showExplanation = true }) { Text("Por que estas opções?") }
    }
    if (showExplanation) {
        BragaFormSheet(
            onDismissRequest = { showExplanation = false },
            title = { Text(group.title) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(group.subtitle, style = MaterialTheme.typography.titleSmall)
                    Text(group.whyItMatters, style = MaterialTheme.typography.bodyMedium)
                    group.options.forEach { option ->
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(option.name, fontWeight = FontWeight.SemiBold)
                            Text(option.functionalBenefit)
                            Text(option.portionTip)
                        }
                    }
                    Text(group.disclaimer, style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = { TextButton(onClick = { showExplanation = false }) { Text("Entendi") } }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NutritionMealRecommendation(meal: String, recommendation: HealthCalculators.MealRecommendation) {
    var expanded by rememberSaveable(meal) { mutableStateOf(false) }
    Column {
        NutritionSectionToggle("$meal · ${recommendation.calories.avg.toInt()} kcal", expanded,
            onClick = { expanded = !expanded })
        AnimatedVisibility(expanded) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Carboidratos: ${recommendation.carbs.avg.toInt()} g")
                Text("Proteínas: ${recommendation.protein.avg.toInt()} g")
                Text("Gorduras: ${recommendation.fat.avg.toInt()} g")
            }
        }
    }
}
