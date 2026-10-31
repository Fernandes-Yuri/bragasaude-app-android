package br.com.bragasaude.domain

import android.content.Context
import br.com.bragasaude.R
import br.com.bragasaude.data.local.BiometryEntity
import br.com.bragasaude.data.local.DailyMetricsEntity
import br.com.bragasaude.data.local.MilestoneEntity
import br.com.bragasaude.data.local.ProfileEntity
import br.com.bragasaude.data.remote.model.RemoteBiometry
import br.com.bragasaude.data.remote.model.RemoteVitalSign
import br.com.bragasaude.data.remote.repository.*
import com.google.firebase.auth.FirebaseAuth
import io.mockk.*
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.*
import java.time.ZonedDateTime

class HealthEngineTest {

    private val context = mockk<Context>(relaxed = true)
    private val vitalsRepository = mockk<VitalsRepository>()
    private val milestonesRepository = mockk<MilestonesRepository>()
    private val conditionRepository = mockk<ConditionRepository>()
    private val profileRepository = mockk<ProfileRepository>()
    private val biometryRepository = mockk<BiometryRepository>()
    private val riskManager = mockk<RiskManager>(relaxed = true)
    private val auth = mockk<FirebaseAuth>(relaxed = true)

    private lateinit var healthEngine: HealthEngine

    @Before
    fun setup() {
        healthEngine = HealthEngine(
            context, vitalsRepository,
            milestonesRepository, conditionRepository,
            profileRepository, biometryRepository,
            riskManager, auth
        )

        every { auth.currentUser } returns null
        coEvery { conditionRepository.saveDetectedCondition(any()) } just Runs
        coEvery { milestonesRepository.saveMilestone(any()) } just Runs
        coEvery { milestonesRepository.getMilestones(any()) } returns flowOf(emptyList())

        // Mock strings default
        every { context.getString(any()) } returns "Mock String"
        every { context.getString(any(), *anyVararg()) } answers { "Mock String with Args ${args.drop(1).joinToString()}" }
    }

    @Test
    fun `sedentary alert preserves the complete movement guidance`() = runBlocking {
        val now = ZonedDateTime.parse("2026-10-31T14:00:00-03:00")
        val action = "Você está em repouso há algum tempo. Que tal uma breve caminhada ou alongamento?"
        every { context.getString(R.string.sedentary_alert) } returns "Momento de Movimento"
        every { context.getString(R.string.sedentary_msg) } returns action
        every { profileRepository.getProfile("user1") } returns flowOf(null)

        val recommendation = healthEngine.analyzeActivityPatterns(
            "user1", listOf(DailyMetricsEntity("user1", "2026-10-31", steps = 499)), now
        ).single()

        assertEquals("Momento de Movimento\n\n$action", recommendation.alertText)
        assertFalse(recommendation.isAchievement)
    }

    @Test
    fun `activity achievement preserves guidance and the personal step goal`() = runBlocking {
        val now = ZonedDateTime.parse("2026-10-31T14:00:00-03:00")
        every { context.getString(R.string.activity_goal_title) } returns "Meta de Passos Alcançada!"
        every { context.getString(R.string.activity_goal_msg, 6000) } returns "Parabéns! Você atingiu sua meta de 6000 passos hoje."
        every { profileRepository.getProfile("user1") } returns flowOf(ProfileEntity(userId = "user1", stepGoal = 6000))

        val recommendation = healthEngine.analyzeActivityPatterns(
            "user1", listOf(DailyMetricsEntity("user1", "2026-10-31", steps = 6000)), now
        ).single()

        assertEquals("Meta de Passos Alcançada!\n\nParabéns! Você atingiu sua meta de 6000 passos hoje.", recommendation.alertText)
        assertTrue(recommendation.isAchievement)
    }

