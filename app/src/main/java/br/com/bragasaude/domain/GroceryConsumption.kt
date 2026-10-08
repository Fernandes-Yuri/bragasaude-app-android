package br.com.bragasaude.domain

import java.time.LocalDate
import java.time.DayOfWeek
import java.time.temporal.TemporalAdjusters

object GroceryWeek {
    fun start(date: LocalDate = LocalDate.now()): String = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toString()
    fun daysRemaining(date: LocalDate = LocalDate.now()): Int = 8 - date.dayOfWeek.value
}

data class IngredientConsumption(
    val amounts: Map<String, Double>,
    val limitations: List<String> = emptyList(),
    val structuredLimitations: List<GroceryLimitation> = emptyList()
)

/** Quantidades na unidade interna da compra: g, ml ou contagem de unidades. */
object GroceryConsumption {
    fun amounts(foodId: String, name: String, preparedGrams: Double, catalog: GroceryIngredientCatalog): IngredientConsumption {
        require(preparedGrams.isFinite() && preparedGrams >= 0)
        val ingredients = catalog.forFood(foodId, name)
        val limitations = mutableListOf<String>()
        val structured = mutableListOf<GroceryLimitation>()
        if (ingredients.isEmpty()) {
            val message = "Sem vínculo de ingredientes para $name."
            return IngredientConsumption(
                amounts = emptyMap(),
                limitations = listOf(message),
                structuredLimitations = listOf(
                    GroceryLimitation(
                        type = GroceryLimitationType.MISSING_INGREDIENT_MAPPING,
                        affectedItems = listOf(name),
                        impact = GroceryCalculationImpact.NOT_CALCULABLE,
                        userSummary = message
                    )
                )
            )
        }
        val shares = catalog.componentProportions[foodId]
        if (ingredients.size > 1 && shares == null) {
            val message = "Proporções dos ingredientes não cadastradas para $name; compra e baixa não calculadas."
            return IngredientConsumption(
                amounts = emptyMap(),
                limitations = listOf(message),
                structuredLimitations = listOf(
                    GroceryLimitation(
                        type = GroceryLimitationType.MISSING_RECIPE_PROPORTIONS,
                        affectedItems = listOf(name),
                        impact = GroceryCalculationImpact.NOT_CALCULABLE,
                        userSummary = message
                    )
                )
            )
        }
        val amounts = ingredients.mapNotNull { ingredient ->
            val factor = catalog.purchaseFactors[foodId]?.get(ingredient.slug)
            if (factor == null) {
                val message = "Rendimento não cadastrado para $name; conversão de massa estimada em 1:1."
                limitations.add(message)
                structured.add(
                    GroceryLimitation(
                        type = GroceryLimitationType.MISSING_YIELD,
                        affectedItems = listOf(name),
                        impact = GroceryCalculationImpact.APPROXIMATED,
                        userSummary = message
                    )
                )
            }
            val grams = preparedGrams * (shares?.get(ingredient.slug) ?: 1.0) * (factor ?: 1.0)
            val amount = when (ingredient.unit) {
                "kg" -> grams
                "L" -> {
                    val density = ingredient.densityGPerMl
                    if (density == null) {
                        val message = "Densidade não cadastrada para ${ingredient.name}; compra em litros não calculada."
                        limitations.add(message)
                        structured.add(
                            GroceryLimitation(
                                type = GroceryLimitationType.MISSING_DENSITY,
                                affectedItems = listOf(ingredient.name),
                                impact = GroceryCalculationImpact.NOT_CALCULABLE,
                                userSummary = message
                            )
                        )
                    }
                    density?.let { grams / it }
                }
                "un" -> {
                    val gramsPerUnit = ingredient.gramsPerUnit
                    if (gramsPerUnit == null) {
                        val message = "Peso por unidade não cadastrado para ${ingredient.name}; contagem de unidades não calculada."
                        limitations.add(message)
                        structured.add(
                            GroceryLimitation(
                                type = GroceryLimitationType.MISSING_UNIT_WEIGHT,
                                affectedItems = listOf(ingredient.name),
                                impact = GroceryCalculationImpact.NOT_CALCULABLE,
                                userSummary = message
                            )
                        )
                    }
                    gramsPerUnit?.let { grams / it }
                }
                else -> {
                    val message = "Conversão física não cadastrada para ${ingredient.name}; compra e baixa não calculadas."
                    limitations.add(message)
                    structured.add(
                        GroceryLimitation(
                            type = GroceryLimitationType.MISSING_INGREDIENT_MAPPING,
                            affectedItems = listOf(ingredient.name),
                            impact = GroceryCalculationImpact.NOT_CALCULABLE,
                            userSummary = message
                        )
                    )
                    null
                }
            }
            amount?.let { ingredient.slug to it }
        }.toMap()
        return IngredientConsumption(amounts, limitations, structured)
    }

    fun insufficient(available: Double, weeklyDemand: Double, daysRemaining: Int): Boolean =
        weeklyDemand > 0 && available + 0.000001 < weeklyDemand * daysRemaining.coerceIn(0, 7) / 7.0
}

object WeeklyGroceryPlanValidity {
    fun needsResize(plan: WeeklyGroceryPlanResult, dailyTarget: Double, weight: Double?): Boolean =
        !plan.isManuallyModified && (kotlin.math.abs(plan.targetWeeklyCalories - dailyTarget * 7) > 1.0 ||
            (plan.profileWeight != null && plan.profileWeight != weight))
}
