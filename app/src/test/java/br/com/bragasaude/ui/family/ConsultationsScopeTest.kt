package br.com.bragasaude.ui.family

import androidx.lifecycle.ViewModelStore
import br.com.bragasaude.data.local.*
import br.com.bragasaude.data.remote.repository.FamilyBridgeRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import io.mockk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import java.util.Date

@OptIn(ExperimentalCoroutinesApi::class)
class ConsultationsScopeTest {
    @Test fun hybridPersonalAgendaStaysPersonalAndRevocationHidesPatientHistory() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val store = ViewModelStore()
        try {
            val auth = mockk<FirebaseAuth>(relaxed = true)
            val user = mockk<FirebaseUser>(relaxed = true)
            every { auth.currentUser } returns user
            every { user.uid } returns "owner"
            val dao = mockk<FamilyDao>(relaxed = true)
            val profiles = mockk<ProfileDao>(relaxed = true)
            val repository = mockk<FamilyBridgeRepository>(relaxed = true)
            fun appointment(owner: String) = ConsultationEntity(owner, owner, null, null, null,
                "Consulta", scheduledDate = Date(System.currentTimeMillis()+86400000), status = "ACCEPTED")
            val bindings = MutableStateFlow(listOf(FamilyBindingEntity(id = "binding", patientUserId = "patient",
                caregiverUserId = "owner", caregiverName = "Caregiver", caregiverRelation = "Familiar",
                connectionCode = "TEST1234", status = "ACTIVE", createdAt = 1, expiresAt = Long.MAX_VALUE)))
            every { dao.getActiveBindingsForCaregiver("owner") } returns bindings
            every { dao.getActiveConsultationsForUser("owner", any()) } returns flowOf(listOf(appointment("owner")))
            every { dao.getConsultationsWindow("patient", any(), any(), any()) } returns flowOf(listOf(appointment("patient")))
            every { dao.getConsultationsWindow("owner", any(), any(), any()) } returns flowOf(emptyList())
            val vm = ConsultationsViewModel(repository, dao, profiles, auth)
            store.put("test", vm)
            val personal = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.next.collect() }
            val history = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.records.collect() }
            runCurrent()
            vm.select("patient", "")
            runCurrent()
            assertEquals("owner", vm.next.value.single().userId)
            assertEquals("patient", vm.records.value.single().userId)
            bindings.value = emptyList()
            runCurrent()
            assertTrue(vm.records.value.isEmpty())
            assertEquals("owner", vm.next.value.single().userId)
            personal.cancel(); history.cancel()
        } finally { store.clear(); Dispatchers.resetMain() }
    }
}
