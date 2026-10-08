package br.com.bragasaude.domain

import org.junit.Test
import org.junit.Assert.*
import java.time.LocalDate

class GroceryConsumptionTest {
    private fun catalog(unit: String, density: Double? = null, grams: Double? = null) = GroceryIngredientCatalog(
        listOf(GroceryIngredient("a", "A", unit, 1, 1, emptyList(), listOf("f"), densityGPerMl = density, gramsPerUnit = grams)),
        mapOf("f" to listOf("a")), purchaseFactors = mapOf("f" to mapOf("a" to 1.0)))
    @Test fun usesPhysicalMetadataAndNeverInfersUnitWeightFromServing() {
        assertEquals(100.0, GroceryConsumption.amounts("f", "F", 92.0, catalog("L", density = 0.92)).amounts["a"]!!, 0.001)
        assertEquals(2.0, GroceryConsumption.amounts("f", "F", 100.0, catalog("un", grams = 50.0)).amounts["a"]!!, 0.001)
        assertTrue(GroceryConsumption.amounts("f", "F", 100.0, catalog("un")).amounts.isEmpty())
        assertTrue(GroceryConsumption.amounts("f", "F", 100.0, catalog("L")).limitations.isNotEmpty())
    }
    @Test fun componentsRequireFormalProportionsAndApplyYieldAfterShare() {
        val base = catalog("kg")
        val extra = base.ingredients.first().copy(slug = "b")
        val recipe = base.copy(ingredients = base.ingredients + extra, components = mapOf("f" to listOf("a", "b")),
            purchaseFactors = mapOf("f" to mapOf("a" to 0.5, "b" to 2.0)),
            componentProportions = mapOf("f" to mapOf("a" to 0.8, "b" to 0.2)))
        val result = GroceryConsumption.amounts("f", "F", 100.0, recipe)
        assertEquals(40.0, result.amounts["a"]!!, 0.001)
        assertEquals(40.0, result.amounts["b"]!!, 0.001)
        assertTrue(GroceryConsumption.amounts("f", "F", 100.0, recipe.copy(componentProportions = emptyMap())).amounts.isEmpty())
    }
    @Test fun weekAndShortageAreCalculatedWithoutCountingPackagingAsConsumption() {
        assertEquals("2026-10-05", GroceryWeek.start(LocalDate.parse("2026-10-08")))
        assertEquals(4, GroceryWeek.daysRemaining(LocalDate.parse("2026-10-08")))
        assertTrue(GroceryConsumption.insufficient(30.0, 70.0, 4))
        assertFalse(GroceryConsumption.insufficient(40.0, 70.0, 4))
    }
    @Test fun targetAndWeightChangesInvalidateCoverageWithoutRewritingItems() {
        val plan = WeeklyGroceryPlanResult(emptyList(), 21000.0, 21000.0, 100.0, 800.0, 2500.0, 600.0, 15,
            statusMessage = "Plano", profileWeight = 108.0)
        assertFalse(WeeklyGroceryPlanValidity.needsResize(plan, 3000.0, 108.0))
        assertTrue(WeeklyGroceryPlanValidity.needsResize(plan, 4000.0, 108.0))
        assertTrue(WeeklyGroceryPlanValidity.needsResize(plan, 3000.0, 100.0))
        assertFalse(WeeklyGroceryPlanValidity.needsResize(plan.copy(isManuallyModified = true), 4000.0, 100.0))
    }

}
