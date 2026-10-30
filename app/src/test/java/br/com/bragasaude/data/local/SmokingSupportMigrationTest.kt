package br.com.bragasaude.data.local

import android.app.Application
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
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
class SmokingSupportMigrationTest {
    @Test fun exported59UpgradesWithEveryExistingTableAndSeedPreserved() {
        val context = RuntimeEnvironment.getApplication()
        val name = "smoking-migration-test.db"
        context.deleteDatabase(name)
        val relative = "schemas/br.com.bragasaude.data.local.BragaDatabase/59.json"
        val source = sequenceOf(File(relative), File("app/$relative")).first { it.isFile }
        val schema = JSONObject(source.readText()).getJSONObject("database")
        val entities = schema.getJSONArray("entities")
        val seeds = listOf("profiles_local", "vital_signs_local", "medications_local")
        try {
            context.openOrCreateDatabase(name, 0, null).use { old ->
                for (i in 0 until entities.length()) {
                    val entity = entities.getJSONObject(i)
                    val table = entity.getString("tableName")
                    old.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                    val indices = entity.optJSONArray("indices") ?: org.json.JSONArray()
                    for (j in 0 until indices.length()) old.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table))
                    if (table in seeds) {
                        val fields = entity.getJSONArray("fields")
                        val required = (0 until fields.length()).map { fields.getJSONObject(it) }.filter { it.optBoolean("notNull", false) }
                        val columns = required.joinToString(",") { "`${it.getString("columnName")}`" }
                        val values = required.joinToString(",") { if (it.getString("affinity") == "TEXT") "'fixture'" else "1" }
                        old.execSQL("INSERT INTO `$table` ($columns) VALUES ($values)")
                    }
                }
                old.version = 59
            }
            val upgraded = Room.databaseBuilder(context, BragaDatabase::class.java, name)
                .allowMainThreadQueries().addMigrations(*Migrations.ALL).build()
            try {
                // Opening invokes Room's full generated schema validation.
                val sql = upgraded.openHelper.writableDatabase
                assertEquals(65, sql.version)
                for (table in seeds) sql.query("SELECT COUNT(*) FROM `$table`").use { c ->
                    assertTrue(c.moveToFirst()); assertEquals(1, c.getInt(0))
                }
                for (i in 0 until entities.length()) {
                    val table = entities.getJSONObject(i).getString("tableName")
                    if (!table.startsWith("grocery_") && table != "food_catalog_local" && table != "meal_rules_local" && table != "weekly_grocery_summary_local")
                        sql.query("SELECT * FROM `$table` LIMIT 0").close()
                }
                sql.query("SELECT COUNT(*) FROM smoking_settings_local").use { c ->
                    c.moveToFirst(); assertEquals(0, c.getInt(0))
                }
            } finally { upgraded.close() }
        } finally { context.deleteDatabase(name) }
    }
}
