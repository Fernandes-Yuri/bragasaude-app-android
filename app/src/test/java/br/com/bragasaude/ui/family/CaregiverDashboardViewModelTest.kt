package br.com.bragasaude.ui.family

import android.content.Context
import br.com.bragasaude.data.local.*
import br.com.bragasaude.data.remote.repository.FamilyBridgeRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.Date

@OptIn(ExperimentalCoroutinesApi::class)
class CaregiverDashboardViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val context = mockk<Context>(relaxed = true)
    private val familyRepository = mockk<FamilyBridgeRepository>(relaxed = true)
    private val profileDao = mockk<ProfileDao>(relaxed = true)
    private val vitalSignDao = mockk<VitalSignDao>(relaxed = true)
    private val dailyMetricsDao = mockk<DailyMetricsDao>(relaxed = true)
    private val auth = mockk<FirebaseAuth>(relaxed = true)
    private val firebaseUser = mockk<FirebaseUser>(relaxed = true)

    private lateinit var viewModel: CaregiverDashboardViewModel
    private val bindingsFlow = kotlinx.coroutines.flow.MutableStateFlow<List<FamilyBindingEntity>>(emptyList())

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        every { auth.currentUser } returns firebaseUser
        every { firebaseUser.uid } returns "caregiver-1"

        every { familyRepository.getActiveBindingsForCaregiver("caregiver-1") } returns bindingsFlow
        every { familyRepository.getQuickMessageTemplates() } returns emptyMap()

        viewModel = CaregiverDashboardViewModel(
            context = context,
            familyRepository = familyRepository,
            profileDao = profileDao,
            vitalSignDao = vitalSignDao,
            dailyMetricsDao = dailyMetricsDao,
            auth = auth
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `selectPatient launches realtime vitals subscription and syncs data`() = runTest(testDispatcher) {
        val binding = FamilyBindingEntity(
            id = "bind-1",
            patientUserId = "patient-100",
            caregiverUserId = "caregiver-1",
            caregiverName = "Ana",
            caregiverRelation = "Filha",
            connectionCode = "CODE1234",
            status = "ACTIVE"
        )

        val profile = ProfileEntity(
            userId = "patient-100",
            fullName = "Dona Maria",
            hasDiabetes = true,
            hydrationTargetMl = 2000,
            stepGoal = 6000
        )

        val vitalSign = VitalSignEntity(
            remoteId = "vital-1",
            userId = "patient-100",
            systolicPressure = 120,
            diastolicPressure = 80,
            glucoseLevel = 110,
            measuredAt = Date()
        )

        every { profileDao.getProfile("patient-100") } returns flowOf(profile)
        every { vitalSignDao.getAll("patient-100") } returns flowOf(listOf(vitalSign))
        every { dailyMetricsDao.getRecent30Days("patient-100") } returns flowOf(emptyList())
        every { familyRepository.subscribePatientVitalSignsRealtime("patient-100") } returns flowOf(listOf(vitalSign))
        every { familyRepository.subscribeFamilyMessagesRealtime("patient-100") } returns flowOf(emptyList())

        bindingsFlow.value = listOf(binding)
        testScheduler.advanceUntilIdle()

        // Verifica que acionou sincronização e subscrição realtime
        coVerify(atLeast = 1) { familyRepository.syncPatientDataForCaregiver("patient-100") }
        verify(atLeast = 1) { familyRepository.subscribePatientVitalSignsRealtime("patient-100") }

        val dashboard = viewModel.dashboard.value
        assertEquals("patient-100", dashboard.patientUserId)
        assertEquals("Dona Maria", dashboard.patientName)
        assertEquals(120, dashboard.systolic)
        assertEquals(80, dashboard.diastolic)
        assertEquals(110, dashboard.glucose)
    }

    @Test
    fun `selectPatient observes active consultations for selected patient`() = runTest(testDispatcher) {
        val binding = FamilyBindingEntity(
            id = "bind-1",
            patientUserId = "patient-100",
            caregiverUserId = "caregiver-1",
            caregiverName = "Ana",
            caregiverRelation = "Filha",
            connectionCode = "CODE1234",
            status = "ACTIVE"
        )
        val consultation = ConsultationEntity(
            id = "consult-1",
            userId = "patient-100",
            caregiverUserId = "caregiver-1",
            caregiverName = "Ana",
            caregiverRelation = "Filha",
            title = "Cardiologista",
            scheduledDate = Date(System.currentTimeMillis() + 86400000L),
            status = "SUGGESTED"
        )

        every { familyRepository.getActiveConsultationsForUser("patient-100") } returns flowOf(listOf(consultation))
        bindingsFlow.value = listOf(binding)
        testScheduler.advanceUntilIdle()

        assertEquals(1, viewModel.upcomingConsultations.value.size)
        assertEquals("Cardiologista", viewModel.upcomingConsultations.value.first().title)
    }

    @Test
    fun `scheduleConsultation invokes repository and sends care message with calendar tag`() = runTest(testDispatcher) {
        val binding = FamilyBindingEntity(
            id = "bind-1",
            patientUserId = "patient-100",
            caregiverUserId = "caregiver-1",
            caregiverName = "Ana",
            caregiverRelation = "Filha",
            connectionCode = "CODE1234",
            status = "ACTIVE"
        )
        bindingsFlow.value = listOf(binding)
        testScheduler.advanceUntilIdle()

        val scheduledTime = System.currentTimeMillis() + 86400000L
        coEvery {
            familyRepository.createConsultation(
                userId = "patient-100",
                title = "Oftalmologista",
                scheduledDate = scheduledTime,
                caregiverUserId = "caregiver-1",
                caregiverName = any(),
                caregiverRelation = any()
            )
        } returns mockk()

        viewModel.scheduleConsultation("Oftalmologista", scheduledTime)
        testScheduler.advanceUntilIdle()

        coVerify(exactly = 1) {
            familyRepository.createConsultation(
                userId = "patient-100",
                title = "Oftalmologista",
                scheduledDate = scheduledTime,
                caregiverUserId = "caregiver-1",
                caregiverName = any(),
                caregiverRelation = any()
            )
        }

        coVerify(exactly = 1) {
            familyRepository.sendCareMessage(
                patientUserId = "patient-100",
                senderName = any(),
                messageText = match { it.contains("Consulta agendada: Oftalmologista") && it.contains("Lembretes ativos") },
                iconType = "CALENDAR"
            )
        }
    }

    @Test
    fun `cancelConsultation invokes repository cancelation`() = runTest(testDispatcher) {
        coEvery { familyRepository.cancelConsultation("consult-99", "caregiver-1") } returns true

        viewModel.cancelConsultation("consult-99")
        testScheduler.advanceUntilIdle()

        coVerify(exactly = 1) {
            familyRepository.cancelConsultation("consult-99", "caregiver-1")
        }
    }
}
