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

    @Test
    fun `refresh concorrente deduplica contexto e carteira em voo`() = runTest {
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
            val gate = CompletableDeferred<PartnerParticipantsPageDto?>()
            coEvery { api.getMyPartnerParticipants(any(), any(), any(), any(), any()) } coAnswers { gate.await() }
            val vm = PartnerWorkspaceViewModel(auth, mockk(relaxed = true), api, store, repo, mockk(relaxed = true))

            vm.refreshWorkspace()
            runCurrent()
            // Segunda chamada dentro da mesma janela de carga: deve sair sem nova rede.
            vm.refreshWorkspace()
            runCurrent()

            coVerify(exactly = 1) { repo.syncContexts("owner") }
            coVerify(exactly = 1) { api.getMyPartnerProgram() }
            coVerify(exactly = 1) { api.getMyPartnerParticipants(any(), any(), any(), any(), any()) }

            gate.complete(PartnerParticipantsPageDto(participants = listOf(PartnerParticipantItemDto("patient", "Paciente"))))
            runCurrent()
            assertEquals(1, vm.participants.value.size)
            assertFalse(vm.isLoading.value)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `falha do detalhe mantem participante selecionado e permite retry`() = runTest {
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
            coEvery { api.getMyPartnerParticipants(any(), any(), any(), any(), any()) } returns
                PartnerParticipantsPageDto(participants = listOf(PartnerParticipantItemDto("patient", "Paciente")))
            val vm = PartnerWorkspaceViewModel(auth, mockk(relaxed = true), api, store, repo, mockk(relaxed = true))
            vm.refreshWorkspace()
            runCurrent()

            coEvery { api.getPartnerParticipantSummary("patient", any(), any()) } returns null
            vm.openParticipantSummary("patient")
            runCurrent()

            assertEquals("patient", vm.selectedParticipantItem.value?.patientUserId)
            assertNull(vm.selectedSummary.value)
            assertNotNull(vm.detailError.value)
            assertNull(vm.statusMessage.value)
            assertFalse(vm.isLoadingSummary.value)

            coEvery { api.getPartnerParticipantSummary("patient", any(), any()) } returns ParticipantRoutineSummaryDto(
                programId = "p",
                programName = "Programa",
                companyName = "Empresa",
                patientUserId = "patient",
                participantName = "Paciente",
                periodDays = 30
            )
            vm.retrySelectedSummary()
            runCurrent()

            assertNull(vm.detailError.value)
            assertEquals("patient", vm.selectedSummary.value?.patientUserId)
            assertEquals("patient", vm.selectedParticipantItem.value?.patientUserId)
        } finally {
            Dispatchers.resetMain()
        }
    }
}
