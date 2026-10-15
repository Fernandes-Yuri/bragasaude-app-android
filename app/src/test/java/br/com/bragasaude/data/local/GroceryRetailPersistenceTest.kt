package br.com.bragasaude.data.local

import android.app.Application
import androidx.room.Room
import br.com.bragasaude.data.remote.repository.GroceryRepository
import br.com.bragasaude.data.remote.repository.WeeklyGrocerySummaryRepository
import br.com.bragasaude.domain.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE, application = Application::class)
class GroceryRetailPersistenceTest {
    private lateinit var db: BragaDatabase
    private val context get() = RuntimeEnvironment.getApplication()
    private val card = GroceryRetailCardEntity("butter-200g", "Manteiga", "Laticínios", "https://example.org/butter.png",
        "Pote 200g", 200.0, "g", 1400.0, 0.0, 1.0, 160.0, 12.90, "butter", "[]", "[]")
    private val ingredient = GroceryIngredient("butter", "Manteiga", "kg", 1000, 1000, emptyList(), emptyList())
    private val catalog = GroceryIngredientCatalog(listOf(ingredient), emptyMap())
    private fun open() {
        db = Room.databaseBuilder(context, BragaDatabase::class.java, "retail-test.db")
            .allowMainThreadQueries().addMigrations(*Migrations.ALL).build()
    }
    @Before fun before() { context.deleteDatabase("retail-test.db"); open() }
    @After fun after() { db.close(); context.deleteDatabase("retail-test.db") }

    @Test fun packagesSurviveReopenNormalizationAndPantryRoundTrip() = runBlocking {
        db.groceryRetailCardDao().replace(listOf(card))
        val repo = GroceryRepository(db.groceryListDao())
        repo.changeCard("u", card, 1)
        repo.changeCard("u", card, 1)
        repo.standardizeIngredients("u", catalog)
        db.close(); open()
        assertEquals(card, db.groceryRetailCardDao().observe().first().single())
        val item = db.groceryListDao().getGrocerySnapshot("u").single()
        assertEquals(2, item.quantity)
        assertEquals("2 × Pote 200g", item.purchaseUnitText)
        assertEquals(25.8, item.estimatedPriceBrl, 0.001)
        assertEquals(2800.0, GroceryWeeklyGoal.calculate(2000.0, listOf(item)).accumulated, 0.001)
        val weekly = WeeklyGrocerySummaryRepository(db)
        weekly.check("u", item.remoteId, true, catalog)
        weekly.check("u", item.remoteId, true, catalog)
        assertEquals(400.0, db.groceryPantryDao().stock("u", "butter")!!.availableAmount, 0.001)
        weekly.check("u", item.remoteId, false, catalog)
        assertEquals(0.0, db.groceryPantryDao().stock("u", "butter")!!.availableAmount, 0.001)
        GroceryRepository(db.groceryListDao()).changeCard("u", card, -1)
        assertEquals(1, db.groceryListDao().getGrocerySnapshot("u").single().quantity)
        assertTrue(db.groceryListDao().getGrocerySnapshot("other").isEmpty())
    }
}
