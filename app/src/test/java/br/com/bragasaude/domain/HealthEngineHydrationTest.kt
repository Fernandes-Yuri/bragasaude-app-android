package br.com.bragasaude.domain

import android.content.Context
import br.com.bragasaude.R
import br.com.bragasaude.data.local.MilestoneEntity
import br.com.bragasaude.data.local.ProfileEntity
import br.com.bragasaude.data.local.VitalSignEntity
import br.com.bragasaude.data.notification.HydrationNotifications
import br.com.bragasaude.data.remote.model.RemoteMilestone
import br.com.bragasaude.data.remote.model.RemoteVitalSign
import br.com.bragasaude.data.remote.repository.*
import br.com.bragasaude.data.util.toEntity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import io.mockk.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date
import java.util.UUID

class HealthEngineHydrationTest {
    private val user = "patient"
    private val today get() = LocalDate.now()
    private val context = mockk<Context>(relaxed = true)
    private val vitals = mockk<VitalsRepository>()
    private val milestones = mockk<MilestonesRepository>()
    private val profiles = mockk<ProfileRepository>()
    private val auth = mockk<FirebaseAuth>()
    private lateinit var engine: HealthEngine
    private var target = 2625
    private var total = 0
    private var notificationsEnabled = true
    private var removedMl = 500
    private val saved = mutableListOf<MilestoneEntity>()
    private val water get() = listOf(RemoteVitalSign(userId = user, hydrationMl = 250))

    @Before fun setup() {
        val firebaseUser = mockk<FirebaseUser>()
        every { auth.currentUser } returns firebaseUser
        every { firebaseUser.uid } returns user
        every { context.getString(any()) } answers { "title:${firstArg<Int>()}" }
        every { context.getString(any(), *anyVararg()) } returns "description"
        every { profiles.getProfile(user) } answers {
            flowOf(ProfileEntity(userId = user, hydrationTargetMl = target, notificationsEnabled = notificationsEnabled))
        }
        coEvery { vitals.getVitalSignsSync(user) } returns emptyList()
        every { vitals.getVitalSigns(user) } answers {
            flowOf(listOf(VitalSignEntity(userId = user, hydrationMl = total)))
        }
        coEvery { vitals.deleteLastHydration(user, any()) } coAnswers { total -= removedMl }
        every { milestones.getMilestones(user) } answers { flowOf(saved.toList()) }
        coEvery { milestones.saveMilestone(any()) } coAnswers {
            saved.add(firstArg<RemoteMilestone>().toEntity())
            Unit
        }
        coEvery { milestones.deleteMilestonesForDay(user, any(), any()) } coAnswers {
            val badge = secondArg<String>()
            val day = thirdArg<LocalDate>()
            saved.removeAll { it.userId == user && it.badgeType == badge &&
                it.achievedAt.toInstant().atZone(ZoneId.systemDefault()).toLocalDate() == day }
            Unit
        }
        mockkObject(HydrationNotifications)
        every { HydrationNotifications.notifyOnce(any(), any(), any(), any(), any()) } returns true
        every { HydrationNotifications.notifyHalfOnce(any(), any(), any(), any(), any()) } returns true
        every { HydrationNotifications.clearFullNotification(any(), any(), any()) } just Runs
        every { HydrationNotifications.clearHalfNotification(any(), any(), any()) } just Runs
        engine = HealthEngine(context, vitals, milestones, mockk(relaxed = true), profiles,
            mockk(), mockk(relaxed = true), auth)
    }

    @After fun cleanup() { unmockkObject(HydrationNotifications) }

    @Test fun `half threshold uses integer division and stable daily ID`() = runTest {
        total = 1311
        assertTrue(engine.analyzeVitals(user, water).newMilestones.isEmpty())
        total = 1312
        val half = engine.analyzeVitals(user, water).newMilestones.single()
        assertEquals("hydration_half", half.badgeType)
        assertEquals(UUID.nameUUIDFromBytes("hydration_half:$user:$today".toByteArray(Charsets.UTF_8)).toString(), half.id)
        verify { context.getString(R.string.hydration_half_milestone_desc, 1312, 2625) }
        assertTrue(engine.analyzeVitals(user, water).newMilestones.isEmpty())
        total = 2625
        assertEquals("hydration_daily", engine.analyzeVitals(user, water).newMilestones.single().badgeType)
        assertEquals(setOf("hydration_half", "hydration_daily"), saved.map { it.badgeType }.toSet())
        verify(exactly = 1) { HydrationNotifications.notifyHalfOnce(context, user, today.toString(), any(), any()) }
        verify(exactly = 1) { HydrationNotifications.notifyOnce(context, user, today.toString(), any(), any()) }
    }

    @Test fun `jump directly above full target does not emit half notification`() = runTest {
        total = 3000
        engine.analyzeVitals(user, water)
        assertEquals("hydration_daily", saved.single().badgeType)
        verify(exactly = 0) { HydrationNotifications.notifyHalfOnce(any(), any(), any(), any(), any()) }
    }

