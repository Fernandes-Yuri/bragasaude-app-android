package br.com.bragasaude.ui.family

import androidx.lifecycle.ViewModelStore
import br.com.bragasaude.data.local.*
import br.com.bragasaude.data.remote.repository.*
import com.google.firebase.auth.FirebaseAuth
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class FamilySessionTest {
    private data class Session(
        val vm: FamilyViewModel,
        val repo: FamilyBridgeRepository,
        val auth: FirebaseAuth,
        val listener: CapturingSlot<FirebaseAuth.AuthStateListener>
    )

    private suspend fun TestScope.withSession(block: suspend TestScope.(Session) -> Unit) {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = ViewModelStore()
        try {
            val auth = mockk<FirebaseAuth>(relaxed = true)
            every { auth.currentUser?.uid } returns "patient"
            val listener = slot<FirebaseAuth.AuthStateListener>()
            every { auth.addAuthStateListener(capture(listener)) } just Runs
            val repo = mockk<FamilyBridgeRepository>(relaxed = true)
            val binding = FamilyBindingEntity(id="link", patientUserId="patient", caregiverUserId="caregiver",
                caregiverName="Familiar", caregiverRelation="Filho", connectionCode="ABC12345", status="ACTIVE",
                createdAt=1L, expiresAt=Long.MAX_VALUE)
            every { repo.getQuickMessageTemplates() } returns emptyMap()
            every { repo.getActiveBindingsForPatient(any()) } returns flowOf(listOf(binding))
            every { repo.getActiveBindingsForCaregiver(any()) } returns flowOf(emptyList())
            every { repo.getAllActiveMessages() } returns flowOf(emptyList())
            every { repo.getRecentMessagesForPatient(any(), any()) } returns flowOf(emptyList())
            every { repo.getUnreadMessagesForPatient(any()) } returns flowOf(emptyList())
            every { repo.getGeneratedCodesForPatient(any()) } returns flowOf(emptyList())
            coEvery { repo.getBindingById("link") } returns binding
            coEvery { repo.createFamilyMessage(any(), any(), any(), any()) } answers {
                FamilyMessageEntity(id="sent", patientUserId=firstArg(), senderName=secondArg(),
                    messageText=thirdArg(), iconType=arg(3), senderUserId="patient")
            }
            coEvery { repo.chatCycleSnapshot(any(), any()) } returns ChatCycleSnapshot("unchanged")
            val vm = FamilyViewModel(mockk(relaxed=true), repo, mockk(relaxed=true), mockk(relaxed=true),
                mockk(relaxed=true), auth)
            store.put("family", vm)
            block(Session(vm, repo, auth, listener))
        } finally {
            store.clear()
            runCurrent()
            Dispatchers.resetMain()
        }
    }

    @Test fun repeatedPayloadKeepsThreeSecondCadenceAndLogoutClearsState() = runTest {
        withSession { (vm, repo, auth, listener) ->
            vm.setChatScreenVisible(true)
            runCurrent()
            coVerify(exactly=1) { repo.chatCycleSnapshot("patient", "patient") }
            advanceTimeBy(2_999)
            runCurrent()
            coVerify(exactly=1) { repo.chatCycleSnapshot("patient", "patient") }
            advanceTimeBy(1)
            runCurrent()
            coVerify(exactly=2) { repo.chatCycleSnapshot("patient", "patient") }
            repeat(3) { advanceTimeBy(3_000); runCurrent() }
            coVerify(exactly=5) { repo.chatCycleSnapshot("patient", "patient") }
            coVerify(exactly=1) { repo.syncBindingsForPatient("patient") }
            coVerify(exactly=1) { repo.syncBindingsForCaregiver("patient") }
            assertEquals(0, vm.chatSyncState.value.consecutiveFailures)
            assertNull(vm.chatSyncState.value.warning)
            assertEquals(1, vm.activeCaregivers.value.size)
            every { auth.currentUser } returns null
            listener.captured.onAuthStateChanged(auth)
            runCurrent()
            assertTrue(vm.activeCaregivers.value.isEmpty())
            assertTrue(vm.familyMessages.value.isEmpty())
            assertNull(vm.chatPatientId.value)
            assertFalse(vm.chatSyncState.value.isPolling)
        }
    }

    @Test fun hiddenChatDoesNotPollAndForegroundResumesWithoutDelay() = runTest {
        withSession { (vm, repo) ->
            runCurrent()
            coVerify(exactly=0) { repo.chatCycleSnapshot(any(), any()) }
            vm.setChatScreenVisible(true)
            runCurrent()
            coVerify(exactly=1) { repo.chatCycleSnapshot(any(), any()) }
            vm.setChatScreenVisible(false)
            runCurrent()
            advanceTimeBy(60_000)
            runCurrent()
            coVerify(exactly=1) { repo.chatCycleSnapshot(any(), any()) }
            assertFalse(vm.chatSyncState.value.isPolling)
            vm.setChatScreenVisible(true)
            runCurrent()
            coVerify(exactly=2) { repo.chatCycleSnapshot(any(), any()) }
            assertTrue(vm.chatSyncState.value.isHealthy)
        }
    }

    @Test fun realFailuresBackOffUpToSixtySecondsAndRecoveryClearsWarning() = runTest {
        withSession { (vm, repo) ->
            coEvery { repo.chatCycleSnapshot(any(), any()) } throws IOException("offline")
            vm.setChatScreenVisible(true)
            runCurrent()
            assertEquals(1, vm.chatSyncState.value.consecutiveFailures)
            assertNotNull(vm.chatSyncState.value.warning)
            listOf(6_000L, 12_000L, 24_000L, 48_000L, 60_000L).forEachIndexed { index, interval ->
                advanceTimeBy(interval - 1)
                runCurrent()
                coVerify(exactly=index + 1) { repo.chatCycleSnapshot(any(), any()) }
                advanceTimeBy(1)
                runCurrent()
                assertEquals(index + 2, vm.chatSyncState.value.consecutiveFailures)
            }
            assertTrue(vm.chatSyncState.value.isFailing)
            coEvery { repo.chatCycleSnapshot(any(), any()) } returns ChatCycleSnapshot("unchanged")
            advanceTimeBy(60_000)
            runCurrent()
            assertEquals(0, vm.chatSyncState.value.consecutiveFailures)
            assertNull(vm.chatSyncState.value.warning)
            advanceTimeBy(3_000)
            runCurrent()
            coVerify(exactly=8) { repo.chatCycleSnapshot(any(), any()) }
        }
    }

    @Test fun sendingTextOrEmojiImmediatelyInterruptsPollingAndBackoff() = runTest {
        withSession { (vm, repo) ->
            coEvery { repo.chatCycleSnapshot(any(), any()) } throws IOException("offline")
            vm.setChatScreenVisible(true)
            runCurrent()
            assertNotNull(vm.chatSyncState.value.warning)
            coEvery { repo.chatCycleSnapshot(any(), any()) } returns ChatCycleSnapshot("unchanged")
            listOf("Estou bem", "🙏").forEachIndexed { index, text ->
                var saved = false
                vm.sendMessageToGroup(text) { saved = it }
                runCurrent()
                assertTrue(saved)
                coVerify(exactly=1) { repo.createFamilyMessage("patient", any(), text, "CUSTOM") }
                coVerify(exactly=index + 2) { repo.chatCycleSnapshot(any(), any()) }
                assertEquals(0L, testScheduler.currentTime)
                assertNull(vm.chatSyncState.value.warning)
            }
        }
    }

    @Test fun sendDuringDownloadQueuesOneRefreshAndLeavingScreenCancelsDownload() = runTest {
        withSession { (vm, repo) ->
            var downloads = 0
            var maxDownloads = 0
            coEvery { repo.chatCycleSnapshot(any(), any()) } coAnswers {
                downloads++
                maxDownloads = maxOf(maxDownloads, downloads)
                try {
                    delay(1_000)
                    ChatCycleSnapshot("unchanged")
                } finally {
                    downloads--
                }
            }
            vm.setChatScreenVisible(true)
            runCurrent()
            repeat(2) { vm.sendMessageToGroup("Olá") {} }
            runCurrent()
            coVerify(exactly=1) { repo.chatCycleSnapshot(any(), any()) }
            advanceTimeBy(1_000)
            runCurrent()
            coVerify(exactly=2) { repo.chatCycleSnapshot(any(), any()) }
            assertEquals(1, maxDownloads)
            vm.setChatScreenVisible(false)
            runCurrent()
            assertEquals(0, downloads)
            assertFalse(vm.chatSyncState.value.isPolling)
            assertNull(vm.chatSyncState.value.warning)
        }
    }
}
