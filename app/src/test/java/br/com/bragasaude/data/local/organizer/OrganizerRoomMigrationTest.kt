package br.com.bragasaude.data.local.organizer

import android.app.Application
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import br.com.bragasaude.data.local.BragaDatabase
import br.com.bragasaude.data.local.Migrations
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
class OrganizerRoomMigrationTest {
    @Test fun opensRealSchema51WithRoom52AndPreservesVitals() {
        val context = RuntimeEnvironment.getApplication()
        val name = "organizer-migration.db"
        context.deleteDatabase(name)
        val relative = "schemas/br.com.bragasaude.data.local.BragaDatabase/51.json"
        val schema = sequenceOf(File(relative), File("app/$relative")).first { it.exists() }
        val json = JSONObject(schema.readText()).getJSONObject("database")
        val path = context.getDatabasePath(name).apply { parentFile?.mkdirs() }
        SQLiteDatabase.openOrCreateDatabase(path, null).use { database ->
            val entities = json.getJSONArray("entities")
            repeat(entities.length()) { i ->
                val entity = entities.getJSONObject(i)
                val table = entity.getString("tableName")
                database.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                val indexes = entity.getJSONArray("indices")
                repeat(indexes.length()) { n ->
                    database.execSQL(indexes.getJSONObject(n).getString("createSql").replace("\${TABLE_NAME}", table))
                }
            }
            val setup = json.getJSONArray("setupQueries")
            repeat(setup.length()) { database.execSQL(setup.getString(it)) }
            database.execSQL("INSERT INTO vital_signs_local(userId,measuredAt,status,pendingSync) VALUES ('u',123,'confirmed',0)")
            database.version = 51
        }
        try {
            Room.databaseBuilder(context, BragaDatabase::class.java, name).allowMainThreadQueries()
                .addMigrations(*Migrations.ALL).build().use { room ->
                    room.openHelper.writableDatabase.query("SELECT userId,measuredAt FROM vital_signs_local").use {
                        assertTrue(it.moveToFirst()); assertEquals("u", it.getString(0)); assertEquals(123, it.getInt(1))
                    }
                    room.openHelper.writableDatabase.query("SELECT name FROM sqlite_master WHERE type='table' AND name IN ('exams_local','exam_items_local','clinical_references_local')").use {
                        assertEquals(0, it.count)
                    }
                }
        } finally { context.deleteDatabase(name) }
    }
}
