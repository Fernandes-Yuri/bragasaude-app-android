package br.com.bragasaude.data.remote.model

import br.com.bragasaude.data.local.GroceryRetailCardEntity
import org.json.JSONObject
import org.json.JSONArray

data class GroceryNutritionFact(val key: String, val label: String, val unit: String, val value: Double)

object GroceryRetailCardDto {
    fun parse(o: JSONObject): GroceryRetailCardEntity {
        val facts = normalizedFacts(o)
        return GroceryRetailCardEntity(
            id = o.getString("id"), name = o.getString("name"), category = o.getString("category"),
            imageUrl = o.optString("image_url").takeUnless { o.isNull("image_url") }.orEmpty(),
            packageDisplay = o.getString("package_display"),
            packageNetValue = o.getDouble("package_net_value"), packageUnit = o.getString("package_unit"),
            kcalPerPackage = o.getDouble("kcal_per_package"), carbsG = o.getDouble("carbs_g"),
            proteinG = o.getDouble("protein_g"), fatG = o.getDouble("fat_g"), priceAvg = o.getDouble("price_avg"),
            linkedIngredientSlug = if (o.isNull("linked_ingredient_slug")) null else o.getString("linked_ingredient_slug"),
            marketsJson = o.optJSONArray("markets")?.toString() ?: "[]",
            clinicalTagsJson = o.optJSONArray("clinical_tags")?.toString() ?: "[]",
            fiberG = facts.firstOrNull { it.getString("key") == "fiber" }?.getDouble("value"),
            nutritionFactsJson = JSONArray(facts).toString()
        )
    }

    fun nutritionFacts(card: GroceryRetailCardEntity): List<GroceryNutritionFact> {
        val facts = JSONArray(card.nutritionFactsJson)
        return (0 until facts.length()).map { index ->
            val fact = facts.getJSONObject(index)
            GroceryNutritionFact(fact.getString("key"), fact.getString("label"), fact.getString("unit"), fact.getDouble("value"))
        }
    }

    private fun normalizedFacts(o: JSONObject): List<JSONObject> {
        if (o.has("nutrition_facts") && !o.isNull("nutrition_facts")) {
            val array = o.getJSONArray("nutrition_facts")
            val keys = mutableSetOf<String>()
            return (0 until array.length()).map { index ->
                val fact = array.getJSONObject(index)
                require(fact.getString("key").isNotBlank() && keys.add(fact.getString("key")))
                require(fact.getString("label").isNotBlank() && fact.getString("unit").isNotBlank())
                require(fact.getDouble("value").let { it.isFinite() && it >= 0 })
                // Keep source attribution along with the nutrient for offline use.
                fact
            }
        }
        val fields = listOf(
            listOf("calories", "Calorias", "kcal", "kcal_per_package"),
            listOf("carbohydrates", "Carboidratos", "g", "carbs_g"),
            listOf("protein", "Proteínas", "g", "protein_g"),
            listOf("fat", "Gorduras", "g", "fat_g"),
            listOf("fiber", "Fibras alimentares", "g", "fiber_g")
        )
        return fields.mapNotNull { (key, label, unit, field) ->
            if (o.isNull(field) || !o.has(field)) null else {
                val value = o.getDouble(field)
                require(value.isFinite() && value >= 0)
                JSONObject().put("key", key).put("label", label).put("unit", unit).put("value", value)
            }
        }
    }
}
