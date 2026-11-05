package br.com.bragasaude.data.remote.repository

import br.com.bragasaude.data.avatar.AvatarPhotoUploader
import br.com.bragasaude.data.local.FamilyDao
import br.com.bragasaude.data.local.PostReactionEntity
import br.com.bragasaude.data.local.ProfileDao
import br.com.bragasaude.data.local.ProfileEntity
import br.com.bragasaude.data.local.SocialFeedDao
import br.com.bragasaude.data.remote.api.BragaApiClient
import br.com.bragasaude.data.remote.sync.SocialBlockSynchronizer
import br.com.bragasaude.data.remote.sync.SyncScheduler
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class SocialFeedRepositoryTest {

    private lateinit var socialFeedDao: SocialFeedDao
    private lateinit var profileDao: ProfileDao
    private lateinit var apiClient: BragaApiClient
    private lateinit var syncScheduler: SyncScheduler
    private lateinit var auth: FirebaseAuth
    private lateinit var firebaseUser: FirebaseUser
    private lateinit var familyDao: FamilyDao
    private lateinit var avatarUploader: AvatarPhotoUploader
    private lateinit var blockSynchronizer: SocialBlockSynchronizer

    private lateinit var repository: SocialFeedRepository

    private val testUserId = "user-test-123"

    @Before
    fun setUp() {
        socialFeedDao = mockk(relaxed = true)
        profileDao = mockk(relaxed = true)
        apiClient = mockk(relaxed = true)
        syncScheduler = mockk(relaxed = true)
        auth = mockk(relaxed = true)
        firebaseUser = mockk(relaxed = true)
        familyDao = mockk(relaxed = true)
        avatarUploader = mockk(relaxed = true)
        blockSynchronizer = mockk(relaxed = true)

        every { auth.currentUser } returns firebaseUser
        every { firebaseUser.uid } returns testUserId
        coEvery { profileDao.getProfileOneShot(testUserId) } returns ProfileEntity(userId = testUserId, fullName = "Usuário Teste")

        repository = SocialFeedRepository(
            socialFeedDao = socialFeedDao,
            profileDao = profileDao,
            apiClient = apiClient,
            syncScheduler = syncScheduler,
            auth = auth,
            familyDao = familyDao,
            avatarUploader = avatarUploader,
            blockSynchronizer = blockSynchronizer
        )
    }

    @Test
    fun reactToPost_success_callsApiAndCompletesReactionImmediately() = runBlocking {
        coEvery { apiClient.reactToPost("post-1", testUserId, "apoio") } returns true

        repository.reactToPost("post-1", testUserId, "apoio")

        coVerify(exactly = 1) { socialFeedDao.queueReaction(any(), any()) }
        coVerify(exactly = 1) { apiClient.reactToPost("post-1", testUserId, "apoio") }
        coVerify(exactly = 1) { socialFeedDao.completeReaction(any()) }
        verify(exactly = 0) { syncScheduler.scheduleSync(any()) }
    }

    @Test
    fun reactToPost_offlineOrFailed_schedulesWorkManagerFallback() = runBlocking {
        coEvery { apiClient.reactToPost("post-1", testUserId, "palmas") } returns false

        repository.reactToPost("post-1", testUserId, "palmas")

        coVerify(exactly = 1) { socialFeedDao.queueReaction(any(), any()) }
        coVerify(exactly = 1) { apiClient.reactToPost("post-1", testUserId, "palmas") }
        coVerify(exactly = 0) { socialFeedDao.completeReaction(any()) }
        verify(exactly = 1) { syncScheduler.scheduleSync(androidx.work.ExistingWorkPolicy.APPEND_OR_REPLACE) }
    }

    @Test
    fun reactToPost_exception_schedulesWorkManagerFallback() = runBlocking {
        coEvery { apiClient.reactToPost("post-1", testUserId, "forca") } throws java.io.IOException("Network down")

        repository.reactToPost("post-1", testUserId, "forca")

        coVerify(exactly = 1) { socialFeedDao.queueReaction(any(), any()) }
        coVerify(exactly = 1) { apiClient.reactToPost("post-1", testUserId, "forca") }
        coVerify(exactly = 0) { socialFeedDao.completeReaction(any()) }
        verify(exactly = 1) { syncScheduler.scheduleSync(androidx.work.ExistingWorkPolicy.APPEND_OR_REPLACE) }
    }

    @Test
    fun removeReaction_success_callsApiAndCompletesTombstone() = runBlocking {
        coEvery { apiClient.removePostReaction("post-1", testUserId) } returns true

        repository.removeReaction("post-1", testUserId)

        coVerify(exactly = 1) { socialFeedDao.queueReaction(match { it.pendingDelete }, any()) }
        coVerify(exactly = 1) { apiClient.removePostReaction("post-1", testUserId) }
        coVerify(exactly = 1) { socialFeedDao.completeReaction(any()) }
        verify(exactly = 0) { syncScheduler.scheduleSync(any()) }
    }

    @Test
    fun removeReaction_offline_schedulesWorkManagerFallback() = runBlocking {
        coEvery { apiClient.removePostReaction("post-1", testUserId) } returns false

        repository.removeReaction("post-1", testUserId)

        coVerify(exactly = 1) { socialFeedDao.queueReaction(match { it.pendingDelete }, any()) }
        coVerify(exactly = 1) { apiClient.removePostReaction("post-1", testUserId) }
        coVerify(exactly = 0) { socialFeedDao.completeReaction(any()) }
        verify(exactly = 1) { syncScheduler.scheduleSync(androidx.work.ExistingWorkPolicy.APPEND_OR_REPLACE) }
    }
}
