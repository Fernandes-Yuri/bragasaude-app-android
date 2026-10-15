package br.com.bragasaude.data.remote.model

import br.com.bragasaude.data.local.GroceryRetailCardEntity
import br.com.bragasaude.data.local.GroceryListItemEntity
import org.json.JSONObject
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

data class PublishedMarketComparison(val name: String, val total: Double, val missing: Int, val best: Boolean, val conditions: String)

data class PublishedPackItem(val card: GroceryRetailCardEntity, val quantity: Int, val price: Double, val conditions: String)
data class PublishedGroceryPack(
    val publicationId: String, val editionId: String, val version: Long, val id: String,
    val name: String, val description: String, val coverUrl: String?, val category: String,
    val region: String, val startsOn: String, val endsOn: String, val marketName: String,
    val marketLogo: String, val marketUrl: String, val total: Double,
    val items: List<PublishedPackItem>, val rawJson: String, val comparisons: List<PublishedMarketComparison> = emptyList()
) {
    fun validToday(): Boolean {
        val today = LocalDate.now(ZoneId.of("America/Sao_Paulo"))
        return today >= LocalDate.parse(startsOn) && today <= LocalDate.parse(endsOn)
    }
    fun listItems(userId: String): List<GroceryListItemEntity> = items.map { item ->
        val c = item.card
        GroceryListItemEntity(remoteId = UUID.randomUUID().toString(), userId = userId,
            weekStartDate = br.com.bragasaude.domain.GroceryWeek.start(), foodId = c.id,
            foodName = c.name, category = c.category, suggestedServingWeekGrams = 0,
            purchaseWeightGrams = if (c.packageUnit == "g") (c.packageNetValue * item.quantity).toInt() else 0,
            purchaseUnitText = "${item.quantity} × ${c.packageDisplay}", estimatedPriceBrl = item.price * item.quantity,
            isManual = true, retailCardId = c.id, quantity = item.quantity, packageDisplay = c.packageDisplay,
            kcalPerUnit = c.kcalPerPackage, packageNetValue = c.packageNetValue,
            packageUnit = c.packageUnit, linkedIngredientSlug = c.linkedIngredientSlug, imageUrl = c.imageUrl)
    }
    companion object {
        fun parse(raw: String): PublishedGroceryPack {
            val o = JSONObject(raw)
            val edition = o.getJSONObject("edition")
            val preview = o.getJSONObject("preview")
            val pack = preview.getJSONObject("pack")
            val market = preview.getJSONObject("market")
            val rows = preview.getJSONArray("items")
            val items = (0 until rows.length()).map { i ->
                val row = rows.getJSONObject(i)
                val quote = row.getJSONObject("quote")
                require(quote.getString("market_id") == pack.getString("market_id"))
                val quantity = row.getInt("quantity")
                val price = quote.getDouble("price")
                require(quantity in 1..999 && price.isFinite() && price > 0)
                require(quote.getBoolean("confirmed") && quantity >= quote.getInt("minimum_quantity"))
                val conditions = listOfNotNull(quote.optString("conditions").takeIf { it.isNotBlank() },
                    "Exige clube/cadastro".takeIf { quote.optBoolean("membership_required") },
                    "Mínimo ${quote.optInt("minimum_quantity", 1)} embalagens".takeIf { quote.optInt("minimum_quantity", 1) > 1 }).joinToString(" • ")
                PublishedPackItem(GroceryRetailCardDto.parse(row.getJSONObject("product")), quantity, price, conditions)
            }
            require(items.isNotEmpty())
            val comparisons = preview.optJSONArray("comparison")
            val ranked = if (comparisons == null) emptyList() else (0 until comparisons.length()).map { i ->
                val row = comparisons.getJSONObject(i)
                val offers = row.optJSONArray("conditions")
                val notes = if (offers == null) "" else (0 until offers.length()).joinToString(" • ") { j ->
                    val offer = offers.getJSONObject(j)
                    listOfNotNull(offer.optString("conditions").takeIf { it.isNotBlank() },
                        "Exige clube/cadastro".takeIf { offer.optBoolean("membership_required") },
                        "Mínimo ${offer.optInt("minimum_quantity", 1)} embalagens".takeIf { offer.optInt("minimum_quantity", 1) > 1 }).joinToString(" • ")
                }
                PublishedMarketComparison(row.getJSONObject("market").getString("name"), row.getDouble("total_brl"),
                    row.getInt("missing_items_count"), row.optBoolean("is_best_deal"), notes)
            }
            return PublishedGroceryPack(o.getString("publication_id"), edition.getString("id"), o.getLong("version"),
                pack.getString("id"), pack.getString("name"), pack.optString("description"),
                if (pack.isNull("cover_url")) null else pack.getString("cover_url"),
                if (pack.optString("pack_type") == "category") pack.optString("category") else "Cesta completa da seleção",
                edition.getString("region"), edition.getString("starts_on"), edition.getString("ends_on"),
                market.getString("name"), market.getString("logo_url"), market.getString("website_url"),
                preview.getDouble("total_brl"), items, raw, ranked)
        }
        fun fromFeed(feed: JSONObject): List<PublishedGroceryPack> {
            val publications = feed.getJSONArray("publications")
            return (0 until publications.length()).flatMap { i ->
                val publication = publications.getJSONObject(i)
                val packs = publication.getJSONArray("packs")
                (0 until packs.length()).map { j -> parse(JSONObject()
                    .put("publication_id", publication.getString("id")).put("version", publication.getLong("version"))
                    .put("edition", publication.getJSONObject("edition")).put("preview", packs.getJSONObject(j)).toString()) }
            }
        }
    }
}
