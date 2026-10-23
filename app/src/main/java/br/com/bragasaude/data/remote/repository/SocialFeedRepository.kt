package br.com.bragasaude.data.remote.repository

import br.com.bragasaude.data.local.PostReactionEntity
import br.com.bragasaude.data.local.ProfileDao
import br.com.bragasaude.data.local.SocialFeedDao
import br.com.bragasaude.data.local.SocialPostEntity
import br.com.bragasaude.data.remote.api.BragaApiClient
import br.com.bragasaude.data.remote.sync.SyncScheduler
import br.com.bragasaude.data.util.withProfileAvatar
import br.com.bragasaude.data.util.sharedSocialPhotoUrl
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.Flow
import java.util.Date
import java.util.UUID
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import br.com.bragasaude.util.BragaConstants

@Singleton
class SocialFeedRepository @Inject constructor(
    private val socialFeedDao: SocialFeedDao,
    private val profileDao: ProfileDao,
    private val apiClient: BragaApiClient,
    private val syncScheduler: SyncScheduler,
    private val auth: com.google.firebase.auth.FirebaseAuth,
    private val familyDao: br.com.bragasaude.data.local.FamilyDao,
    private val avatarUploader: br.com.bragasaude.data.avatar.AvatarPhotoUploader
) {
    private val guestId = BragaConstants.GUEST_UID

    private fun requireSession(userId: String) {
        check(userId != guestId && auth.currentUser?.uid == userId) { "Sua conta mudou. Abra o mural novamente." }
    }

    fun getGlobalFeed(currentUserId: String? = null): Flow<List<SocialPostEntity>> {
        val posts = socialFeedDao.getGlobalFeed()
        if (currentUserId == null || currentUserId == guestId) return kotlinx.coroutines.flow.flowOf(emptyList())
        return combine(posts, profileDao.getProfile(currentUserId),
            familyDao.getActiveBindingsForPatient(currentUserId),
            familyDao.getActiveBindingsForCaregiver(currentUserId)) { feed, profile, caregivers, patients ->
            val familyAuthors = (caregivers + patients).filter { it.status == "ACTIVE" &&
                currentUserId in listOf(it.patientUserId, it.caregiverUserId) }
                .flatMap { listOf(it.patientUserId, it.caregiverUserId) }.toSet()
            feed.filter { post -> br.com.bragasaude.domain.canDisplayCachedSocialPost(post, currentUserId, familyAuthors) }
                .map { post -> if (post.userId == currentUserId) post.withProfileAvatar(profile) else post }
        }
    }

    suspend fun fetchGlobalFeed(limit: Int = 20, offset: Int = 0, currentUserId: String): Int {
        require(limit in 1..100 && offset == 0) { "Atualização do mural exige a primeira página, com até 100 publicações." }
        requireSession(currentUserId)
        val expected = socialFeedDao.getCachedPosts().associateBy { it.id }
        val posts = apiClient.getSocialFeed(currentUserId, limit)
        requireSession(currentUserId)
        if (posts.isNotEmpty()) {
            socialFeedDao.cacheRemotePosts(posts, expected, firstPageLimit = limit)
        }
        return posts.size
    }

    suspend fun refreshAuthorPhoto(userId: String) {
        if (userId == guestId) return
        requireSession(userId)
        val before = profileDao.getProfileOneShot(userId) ?: return
        if (before.avatarIdentifier != null) return
        val remote = apiClient.fetchProfilePhotoUrl(userId)
        requireSession(userId)
        val photo = sharedSocialPhotoUrl(remote) ?: return
        if (profileDao.getProfileOneShot(userId) == before) profileDao.updateAvatar(userId, null, photo)
    }

    /** Ação explícita do titular; a foto Google não é copiada na abertura do mural. */
    suspend fun shareGooglePhoto(userId: String) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        requireSession(userId)
        val photo = auth.currentUser?.photoUrl?.toString()
        val url = br.com.bragasaude.data.util.googlePhotoDownloadUrl(photo)
            ?: error("Escolha uma foto na galeria do perfil para compartilhar.")
        // Cliente sem interceptor de identidade: nunca envia o token Braga ao Google.
        val client = br.com.bragasaude.data.remote.network.GatewayTlsPolicy.defaultClient().newBuilder()
            .callTimeout(20, java.util.concurrent.TimeUnit.SECONDS).build()
        val bytes = client.newCall(okhttp3.Request.Builder().url(url).build()).execute().use { response ->
            check(response.isSuccessful) { "Não foi possível buscar sua foto Google." }
            val body = response.body ?: error("Foto indisponível.")
            val limit = 5 * 1024 * 1024
            require(body.contentLength() <= limit) { "A foto excede o limite de 5 MB." }
            body.byteStream().use { input ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    require(output.size() + read <= limit) { "A foto excede o limite de 5 MB." }
                    output.write(buffer, 0, read)
                }
                output.toByteArray()
            }
        }
        val result = avatarUploader.upload(userId, bytes)
        requireSession(userId)
        val shared = sharedSocialPhotoUrl(result.photoUrl)
            ?: error(result.detail ?: if (result.code == 422) "Foto não aprovada. Escolha outra imagem no perfil."
                else "Não foi possível compartilhar a foto agora. Tente novamente.")
        profileDao.updateAvatar(userId, null, shared)
    }

    fun getAuthorProfile(userId: String) = profileDao.getProfile(userId)

    suspend fun getCommunityNickname(userId: String): String {
        requireSession(userId)
        val local = profileDao.getProfileOneShot(userId)
        local?.communityNickname?.let { return it }
        val nickname = apiClient.getCommunityNickname()
        requireSession(userId)
        profileDao.updateCommunityNickname(userId, nickname)
        return nickname
    }

    suspend fun editPost(postId: String, userId: String, title: String, description: String) {
        requireSession(userId)
        val clean = title.trim()
        require(clean.isNotEmpty() && clean.length <= 200 && description.length <= 5000) { "Revise o título e o conteúdo da publicação." }
        socialFeedDao.queueMutation(postId, userId, clean, description.trim(), delete = false)
        syncScheduler.scheduleSync(androidx.work.ExistingWorkPolicy.APPEND_OR_REPLACE)
    }

    suspend fun deletePost(postId: String, userId: String) {
        requireSession(userId)
        socialFeedDao.queueMutation(postId, userId, null, null, delete = true)
        syncScheduler.scheduleSync(androidx.work.ExistingWorkPolicy.APPEND_OR_REPLACE)
    }

    fun reactionsForPost(postId: String) = socialFeedDao.getReactionsForPost(postId)

    suspend fun reactToPost(postId: String, userId: String, reactionType: String = "apoio"): Boolean {
        requireSession(userId)
        val reactionId = UUID.nameUUIDFromBytes((postId + ":" + userId).toByteArray()).toString()
        val userProfile = profileDao.getProfileOneShot(userId)

        val reactionEntity = PostReactionEntity(
            id = reactionId,
            postId = postId,
            userId = userId,
            userName = userProfile?.fullName?.takeIf { it.isNotBlank() } ?: "Você",
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

        if (userId == guestId) return false

        try {
            val ok = apiClient.reactToPost(postId, userId, reactionType)
            if (!ok) {
                socialFeedDao.insertReaction(reactionEntity.copy(pendingSync = true))
                triggerSync()
            }
            return ok
        } catch (e: Exception) {
            socialFeedDao.insertReaction(reactionEntity.copy(pendingSync = true))
            triggerSync()
            return false
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
        requireSession(userId)
        val cleanTitle = title.trim()
        require(cleanTitle.isNotEmpty() && cleanTitle.length <= 200 && (description?.length ?: 0) <= 5000) { "Revise o título e o conteúdo da publicação." }
        require(visibility in setOf("PUBLIC", "FAMILY")) { "Visibilidade inválida." }
        val userProfile = profileDao.getProfileOneShot(userId)
        val nickname = getCommunityNickname(userId)
        val name = br.com.bragasaude.domain.communityDisplayName(userName ?: userProfile?.fullName, nickname)
        val post = SocialPostEntity(
            id = UUID.randomUUID().toString(),
            userId = userId,
            userName = name,
            userAvatarUrl = sharedSocialPhotoUrl(userProfile?.customPhotoUri),
            userAvatarIdentifier = userProfile?.avatarIdentifier,
            postType = postType,
            title = cleanTitle,
            description = description,
            relatedMilestoneId = relatedMilestoneId,
            createdAt = Date(),
            isVisible = true,
            reactionCount = 0,
            hasUserReacted = false,
            pendingSync = true,
            visibility = visibility
        )

        // A publicação aparece imediatamente, mas só permanece se o servidor confirmar.
        requireSession(userId)
        socialFeedDao.insertPost(post)

        val remoteId = try {
            apiClient.syncSocialPost(post)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
        requireSession(userId)
        if (remoteId.isNullOrBlank()) {
            socialFeedDao.deletePostById(post.id)
            throw IOException("Não foi possível publicar agora. Tente novamente.")
        }

        if (remoteId != post.id) socialFeedDao.deletePostById(post.id)
        val finalPost = post.copy(id = remoteId, pendingSync = false)
        socialFeedDao.insertPost(finalPost)
        return finalPost
    }

    private fun triggerSync() {
        syncScheduler.scheduleSync()
    }
}
