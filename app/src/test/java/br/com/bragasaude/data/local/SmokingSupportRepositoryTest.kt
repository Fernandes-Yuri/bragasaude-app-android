package br.com.bragasaude.data.local

import android.app.Application
import androidx.room.Room
import br.com.bragasaude.data.local.smoking.*
import br.com.bragasaude.domain.smoking.*
import io.mockk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.*
import javax.inject.Provider

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE, application = Application::class)
class SmokingSupportRepositoryTest {
    private lateinit var db: BragaDatabase
    private lateinit var repo: SmokingSupportRepository
    private val owners = MutableStateFlow<String?>("A")
    private val session = mockk<SmokingSupportSession>()
    private val date = LocalDate.parse("2026-10-22")
    @Before fun setup() {
        owners.value = "A"
        every { session.currentUserId() } answers { owners.value }
        every { session.owners } returns owners
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), BragaDatabase::class.java)
            .allowMainThreadQueries().build()
        repo = SmokingSupportRepository(db, session, Clock.fixed(Instant.parse("2026-10-22T15:00:00Z"), ZoneOffset.UTC),
            Provider { ZoneId.of("America/Sao_Paulo") })
    }
    @After fun cleanup() = db.close()
    private suspend fun activate() = repo.activate(date, null, null, emptySet(), PreparationStage.STARTED)
    @Test fun switchAndLogoutNeverReturnPreviousOwnersDiary() = runBlocking {
        activate(); repo.saveDay(date, false, null)
        assertEquals(1, repo.snapshot.first { it.enabled }.days.size)
        owners.value = "B"
        assertTrue(repo.snapshot.first().days.isEmpty())
        activate(); repo.saveDay(date, true, null)
        assertTrue(repo.snapshot.first { it.days.isNotEmpty() }.days.single().smoked)
        owners.value = null
        assertNull(repo.snapshot.first().ownerId)
        try { repo.saveDay(date, false, null); fail("Sessão obrigatória") } catch (_: IllegalArgumentException) { }
    }
    @Test fun disableKeepsHistoryEraseSuppressesOfferAndReactivationIsExplicit() = runBlocking {
        activate(); repo.saveDay(date, false, null)
        repo.disable()
        assertFalse(db.smokingSupportDao().settings("A")!!.enabled)
        assertNotNull(db.smokingSupportDao().day("A", date.toString()))
        activate()
        assertNotNull(db.smokingSupportDao().day("A", date.toString()))
        repo.erase()
        assertNull(db.smokingSupportDao().plan("A"))
        assertEquals(OfferDecision.DECLINED, db.smokingSupportDao().settings("A")!!.offerDecision)
    }
    @Test fun sessionChangeDuringMutationRollsBackWrites() = runBlocking {
        every { session.currentUserId() } returnsMany listOf("A", "A", null)
        try { activate(); fail("Sessão mudou") } catch (_: IllegalStateException) { }
        assertNull(db.smokingSupportDao().settings("A"))
        assertNull(db.smokingSupportDao().plan("A"))
    }
    @Test fun invalidMutationDoesNotDamagePreviousDayOrPlan() = runBlocking {
        activate(); repo.saveDay(date, false, null)
        try { repo.saveDay(date, true, 0); fail("Zero inválido") } catch (_: IllegalArgumentException) { }
        assertEquals(0, db.smokingSupportDao().day("A", date.toString())!!.cigaretteCount)
        assertEquals(date.toString(), db.smokingSupportDao().plan("A")!!.startedOn)
    }
}
