package br.com.bragasaude.domain

import java.time.LocalDate
import java.time.DayOfWeek
import java.time.temporal.TemporalAdjusters

object GroceryWeek {
    fun start(date: LocalDate = LocalDate.now()): String = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toString()
    fun daysRemaining(date: LocalDate = LocalDate.now()): Int = 8 - date.dayOfWeek.value
}

data class IngredientConsumption(val amounts: Map<String, Double>, val limitations: List<String>)

/** Quantidades na unidade interna da compra: g, ml ou contagem de unidades. */
object GroceryConsumption {
    fun amounts(foodId: String, name: String, preparedGrams: Double, catalog: GroceryIngredientCatalog): IngredientConsumption {
        require(preparedGrams.isFinite() && preparedGrams >= 0)
        val ingredients = catalog.forFood(foodId, name)
        val limitations = mutableListOf<String>()
        if (ingredients.isEmpty()) return IngredientConsumption(emptyMap(), listOf("Sem vínculo de ingredientes para $name."))
        val shares = catalog.componentProportions[foodId]
        if (ingredients.size > 1 && shares == null) return IngredientConsumption(emptyMap(),
            listOf("Proporções dos ingredientes não cadastradas para $name; compra e baixa não calculadas."))
        val amounts = ingredients.mapNotNull { ingredient ->
            val factor = catalog.purchaseFactors[foodId]?.get(ingredient.slug)
            if (factor == null) {
                limitations.add("Rendimento não cadastrado para $name; conversão de massa estimada em 1:1.")
            }
            val grams = preparedGrams * (shares?.get(ingredient.slug) ?: 1.0) * (factor ?: 1.0)
            val amount = when (ingredient.unit) {
                "kg" -> grams
                "L" -> ingredient.densityGPerMl?.let { grams / it }
                "un" -> ingredient.gramsPerUnit?.let { grams / it }
                else -> null
            }
            if (amount == null) limitations.add("Conversão física não cadastrada para ${ingredient.name}; compra e baixa não calculadas.")
            amount?.let { ingredient.slug to it }
        }.toMap()
        return IngredientConsumption(amounts, limitations)
    }

    fun insufficient(available: Double, weeklyDemand: Double, daysRemaining: Int): Boolean =
        weeklyDemand > 0 && available + 0.000001 < weeklyDemand * daysRemaining.coerceIn(0, 7) / 7.0
}
