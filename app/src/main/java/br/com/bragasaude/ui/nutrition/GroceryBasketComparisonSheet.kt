package br.com.bragasaude.ui.nutrition

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import br.com.bragasaude.data.remote.model.GroceryBasketUiState
import br.com.bragasaude.data.remote.model.groceryBasketExternalUrl
import br.com.bragasaude.ui.components.BragaBottomSheet
import java.text.NumberFormat
import java.util.Locale

@Composable
fun GroceryBasketComparisonSheet(state: GroceryBasketUiState, onDismiss: () -> Unit, onRetry: () -> Unit) {
    val context = LocalContext.current
    val money = remember { NumberFormat.getCurrencyInstance(Locale("pt", "BR")) }
    var linkError by remember { mutableStateOf(false) }
    fun open(url: String) {
        val safe = groceryBasketExternalUrl(url) ?: return
        linkError = runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(safe)).apply {
                addCategory(Intent.CATEGORY_BROWSABLE)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        }.isFailure
    }
    BragaBottomSheet(onDismissRequest = onDismiss) {
        LazyColumn(Modifier.fillMaxWidth().testTag("groceryBasketRanking"), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Text("Comparação de mercados", style = MaterialTheme.typography.titleLarge)
                Text("Embalagens pendentes da sua lista. Valores pesquisados; frete e descontos do checkout não incluídos.", style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = onDismiss) { Text("Fechar") }
                if (linkError) Text("Não foi possível abrir o link neste aparelho.")
            }
            when {
                state.loading -> item { LinearProgressIndicator(Modifier.fillMaxWidth()); Text("Comparando mercados...") }
                state.error != null -> item {
                    Text(state.error)
                    Button(onClick = onRetry) { Text("Tentar novamente") }
                }
                state.comparison != null -> {
                    if (state.comparison.markets.isEmpty()) item { Text("Nenhum supermercado ativo disponível para comparar.") }
                    items(state.comparison.markets, key = { it.marketId }) { market ->
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(market.marketName, style = MaterialTheme.typography.titleMedium)
                                if (market.isBestDeal) Text("Menor preço com cesta completa", style = MaterialTheme.typography.labelLarge)
                                Text("${if (market.complete) "Total" else "Subtotal dos itens disponíveis"}: ${money.format(market.totalBrl)}")
                                market.savingsBrl?.takeIf { it > 0 }?.let { Text("Economia estimada: ${money.format(it)} em relação à cesta completa mais cara.") }
                                val missing = market.items.filter { !it.available }
                                if (missing.isNotEmpty()) Text("Faltam: ${missing.joinToString { "${it.name} (${it.quantity})" }}")
                                if (market.superUrl != null) {
                                    Button(onClick = { open(market.superUrl) }, modifier = Modifier.fillMaxWidth()) {
                                        Text("Ir para o Carrinho no ${market.marketName}")
                                    }
                                    if (!market.complete) Text("O carrinho contém apenas os itens disponíveis.", style = MaterialTheme.typography.bodySmall)
                                } else {
                                    if (market.assistedLinks.isNotEmpty()) Text("Adicione pelos links dos produtos:")
                                    market.assistedLinks.forEach { link ->
                                        OutlinedButton(onClick = { open(link.url) }, modifier = Modifier.fillMaxWidth()) {
                                            Text("${link.name} · ${link.quantity} embalagem(ns)")
                                        }
                                    }
                                    if (market.assistedLinks.isEmpty()) Text("Links de compra ainda não cadastrados.")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
