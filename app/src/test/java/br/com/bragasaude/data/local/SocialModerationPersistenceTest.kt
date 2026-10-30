package br.com.bragasaude.data.local

import android.app.Application
import androidx.room.Room
import kotlinx.coroutines.flow.first
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
class SocialModerationPersistenceTest {
    private val post = SocialPostEntity(id = "post", userId = "author", postType = "milestone", title = "Meta")

    @Test fun migration64PreservesPostsAndBlockSurvivesReopening() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val name = "social-moderation-migration.db"
        context.deleteDatabase(name)
        val relative = "schemas/br.com.bragasaude.data.local.BragaDatabase/64.json"
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
                }
                old.execSQL("""INSERT INTO social_posts_local(id,userId,userLevel,postType,title,createdAt,isVisible,
                    reactionCount,hasUserReacted,pendingSync,isFamilyPost,visibility)
                    VALUES ('post','author',1,'milestone','Meta preservada',123,1,0,0,0,0,'PUBLIC')""")
                old.version = 64
            }
            fun open() = Room.databaseBuilder(context, BragaDatabase::class.java, name)
                .allowMainThreadQueries().addMigrations(*Migrations.ALL).build()
            var db = open()
            try {
                assertEquals("Meta preservada", db.socialFeedDao().getPostById("post")!!.title)
                assertEquals(65, db.openHelper.readableDatabase.version)
                db.socialFeedDao().queueBlock("reader", "author")
            } finally { db.close() }
            db = open()
            try {
                assertTrue(db.socialFeedDao().getUnblockedFeed("reader").first().isEmpty())
                assertTrue(db.socialFeedDao().getBlocks("reader").single().pendingSync)
                assertEquals("post", db.socialFeedDao().getUnblockedFeed("another-account").first().single().id)
            } finally { db.close() }
        } finally { context.deleteDatabase(name) }
    }

    @Test fun pendingBlockIsImmediateScopedIdempotentAndSurvivesStaleRefresh() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), BragaDatabase::class.java)
            .allowMainThreadQueries().build()
        try {
            val dao = db.socialFeedDao()
            dao.insertPosts(listOf(post, post.copy(id = "older-post"), post.copy(id = "other", userId = "other-author")))
            dao.insertReaction(PostReactionEntity(id = "pending-reaction", postId = "post", userId = "reader", pendingSync = true))
            dao.queueBlock("reader", "author")
            dao.queueBlock("reader", "author")
            assertEquals(listOf("other"), dao.getUnblockedFeed("reader").first().map { it.id })
            assertEquals(3, dao.getUnblockedFeed("other-reader").first().size)
            assertEquals(1, dao.getBlocks("reader").size)
            assertTrue(dao.getPendingReactions().isEmpty())
            dao.reconcileBlocks("reader", emptySet())
            dao.insertPost(post.copy(title = "Resposta em voo"))
            assertEquals(listOf("other"), dao.getUnblockedFeed("reader").first().map { it.id })
            dao.acknowledgeBlock("reader", "author")
            dao.reconcileBlocks("reader", setOf("author"))
            assertFalse(dao.getBlocks("reader").single().pendingSync)
            dao.reconcileBlocks("reader", emptySet())
            assertEquals(3, dao.getUnblockedFeed("reader").first().size)
            try { dao.queueBlock("reader", "reader"); fail("Bloqueio da própria conta aceito") }
            catch (_: IllegalArgumentException) { /* esperado */ }
        } finally { db.close() }
    }
}
