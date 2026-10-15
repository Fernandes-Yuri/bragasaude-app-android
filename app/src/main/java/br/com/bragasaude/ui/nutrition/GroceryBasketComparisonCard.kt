package br.com.bragasaude.ui.nutrition

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import android.content.Intent
import android.net.Uri
import coil3.compose.AsyncImage
import br.com.bragasaude.data.remote.model.BasketComparisonResultDto
import java.text.NumberFormat
import java.util.Locale

@Composable
fun GroceryMarketLink(name: String, logo: String, url: String) {
    val context = LocalContext.current
    val uri = Uri.parse(url)
    val valid = uri.scheme in listOf("http", "https") && !uri.host.isNullOrBlank()
    Box(Modifier.size(48.dp).semantics { contentDescription = "Ir à loja $name" }.clickable(enabled = valid) {
        try { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
        catch (_: android.content.ActivityNotFoundException) {
            android.widget.Toast.makeText(context, "Nenhum navegador disponível", android.widget.Toast.LENGTH_SHORT).show()
        }
    }) {
        AsyncImage(model = logo, contentDescription = name, modifier = Modifier.fillMaxSize())
    }
}

@Composable
fun GroceryBasketComparisonCard(result: BasketComparisonResultDto?, message: String?, legacyCount: Int) {
    if (result == null && message == null) return
    val money = NumberFormat.getCurrencyInstance(Locale("pt", "BR"))
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Comparativo de supermercados", style = MaterialTheme.typography.titleMedium)
            if (legacyCount > 0) Text("Comparação parcial: $legacyCount item(ns) fora do catálogo de embalagens.", style = MaterialTheme.typography.bodySmall)
            if (message != null) Text(message)
            val rows = result?.rankings.orEmpty()
            if (result != null && rows.none { it.missingItemsCount == 0 }) Text("Nenhum mercado tem cotação de todos os produtos da cesta.")
            rows.forEach { market ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GroceryMarketLink(market.name, market.logoUrl, market.websiteUrl)
                    Column(Modifier.weight(1f)) {
                        Text(market.name + if (market.isBestDeal) " • Melhor opção" else "")
                        Text(money.format(market.totalBrl) + if (market.missingItemsCount > 0) " • subtotal, ${market.missingItemsCount} produto(s) sem cotação" else " • cesta completa")
                        if (market.isBestDeal && market.savings > 0) Text("Economia de ${money.format(market.savings)} frente à cesta completa mais cara")
                        Text("Toque no logo para ir à loja", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            Text("Cotações de referência; preços, disponibilidade e frete podem variar na loja.", style = MaterialTheme.typography.bodySmall)
        }
    }
}
