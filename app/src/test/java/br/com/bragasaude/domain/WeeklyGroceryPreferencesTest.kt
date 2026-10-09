package br.com.bragasaude.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WeeklyGroceryPreferencesTest {

    @Test
    fun recommendedPreferences_isUltraEconomicAndContainsEggsAndPoultry() {
        val prefs = WeeklyGroceryPreferences.RECOMMENDED
        assertEquals(GroceryBudgetTier.ULTRA_ECONOMIC, prefs.budgetTier)
        assertTrue(prefs.isUltraEconomic)
        assertTrue(prefs.isEconomic)
        assertTrue(prefs.selectedProteins.contains(GroceryProteinPreference.EGGS))
        assertTrue(prefs.selectedProteins.contains(GroceryProteinPreference.POULTRY))
        assertTrue(prefs.hasPantryStaples)
    }

    @Test
    fun defaultPreferences_isModerate() {
        val prefs = WeeklyGroceryPreferences()
        assertEquals(GroceryBudgetTier.MODERATE, prefs.budgetTier)
        assertFalse(prefs.isEconomic)
        assertFalse(prefs.isUltraEconomic)
        assertTrue(prefs.selectedProteins.isEmpty())
        assertFalse(prefs.hasPantryStaples)
    }

    @Test
    fun normalized_whenProteinsEmptyInUltraEconomic_defaultsToEggs() {
        val emptyPrefs = WeeklyGroceryPreferences(
            budgetTier = GroceryBudgetTier.ULTRA_ECONOMIC,
            selectedProteins = emptySet()
        )
        val normalized = emptyPrefs.normalized()
        assertFalse(normalized.selectedProteins.isEmpty())
        assertTrue(normalized.selectedProteins.contains(GroceryProteinPreference.EGGS))
    }

    @Test
    fun allFourTiersHaveConsistentProperties() {
        assertEquals(10, GroceryBudgetTier.ULTRA_ECONOMIC.maxBasketSize)
        assertEquals(15, GroceryBudgetTier.ECONOMIC.maxBasketSize)
        assertEquals(20, GroceryBudgetTier.MODERATE.maxBasketSize)
        assertEquals(35, GroceryBudgetTier.FREE.maxBasketSize)
        assertEquals(GroceryBudgetTier.MODERATE, GroceryBudgetTier.BALANCED)
    }

    @Test
    fun moderateAndFreeTier_areNotEconomic() {
        val modPrefs = WeeklyGroceryPreferences(budgetTier = GroceryBudgetTier.MODERATE)
        val freePrefs = WeeklyGroceryPreferences(budgetTier = GroceryBudgetTier.FREE)
        assertFalse(modPrefs.isEconomic)
        assertFalse(modPrefs.isUltraEconomic)
        assertFalse(freePrefs.isEconomic)
        assertFalse(freePrefs.isUltraEconomic)
    }
}
