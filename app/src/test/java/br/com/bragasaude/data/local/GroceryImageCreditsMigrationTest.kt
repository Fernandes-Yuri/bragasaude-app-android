package br.com.bragasaude.data.local

import android.app.Application
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
class GroceryImageCreditsMigrationTest {
    @Test fun exported60UpgradesTo61PreservingExistingCardsAndAddingCreditColumns() {
        val context = RuntimeEnvironment.getApplication()
        val name = "grocery-credit-migration-test.db"
        context.deleteDatabase(name)
        val relative = "schemas/br.com.bragasaude.data.local.BragaDatabase/60.json"
        val source = sequenceOf(File(relative), File("app/$relative")).first { it.isFile }
        val schema = JSONObject(source.readText()).getJSONObject("database")
        val entities = schema.getJSONArray("entities")
        val seeds = listOf("profiles_local", "vital_signs_local", "grocery_retail_cards_local")
        try {
            context.openOrCreateDatabase(name, 0, null).use { old ->
                for (i in 0 until entities.length()) {
                    val entity = entities.getJSONObject(i)
                    val table = entity.getString("tableName")
                    old.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                    val indices = entity.optJSONArray("indices") ?: org.json.JSONArray()
                    for (j in 0 until indices.length()) {
                        old.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table))
                    }
                    if (table in seeds) {
                        val fields = entity.getJSONArray("fields")
                        val required = (0 until fields.length()).map { fields.getJSONObject(it) }.filter { it.optBoolean("notNull", false) }
                        val columns = required.joinToString(",") { "`${it.getString("columnName")}`" }
                        val values = required.joinToString(",") { if (it.getString("affinity") == "TEXT") "'fixture'" else "1" }
                        old.execSQL("INSERT INTO `$table` ($columns) VALUES ($values)")
                    }
                }
                old.version = 60
            }
            val upgraded = Room.databaseBuilder(context, BragaDatabase::class.java, name)
                .allowMainThreadQueries().addMigrations(*Migrations.ALL).build()
            try {
                val sql = upgraded.openHelper.writableDatabase
                assertEquals(61, sql.version)

                // Verify pre-existing seed card survived with null credits
                sql.query("SELECT id, name, imageCredit, imageCreditUrl FROM grocery_retail_cards_local WHERE id = 'fixture'").use { c ->
                    assertTrue(c.moveToFirst())
                    assertEquals("fixture", c.getString(0))
                    assertEquals("fixture", c.getString(1))
                    assertNull(c.getString(2))
                    assertNull(c.getString(3))
                }

                // Verify inserting card with credits works seamlessly via SQLite
                sql.execSQL("""
                    INSERT INTO grocery_retail_cards_local (
                        id, name, category, imageUrl, packageDisplay, packageNetValue,
                        packageUnit, kcalPerPackage, carbsG, proteinG, fatG, priceAvg,
                        marketsJson, clinicalTagsJson, imageCredit, imageCreditUrl
                    ) VALUES (
                        'feijao-1kg', 'Feijão Preto', 'Grãos', 'https://example.org/feijao.jpg', '1 kg', 1.0,
                        'kg', 1300.0, 240.0, 88.0, 5.0, 9.50,
                        '[]', '[]', 'Foto por Autor / CC BY-SA 4.0', 'https://commons.wikimedia.org/wiki/File:Feijao.jpg'
                    )
                """.trimIndent())

                sql.query("SELECT imageCredit, imageCreditUrl FROM grocery_retail_cards_local WHERE id = 'feijao-1kg'").use { c ->
                    assertTrue(c.moveToFirst())
                    assertEquals("Foto por Autor / CC BY-SA 4.0", c.getString(0))
                    assertEquals("https://commons.wikimedia.org/wiki/File:Feijao.jpg", c.getString(1))
                }
            } finally {
                upgraded.close()
            }
        } finally {
            context.deleteDatabase(name)
        }
    }
}