    @Test fun `undo below full preserves half and allows full achievement again`() = runTest {
        total = 1500
        engine.analyzeVitals(user, water)
        total = 2625
        val fullId = engine.analyzeVitals(user, water).newMilestones.single().id
        engine.removeLastHydration(user)
        assertEquals(2125, total)
        assertEquals("hydration_half", saved.single().badgeType)
        verify { HydrationNotifications.clearFullNotification(context, user, today.toString()) }
        verify(exactly = 0) { HydrationNotifications.clearHalfNotification(any(), any(), any()) }
        total = 2625
        assertEquals(fullId, engine.analyzeVitals(user, water).newMilestones.single().id)
        verify(exactly = 2) { HydrationNotifications.notifyOnce(any(), user, any(), any(), any()) }
    }

    @Test fun `undo below half removes both and rearms both achievements`() = runTest {
        total = 1312
        val halfId = engine.analyzeVitals(user, water).newMilestones.single().id
        total = 2625
        engine.analyzeVitals(user, water)
        removedMl = 1625
        engine.removeLastHydration(user)
        assertEquals(1000, total)
        assertTrue(saved.isEmpty())
        verify { HydrationNotifications.clearFullNotification(context, user, today.toString()) }
        verify { HydrationNotifications.clearHalfNotification(context, user, today.toString()) }
        total = 1312
        assertEquals(halfId, engine.analyzeVitals(user, water).newMilestones.single().id)
        total = 2625
        engine.analyzeVitals(user, water)
        verify(exactly = 2) { HydrationNotifications.notifyHalfOnce(any(), user, any(), any(), any()) }
        verify(exactly = 2) { HydrationNotifications.notifyOnce(any(), user, any(), any(), any()) }
    }

    @Test fun `undo at exact boundaries preserves achieved levels`() = runTest {
        total = 1312
        engine.analyzeVitals(user, water)
        total = 3125
        engine.analyzeVitals(user, water)
        engine.removeLastHydration(user)
        assertEquals(2625, total)
        assertEquals(2, saved.size)
        verify(exactly = 0) { HydrationNotifications.clearFullNotification(any(), any(), any()) }
        removedMl = 1313
        engine.removeLastHydration(user)
        assertEquals(1312, total)
        assertEquals("hydration_half", saved.single().badgeType)
        verify(exactly = 0) { HydrationNotifications.clearHalfNotification(any(), any(), any()) }
    }

    @Test fun `previous day water and achievements do not count toward today`() = runTest {
        val yesterday = Date.from(today.minusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant())
        every { vitals.getVitalSigns(user) } returns flowOf(listOf(
            VitalSignEntity(userId = user, hydrationMl = 3000, measuredAt = yesterday),
            VitalSignEntity(userId = user, hydrationMl = 1000)
        ))
        saved.add(MilestoneEntity(userId = user, title = "yesterday", badgeType = "hydration_half", achievedAt = yesterday))
        assertTrue(engine.analyzeVitals(user, water).newMilestones.isEmpty())
        engine.removeLastHydration(user)
        assertEquals("yesterday", saved.single().title)
    }

    @Test fun `silent analysis and disabled notifications do not consume half notification`() = runTest {
        total = 1500
        assertEquals("hydration_half", engine.analyzeVitals(user, water, silent = true).newMilestones.single().badgeType)
        assertTrue(saved.isEmpty())
        verify(exactly = 0) { HydrationNotifications.notifyHalfOnce(any(), any(), any(), any(), any()) }
        notificationsEnabled = false
        engine.analyzeVitals(user, water)
        assertEquals(1, saved.size)
        verify(exactly = 0) { HydrationNotifications.notifyHalfOnce(any(), any(), any(), any(), any()) }
    }

    @Test fun `nonpositive targets never award milestones`() = runTest {
        total = 1500
        for (invalidTarget in listOf(0, -100)) {
            target = invalidTarget
            assertTrue(engine.analyzeVitals(user, water).newMilestones.isEmpty())
        }
        assertTrue(saved.isEmpty())
    }

    @Test fun `concurrent half analyses award only once`() = runTest {
        total = 1500
        coEvery { milestones.saveMilestone(any()) } coAnswers {
            kotlinx.coroutines.yield()
            saved.add(firstArg<RemoteMilestone>().toEntity())
            Unit
        }
        listOf(async { engine.analyzeVitals(user, water) }, async { engine.analyzeVitals(user, water) }).awaitAll()
        assertEquals(1, saved.size)
        verify(exactly = 1) { HydrationNotifications.notifyHalfOnce(any(), user, any(), any(), any()) }
    }

    @Test fun `undo waits for in flight award then removes it deterministically`() = runTest {
        total = 2625
        val saving = CompletableDeferred<Unit>()
        val finishSaving = CompletableDeferred<Unit>()
        coEvery { milestones.saveMilestone(any()) } coAnswers {
            saving.complete(Unit)
            finishSaving.await()
            saved.add(firstArg<RemoteMilestone>().toEntity())
            Unit
        }
        val analysis = async { engine.analyzeVitals(user, water) }
        saving.await()
        val undo = async { engine.removeLastHydration(user) }
        kotlinx.coroutines.yield()
        coVerify(exactly = 0) { vitals.deleteLastHydration(any(), any()) }
        finishSaving.complete(Unit)
        analysis.await()
        undo.await()
        assertEquals(2125, total)
        assertTrue(saved.isEmpty())
        verifyOrder {
            HydrationNotifications.notifyOnce(any(), user, any(), any(), any())
            HydrationNotifications.clearFullNotification(any(), user, any())
        }
    }
}
