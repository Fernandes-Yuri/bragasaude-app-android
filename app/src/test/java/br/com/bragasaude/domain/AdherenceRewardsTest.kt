package br.com.bragasaude.domain

import org.junit.Assert.*
import org.junit.Test

class AdherenceRewardsTest {

    @Test
    fun `recording outside reference range is rewarded equally`() {
        assertTrue(GamificationEngine.isVitalsRecordEligible(120, 80, null))
        assertTrue(GamificationEngine.isVitalsRecordEligible(180, 110, null))
        assertTrue(GamificationEngine.isVitalsRecordEligible(null, null, 240))
        assertFalse(GamificationEngine.isVitalsRecordEligible(null, null, null))
        assertFalse(GamificationEngine.isVitalsRecordEligible(120, null, null))
    }

    @Test
    fun `older measurement reward consumes first daily slot`() {
        assertEquals("VITALS_RECORDED:2", GamificationEngine.nextVitalsAwardKey(listOf("VITALS_RECORDED")))
        assertNull(GamificationEngine.nextVitalsAwardKey(listOf("VITALS_RECORDED", "VITALS_RECORDED:2", "VITALS_RECORDED:3")))
    }

    @Test
    fun `hydration thresholds are progressive and capped`() {
        assertEquals(emptyList<Int>(), GamificationEngine.hydrationMilestones(499, 2000))
        assertEquals(listOf(25), GamificationEngine.hydrationMilestones(500, 2000))
        assertEquals(listOf(25, 50, 75, 100), GamificationEngine.hydrationMilestones(4000, 2000))
        assertEquals(emptyList<Int>(), GamificationEngine.hydrationMilestones(500, 0))
    }
}
