package br.com.bragasaude.ui.medication.doses

import br.com.bragasaude.data.medicationdoses.MedicationDoseRepository
import io.mockk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MedicationDoseContextTest {
    @Test fun resumeBeforeOpenNeverQueriesAnEmptyPatientAndRepeatedRefreshIsConsolidated() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repo = mockk<MedicationDoseRepository>(relaxed = true)
        every { repo.actor() } returns "owner"
        every { repo.groups(any()) } returns flowOf(emptyList())
        every { repo.intents(any()) } returns flowOf(emptyList())
        coEvery { repo.reservations(any()) } returns emptySet()
        val release = CompletableDeferred<Unit>()
        coEvery { repo.refresh("patient", any(), any()) } coAnswers { release.await() }
        try {
            val vm = MedicationDoseViewModel(repo)
            vm.refresh(); runCurrent()
            coVerify(exactly = 0) { repo.refresh(any(), any(), any()) }
            vm.open("patient"); vm.refresh(); vm.refresh(); runCurrent()
            coVerify(exactly = 1) { repo.refresh("patient", 7, null) }
            assertTrue(vm.state.value.busy)
            release.complete(Unit); runCurrent()
            assertTrue(vm.state.value.accessVerified)
            vm.open(""); runCurrent()
            assertFalse(vm.state.value.accessVerified)
            coVerify(exactly = 0) { repo.refresh("", any(), any()) }
        } finally { Dispatchers.resetMain() }
    }

    @Test fun anOldPatientOrAccountCannotPublishAccessIntoANewContext() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repo = mockk<MedicationDoseRepository>(relaxed = true)
        var actor = "owner"
        every { repo.actor() } answers { actor }
        every { repo.groups(any()) } returns flowOf(emptyList())
        every { repo.intents(any()) } returns flowOf(emptyList())
        coEvery { repo.reservations(any()) } returns emptySet()
        val release = CompletableDeferred<Unit>()
        coEvery { repo.refresh("A", any(), any()) } coAnswers { withContext(NonCancellable) { release.await() } }
        coEvery { repo.refresh("B", any(), any()) } returns Unit
        try {
            val vm = MedicationDoseViewModel(repo)
            vm.open("A"); runCurrent()
            vm.open("B"); runCurrent()
            assertTrue(vm.state.value.accessVerified)
            assertEquals("B", vm.state.value.patient)
            actor = "next-owner"
            vm.open("B"); runCurrent()
            release.complete(Unit); runCurrent()
            assertEquals("next-owner", vm.state.value.actor)
            assertEquals("B", vm.state.value.patient)
            assertFalse(vm.state.value.busy)
        } finally { Dispatchers.resetMain() }
    }
}
