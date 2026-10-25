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
    fun defaultPreferences_isModerate() {
        val prefs = WeeklyGroceryPreferences()
        assertEquals(GroceryBudgetTier.MODERATE, prefs.budgetTier)
        assertFalse(prefs.isEconomic)
        assertFalse(prefs.isUltraEconomic)
        assertTrue(prefs.selectedProteins.isEmpty())
        assertFalse(prefs.hasPantryStaples)
    }

    @Test
    fun normalized_whenProteinsEmpty_preservesEmptySetForEngineFlexibility() {
        val emptyPrefs = WeeklyGroceryPreferences(
            budgetTier = GroceryBudgetTier.MODERATE,
            selectedProteins = emptySet()
        )
        val normalized = emptyPrefs.normalized()
        assertTrue("Modo automatizado sem filtro preserva emptySet para total flexibilidade e rotatividade do motor", normalized.selectedProteins.isEmpty())
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
    fun maxBasketSizeFor_scalesWithPlanningDays() {
        assertEquals(10, GroceryBudgetTier.ULTRA_ECONOMIC.maxBasketSizeFor(7))
        assertEquals(16, GroceryBudgetTier.ULTRA_ECONOMIC.maxBasketSizeFor(14))
        assertEquals(22, GroceryBudgetTier.ULTRA_ECONOMIC.maxBasketSizeFor(30))

        assertEquals(15, GroceryBudgetTier.ECONOMIC.maxBasketSizeFor(7))
        assertEquals(20, GroceryBudgetTier.ECONOMIC.maxBasketSizeFor(14))
        assertEquals(28, GroceryBudgetTier.ECONOMIC.maxBasketSizeFor(30))

        assertEquals(20, GroceryBudgetTier.MODERATE.maxBasketSizeFor(7))
        assertEquals(26, GroceryBudgetTier.MODERATE.maxBasketSizeFor(14))
        assertEquals(34, GroceryBudgetTier.MODERATE.maxBasketSizeFor(30))

        assertEquals(35, GroceryBudgetTier.FREE.maxBasketSizeFor(7))
        assertEquals(36, GroceryBudgetTier.FREE.maxBasketSizeFor(14))
        assertEquals(45, GroceryBudgetTier.FREE.maxBasketSizeFor(30))
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

    @Test
    fun groceryPlanningPeriod_labelsAndBadgesAreConsistent() {
        assertEquals("7 dias (Semanal)", GroceryPlanningPeriod.labelFor(7))
        assertEquals("14 dias (Quinzenal)", GroceryPlanningPeriod.labelFor(14))
        assertEquals("30 dias (Mensal)", GroceryPlanningPeriod.labelFor(30))

        assertEquals("Recomendado", GroceryPlanningPeriod.badgeFor(7))
        assertEquals("Recomendado", GroceryPlanningPeriod.badgeFor(14))
        org.junit.Assert.assertNull(GroceryPlanningPeriod.badgeFor(30))

        assertEquals(listOf("80", "120", "160", "200"), GroceryPlanningPeriod.quickBudgetsFor(7))
        assertEquals(listOf("160", "240", "320", "400"), GroceryPlanningPeriod.quickBudgetsFor(14))
    }
}
