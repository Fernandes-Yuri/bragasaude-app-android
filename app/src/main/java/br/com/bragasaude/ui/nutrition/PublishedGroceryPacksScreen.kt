package br.com.bragasaude.ui.nutrition

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import br.com.bragasaude.data.remote.model.PublishedGroceryPack
import coil3.compose.AsyncImage
import java.text.NumberFormat
import java.util.Locale

@Composable
fun PublishedGroceryPacksScreen(
    packs: List<PublishedGroceryPack>, loading: Boolean, message: String?,
    onRefresh: () -> Unit, onSelect: (PublishedGroceryPack) -> Unit, modifier: Modifier = Modifier
) {
    var region by remember { mutableStateOf("") }
    val money = remember { NumberFormat.getCurrencyInstance(Locale("pt", "BR")) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Packs selecionados para a semana", style = MaterialTheme.typography.titleMedium)
        Text("Cada seleção pertence a um único supermercado. Confira a região e as condições da pesquisa.", style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(value = region, onValueChange = { region = it }, label = { Text("Buscar região ou cidade") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (message != null) Text(message, style = MaterialTheme.typography.bodySmall)
        TextButton(onClick = onRefresh, enabled = !loading) { Text("Atualizar packs publicados") }
        val visible = packs.filter { it.validToday() && it.region.contains(region.trim(), ignoreCase = true) }
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (visible.isEmpty()) item { Text("Nenhum pack publicado disponível para esta região no momento.") }
            items(visible, key = { it.publicationId + ":" + it.id }) { pack ->
                var expanded by remember(pack.rawJson) { mutableStateOf(false) }
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (!pack.coverUrl.isNullOrBlank()) AsyncImage(pack.coverUrl, pack.name, Modifier.fillMaxWidth().height(112.dp))
                        Text(pack.name, fontWeight = FontWeight.Bold)
                        Text("${pack.marketName} • ${pack.category}", style = MaterialTheme.typography.bodyMedium)
                        Text("${pack.region} • ${pack.startsOn} a ${pack.endsOn}", style = MaterialTheme.typography.bodySmall)
                        if (pack.description.isNotBlank()) Text(pack.description, style = MaterialTheme.typography.bodySmall)
                        Text(money.format(pack.total), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text("${pack.items.size} produtos • ${pack.items.sumOf { it.quantity }} embalagens", style = MaterialTheme.typography.bodySmall)
                        GroceryMarketLink(pack.marketName, pack.marketLogo, pack.marketUrl)
                        TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "Ocultar composição" else "Ver produtos e condições") }
                        if (expanded) pack.items.forEach { item ->
                            Text("${item.quantity} × ${item.card.name} • ${item.card.packageDisplay} • ${money.format(item.price * item.quantity)}", style = MaterialTheme.typography.bodySmall)
                            if (item.conditions.isNotBlank()) Text(item.conditions, style = MaterialTheme.typography.bodySmall)
                        }
                        if (expanded && pack.comparisons.isNotEmpty()) {
                            Text("A mesma composição em outros mercados", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
                            pack.comparisons.forEach { comparison ->
                                Text("${comparison.name}: ${money.format(comparison.total)}" +
                                    if (comparison.missing > 0) " • subtotal, ${comparison.missing} produtos sem cotação" else
                                        if (comparison.best) " • menor total pesquisado" else " • cesta completa", style = MaterialTheme.typography.bodySmall)
                                if (comparison.conditions.isNotBlank()) Text(comparison.conditions, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        Text("Preços pesquisados para a região informada. Frete não incluído; confira disponibilidade e condições na loja.", style = MaterialTheme.typography.bodySmall)
                        Button(onClick = { onSelect(pack) }, modifier = Modifier.fillMaxWidth()) { Text("Usar este pack como minha lista") }
                    }
                }
            }
        }
    }
}

@Composable
fun AdoptedGroceryPackCard(pack: PublishedGroceryPack, total: Double, updated: Boolean) {
    val money = NumberFormat.getCurrencyInstance(Locale("pt", "BR"))
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Sua lista em ${pack.marketName}", fontWeight = FontWeight.Bold)
            Text("${pack.name} • ${pack.region}", style = MaterialTheme.typography.bodySmall)
            Text("Total estimado da lista: ${money.format(total)}")
            GroceryMarketLink(pack.marketName, pack.marketLogo, pack.marketUrl)
            if (updated) Text("A publicação mudou, venceu ou foi retirada. Sua lista e os itens comprados foram preservados. Confira as opções atuais na vitrine.", style = MaterialTheme.typography.bodySmall)
        }
    }
}
