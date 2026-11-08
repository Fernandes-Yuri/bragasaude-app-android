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

    @Test
    fun homeSummaryUnifiesCaregiverAndWatchedPatientWithIdenticalConsultationId() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val store = ViewModelStore()
        try {
            val auth = mockk<FirebaseAuth>(relaxed = true)
            val user = mockk<FirebaseUser>(relaxed = true)
            every { auth.currentUser } returns user
            every { user.uid } returns "mariana"
            val dao = mockk<FamilyDao>(relaxed = true)
            val profiles = mockk<ProfileDao>(relaxed = true)
            val repository = mockk<FamilyBridgeRepository>(relaxed = true)

            val fredConsultation = ConsultationEntity(
                id = "c-fred-1",
                userId = "fred",
                bindingId = "bind-fred",
                createdByUserId = "mariana",
                createdByName = "Mariana",
                title = "Cardiologista",
                scheduledDate = Date(System.currentTimeMillis() + 3_600_000L),
                status = "SUGGESTED"
            )
            val bindings = MutableStateFlow(
                listOf(
                    FamilyBindingEntity(
                        id = "bind-fred",
                        patientUserId = "fred",
                        caregiverUserId = "mariana",
                        caregiverName = "Mariana",
                        caregiverRelation = "Filha",
                        connectionCode = "FRED1234",
                        status = "ACTIVE",
                        createdAt = 1L,
                        expiresAt = Long.MAX_VALUE
                    )
                )
            )
            every { dao.getActiveBindingsForCaregiver("mariana") } returns bindings
            every { dao.getActiveConsultationsForUser("mariana", any()) } returns flowOf(emptyList())
            every { dao.getActiveConsultationsForUser("fred", any()) } returns flowOf(listOf(fredConsultation))
            every { profiles.getProfile("fred") } returns flowOf(ProfileEntity(userId = "fred", fullName = "José Fred"))

            val vm = ConsultationsViewModel(repository, dao, profiles, auth)
            store.put("test-home-unify", vm)
            val personalJob = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.next.collect() }
            val summaryJob = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.homeSummary.collect() }
            runCurrent()

            // A agenda pessoal ("Minha agenda") permanece vazia para Mariana
            assertTrue(vm.next.value.isEmpty())

            // O resumo da Home exibe a mesma consulta de Fred vista em Cuidar, rotulada com o titular
            val summary = vm.homeSummary.value
            assertFalse(summary.loading)
            assertNull(summary.error)
            assertEquals(listOf("fred"), summary.watchedPatientIds)
            assertEquals(1, summary.items.size)
            val item = summary.items.single()
            assertEquals("c-fred-1", item.consultation.id)
            assertFalse(item.isPersonal)
            assertEquals("fred", item.holderUserId)
            assertEquals("José Fred", item.holderLabel)
            assertEquals("José Fred · Cardiologista", item.headerLine)
            assertEquals("fred", item.navigationPatientId)

            personalJob.cancel()
            summaryJob.cancel()
        } finally {
            store.clear()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun homeSummaryMergesOwnAndMultipleWatchedPatientsSortedAndLimitedToThree() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val store = ViewModelStore()
        try {
            val auth = mockk<FirebaseAuth>(relaxed = true)
            val user = mockk<FirebaseUser>(relaxed = true)
            every { auth.currentUser } returns user
            every { user.uid } returns "mariana"
            val dao = mockk<FamilyDao>(relaxed = true)
            val profiles = mockk<ProfileDao>(relaxed = true)
            val repository = mockk<FamilyBridgeRepository>(relaxed = true)

            val base = System.currentTimeMillis()
            val cFredEarly = ConsultationEntity(
                id = "c-fred-1h",
                userId = "fred",
                bindingId = "b-fred",
                createdByUserId = "mariana",
                createdByName = "Mariana",
                title = "Cardiologia",
                scheduledDate = Date(base + 3_600_000L),
                status = "ACCEPTED"
            )
            val cFredCancelled = ConsultationEntity(
                id = "c-fred-cancelled",
                userId = "fred",
                bindingId = "b-fred",
                createdByUserId = "mariana",
                createdByName = "Mariana",
                title = "Cancelada",
                scheduledDate = Date(base + 1_800_000L),
                status = "CANCELLED"
            )
            val cFredPast = ConsultationEntity(
                id = "c-fred-past",
                userId = "fred",
                bindingId = "b-fred",
                createdByUserId = "mariana",
                createdByName = "Mariana",
                title = "Passada",
                scheduledDate = Date(base - 3_600_000L),
                status = "ACCEPTED"
            )
            val cMarianaOwn = ConsultationEntity(
                id = "c-mariana-2h",
                userId = "mariana",
                bindingId = null,
                createdByUserId = "mariana",
                createdByName = "Mariana",
                title = "Oftalmologista",
                scheduledDate = Date(base + 7_200_000L),
                status = "SUGGESTED"
            )
            val cAnaMid = ConsultationEntity(
                id = "c-ana-3h",
                userId = "ana",
                bindingId = "b-ana",
                createdByUserId = "mariana",
                createdByName = "Mariana",
                title = "Endocrinologista",
                scheduledDate = Date(base + 10_800_000L),
                status = "ACCEPTED"
            )
            val cFredLate = ConsultationEntity(
                id = "c-fred-4h",
                userId = "fred",
                bindingId = "b-fred",
                createdByUserId = "mariana",
                createdByName = "Mariana",
                title = "Retorno",
                scheduledDate = Date(base + 14_400_000L),
                status = "ACCEPTED"
            )

            val bindings = MutableStateFlow(
                listOf(
                    FamilyBindingEntity(
                        id = "b-fred",
                        patientUserId = "fred",
                        caregiverUserId = "mariana",
                        caregiverName = "Mariana",
                        caregiverRelation = "Filha",
                        connectionCode = "FRED1234",
                        status = "ACTIVE",
                        createdAt = 1L,
                        expiresAt = Long.MAX_VALUE
                    ),
                    FamilyBindingEntity(
                        id = "b-ana",
                        patientUserId = "ana",
                        caregiverUserId = "mariana",
                        caregiverName = "Mariana",
                        caregiverRelation = "Sobrinha",
                        connectionCode = "ANA12345",
                        status = "ACTIVE",
                        createdAt = 2L,
                        expiresAt = Long.MAX_VALUE,
                        patientName = "Tia Ana"
                    )
                )
            )
            every { dao.getActiveBindingsForCaregiver("mariana") } returns bindings
            every { dao.getActiveConsultationsForUser("mariana", any()) } returns flowOf(listOf(cMarianaOwn))
            every { dao.getActiveConsultationsForUser("fred", any()) } returns flowOf(
                listOf(cFredLate, cFredEarly, cFredCancelled, cFredPast)
            )
            every { dao.getActiveConsultationsForUser("ana", any()) } returns flowOf(listOf(cAnaMid))
            every { profiles.getProfile("fred") } returns flowOf(ProfileEntity(userId = "fred", fullName = "José Fred"))
            every { profiles.getProfile("ana") } returns flowOf(null)

            val vm = ConsultationsViewModel(repository, dao, profiles, auth)
            store.put("test-home-multi", vm)
            val summaryJob = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.homeSummary.collect() }
            runCurrent()

            val items = vm.homeSummary.value.items
            assertEquals(3, items.size)
            assertEquals(listOf("c-fred-1h", "c-mariana-2h", "c-ana-3h"), items.map { it.consultation.id })
            assertEquals(
                listOf("José Fred · Cardiologia", "Você · Oftalmologista", "Tia Ana · Endocrinologista"),
                items.map { it.headerLine }
            )
            assertEquals(listOf("fred", "", "ana"), items.map { it.navigationPatientId })
            assertEquals(listOf(false, true, false), items.map { it.isPersonal })

            summaryJob.cancel()
        } finally {
            store.clear()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun revocationRemovesWatchedPatientFromHomeAndRebindingRestoresAndResyncs() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val store = ViewModelStore()
        try {
            val auth = mockk<FirebaseAuth>(relaxed = true)
            val user = mockk<FirebaseUser>(relaxed = true)
            every { auth.currentUser } returns user
            every { user.uid } returns "mariana"
            val dao = mockk<FamilyDao>(relaxed = true)
            val profiles = mockk<ProfileDao>(relaxed = true)
            val repository = mockk<FamilyBridgeRepository>(relaxed = true)

            val fredConsultation = ConsultationEntity(
                id = "c-fred-rebind",
                userId = "fred",
                bindingId = "b-fred",
                createdByUserId = "mariana",
                createdByName = "Mariana",
                title = "Geriatra",
                scheduledDate = Date(System.currentTimeMillis() + 3_600_000L),
                status = "ACCEPTED"
            )
            val activeBinding = FamilyBindingEntity(
                id = "b-fred",
                patientUserId = "fred",
                caregiverUserId = "mariana",
                caregiverName = "Mariana",
                caregiverRelation = "Filha",
                connectionCode = "FRED1234",
                status = "ACTIVE",
                createdAt = 1L,
                expiresAt = Long.MAX_VALUE
            )
            val bindings = MutableStateFlow(listOf(activeBinding))
            every { dao.getActiveBindingsForCaregiver("mariana") } returns bindings
            every { dao.getActiveConsultationsForUser("mariana", any()) } returns flowOf(emptyList())
            every { dao.getActiveConsultationsForUser("fred", any()) } returns flowOf(listOf(fredConsultation))
            every { profiles.getProfile("fred") } returns flowOf(ProfileEntity(userId = "fred", fullName = "José Fred"))

            val vm = ConsultationsViewModel(repository, dao, profiles, auth)
            store.put("test-home-rebind", vm)
            val summaryJob = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.homeSummary.collect() }
            val nextJob = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.next.collect() }
            runCurrent()

            assertEquals(listOf("c-fred-rebind"), vm.homeSummary.value.items.map { it.consultation.id })
            coVerify(atLeast = 1) { repository.syncConsultations("fred") }

            // Revogar vínculo remove imediatamente as consultas do familiar do resumo da Home
            clearMocks(repository, answers = false)
            bindings.value = emptyList()
            runCurrent()
            assertTrue(vm.homeSummary.value.items.isEmpty())
            assertTrue(vm.next.value.isEmpty())

            // Re-vincular o mesmo familiar dispara novo sync e restaura o resumo sem contaminar "Minha agenda"
            bindings.value = listOf(activeBinding.copy(id = "b-fred-2"))
            runCurrent()
            assertEquals(listOf("c-fred-rebind"), vm.homeSummary.value.items.map { it.consultation.id })
            assertTrue(vm.next.value.isEmpty())
            coVerify(atLeast = 1) { repository.syncConsultations("fred") }

            summaryJob.cancel()
            nextJob.cancel()
        } finally {
            store.clear()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun offlineRefreshPreservesCachedHomeSummaryAndExposesErrorWithoutFalseEmptyState() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val store = ViewModelStore()
        try {
            val auth = mockk<FirebaseAuth>(relaxed = true)
            val user = mockk<FirebaseUser>(relaxed = true)
            every { auth.currentUser } returns user
            every { user.uid } returns "mariana"
            val dao = mockk<FamilyDao>(relaxed = true)
            val profiles = mockk<ProfileDao>(relaxed = true)
            val repository = mockk<FamilyBridgeRepository>(relaxed = true)

            val cachedConsultation = ConsultationEntity(
                id = "c-cached-1",
                userId = "mariana",
                bindingId = null,
                createdByUserId = "mariana",
                createdByName = "Mariana",
                title = "Clínico Geral",
                scheduledDate = Date(System.currentTimeMillis() + 3_600_000L),
                status = "ACCEPTED"
            )
            every { dao.getActiveBindingsForCaregiver("mariana") } returns flowOf(emptyList())
            every { dao.getActiveConsultationsForUser("mariana", any()) } returns flowOf(listOf(cachedConsultation))
            coEvery { repository.syncConsultations("mariana") } throws java.io.IOException("Sem rede")

            val vm = ConsultationsViewModel(repository, dao, profiles, auth)
            store.put("test-home-offline", vm)
            val summaryJob = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.homeSummary.collect() }
            runCurrent()

            val state = vm.homeSummary.value
            assertFalse(state.loading)
            assertNotNull(state.error)
            assertEquals(listOf("c-cached-1"), state.items.map { it.consultation.id })
            assertEquals("Você · Clínico Geral", state.items.single().headerLine)

            summaryJob.cancel()
        } finally {
            store.clear()
            Dispatchers.resetMain()
        }
    }
}
