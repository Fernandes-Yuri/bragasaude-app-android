package br.com.bragasaude.ui.family

import android.content.Context
import br.com.bragasaude.data.local.*
import br.com.bragasaude.data.remote.repository.FamilyBridgeRepository
import br.com.bragasaude.domain.b2b.FamilyProgramOverviewDto
import br.com.bragasaude.domain.b2b.FamilyProgramOverviewItemDto
import br.com.bragasaude.domain.b2b.FamilyReminderAcknowledgementDto
import br.com.bragasaude.domain.b2b.ProgramReminderDto
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import br.com.bragasaude.ui.util.NotificationHelper
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
    private val authListener = slot<FirebaseAuth.AuthStateListener>()

    private lateinit var viewModel: CaregiverDashboardViewModel
    private val bindingsFlow = kotlinx.coroutines.flow.MutableStateFlow<List<FamilyBindingEntity>>(emptyList())

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        every { auth.currentUser } returns firebaseUser
        every { firebaseUser.uid } returns "caregiver-1"
        every { auth.addAuthStateListener(capture(authListener)) } just Runs

        every { familyRepository.getActiveBindingsForCaregiver("caregiver-1") } returns bindingsFlow
        every { familyRepository.getQuickMessageTemplates() } returns emptyMap()
        coEvery { familyRepository.syncPatientDataForCaregiver(any()) } returns Result.success(Unit)
        coEvery { familyRepository.getFamilyProgramOverview(any()) } answers {
            FamilyProgramOverviewDto(firstArg())
        }

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

    private fun binding(patientId: String = "patient-100") = FamilyBindingEntity(
        id = "binding-$patientId", patientUserId = patientId, caregiverUserId = "caregiver-1",
        caregiverName = "Ana", caregiverRelation = "Filha", connectionCode = "FAMILY1234", status = "ACTIVE"
    )

    private fun sharedProgram(programId: String = "program-1") = FamilyProgramOverviewItemDto(
        programId = programId, programName = "Programa de rotina", familyShareEnabled = true,
        remindersEnabled = true,
        reminders = listOf(ProgramReminderDto(
            reminderId = "reminder-1", programId = programId, title = "Lembrete de rotina",
            body = "Confira seu registro de hoje.", createdAt = "2026-11-19T12:00:00Z"
        ))
    )

    @Test
    fun `shared programs load without access to personal vitals and disappear on revoked consent`() = runTest(testDispatcher) {
        coEvery { familyRepository.getFamilyProgramOverview("patient-100") } returns
            FamilyProgramOverviewDto("patient-100", listOf(sharedProgram()))
        coEvery { familyRepository.syncPatientDataForCaregiver(any()) } returns
            Result.failure(java.io.IOException("permission denied"))
        bindingsFlow.value = listOf(binding())
        advanceUntilIdle()
        assertEquals(listOf("program-1"), viewModel.partnerPrograms.value.map { it.programId })
        assertEquals(null, viewModel.lastCloudSync.value)

        coEvery { familyRepository.getFamilyProgramOverview("patient-100") } returns FamilyProgramOverviewDto("patient-100")
        viewModel.refreshPatientData()
        advanceUntilIdle()
        assertEquals(emptyList<FamilyProgramOverviewItemDto>(), viewModel.partnerPrograms.value)
    }

    @Test
    fun `failed overview clears previously authorized data`() = runTest(testDispatcher) {
        coEvery { familyRepository.getFamilyProgramOverview("patient-100") } returns
            FamilyProgramOverviewDto("patient-100", listOf(sharedProgram()))
        bindingsFlow.value = listOf(binding())
        advanceUntilIdle()
        assertEquals(1, viewModel.partnerPrograms.value.size)
        coEvery { familyRepository.getFamilyProgramOverview("patient-100") } throws java.io.IOException("403")
        viewModel.refreshPatientData()
        advanceUntilIdle()
        assertEquals(0, viewModel.partnerPrograms.value.size)
        org.junit.Assert.assertNotNull(viewModel.partnerProgramsError.value)
    }

    @Test
    fun `caregiver acknowledgement is scoped to selected patient and reloads server receipt`() = runTest(testDispatcher) {
        val program = sharedProgram()
        coEvery { familyRepository.getFamilyProgramOverview("patient-100") } returns
            FamilyProgramOverviewDto("patient-100", listOf(program))
        bindingsFlow.value = listOf(binding())
        advanceUntilIdle()
        viewModel.acknowledgePartnerReminder("another-program", "reminder-1")
        coVerify(exactly = 0) { familyRepository.acknowledgeFamilyProgramReminder(any(), any(), any()) }

        coEvery { familyRepository.acknowledgeFamilyProgramReminder("patient-100", "program-1", "reminder-1") } returns
            FamilyReminderAcknowledgementDto("2026-11-19T12:05:00Z", "caregiver-1")
        coEvery { familyRepository.getFamilyProgramOverview("patient-100") } returns FamilyProgramOverviewDto(
            "patient-100", listOf(program.copy(reminders = listOf(program.reminders.single().copy(
                acknowledgedAt = "2026-11-19T12:05:00Z", acknowledgedByUid = "caregiver-1"
            ))))
        )
        viewModel.acknowledgePartnerReminder("program-1", "reminder-1")
        viewModel.acknowledgePartnerReminder("program-1", "reminder-1")
        advanceUntilIdle()
        coVerify(exactly = 1) { familyRepository.acknowledgeFamilyProgramReminder("patient-100", "program-1", "reminder-1") }
        assertEquals("caregiver-1", viewModel.partnerPrograms.value.single().reminders.single().acknowledgedByUid)
        assertEquals(false, viewModel.acknowledgingPartnerReminder.value)
    }

    @Test
    fun `failed acknowledgement never displays success and clears inaccessible program`() = runTest(testDispatcher) {
        coEvery { familyRepository.getFamilyProgramOverview("patient-100") } returns
            FamilyProgramOverviewDto("patient-100", listOf(sharedProgram()))
        bindingsFlow.value = listOf(binding())
        advanceUntilIdle()
        coEvery { familyRepository.acknowledgeFamilyProgramReminder(any(), any(), any()) } throws java.io.IOException("403")
        val events = mutableListOf<FamilyUiEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiEvents.collect { events.add(it) } }
        viewModel.acknowledgePartnerReminder("program-1", "reminder-1")
        advanceUntilIdle()
        assertEquals(0, viewModel.partnerPrograms.value.size)
        assertEquals(false, viewModel.acknowledgingPartnerReminder.value)
        org.junit.Assert.assertTrue(events.single() is FamilyUiEvent.Error)
    }

    @Test
    fun `late response from previous selection cannot repopulate dashboard even after selecting same patient again`() = runTest(testDispatcher) {
        val delayed = kotlinx.coroutines.CompletableDeferred<FamilyProgramOverviewDto>()
        coEvery { familyRepository.getFamilyProgramOverview("patient-100") } coAnswers {
            kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) { delayed.await() }
        }
        bindingsFlow.value = listOf(binding(), binding("patient-200"))
        runCurrent()
        viewModel.selectPatient(binding("patient-200"))
        runCurrent()
        viewModel.selectPatient(binding())
        runCurrent()
        delayed.complete(FamilyProgramOverviewDto("patient-100", listOf(sharedProgram())))
        advanceUntilIdle()
        assertEquals(0, viewModel.partnerPrograms.value.size)
    }

    @Test
    fun `logout and removal of active family binding erase shared programs`() = runTest(testDispatcher) {
        coEvery { familyRepository.getFamilyProgramOverview("patient-100") } returns
            FamilyProgramOverviewDto("patient-100", listOf(sharedProgram()))
        bindingsFlow.value = listOf(binding())
        advanceUntilIdle()
        assertEquals(1, viewModel.partnerPrograms.value.size)
        bindingsFlow.value = emptyList()
        advanceUntilIdle()
        assertEquals(0, viewModel.partnerPrograms.value.size)
        bindingsFlow.value = listOf(binding())
        advanceUntilIdle()
        assertEquals(1, viewModel.partnerPrograms.value.size)
        every { auth.currentUser } returns null
        authListener.captured.onAuthStateChanged(auth)
        assertEquals(0, viewModel.partnerPrograms.value.size)
        assertEquals("", viewModel.dashboard.value.patientUserId)
    }

    @Test
    fun `visible dashboard polls every fifteen seconds and stops when cancelled`() = runTest(testDispatcher) {
        val binding = FamilyBindingEntity(
            id = "poll-binding", patientUserId = "patient-100", caregiverUserId = "caregiver-1",
            caregiverName = "Ana", caregiverRelation = "Filha", connectionCode = "POLL1234", status = "ACTIVE"
        )
        every { profileDao.getProfile("patient-100") } returns flowOf(null)
        every { vitalSignDao.getAll("patient-100") } returns flowOf(emptyList())
        every { dailyMetricsDao.getRecent30Days("patient-100") } returns flowOf(emptyList())
        bindingsFlow.value = listOf(binding)
        runCurrent()
        clearMocks(familyRepository, answers = false, recordedCalls = true)
        val polling = backgroundScope.launch { viewModel.pollPatientData() }
        runCurrent()
        coVerify(exactly = 1) { familyRepository.syncPatientDataForCaregiver("patient-100") }
        advanceTimeBy(15_000L)
        runCurrent()
        coVerify(exactly = 2) { familyRepository.syncPatientDataForCaregiver("patient-100") }
        polling.cancel()
        advanceTimeBy(30_000L)
        runCurrent()
        coVerify(exactly = 2) { familyRepository.syncPatientDataForCaregiver("patient-100") }
    }

    @Test
    fun `failed cloud download never publishes a successful timestamp`() = runTest(testDispatcher) {
        val binding = FamilyBindingEntity(
            id = "failure-binding", patientUserId = "patient-100", caregiverUserId = "caregiver-1",
            caregiverName = "Ana", caregiverRelation = "Filha", connectionCode = "FAIL1234", status = "ACTIVE"
        )
        every { profileDao.getProfile("patient-100") } returns flowOf(null)
        every { vitalSignDao.getAll("patient-100") } returns flowOf(emptyList())
        every { dailyMetricsDao.getRecent30Days("patient-100") } returns flowOf(emptyList())
        coEvery { familyRepository.syncPatientDataForCaregiver(any()) } returns Result.failure(java.io.IOException("offline"))
        bindingsFlow.value = listOf(binding)
        runCurrent()
        assertEquals(null, viewModel.lastCloudSync.value)
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
                messageText = match { it.contains("Consulta agendada: Oftalmologista") },
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

    @Test
    fun `reminder status uses logged in caregiver preference and device permission`() = runTest(testDispatcher) {
        val consultation = ConsultationEntity("c", "patient-100", "caregiver-1", "Ana", "Filha", "Consulta",
            scheduledDate = Date(System.currentTimeMillis() + 172800000), status = "ACCEPTED")
        mockkObject(NotificationHelper)
        try {
            every { profileDao.getProfile("caregiver-1") } returns flowOf(
                ProfileEntity(userId = "caregiver-1", notificationsEnabled = false))
            every { NotificationHelper.observeConsultationReminderTags(context, "c", "caregiver-1", consultation.scheduledDate.time) } returns
                flowOf(setOf("24h", "1h"))
            every { NotificationHelper.consultationNotificationsAllowed(context) } returns true
            assertEquals("Avisos desativados no app", viewModel.observeConsultationReminders(consultation).first().label)
            every { profileDao.getProfile("caregiver-1") } returns flowOf(
                ProfileEntity(userId = "caregiver-1", notificationsEnabled = true))
            every { NotificationHelper.consultationNotificationsAllowed(context) } returns false
            assertEquals("Avisos bloqueados no aparelho", viewModel.observeConsultationReminders(consultation).first().label)
            every { NotificationHelper.consultationNotificationsAllowed(context) } returns true
            assertEquals("Avisos: 24h e 1h antes", viewModel.observeConsultationReminders(consultation).first().label)
            assertEquals("Avisos não agendados", viewModel.observeConsultationReminders(consultation.copy(status = "CANCELLED")).first().label)
        } finally {
            unmockkObject(NotificationHelper)
        }
    }
}
