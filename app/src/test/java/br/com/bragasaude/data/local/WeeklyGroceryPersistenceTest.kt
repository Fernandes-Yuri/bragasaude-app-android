package br.com.bragasaude.data.local

import androidx.room.Room
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
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class WeeklyGroceryPersistenceTest {
    private lateinit var db: BragaDatabase
    private lateinit var repository: WeeklyGrocerySummaryRepository
    private val context get() = RuntimeEnvironment.getApplication()
    private val ingredient = GroceryIngredient("a", "Produto", "kg", 1, 1, emptyList(), listOf("f"))
    private val catalog = GroceryIngredientCatalog(listOf(ingredient), mapOf("f" to listOf("a")), purchaseFactors = mapOf("f" to mapOf("a" to 0.5)))
    private fun row(manual: Boolean = false) = GroceryListItemEntity("r", "u", GroceryWeek.start(), "a", "Produto", "Grãos", 1000, 1000, "1000 g", 0.0,
        isManual = manual, plannedWeeklyAmount = 500.0)
    private fun plan() = WeeklyGroceryPlanResult(listOf(row()), 21000.0, 20000.0, 95.238, 800.0, 2400.0, 700.0, 10, listOf("Limitação"), "Resumo")
    private fun open() {
        db = Room.databaseBuilder(context, BragaDatabase::class.java, "weekly-test.db").allowMainThreadQueries().addMigrations(*Migrations.ALL).build()
        repository = WeeklyGrocerySummaryRepository(db)
    }
    @Before fun before() { context.deleteDatabase("weekly-test.db"); open() }
    @After fun after() { db.close(); context.deleteDatabase("weekly-test.db") }

    @Test fun summarySurvivesDatabaseReopeningAndIsScopedByUserAndWeek() = runBlocking {
        repository.save("u", plan(), false)
        db.close(); open()
        val restored = repository.observe("u", GroceryWeek.start()).first()!!
        assertEquals(21000.0, restored.targetWeeklyCalories, 0.001)
        assertEquals(listOf("Limitação"), restored.limitations)
        assertNull(repository.observe("other", GroceryWeek.start()).first())
        assertNull(repository.observe("u", "2000-01-03").first())
        repository.invalidate("u", "Alterada")
        db.close(); open()
        assertTrue(repository.observe("u", GroceryWeek.start()).first()!!.isManuallyModified)
    }

    @Test fun resizingPreservesManualItemsAndInvalidatesCoverage() = runBlocking {
        db.groceryListDao().insertAll(listOf(row(true).copy(purchaseWeightGrams = 5000)))
        repository.save("u", plan(), true)
        val result = repository.observe("u", GroceryWeek.start()).first()!!
        assertEquals(1, result.items.size)
        assertEquals(5000, result.items.single().purchaseWeightGrams)
        assertTrue(result.isManuallyModified)
    }

    @Test fun mealConsumptionIsPersistentIdempotentAndReversible() = runBlocking {
        repository.save("u", plan(), false)
        repository.check("u", "r", true, catalog)
        val meal = GroceryMealEntity("m", "u", LocalDate.now().toString(), "f", "Preparo", "Almoço", 200, 300.0)
        repository.log(meal, catalog)
        repository.log(meal, catalog)
        assertEquals(900.0, db.groceryPantryDao().stock("u", "a")!!.availableAmount, 0.001)
        db.close(); open()
        assertEquals(1, repository.meals("u", LocalDate.now().toString()).first().size)
        repository.updatePortion("u", "m", 400, catalog)
        assertEquals(800.0, db.groceryPantryDao().stock("u", "a")!!.availableAmount, 0.001)
        repository.removeMeal("u", "m")
        assertEquals(1000.0, db.groceryPantryDao().stock("u", "a")!!.availableAmount, 0.001)
        assertTrue(repository.meals("u", LocalDate.now().toString()).first().isEmpty())
    }

    @Test fun unmarkedStockIsNeverDebitedAndStockCannotBecomeNegative() = runBlocking {
        repository.save("u", plan(), false)
        val meal = GroceryMealEntity("m", "u", LocalDate.now().toString(), "f", "Preparo", "Almoço", 3000, 300.0)
        repository.log(meal, catalog)
        assertNull(db.groceryPantryDao().stock("u", "a"))
        repository.check("u", "r", true, catalog)
        assertTrue(repository.log(meal, catalog).any { it.contains("Saldo insuficiente") })
        assertEquals(0.0, db.groceryPantryDao().stock("u", "a")!!.availableAmount, 0.001)
        repository.removeMeal("u", "m")
        assertEquals(1000.0, db.groceryPantryDao().stock("u", "a")!!.availableAmount, 0.001)
    }
}
