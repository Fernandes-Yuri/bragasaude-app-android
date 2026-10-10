package br.com.bragasaude.data.local

import android.app.Application
import android.database.sqlite.SQLiteDatabase
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
class CommunityMigrationTest {
    @Test fun migration52To53PreservesOldPostsAndAddsNullableEditDate() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val name = "community-migration.db"
        context.deleteDatabase(name)
        val relative = "schemas/br.com.bragasaude.data.local.BragaDatabase/52.json"
        val schema = sequenceOf(File(relative), File("app/$relative")).first { it.exists() }
        val json = JSONObject(schema.readText()).getJSONObject("database")
        val path = context.getDatabasePath(name).apply { parentFile?.mkdirs() }
        SQLiteDatabase.openOrCreateDatabase(path, null).use { database ->
            val entities = json.getJSONArray("entities")
            repeat(entities.length()) { i ->
                val entity = entities.getJSONObject(i)
                val table = entity.getString("tableName")
                database.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                val indexes = entity.optJSONArray("indices") ?: org.json.JSONArray()
                repeat(indexes.length()) { n ->
                    database.execSQL(indexes.getJSONObject(n).getString("createSql").replace("\${TABLE_NAME}", table))
                }
            }
            val setup = json.getJSONArray("setupQueries")
            repeat(setup.length()) { database.execSQL(setup.getString(it)) }
            database.execSQL("""INSERT INTO social_posts_local(id,userId,userLevel,postType,title,createdAt,isVisible,
                reactionCount,hasUserReacted,pendingSync,isFamilyPost,visibility)
                VALUES ('post','u',1,'milestone','Conquista',123,1,0,0,0,0,'PUBLIC')""")
            database.version = 52
        }
        val room = Room.databaseBuilder(context, BragaDatabase::class.java, name).allowMainThreadQueries()
            .addMigrations(*Migrations.ALL).build()
        try {
            val post = room.socialFeedDao().getPostById("post")!!
            assertEquals("Conquista", post.title)
            assertEquals(123L, post.createdAt.time)
            assertNull(post.editedAt)
            assertEquals("", post.pendingMutation)
            room.profileDao().insert(ProfileEntity(userId = "u"))
            assertNull(room.profileDao().getProfileOneShot("u")!!.communityNickname)
            room.profileDao().updateCommunityNickname("u", "Colega")
            assertEquals("Colega", room.profileDao().getProfileOneShot("u")!!.communityNickname)
        } finally { room.close(); context.deleteDatabase(name) }
    }
}
