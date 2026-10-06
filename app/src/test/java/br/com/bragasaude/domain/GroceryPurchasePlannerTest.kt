package br.com.bragasaude.domain

import br.com.bragasaude.data.local.GroceryListItemEntity
import org.junit.Assert.*
import org.junit.Test

class GroceryPurchasePlannerTest {
    private fun ingredient(unit: String = "kg", price: Double? = 16.0, step: Int = 1, minimum: Int = 50) =
        GroceryIngredient("test", "Ingrediente", unit, step, minimum, listOf("Cozido", "Refogado"), listOf("a", "b"), price)

    @Test fun projectsMassVolumeAndCountUsingOnlyTheirOwnUnit() {
        assertEquals(8.0, GroceryPurchasePlanner.cost(ingredient(), 500), 0.001)
        assertEquals(10.0, GroceryPurchasePlanner.cost(ingredient("L", 20.0), 500), 0.001)
        assertEquals(12.0, GroceryPurchasePlanner.cost(ingredient("un", 1.0), 12), 0.001)
        assertEquals("500 g", GroceryPurchasePlanner.text(ingredient(), 500))
        assertEquals("0.500 L", GroceryPurchasePlanner.text(ingredient("L"), 500))
        assertEquals("12 un", GroceryPurchasePlanner.text(ingredient("un"), 12))
        assertEquals(0.0, GroceryPurchasePlanner.cost(ingredient(price = null), 500), 0.001)
    }

    @Test fun plansCompletePackagesInsteadOfChargingTheWholeUnitForEveryItem() {
        val rice = ingredient(step = 1000, minimum = 1000)
        assertEquals(1000, GroceryPurchasePlanner.quantity(rice, 600))
        assertEquals(2000, GroceryPurchasePlanner.quantity(rice, 1400))
        assertEquals(12, GroceryPurchasePlanner.quantity(ingredient("un", step = 12, minimum = 12), 350))
        assertEquals(500, GroceryPurchasePlanner.quantity(ingredient("L", minimum = 500), 100))
    }

    private fun old(id: String, foodId: String, name: String, checked: Boolean) = GroceryListItemEntity(
        id, "user", "2026-10-06", foodId, name, "Feira", 200, 200, "1 pacote", 16.0, checked)

    @Test fun canonicalizesExistingListWithoutLosingCheckedStateOrDoublingRecipes() {
        val ing = ingredient()
        val catalog = GroceryIngredientCatalog(listOf(ing), mapOf("a" to listOf("test"), "b" to listOf("test")))
        val migrated = GroceryPurchasePlanner.canonicalize(listOf(old("1", "a", "Cozido", true), old("2", "b", "Refogado", false)), catalog)
        assertEquals(1, migrated.size)
        assertEquals("Ingrediente", migrated.single().foodName)
        assertEquals(400, migrated.single().purchaseWeightGrams)
        assertEquals(6.4, migrated.single().estimatedPriceBrl, 0.001)
        assertFalse(migrated.single().isCheckedInPantry)
        assertEquals(migrated, GroceryPurchasePlanner.canonicalize(migrated, catalog))
        val preserved = GroceryPurchasePlanner.canonicalize(listOf(old("1", "a", "Cozido", true)), catalog)
        assertTrue(preserved.single().isCheckedInPantry)
        val custom = old("3", "custom", "Item pessoal", true)
        assertEquals(listOf(custom), GroceryPurchasePlanner.canonicalize(listOf(custom), catalog))
    }
}
