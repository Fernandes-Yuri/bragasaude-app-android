package br.com.bragasaude.ui.family

import androidx.lifecycle.ViewModelStore
import br.com.bragasaude.data.local.*
import br.com.bragasaude.data.remote.repository.*
import com.google.firebase.auth.FirebaseAuth
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FamilySessionTest {
    @Test fun repeatedSuccessfulPayloadDoesNotBecomeConnectionFailureAndLogoutClearsState() = runTest {
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
            coEvery { repo.chatCycleSnapshot(any(), any()) } returns ChatCycleSnapshot("unchanged")
            val vm = FamilyViewModel(mockk(relaxed=true), repo, mockk(relaxed=true), mockk(relaxed=true),
                mockk(relaxed=true), mockk(relaxed=true), auth)
            store.put("family", vm)
            vm.setChatScreenVisible(true)
            runCurrent()
            advanceTimeBy(15_001)
            runCurrent()
            coVerify(atLeast=2) { repo.chatCycleSnapshot("patient", "patient") }
            assertEquals(0, vm.chatSyncState.value.consecutiveFailures)
            assertNull(vm.chatSyncState.value.warning)
            assertEquals(1, vm.activeCaregivers.value.size)
            every { auth.currentUser } returns null
            listener.captured.onAuthStateChanged(auth)
            runCurrent()
            assertTrue(vm.activeCaregivers.value.isEmpty())
            assertTrue(vm.familyMessages.value.isEmpty())
            assertTrue(vm.groceryItems.value.isEmpty())
            assertNull(vm.chatPatientId.value)
        } finally {
            store.clear()
            Dispatchers.resetMain()
        }
    }
}
