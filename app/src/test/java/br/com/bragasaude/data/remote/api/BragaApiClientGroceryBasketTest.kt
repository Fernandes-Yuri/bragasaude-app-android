package br.com.bragasaude.data.remote.api

import br.com.bragasaude.data.remote.auth.AuthService
import br.com.bragasaude.data.remote.model.GroceryBasketItem
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class BragaApiClientGroceryBasketTest {
    private lateinit var server: MockWebServer
    private lateinit var api: BragaApiClient

    @Before fun setup() {
        server = MockWebServer().also { it.start() }
        val auth = mockk<AuthService>()
        every { auth.getTokenBlocking(any(), any()) } returns "test-value"
        api = BragaApiClient(mockk(relaxed = true), server.url("/").toString().trimEnd('/'), auth)
    }
    @After fun cleanup() = server.shutdown()

    @Test fun basketPostsPackageQuantitiesToCanonicalEndpoint() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"market_rankings":[],"best_market":null,"total_items":1}"""))
        assertTrue(api.compareGroceryBasket(listOf(GroceryBasketItem("rice", 2))).markets.isEmpty())
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/catalog/grocery/basket-comparison", request.path)
        val item = JSONObject(request.body.readUtf8()).getJSONArray("items").getJSONObject(0)
        assertEquals("rice", item.getString("card_id"))
        assertEquals(2, item.getInt("quantity"))
    }

    @Test(expected = java.io.IOException::class)
    fun unavailableComparisonIsAnErrorRatherThanEmptyRanking() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(503).setBody("""{"detail":"Indisponível"}"""))
        api.compareGroceryBasket(listOf(GroceryBasketItem("rice", 1)))
        Unit
    }

    @Test(expected = java.io.IOException::class)
    fun retiredProductsRequireCatalogRefresh() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(422).setBody("""{"detail":"Produto inativo"}"""))
        api.compareGroceryBasket(listOf(GroceryBasketItem("rice", 1)))
        Unit
    }
}
