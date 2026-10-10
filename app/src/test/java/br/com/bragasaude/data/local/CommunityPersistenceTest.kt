package br.com.bragasaude.data.local

import android.app.Application
import androidx.room.Room
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE, application = Application::class)
class CommunityPersistenceTest {
    private lateinit var db: BragaDatabase
    private val context get() = RuntimeEnvironment.getApplication()
    private val post = SocialPostEntity(id = "post", userId = "u", postType = "milestone", title = "Antes")
    private fun open() {
        db = Room.databaseBuilder(context, BragaDatabase::class.java, "community-test.db")
            .allowMainThreadQueries().addMigrations(*Migrations.ALL).build()
    }
    @Before fun setup() { context.deleteDatabase("community-test.db"); open() }
    @After fun cleanup() { db.close(); context.deleteDatabase("community-test.db") }

    @Test fun nicknameAndEditSurviveReopening() = runBlocking {
        db.profileDao().insert(ProfileEntity(userId = "u"))
        db.profileDao().updateCommunityNickname("u", "Colega")
        db.socialFeedDao().insertPost(post)
        db.socialFeedDao().queueMutation("post", "u", "Depois", "Texto", false)
        db.close(); open()
        assertEquals("Colega", db.profileDao().getProfileOneShot("u")!!.communityNickname)
        val saved = db.socialFeedDao().getGlobalFeed().first().single()
        assertEquals("Depois", saved.title)
        assertEquals("EDIT", saved.pendingMutation)
        assertNotNull(saved.editedAt)
    }

    @Test fun deletionIsImmediateAndSurvivesRefreshAndReopening() = runBlocking {
        val dao = db.socialFeedDao()
        dao.insertPost(post)
        dao.queueMutation("post", "u", null, null, true)
        assertTrue(dao.getGlobalFeed().first().isEmpty())
        dao.cacheRemotePosts(listOf(post), mapOf(post.id to post))
        val deleted = dao.getPostById(post.id)!!
        dao.completeMutation(deleted)
        dao.cacheRemotePosts(listOf(post), mapOf(post.id to deleted.copy(pendingMutation = "")))
        db.close(); open()
        assertTrue(db.socialFeedDao().getGlobalFeed().first().isEmpty())
        assertFalse(db.socialFeedDao().getPostById(post.id)!!.isVisible)
    }

    @Test fun lateRefreshCannotUndoEditAcknowledgedDuringRequest() = runBlocking {
        val dao = db.socialFeedDao()
        dao.insertPost(post)
        val expected = dao.getCachedPosts().associateBy { it.id }
        dao.queueMutation("post", "u", "Depois", "Texto", false)
        dao.completeMutation(dao.getPostById(post.id)!!)
        dao.cacheRemotePosts(listOf(post), expected)
        assertEquals("Depois", dao.getPostById(post.id)!!.title)
    }

    @Test fun acknowledgingEarlierEditPreservesNewerDeletion() = runBlocking {
        val dao = db.socialFeedDao()
        dao.insertPost(post)
        dao.queueMutation("post", "u", "Depois", "Texto", false)
        val earlier = dao.getPostById(post.id)!!
        dao.queueMutation("post", "u", null, null, true)
        dao.completeMutation(earlier)
        assertEquals("DELETE", dao.getPendingMutations().single().pendingMutation)
        assertTrue(dao.getGlobalFeed().first().isEmpty())
    }

    @Test fun profileSyncPreservesCachedNickname() = runBlocking {
        val profile = ProfileEntity(userId = "u", communityNickname = "Colega")
        db.profileDao().insert(profile)
        db.profileDao().cacheRemoteProfile(ProfileEntity(userId = "u", fullName = "Atualizado"), profile)
        val result = db.profileDao().getProfileOneShot("u")!!
        assertEquals("Colega", result.communityNickname)
        assertEquals("Atualizado", result.fullName)
    }

    @Test fun otherAuthorCannotQueueMutation() = runBlocking {
        db.socialFeedDao().insertPost(post)
        try {
            db.socialFeedDao().queueMutation("post", "other", null, null, true)
            fail("A autoria deve ser validada")
        } catch (_: IllegalArgumentException) { }
        assertTrue(db.socialFeedDao().getPostById(post.id)!!.isVisible)
        assertTrue(db.socialFeedDao().getPendingMutations().isEmpty())
    }
}
