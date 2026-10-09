package br.com.bragasaude.data.remote.repository

import android.content.Context
import android.content.SharedPreferences
import br.com.bragasaude.data.local.FoodDao
import br.com.bragasaude.data.local.FoodEntity
import br.com.bragasaude.data.local.MealRuleDao
import br.com.bragasaude.data.remote.api.BragaApiClient
import io.mockk.*
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class RemoteCatalogRepositoryTest {
    private fun snapshot(name: String = "Nome vindo do servidor") = JSONObject("""{
        "version":8,
        "foods":[{"remoteId":"remote_test","name":"$name","shoppingComponents":["ingredient_test"],"purchaseFactors":{}}],
        "ingredients":[{"slug":"ingredient_test","name":"Ingrediente remoto","unit":"kg","step":500,"minimum":500,"aliases":[],"food_ids":["remote_test"],"price_avg":null}],
        "policy":{"required_groups":[["remote_test"]]}
    }""")

    private class Setup(initial: String? = null) {
        var cached = initial
        val context = mockk<Context>()
        val preferences = mockk<SharedPreferences>()
        val editor = mockk<SharedPreferences.Editor>()
        val dao = mockk<FoodDao>(relaxed = true)
        val api = mockk<BragaApiClient>()
        init {
            every { context.getSharedPreferences(any(), any()) } returns preferences
            every { preferences.getString("snapshot", null) } answers { cached }
            every { preferences.edit() } returns editor
            every { editor.putString("snapshot", any()) } answers { cached = secondArg(); editor }
            every { editor.commit() } returns true
        }
        fun repository() = CatalogRepository(context, dao, mockk<MealRuleDao>(), api)
    }

    @Test fun updatesExistingLocalCatalogFromServerWithoutReadingAssets() = runTest {
        val setup = Setup()
        coEvery { setup.api.getNutritionCatalog() } returns snapshot()
        every { setup.dao.getCatalog() } returns flowOf(listOf(FoodEntity("old", "Antigo")))
        val catalog = setup.repository().fetchGroceryIngredients()
        coVerify { setup.dao.replaceServerCatalog(match { foods -> foods.single().name == "Nome vindo do servidor" }) }
        verify(exactly = 0) { setup.context.assets }
        assertEquals(8, catalog.version)
        assertNotNull(setup.cached)
    }

    @Test fun usesLastCompleteSnapshotOfflineAndRejectsInvalidRefresh() = runTest {
        val previous = snapshot("Versão anterior").toString()
        val setup = Setup(previous)
        val broken = snapshot("Resposta incompleta")
        broken.getJSONArray("ingredients").remove(0)
        coEvery { setup.api.getNutritionCatalog() } returns broken
        val repository = setup.repository()
        repository.fetchGroceryIngredients()
        assertEquals(previous, setup.cached)
        coVerify(exactly = 1) { setup.dao.replaceServerCatalog(match { it.single().name == "Versão anterior" }) }
        coEvery { setup.api.getNutritionCatalog() } returns null
        assertEquals(8, repository.fetchGroceryIngredients().version)
        verify(exactly = 0) { setup.context.assets }
    }

    @Test fun offlineReadDoesNotRequestNetworkOrReadAssets() = runTest {
        val setup = Setup(snapshot().toString())
        setup.repository().fetchGroceryIngredients(includePrices = false)
        coVerify(exactly = 0) { setup.api.getNutritionCatalog() }
        verify(exactly = 0) { setup.context.assets }
    }
}
