package br.com.bragasaude.domain

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Motor Deterministico de Gamificacao & Ligas Semanais - Braga Saude
 *
 * Segue o padrão de regras puras e auditáveis estabelecido em Contexto/01_LIMITES_CLINICOS.md
 * e os princípios de UX Geriátrica de Contexto/09_MANUAL_DE_ESTILO_E_UX_GERIATRICA.md.
 */
object XpRewards {
    // Tabela de XP por Ação
    const val VITALS_IN_TARGET_XP = 10
    const val MEDICATION_ON_TIME_XP = 10
    const val STEP_GOAL_HIT_XP = 15
    const val HYDRATION_GOAL_HIT_XP = 10
    const val SAFE_MEAL_XP = 10
    const val MILESTONE_ACHIEVED_XP = 25
    const val STREAK_7_DAYS_BONUS_XP = 50

    // Constantes e Limites de Regras
    const val STREAK_BONUS_INTERVAL_DAYS = 7
    const val MEDICATION_TOLERANCE_MINUTES = 60
    const val MIN_LEVEL = 1
    const val MIN_PARTICIPANTS_FOR_TIER_MOVEMENT = 5
    const val PROMOTION_THRESHOLD_PERCENT = 0.20
    const val DEMOTION_THRESHOLD_PERCENT = 0.80

    // Fase 3 — Curva de Nível por XP acumulado
    /** Passo da curva polinomial de nível: XP(n) = XP_LEVEL_CURVE_STEP * (n-1) * n */
    const val XP_LEVEL_CURVE_STEP = 50
    /** Trava de segurança para o cálculo de nível. */
    const val MAX_LEVEL = 100

    // Fase 3 — Balanceamento de ligas pequenas e anti-sandbagging
    /** Em ligas com menos de 5 participantes, a promoção passa a ser por XP absoluto. */
    const val SMALL_LEAGUE_PROMOTION_XP = 200
    /** Em ligas pequenas, só é rebaixado quem praticamente não participou. */
    const val SMALL_LEAGUE_DEMOTION_XP = 30
    /** XP mínimo para ser promovido em liga cheia (níveis mais altos exigem mais). */
    const val MIN_XP_FOR_PROMOTION_BASE = 100
    const val MIN_XP_FOR_PROMOTION_PER_LEVEL = 50

    /**
     * Limiar anti-fraude da Fase 2.
     * Sessões de atividade com reliabilityScore ABAIXO deste valor NÃO devem render
     * XP de passos nem contar para o ranking semanal. Veja MovementManager/ActivityReconciler.
     */
    const val MIN_RELIABILITY_FOR_STEP_XP = 0.4f

    // Metas clínicas padrão de referência (01_LIMITES_CLINICOS.md)
    const val SYSTOLIC_MIN_TARGET = 110
    const val SYSTOLIC_MAX_TARGET = 130
    const val DIASTOLIC_MIN_TARGET = 70
    const val DIASTOLIC_MAX_TARGET = 85
    const val GLUCOSE_FASTING_MIN_TARGET = 80
    const val GLUCOSE_FASTING_MAX_TARGET = 130
    const val GLUCOSE_POST_PRANDIAL_MAX_TARGET = 160
    const val OXYGEN_SPO2_MIN_TARGET = 95
    const val HEART_RATE_RESTING_MIN_TARGET = 60
    const val HEART_RATE_RESTING_MAX_TARGET = 90
}

enum class GamificationActionType {
    VITALS_RECORDED,
    MEDICATION_TAKEN_ON_TIME,
    STEP_GOAL_HIT,
    HYDRATION_GOAL_HIT,
    SAFE_MEAL_RECORDED,
    MILESTONE_ACHIEVED,
    STREAK_BONUS
}

enum class LeagueOutcome(val key: String) {
    PROMOTED("promoted"),
    MAINTAINED("maintained"),
    DEMOTED("demoted");

    companion object {
        fun fromKey(key: String?): LeagueOutcome =
            values().find { it.key.equals(key, ignoreCase = true) } ?: MAINTAINED
    }
}

