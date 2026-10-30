package br.com.bragasaude.data.local

import android.app.Application
import androidx.room.Room
import br.com.bragasaude.data.remote.repository.MilestonesRepository
import io.mockk.mockk
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE, application = Application::class)
class HydrationRollbackPersistenceTest {
    private lateinit var db: BragaDatabase
    private val context get() = RuntimeEnvironment.getApplication()
    private val day = LocalDate.of(2026, 10, 30)
    private val start = day.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    private val end = day.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private fun open() {
        db = Room.databaseBuilder(context, BragaDatabase::class.java, "hydration-rollback.db")
            .allowMainThreadQueries().build()
    }

    @Before fun setup() { context.deleteDatabase("hydration-rollback.db"); open() }
    @After fun cleanup() { db.close(); context.deleteDatabase("hydration-rollback.db") }

    @Test fun rollbackRemovesLegacyDuplicatesEmitsToCollectorsAndSurvivesReopening() = runBlocking {
        val dao = db.milestoneDao()
        val repository = MilestonesRepository(dao, mockk())
        val full = MilestoneEntity(userId = "patient", title = "full", badgeType = "hydration_daily", achievedAt = Date(start))
        dao.insert(full.copy(remoteId = "legacy-random-id"))
        dao.insert(full.copy(remoteId = "duplicate", achievedAt = Date(end - 1)))
        dao.insert(full.copy(title = "yesterday", achievedAt = Date(start - 1)))
        dao.insert(full.copy(title = "tomorrow", achievedAt = Date(end)))
        dao.insert(full.copy(title = "another-account", userId = "another"))
        dao.insert(full.copy(title = "half", badgeType = "hydration_half"))
        dao.insert(full.copy(title = "weight", badgeType = "weight_goal"))
        assertEquals(6, repository.getMilestones("patient").first().size)
        // Mesma assinatura de Flow observada pela Home e pelo histórico.
        val updated = async(start = CoroutineStart.UNDISPATCHED) {
            withTimeout(5000) { repository.getMilestones("patient").first { it.size == 4 } }
        }
        repository.deleteMilestonesForDay("patient", "hydration_daily", day)
        assertEquals(setOf("yesterday", "tomorrow", "half", "weight"), updated.await().map { it.title }.toSet())
        assertEquals("another-account", repository.getMilestones("another").first().single().title)
        repository.deleteMilestonesForDay("patient", "hydration_half", day)
        db.close()
        open()
        assertEquals(setOf("yesterday", "tomorrow", "weight"), db.milestoneDao().getAll("patient").first().map { it.title }.toSet())
    }

    @Test fun undoUsesLatestLocalIdOnTimestampTieAndNeverDeletesOtherDaysOrAccounts() = runBlocking {
        val dao = db.vitalSignDao()
        val water = VitalSignEntity(userId = "patient", hydrationMl = 250, measuredAt = Date(start))
        dao.insert(water.copy(hydrationMl = 100, measuredAt = Date(start - 1)))
        dao.insert(water.copy(hydrationMl = 200, measuredAt = Date(end)))
        dao.insert(water.copy(userId = "another"))
        dao.insert(water.copy(hydrationMl = 300))
        dao.insert(water.copy(hydrationMl = 500))
        dao.deleteLastHydration("patient", start, end)
        assertEquals(setOf(100, 200, 300), dao.getAll("patient").first().map { it.hydrationMl }.toSet())
        repeat(2) { dao.deleteLastHydration("patient", start, end) }
        assertEquals(setOf(100, 200), dao.getAll("patient").first().map { it.hydrationMl }.toSet())
        assertEquals(250, dao.getAll("another").first().single().hydrationMl)
    }
}
