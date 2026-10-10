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
    fun normalized_whenProteinsEmptyInAnyBudgetTier_containsCarnivorousAndComplementaryEggs() {
        val tiers = listOf(
            GroceryBudgetTier.ULTRA_ECONOMIC,
            GroceryBudgetTier.ECONOMIC,
            GroceryBudgetTier.MODERATE,
            GroceryBudgetTier.FREE
        )
        for (tier in tiers) {
            val emptyPrefs = WeeklyGroceryPreferences(
                budgetTier = tier,
                selectedProteins = emptySet()
            )
            val normalized = emptyPrefs.normalized()
            assertFalse("Proteínas não devem ser vazias para $tier", normalized.selectedProteins.isEmpty())
            assertTrue("Deve conter frango como carnívora para $tier", normalized.selectedProteins.contains(GroceryProteinPreference.POULTRY))
            assertTrue("Deve conter ovos como complementar para $tier", normalized.selectedProteins.contains(GroceryProteinPreference.EGGS))
        }
    }

    @Test
    fun normalized_whenOnlyEggsSelected_addsCarnivorousProtein() {
        val eggsOnlyPrefs = WeeklyGroceryPreferences(
            budgetTier = GroceryBudgetTier.ECONOMIC,
            selectedProteins = setOf(GroceryProteinPreference.EGGS)
        )
        val normalized = eggsOnlyPrefs.normalized()
        assertTrue("Deve manter ovo como complementar", normalized.selectedProteins.contains(GroceryProteinPreference.EGGS))
        assertTrue("Deve adicionar proteína carnívora (frango)", normalized.selectedProteins.contains(GroceryProteinPreference.POULTRY))
    }

    @Test
    fun normalized_whenCarnivorousAlreadyPresent_preservesUserSelection() {
        val fishPrefs = WeeklyGroceryPreferences(
            budgetTier = GroceryBudgetTier.MODERATE,
            selectedProteins = setOf(GroceryProteinPreference.FISH)
        )
        val normalized = fishPrefs.normalized()
        assertTrue(normalized.selectedProteins.contains(GroceryProteinPreference.FISH))
        assertEquals(setOf(GroceryProteinPreference.FISH), normalized.selectedProteins)
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
