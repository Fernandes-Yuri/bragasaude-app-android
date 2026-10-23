package br.com.bragasaude.ui.nutrition

import br.com.bragasaude.data.local.GroceryListItemEntity
import br.com.bragasaude.data.local.GroceryRetailCardEntity
import org.junit.Assert.*
import org.junit.Test

class ShoppingRetailCardResolverTest {
    private val row = GroceryListItemEntity("row", "user", "2026-10-19", "nozes-chilenas", "Nozes Chilenas",
        "Mercearia", 200, 250, "250 g", 12.0)
    private val card = GroceryRetailCardEntity("pending-nuts", "Nozes Chilenas", "Mercearia", "https://example.org/nuts.jpg",
        "500 g", 500.0, "g", 3000.0, 20.0, 50.0, 200.0, 25.0, "nozes-chilenas", "[]", "[]")

    @Test fun dietItemFindsImageByCanonicalSlugWithoutChangingPurchaseData() {
        val renamed = card.copy(name = "Embalagem de nozes")
        assertEquals(renamed, resolveShoppingRetailCard(row, listOf(renamed)))
        assertNull(row.retailCardId)
        assertEquals("250 g", row.purchaseUnitText)
        assertEquals(12.0, row.estimatedPriceBrl, 0.001)
    }
    @Test fun explicitRetailIdWinsOverSlugAndName() {
        val explicit = card.copy(id = "chosen", name = "Outro pacote", linkedIngredientSlug = null)
        assertEquals(explicit, resolveShoppingRetailCard(row.copy(retailCardId = "chosen"), listOf(card, explicit)))
    }
    @Test fun canonicalSlugWinsOverNameAndUnknownIdFallsBack() {
        val nameOnly = card.copy(id = "name-only", linkedIngredientSlug = null)
        assertEquals(card, resolveShoppingRetailCard(row.copy(retailCardId = "retired"), listOf(nameOnly, card)))
        assertEquals(nameOnly, resolveShoppingRetailCard(row.copy(foodId = "unknown", foodName = " nozes chilenas "), listOf(nameOnly)))
    }
    @Test fun unrelatedProductsAndMissingSlugsDoNotAcquireRandomImages() {
        assertNull(resolveShoppingRetailCard(row, listOf(card.copy(name = "Arroz", linkedIngredientSlug = null))))
        assertNull(resolveShoppingRetailCard(row.copy(foodId = "", foodName = ""), listOf(card.copy(name = "", linkedIngredientSlug = ""))))
        assertNull(resolveShoppingRetailCard(row, emptyList()))
    }
}
