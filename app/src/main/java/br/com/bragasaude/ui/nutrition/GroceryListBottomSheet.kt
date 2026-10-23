package br.com.bragasaude.ui.nutrition

import br.com.bragasaude.ui.components.BragaAlertDialog
import br.com.bragasaude.ui.components.BragaBottomSheet
import br.com.bragasaude.domain.WeeklyGroceryEngine

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import br.com.bragasaude.data.local.GroceryListItemEntity
import java.util.Locale
import br.com.bragasaude.ui.theme.BragaCardBorder
import br.com.bragasaude.ui.theme.BragaCardSurface
import br.com.bragasaude.ui.theme.BragaEmerald
import br.com.bragasaude.ui.theme.BragaEmeraldDark
import br.com.bragasaude.ui.theme.BragaMintBorder
import br.com.bragasaude.ui.theme.BragaMintSurface
import br.com.bragasaude.ui.theme.BragaTextPrimary
import br.com.bragasaude.ui.theme.BragaTextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroceryListBottomSheet(
    groceryList: List<GroceryListItemEntity>,
    onDismiss: () -> Unit,
    onToggleItem: (String, Boolean) -> Unit,
    onGenerateList: (preserveManual: Boolean) -> Unit,
    onExportPdf: () -> Unit,
    // Agente B1: itens sugeridos pelo chat; o usuário confirma via botão.
    suggestedItems: List<String> = emptyList(),
    onAddSuggested: (List<String>) -> Unit = {},
    contributionState: br.com.bragasaude.domain.GroceryContributionState = br.com.bragasaude.domain.GroceryContributionState(),
    communityPrices: List<br.com.bragasaude.domain.CommunityGroceryPrice> = emptyList(),
    onStartContribution: () -> Unit = {},
    onContribute: (GroceryListItemEntity, String, String, String, String, String) -> Unit = { _, _, _, _, _, _ -> },
    manualIngredients: List<br.com.bragasaude.domain.GroceryIngredient> = emptyList(),
    onSaveManualItem: (String, String, String?) -> Unit = { _, _, _ -> },
    onRemoveItem: (String) -> Unit = {},
    onClearList: () -> Unit = {},
    groceryMessage: String? = null,
    plan: br.com.bragasaude.domain.WeeklyGroceryPlanResult? = null,
    pantryStock: List<br.com.bragasaude.data.local.GroceryPantryStockEntity> = emptyList(),
    onResize: () -> Unit = { onGenerateList(true) },
    isLoading: Boolean = false,
    weeklyPreferences: br.com.bragasaude.domain.WeeklyGroceryPreferences = br.com.bragasaude.domain.WeeklyGroceryPreferences(),
    retailCards: List<br.com.bragasaude.data.local.GroceryRetailCardEntity> = emptyList(),
    weeklyGoal: br.com.bragasaude.domain.GroceryWeeklyGoal = br.com.bragasaude.domain.GroceryWeeklyGoal(),
    onPlanningDaysChange: (Int) -> Unit = {},
    retailLoading: Boolean = false,
    retailMessage: String? = null,
    onRefreshRetail: () -> Unit = {},
    onAddCard: (br.com.bragasaude.data.local.GroceryRetailCardEntity) -> Unit = {},
    onDecrementCard: (String) -> Unit = {},
    onGenerateListWithPreferences: ((preserveManual: Boolean, preferences: br.com.bragasaude.domain.WeeklyGroceryPreferences) -> Unit)? = null
) {
    var showCatalog by rememberSaveable { mutableStateOf(false) }
    var showPlanningDetails by rememberSaveable { mutableStateOf(false) }
    var choosingPeriod by rememberSaveable { mutableStateOf(false) }
    var reportingItem by remember { mutableStateOf<GroceryListItemEntity?>(null) }
    var manualEditingItem by remember { mutableStateOf<GroceryListItemEntity?>(null) }
    var isCreatingManual by remember { mutableStateOf(false) }
    var showPlanOptionsDialog by remember { mutableStateOf(false) }
    var showPreferencesSheet by remember { mutableStateOf(false) }
    var pendingPreserveManual by remember { mutableStateOf(true) }

    if (isCreatingManual || manualEditingItem != null) {
        ManualGroceryItemSheet(
            ingredients = manualIngredients,
            editing = manualEditingItem,
            onDismiss = {
                isCreatingManual = false
                manualEditingItem = null
            },
            onSave = onSaveManualItem
        )
        return
    }

    reportingItem?.let { selected ->
        GroceryPriceContributionSheet(
            selected,
            contributionState,
            onDismiss = { reportingItem = null },
            onSubmit = { amount, quantity, unit, state, date ->
                onContribute(selected, amount, quantity, unit, state, date)
            }
        )
        return
    }

    val totalCost = groceryList.filter { it.estimatedPriceBrl > 0.0 }.sumOf { it.estimatedPriceBrl }
    val dailyAvg = if (totalCost > 0.0) totalCost / weeklyGoal.planningDays.toDouble() else 0.0
    val checkedCount = groceryList.count { it.isCheckedInPantry }
    val grouped = remember(groceryList) { groceryList.groupBy { it.category } }

    if (showPlanOptionsDialog) {
        BragaAlertDialog(
            onDismissRequest = { showPlanOptionsDialog = false },
            icon = {
                Icon(
                    Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = BragaEmeraldDark,
                    modifier = Modifier.size(28.dp)
                )
            },
            title = {
                Text(
                    "Planejamento de compras",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleLarge
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = if (plan != null)
                            "Meta do período: ${plan.targetWeeklyCalories.toInt()} kcal (${plan.plannedWeeklyCalories.toInt()} kcal planejadas para ${plan.planningDays} dias). Escolha como deseja atualizar:"
                        else
                            "Sua lista já possui alimentos cadastrados. Escolha como deseja atualizar o seu planejamento de compras:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = BragaTextSecondary
                    )

                    Surface(
                        onClick = {
                            showPlanOptionsDialog = false
                            pendingPreserveManual = true
                            showPreferencesSheet = true
                        },
                        shape = RoundedCornerShape(14.dp),
                        color = BragaMintSurface,
                        border = BorderStroke(1.dp, BragaMintBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = BragaEmerald.copy(alpha = 0.15f),
                                modifier = Modifier.size(38.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Default.Refresh,
                                        contentDescription = null,
                                        tint = BragaEmeraldDark,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Recalcular para meta diária",
                                    fontWeight = FontWeight.SemiBold,
                                    color = BragaTextPrimary,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    "Ajusta porções para a meta do período escolhido mantendo itens adicionados manualmente.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = BragaTextSecondary
                                )
                            }
                        }
                    }

                    Surface(
                        onClick = {
                            showPlanOptionsDialog = false
                            pendingPreserveManual = false
                            showPreferencesSheet = true
                        },
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.size(38.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Default.AutoAwesome,
                                        contentDescription = null,
                                        tint = BragaEmeraldDark,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Gerar nova variedade para o período",
                                    fontWeight = FontWeight.SemiBold,
                                    color = BragaTextPrimary,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    "Cria uma nova seleção completa de alimentos para o período escolhido.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = BragaTextSecondary
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPlanOptionsDialog = false }) {
                    Text("Cancelar", color = BragaTextSecondary)
                }
            }
        )
    }

    BragaBottomSheet(
        onDismissRequest = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
        ) {
            val planningHeader: @Composable () -> Unit = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { showPlanningDetails = !showPlanningDetails }) {
                        Text(if (showPlanningDetails) "Recolher resumo do plano" else "Ver resumo do plano")
                    }
                    if (showPlanningDetails) {
                        Column(Modifier.testTag("groceryPlanDetails"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (weeklyGoal.planningDays > 7) Text("Planeje reposições de frutas, verduras e outros perecíveis durante o período.", style = MaterialTheme.typography.bodySmall)
                            groceryList.firstOrNull()?.let { first ->
                                val end = java.time.LocalDate.parse(first.weekStartDate).plusDays(first.planningDays.toLong() - 1)
                                Text("Período: ${first.weekStartDate} a $end", style = MaterialTheme.typography.bodySmall)
                            }
                            GroceryWeeklyGoalBar(weeklyGoal)
                            Text("Estimativa orçamentária. O app não vende produtos nem garante ofertas ou disponibilidade. Valores e lojas são referências de pesquisa e podem variar por região e data.", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            // Compact, opaque navigation remains separate from both scrolling lists.
            Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 2.dp) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    FilterChip(selected = !showCatalog, onClick = { showCatalog = false }, label = { Text("Minha lista", maxLines = 1) })
                    FilterChip(selected = showCatalog, onClick = { showCatalog = true }, label = { Text("Mercadinho", maxLines = 1) })
                    Spacer(Modifier.weight(1f))
                    if (groceryList.isNotEmpty()) {
                        IconButton(onClick = onExportPdf) {
                            Icon(Icons.Default.Share, contentDescription = "Compartilhar lista em PDF")
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Fechar lista de compras")
                    }
                }
            }
            GroceryQuickActions(weeklyGoal.planningDays, isLoading, onPlanningDaysChange,
                { choosingPeriod = true }, { isCreatingManual = true }, {
                    if (groceryList.isEmpty()) { pendingPreserveManual = false; showPreferencesSheet = true }
                    else showPlanOptionsDialog = true
                })
            if (showCatalog) {
                GroceryMarketShelfScreen(retailCards, groceryList, retailLoading, retailMessage,
                    onRefreshRetail, onAddCard, onDecrementCard,
                    Modifier.fillMaxWidth().weight(1f), allowSelection = true,
                    header = planningHeader)
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f).testTag("groceryItems"),
                    contentPadding = PaddingValues(vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item(key = "planningHeader") { planningHeader() }
                    if (showPlanningDetails && plan != null && groceryList.all { it.retailCardId == null }) {
                        item { WeeklyGrocerySummaryCard(plan, onResize) }
                    }
                    val checkedSlugs = groceryList.filter { it.isCheckedInPantry }.map { it.linkedIngredientSlug ?: it.foodId }.toSet()
                    val trackedStock = pantryStock.filter { it.ingredientSlug in checkedSlugs }
                    if (trackedStock.isNotEmpty()) {
                        item {
                            Card(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text("Saldo estimado da despensa", style = MaterialTheme.typography.titleMedium)
                                    trackedStock.forEach { stock ->
                                        val row = groceryList.first { (it.linkedIngredientSlug ?: it.foodId) == stock.ingredientSlug }
                                        val shortage = br.com.bragasaude.domain.GroceryConsumption.insufficient(stock.availableAmount,
                                            row.plannedWeeklyAmount, br.com.bragasaude.domain.GroceryPlanningPeriod.daysRemaining(row.weekStartDate, row.planningDays), row.planningDays)
                                        val amount = when (stock.unit) {
                                            "kg" -> "${stock.availableAmount.toInt()} g"
                                            "L" -> String.format(Locale.getDefault(), "%.2f L", stock.availableAmount / 1000)
                                            else -> String.format(Locale.getDefault(), "%.1f un", stock.availableAmount)
                                        }
                                        Text("${row.foodName}: $amount" + if (shortage) " • insuficiente para os dias restantes" else "")
                                    }
                                    Text("Baixa estimada pelos registros de refeições; não representa medição do estoque.", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                    if (groceryMessage != null && (plan == null || groceryMessage != plan.statusMessage)) {
                        item {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = BragaMintSurface,
                                border = BorderStroke(1.dp, BragaMintBorder)
                            ) {
                                Text(
                                    groceryMessage,
                                    modifier = Modifier.padding(12.dp),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = BragaEmeraldDark
                                )
                            }
                        }
                    }

                    if (groceryList.isNotEmpty()) {
                        item {
                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = BragaMintSurface),
                                border = BorderStroke(1.dp, BragaMintBorder)
                            ) {
                                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("Estimativa do período", style = MaterialTheme.typography.labelLarge, color = BragaTextSecondary)
                                        TextButton(
                                            onClick = onClearList,
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                        ) {
                                            Text("Limpar lista", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                    Text(
                                        String.format(Locale.getDefault(), "R$ %.2f", totalCost),
                                        style = MaterialTheme.typography.headlineSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = BragaTextPrimary
                                    )
                                    Text(
                                        String.format(Locale.getDefault(), "Média de R$ %.2f por dia • %d de %d itens comprados", dailyAvg, checkedCount, groceryList.size),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = BragaEmeraldDark
                                    )

                                    val maxBudget = weeklyPreferences.maxWeeklyBudgetReais
                                    if (maxBudget != null && maxBudget > 0.0) {
                                        val isOverBudget = totalCost > maxBudget
                                        val diff = kotlin.math.abs(totalCost - maxBudget)
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = if (isOverBudget) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f) else BragaEmerald.copy(alpha = 0.12f),
                                            border = BorderStroke(1.dp, if (isOverBudget) MaterialTheme.colorScheme.error.copy(alpha = 0.5f) else BragaEmerald.copy(alpha = 0.3f)),
                                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text(
                                                    text = if (isOverBudget) "Excede o teto em R$ ${String.format(Locale.getDefault(), "%.2f", diff)}"
                                                           else "Dentro do teto (R$ ${String.format(Locale.getDefault(), "%.2f", diff)} livres)",
                                                    style = MaterialTheme.typography.labelMedium,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = if (isOverBudget) MaterialTheme.colorScheme.error else BragaEmeraldDark
                                                )
                                                Text(
                                                    text = "Teto: R$ ${String.format(Locale.getDefault(), "%.2f", maxBudget)}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = BragaTextSecondary
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    item {
                        Text(
                            if (showPlanningDetails) br.com.bragasaude.domain.GROCERY_PRICE_NOTICE
                            else "Preços estimados. Valores e disponibilidade podem variar.",
                            style = MaterialTheme.typography.bodySmall,
                            color = BragaTextSecondary
                        )
                    }

                    if (suggestedItems.isNotEmpty()) {
                        item {
                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = BragaMintSurface),
                                border = BorderStroke(1.dp, BragaMintBorder)
                            ) {
                                Column(
                                    Modifier.fillMaxWidth().padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Text("Sugestões do Assistente", style = MaterialTheme.typography.titleSmall, color = BragaEmeraldDark)
                                    Text(suggestedItems.joinToString(", "), style = MaterialTheme.typography.bodyMedium)
                                    Button(
                                        onClick = { onAddSuggested(suggestedItems) },
                                        modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = BragaEmerald)
                                    ) {
                                        Text("Adicionar sugestões (${suggestedItems.size})", style = MaterialTheme.typography.bodyMedium)
                                    }
                                }
                            }
                        }
                    }

                    if (groceryList.isEmpty()) {
                        item {
                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = BragaCardSurface),
                                border = BorderStroke(1.dp, BragaCardBorder),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    Modifier.fillMaxWidth().padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Icon(
                                        Icons.Default.ShoppingCart,
                                        contentDescription = null,
                                        tint = BragaEmerald,
                                        modifier = Modifier.size(48.dp)
                                    )
                                    Text(
                                        "Sua lista está livre para você montar",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = BragaTextPrimary
                                    )
                                    Text(
                                        "Você pode adicionar quantos alimentos quiser do catálogo ou gerar uma lista automática sugerida com um clique.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = BragaTextSecondary,
                                        textAlign = TextAlign.Center
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Button(
                                        onClick = { isCreatingManual = true },
                                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = BragaEmerald)
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(8.dp))
                                        Text("Adicionar alimentos manualmente")
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            pendingPreserveManual = false
                                            showPreferencesSheet = true
                                        },
                                        enabled = !isLoading,
                                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                                        border = BorderStroke(1.dp, BragaMintBorder)
                                    ) {
                                        if (isLoading) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(18.dp),
                                                strokeWidth = 2.dp,
                                                color = BragaEmeraldDark
                                            )
                                            Spacer(Modifier.width(8.dp))
                                            Text("Planejando lista de compras...", color = BragaEmeraldDark)
                                        } else {
                                            Icon(
                                                Icons.Default.AutoAwesome,
                                                contentDescription = null,
                                                tint = BragaEmeraldDark,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(Modifier.width(8.dp))
                                            Text("Planejar lista de compras com IA", color = BragaEmeraldDark)
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        grouped.forEach { (category, entries) ->
                            item {
                                Text(
                                    category,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = BragaEmeraldDark,
                                    modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
                                )
                            }
                            items(entries, key = { it.remoteId }) { entry ->
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    val retailCard = resolveShoppingRetailCard(entry, retailCards)
                                    GroceryShoppingCard(
                                        item = entry, retailCard = retailCard,
                                        onToggle = { checked -> onToggleItem(entry.remoteId, checked) },
                                        onEdit = { manualEditingItem = entry },
                                        onDelete = { onRemoveItem(entry.remoteId) },
                                        onIncrease = retailCard?.let { card -> { onAddCard(card) } },
                                        onDecrease = retailCard?.let { card -> { onDecrementCard(card.id) } }
                                    )
                                    communityPrices.filter { it.foodName.equals(entry.foodName, ignoreCase = true) }.forEach { price ->
                                        Text(
                                            String.format(
                                                Locale.getDefault(),
                                                "Referência da comunidade: R$ %.2f/%s (%d pessoas)",
                                                price.average, price.unit, price.contributors
                                            ),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = BragaTextSecondary,
                                            modifier = Modifier.padding(horizontal = 12.dp)
                                        )
                                    }
                                    if (entry.retailCardId == null) TextButton(
                                        onClick = { onStartContribution(); reportingItem = entry },
                                        modifier = Modifier.heightIn(min = 36.dp)
                                    ) {
                                        Text("Informar quanto paguei", style = MaterialTheme.typography.labelMedium)
                                    }
                                }
                            }
                        }
                    }

                    item { Spacer(Modifier.height(16.dp)) }
                }
            }
        }
    }

    if (choosingPeriod) br.com.bragasaude.ui.components.BragaFormSheet(
        onDismissRequest = { choosingPeriod = false },
        title = { Text("Período da lista") },
        text = {
            Column {
                br.com.bragasaude.domain.GroceryPlanningPeriod.options.forEach { days ->
                    TextButton(onClick = { onPlanningDaysChange(days); choosingPeriod = false },
                        enabled = !isLoading, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        Text("$days dias" + if (weeklyGoal.planningDays == days) " · selecionado" else "")
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { choosingPeriod = false }) { Text("Fechar") } }
    )

    if (showPreferencesSheet) {
        WeeklyGroceryPreferencesSheet(
            initialPreferences = weeklyPreferences,
            onDismissRequest = { showPreferencesSheet = false },
            onConfirm = { chosenPreferences ->
                showPreferencesSheet = false
                if (onGenerateListWithPreferences != null) {
                    onGenerateListWithPreferences(pendingPreserveManual, chosenPreferences)
                } else {
                    onGenerateList(pendingPreserveManual)
                }
            }
        )
    }
}
