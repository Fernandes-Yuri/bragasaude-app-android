package br.com.bragasaude.ui.nutrition

import br.com.bragasaude.ui.components.BragaFormSheet

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
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
    onGenerateList: () -> Unit,
    onExportPdf: () -> Unit,
    manualIngredients: List<br.com.bragasaude.domain.GroceryIngredient> = emptyList(),
    message: String? = null,
    isLoading: Boolean = false,
    onSaveManualItem: (String, String, String?) -> Unit = { _, _, _ -> },
    onRemoveItem: (String) -> Unit = {},
    // Agente B1: itens sugeridos pelo chat; o usuário confirma via botão.
    suggestedItems: List<String> = emptyList(),
    onAddSuggested: (List<String>) -> Unit = {},
    contributionState: br.com.bragasaude.domain.GroceryContributionState = br.com.bragasaude.domain.GroceryContributionState(),
    communityPrices: List<br.com.bragasaude.domain.CommunityGroceryPrice> = emptyList(),
    onStartContribution: () -> Unit = {},
    onContribute: (GroceryListItemEntity, String, String, String, String, String) -> Unit = { _, _, _, _, _, _ -> }
) {
    var showManual by remember { mutableStateOf(false) }
    var editingItem by remember { mutableStateOf<GroceryListItemEntity?>(null) }
    var confirmRegenerate by remember { mutableStateOf(false) }
    var removingItem by remember { mutableStateOf<GroceryListItemEntity?>(null) }
    if (showManual) {
        ManualGroceryItemSheet(manualIngredients, editingItem,
            onDismiss = { showManual = false; editingItem = null }, onSave = onSaveManualItem)
        return
    }
    if (confirmRegenerate) br.com.bragasaude.ui.components.BragaAlertDialog(
        onDismissRequest = { confirmRegenerate = false },
        title = { Text("Gerar nova lista aleatória?") },
        text = { Text("A lista atual e suas marcações de compra serão substituídas. Você também pode continuar editando os itens da sua lista.") },
        confirmButton = { TextButton(onClick = { confirmRegenerate = false; onGenerateList() }) { Text("Substituir lista") } },
        dismissButton = { TextButton(onClick = { confirmRegenerate = false }) { Text("Manter lista atual") } }
    )
    removingItem?.let { item -> br.com.bragasaude.ui.components.BragaAlertDialog(
        onDismissRequest = { removingItem = null }, title = { Text("Remover ${item.foodName}?") },
        text = { Text("As sugestões de receitas serão atualizadas e mostrarão este ingrediente como faltante quando necessário.") },
        confirmButton = { TextButton(onClick = { onRemoveItem(item.remoteId); removingItem = null }) { Text("Remover") } },
        dismissButton = { TextButton(onClick = { removingItem = null }) { Text("Manter item") } }
    ) }
    var reportingItem by remember { mutableStateOf<GroceryListItemEntity?>(null) }
    reportingItem?.let { selected ->
        GroceryPriceContributionSheet(selected, contributionState,
            onDismiss = { reportingItem = null },
            onSubmit = { amount, quantity, unit, state, date -> onContribute(selected, amount, quantity, unit, state, date) })
        return
    }
    val totalCost = groceryList.filter { it.estimatedPriceBrl > 0.0 }.sumOf { it.estimatedPriceBrl }
    val dailyAvg = if (totalCost > 0.0) totalCost / 7.0 else 0.0
    val checkedCount = groceryList.count { it.isCheckedInPantry }

    val grouped = remember(groceryList) { groceryList.groupBy { it.category } }
    BragaFormSheet(
        onDismissRequest = onDismiss,
        title = { Text("Lista de compras") },
        scrollContent = false,
        confirmButton = {
            if (groceryList.isNotEmpty()) {
                Button(onClick = onExportPdf, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                    Icon(Icons.Default.Share, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Compartilhar lista", style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        dismissButton = {
            OutlinedButton(onClick = { if (groceryList.isEmpty()) onGenerateList() else confirmRegenerate = true }, enabled = !isLoading, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                Icon(Icons.Default.Refresh, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(if (isLoading) "Gerando lista..." else if (groceryList.isEmpty()) "Gerar lista aleatória" else "Gerar nova lista aleatória",
                    style = MaterialTheme.typography.bodyMedium)
            }
        },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    Text("Preencha nomes e quantidades corretamente: as receitas e outras sugestões de alimentação dependem desta lista. Marque como comprado apenas o que já está na sua despensa. Ter todos os itens na lista não garante que foram comprados nem que a quantidade basta para a receita.",
                        style = MaterialTheme.typography.bodyMedium)
                }
                item {
                    Text(br.com.bragasaude.domain.GROCERY_PRICE_NOTICE,
                        style = MaterialTheme.typography.bodySmall, color = BragaTextSecondary)
                    val missing = groceryList.count { it.estimatedPriceBrl <= 0.0 }
                    if (missing > 0) Text("$missing itens sem preço informado não entram no total estimado.",
                        style = MaterialTheme.typography.bodySmall, color = BragaTextSecondary)
                }
                item {
                    OutlinedButton(onClick = { editingItem = null; showManual = true }, enabled = !isLoading,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        Text(if (groceryList.isEmpty()) "Montar minha lista" else "Adicionar alimento")
                    }
                    message?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                }
                if (suggestedItems.isNotEmpty()) {
                    item {
                        Card(
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = BragaMintSurface),
                            border = BorderStroke(1.dp, BragaMintBorder)
                        ) {
                            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text("Sugestões do Braga", style = MaterialTheme.typography.titleSmall, color = BragaEmeraldDark)
                                Text(suggestedItems.joinToString(", "), style = MaterialTheme.typography.bodyMedium)
                                Button(
                                    onClick = { onAddSuggested(suggestedItems) },
                                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                                ) {
                                    Text("Adicionar sugestões (${suggestedItems.size})", style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }
                }
                if (groceryList.isEmpty()) {
                    item {
                        Column(Modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Icon(Icons.Default.ShoppingCart, contentDescription = null, tint = BragaEmerald, modifier = Modifier.size(48.dp))
                            Text("Sua lista começa aqui", style = MaterialTheme.typography.titleMedium)
                            Text("Monte sua lista com os alimentos que deseja comprar ou gere uma sugestão aleatória para a semana.", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                } else {
                    item {
                        Card(
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = BragaMintSurface),
                            border = BorderStroke(1.dp, BragaMintBorder)
                        ) {
                            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Estimativa da semana", style = MaterialTheme.typography.bodyMedium)
                                Text(String.format(Locale.getDefault(), "R$ %.2f", totalCost),
                                    style = MaterialTheme.typography.titleLarge, color = BragaTextPrimary)
                                Text(String.format(Locale.getDefault(), "Média de R$ %.2f por dia", dailyAvg),
                                    style = MaterialTheme.typography.bodyMedium)
                                Text("$checkedCount de ${groceryList.size} itens comprados",
                                    style = MaterialTheme.typography.bodyMedium, color = BragaEmeraldDark)
                            }
                        }
                    }
                    grouped.forEach { (category, entries) ->
                        item {
                            Text(category, style = MaterialTheme.typography.titleMedium, color = BragaEmeraldDark)
                        }
                        items(entries, key = { it.remoteId }) { entry ->
                            Column {
                                GroceryItemRow(entry) { checked -> onToggleItem(entry.remoteId, checked) }
                                communityPrices.filter { it.foodName.equals(entry.foodName, ignoreCase = true) }.forEach { price ->
                                    Text(String.format(Locale.getDefault(), "Referência dos participantes: R$ %.2f/%s (%d pessoas)",
                                        price.average, price.unit, price.contributors),
                                        style = MaterialTheme.typography.bodySmall, color = BragaTextSecondary,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
                                }
                                Row {
                                    TextButton(onClick = { editingItem = entry; showManual = true }, enabled = !isLoading) { Text("Editar") }
                                    TextButton(onClick = { removingItem = entry }, enabled = !isLoading) { Text("Remover") }
                                }
                                TextButton(onClick = { onStartContribution(); reportingItem = entry },
                                    modifier = Modifier.heightIn(min = 48.dp)) { Text("Informar quanto paguei") }
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(4.dp)) }
            }
        }
    )
}

@Composable
fun GroceryItemRow(
    item: GroceryListItemEntity,
    onToggle: (Boolean) -> Unit
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
                Text(
                    text = if (item.estimatedPriceBrl > 0.0) {
                        String.format(Locale.getDefault(), "Estimado: R$ %.2f", item.estimatedPriceBrl)
                    } else {
                        "—"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (item.isCheckedInPantry) BragaEmerald else BragaTextPrimary
                )
            }
        }
    }
}
