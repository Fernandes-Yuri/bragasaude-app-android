package br.com.bragasaude.data.remote.repository

import br.com.bragasaude.data.local.LeagueCycleEntity
import br.com.bragasaude.data.local.LeagueDao
import br.com.bragasaude.data.local.LeagueMembershipEntity
import br.com.bragasaude.data.local.ProfileDao
import br.com.bragasaude.data.remote.api.BragaApiClient
import br.com.bragasaude.data.remote.sync.SyncScheduler
import br.com.bragasaude.domain.GamificationEngine
import br.com.bragasaude.domain.LeagueOutcome
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import br.com.bragasaude.util.BragaConstants

@Singleton
class LeagueRepository @Inject constructor(
    private val leagueDao: LeagueDao,
    private val profileDao: ProfileDao,
    private val apiClient: BragaApiClient,
    private val syncScheduler: SyncScheduler
) {
    private val guestId = BragaConstants.GUEST_UID

    /** Mensagem de encerramento de ciclo (UX geriátrica positiva) para exibição na UI. */
    private val _cycleOutcomeMessage = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val cycleOutcomeMessage: SharedFlow<String> = _cycleOutcomeMessage.asSharedFlow()

    fun getActiveCycle(level: Int): Flow<LeagueCycleEntity?> = leagueDao.getActiveCycle(level)

    fun getMyMembership(userId: String, cycleId: String): Flow<LeagueMembershipEntity?> =
        leagueDao.getMyMembership(userId, cycleId)

    fun getRanking(cycleId: String): Flow<List<LeagueMembershipEntity>> =
        leagueDao.getRanking(cycleId)

    suspend fun fetchAndSyncLeagueData(userId: String, level: Int, depth: Int = 0) {
        if (userId == guestId) return
        ensureLocalWeeklyCycle(userId, level)
    }

    /**
     * Fallback de ciclo semanal local caso o backend remoto esteja indisponível
     * ou ainda não tenha ciclo ativo cadastrado para o nível do usuário.
     */
    private suspend fun ensureLocalWeeklyCycle(userId: String, level: Int) {
        val now = LocalDate.now()
        val monday = now.with(java.time.DayOfWeek.MONDAY)
        val sunday = now.with(java.time.DayOfWeek.SUNDAY)
        val fallbackCycleId = "local-cycle-${monday}-lvl$level"

        val existing = leagueDao.getCycleOneShot(fallbackCycleId)
        val cycleEntity = if (existing == null) {
            val created = LeagueCycleEntity(
                id = fallbackCycleId,
                level = level,
                weekStartDate = monday.toString(),
                weekEndDate = sunday.toString(),
                status = "active"
            )
            leagueDao.insertCycle(created)
            created
        } else {
            existing
        }

        val existingMember = leagueDao.getMyMembershipOneShot(userId, fallbackCycleId)
        if (existingMember == null) {
            val profile = profileDao.getProfileOneShot(userId)
            // AUD-AN39: era pendingSync = false — a filiação local era gravada
            // como "sincronizada" sem nunca ter ido ao Postgres. Divergência
            // silenciosa entre aparelhos (cuidador via XP diferente do
            // paciente). Agora entra na fila do SyncWorker.
            val memberEntity = LeagueMembershipEntity(
                id = UUID.randomUUID().toString(),
                userId = userId,
                userName = profile?.fullName ?: "Você",
                userLevel = profile?.currentLevel ?: level,
                userStreak = profile?.currentStreak ?: 0,
                leagueCycleId = fallbackCycleId,
                xpEarned = 0,
                rankAtClose = null,
                outcome = null,
                pendingSync = true
            )
            leagueDao.insertMembership(memberEntity)
        }

        processCycleCloseIfNeeded(userId, cycleEntity, 0)
    }

    // ------------------------------------------------------------------
    // FASE 3 — ENCERRAMENTO DE CICLO SEMANAL (promoção / manutenção / rebaixamento)
    // ------------------------------------------------------------------

    private suspend fun processCycleCloseIfNeeded(userId: String, cycle: LeagueCycleEntity, depth: Int = 0) {
        if (depth > 2) return
        if (cycle.status != "active") return

        val endDate = try {
            LocalDate.parse(cycle.weekEndDate)
        } catch (e: Exception) {
            return
        }
        if (LocalDate.now() <= endDate) return

        val memberships = leagueDao.getRankingOneShot(cycle.id)
        if (memberships.isEmpty()) {
            leagueDao.insertCycle(cycle.copy(status = "closed"))
            return
        }

        val totalParticipants = memberships.size
        var myOutcome: LeagueOutcome? = null
        var myFinalLevel = 0

        memberships.forEachIndexed { index, member ->
            val rank = index + 1
            val outcome = GamificationEngine.determineLeagueOutcomeImproved(
                currentLevel = member.userLevel,
                rankAtClose = rank,
                totalParticipantsInLevel = totalParticipants,
                xpEarned = member.xpEarned
            )
            val outcomeLevel = GamificationEngine.calculateNewLevel(member.userLevel, outcome)

            val floorLevel = if (member.userId == userId) {
                val profile = profileDao.getProfileOneShot(userId)
                GamificationEngine.levelFromTotalXp(profile?.totalXp ?: 0)
            } else {
                member.userLevel
            }
            val finalLevel = GamificationEngine.resolveFinalLevel(outcomeLevel, floorLevel)

            leagueDao.insertMembership(
                member.copy(rankAtClose = rank, outcome = outcome.key, userLevel = finalLevel)
            )

            if (member.userId == userId) {
                myOutcome = outcome
                myFinalLevel = finalLevel
            }
        }

        leagueDao.insertCycle(cycle.copy(status = "closed"))

        val outcome = myOutcome ?: LeagueOutcome.MAINTAINED
        val finalLevel = if (myFinalLevel > 0) myFinalLevel else maxOf(1, profileDao.getProfileOneShot(userId)?.currentLevel ?: 1)
        profileDao.getProfileOneShot(userId)?.let { profile ->
            // AUD-AN39: era pendingSync = false — level do perfil gravado como
            // sincronizado sem ir ao Postgres. Agora entra na fila.
            profileDao.insert(profile.copy(currentLevel = finalLevel, updatedAt = java.util.Date(), pendingSync = true))
        }

        _cycleOutcomeMessage.tryEmit(GamificationEngine.buildOutcomeMessage(outcome, finalLevel))

        fetchAndSyncLeagueData(userId, finalLevel, depth + 1)
    }

    suspend fun joinLeagueCycle(userId: String, cycleId: String) {
        val memberId = UUID.randomUUID().toString()
        val profile = profileDao.getProfileOneShot(userId)

        val localEntity = LeagueMembershipEntity(
            id = memberId,
            userId = userId,
            userName = profile?.fullName ?: "Você",
            userLevel = profile?.currentLevel ?: 1,
            userStreak = profile?.currentStreak ?: 0,
            leagueCycleId = cycleId,
            xpEarned = 0,
            pendingSync = false
        )
        leagueDao.insertMembership(localEntity)
    }

    private fun triggerSync() {
        syncScheduler.scheduleSync()
    }
}
