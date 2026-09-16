package br.com.bragasaude.domain

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class GamificationEngineTest {

    @Test
    fun `calculateStreak should initialize to 1 when lastXpDate is null`() {
        val today = LocalDate.of(2026, 8, 31)
        val streak = GamificationEngine.calculateStreak(currentStreak = 0, lastXpDate = null, today = today)
        assertEquals(1, streak)
    }

    @Test
    fun `calculateStreak should increment by 1 when last activity was yesterday`() {
        val yesterday = LocalDate.of(2026, 8, 30)
        val today = LocalDate.of(2026, 8, 31)
        val streak = GamificationEngine.calculateStreak(currentStreak = 5, lastXpDate = yesterday, today = today)
        assertEquals(6, streak)
    }

    @Test
    fun `calculateStreak should maintain streak when already recorded today`() {
        val today = LocalDate.of(2026, 8, 31)
        val streak = GamificationEngine.calculateStreak(currentStreak = 5, lastXpDate = today, today = today)
        assertEquals(5, streak)
    }

    @Test
    fun `calculateStreak should reset to 1 when there is a gap greater than 1 day`() {
        val threeDaysAgo = LocalDate.of(2026, 8, 28)
        val today = LocalDate.of(2026, 8, 31)
        val streak = GamificationEngine.calculateStreak(currentStreak = 10, lastXpDate = threeDaysAgo, today = today)
        assertEquals(1, streak)
    }

    @Test
    fun `isStreakBonusEligible should award bonus on multiples of 7 days`() {
        assertTrue(GamificationEngine.isStreakBonusEligible(7))
        assertTrue(GamificationEngine.isStreakBonusEligible(14))
        assertTrue(GamificationEngine.isStreakBonusEligible(21))
        assertFalse(GamificationEngine.isStreakBonusEligible(0))
        assertFalse(GamificationEngine.isStreakBonusEligible(6))
        assertFalse(GamificationEngine.isStreakBonusEligible(8))
    }

    @Test
    fun `canAwardDailyAction should prevent duplicate actions on same day`() {
        val awardedToday = setOf(GamificationActionType.VITALS_RECORDED, GamificationActionType.STEP_GOAL_HIT)

        assertFalse(GamificationEngine.canAwardDailyAction(GamificationActionType.VITALS_RECORDED, awardedToday))
        assertFalse(GamificationEngine.canAwardDailyAction(GamificationActionType.STEP_GOAL_HIT, awardedToday))
        assertTrue(GamificationEngine.canAwardDailyAction(GamificationActionType.HYDRATION_GOAL_HIT, awardedToday))
        assertTrue(GamificationEngine.canAwardDailyAction(GamificationActionType.SAFE_MEAL_RECORDED, awardedToday))
    }

    @Test
    fun `determineLeagueOutcome should promote top 20 percent on exact boundary`() {
        // 2 de 10 = 20% exato -> PROMOTED
        val outcomeExact = GamificationEngine.determineLeagueOutcome(currentLevel = 2, rankAtClose = 2, totalParticipantsInLevel = 10)
        assertEquals(LeagueOutcome.PROMOTED, outcomeExact)

        // 1 de 10 = 10% -> PROMOTED
        val outcomeTop1 = GamificationEngine.determineLeagueOutcome(currentLevel = 2, rankAtClose = 1, totalParticipantsInLevel = 10)
        assertEquals(LeagueOutcome.PROMOTED, outcomeTop1)
    }

    @Test
    fun `determineLeagueOutcome should maintain middle 60 percent`() {
        // 3 a 7 de 10 -> MAINTAINED
        val outcome3 = GamificationEngine.determineLeagueOutcome(currentLevel = 2, rankAtClose = 3, totalParticipantsInLevel = 10)
        val outcome5 = GamificationEngine.determineLeagueOutcome(currentLevel = 2, rankAtClose = 5, totalParticipantsInLevel = 10)
        val outcome7 = GamificationEngine.determineLeagueOutcome(currentLevel = 2, rankAtClose = 7, totalParticipantsInLevel = 10)

        assertEquals(LeagueOutcome.MAINTAINED, outcome3)
        assertEquals(LeagueOutcome.MAINTAINED, outcome5)
        assertEquals(LeagueOutcome.MAINTAINED, outcome7)
    }

    @Test
    fun `determineLeagueOutcome should demote bottom 20 percent on exact boundary if level greater than 1`() {
        // 8 de 10 = 80% exato -> DEMOTED (nível 2)
        val outcome8 = GamificationEngine.determineLeagueOutcome(currentLevel = 2, rankAtClose = 8, totalParticipantsInLevel = 10)
        assertEquals(LeagueOutcome.DEMOTED, outcome8)

        // 10 de 10 = 100% -> DEMOTED (nível 3)
        val outcome10 = GamificationEngine.determineLeagueOutcome(currentLevel = 3, rankAtClose = 10, totalParticipantsInLevel = 10)
        assertEquals(LeagueOutcome.DEMOTED, outcome10)
    }

    @Test
    fun `determineLeagueOutcome should never demote below Level 1`() {
        // No Nível 1, mesmo em último lugar (10 de 10), o outcome é MAINTAINED (piso de proteção geriátrica)
        val outcomeLevel1 = GamificationEngine.determineLeagueOutcome(currentLevel = 1, rankAtClose = 10, totalParticipantsInLevel = 10)
        assertEquals(LeagueOutcome.MAINTAINED, outcomeLevel1)

        val newLevel = GamificationEngine.calculateNewLevel(currentLevel = 1, outcome = outcomeLevel1)
        assertEquals(1, newLevel)
    }

    @Test
    fun `determineLeagueOutcome should maintain when participants count is less than 5`() {
        // Amostra muito pequena (ex: 4 usuários no nível) -> não promove nem rebaixa
        val outcomeSmall = GamificationEngine.determineLeagueOutcome(currentLevel = 2, rankAtClose = 1, totalParticipantsInLevel = 4)
        assertEquals(LeagueOutcome.MAINTAINED, outcomeSmall)
    }

    @Test
    fun `calculateNewLevel should update correctly`() {
        assertEquals(3, GamificationEngine.calculateNewLevel(currentLevel = 2, outcome = LeagueOutcome.PROMOTED))
        assertEquals(2, GamificationEngine.calculateNewLevel(currentLevel = 2, outcome = LeagueOutcome.MAINTAINED))
        assertEquals(1, GamificationEngine.calculateNewLevel(currentLevel = 2, outcome = LeagueOutcome.DEMOTED))
        assertEquals(1, GamificationEngine.calculateNewLevel(currentLevel = 1, outcome = LeagueOutcome.DEMOTED))
    }

    @Test
    fun `buildOutcomeMessage should always return positive and empowering messages`() {
        val promoMsg = GamificationEngine.buildOutcomeMessage(LeagueOutcome.PROMOTED, 3)
        val maintMsg = GamificationEngine.buildOutcomeMessage(LeagueOutcome.MAINTAINED, 2)
        val demoMsg = GamificationEngine.buildOutcomeMessage(LeagueOutcome.DEMOTED, 1)

        assertTrue(promoMsg.contains("Parabéns") && promoMsg.contains("Nível 3"))
        assertTrue(maintMsg.contains("manteve firme") && maintMsg.contains("Nível 2"))
        assertTrue(demoMsg.contains("vamos juntos de novo") && demoMsg.contains("Nível 1"))

        // Garantir ausência de termos punitivos
        assertFalse(promoMsg.contains("perdeu") || promoMsg.contains("falhou"))
        assertFalse(maintMsg.contains("perdeu") || maintMsg.contains("falhou"))
        assertFalse(demoMsg.contains("perdeu") || demoMsg.contains("falhou") || demoMsg.contains("caiu de rendimento"))
    }

    @Test
    fun `isVitalsInTarget should validate clinical limits properly`() {
        // Pressão ideal (120/80)
        assertTrue(GamificationEngine.isVitalsInTarget(systolic = 120, diastolic = 80))
        // Shorthand brasileiro 12 por 8
        assertTrue(GamificationEngine.isVitalsInTarget(systolic = 12, diastolic = 8))
        // Pressão alta fora da meta (150/95)
        assertFalse(GamificationEngine.isVitalsInTarget(systolic = 150, diastolic = 95))
        // Glicose jejum na meta (100)
        assertTrue(GamificationEngine.isVitalsInTarget(glucose = 100, glucoseType = "fasting"))
        // Glicose jejum alta (190)
        assertFalse(GamificationEngine.isVitalsInTarget(glucose = 190, glucoseType = "fasting"))
    }

    @Test
    fun `isMedicationOnTime should respect tolerance window`() {
        // Agendado 08:00, tomado 08:30 (dentro dos 60 min)
        assertTrue(GamificationEngine.isMedicationOnTime("08:00", takenHour = 8, takenMinute = 30))
        // Agendado 08:00, tomado 07:15 (45 min antes, dentro da tolerância)
        assertTrue(GamificationEngine.isMedicationOnTime("08:00", takenHour = 7, takenMinute = 15))
        // Agendado 08:00, tomado 10:00 (120 min depois, fora da tolerância)
        assertFalse(GamificationEngine.isMedicationOnTime("08:00", takenHour = 10, takenMinute = 0))
    }

    @Test
    fun `isMealSafeForProfile should respect clinical conditions`() {
        val diabeticProfile = ProfileHealthFlags(hasDiabetes = true, hasHypertension = false)
        val safeMeal = MealHealthFlags(isDiabetesSafe = true, isHypertensionSafe = true)
        val unsafeMeal = MealHealthFlags(isDiabetesSafe = false, isHypertensionSafe = true)

        assertTrue(GamificationEngine.isMealSafeForProfile(safeMeal, diabeticProfile))
        assertFalse(GamificationEngine.isMealSafeForProfile(unsafeMeal, diabeticProfile))
    }

    // ------------------------------------------------------------------
    // FASE 3 — CURVA DE NÍVEL POR XP ACUMULADO
    // ------------------------------------------------------------------

    @Test
    fun `xpForLevel should follow the polynomial curve`() {
        assertEquals(0, GamificationEngine.xpForLevel(1))
        assertEquals(100, GamificationEngine.xpForLevel(2))
        assertEquals(300, GamificationEngine.xpForLevel(3))
        assertEquals(600, GamificationEngine.xpForLevel(4))
        assertEquals(1000, GamificationEngine.xpForLevel(5))
        // Níveis abaixo de 1 são normalizados para 1
        assertEquals(0, GamificationEngine.xpForLevel(0))
        assertEquals(0, GamificationEngine.xpForLevel(-5))
    }

    @Test
    fun `levelFromTotalXp should derive level from accumulated XP`() {
        assertEquals(1, GamificationEngine.levelFromTotalXp(0))
        assertEquals(1, GamificationEngine.levelFromTotalXp(99))
        assertEquals(2, GamificationEngine.levelFromTotalXp(100))
        assertEquals(2, GamificationEngine.levelFromTotalXp(299))
        assertEquals(3, GamificationEngine.levelFromTotalXp(300))
        assertEquals(5, GamificationEngine.levelFromTotalXp(1234))
        // XP negativo é tratado como 0
        assertEquals(1, GamificationEngine.levelFromTotalXp(-50))
    }

    @Test
    fun `progressInLevel should return correct fraction within level`() {
        // Nível 1: base 0, próximo 100
        assertEquals(0f, GamificationEngine.progressInLevel(0), 0.001f)
        assertEquals(0.5f, GamificationEngine.progressInLevel(50), 0.001f)
        // Nível 2: base 100, próximo 300 → 200 XP = meio do nível
        assertEquals(0.5f, GamificationEngine.progressInLevel(200), 0.001f)
        // Nunca deve passar de 1
        assertTrue(GamificationEngine.progressInLevel(10_000_000) <= 1f)
        assertTrue(GamificationEngine.progressInLevel(-100) >= 0f)
    }

    @Test
    fun `getStreakMultiplier should scale with streak length`() {
        assertEquals(1.0f, GamificationEngine.getStreakMultiplier(0))
        assertEquals(1.0f, GamificationEngine.getStreakMultiplier(6))
        assertEquals(1.3f, GamificationEngine.getStreakMultiplier(7))
        assertEquals(1.3f, GamificationEngine.getStreakMultiplier(13))
        assertEquals(1.5f, GamificationEngine.getStreakMultiplier(14))
        assertEquals(1.8f, GamificationEngine.getStreakMultiplier(21))
        assertEquals(2.0f, GamificationEngine.getStreakMultiplier(28))
        assertEquals(2.0f, GamificationEngine.getStreakMultiplier(100))
    }

    // ------------------------------------------------------------------
    // FASE 3 — BALANCEAMENTO DE LIGAS (determineLeagueOutcomeImproved)
    // ------------------------------------------------------------------

    @Test
    fun `improved league outcome should promote small leagues by absolute XP`() {
        // Liga com apenas 3 participantes — o antigo algoritmo travaria o progresso
        val outcome = GamificationEngine.determineLeagueOutcomeImproved(
            currentLevel = 2,
            rankAtClose = 1,
            totalParticipantsInLevel = 3,
            xpEarned = XpRewards.SMALL_LEAGUE_PROMOTION_XP
        )
        assertEquals(LeagueOutcome.PROMOTED, outcome)
    }

    @Test
    fun `improved league outcome should demote inactive users only in small leagues above level 1`() {
        // Nível 2, liga pequena, quase sem participar → rebaixa
        val demoted = GamificationEngine.determineLeagueOutcomeImproved(
            currentLevel = 2,
            rankAtClose = 3,
            totalParticipantsInLevel = 3,
            xpEarned = XpRewards.SMALL_LEAGUE_DEMOTION_XP - 1
        )
        assertEquals(LeagueOutcome.DEMOTED, demoted)

        // Nível 1 nunca rebaixa, mesmo sem participar
        val maintainedLevel1 = GamificationEngine.determineLeagueOutcomeImproved(
            currentLevel = 1,
            rankAtClose = 3,
            totalParticipantsInLevel = 3,
            xpEarned = 0
        )
        assertEquals(LeagueOutcome.MAINTAINED, maintainedLevel1)
    }

    @Test
    fun `improved league outcome should require minimum XP to promote in full leagues`() {
        // Top 1 de 10, mas SEM o mínimo de XP → NÃO promove (anti-sandbagging)
        val noXp = GamificationEngine.determineLeagueOutcomeImproved(
            currentLevel = 2,
            rankAtClose = 1,
            totalParticipantsInLevel = 10,
            xpEarned = 0
        )
        assertEquals(LeagueOutcome.MAINTAINED, noXp)

        // Top 1 de 10 COM o mínimo de XP → promove
        val withXp = GamificationEngine.determineLeagueOutcomeImproved(
            currentLevel = 2,
            rankAtClose = 1,
            totalParticipantsInLevel = 10,
            xpEarned = GamificationEngine.minXpForPromotion(2)
        )
        assertEquals(LeagueOutcome.PROMOTED, withXp)
    }

    @Test
    fun `minXpForPromotion should increase with level`() {
        assertEquals(XpRewards.MIN_XP_FOR_PROMOTION_BASE, GamificationEngine.minXpForPromotion(1))
        assertEquals(
            XpRewards.MIN_XP_FOR_PROMOTION_BASE + XpRewards.MIN_XP_FOR_PROMOTION_PER_LEVEL,
            GamificationEngine.minXpForPromotion(2)
        )
        assertTrue(GamificationEngine.minXpForPromotion(5) > GamificationEngine.minXpForPromotion(2))
    }

    @Test
    fun `improved league outcome should still demote bottom of full leagues`() {
        val demoted = GamificationEngine.determineLeagueOutcomeImproved(
            currentLevel = 3,
            rankAtClose = 9,
            totalParticipantsInLevel = 10,
            xpEarned = 50
        )
        assertEquals(LeagueOutcome.DEMOTED, demoted)
    }

    @Test
    fun `improved league outcome should handle invalid inputs gracefully`() {
        assertEquals(
            LeagueOutcome.MAINTAINED,
            GamificationEngine.determineLeagueOutcomeImproved(currentLevel = 1, rankAtClose = 0, totalParticipantsInLevel = 10, xpEarned = 500)
        )
        assertEquals(
            LeagueOutcome.MAINTAINED,
            GamificationEngine.determineLeagueOutcomeImproved(currentLevel = 1, rankAtClose = 1, totalParticipantsInLevel = 0, xpEarned = 500)
        )
    }

    @Test
    fun `resolveFinalLevel should never go below the XP floor`() {
        // Rebaixamento de liga para nível 1, mas o XP já garante nível 3 → mantém 3
        assertEquals(3, GamificationEngine.resolveFinalLevel(outcomeLevel = 1, xpFloorLevel = 3))
        // Promoção de liga acima do piso → vale o maior
        assertEquals(5, GamificationEngine.resolveFinalLevel(outcomeLevel = 5, xpFloorLevel = 2))
        // Nunca abaixo de 1
        assertEquals(1, GamificationEngine.resolveFinalLevel(outcomeLevel = 0, xpFloorLevel = -2))
    }

    @Test
    fun `isActivityXPEligible should reject low reliability sessions`() {
        assertTrue(GamificationEngine.isActivityXPEligible(0.4f))
        assertTrue(GamificationEngine.isActivityXPEligible(0.9f))
        assertFalse(GamificationEngine.isActivityXPEligible(0.39f))
        assertFalse(GamificationEngine.isActivityXPEligible(0.0f))
    }
}
