package br.com.bragasaude.data.local

import android.app.Application
import androidx.room.Room
import br.com.bragasaude.data.local.smoking.*
import br.com.bragasaude.domain.smoking.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE, application = Application::class)
class SmokingSupportPersistenceTest {
    private lateinit var db: BragaDatabase
    private val dao get() = db.smokingSupportDao()
    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), BragaDatabase::class.java)
            .allowMainThreadQueries().build()
    }
    @After fun cleanup() = db.close()
    private suspend fun activate(owner: String) {
        dao.activate(SmokingSupportSettings(owner, OfferDecision.ACCEPTED, true, 1, 1, 1),
            SmokingSupportPlan(owner, "2026-10-20", stage = PreparationStage.PREPARING, createdAt = 1, updatedAt = 1))
    }
    @Test fun editIsIdempotentAndOwnerIsolationSurvivesErasure() = runBlocking {
        activate("A"); activate("B")
        val a = SmokingDailyEntry("A", "2026-10-20", false, 0, "America/Sao_Paulo", 1, 1)
        dao.saveDay(a)
        dao.saveDay(a.copy(smoked = true, cigaretteCount = null, updatedAt = 2))
        dao.saveDay(a.copy(userId = "B"))
        dao.saveCraving(SmokingCravingEntry("c", "A", 1, "UTC", CravingIntensity.HIGH))
        assertEquals(1, dao.observeDays("A").first().size)
        assertNull(dao.observeDays("A").first().single().cigaretteCount)
        dao.erase(SmokingSupportSettings("A", OfferDecision.DECLINED, updatedAt = 3))
        assertNull(dao.plan("A"))
        assertTrue(dao.observeDays("A").first().isEmpty())
        assertTrue(dao.observeCravings("A").first().isEmpty())
        assertEquals(OfferDecision.DECLINED, dao.settings("A")!!.offerDecision)
        assertEquals(1, dao.observeDays("B").first().size)
        assertNotNull(dao.plan("B"))
    }
    @Test fun failedPlanInsertRollsBackActivation() = runBlocking {
        // Aborts the second write to demonstrate the transaction, not just input validation.
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER reject_plan BEFORE INSERT ON smoking_plan_local BEGIN SELECT RAISE(ABORT, 'test'); END")
        var failed = false
        try { activate("A") } catch (_: android.database.SQLException) { failed = true }
        assertTrue(failed)
        assertNull(dao.settings("A"))
    }
    @Test fun clearAllTablesIncludesModuleAndDefensiveConvertersDoNotEnableUnknownDecisions() = runBlocking {
        activate("A")
        db.clearAllTables()
        assertNull(dao.settings("A"))
        assertNull(dao.plan("A"))
        val converters = SmokingSupportConverters()
        assertEquals(OfferDecision.DECLINED, converters.decision("FUTURE"))
        assertNull(converters.intensity("FUTURE"))
    }
}
