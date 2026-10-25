package br.com.bragasaude.data.remote.model

import br.com.bragasaude.data.local.GroceryListItemEntity
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class GroceryBasketComparisonTest {
    private fun row(id: String, cardId: String? = "rice", quantity: Int = 1, bought: Boolean = false) = GroceryListItemEntity(
        remoteId = id, userId = "u", weekStartDate = "2026-10-25", foodId = "rice", foodName = "Arroz",
        category = "Cereais", suggestedServingWeekGrams = 0, purchaseWeightGrams = 1000,
        purchaseUnitText = "1 kg", estimatedPriceBrl = 5.0, retailCardId = cardId,
        quantity = quantity, isCheckedInPantry = bought
    )

    @Test fun pendingPackagesAreGroupedAndLegacyAndPurchasedRowsExcluded() {
        val selected = groceryBasketItems(listOf(row("1", quantity = 2), row("2", quantity = 3),
            row("3", cardId = null), row("4", bought = true), row("5", quantity = 0)))
        assertEquals(listOf(GroceryBasketItem("rice", 5)), selected)
        val payload = groceryBasketRequest(selected).getJSONArray("items")
        assertEquals(1, payload.length())
        assertEquals("rice", payload.getJSONObject(0).getString("card_id"))
        assertEquals(5, payload.getJSONObject(0).getInt("quantity"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun aggregatedQuantitiesMustFitGatewayLimit() {
        groceryBasketItems(listOf(row("1", quantity = 999), row("2")))
    }

    @Test(expected = IllegalArgumentException::class)
    fun emptyBasketRejectedBeforeNetwork() { groceryBasketRequest(emptyList()) }

    @Test(expected = IllegalArgumentException::class)
    fun oversizedBasketRejectedBeforeNetwork() { groceryBasketRequest((1..101).map { GroceryBasketItem("c$it", 1) }) }

    @Test fun completeAndPartialMarketsPreserveRankingSavingsAndAssistedLinks() {
        val json = JSONObject("""{"market_rankings":[
          {"market_id":"a","market_name":"A","total_brl":"8.40","complete":true,"is_best_deal":true,"savings_vs_highest_brl":"2.60",
           "super_url":"https://a.test/cart/123:2","items":[{"card_id":"rice","name":"Arroz","quantity":2,"available":true}],"assisted_links":[]},
          {"market_id":"b","market_name":"B","total_brl":4.2,"complete":false,"is_best_deal":false,"savings_vs_highest_brl":null,"super_url":null,
           "items":[{"card_id":"rice","name":"Arroz","quantity":2,"available":false}],
           "assisted_links":[{"name":"Feijão","quantity":1,"url":"https://b.test/beans"},{"name":"Inseguro","quantity":1,"url":"intent://unsafe"}]}
        ]}""")
        val markets = GroceryBasketComparisonDto.parse(json).markets
        assertEquals(listOf("a", "b"), markets.map { it.marketId })
        assertTrue(markets.first().isBestDeal)
        assertEquals(8.4, markets.first().totalBrl, .001)
        assertEquals(2.6, markets.first().savingsBrl!!, .001)
        assertFalse(markets.last().items.first().available)
        assertNull(markets.last().savingsBrl)
        assertNull(markets.last().superUrl)
        assertEquals(listOf(GroceryBasketLink("Feijão", 1, "https://b.test/beans")), markets.last().assistedLinks)
    }

    @Test fun externalUrlsAreRestrictedToWebLinks() {
        listOf(null, "javascript:alert(1)", "intent://cart", "https://user:pass@a.test", "invalid").forEach {
            assertNull(groceryBasketExternalUrl(it))
        }
        assertEquals("https://a.test/cart/123:2", groceryBasketExternalUrl("https://a.test/cart/123:2"))
    }
}