    @Test
    fun `sedentary alerts still require current low steps after ten`() = runBlocking {
        val now = ZonedDateTime.parse("2026-10-31T14:00:00-03:00")
        every { profileRepository.getProfile("user1") } returns flowOf(null)
        val today = DailyMetricsEntity("user1", "2026-10-31", steps = 499)

        assertTrue(healthEngine.analyzeActivityPatterns("user1", listOf(today), now.withHour(10)).isEmpty())
        assertTrue(healthEngine.analyzeActivityPatterns("user1", listOf(today.copy(steps = 500)), now).isEmpty())
        assertTrue(healthEngine.analyzeActivityPatterns("user1", listOf(today.copy(date = "2026-10-30")), now).isEmpty())
        assertTrue(healthEngine.analyzeActivityPatterns("user1", emptyList(), now).isEmpty())
    }

    @Test
    fun `vital alert retains the explanation instead of only the measured value`() = runBlocking {
        coEvery { vitalsRepository.getVitalSignsSync("user1") } returns emptyList()
        every { context.getString(R.string.bp_high, 145) } returns "Pressão sistólica: 145 mmHg"
        every { context.getString(R.string.bp_high_action) } returns "Repouse e registre uma nova medida."

        val result = healthEngine.analyzeVitals(
            "user1", listOf(RemoteVitalSign(userId = "user1", systolicPressure = 145)), silent = true
        )

        assertEquals(listOf("Pressão sistólica: 145 mmHg\n\nRepouse e registre uma nova medida."), result.alerts)
    }

    @Test
    fun `hydration saves once and silent analysis never consumes achievement`() = runBlocking {
        val user = "user1"
        val saved = mutableListOf<MilestoneEntity>()
        val firebaseUser = mockk<com.google.firebase.auth.FirebaseUser>()
        every { auth.currentUser } returns firebaseUser
        every { firebaseUser.uid } returns user
        coEvery { vitalsRepository.getVitalSignsSync(user) } returns emptyList()
        coEvery { profileRepository.getProfile(user) } returns flowOf(ProfileEntity(userId = user, hydrationTargetMl = 2625))
        var total = 1000
        coEvery { vitalsRepository.getVitalSigns(user) } answers { flowOf(listOf(
            br.com.bragasaude.data.local.VitalSignEntity(userId = user, hydrationMl = total, measuredAt = Date())
        )) }
        coEvery { milestonesRepository.getMilestones(user) } answers { flowOf(saved.toList()) }
        coEvery { milestonesRepository.saveMilestone(any()) } coAnswers {
            val milestone = firstArg<br.com.bragasaude.data.remote.model.RemoteMilestone>()
            kotlinx.coroutines.yield()
            saved.add(MilestoneEntity(remoteId = requireNotNull(milestone.id), userId = user,
                title = milestone.title, description = milestone.description, badgeType = milestone.badgeType))
        }
        mockkObject(br.com.bragasaude.data.notification.HydrationNotifications)
        try {
            every { br.com.bragasaude.data.notification.HydrationNotifications.notifyOnce(any(), any(), any(), any(), any()) } returns true
            val water = listOf(RemoteVitalSign(userId = user, hydrationMl = 250))
            healthEngine.analyzeVitals(user, water)
            assertTrue(saved.isEmpty())
            verify(exactly = 0) { br.com.bragasaude.data.notification.HydrationNotifications.notifyOnce(any(), any(), any(), any(), any()) }
            total = 2625
            healthEngine.analyzeVitals(user, water, silent = true)
            assertTrue(saved.isEmpty())
            listOf(async { healthEngine.analyzeVitals(user, water) }, async { healthEngine.analyzeVitals(user, water) }).awaitAll()
            assertEquals(1, saved.size)
            verify(exactly = 1) { br.com.bragasaude.data.notification.HydrationNotifications.notifyOnce(any(), user, any(), any(), any()) }
        } finally {
            unmockkObject(br.com.bragasaude.data.notification.HydrationNotifications)
        }
    }

