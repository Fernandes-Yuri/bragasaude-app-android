package br.com.bragasaude.ui.profile

import androidx.lifecycle.ViewModelStore
import br.com.bragasaude.data.local.*
import br.com.bragasaude.data.remote.api.BragaApiClient
import br.com.bragasaude.data.remote.repository.*
import com.google.firebase.auth.FirebaseAuth
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CommunityNicknameTest {
    private val repository = mockk<ProfileRepository>()
    private val feedback = mockk<FeedbackRepository>()
    private val auth = mockk<FirebaseAuth>(relaxed = true)
    private val database = mockk<BragaDatabase>()
    private val dao = mockk<ProfileDao>()
    private val api = mockk<BragaApiClient>()
    private val local = MutableStateFlow<ProfileEntity?>(null)

    private fun model(nickname: String?): ProfileViewModel {
        local.value = ProfileEntity(userId = "u", communityNickname = nickname)
        every { auth.currentUser?.uid } returns "u"
        every { database.profileDao() } returns dao
        every { repository.getProfile("u") } returns local
        every { feedback.getUserFeedbacks("u") } returns flowOf(emptyList())
        coEvery { dao.getProfileOneShot("u") } answers { local.value }
        coEvery { dao.updateCommunityNickname("u", any()) } answers {
            local.value = local.value?.copy(communityNickname = secondArg())
        }
        return ProfileViewModel(repository, feedback, auth, database, api)
    }

    @Test fun `abrir perfil com apelido local nao chama rede`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = ViewModelStore()
        try {
            val vm = model("Colega"); store.put("vm", vm)
            repeat(3) { vm.loadProfile() }
            advanceUntilIdle()
            assertEquals("Colega", vm.communityNickname.value)
            coVerify(exactly = 0) { api.getCommunityNickname() }
            coVerify(exactly = 0) { repository.syncProfile(any()) }
        } finally { store.clear(); Dispatchers.resetMain() }
    }

    @Test fun `cache ausente consulta uma vez mesmo com chamadas simultaneas e guarda vazio`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = ViewModelStore()
        try {
            coEvery { api.getCommunityNickname() } returns ""
            val vm = model(null); store.put("vm", vm)
            repeat(3) { vm.loadCommunityNickname() }
            advanceUntilIdle()
            coVerify(exactly = 1) { api.getCommunityNickname() }
            assertEquals("", local.value!!.communityNickname)
            vm.loadCommunityNickname(); advanceUntilIdle()
            coVerify(exactly = 1) { api.getCommunityNickname() }
        } finally { store.clear(); Dispatchers.resetMain() }
    }

    @Test fun `edicao grava no Room antes de enviar e preserva valor anterior se falhar`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = ViewModelStore()
        try {
            coEvery { api.saveCommunityNickname("Novo") } coAnswers {
                assertEquals("Novo", local.value!!.communityNickname)
                throw java.io.IOException("Offline")
            }
            val vm = model("Colega"); store.put("vm", vm)
            vm.saveCommunityNickname("Novo") {}
            advanceUntilIdle()
            assertEquals("Colega", local.value!!.communityNickname)
            assertEquals("Offline", vm.communityError.value)
        } finally { store.clear(); Dispatchers.resetMain() }
    }
}
