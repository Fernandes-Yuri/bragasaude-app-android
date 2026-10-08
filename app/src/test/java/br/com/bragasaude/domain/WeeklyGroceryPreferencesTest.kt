package br.com.bragasaude.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WeeklyGroceryPreferencesTest {

    @Test
    fun defaultPreferences_isEconomicAndContainsEggsAndPoultry() {
        val prefs = WeeklyGroceryPreferences()
        assertEquals(GroceryBudgetTier.ECONOMIC, prefs.budgetTier)
        assertTrue(prefs.isEconomic)
        assertTrue(prefs.selectedProteins.contains(GroceryProteinPreference.EGGS))
        assertTrue(prefs.selectedProteins.contains(GroceryProteinPreference.POULTRY))
        assertTrue(prefs.hasPantryStaples)
    }

    @Test
    fun normalized_whenProteinsEmpty_defaultsToEggs() {
        val emptyPrefs = WeeklyGroceryPreferences(
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
