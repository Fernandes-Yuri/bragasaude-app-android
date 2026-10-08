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
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    isLoading: Boolean = false
) {
    var reportingItem by remember { mutableStateOf<GroceryListItemEntity?>(null) }
    var manualEditingItem by remember { mutableStateOf<GroceryListItemEntity?>(null) }
    var isCreatingManual by remember { mutableStateOf(false) }
    var showPlanOptionsDialog by remember { mutableStateOf(false) }

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
    val dailyAvg = if (totalCost > 0.0) totalCost / 7.0 else 0.0
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
                    "Planejamento semanal com IA",
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
                            "Meta semanal calculada: ${plan.targetWeeklyCalories.toInt()} kcal (${plan.plannedWeeklyCalories.toInt()} kcal planejadas para 7 dias). Escolha como deseja atualizar:"
                        else
                            "Sua lista já possui alimentos cadastrados. Escolha como deseja atualizar o seu planejamento semanal:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = BragaTextSecondary
                    )

                    Surface(
                        onClick = {
                            showPlanOptionsDialog = false
                            onGenerateList(true)
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
                                    "Ajusta porções para a meta de 7 dias mantendo itens adicionados manualmente.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = BragaTextSecondary
                                )
                            }
                        }
                    }

                    Surface(
                        onClick = {
                            showPlanOptionsDialog = false
                            onGenerateList(false)
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
                                    "Gerar nova variedade semanal",
                                    fontWeight = FontWeight.SemiBold,
                                    color = BragaTextPrimary,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    "Cria uma nova seleção completa de alimentos para a semana inteira.",
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
            // 1. Cabeçalho Principal (Título + Contador de itens + Compartilhar PDF + Botão fechar)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Lista de compras",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = BragaTextPrimary
                    )
                    Text(
                        text = if (groceryList.isEmpty()) "Nenhum item na lista no momento"
                               else "${groceryList.size} alimento(s) • $checkedCount comprado(s)",
                        style = MaterialTheme.typography.bodyMedium,
                        color = BragaTextSecondary
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (groceryList.isNotEmpty()) {
                        IconButton(onClick = onExportPdf) {
                            Icon(
                                Icons.Default.Share,
                                contentDescription = "Compartilhar lista em PDF",
                                tint = BragaEmeraldDark
                            )
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Fechar lista de compras",
                            tint = BragaTextSecondary
                        )
                    }
                }
            }

            // 2. Barra de Ações Rápidas (Proporcionais 1:1, Sem Quebra de Linha e com Planejador Semanal Contextual)
            val hasItems = groceryList.isNotEmpty()
            val needsResize = plan?.isManuallyModified == true || (plan != null && plan.coveragePercent < WeeklyGroceryEngine.MIN_ENERGY_COVERAGE_PERCENT)
            val buttonLabel = when {
                isLoading -> "Planejando..."
                !hasItems -> "Planejar com IA"
                needsResize -> "Recalcular meta"
                else -> "Opções com IA"
            }
            val buttonIcon = when {
                needsResize -> Icons.Default.Refresh
                else -> Icons.Default.AutoAwesome
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = { isCreatingManual = true },
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = BragaEmerald),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "Adicionar",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                }

                OutlinedButton(
                    onClick = {
                        if (isLoading) return@OutlinedButton
                        if (!hasItems) {
                            onGenerateList(true)
                        } else {
                            showPlanOptionsDialog = true
                        }
                    },
                    enabled = !isLoading,
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(
                        if (needsResize) 1.5.dp else 1.dp,
                        if (needsResize) BragaEmerald else BragaMintBorder
                    ),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = if (needsResize) BragaMintSurface else BragaMintSurface.copy(alpha = 0.5f)
                    ),
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = BragaEmeraldDark
                        )
                        Spacer(Modifier.width(6.dp))
                    } else {
                        Icon(
                            buttonIcon,
                            contentDescription = null,
                            tint = BragaEmeraldDark,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(
                        buttonLabel,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = BragaEmeraldDark,
                        maxLines = 1
                    )
                }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (plan != null) {
                    item { WeeklyGrocerySummaryCard(plan, onResize) }
                }
                val checkedSlugs = groceryList.filter { it.isCheckedInPantry }.map { it.foodId }.toSet()
                val trackedStock = pantryStock.filter { it.ingredientSlug in checkedSlugs }
                if (trackedStock.isNotEmpty()) {
                    item {
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("Saldo estimado da despensa", style = MaterialTheme.typography.titleMedium)
                                trackedStock.forEach { stock ->
                                    val row = groceryList.first { it.foodId == stock.ingredientSlug }
                                    val shortage = br.com.bragasaude.domain.GroceryConsumption.insufficient(stock.availableAmount,
                                        row.plannedWeeklyAmount, br.com.bragasaude.domain.GroceryWeek.daysRemaining())
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
                                    Text("Estimativa da semana", style = MaterialTheme.typography.labelLarge, color = BragaTextSecondary)
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
                            }
                        }
                    }
                }

                item {
                    Text(
                        br.com.bragasaude.domain.GROCERY_PRICE_NOTICE,
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
                                    onClick = { onGenerateList(true) },
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
                                        Text("Planejando lista semanal...", color = BragaEmeraldDark)
                                    } else {
                                        Icon(
                                            Icons.Default.AutoAwesome,
                                            contentDescription = null,
                                            tint = BragaEmeraldDark,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text("Planejar lista semanal com IA", color = BragaEmeraldDark)
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
                                GroceryItemRow(
                                    item = entry,
                                    onToggle = { checked -> onToggleItem(entry.remoteId, checked) },
                                    onEdit = { manualEditingItem = entry },
                                    onDelete = { onRemoveItem(entry.remoteId) }
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
                                TextButton(
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

@Composable
fun GroceryItemRow(
    item: GroceryListItemEntity,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit = {},
    onDelete: () -> Unit = {}
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = item.isCheckedInPantry, role = Role.Checkbox, onValueChange = onToggle),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (item.isCheckedInPantry) BragaMintSurface else BragaCardSurface
        ),
        border = if (item.isCheckedInPantry) BorderStroke(1.dp, BragaMintBorder) else BorderStroke(1.dp, BragaCardBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = item.isCheckedInPantry,
                onCheckedChange = null,
                colors = CheckboxDefaults.colors(
                    checkedColor = BragaEmerald,
                    uncheckedColor = BragaCardBorder
                )
            )

            Spacer(modifier = Modifier.width(8.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.foodName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    textDecoration = if (item.isCheckedInPantry) TextDecoration.LineThrough else TextDecoration.None,
                    color = if (item.isCheckedInPantry) BragaTextSecondary else BragaTextPrimary
                )
                Text(
                    text = "Comprar: ${item.purchaseUnitText}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = BragaTextSecondary
                )
                if (item.estimatedPriceBrl > 0.0) {
                    Text(
                        text = String.format(Locale.getDefault(), "Estimado: R$ %.2f", item.estimatedPriceBrl),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (item.isCheckedInPantry) BragaTextSecondary else BragaEmeraldDark
                    )
                }
            }

            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(36.dp)
            ) {
                IconButton(onClick = onEdit, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = "Editar item",
                        tint = BragaTextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(Modifier.width(6.dp))

            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
                modifier = Modifier.size(36.dp)
            ) {
                IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Remover item",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
