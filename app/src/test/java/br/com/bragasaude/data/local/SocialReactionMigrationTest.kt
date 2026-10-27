package br.com.bragasaude.data.local

import android.app.Application
import androidx.room.Room
import kotlinx.coroutines.runBlocking
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
class SocialReactionMigrationTest {
    @Test fun published63PreservesPendingReactionsAndAddsSafeDefaults() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val name = "social-reaction-migration.db"
        context.deleteDatabase(name)
        val relative = "schemas/br.com.bragasaude.data.local.BragaDatabase/63.json"
        val source = sequenceOf(File(relative), File("app/$relative")).first { it.isFile }
        val entities = JSONObject(source.readText()).getJSONObject("database").getJSONArray("entities")
        try {
            context.openOrCreateDatabase(name, 0, null).use { old ->
                repeat(entities.length()) { i ->
                    val entity = entities.getJSONObject(i)
                    val table = entity.getString("tableName")
                    old.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                    val indices = entity.optJSONArray("indices") ?: org.json.JSONArray()
                    repeat(indices.length()) { j -> old.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table)) }
                    if (table in setOf("social_posts_local", "post_reactions_local")) {
                        val fields = entity.getJSONArray("fields")
                        val required = (0 until fields.length()).map { fields.getJSONObject(it) }.filter { it.optBoolean("notNull", false) }
                        val columns = required.joinToString(",") { "`${it.getString("columnName")}`" }
                        val values = required.joinToString(",") { if (it.getString("affinity") == "TEXT") "'fixture'" else "1" }
                        old.execSQL("INSERT INTO `$table` ($columns) VALUES ($values)")
                    }
                }
                old.version = 63
            }
            val db = Room.databaseBuilder(context, BragaDatabase::class.java, name)
                .allowMainThreadQueries().addMigrations(*Migrations.ALL).build()
            try {
                val reaction = db.socialFeedDao().getPendingReactions().single()
                assertEquals("fixture", reaction.id)
                assertFalse(reaction.pendingDelete)
                assertNull(db.socialFeedDao().getPostById("fixture")!!.viewerReactionType)
                assertEquals(64, db.openHelper.readableDatabase.version)
            } finally { db.close() }
        } finally { context.deleteDatabase(name) }
    }
}
