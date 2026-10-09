package br.com.bragasaude.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WeeklyGroceryPreferencesTest {

    @Test
    fun recommendedPreferences_isEconomicAndContainsEggsAndPoultry() {
        val prefs = WeeklyGroceryPreferences.RECOMMENDED
        assertEquals(GroceryBudgetTier.ECONOMIC, prefs.budgetTier)
        assertTrue(prefs.isEconomic)
        assertTrue(prefs.selectedProteins.contains(GroceryProteinPreference.EGGS))
        assertTrue(prefs.selectedProteins.contains(GroceryProteinPreference.POULTRY))
        assertTrue(prefs.hasPantryStaples)
    }

    @Test
    fun defaultPreferences_isBalanced() {
        val prefs = WeeklyGroceryPreferences()
        assertEquals(GroceryBudgetTier.BALANCED, prefs.budgetTier)
        assertFalse(prefs.isEconomic)
        assertTrue(prefs.selectedProteins.isEmpty())
        assertFalse(prefs.hasPantryStaples)
    }

    @Test
    fun normalized_whenProteinsEmptyInEconomic_defaultsToEggs() {
        val emptyPrefs = WeeklyGroceryPreferences(
            budgetTier = GroceryBudgetTier.ECONOMIC,
            selectedProteins = emptySet()
        )
        val normalized = emptyPrefs.normalized()
        assertFalse(normalized.selectedProteins.isEmpty())
        assertTrue(normalized.selectedProteins.contains(GroceryProteinPreference.EGGS))
    }

    @Test
    fun balancedTier_isNotEconomic() {
        val balancedPrefs = WeeklyGroceryPreferences(
            budgetTier = GroceryBudgetTier.BALANCED,
            selectedProteins = setOf(GroceryProteinPreference.FISH, GroceryProteinPreference.BEEF),
            hasPantryStaples = false
        )
        assertFalse(balancedPrefs.isEconomic)
        assertEquals(2, balancedPrefs.selectedProteins.size)
        assertFalse(balancedPrefs.hasPantryStaples)
    }
}
