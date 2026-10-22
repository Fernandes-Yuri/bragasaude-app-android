package br.com.bragasaude.ui.components

import androidx.lifecycle.ViewModelStore
import br.com.bragasaude.data.remote.api.BragaApiClient
import br.com.bragasaude.data.remote.api.ProfileAvatarIdentity
import com.google.firebase.auth.FirebaseAuth
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ConnectedUserAvatarViewModelTest {
    @Test fun sharedPhotoRefreshesRemovalAndLogoutClearsOtherUsersAvatars() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = ViewModelStore()
        try {
            val auth = mockk<FirebaseAuth>(relaxed=true)
            every { auth.currentUser?.uid } returns "reader"
            val listener = slot<FirebaseAuth.AuthStateListener>()
            every { auth.addAuthStateListener(capture(listener)) } just Runs
            val api = mockk<BragaApiClient>()
            val photo = ProfileAvatarIdentity("https://api.bragasaude.online/api/files/avatars/author/f.jpg", null)
            coEvery { api.getProfileAvatar("author") } returns photo
            val vm = ConnectedUserAvatarViewModel(api, auth)
            store.put("avatar", vm)
            vm.load("author"); vm.load("author"); runCurrent()
            assertEquals(photo, vm.session.value.avatars["author"])
            coVerify(exactly=1) { api.getProfileAvatar("author") }
            coEvery { api.getProfileAvatar("author") } returns ProfileAvatarIdentity(null, "Star")
            vm.load("author", force=true); runCurrent()
            assertNull(vm.session.value.avatars["author"]!!.photoUrl)
            assertEquals("Star", vm.session.value.avatars["author"]!!.avatarIdentifier)
            every { auth.currentUser } returns null
            listener.captured.onAuthStateChanged(auth); runCurrent()
            assertTrue(vm.session.value.avatars.isEmpty())
            vm.load("author"); runCurrent()
            coVerify(exactly=2) { api.getProfileAvatar("author") }
        } finally { store.clear(); runCurrent(); Dispatchers.resetMain() }
    }
}
