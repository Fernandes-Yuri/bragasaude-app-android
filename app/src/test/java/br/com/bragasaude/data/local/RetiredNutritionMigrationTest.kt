package br.com.bragasaude.data.local

import android.app.Application
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE, application = Application::class)
class RetiredNutritionMigrationTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private val name = "retired-nutrition.db"
    private val retired = listOf("food_catalog_local", "meal_rules_local", "grocery_list_local",
        "grocery_retail_cards_local", "grocery_publication_cache", "grocery_adopted_pack",
        "weekly_grocery_summary_local", "grocery_pantry_stock_local", "grocery_meal_local",
        "grocery_consumption_local")
    private fun open() = Room.databaseBuilder(context, BragaDatabase::class.java, name)
        .allowMainThreadQueries().addMigrations(*Migrations.ALL).build()
    @After fun cleanup() { context.deleteDatabase(name) }

    @Test fun upgrade62PreservesHealthAndDropsOnlyNutrition() = runBlocking<Unit> {
        context.deleteDatabase(name)
        val profile = ProfileEntity(userId = "u", fullName = "Pessoa", foodAllergies = listOf("leite"), pendingSync = true)
        val medication = MedicationEntity(id = "med", userId = "u", name = "Medicamento", currentUnits = 12)
        val initial = open()
        try {
            initial.profileDao().insert(profile)
            initial.medicationDao().insert(medication)
            initial.vitalSignDao().insert(VitalSignEntity(userId = "u", systolicPressure = 120, diastolicPressure = 80))
        } finally { initial.close() }
        // Reconstitui apenas os objetos retirados; Room valida todo o esquema após a migração.
        SQLiteDatabase.openDatabase(context.getDatabasePath(name).path, null, SQLiteDatabase.OPEN_READWRITE).use { old ->
            old.execSQL("ALTER TABLE profiles_local ADD COLUMN dailyCalorieTarget REAL")
            old.execSQL("UPDATE profiles_local SET dailyCalorieTarget=1800")
            retired.forEach { table ->
                old.execSQL("CREATE TABLE `$table` (id TEXT)")
                old.execSQL("INSERT INTO `$table` VALUES ('retired')")
            }
            val snapshotPath = "schemas/br.com.bragasaude.data.local.BragaDatabase/63.json"
            val snapshotFile = sequenceOf(java.io.File(snapshotPath), java.io.File("app/$snapshotPath")).first { it.isFile }
            val snapshotEntities = org.json.JSONObject(snapshotFile.readText()).getJSONObject("database").getJSONArray("entities")
            repeat(snapshotEntities.length()) { index ->
                val table = snapshotEntities.getJSONObject(index)
                val tableName = table.getString("tableName")
                if (tableName in setOf("post_reactions_local", "social_posts_local")) {
                    old.execSQL("DROP TABLE `$tableName`")
                    old.execSQL(table.getString("createSql").replace("\${TABLE_NAME}", tableName))
                }
            }
            old.version = 62
        }
        val db = open()
        try {
            assertEquals(profile, db.profileDao().getProfileOneShot("u"))
            assertEquals(medication, db.medicationDao().getAllSync("u").single())
            assertEquals(120, db.vitalSignDao().getAllOneShot("u").single().systolicPressure)
            retired.forEach { table ->
                db.openHelper.readableDatabase.query("SELECT name FROM sqlite_master WHERE type='table' AND name='$table'").use { assertFalse(it.moveToFirst()) }
            }
            db.openHelper.readableDatabase.query("PRAGMA table_info(profiles_local)").use { cursor ->
                while (cursor.moveToNext()) assertNotEquals("dailyCalorieTarget", cursor.getString(cursor.getColumnIndexOrThrow("name")))
            }
        } finally { db.close() }
    }

    @Test fun freshInstallHasNoNutritionTables() {
        context.deleteDatabase(name)
        val db = open()
        try {
            assertEquals(64, db.openHelper.readableDatabase.version)
            retired.forEach { table ->
                db.openHelper.readableDatabase.query("SELECT name FROM sqlite_master WHERE type='table' AND name='$table'").use { assertFalse(it.moveToFirst()) }
            }
        } finally { db.close() }
    }
}
