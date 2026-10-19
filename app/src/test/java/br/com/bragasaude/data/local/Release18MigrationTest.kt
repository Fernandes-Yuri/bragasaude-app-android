package br.com.bragasaude.data.local

import android.app.Application
import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE, application = Application::class)
class Release18MigrationTest {
    @Test fun release173DataSurvivesUpgradeTo18() = verifyUpgrade(54)
    @Test fun currentTestBuildDataAndCachedPhotosSurviveUpgradeTo18() = verifyUpgrade(57)

    private fun verifyUpgrade(version: Int) = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val name = "release18-migration-$version.db"
        context.deleteDatabase(name)
        val relative = "schemas/br.com.bragasaude.data.local.BragaDatabase/$version.json"
        val schema = sequenceOf(File(relative), File("app/$relative")).first { it.exists() }
        val json = JSONObject(schema.readText()).getJSONObject("database")
        val path = context.getDatabasePath(name).apply { parentFile?.mkdirs() }
        SQLiteDatabase.openOrCreateDatabase(path, null).use { database ->
            val entities = json.getJSONArray("entities")
            repeat(entities.length()) { i ->
                val entity = entities.getJSONObject(i)
                val table = entity.getString("tableName")
                database.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                val indices = entity.optJSONArray("indices") ?: JSONArray()
                repeat(indices.length()) { n ->
                    database.execSQL(indices.getJSONObject(n).getString("createSql").replace("\${TABLE_NAME}", table))
                }
                if (table == "profiles_local") {
                    val profile = ContentValues()
                    val fields = entity.getJSONArray("fields")
                    repeat(fields.length()) { n ->
                        val field = fields.getJSONObject(n)
                        if (field.getBoolean("notNull")) {
                            val column = field.getString("columnName")
                            when (field.getString("affinity")) {
                                "TEXT" -> profile.put(column, "")
                                "REAL" -> profile.put(column, 0.0)
                                else -> profile.put(column, 0)
                            }
                        }
                    }
                    profile.put("userId", "u")
                    profile.put("fullName", "Pessoa")
                    profile.put("foodAllergies", "[]")
                    database.insertOrThrow(table, null, profile)
                }
            }
            val setup = json.getJSONArray("setupQueries")
            repeat(setup.length()) { database.execSQL(setup.getString(it)) }
            database.execSQL("""INSERT INTO grocery_list_local(remoteId,userId,weekStartDate,foodId,foodName,category,
                suggestedServingWeekGrams,purchaseWeightGrams,purchaseUnitText,estimatedPriceBrl,isCheckedInPantry,createdAt)
                VALUES ('item','u','2026-10-12','rice','Arroz','Cereais',1000,1000,'1 kg',8.9,1,123)""")
            database.execSQL("INSERT INTO grocery_pantry_stock_local(userId,ingredientSlug,unit,availableAmount) VALUES ('u','rice','kg',1000)")
            if (version == 57) {
                database.execSQL("""INSERT INTO grocery_retail_cards_local(id,name,category,imageUrl,packageDisplay,
                    packageNetValue,packageUnit,kcalPerPackage,carbsG,proteinG,fatG,priceAvg,linkedIngredientSlug,marketsJson,clinicalTagsJson)
                    VALUES ('rice','Arroz','Cereais','https://example.org/rice.jpg','1000 g',1000,'g',3500,700,70,10,8.9,'rice','[]','[]')""")
            }
            database.version = version
        }
        val room = Room.databaseBuilder(context, BragaDatabase::class.java, name)
            .allowMainThreadQueries().addMigrations(*Migrations.ALL).build()
        try {
            val item = room.groceryListDao().getGrocerySnapshot("u").single()
            assertEquals("Arroz", item.foodName)
            assertEquals(8.9, item.estimatedPriceBrl, 0.001)
            assertTrue(item.isCheckedInPantry)
            assertEquals(7, item.planningDays)
            assertEquals("Pessoa", room.profileDao().getProfileOneShot("u")!!.fullName)
            assertEquals(1000.0, room.groceryPantryDao().stock("u", "rice")!!.availableAmount, 0.001)
            if (version == 57) {
                val card = room.groceryRetailCardDao().observe().first().single()
                assertEquals("https://example.org/rice.jpg", card.imageUrl)
                assertEquals("[]", card.nutritionFactsJson)
                assertNull(card.fiberG)
            }
        } finally { room.close(); context.deleteDatabase(name) }
    }
}
