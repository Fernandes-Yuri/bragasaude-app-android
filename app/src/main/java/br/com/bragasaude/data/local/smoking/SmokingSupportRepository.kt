package br.com.bragasaude.data.local.smoking

import androidx.room.withTransaction
import br.com.bragasaude.data.local.BragaDatabase
import br.com.bragasaude.domain.smoking.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

data class SmokingSupportSnapshot(
    val ownerId: String? = null,
    val settings: SmokingSupportSettings? = null,
    val plan: SmokingSupportPlan? = null,
    val days: List<SmokingDailyEntry> = emptyList(),
    val cravings: List<SmokingCravingEntry> = emptyList()
) {
    val enabled: Boolean get() = settings?.enabled == true && settings.offerDecision == OfferDecision.ACCEPTED && plan != null
}

/** No remote transport, analytics, logging or selected-patient dependency. */
@Singleton
class SmokingSupportRepository @Inject constructor(
    private val database: BragaDatabase,
    private val session: SmokingSupportSession,
    @br.com.bragasaude.di.SmokingClock private val clock: Clock,
    @br.com.bragasaude.di.SmokingZone private val zoneProvider: javax.inject.Provider<ZoneId>
) {
    private val dao get() = database.smokingSupportDao()

    @OptIn(ExperimentalCoroutinesApi::class)
    val snapshot: Flow<SmokingSupportSnapshot> = session.owners.flatMapLatest { owner ->
        if (owner == null) flowOf(SmokingSupportSnapshot()) else dao.observeRecord(owner).map { record ->
            if (owner != session.currentUserId()) SmokingSupportSnapshot()
            else SmokingSupportSnapshot(owner, record?.settings, record?.plans?.singleOrNull(),
                record?.days.orEmpty().sortedByDescending { it.localDate },
                record?.cravings.orEmpty().sortedByDescending { it.occurredAt })
        }.onStart { emit(SmokingSupportSnapshot(ownerId = owner)) }
    }

    private suspend fun mutate(block: suspend (String, Long, ZoneId, SmokingSupportRules) -> Unit) {
        val owner = requireNotNull(session.currentUserId()) { "Entre na sua conta para usar o acompanhamento." }
        database.withTransaction {
            check(owner == session.currentUserId()) { "A sessão mudou." }
            val zone = zoneProvider.get()
            block(owner, clock.millis(), zone, SmokingSupportRules(clock, zone))
            // Roll back if logout/login occurred while waiting for Room.
            check(owner == session.currentUserId()) { "A sessão mudou." }
        }
    }

    suspend fun declineOffer() = mutate { owner, now, _, _ ->
        val old = dao.settings(owner)
        if (old == null || old.offerDecision == OfferDecision.UNSEEN)
            dao.saveSettings(SmokingSupportSettings(owner, OfferDecision.DECLINED, updatedAt = now))
    }

    suspend fun activate(startedOn: LocalDate, target: LocalDate?, reason: String?,
                         triggers: Set<SmokingTrigger>, stage: PreparationStage) = mutate { owner, now, _, rules ->
        rules.validatePlan(startedOn, target, reason, triggers)
        val previous = dao.plan(owner)
        // Re-enabling keeps the original start and diary. Editing the plan is explicit.
        val plan = previous ?: SmokingSupportPlan(owner, startedOn.toString(), target?.toString(),
            reason?.trim()?.takeIf { it.isNotEmpty() }, triggers.sortedBy { it.name }.joinToString(",") { it.name }, stage, now, now)
        dao.activate(SmokingSupportSettings(owner, OfferDecision.ACCEPTED, true, 1, now, now), plan)
    }

    suspend fun disable() = mutate { owner, now, _, _ ->
        val old = dao.settings(owner) ?: SmokingSupportSettings(owner, updatedAt = now)
        dao.saveSettings(old.copy(offerDecision = OfferDecision.DECLINED, enabled = false, updatedAt = now))
    }

    suspend fun erase() = mutate { owner, now, _, _ ->
        dao.erase(SmokingSupportSettings(owner, OfferDecision.DECLINED, updatedAt = now))
    }

    suspend fun saveDay(date: LocalDate, smoked: Boolean, count: Int?) = mutate { owner, now, zone, rules ->
        val plan = requireActivePlan(owner)
        val storedCount = if (smoked) count else 0
        rules.validateEntry(date, LocalDate.parse(plan.startedOn), smoked, storedCount)
        val previous = dao.day(owner, date.toString())
        dao.saveDay(SmokingDailyEntry(owner, date.toString(), smoked, storedCount,
            previous?.zoneId ?: zone.id, previous?.createdAt ?: now, now))
    }

    suspend fun deleteDay(date: LocalDate) = mutate { owner, _, _, _ -> dao.deleteDay(owner, date.toString()) }

    suspend fun recordCraving(intensity: CravingIntensity) = mutate { owner, now, zone, _ ->
        requireActivePlan(owner)
        dao.saveCraving(SmokingCravingEntry(UUID.randomUUID().toString(), owner, now, zone.id, intensity))
    }

    private suspend fun requireActivePlan(owner: String): SmokingSupportPlan {
        val settings = dao.settings(owner)
        check(settings?.enabled == true && settings.offerDecision == OfferDecision.ACCEPTED) { "Ative seu apoio primeiro." }
        return requireNotNull(dao.plan(owner))
    }
}