data class XpEvaluationResult(
    val actionType: GamificationActionType,
    val xpEarned: Int,
    val isAwarded: Boolean,
    val reason: String
)

data class MealHealthFlags(
    val isDiabetesSafe: Boolean = true,
    val isHypertensionSafe: Boolean = true,
    val isThyroidSafe: Boolean = true
)

data class ProfileHealthFlags(
    val hasDiabetes: Boolean = false,
    val hasHypertension: Boolean = false,
    val hasThyroidIssue: Boolean = false
)

object GamificationEngine {

    /**
     * [R12] ANTI-FRAUDE DE XP DE ATIVIDADE FÍSICA (Fase 2)
     *
     * Sessões de caminhada com reliabilityScore abaixo do limiar (0.4) não devem
     * render XP nem contar para o ranking semanal. Impede que um usuário "balançe
     * o celular no bolso" ou provoque drift de GPS para subir de liga.
     *
     * @param reliabilityScore score produzido pelo ActivityReconciler (0..1)
     * @return true se a sessão é confiável o suficiente para pontuar
     */
    fun isActivityXPEligible(reliabilityScore: Float): Boolean {
        return reliabilityScore >= XpRewards.MIN_RELIABILITY_FOR_STEP_XP
    }

    /**
     * [R1] CÁLCULO DE DIAS CONSECUTIVOS (STREAK)
     *
     * Pseudocódigo:
     * ```
     * IF lastXpDate == null:
     *     streak = 1
     * ELSE IF today - lastXpDate == 1 dia:
     *     streak = currentStreak + 1
     * ELSE IF today - lastXpDate == 0 dia:
     *     streak = currentStreak  # já contou hoje, não duplica
     * ELSE:
     *     streak = 1              # quebrou a sequência, reinicia
     * ```
     */
    fun calculateStreak(
        currentStreak: Int,
        lastXpDate: LocalDate?,
        today: LocalDate
    ): Int {
        if (lastXpDate == null) {
            return 1
        }
        val daysDiff = ChronoUnit.DAYS.between(lastXpDate, today)
        return when {
            daysDiff == 1L -> currentStreak + 1
            daysDiff == 0L -> maxOf(1, currentStreak)
            daysDiff > 1L -> 1
            else -> currentStreak // Datas futuras / anomalias de fuso
        }
    }

    /**
     * [R2] ELEGIBILIDADE PARA BÔNUS DE STREAK (SEMANAL)
     *
     * Concede bônus a cada múltiplo de 7 dias (7, 14, 21, 28...).
     */
    fun isStreakBonusEligible(newStreak: Int): Boolean {
        return newStreak > 0 && (newStreak % XpRewards.STREAK_BONUS_INTERVAL_DAYS == 0)
    }

    /**
     * [R3] ANTI-FARMING: VALIDAÇÃO DE CONCESSÃO DIÁRIA ÚNICA POR TIPO DE AÇÃO
     *
     * Pseudocódigo:
     * ```
     * IF actionType IN alreadyAwardedToday:
     *     CAN_AWARD = false
     * ELSE:
     *     CAN_AWARD = true
     * ```
     */
    fun canAwardDailyAction(
        actionType: GamificationActionType,
        alreadyAwardedToday: Set<GamificationActionType>
    ): Boolean {
        return actionType !in alreadyAwardedToday
    }

