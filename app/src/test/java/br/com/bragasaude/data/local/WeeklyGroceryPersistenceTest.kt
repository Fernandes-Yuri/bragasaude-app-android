package br.com.bragasaude.data.local

import android.app.Application
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
@Config(sdk = [28], manifest = Config.NONE, application = Application::class)
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
        repository.save("u", plan().copy(profileWeight = 108.0), false)
        db.close(); open()
        val restored = repository.observe("u", GroceryWeek.start()).first()!!
        assertEquals(21000.0, restored.targetWeeklyCalories, 0.001)
        assertEquals(108.0, restored.profileWeight!!, 0.001)
        assertEquals(listOf("Limitação"), restored.limitations)
        assertNull(repository.observe("other", GroceryWeek.start()).first())
        assertNull(repository.observe("u", "2000-01-03").first())
        repository.invalidate("u", "Alterada")
        db.close(); open()
        assertTrue(repository.observe("u", GroceryWeek.start()).first()!!.isManuallyModified)
    }

    @Test fun structuredLimitationsAndLegacyFormatSurvivePersistence() = runBlocking {
        val structuredLimitation = GroceryLimitation(
            type = GroceryLimitationType.MISSING_YIELD,
            affectedItems = listOf("Feijão"),
            impact = GroceryCalculationImpact.APPROXIMATED,
            userSummary = "Rendimento estimado 1:1"
        )
        val structuredPlan = plan().copy(
            structuredLimitations = listOf(structuredLimitation),
            purchaseStatus = PurchaseCalculationStatus.APPROXIMATED
        )
        repository.save("u", structuredPlan, false)
        db.close(); open()
        val restoredStructured = repository.observe("u", GroceryWeek.start()).first()!!
        assertEquals(1, restoredStructured.structuredLimitations.size)
        assertEquals(GroceryLimitationType.MISSING_YIELD, restoredStructured.structuredLimitations.first().type)
        assertEquals(listOf("Feijão"), restoredStructured.structuredLimitations.first().affectedItems)
        assertEquals(PurchaseCalculationStatus.APPROXIMATED, restoredStructured.purchaseStatus)

        // Simula registro pré-existente legado no banco com apenas array de strings no JSON
        db.weeklyGrocerySummaryDao().upsert(
            WeeklyGrocerySummaryEntity(
                userId = "legacy_user",
                weekStartDate = GroceryWeek.start(),
                targetWeeklyCalories = 14000.0,
                plannedWeeklyCalories = 14000.0,
                coveragePercent = 100.0,
                plannedProteinGrams = 500.0,
                plannedCarbsGrams = 1500.0,
                plannedFatGrams = 400.0,
                foodVarietyCount = 12,
                limitationsJson = "[\"Aviso legado salvo anteriormente\"]",
                isManuallyModified = false,
                generatedAt = System.currentTimeMillis(),
                statusMessage = "Status legado"
            )
        )
        val restoredLegacy = repository.observe("legacy_user", GroceryWeek.start()).first()!!
        assertEquals(listOf("Aviso legado salvo anteriormente"), restoredLegacy.limitations)
        assertEquals(1, restoredLegacy.structuredLimitations.size)
        assertEquals(PurchaseCalculationStatus.APPROXIMATED, restoredLegacy.purchaseStatus)
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
    @Test fun migrationPreservesExistingManualListAndValidatesRoomSchema() = runBlocking {
        db.close()
        context.deleteDatabase("weekly-test.db")
        val path = listOf(java.io.File("schemas/br.com.bragasaude.data.local.BragaDatabase/50.json"),
            java.io.File("app/schemas/br.com.bragasaude.data.local.BragaDatabase/50.json")).first { it.exists() }
        val schema = org.json.JSONObject(path.readText()).getJSONObject("database")
        val config = androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(context)
            .name("weekly-test.db").callback(object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(50) {
                override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                    val entities = schema.getJSONArray("entities")
                    for (index in 0 until entities.length()) {
                        val entity = entities.getJSONObject(index)
                        db.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", entity.getString("tableName")))
                        val indices = entity.optJSONArray("indices")
                        if (indices != null) for (i in 0 until indices.length()) {
                            db.execSQL(indices.getJSONObject(i).getString("createSql").replace("\${TABLE_NAME}", entity.getString("tableName")))
                        }
                    }
                    val queries = schema.getJSONArray("setupQueries")
                    for (index in 0 until queries.length()) db.execSQL(queries.getString(index))
                    db.execSQL("INSERT INTO grocery_list_local(remoteId,userId,weekStartDate,foodId,foodName,category,suggestedServingWeekGrams,purchaseWeightGrams,purchaseUnitText,estimatedPriceBrl,isCheckedInPantry,createdAt) VALUES ('legacy','u','2026-10-05','a','Produto','Minha lista',0,1000,'1000 g',0,1,1)")
                }
                override fun onUpgrade(db: androidx.sqlite.db.SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            }).build()
        val helper = androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory().create(config)
        helper.writableDatabase
        helper.close()
        open()
        val migrated = db.groceryListDao().getGrocerySnapshot("u").single()
        assertTrue(migrated.isManual)
        assertTrue(migrated.isCheckedInPantry)
        assertEquals(1000, migrated.purchaseWeightGrams)
        assertEquals(0.0, migrated.plannedWeeklyAmount, 0.0)
        assertNull(repository.observe("u", GroceryWeek.start()).first())
    }

}
