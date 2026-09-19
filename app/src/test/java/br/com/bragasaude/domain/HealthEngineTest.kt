package br.com.bragasaude.domain

import android.content.Context
import br.com.bragasaude.R
import br.com.bragasaude.data.local.BiometryEntity
import br.com.bragasaude.data.local.MilestoneEntity
import br.com.bragasaude.data.local.ProfileEntity
import br.com.bragasaude.data.remote.model.RemoteBiometry
import br.com.bragasaude.data.remote.model.RemoteVitalSign
import br.com.bragasaude.data.remote.repository.*
import com.google.firebase.auth.FirebaseAuth
import io.mockk.*
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.*

class HealthEngineTest {

    private val context = mockk<Context>(relaxed = true)
    private val vitalsRepository = mockk<VitalsRepository>()
    private val examsRepository = mockk<ExamsRepository>()
    private val milestonesRepository = mockk<MilestonesRepository>()
    private val conditionRepository = mockk<ConditionRepository>()
    private val profileRepository = mockk<ProfileRepository>()
    private val biometryRepository = mockk<BiometryRepository>()
    private val riskManager = mockk<RiskManager>(relaxed = true)
    private val auth = mockk<FirebaseAuth>(relaxed = true)
    // doc 10 §3.3: exame critico avisa o cuidador (apiClient + familyDao).
    private val apiClient = mockk<br.com.bragasaude.data.remote.api.BragaApiClient>(relaxed = true)
    private val familyDao = mockk<br.com.bragasaude.data.local.FamilyDao>(relaxed = true)

    private lateinit var healthEngine: HealthEngine

    @Before
    fun setup() {
        healthEngine = HealthEngine(
            context, vitalsRepository, examsRepository, 
            milestonesRepository, conditionRepository, 
            profileRepository, biometryRepository,
            riskManager, auth, apiClient, familyDao
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
    fun `exam low limit is evaluated and HDL is not treated as LDL`() = runBlocking {
        coEvery { examsRepository.getExamItems("user1") } returns flowOf(emptyList())
        coEvery { examsRepository.getClinicalReference("glucose_fasting") } returns
            br.com.bragasaude.data.local.ClinicalReferenceEntity(
                itemKey = "glucose_fasting", itemName = "Glicose", category = "test",
                minCritical = 54.0, maxCritical = 300.0, unit = "mg/dL")
        coEvery { examsRepository.getClinicalReference("hdl") } returns null
        val low = br.com.bragasaude.data.remote.model.RemoteExamItem(
            examId = "exam", userId = "user1", itemKey = "glucose_fasting", itemName = "Glicose",
            valueNumeric = 50.0, unit = "mg/dL")
        assertTrue(healthEngine.analyzeExamItems("user1", listOf(low), silent = true).recommendations.single().isEmergency)
        assertTrue(healthEngine.analyzeExamItems("user1", listOf(low.copy(itemKey = "hdl", itemName = "Colesterol HDL", valueNumeric = 135.0)), silent = true).recommendations.isEmpty())
        assertTrue(healthEngine.analyzeExamItems("user1", listOf(low.copy(unit = "mmol/L")), silent = true).recommendations.isEmpty())
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
