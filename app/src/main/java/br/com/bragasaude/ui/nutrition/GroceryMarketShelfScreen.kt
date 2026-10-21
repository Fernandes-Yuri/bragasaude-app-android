package br.com.bragasaude.ui.nutrition

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Info
import androidx.compose.ui.text.style.TextOverflow
import br.com.bragasaude.ui.components.BragaFormSheet
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import br.com.bragasaude.ui.theme.BragaTextPrimary
import br.com.bragasaude.ui.theme.BragaTextSecondary
import br.com.bragasaude.ui.theme.BragaCardBorder
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import br.com.bragasaude.data.remote.model.GroceryRetailCardDto
import br.com.bragasaude.data.local.GroceryRetailCardEntity
import br.com.bragasaude.data.local.GroceryListItemEntity
import br.com.bragasaude.domain.GroceryWeeklyGoal
import br.com.bragasaude.ui.theme.BragaEmerald
import br.com.bragasaude.ui.theme.BragaEmeraldDark
import java.text.NumberFormat
import java.util.Locale
import org.json.JSONArray

@Composable
fun GroceryWeeklyGoalBar(goal: GroceryWeeklyGoal) {
    val number = remember { NumberFormat.getIntegerInstance(Locale("pt", "BR")) }
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Meta de ${goal.planningDays} dias: ${number.format(goal.accumulated)} / ${number.format(goal.target)} kcal (${(goal.fraction * 100).toInt()}%)",
            style = MaterialTheme.typography.labelLarge)
        Box(Modifier.fillMaxWidth().height(12.dp).border(1.dp, BragaEmerald, RoundedCornerShape(6.dp))) {
            if (goal.fraction > 0) Box(Modifier.fillMaxWidth(goal.fraction.toFloat().coerceIn(0f, 1f)).fillMaxHeight()
                .background(Brush.horizontalGradient(listOf(BragaEmerald, BragaEmeraldDark)), RoundedCornerShape(6.dp)))
        }
        Text(when {
            goal.withinTarget -> "Na faixa da meta do período (90% a 110%)"
            goal.accumulated > goal.target -> "${number.format(goal.accumulated - goal.target)} kcal acima da meta"
            else -> "Faltam ${number.format(goal.remaining)} kcal"
        }, style = MaterialTheme.typography.bodySmall)
        if (goal.unknownItems > 0) Text("Cobertura parcial: ${goal.unknownItems} item(ns) sem calorias por embalagem.", style = MaterialTheme.typography.bodySmall)
        Text("Energia das embalagens compradas; distribua o consumo conforme seu plano alimentar.", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun GroceryMarketShelfScreen(
    cards: List<GroceryRetailCardEntity>, rows: List<GroceryListItemEntity>,
    loading: Boolean, message: String?, onRetry: () -> Unit,
    onAdd: (GroceryRetailCardEntity) -> Unit, onDecrement: (String) -> Unit,
    modifier: Modifier = Modifier,
    allowSelection: Boolean = true,
    header: @Composable () -> Unit = {}
) {
    var detailCard by remember { mutableStateOf<GroceryRetailCardEntity?>(null) }
    var category by remember { mutableStateOf("Todos") }
    var query by remember { mutableStateOf("") }
    val haptic = LocalHapticFeedback.current
    val money = remember { NumberFormat.getCurrencyInstance(Locale("pt", "BR")) }
    val shown = cards.filter { (category == "Todos" || it.category == category) && it.name.contains(query.trim(), ignoreCase = true) }
    LazyVerticalGrid(columns = GridCells.Adaptive(150.dp), modifier = modifier.testTag("groceryCatalog"),
        contentPadding = PaddingValues(vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item(key = "planningHeader", span = { GridItemSpan(maxLineSpan) }) { header() }
        item(key = "catalogFilters", span = { GridItemSpan(maxLineSpan) }) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                OutlinedTextField(value = query, onValueChange = { query = it },
                    placeholder = { Text("Buscar produtos", maxLines = 1) }, singleLine = true,
                    trailingIcon = {
                        IconButton(onClick = onRetry, enabled = !loading) {
                            Icon(Icons.Default.Refresh, contentDescription = "Atualizar catálogo e preços")
                        }
                    }, modifier = Modifier.fillMaxWidth())
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    (listOf("Todos") + cards.map { it.category }.distinct()).forEach { name ->
                        FilterChip(selected = category == name, onClick = { category = name }, label = { Text(name) })
                    }
                }
                if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (message != null) Text(message, style = MaterialTheme.typography.bodySmall)
                if (shown.isEmpty()) Text(if (cards.isEmpty()) "Nenhum produto disponível no catálogo." else "Nenhum produto para esta busca.")
            }
        }
        items(shown, key = { it.id }) { card ->
            val row = rows.firstOrNull { it.retailCardId == card.id }
            val count = row?.quantity ?: 0
            val locked = row?.isCheckedInPantry == true
            Card(
                onClick = { detailCard = card },
                modifier = Modifier.fillMaxWidth().testTag("retailCard-${card.id}"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White, contentColor = BragaTextPrimary),
                border = BorderStroke(1.dp, BragaCardBorder)
            ) {
                Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box {
                        GroceryProductImage(card.imageUrl, card.name, Modifier.fillMaxWidth().height(88.dp))
                        Icon(Icons.Default.Info, contentDescription = "Ver detalhes de ${card.name}",
                            tint = BragaTextSecondary,
                            modifier = Modifier.align(Alignment.TopEnd).size(18.dp))
                    }
                    Text(card.name, style = MaterialTheme.typography.titleSmall,
                        minLines = 2, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(card.packageDisplay, style = MaterialTheme.typography.bodySmall,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        color = BragaTextSecondary)
                    Text(money.format(card.priceAvg), style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold, maxLines = 1)
                    Text("${card.kcalPerPackage.toInt()} kcal/emb.", style = MaterialTheme.typography.bodySmall,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        color = BragaTextSecondary)
                    if (allowSelection) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onDecrement(card.id) },
                            enabled = count > 0 && !locked,
                            colors = ButtonDefaults.textButtonColors(contentColor = BragaEmeraldDark, disabledContentColor = BragaTextSecondary.copy(alpha = 0.5f)), contentPadding = PaddingValues(0.dp), modifier = Modifier.semantics { contentDescription = "Remover uma embalagem de ${card.name}" }.sizeIn(minWidth = 48.dp, minHeight = 48.dp)) { Text("−", style = MaterialTheme.typography.titleLarge) }
                        Text("$count", style = MaterialTheme.typography.labelLarge)
                        TextButton(onClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onAdd(card) },
                            enabled = count < 999 && !locked,
                            colors = ButtonDefaults.textButtonColors(contentColor = BragaEmeraldDark, disabledContentColor = BragaTextSecondary.copy(alpha = 0.5f)), contentPadding = PaddingValues(0.dp), modifier = Modifier.semantics { contentDescription = "Adicionar uma embalagem de ${card.name}" }.sizeIn(minWidth = 48.dp, minHeight = 48.dp)) { Text("+", style = MaterialTheme.typography.titleLarge) }
                    }
                }
            }
        }
    }
    detailCard?.let { card ->
        BragaFormSheet(
            onDismissRequest = { detailCard = null },
            title = { Text(card.name) },
            confirmButton = { TextButton(onClick = { detailCard = null }) { Text("Fechar") } },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Embalagem: ${card.packageDisplay}")
                    Text("Preço estimado: ${money.format(card.priceAvg)}")
                    Text("${card.kcalPerPackage.toInt()} kcal por embalagem")
                    val facts = remember(card.nutritionFactsJson) { GroceryRetailCardDto.nutritionFacts(card) }
                    val number = remember { NumberFormat.getNumberInstance(Locale("pt", "BR")).apply { maximumFractionDigits = 2 } }
                    Text("Informações nutricionais", style = MaterialTheme.typography.titleSmall)
                    if (facts.isEmpty()) Text("Nutrientes não informados.")
                    facts.forEach { fact -> Text("${fact.label}: ${number.format(fact.value)} ${fact.unit}") }
                    val sources = remember(card.marketsJson) { runCatching { JSONArray(card.marketsJson) }.getOrElse { JSONArray() } }
                    for (index in 0 until sources.length()) {
                        sources.optJSONObject(index)?.let { source ->
                            Text("Fonte: ${source.optString("market_name")} · ${source.optString("last_updated").take(10)}",
                                style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    if (rows.any { it.retailCardId == card.id && it.isCheckedInPantry }) {
                        Text("Comprado. Desmarque na lista para alterar a quantidade.")
                    }
                    Text("Valores de referência por embalagem; podem variar por loja, região e data.",
                        style = MaterialTheme.typography.bodySmall)
                }
            }
        )
    }

}
