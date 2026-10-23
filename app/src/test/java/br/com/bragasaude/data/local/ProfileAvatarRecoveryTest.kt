package br.com.bragasaude.data.local

import android.app.Application
import androidx.room.Room
import br.com.bragasaude.data.remote.api.BragaApiClient
import br.com.bragasaude.data.remote.model.ProfileLookup
import br.com.bragasaude.data.remote.model.RemoteProfile
import br.com.bragasaude.data.remote.repository.ProfileRepository
import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE, application = Application::class)
class ProfileAvatarRecoveryTest {
    private lateinit var db: BragaDatabase
    private val photo = "https://api.bragasaude.online/api/files/avatars/owner/photo.jpg"
    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), BragaDatabase::class.java)
            .allowMainThreadQueries().build()
    }
    @After fun cleanup() { db.close() }

    @Test fun logoutClearsLocalPhotoAndLoginRecoversItFromProfileResponse() = runBlocking {
        db.profileDao().insert(ProfileEntity(userId = "owner", customPhotoUri = photo))
        db.clearAllTables()
        assertNull(db.profileDao().getProfileOneShot("owner"))
        val api = mockk<BragaApiClient>()
        coEvery { api.getProfileLookup("owner") } returns ProfileLookup.Found(RemoteProfile(id = "owner", photoUrl = photo))
        val repo = ProfileRepository(api, db.profileDao(), mockk(relaxed = true), mockk(relaxed = true))
        repo.refreshProfileForLogin("owner")
        assertEquals(photo, db.profileDao().getProfileOneShot("owner")!!.customPhotoUri)
        assertNull(db.profileDao().getProfileOneShot("other"))
    }

    @Test fun authoritativePhotoUpdatesAndRemovalPreserveNickname() = runBlocking {
        val before = ProfileEntity(userId = "owner", customPhotoUri = "old", communityNickname = "Colega")
        db.profileDao().insert(before)
        val fresh = before.copy(customPhotoUri = photo, communityNickname = null)
        db.profileDao().cacheRemoteProfile(fresh, before)
        assertEquals(photo, db.profileDao().getProfileOneShot("owner")!!.customPhotoUri)
        val expected = db.profileDao().getProfileOneShot("owner")!!
        db.profileDao().cacheRemoteProfile(fresh.copy(customPhotoUri = null), expected)
        val removed = db.profileDao().getProfileOneShot("owner")!!
        assertNull(removed.customPhotoUri)
        assertEquals("Colega", removed.communityNickname)
    }

    @Test fun lateRefreshCannotUndoLocalAvatarChangeOrPendingProfile() = runBlocking {
        val before = ProfileEntity(userId = "owner", customPhotoUri = photo)
        db.profileDao().insert(before)
        db.profileDao().updateAvatar("owner", "Star", null)
        db.profileDao().cacheRemoteProfile(before, before)
        assertEquals("Star", db.profileDao().getProfileOneShot("owner")!!.avatarIdentifier)
        assertNull(db.profileDao().getProfileOneShot("owner")!!.customPhotoUri)
        val pending = before.copy(pendingSync = true)
        db.profileDao().insert(pending)
        db.profileDao().cacheRemoteProfile(before.copy(customPhotoUri = null), pending)
        assertEquals(pending, db.profileDao().getProfileOneShot("owner"))
    }
}
