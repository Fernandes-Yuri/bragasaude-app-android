package br.com.bragasaude.domain

import br.com.bragasaude.data.local.GroceryListItemEntity
import org.junit.Assert.*
import org.junit.Test

class GroceryWeeklyGoalTest {
    private fun butter(quantity: Int = 1) = GroceryListItemEntity(
        remoteId = "butter", userId = "u", weekStartDate = GroceryWeek.start(), foodId = "butter-200g",
        foodName = "Manteiga", category = "Laticínios", suggestedServingWeekGrams = 0,
        purchaseWeightGrams = quantity * 200, purchaseUnitText = "$quantity × Pote 200g", estimatedPriceBrl = quantity * 12.90,
        retailCardId = "butter-200g", quantity = quantity, kcalPerUnit = 1400.0
    )

    @Test fun packagesCountWithoutRoundingToKilograms() {
        val goal = GroceryWeeklyGoal.calculate(2000.0, listOf(butter(2)))
        assertEquals(14000.0, goal.target, 0.001)
        assertEquals(2800.0, goal.accumulated, 0.001)
        assertEquals(11200.0, goal.remaining, 0.001)
        assertEquals(0.2, goal.fraction, 0.001)
    }
    @Test fun editsAndNewDailyTargetImmediatelyChangeCoverage() {
        assertEquals(1400.0, GroceryWeeklyGoal.calculate(2000.0, listOf(butter())).accumulated, 0.001)
        assertEquals(0.0, GroceryWeeklyGoal.calculate(1800.0, emptyList()).accumulated, 0.001)
        assertEquals(12600.0, GroceryWeeklyGoal.calculate(1800.0, emptyList()).remaining, 0.001)
        assertTrue(GroceryWeeklyGoal.calculate(2000.0, listOf(butter(10))).withinTarget)
        assertEquals(0.0, GroceryWeeklyGoal.calculate(2000.0, listOf(butter(11))).remaining, 0.001)
    }
    @Test fun checkedProductsStillSupplyTheWeeksEnergyAndLegacyRowsAreExplicit() {
        val row = butter().copy(isCheckedInPantry = true)
        assertEquals(1400.0, GroceryWeeklyGoal.calculate(2000.0, listOf(row)).accumulated, 0.001)
        val legacy = butter().copy(retailCardId = null, kcalPerUnit = 0.0)
        assertEquals(1, GroceryWeeklyGoal.calculate(2000.0, listOf(legacy)).unknownItems)
    }
}
