package br.com.bragasaude.data.remote.model

import br.com.bragasaude.data.local.GroceryListItemEntity
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI

data class GroceryBasketItem(val cardId: String, val quantity: Int) {
    init { require(cardId.isNotBlank() && quantity in 1..999) }
}

data class GroceryBasketProduct(val cardId: String, val name: String, val quantity: Int, val available: Boolean)
data class GroceryBasketLink(val name: String, val quantity: Int, val url: String)
data class GroceryBasketMarket(
    val marketId: String, val marketName: String, val totalBrl: Double,
    val complete: Boolean, val isBestDeal: Boolean, val savingsBrl: Double?,
    val items: List<GroceryBasketProduct>, val superUrl: String?, val assistedLinks: List<GroceryBasketLink>
)
data class GroceryBasketComparison(val markets: List<GroceryBasketMarket>)
data class GroceryBasketUiState(
    val loading: Boolean = false, val comparison: GroceryBasketComparison? = null, val error: String? = null
)

/** Pending catalog packages only. Legacy loose-weight entries cannot be guessed as SKUs. */
fun groceryBasketItems(rows: List<GroceryListItemEntity>): List<GroceryBasketItem> = rows
    .filter { !it.isCheckedInPantry && !it.retailCardId.isNullOrBlank() && it.quantity > 0 }
    .groupBy { requireNotNull(it.retailCardId) }
    .map { (id, entries) ->
        val total = entries.sumOf { it.quantity.toLong() }
        require(total in 1L..999L) { "Ajuste a quantidade de embalagens para comparar (máximo 999 por produto)." }
        GroceryBasketItem(id, total.toInt())
    }

fun groceryBasketRequest(items: List<GroceryBasketItem>): JSONObject {
    require(items.size in 1..100) { "Selecione de 1 a 100 produtos do catálogo para comparar." }
    require(items.map { it.cardId }.distinct().size == items.size)
    return JSONObject().put("items", JSONArray(items.map {
        JSONObject().put("card_id", it.cardId).put("quantity", it.quantity)
    }))
}

fun groceryBasketExternalUrl(value: String?): String? = value?.takeIf {
    runCatching { URI(it).let { uri -> uri.scheme in listOf("https", "http") && !uri.host.isNullOrBlank() && uri.userInfo == null } }.getOrDefault(false)
}

object GroceryBasketComparisonDto {
    fun parse(json: JSONObject): GroceryBasketComparison {
        val rankings = json.getJSONArray("market_rankings")
        return GroceryBasketComparison((0 until rankings.length()).map { index ->
            val market = rankings.getJSONObject(index)
            val products = market.getJSONArray("items")
            val links = market.getJSONArray("assisted_links")
            GroceryBasketMarket(
                marketId = market.getString("market_id"), marketName = market.getString("market_name"),
                totalBrl = market.getDouble("total_brl").also { require(it.isFinite() && it >= 0) },
                complete = market.getBoolean("complete"), isBestDeal = market.getBoolean("is_best_deal"),
                savingsBrl = if (market.isNull("savings_vs_highest_brl")) null else market.getDouble("savings_vs_highest_brl"),
                superUrl = groceryBasketExternalUrl(if (market.isNull("super_url")) null else market.getString("super_url")),
                items = (0 until products.length()).map { i -> products.getJSONObject(i).let {
                    GroceryBasketProduct(it.getString("card_id"), it.getString("name"), it.getInt("quantity"), it.getBoolean("available"))
                } },
                assistedLinks = (0 until links.length()).mapNotNull { i -> links.getJSONObject(i).let {
                    val url = groceryBasketExternalUrl(it.getString("url")) ?: return@let null
                    GroceryBasketLink(it.getString("name"), it.getInt("quantity"), url)
                } }
            )
        })
    }
}