    /**
     * [R4] AVALIAÇÃO DE REGISTRO DE SINAIS VITAIS NA META
     *
     * Verifica se os valores informados estão dentro do intervalo ideal (01_LIMITES_CLINICOS.md).
     */
    fun isVitalsInTarget(
        systolic: Int? = null,
        diastolic: Int? = null,
        glucose: Int? = null,
        glucoseType: String? = "fasting",
        spo2: Int? = null,
        heartRate: Int? = null
    ): Boolean {
        var hasAtLeastOne = false

        if (systolic != null) {
            val normalized = br.com.bragasaude.domain.util.BloodPressureParser.normalizePressure(systolic)
            if (normalized !in XpRewards.SYSTOLIC_MIN_TARGET..XpRewards.SYSTOLIC_MAX_TARGET) return false
            hasAtLeastOne = true
        }

        if (diastolic != null) {
            val normalized = br.com.bragasaude.domain.util.BloodPressureParser.normalizePressure(diastolic)
            if (normalized !in XpRewards.DIASTOLIC_MIN_TARGET..XpRewards.DIASTOLIC_MAX_TARGET) return false
            hasAtLeastOne = true
        }

        if (glucose != null) {
            if (glucoseType.equals("post_prandial", ignoreCase = true)) {
                if (glucose > XpRewards.GLUCOSE_POST_PRANDIAL_MAX_TARGET || glucose < 70) return false
            } else {
                if (glucose !in XpRewards.GLUCOSE_FASTING_MIN_TARGET..XpRewards.GLUCOSE_FASTING_MAX_TARGET) return false
            }
            hasAtLeastOne = true
        }

        if (spo2 != null) {
            if (spo2 < XpRewards.OXYGEN_SPO2_MIN_TARGET) return false
            hasAtLeastOne = true
        }

        if (heartRate != null) {
            if (heartRate !in XpRewards.HEART_RATE_RESTING_MIN_TARGET..XpRewards.HEART_RATE_RESTING_MAX_TARGET) return false
            hasAtLeastOne = true
        }

        return hasAtLeastOne
    }

    /**
     * [R5] AVALIAÇÃO DE TOMADA DE MEDICAÇÃO NO HORÁRIO (COM JANELA DE TOLERÂNCIA)
     *
     * Compara hora agendada ("HH:mm") com hora tomada (minutos do dia).
     */
    fun isMedicationOnTime(
        scheduledTimeStr: String,
        takenHour: Int,
        takenMinute: Int,
        toleranceMinutes: Int = XpRewards.MEDICATION_TOLERANCE_MINUTES
    ): Boolean {
        val parts = scheduledTimeStr.split(":")
        if (parts.size < 2) return false
        val schedHour = parts[0].toIntOrNull() ?: return false
        val schedMinute = parts[1].toIntOrNull() ?: return false

        val schedTotalMinutes = schedHour * 60 + schedMinute
        val takenTotalMinutes = takenHour * 60 + takenMinute

        val diff = Math.abs(schedTotalMinutes - takenTotalMinutes)
        val circularDiff = Math.min(diff, 1440 - diff)
        return circularDiff <= toleranceMinutes
    }

    /**
     * [R6] AVALIAÇÃO DE META DE PASSOS
     */
    fun isStepGoalHit(stepsTaken: Int, stepGoal: Int): Boolean {
        return stepGoal > 0 && stepsTaken >= stepGoal
    }

    /**
     * [R7] AVALIAÇÃO DE META DE HIDRATAÇÃO
     */
    fun isHydrationGoalHit(hydrationMl: Int, targetMl: Int): Boolean {
        return targetMl > 0 && hydrationMl >= targetMl
    }

    /**
     * [R8] AVALIAÇÃO DE REFEIÇÃO COMPATÍVEL COM CONDIÇÕES CLÍNICAS
     */
    fun isMealSafeForProfile(
        meal: MealHealthFlags,
        profile: ProfileHealthFlags
    ): Boolean {
        if (profile.hasDiabetes && !meal.isDiabetesSafe) return false
        if (profile.hasHypertension && !meal.isHypertensionSafe) return false
        if (profile.hasThyroidIssue && !meal.isThyroidSafe) return false
        return true
    }

