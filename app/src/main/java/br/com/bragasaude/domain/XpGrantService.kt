package br.com.bragasaude.domain

import br.com.bragasaude.data.local.LeagueDao
import br.com.bragasaude.data.local.LeagueMembershipEntity
import br.com.bragasaude.data.local.ProfileDao
import br.com.bragasaude.data.local.XpAwardDao
import br.com.bragasaude.data.local.XpAwardEntity
import br.com.bragasaude.data.remote.api.BragaApiClient
import br.com.bragasaude.ui.util.NotificationHelper
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import br.com.bragasaude.util.BragaConstants

@Singleton
class XpGrantService @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val profileDao: ProfileDao,
    private val xpAwardDao: XpAwardDao,
    private val leagueDao: LeagueDao,
    private val apiClient: BragaApiClient
) {
    private val guestId = BragaConstants.GUEST_UID

    private val grantMutex = Mutex()

    /** Eventos de concessão para feedback imediato na UI (Snackbar global). */
    private val _xpEvents = MutableSharedFlow<XpGrantResult>(extraBufferCapacity = 16)
    val xpEvents: SharedFlow<XpGrantResult> = _xpEvents.asSharedFlow()

    /**
     * Concede XP para uma ação já VALIDADA pelo chamador.
     *
     * @param userId usuário autenticado (ou guest)
     * @param action tipo de ação de gamificação
     * @param isActionValid resultado da avaliação de domínio (ex: [GamificationEngine.isVitalsInTarget])
     * @param invalidReason motivo amigável quando a ação não pontua
     * @param at momento da ação (default: agora)
     */
    suspend fun grantXp(
        userId: String,
        action: GamificationActionType,
        isActionValid: Boolean,
        invalidReason: String = "Ação fora dos critérios de pontuação",
        at: Date = Date()
    ): XpGrantResult = grantMutex.withLock {
        val profile = profileDao.getProfileOneShot(userId)
            ?: return@withLock denied(action, "Perfil não encontrado")

        if (!isActionValid) {
            return@withLock denied(action, invalidReason)
        }

        val today = LocalDate.now()
        val dateStr = today.toString() // yyyy-MM-dd

        // ---- [R3] Anti-farming: uma concessão por tipo de ação por dia ----
        val awardedToday = xpAwardDao.getAwardedActionTypes(userId, dateStr)
            .mapNotNull { runCatching { GamificationActionType.valueOf(it) }.getOrNull() }
            .toSet()
        if (!GamificationEngine.canAwardDailyAction(action, awardedToday)) {
            return@withLock denied(action, "Você já pontuou por isso hoje.")
        }

        // ---- [R1] Streak ----
        val lastXpDate = profile.lastXpAt?.toInstant()
            ?.atZone(ZoneId.systemDefault())
            ?.toLocalDate()
        val newStreak = GamificationEngine.calculateStreak(profile.currentStreak, lastXpDate, today)

        // ---- [R16] Multiplicador de streak ----
        val multiplier = GamificationEngine.getStreakMultiplier(newStreak)
        var finalXp = (baseXpFor(action) * multiplier).toInt().coerceAtLeast(1)

        // ---- [R2] Bônus semanal de streak (7, 14, 21, 28...) ----
        val streakBonusXp = if (GamificationEngine.isStreakBonusEligible(newStreak)) {
            XpRewards.STREAK_7_DAYS_BONUS_XP
        } else {
            0
        }
        finalXp += streakBonusXp

        // ---- [R13/R14] Curva de nível por XP acumulado ----
        val newTotalXp = maxOf(0, profile.totalXp) + finalXp
        val newLevel = GamificationEngine.levelFromTotalXp(newTotalXp)
        val leveledUp = newLevel > maxOf(XpRewards.MIN_LEVEL, profile.currentLevel)

        val updatedProfile = profile.copy(
            currentXp = newTotalXp - GamificationEngine.xpForLevel(newLevel),
            totalXp = newTotalXp,
            currentLevel = newLevel,
            currentStreak = newStreak,
            lastXpAt = at,
            updatedAt = Date(),
            pendingSync = true
        )
        profileDao.insert(updatedProfile)

        // ---- Registro anti-farming ----
        xpAwardDao.insert(
            XpAwardEntity(
                userId = userId,
                date = dateStr,
                actionType = action.name,
                xp = finalXp,
                awardedAt = Date()
            )
        )

        // ---- [Fase 3] XP da liga semanal ----
        addLeagueXp(userId, newLevel, finalXp)

        // ---- Sync do perfil (best-effort) ----
        syncProfileGamification(updatedProfile)

        val result = XpGrantResult(
            actionType = action,
            awarded = true,
            xp = finalXp,
            streakBonusXp = streakBonusXp,
            newStreak = newStreak,
            newLevel = newLevel,
            leveledUp = leveledUp,
            reason = ""
        )
        _xpEvents.tryEmit(result)

        // doc 10 §4.2: level-up com app em background. O XpToastHost só mostra a
        // Snackbar com a UI aberta; o SharedFlow não persiste, e o evento seria
        // descartado. A notificação local garante que a conquista não se perca.
        if (leveledUp) {
            try {
                NotificationHelper.sendLevelUpNotification(appContext, newLevel)
            } catch (e: Exception) {
                // best-effort: a concessão de XP já foi gravada.
            }
        }
        result
    }

    /**
     * Concede XP de META DE PASSOS com o anti-fraude da Fase 2:
     * só pontua se a confiabilidade da sessão for suficiente ([GamificationEngine.isActivityXPEligible]).
     */
    suspend fun grantStepGoalXp(
        userId: String,
        stepsTaken: Int,
        stepGoal: Int,
        reliabilityScore: Float
    ): XpGrantResult {
        val goalHit = GamificationEngine.isStepGoalHit(stepsTaken, stepGoal)
        val reliable = GamificationEngine.isActivityXPEligible(reliabilityScore)
        val reason = when {
            !goalHit -> "Meta de passos ainda não atingida"
            !reliable -> "Meta de passos atingida! Para pontuar na liga, procure caminhar com GPS ativo e sinal estável."
            else -> ""
        }
        return grantXp(
            userId = userId,
            action = GamificationActionType.STEP_GOAL_HIT,
            isActionValid = goalHit && reliable,
            invalidReason = reason
        )
    }

    // ------------------------------------------------------------------
    // LIGA SEMANAL
    // ------------------------------------------------------------------

    /**
     * Soma o XP ganho ao membership do ciclo ativo do nível do usuário.
     * Se ainda não ingressou, o auto-join acontece no próximo sync da Liga.
     */
    private suspend fun addLeagueXp(userId: String, level: Int, xpDelta: Int) {
        if (userId == guestId) return
        val cycle = leagueDao.getActiveCycleOneShot(level) ?: return
        val membership = leagueDao.getMyMembershipOneShot(userId, cycle.id) ?: return

        val updated = membership.copy(
            xpEarned = membership.xpEarned + xpDelta,
            userLevel = level,
            pendingSync = true
        )
        leagueDao.insertMembership(updated)

        leagueDao.insertMembership(updated.copy(pendingSync = false))
    }

    // ------------------------------------------------------------------
    // SYNC DE NUVEM (best-effort; falha vira pendingSync para o SyncWorker)
    // ------------------------------------------------------------------

    private suspend fun syncProfileGamification(profile: br.com.bragasaude.data.local.ProfileEntity) {
        if (profile.userId == guestId) return
        try {
            apiClient.syncProfile(profile)
            profileDao.insert(profile.copy(pendingSync = false))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun baseXpFor(action: GamificationActionType): Int = when (action) {
        GamificationActionType.VITALS_RECORDED -> XpRewards.VITALS_IN_TARGET_XP
        GamificationActionType.MEDICATION_TAKEN_ON_TIME -> XpRewards.MEDICATION_ON_TIME_XP
        GamificationActionType.STEP_GOAL_HIT -> XpRewards.STEP_GOAL_HIT_XP
        GamificationActionType.HYDRATION_GOAL_HIT -> XpRewards.HYDRATION_GOAL_HIT_XP
        GamificationActionType.SAFE_MEAL_RECORDED -> XpRewards.SAFE_MEAL_XP
        GamificationActionType.MILESTONE_ACHIEVED -> XpRewards.MILESTONE_ACHIEVED_XP
        GamificationActionType.STREAK_BONUS -> 0
    }

    private fun denied(action: GamificationActionType, reason: String): XpGrantResult {
        val result = XpGrantResult(
            actionType = action,
            awarded = false,
            xp = 0,
            streakBonusXp = 0,
            newStreak = 0,
            newLevel = 1,
            leveledUp = false,
            reason = reason
        )
        if (reason.isNotBlank() && reason != "Ação fora dos critérios de pontuação" && reason != "Perfil não encontrado") {
            _xpEvents.tryEmit(result)
        }
        return result
    }
}

/**
 * Resultado de uma tentativa de concessão de XP.
 */
data class XpGrantResult(
    val actionType: GamificationActionType,
    val awarded: Boolean,
    val xp: Int,
    val streakBonusXp: Int,
    val newStreak: Int,
    val newLevel: Int,
    val leveledUp: Boolean,
    val reason: String
) {
    /** Mensagem positiva no tom da UX geriátrica (sem termos punitivos). */
    val displayMessage: String
        get() = when {
            !awarded -> reason
            leveledUp -> "+$xp XP - Você subiu para o Nivel $newLevel!"
            streakBonusXp > 0 -> "+$xp XP (com bonus de sequencia)"
            else -> "+$xp XP"
        }
}
