package br.com.bragasaude.domain

import br.com.bragasaude.data.local.*
import br.com.bragasaude.util.BragaConstants
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date

class AdherenceRewardsTest {
    private val user = BragaConstants.GUEST_UID
    private val today = Date.from(LocalDate.of(2026, 9, 21).atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant())
    private var profile = ProfileEntity(userId = user)
    private val awards = mutableMapOf<String, XpAwardEntity>()
    private val profiles = mockk<ProfileDao>(relaxed = true)
    private val xpAwards = mockk<XpAwardDao>(relaxed = true)
    private fun service(): XpGrantService {
        coEvery { profiles.getProfileOneShot(user) } answers { profile }
        coEvery { profiles.insert(any()) } coAnswers { profile = firstArg() }
        coEvery { xpAwards.getAwardedActionTypes(user, any()) } coAnswers {
            val date: String = secondArg()
            awards.values.filter { it.date == date }.map { it.actionType }
        }
        coEvery { xpAwards.insert(any()) } coAnswers {
            val award: XpAwardEntity = firstArg()
            awards[award.date + ":" + award.actionType] = award
        }
        return XpGrantService(mockk(relaxed = true), profiles, xpAwards, mockk(relaxed = true))
    }

    @Test fun `recording outside reference range is rewarded equally`() {
        assertTrue(GamificationEngine.isVitalsRecordEligible(120, 80, null))
        assertTrue(GamificationEngine.isVitalsRecordEligible(180, 110, null))
        assertTrue(GamificationEngine.isVitalsRecordEligible(null, null, 240))
        assertFalse(GamificationEngine.isVitalsRecordEligible(null, null, null))
        assertFalse(GamificationEngine.isVitalsRecordEligible(120, null, null))
    }

    @Test fun `fourth measurement has no reward and next day resets cap`() = runTest {
        val service = service()
        repeat(3) { assertTrue(service.grantXp(user, GamificationActionType.VITALS_RECORDED, true, at = today).awarded) }
        assertFalse(service.grantXp(user, GamificationActionType.VITALS_RECORDED, true, at = today).awarded)
        assertEquals(30, profile.totalXp)
        assertTrue(service.grantXp(user, GamificationActionType.VITALS_RECORDED, true, at = Date(today.time + 86_400_000)).awarded)
    }

    @Test fun `older measurement reward consumes first daily slot`() {
        assertEquals("VITALS_RECORDED:2", GamificationEngine.nextVitalsAwardKey(listOf("VITALS_RECORDED")))
        assertNull(GamificationEngine.nextVitalsAwardKey(listOf("VITALS_RECORDED", "VITALS_RECORDED:2", "VITALS_RECORDED:3")))
    }

    @Test fun `hydration thresholds are progressive and capped`() {
        assertEquals(emptyList<Int>(), GamificationEngine.hydrationMilestones(499, 2000))
        assertEquals(listOf(25), GamificationEngine.hydrationMilestones(500, 2000))
        assertEquals(listOf(25, 50, 75, 100), GamificationEngine.hydrationMilestones(4000, 2000))
        assertEquals(emptyList<Int>(), GamificationEngine.hydrationMilestones(500, 0))
    }

    @Test fun `repeated hydration milestones never duplicate xp`() = runTest {
        val service = service()
        repeat(2) {
            listOf(25, 50, 75, 100).forEach { percent ->
                service.grantXp(user, GamificationActionType.HYDRATION_GOAL_HIT, true, at = today, hydrationMilestone = percent)
            }
        }
        assertEquals(10, profile.totalXp)
        assertEquals(4, awards.size)
    }

    @Test fun `legacy full hydration reward prevents extra progress rewards`() = runTest {
        val service = service()
        service.grantXp(user, GamificationActionType.HYDRATION_GOAL_HIT, true, at = today)
        assertFalse(service.grantXp(user, GamificationActionType.HYDRATION_GOAL_HIT, true, at = today, hydrationMilestone = 25).awarded)
        assertEquals(10, profile.totalXp)
    }

    @Test fun `weekly streak bonus is granted only once across actions`() = runTest {
        profile = profile.copy(currentStreak = 6, lastXpAt = Date(today.time - 86_400_000))
        val service = service()
        val first = service.grantXp(user, GamificationActionType.VITALS_RECORDED, true, at = today)
        val second = service.grantXp(user, GamificationActionType.MEDICATION_TAKEN_ON_TIME, true, at = today)
        assertEquals(50, first.streakBonusXp)
        assertEquals(0, second.streakBonusXp)
    }
}