    /**
     * [R9] DETERMINAÇÃO DE RESULTADO DE LIGA SEMANAL (PROMOÇÃO / MANUTENÇÃO / REBAIXAMENTO)
     *
     * Pseudocódigo:
     * ```
     * IF totalParticipantsInLevel < 5:
     *     outcome = "maintained"
     * topPercent = rankAtClose / totalParticipantsInLevel
     * IF topPercent <= 0.20:
     *     outcome = "promoted"      # sobe para currentLevel + 1
     * ELSE IF topPercent >= 0.80 AND currentLevel > 1:
     *     outcome = "demoted"       # cai para currentLevel - 1 (nunca abaixo de 1)
     * ELSE:
     *     outcome = "maintained"
     * ```
     */
    fun determineLeagueOutcome(
        currentLevel: Int,
        rankAtClose: Int,
        totalParticipantsInLevel: Int
    ): LeagueOutcome {
        if (totalParticipantsInLevel < XpRewards.MIN_PARTICIPANTS_FOR_TIER_MOVEMENT || rankAtClose < 1) {
            return LeagueOutcome.MAINTAINED
        }

        val topPercent = rankAtClose.toDouble() / totalParticipantsInLevel.toDouble()

        return when {
            topPercent <= XpRewards.PROMOTION_THRESHOLD_PERCENT -> LeagueOutcome.PROMOTED
            topPercent >= XpRewards.DEMOTION_THRESHOLD_PERCENT && currentLevel > XpRewards.MIN_LEVEL -> LeagueOutcome.DEMOTED
            else -> LeagueOutcome.MAINTAINED
        }
    }

    /**
     * [R10] CÁLCULO DO NOVO NÍVEL
     *
     * Trava: currentLevel nunca fica menor que MIN_LEVEL (1).
     */
    fun calculateNewLevel(currentLevel: Int, outcome: LeagueOutcome): Int {
        val baseLevel = maxOf(XpRewards.MIN_LEVEL, currentLevel)
        return when (outcome) {
            LeagueOutcome.PROMOTED -> baseLevel + 1
            LeagueOutcome.MAINTAINED -> baseLevel
            LeagueOutcome.DEMOTED -> maxOf(XpRewards.MIN_LEVEL, baseLevel - 1)
        }
    }

    /**
     * [R11] MENSAGEM DE ENCERRAMENTO DE LIGA (UX GERIÁTRICA POSITIVA)
     *
     * Conforme 09_MANUAL_DE_ESTILO_E_UX_GERIATRICA.md:
     * Sem termos punitivos, sem jargões, tom encorajador e acolhedor.
     */
    fun buildOutcomeMessage(outcome: LeagueOutcome, level: Int): String {
        val safeLevel = maxOf(XpRewards.MIN_LEVEL, level)
        return when (outcome) {
            LeagueOutcome.PROMOTED ->
                "Parabéns! Você subiu para o Nível $safeLevel. Continue nesse ritmo!"
            LeagueOutcome.MAINTAINED ->
                "Você se manteve firme no Nível $safeLevel esta semana. Continue assim!"
            LeagueOutcome.DEMOTED ->
                "Nova semana, novo ciclo! Você está no Nível $safeLevel — vamos juntos de novo?"
        }
    }

    // ------------------------------------------------------------------
    // FASE 3 — CURVA DE NÍVEL POR XP ACUMULADO
    // ------------------------------------------------------------------

    /**
     * [R13] XP TOTAL necessário para alcançar um nível.
     *
     * Curva polinomial suave, pensada para público geriátrico (progresso sempre
     * visível no início): Nível 1 = 0 XP, Nível 2 = 100 XP, Nível 3 = 300 XP,
     * Nível 4 = 600 XP, Nível 5 = 1000 XP...
     */
    fun xpForLevel(level: Int): Int {
        val safeLevel = maxOf(XpRewards.MIN_LEVEL, level)
        return XpRewards.XP_LEVEL_CURVE_STEP * (safeLevel - 1) * safeLevel
    }

    /**
     * [R14] Deriva o nível a partir do XP total acumulado (lifetime).
     * Garante que o usuário NUNCA fique preso em um nível por falta de liga ativa.
     */
    fun levelFromTotalXp(totalXp: Int): Int {
        val safeXp = maxOf(0, totalXp)
        var level = XpRewards.MIN_LEVEL
        while (level < XpRewards.MAX_LEVEL && xpForLevel(level + 1) <= safeXp) {
            level++
        }
        return level
    }

    /**
     * [R15] Progresso (0..1) dentro do nível atual — usado em barras de progresso.
     */
    fun progressInLevel(totalXp: Int): Float {
        val level = levelFromTotalXp(totalXp)
        if (level >= XpRewards.MAX_LEVEL) return 1f
        val base = xpForLevel(level)
        val next = xpForLevel(level + 1)
        if (next <= base) return 1f
        return ((maxOf(0, totalXp) - base).toFloat() / (next - base)).coerceIn(0f, 1f)
    }

