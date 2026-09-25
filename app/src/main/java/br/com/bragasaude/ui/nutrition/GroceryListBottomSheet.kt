package br.com.bragasaude.ui.nutrition

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.bragasaude.data.local.GroceryListItemEntity
import java.util.Locale
import br.com.bragasaude.ui.theme.BragaCardBorder
import br.com.bragasaude.ui.theme.BragaCardSurface
import br.com.bragasaude.ui.theme.BragaEmerald
import br.com.bragasaude.ui.theme.BragaEmeraldDark
import br.com.bragasaude.ui.theme.BragaMint
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
    onAddSuggested: (List<String>) -> Unit = {}
) {
    val context = LocalContext.current
    val totalCost = groceryList.sumOf { it.estimatedPriceBrl }
    val dailyAvg = if (totalCost > 0) totalCost / 7.0 else 0.0
    val checkedCount = groceryList.count { it.isCheckedInPantry }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = BragaCardSurface,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Lista Semanal de Compras",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = BragaTextPrimary
                        )
                    }
                    Text(
                        "Itens checados vão para a despensa",
                        style = MaterialTheme.typography.bodySmall,
                        color = BragaTextSecondary
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Fechar")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Agente B1: sugestões vindas do chat (toque para adicionar)
            if (suggestedItems.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = BragaMintSurface),
                    border = BorderStroke(1.dp, BragaMintBorder)
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
                        Text(
                            "Sugestão do Braga: ${suggestedItems.joinToString(", ")}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = BragaEmeraldDark
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = { onAddSuggested(suggestedItems) },
                            modifier = Modifier.heightIn(min = 48.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = BragaEmerald)
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Adicionar (${suggestedItems.size})", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Card Resumo de Preço e Despensa
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = BragaMintSurface),
                border = BorderStroke(1.dp, BragaMintBorder)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            "Estimativa da Semana",
                            style = MaterialTheme.typography.labelMedium,
                            color = BragaEmeraldDark,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            String.format(Locale.getDefault(), "R$ %.2f", totalCost),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = BragaTextPrimary
                        )
                        Text(
                            String.format(Locale.getDefault(), "Média: ~R$ %.2f / dia", dailyAvg),
                            style = MaterialTheme.typography.bodySmall,
                            color = BragaTextSecondary
                        )
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (checkedCount > 0) BragaEmerald else BragaMint
                        ) {
                            Text(
                                "$checkedCount de ${groceryList.size} comprados",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (checkedCount > 0) Color.White else BragaTextSecondary
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Na Despensa",
                            style = MaterialTheme.typography.labelSmall,
                            color = BragaEmeraldDark
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Botões de Ação Rápida
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onExportPdf,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = BragaEmerald)
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Gerar PDF / WhatsApp", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = onGenerateList,
                    modifier = Modifier.heightIn(min = 48.dp),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, BragaMintBorder)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Recriar", fontSize = 14.sp)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (groceryList.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.ShoppingCart,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.outline
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Nenhuma lista gerada ainda",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.outline
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(onClick = onGenerateList) {
                            Text("Gerar Lista Semanal Inteligente")
                        }
                    }
                }
            } else {
                val grouped = remember(groceryList) { groceryList.groupBy { it.category } }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    grouped.forEach { (corridor, itemsInCorridor) ->
                        item {
                            Text(
                                text = corridor,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = BragaEmeraldDark,
                                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                            )
                        }

                        items(itemsInCorridor, key = { it.remoteId }) { item ->
                            GroceryItemRow(
                                item = item,
                                onToggle = { isChecked -> onToggleItem(item.remoteId, isChecked) }
                            )
                        }
                    }

                    item {
                        Spacer(modifier = Modifier.height(30.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun GroceryItemRow(
    item: GroceryListItemEntity,
    onToggle: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle(!item.isCheckedInPantry) },
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
                onCheckedChange = onToggle,
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
                    style = MaterialTheme.typography.bodySmall,
                    color = if (item.isCheckedInPantry) BragaTextSecondary else BragaTextSecondary
                )
            }

            Text(
                text = String.format(Locale.getDefault(), "R$ %.2f", item.estimatedPriceBrl),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = if (item.isCheckedInPantry) BragaEmerald else BragaTextPrimary
            )
        }
    }
}