    @Test
    fun `moderate systolic must not suppress critical diastolic heart rate or glucose`() = runBlocking {
        coEvery { vitalsRepository.getVitalSignsSync("user1") } returns emptyList()
        val cases = listOf(
            RemoteVitalSign(userId = "user1", systolicPressure = 145, diastolicPressure = 125),
            RemoteVitalSign(userId = "user1", systolicPressure = 145, heartRate = 140),
            RemoteVitalSign(userId = "user1", heartRate = 110, glucoseLevel = 45)
        )
        cases.forEach { vital ->
            val result = healthEngine.analyzeVitals("user1", listOf(vital), silent = true)
            assertTrue(result.recommendations.first().isEmergency)
        }
    }

    @Test
    fun `multiple emergencies are retained and canonical pressure is not multiplied`() = runBlocking {
        coEvery { vitalsRepository.getVitalSignsSync("user1") } returns emptyList()
        every { context.getString(R.string.bp_diastolic_high_crisis, 125) } returns "diastolic"
        every { context.getString(R.string.glucose_hypo_severe, 45) } returns "glucose"
        val result = healthEngine.analyzeVitals("user1", listOf(
            RemoteVitalSign(userId = "user1", systolicPressure = 25, diastolicPressure = 125, glucoseLevel = 45)
        ), silent = true)
        assertTrue(result.recommendations.first().message.contains("diastolic"))
        assertTrue(result.recommendations.first().message.contains("glucose"))
        verify(exactly = 0) { context.getString(R.string.bp_high_crisis, 250) }
    }

    @Test
    fun `analyzeVitals should detect high BP crisis`() = runBlocking {
        val userId = "user1"
        val vital = RemoteVitalSign(userId = userId, systolicPressure = 185)

        coEvery { vitalsRepository.getVitalSignsSync(userId) } returns emptyList()
        coEvery { vitalsRepository.getVitalSigns(userId) } returns flowOf(emptyList())
        coEvery { profileRepository.getProfile(userId) } returns flowOf(null)
        coEvery { milestonesRepository.getMilestones(userId) } returns flowOf(emptyList())

        val result = healthEngine.analyzeVitals(userId, listOf(vital))

        assertTrue(result.recommendations.any { it.isEmergency })
        verify { context.getString(R.string.bp_high_crisis, 185) }
    }

    @Test
    fun `analyzeBiometry should detect weight goal achievement`() = runBlocking {
        val userId = "user1"
        val current = RemoteBiometry(userId = userId, weight = 70f, height = 1.70f, imc = 24.2f)

        coEvery { biometryRepository.getBiometry(userId) } returns flowOf(emptyList())
        coEvery { profileRepository.getProfile(userId) } returns flowOf(
            ProfileEntity(userId = userId, weightGoal = 70.0)
        )
        coEvery { milestonesRepository.saveMilestone(any()) } just Runs

        val result = healthEngine.analyzeBiometry(userId, current)

        assertTrue(result.newMilestones.any { it.badgeType == "weight_goal" })
        coVerify { milestonesRepository.saveMilestone(match { it.badgeType == "weight_goal" }) }
    }

    @Test
    fun `analyzeBiometry should prioritize urgent weight gain within 48 hours`() = runBlocking {
        val userId = "user1"
        val current = RemoteBiometry(userId = userId, weight = 71f, height = 1.70f, imc = 22.5f)
        val history = listOf(
            BiometryEntity(userId = userId, weight = 71f, height = 1.70f, imc = 22.5f), // Atual (simulado no repo)
            BiometryEntity(userId = userId, weight = 68f, height = 1.70f, imc = 23.5f, measuredAt = Date(System.currentTimeMillis() - 24 * 60 * 60 * 1000L))  // Anterior (-3kg)
        )

        coEvery { biometryRepository.getBiometry(userId) } returns flowOf(history)
        coEvery { profileRepository.getProfile(userId) } returns flowOf(null)

        val result = healthEngine.analyzeBiometry(userId, current)

        assertTrue(result.alerts.any { it.contains("Mock String with Args") })
        assertTrue(result.recommendations.first().isEmergency)
        verify { context.getString(R.string.weight_gain_48h, any()) }
    }
}
