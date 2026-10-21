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

    @Test fun normalizesCommercialPacksToAvoidAstronomicalCosts() {
        val eggsPack = GroceryIngredient("ovo-caipira", "Ovo Caipira", "un", 1, 1, emptyList(), emptyList(), 13.90)
        // 90 ovos para 30 dias não devem custar R$ 1.251,00, e sim o valor de mercado (bandeja de 10 por R$ 13,90 ~ R$ 1,39 cada)
        val costFor90Eggs = GroceryPurchasePlanner.cost(eggsPack, 90)
        assertTrue("Custo de 90 ovos deve ser realista (~R$ 125), obteve $costFor90Eggs", costFor90Eggs in 40.0..130.0)

        val rice5kgPack = GroceryIngredient("arroz-tipo-1", "Arroz Branco Tipo 1", "kg", 1000, 1000, emptyList(), emptyList(), 21.90)
        // 5kg de arroz não devem custar 5 * 21.90 = R$ 109,50 (saco de 5kg a R$ 21,90 sai ~R$ 4,38/kg)
        val costFor5kgRice = GroceryPurchasePlanner.cost(rice5kgPack, 5000)
        assertTrue("Custo de 5kg de arroz deve ficar em torno de R$ 22, obteve $costFor5kgRice", costFor5kgRice in 15.0..30.0)
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
