package br.com.bragasaude.ui.b2b

import br.com.bragasaude.data.remote.api.BragaApiClient
import br.com.bragasaude.data.remote.b2b.ActiveContextStore
import br.com.bragasaude.data.remote.b2b.B2BContextRepository
import br.com.bragasaude.domain.b2b.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import io.mockk.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PartnerWorkspaceIsolationTest {
    @Test
    fun `switch clears wallet and cancels late responses before personal session`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val store = ActiveContextStore()
            val owner = B2BContextDescriptor(mode = "PARTNER_OWNER", userId = "owner", programId = "p", approvalStatus = "APPROVED")
            store.updateFromEnvelope("owner", B2BContextsEnvelope(contexts = listOf(owner), accountMode = "DUAL"))
            store.setBusinessSessionActive(true)
            val user = mockk<FirebaseUser> { every { uid } returns "owner" }
            val auth = mockk<FirebaseAuth> { every { currentUser } returns user }
            val api = mockk<BragaApiClient>(relaxed = true)
            val repo = mockk<B2BContextRepository>(relaxed = true)
            coEvery { repo.syncContexts("owner") } returns listOf(owner)
            coEvery { api.getMyPartnerProgram() } returns PartnerProgramDto("p", "owner", "Empresa", "Programa", approvalStatus = "APPROVED")
            val page = PartnerParticipantsPageDto(participants = listOf(PartnerParticipantItemDto("patient", "Paciente")),
                kpis = PartnerWalletKpisDto(activeUsers = 1))
            coEvery { api.getMyPartnerParticipants(any(), any(), any(), any(), any()) } returns page
            val vm = PartnerWorkspaceViewModel(auth, mockk(relaxed = true), api, store, repo, mockk(relaxed = true))
            vm.refreshWorkspace()
            runCurrent()
            assertEquals(1, vm.participants.value.size)
            assertNotNull(vm.walletKpis.value)

            val delayed = CompletableDeferred<PartnerParticipantsPageDto?>()
            coEvery { api.getMyPartnerParticipants(any(), any(), any(), any(), any()) } coAnswers { delayed.await() }
            vm.refreshWorkspace()
            runCurrent()
            assertTrue(store.setBusinessSessionActive(false))
            assertTrue(vm.participants.value.isEmpty())
            assertNull(vm.walletKpis.value)
            assertNull(vm.programInfo.value)
            assertNull(vm.selectedSummary.value)
            assertNull(vm.activeDirectChatPage.value)
            delayed.complete(page)
            runCurrent()
            assertTrue(vm.participants.value.isEmpty())
            assertNull(vm.walletKpis.value)
            assertFalse(vm.isLoading.value)
        } finally {
            Dispatchers.resetMain()
        }
    }
}
