package br.com.bragasaude.data.remote.model

import br.com.bragasaude.data.local.GroceryRetailCardEntity
import org.json.JSONObject
import org.json.JSONArray

data class BasketItemDto(val cardId: String, val quantity: Int)
data class GroceryMarketTotal(
    val marketId: String, val name: String, val logoUrl: String, val websiteUrl: String,
    val totalBrl: Double, val missingItemsCount: Int, val isBestDeal: Boolean, val savings: Double
) {
    companion object {
        fun parse(o: JSONObject) = GroceryMarketTotal(o.getString("market_id"), o.getString("market_name"),
            o.getString("market_logo_url"), o.getString("website_url"), o.getDouble("total_brl"),
            o.getInt("missing_items_count"), o.getBoolean("is_best_deal"), o.getDouble("savings_vs_highest_brl"))
    }
}
data class BasketComparisonResultDto(val rankings: List<GroceryMarketTotal>) {
    companion object {
        fun parse(o: JSONObject): BasketComparisonResultDto {
            val rows = o.getJSONArray("market_rankings")
            return BasketComparisonResultDto((0 until rows.length()).map { GroceryMarketTotal.parse(rows.getJSONObject(it)) })
        }
    }
}

object GroceryRetailCardDto {
    fun parse(o: JSONObject) = GroceryRetailCardEntity(
        id = o.getString("id"), name = o.getString("name"), category = o.getString("category"),
        imageUrl = o.getString("image_url"), packageDisplay = o.getString("package_display"),
        packageNetValue = o.getDouble("package_net_value"), packageUnit = o.getString("package_unit"),
        kcalPerPackage = o.getDouble("kcal_per_package"), carbsG = o.getDouble("carbs_g"),
        proteinG = o.getDouble("protein_g"), fatG = o.getDouble("fat_g"), priceAvg = o.getDouble("price_avg"),
        linkedIngredientSlug = if (o.isNull("linked_ingredient_slug")) null else o.getString("linked_ingredient_slug"),
        marketsJson = o.optJSONArray("markets")?.toString() ?: "[]",
        clinicalTagsJson = o.optJSONArray("clinical_tags")?.toString() ?: "[]"
    )
}
