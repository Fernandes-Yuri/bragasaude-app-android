package br.com.bragasaude.data.local

import android.app.Application
import androidx.room.Room
import br.com.bragasaude.data.remote.api.BragaApiClient
import br.com.bragasaude.data.remote.model.PublishedGroceryPack
import br.com.bragasaude.data.remote.repository.GroceryRetailRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE, application = Application::class)
class PublishedGroceryPackPersistenceTest {
    private lateinit var db: BragaDatabase
    private val context get() = RuntimeEnvironment.getApplication()
    private val date get() = LocalDate.now(ZoneId.of("America/Sao_Paulo")).toString()
    private fun open() {
        db = Room.databaseBuilder(context, BragaDatabase::class.java, "published-test.db")
            .allowMainThreadQueries().addMigrations(*Migrations.ALL).build()
    }
    @Before fun before() { context.deleteDatabase("published-test.db"); open() }
    @After fun after() { db.close(); context.deleteDatabase("published-test.db") }
    private fun feed(version: Long, market: String = "a"): JSONObject {
        val product = JSONObject().put("id", "butter").put("name", "Manteiga").put("category", "Laticínios")
            .put("image_url", "https://example.org/butter.png").put("package_display", "Pote 200g")
            .put("package_net_value", 200).put("package_unit", "g").put("kcal_per_package", 1400)
            .put("carbs_g", 0).put("protein_g", 1).put("fat_g", 160).put("price_avg", 12.9)
        val quote = JSONObject().put("market_id", market).put("price", 10.0).put("conditions", "Oferta na loja").put("confirmed", true).put("minimum_quantity", 1)
        val pack = JSONObject().put("id", "pack").put("market_id", "a").put("name", "Pack da semana").put("pack_type", "complete")
        val preview = JSONObject().put("pack", pack).put("market", JSONObject().put("name", "Mercado A")
            .put("logo_url", "https://example.org/logo.png").put("website_url", "https://example.org"))
            .put("total_brl", 20.0).put("items", JSONArray().put(JSONObject().put("product", product).put("quote", quote).put("quantity", 2)))
        val edition = JSONObject().put("id", "edition").put("region", "Rio").put("starts_on", date).put("ends_on", date)
        return JSONObject().put("revision", version).put("business_date", date).put("publications", JSONArray().put(
            JSONObject().put("id", "publication-$version").put("version", version).put("edition", edition).put("packs", JSONArray().put(preview))))
    }

    @Test fun adoptionUsesMarketPriceAndFeedUpdatesPreserveExistingCheckedList() = runBlocking {
        val api = mockk<BragaApiClient>()
        coEvery { api.getGroceryPublicationRevision() } returns JSONObject().put("revision", 1).put("business_date", date)
        coEvery { api.getGroceryPublications() } returns feed(1)
        val repo = GroceryRetailRepository(db, api)
        repo.refreshPublications()
        val pack = repo.publications().first().single()
        repo.adopt("u", pack)
        val item = db.groceryListDao().getGrocerySnapshot("u").single()
        assertEquals(20.0, item.estimatedPriceBrl, 0.001)
        assertEquals(2, item.quantity)
        db.groceryListDao().updateCheckedStatus(item.remoteId, true)
        coEvery { api.getGroceryPublicationRevision() } returns JSONObject().put("revision", 2).put("business_date", date)
        coEvery { api.getGroceryPublications() } returns feed(2)
        repo.refreshPublications()
        assertTrue(db.groceryListDao().getGrocerySnapshot("u").single().isCheckedInPantry)
        assertEquals(item.remoteId, db.groceryListDao().getGrocerySnapshot("u").single().remoteId)
        assertEquals("publication-1", repo.adopted("u").first()!!.publicationId)
        db.close(); open()
        assertEquals("publication-1", GroceryRetailRepository(db, api).adopted("u").first()!!.publicationId)
        assertNull(GroceryRetailRepository(db, api).adopted("other").first())
    }

    @Test fun withdrawnPackCannotBeAdoptedAndClearingListPreservesPantry() = runBlocking {
        val api = mockk<BragaApiClient>()
        val repo = GroceryRetailRepository(db, api)
        val original = feed(1)
        db.groceryPublicationDao().save(GroceryPublicationCache(payload = original.toString(), fetchedAt = 0))
        val pack = PublishedGroceryPack.fromFeed(original).single()
        repo.adopt("u", pack)
        db.groceryPantryDao().upsert(GroceryPantryStockEntity("u", "butter", "kg", 400.0))
        db.groceryPublicationDao().save(GroceryPublicationCache(payload = JSONObject().put("publications", JSONArray()).toString(), fetchedAt = 0))
        try { repo.adopt("u", pack); fail("Withdrawn pack must not be adopted") } catch (_: IllegalArgumentException) { }
        repo.clearAdoptedList("u")
        assertTrue(db.groceryListDao().getGrocerySnapshot("u").isEmpty())
        assertNull(repo.adopted("u").first())
        assertEquals(400.0, db.groceryPantryDao().stock("u", "butter")!!.availableAmount, 0.001)
    }

    @Test fun mixedMarketsAreRejectedBeforeAnyPersistence() {
        try { PublishedGroceryPack.fromFeed(feed(1, market = "other")); fail("A pack must use one market") }
        catch (_: IllegalArgumentException) { }
    }
}