    /**
     * [R16] MULTIPLICADOR DE STREAK — motivação crescente para consistência.
     *
     * 7 dias = 1.3x | 14 dias = 1.5x | 21 dias = 1.8x | 28+ dias = 2.0x
     */
    fun getStreakMultiplier(streak: Int): Float = when {
        streak >= 28 -> 2.0f
        streak >= 21 -> 1.8f
        streak >= 14 -> 1.5f
        streak >= 7 -> 1.3f
        else -> 1.0f
    }

    // ------------------------------------------------------------------
    // FASE 3 — BALANCEAMENTO DE LIGAS
    // ------------------------------------------------------------------

    /**
     * [R17] XP mínimo para promoção em ligas cheias (anti-sandbagging).
     * Usuários de nível alto precisam demonstrar mais atividade para subir.
     */
    fun minXpForPromotion(level: Int): Int {
        val safeLevel = maxOf(XpRewards.MIN_LEVEL, level)
        return XpRewards.MIN_XP_FOR_PROMOTION_BASE +
            (safeLevel - 1) * XpRewards.MIN_XP_FOR_PROMOTION_PER_LEVEL
    }

    /**
     * [R9.1] RESULTADO DE LIGA MELHORADO (substitui [determineLeagueOutcome] na Fase 3).
     *
     * Correções em relação à versão original:
     *  1. Ligas pequenas (< 5 participantes) deixam de travar o progresso:
     *     a promoção/rebaixamento passa a usar LIMIARES ABSOLUTOS DE XP.
     *  2. Em ligas cheias, estar no Top 20% só promove se o usuário também
     *     atingir o mínimo de XP da semana (anti-sandbagging / semanas vazias).
     *  3. Em ligas pequenas, só é rebaixado quem praticamente não participou.
     */
    fun determineLeagueOutcomeImproved(
        currentLevel: Int,
        rankAtClose: Int,
        totalParticipantsInLevel: Int,
        xpEarned: Int
    ): LeagueOutcome {
        if (rankAtClose < 1 || totalParticipantsInLevel < 1) {
            return LeagueOutcome.MAINTAINED
        }

        // Regra 1: liga pequena → limiares absolutos de XP (progresso nunca trava)
        if (totalParticipantsInLevel < XpRewards.MIN_PARTICIPANTS_FOR_TIER_MOVEMENT) {
            return when {
                xpEarned >= XpRewards.SMALL_LEAGUE_PROMOTION_XP -> LeagueOutcome.PROMOTED
                xpEarned < XpRewards.SMALL_LEAGUE_DEMOTION_XP && currentLevel > XpRewards.MIN_LEVEL -> LeagueOutcome.DEMOTED
                else -> LeagueOutcome.MAINTAINED
            }
        }

        // Regra 2: liga cheia → percentil + mínimo de XP para promoção
        val topPercent = rankAtClose.toDouble() / totalParticipantsInLevel.toDouble()
        return when {
            topPercent <= XpRewards.PROMOTION_THRESHOLD_PERCENT &&
                xpEarned >= minXpForPromotion(currentLevel) -> LeagueOutcome.PROMOTED
            topPercent >= XpRewards.DEMOTION_THRESHOLD_PERCENT &&
                currentLevel > XpRewards.MIN_LEVEL -> LeagueOutcome.DEMOTED
            else -> LeagueOutcome.MAINTAINED
        }
    }

    /**
     * [R19] NÍVEL FINAL PÓS-CICLO.
     *
     * UX Geriátrica: o rebaixamento de liga NUNCA pode colocar o usuário abaixo
     * do piso conquistado por XP acumulado. Promoções de liga aceleram o progresso;
     * o XP acumulado é o piso de proteção.
     */
    fun resolveFinalLevel(outcomeLevel: Int, xpFloorLevel: Int): Int {
        return maxOf(maxOf(XpRewards.MIN_LEVEL, outcomeLevel), maxOf(XpRewards.MIN_LEVEL, xpFloorLevel))
    }
}
