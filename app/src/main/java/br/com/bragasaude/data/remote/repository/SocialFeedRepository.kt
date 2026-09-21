package br.com.bragasaude.data.remote.repository

import br.com.bragasaude.data.local.PostReactionEntity
import br.com.bragasaude.data.local.ProfileDao
import br.com.bragasaude.data.local.SocialFeedDao
import br.com.bragasaude.data.local.SocialPostEntity
import br.com.bragasaude.data.remote.api.BragaApiClient
import br.com.bragasaude.data.remote.sync.SyncScheduler
import kotlinx.coroutines.flow.Flow
import java.util.Date
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import br.com.bragasaude.util.BragaConstants

@Singleton
class SocialFeedRepository @Inject constructor(
    private val socialFeedDao: SocialFeedDao,
    private val profileDao: ProfileDao,
    private val apiClient: BragaApiClient,
    private val syncScheduler: SyncScheduler
) {
    private val guestId = BragaConstants.GUEST_UID

    fun getGlobalFeed(): Flow<List<SocialPostEntity>> = socialFeedDao.getGlobalFeed()

    suspend fun fetchGlobalFeed(limit: Int = 20, offset: Int = 0, currentUserId: String): Int {
        return try {
            val posts = apiClient.getSocialFeed(currentUserId, limit)
            if (posts.isNotEmpty()) {
                socialFeedDao.insertPosts(posts)
            }
            posts.size
        } catch (e: Exception) {
            android.util.Log.e("SocialFeedRepo", "Falha ao buscar feed global: ${e.message}")
            0
        }
    }

    fun reactionsForPost(postId: String) = socialFeedDao.getReactionsForPost(postId)

    suspend fun reactToPost(postId: String, userId: String, reactionType: String = "apoio") {
        val reactionId = UUID.nameUUIDFromBytes((postId + ":" + userId).toByteArray()).toString()
        val userProfile = profileDao.getProfileOneShot(userId)

        val reactionEntity = PostReactionEntity(
            id = reactionId,
            postId = postId,
            userId = userId,
            userName = userProfile?.fullName ?: "Você",
            reactionType = reactionType,
            createdAt = Date(),
            pendingSync = false
        )
        socialFeedDao.insertReaction(reactionEntity)

        // Optimistic update
        val existingPost = socialFeedDao.getPostById(postId)
        if (existingPost != null && !existingPost.hasUserReacted) {
            socialFeedDao.updatePostReactionState(
                postId = postId,
                count = existingPost.reactionCount + 1,
                hasReacted = true
            )
        }

        if (userId == guestId) return

        try {
            val ok = apiClient.reactToPost(postId, userId, reactionType)
            if (!ok) {
                socialFeedDao.insertReaction(reactionEntity.copy(pendingSync = true))
                triggerSync()
            }
        } catch (e: Exception) {
            socialFeedDao.insertReaction(reactionEntity.copy(pendingSync = true))
            triggerSync()
        }
    }

    suspend fun removeReaction(postId: String, userId: String) {
        socialFeedDao.deleteReaction(postId, userId)

        // Optimistic update
        val existingPost = socialFeedDao.getPostById(postId)
        if (existingPost != null && existingPost.hasUserReacted) {
            socialFeedDao.updatePostReactionState(
                postId = postId,
                count = maxOf(0, existingPost.reactionCount - 1),
                hasReacted = false
            )
        }
    }

    suspend fun createPost(
        userId: String,
        userName: String? = null,
        postType: String,
        title: String,
        description: String? = null,
        visibility: String = "PUBLIC",
        relatedMilestoneId: String? = null
    ): SocialPostEntity {
        val userProfile = profileDao.getProfileOneShot(userId)
        val name = if (!userName.isNullOrBlank()) userName else (userProfile?.fullName ?: "Você")
        val level = userProfile?.currentLevel ?: 1
        var remoteId: String? = null
        var isPending = true

        val post = SocialPostEntity(
            id = UUID.randomUUID().toString(),
            userId = userId,
            userName = name,
            userLevel = level,
            postType = postType,
            title = title,
            description = description,
            relatedMilestoneId = relatedMilestoneId,
            createdAt = Date(),
            isVisible = true,
            reactionCount = 0,
            hasUserReacted = false,
            pendingSync = true,
            visibility = visibility
        )

        if (userId != guestId) {
            try {
                remoteId = apiClient.syncSocialPost(post)
                if (remoteId != null) {
                    isPending = false
                }
            } catch (e: Exception) {
                android.util.Log.e("SocialFeedRepo", "Falha ao sincronizar post com servidor: ${e.message}")
            }
        }

        val finalPost = post.copy(
            id = remoteId ?: post.id,
            pendingSync = isPending
        )
        socialFeedDao.insertPost(finalPost)
        if (isPending && userId != guestId) {
            triggerSync()
        }
        return finalPost
    }

    private fun triggerSync() {
        syncScheduler.scheduleSync()
    }
}
