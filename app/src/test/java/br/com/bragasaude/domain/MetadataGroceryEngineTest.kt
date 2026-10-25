package br.com.bragasaude.domain

import br.com.bragasaude.data.local.*
import br.com.bragasaude.data.remote.model.RemoteProfile
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test

class MetadataGroceryEngineTest {
    private fun food(id: Int, pillar: NutritionalPillar, role: MealRole, shelf: ShelfLife,
                     cap: Int = 200, tier: CostTier = CostTier.ESSENTIAL) = FoodEntity(
        remoteId = "alimento_xyz_$id", name = "Exibição $id", category = "categoria arbitrária",
        kcal = 100.0, carbsG = 15.0, proteinG = 5.0, fatG = 2.0,
        servingSizeGrams = 100, minServingGrams = 20, maxDailyGrams = cap,
        semanticMetadata = FoodSemanticMetadata(pillar, role,
            if (pillar == NutritionalPillar.PROTEIN) ProteinSubtype.POULTRY else ProteinSubtype.NONE,
            shelf, tier, role == MealRole.PANTRY_SEASONING)
    )

    private val foods = listOf(
        food(1, NutritionalPillar.GRAINS_TUBERS, MealRole.STAPLE_BASE, ShelfLife.SHELF_STABLE, 400),
        food(2, NutritionalPillar.PROTEIN, MealRole.PRIMARY_PROTEIN, ShelfLife.PERISHABLE_FAST, 300),
        food(3, NutritionalPillar.PRODUCE, MealRole.VEGETABLE_LEAFY, ShelfLife.PERISHABLE_FAST, 50),
        food(4, NutritionalPillar.PRODUCE, MealRole.VEGETABLE_SOLID, ShelfLife.PERISHABLE_DURABLE, 500),
        food(5, NutritionalPillar.PRODUCE, MealRole.FRUIT_DENSE, ShelfLife.PERISHABLE_DURABLE, 500),
        food(6, NutritionalPillar.PANTRY_FATS, MealRole.PANTRY_SEASONING, ShelfLife.SHELF_STABLE, 20)
    )
    private fun ingredients(values: List<FoodEntity>, required: Boolean = false) = GroceryIngredientCatalog(
        values.mapIndexed { i, f -> GroceryIngredient("ingrediente_abc_$i", "Produto $i", "kg", 1, 1,
            emptyList(), listOf(f.remoteId), price = 10.0) },
        values.mapIndexed { i, f -> f.remoteId to listOf("ingrediente_abc_$i") }.toMap(),
        if (required) values.map { listOf(it.remoteId) } else emptyList(),
        values.mapIndexed { i, f -> f.remoteId to mapOf("ingrediente_abc_$i" to 1.0) }.toMap()
    )
    private fun plan(days: Int = 7, values: List<FoodEntity> = foods,
                     prefs: WeeklyGroceryPreferences = WeeklyGroceryPreferences(), required: Boolean = false) =
        WeeklyGroceryEngine.planWeeklyGrocery("u", emptyList(), null, values,
            ingredientCatalog = ingredients(values, required), targetCalories = 1800.0,
            preferences = prefs.copy(planningDays = days), shuffleSeed = 42)

    @Test fun renamingEveryDisplayLabelPreservesConsumptionAndNutrition() {
        val first = plan()
        val renamed = plan(values = foods.map { it.copy(name = "任意 ${it.remoteId}", category = "???") })
        assertEquals(first.items.map { it.foodId to it.suggestedServingWeekGrams }, renamed.items.map { it.foodId to it.suggestedServingWeekGrams })
        assertEquals(first.plannedWeeklyCalories, renamed.plannedWeeklyCalories, 0.001)
        assertTrue(first.items.isNotEmpty())
        assertTrue(first.plannedWeeklyCalories > 0)
    }

    @Test fun fortnightCapsFreshProduceAndScalesProteinWithFreezingNotice() {
        val week = plan()
        val fortnight = plan(14)
        fun grams(result: WeeklyGroceryPlanResult, index: Int) = result.items.first { it.foodId == "ingrediente_abc_$index" }.suggestedServingWeekGrams
        assertEquals(week.targetWeeklyCalories * 2, fortnight.targetWeeklyCalories, 0.001)
        assertEquals(grams(week, 2), grams(fortnight, 2))
        assertTrue(grams(fortnight, 2) <= 350)
        assertEquals(grams(week, 1) * 2, grams(fortnight, 1))
        assertEquals(grams(week, 5), grams(fortnight, 5))
        assertTrue(grams(fortnight, 3) + grams(fortnight, 4) > grams(week, 3) + grams(week, 4))
        assertTrue(fortnight.limitations.any { it.contains("Congele") })
    }

    @Test fun requiredGroupsCannotBypassBudgetPantryOrDailyCaps() {
        val values = foods.map { if (it.remoteId.endsWith("2")) it.copy(semanticMetadata = it.semanticMetadata.copy(costTier = CostTier.PREMIUM)) else it }
        val result = plan(14, values, WeeklyGroceryPreferences(budgetTier = GroceryBudgetTier.ULTRA_ECONOMIC, hasPantryStaples = true), true)
        assertFalse(result.items.any { it.foodId in setOf("ingrediente_abc_1", "ingrediente_abc_5") })
        result.items.forEach { item ->
            val index = item.foodId.substringAfterLast('_').toInt()
            val days = if (index == 2) 7 else 14
            assertTrue(item.suggestedServingWeekGrams <= values[index].maxDailyGrams!! * days)
        }
    }

    @Test fun clinicalAndAllergyTagsOverrideRequiredGroups() {
        val values = foods.take(3).mapIndexed { i, food -> when (i) {
            0 -> food.copy(isDiabetesSafe = false)
            1 -> food.copy(functionalTags = listOf("allergen:teste"))
            else -> food.copy(isHypertensionSafe = false)
        } }
        val result = WeeklyGroceryEngine.planWeeklyGrocery("u", emptyList(),
            RemoteProfile("u", hasDiabetes = true, hasHypertension = true, foodAllergies = listOf("teste")),
            values, ingredientCatalog = ingredients(values, true))
        assertTrue(result.items.isEmpty())
    }

    @Test fun parserAndRoomConverterTolerateLegacyAndUnknownEnums() {
        val decoded = RemoteNutritionCatalogParser.parseFoods(JSONArray("""[
            {"remoteId":"a","name":"A"},
            {"remoteId":"b","name":"B","semanticMetadata":{"pillar":"FUTURE","shelfLife":"FUTURE"}},
            {"remoteId":"c","name":"C","maxDailyGrams":50,"semanticMetadata":{"pillar":"PRODUCE","mealRole":"VEGETABLE_LEAFY","shelfLife":"PERISHABLE_FAST"}}
        ]"""))
        assertEquals(FoodSemanticMetadata(), decoded[0].semanticMetadata)
        assertEquals(FoodSemanticMetadata(), decoded[1].semanticMetadata)
        assertEquals(MealRole.VEGETABLE_LEAFY, decoded[2].semanticMetadata.mealRole)
        val converter = Converters()
        assertEquals(decoded[2].semanticMetadata, converter.metadataFromString(converter.metadataToString(decoded[2].semanticMetadata)))
        assertEquals(FoodSemanticMetadata(), converter.metadataFromString("{}"))
    }
}
