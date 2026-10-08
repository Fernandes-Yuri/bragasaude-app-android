package br.com.bragasaude.domain

import br.com.bragasaude.data.local.GroceryListItemEntity
import java.util.Locale
import kotlin.math.ceil

fun groceryNameKey(value: String): String = java.text.Normalizer.normalize(value, java.text.Normalizer.Form.NFD)
    .replace(Regex("[\\p{InCombiningDiacriticalMarks}]"), "").lowercase(Locale.ROOT).trim().replace(Regex("\\s+"), " ")

data class GroceryIngredient(
    val slug: String, val name: String, val unit: String, val step: Int, val minimum: Int,
    val aliases: List<String>, val foodIds: List<String>, val price: Double? = null,
    val source: String = "unavailable"
)

data class GroceryIngredientCatalog(
    val ingredients: List<GroceryIngredient>,
    val components: Map<String, List<String>>,
    val requiredGroups: List<List<String>> = emptyList(),
    val purchaseFactors: Map<String, Map<String, Double>> = emptyMap(),
    val version: Int = 1
) {
    fun forFood(id: String, name: String): List<GroceryIngredient> {
        val keys = components[id]
        if (keys != null) return keys.mapNotNull { slug -> ingredients.firstOrNull { it.slug == slug } }
        return ingredients.filter { it.slug == id || groceryNameKey(it.name) == groceryNameKey(name) ||
            it.aliases.any { alias -> groceryNameKey(alias) == groceryNameKey(name) } }.take(1)
    }
}

object GroceryPurchasePlanner {
    fun parseAmount(text: String, unit: String): Int? {
        val value = text.trim().replace(',', '.').toDoubleOrNull() ?: return null
        if (value <= 0.0 || !value.isFinite()) return null
        return when (unit) {
            "kg" -> (value * 1000).toInt().takeIf { it > 0 }
            "L" -> (value * 1000).toInt().takeIf { it > 0 }
            else -> value.toInt().takeIf { it > 0 }
        }
    }

    fun quantity(item: GroceryIngredient, requestedGrams: Int): Int = when (item.unit) {
        "kg" -> (ceil(maxOf(requestedGrams, item.minimum).toDouble() / item.step) * item.step).toInt()
        // Litros e unidades são planejados por embalagem; não inferimos densidade ou peso de um ovo.
        else -> item.minimum
    }

    fun text(item: GroceryIngredient, amount: Int): String = when (item.unit) {
        "kg" -> "$amount g"
        "L" -> String.format(Locale.ROOT, "%.3f L", amount / 1000.0)
        else -> "$amount un"
    }

    fun cost(item: GroceryIngredient, amount: Int): Double {
        val price = item.price?.takeIf { it.isFinite() && it > 0 } ?: return 0.0
        val quantity = if (item.unit == "un") amount.toDouble() else amount / 1000.0
        return kotlin.math.round(price * quantity * 100) / 100.0
    }

    fun canonicalize(items: List<GroceryListItemEntity>, catalog: GroceryIngredientCatalog): List<GroceryListItemEntity> {
        val transformed = items.flatMap { old ->
            val ingredients = catalog.forFood(old.foodId, old.foodName)
            if (ingredients.isEmpty()) listOf(old) else ingredients.mapIndexed { index, ingredient ->
                val alreadyCanonical = old.foodId == ingredient.slug && old.foodName == ingredient.name
                val amount = if (alreadyCanonical) {
                    val parsed = old.purchaseUnitText.substringBefore(' ').replace(',', '.').toDoubleOrNull()
                    if (parsed != null && parsed > 0) (if (ingredient.unit == "L") parsed * 1000 else parsed).toInt()
                    else quantity(ingredient, old.purchaseWeightGrams)
                } else quantity(ingredient, if (ingredients.size == 1) old.purchaseWeightGrams else ingredient.minimum)
                old.copy(remoteId = if (index == 0) old.remoteId else old.remoteId + ":" + ingredient.slug,
                    foodId = ingredient.slug, foodName = ingredient.name,
                    purchaseWeightGrams = if (ingredient.unit == "kg") amount else 0,
                    purchaseUnitText = text(ingredient, amount), estimatedPriceBrl = if (alreadyCanonical && ingredient.price == null) old.estimatedPriceBrl else cost(ingredient, amount),
                    isCheckedInPantry = old.isCheckedInPantry && ingredients.size == 1)
            }
        }
        return transformed.groupBy { it.foodId }.values.map { group ->
            val first = group.first()
            val ingredient = catalog.ingredients.firstOrNull { it.slug == first.foodId }
            if (ingredient == null || group.size == 1) first else {
                val amount = if (ingredient.unit == "kg") group.sumOf { it.purchaseWeightGrams }
                    else group.sumOf { row -> val value = row.purchaseUnitText.substringBefore(' ').toDoubleOrNull() ?: 0.0
                        (if (ingredient.unit == "L") value * 1000 else value).toInt() }
                first.copy(purchaseWeightGrams = if (ingredient.unit == "kg") amount else 0,
                    purchaseUnitText = text(ingredient, amount), estimatedPriceBrl = cost(ingredient, amount),
                    isCheckedInPantry = group.all { it.isCheckedInPantry })
            }
        }
    }
}
