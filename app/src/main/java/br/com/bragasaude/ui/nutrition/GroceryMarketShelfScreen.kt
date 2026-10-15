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
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import coil3.compose.AsyncImage
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
        Text("Meta da semana: ${number.format(goal.accumulated)} / ${number.format(goal.target)} kcal (${(goal.fraction * 100).toInt()}%)",
            style = MaterialTheme.typography.labelLarge)
        Box(Modifier.fillMaxWidth().height(12.dp).border(1.dp, BragaEmerald, RoundedCornerShape(6.dp))) {
            if (goal.fraction > 0) Box(Modifier.fillMaxWidth(goal.fraction.toFloat().coerceIn(0f, 1f)).fillMaxHeight()
                .background(Brush.horizontalGradient(listOf(BragaEmerald, BragaEmeraldDark)), RoundedCornerShape(6.dp)))
        }
        Text(when {
            goal.withinTarget -> "Na faixa da meta semanal (90% a 110%)"
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
    allowSelection: Boolean = true
) {
    var category by remember { mutableStateOf("Todos") }
    var query by remember { mutableStateOf("") }
    val haptic = LocalHapticFeedback.current
    val money = remember { NumberFormat.getCurrencyInstance(Locale("pt", "BR")) }
    val shown = cards.filter { (category == "Todos" || it.category == category) && it.name.contains(query.trim(), ignoreCase = true) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            (listOf("Todos") + cards.map { it.category }.distinct()).forEach { name ->
                FilterChip(selected = category == name, onClick = { category = name }, label = { Text(name) })
            }
        }
        OutlinedTextField(value = query, onValueChange = { query = it }, label = { Text("Buscar produtos") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (message != null) Text(message, style = MaterialTheme.typography.bodySmall)
        TextButton(onClick = onRetry, enabled = !loading) { Text("Atualizar catálogo e preços") }
        if (shown.isEmpty()) Text(if (cards.isEmpty()) "Nenhum produto disponível no catálogo." else "Nenhum produto para esta busca.")
        LazyVerticalGrid(columns = GridCells.Adaptive(150.dp), modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(shown, key = { it.id }) { card ->
                val row = rows.firstOrNull { it.retailCardId == card.id }
                val count = row?.quantity ?: 0
                val locked = row?.isCheckedInPantry == true
                Card(shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                    Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        AsyncImage(model = card.imageUrl, contentDescription = card.name,
                            modifier = Modifier.fillMaxWidth().height(108.dp), contentScale = ContentScale.Fit)
                        Surface(shape = RoundedCornerShape(6.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                            Text(card.packageDisplay, Modifier.padding(4.dp), style = MaterialTheme.typography.labelSmall)
                        }
                        Text(card.name, style = MaterialTheme.typography.titleSmall, minLines = 2)
                        Text(money.format(card.priceAvg), fontWeight = FontWeight.Bold)
                        Text("+ ${card.kcalPerPackage.toInt()} kcal / embalagem", style = MaterialTheme.typography.bodySmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            val markets = remember(card.marketsJson) { JSONArray(card.marketsJson) }
                            for (i in 0 until markets.length()) {
                                val market = markets.getJSONObject(i)
                                GroceryMarketLink(market.optString("market_name"), market.optString("market_logo_url"), market.optString("website_url"))
                            }
                        }
                        if (allowSelection) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onDecrement(card.id) },
                                enabled = count > 0 && !locked, contentPadding = PaddingValues(0.dp), modifier = Modifier.semantics { contentDescription = "Remover uma embalagem de ${card.name}" }.sizeIn(minWidth = 48.dp, minHeight = 48.dp)) { Text("−", modifier = Modifier, style = MaterialTheme.typography.titleLarge) }
                            Text("$count")
                            TextButton(onClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onAdd(card) },
                                enabled = count < 999 && !locked, contentPadding = PaddingValues(0.dp), modifier = Modifier.semantics { contentDescription = "Adicionar uma embalagem de ${card.name}" }.sizeIn(minWidth = 48.dp, minHeight = 48.dp)) { Text("+", style = MaterialTheme.typography.titleLarge) }
                        }
                        if (locked && allowSelection) Text("Comprado. Desmarque na lista para alterar.", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}
