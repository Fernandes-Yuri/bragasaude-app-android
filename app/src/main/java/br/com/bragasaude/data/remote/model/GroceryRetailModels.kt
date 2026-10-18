package br.com.bragasaude.data.remote.model

import br.com.bragasaude.data.local.GroceryRetailCardEntity
import org.json.JSONObject
import org.json.JSONArray

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
