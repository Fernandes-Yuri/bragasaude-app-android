package br.com.bragasaude.domain

import org.json.JSONArray
import org.json.JSONObject

/** Decodifica dados do gateway sem conhecer alimentos ou identificadores específicos. */
object RemoteNutritionCatalogParser {
    private fun strings(values: JSONArray): List<String> = (0 until values.length()).map { values.getString(it) }

    private fun ingredients(values: JSONArray): List<GroceryIngredient> = (0 until values.length()).map { i ->
        val item = values.getJSONObject(i)
        val unit = item.getString("unit")
        val step = item.getInt("step")
        val minimum = item.getInt("minimum")
        require(unit in setOf("kg", "L", "un") && step > 0 && minimum > 0)
        val price = if (item.isNull("price_avg")) null else item.optDouble("price_avg").takeIf { it.isFinite() && it > 0 }
        GroceryIngredient(item.getString("slug"), item.getString("name"), unit, step, minimum,
            strings(item.getJSONArray("aliases")), strings(item.getJSONArray("food_ids")), price,
            item.optString("source", "unavailable"))
    }

    fun parse(snapshot: JSONObject): GroceryIngredientCatalog {
        val version = snapshot.getInt("version")
        require(version >= 2)
        val ingredients = ingredients(snapshot.getJSONArray("ingredients"))
        require(ingredients.isNotEmpty() && ingredients.map { it.slug }.distinct().size == ingredients.size)
        val slugs = ingredients.map { it.slug }.toSet()
        val foods = snapshot.getJSONArray("foods")
        require(foods.length() > 0)
        val components = mutableMapOf<String, List<String>>()
        val factors = mutableMapOf<String, Map<String, Double>>()
        for (i in 0 until foods.length()) {
            val food = foods.getJSONObject(i)
            val id = food.getString("remoteId")
            require(id.isNotBlank() && !components.containsKey(id))
            val refs = strings(food.getJSONArray("shoppingComponents"))
            require(refs.isNotEmpty() && refs.all { it in slugs })
            components[id] = refs
            val values = food.optJSONObject("purchaseFactors") ?: JSONObject()
            factors[id] = values.keys().asSequence().associateWith { slug ->
                val factor = values.getDouble(slug)
                require(slug in refs && factor.isFinite() && factor > 0 && factor <= 10)
                factor
            }
        }
        val groups = snapshot.getJSONObject("policy").getJSONArray("required_groups")
        val required = (0 until groups.length()).map { strings(groups.getJSONArray(it)) }
        require(required.all { group -> group.isNotEmpty() && group.all { it in components } })
        return GroceryIngredientCatalog(ingredients, components, required, factors, version)
    }

    fun parseLegacy(manifest: JSONObject): GroceryIngredientCatalog {
        val ingredients = ingredients(manifest.getJSONArray("ingredients"))
        val foods = manifest.getJSONArray("foods")
        val components = (0 until foods.length()).associate { i ->
            val food = foods.getJSONObject(i)
            food.getString("food_id") to strings(food.getJSONArray("shopping_components"))
        }
        return GroceryIngredientCatalog(ingredients, components)
    }
}
