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

    @Test fun removalSurvivesRestartAndLateAcknowledgmentCannotEraseNewReaction() = runBlocking {
        val dao = db.socialFeedDao()
        dao.insertPost(post.copy(hasUserReacted = true, viewerReactionType = "apoio", reactionCount = 2))
        val removal = PostReactionEntity("remove", post.id, "u", pendingSync = true, pendingDelete = true)
        dao.queueReaction(removal)
        assertTrue(dao.getReactionsForPost(post.id).first().isEmpty())
        assertEquals(1, dao.getPostById(post.id)!!.reactionCount)
        val expected = dao.getCachedPosts().associateBy { it.id }
        dao.cacheRemotePosts(listOf(post.copy(hasUserReacted = true, reactionCount = 2)), expected, viewerId = "u")
        assertFalse(dao.getPostById(post.id)!!.hasUserReacted)
        db.close(); open()
        val reopened = db.socialFeedDao()
        assertEquals(removal, reopened.getPendingReactions().single())
        val newer = PostReactionEntity("newer", post.id, "u", reactionType = "palmas", pendingSync = true)
        reopened.queueReaction(newer)
        reopened.completeReaction(removal)
        assertEquals(newer, reopened.getPendingReactions().single())
        assertEquals("palmas", reopened.getPostById(post.id)!!.viewerReactionType)
        reopened.completeReaction(newer)
        assertTrue(reopened.getPendingReactions().isEmpty())
    }

    @Test fun refreshRestoresCanonicalReactionAndRemovalAcrossDevices() = runBlocking {
        val dao = db.socialFeedDao()
        dao.insertPost(post)
        val remote = post.copy(hasUserReacted = true, viewerReactionType = "forca", reactionCount = 1)
        dao.cacheRemotePosts(listOf(remote), mapOf(post.id to post), viewerId = "u")
        assertEquals("forca", dao.getReactionsForPost(post.id).first().single().reactionType)
        dao.cacheRemotePosts(listOf(post), mapOf(post.id to remote), viewerId = "u")
        assertTrue(dao.getReactionsForPost(post.id).first().isEmpty())
    }

    @Test fun acknowledgedRemovalDeletesTombstoneWithoutRestoringCount() = runBlocking {
        val dao = db.socialFeedDao()
        dao.insertPost(post.copy(hasUserReacted = true, reactionCount = 1))
        val removal = PostReactionEntity("remove", post.id, "u", pendingSync = true, pendingDelete = true)
        dao.queueReaction(removal)
        dao.completeReaction(removal)
        assertTrue(dao.getPendingReactions().isEmpty())
        assertEquals(0, dao.getPostById(post.id)!!.reactionCount)
        assertFalse(dao.getPostById(post.id)!!.hasUserReacted)
    }

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
    private fun cached(id: String, time: Long) = post.copy(id=id, createdAt=java.util.Date(time))

    @Test fun shorterConfirmedPageRemovesMissingPostsAndTheirSyncedReactions() = runBlocking {
        val dao = db.socialFeedDao()
        val removed = cached("removed", 10)
        val visible = cached("visible", 20)
        dao.insertPosts(listOf(removed, visible))
        dao.insertReaction(PostReactionEntity(id="reaction", postId=removed.id, userId="u"))
        val expected = dao.getCachedPosts().associateBy { it.id }
        dao.cacheRemotePosts(listOf(visible), expected, firstPageLimit=20)
        assertNull(dao.getPostById(removed.id))
        assertTrue(dao.getReactionsForPost(removed.id).first().isEmpty())
        db.close(); open()
        assertEquals(listOf(visible.id), db.socialFeedDao().getGlobalFeed().first().map { it.id })
    }

    @Test fun fullPageOnlyReconcilesNewerItemsAndPreservesBoundaryTiesAndOlderPages() = runBlocking {
        val dao = db.socialFeedDao()
        val newest = cached("newest", 100)
        val boundary = cached("boundary", 50)
        val removed = cached("removed", 75)
        val tied = cached("tied", 50)
        val older = cached("older", 25)
        dao.insertPosts(listOf(newest, boundary, removed, tied, older))
        dao.cacheRemotePosts(listOf(newest, boundary), dao.getCachedPosts().associateBy { it.id }, firstPageLimit=2)
        assertNull(dao.getPostById(removed.id))
        assertNotNull(dao.getPostById(tied.id))
        assertNotNull(dao.getPostById(older.id))
    }

    @Test fun emptyResponseNeverErasesCacheBecauseServerCanBeUnavailable() = runBlocking {
        val dao = db.socialFeedDao()
        dao.insertPost(post)
        dao.cacheRemotePosts(emptyList(), dao.getCachedPosts().associateBy { it.id }, firstPageLimit=20)
        assertEquals(post, dao.getPostById(post.id))
    }

    @Test fun confirmedEmptyFeedRemovesSyncedPostsButPreservesPendingLocalWork() = runBlocking {
        val dao = db.socialFeedDao()
        val synced = cached("deleted-author", 1)
        val pending = cached("pending", 2).copy(pendingSync = true)
        val edited = cached("edited", 3).copy(pendingMutation = "EDIT")
        dao.insertPosts(listOf(synced, pending, edited))
        dao.insertReaction(PostReactionEntity(id = "synced-reaction", postId = synced.id, userId = "u"))
        dao.cacheRemotePosts(emptyList(), dao.getCachedPosts().associateBy { it.id }, firstPageLimit = 20, confirmedEmpty = true)
        assertNull(dao.getPostById(synced.id))
        assertTrue(dao.getReactionsForPost(synced.id).first().isEmpty())
        assertEquals(pending, dao.getPostById(pending.id))
        assertEquals(edited, dao.getPostById(edited.id))
    }

    @Test fun reconciliationPreservesPostMutationsAndRemovesReactionsOfAbsentPosts() = runBlocking {
        val dao = db.socialFeedDao()
        val visible = cached("visible", 100)
        val edit = cached("edit", 50).copy(pendingMutation="EDIT")
        val create = cached("create", 50).copy(pendingSync=true)
        val deletion = cached("delete", 50).copy(pendingMutation="DELETE", isVisible=false)
        val reacting = cached("reacting", 50)
        dao.insertPosts(listOf(visible, edit, create, deletion, reacting))
        dao.insertReaction(PostReactionEntity(id="queued", postId=reacting.id, userId="u", pendingSync=true))
        dao.cacheRemotePosts(listOf(visible), dao.getCachedPosts().associateBy { it.id }, firstPageLimit=20)
        listOf(edit, create, deletion).forEach { assertEquals(it, dao.getPostById(it.id)) }
        assertNull(dao.getPostById(reacting.id))
        assertTrue(dao.getPendingReactions().isEmpty())
    }

    @Test fun lateResponsePreservesAcknowledgedEditAndNewPostCreatedDuringRequest() = runBlocking {
        val dao = db.socialFeedDao()
        val visible = cached("visible", 100)
        dao.insertPosts(listOf(post, visible))
        val expected = dao.getCachedPosts().associateBy { it.id }
        dao.queueMutation(post.id, "u", "Atualizado", "Texto", false)
        dao.completeMutation(dao.getPostById(post.id)!!)
        val added = cached("added-during-request", 200)
        dao.insertPost(added)
        dao.cacheRemotePosts(listOf(visible), expected, firstPageLimit=20)
        assertEquals("Atualizado", dao.getPostById(post.id)!!.title)
        assertEquals(added, dao.getPostById(added.id))
    }

    @Test fun remotePageDoesNotOverwritePendingLocalCreation() = runBlocking {
        val dao = db.socialFeedDao()
        val pending = post.copy(pendingSync=true)
        dao.insertPost(pending)
        dao.cacheRemotePosts(listOf(post.copy(title="Remoto")), mapOf(post.id to pending), firstPageLimit=20)
        assertEquals(pending, dao.getPostById(post.id))
    }

    @Test fun legacyLocalFamilyPostIsPreservedUntilItsPublishingFlowIsFixed() = runBlocking {
        val dao = db.socialFeedDao()
        val family = post.copy(isFamilyPost=true)
        val visible = cached("visible", 100)
        dao.insertPosts(listOf(family, visible))
        dao.cacheRemotePosts(listOf(visible), dao.getCachedPosts().associateBy { it.id }, firstPageLimit=20)
        assertEquals(family, dao.getPostById(family.id))
    }

    @Test fun invalidPageRollsBackUpdatesAndDeletesInOneTransaction() = runBlocking {
        val dao = db.socialFeedDao()
        dao.insertPost(post)
        val expected = mapOf(post.id to post)
        try {
            dao.cacheRemotePosts(listOf(post.copy(title="Não aplicar", createdAt=java.util.Date(10)),
                cached("newer-out-of-order", 100)), expected, firstPageLimit=2)
            fail("Página fora de ordem deve ser rejeitada")
        } catch (_: IllegalArgumentException) { }
        assertEquals(listOf(post), dao.getCachedPosts())
    }

}
