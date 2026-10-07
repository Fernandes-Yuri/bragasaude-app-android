package br.com.bragasaude.ui.nutrition

import br.com.bragasaude.ui.components.BragaBottomSheet

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
    onGenerateList: () -> Unit,
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
    groceryMessage: String? = null
) {
    var reportingItem by remember { mutableStateOf<GroceryListItemEntity?>(null) }
    var manualEditingItem by remember { mutableStateOf<GroceryListItemEntity?>(null) }
    var isCreatingManual by remember { mutableStateOf(false) }

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

    BragaBottomSheet(
        onDismissRequest = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
        ) {
            // 1. Cabeçalho Principal (Título + Contador de itens + Botão fechar)
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
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Fechar lista de compras",
                        tint = BragaTextSecondary
                    )
                }
            }

            // 2. Barra de Ações Rápidas em Destaque (Sempre Visível no Topo)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = { isCreatingManual = true },
                    modifier = Modifier
                        .weight(1.3f)
                        .heightIn(min = 48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = BragaEmerald),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "Adicionar alimento",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                OutlinedButton(
                    onClick = onGenerateList,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, BragaMintBorder)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(
                        if (groceryList.isEmpty()) "Gerar auto" else "Regenerar",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                if (groceryList.isNotEmpty()) {
                    IconButton(
                        onClick = onExportPdf,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            Icons.Default.Share,
                            contentDescription = "Compartilhar lista em PDF",
                            tint = BragaEmeraldDark
                        )
                    }
                }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (groceryMessage != null) {
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
                                    onClick = onGenerateList,
                                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                                    border = BorderStroke(1.dp, BragaMintBorder)
                                ) {
                                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("Gerar lista semanal automática")
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
