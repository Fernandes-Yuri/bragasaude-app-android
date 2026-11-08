package br.com.bragasaude.data.local

import androidx.room.Room
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.Date

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE, application = android.app.Application::class)
class ClinicalIntegrityPersistenceTest {
    private lateinit var db: BragaDatabase
    @Before fun open() { db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), BragaDatabase::class.java).allowMainThreadQueries().build() }
    @After fun close() { db.close() }

    @Test fun oldPendingVitalsAndMetricsSurviveRetention() = runBlocking {
        val dao = db.vitalSignDao()
        dao.insert(VitalSignEntity(remoteId = "pending", userId = "p", hydrationMl = 250, measuredAt = Date(1), pendingSync = true))
        dao.insert(VitalSignEntity(remoteId = "confirmed", userId = "p", hydrationMl = 250, measuredAt = Date(1)))
        dao.purgeOlderThan(100)
        assertEquals(listOf("pending"), dao.getAllOneShot("p").map { it.remoteId })
        db.dailyMetricsDao().insert(DailyMetricsEntity(userId = "p", date = "2020-01-01", pendingSync = true))
        db.dailyMetricsDao().purgeOlderThan("2026-01-01")
        assertNotNull(db.dailyMetricsDao().getByDate("p", "2020-01-01"))
    }

    @Test fun oldMetricsAcknowledgementCannotOverwriteNewSteps() = runBlocking {
        val dao = db.dailyMetricsDao()
        val sent = DailyMetricsEntity(userId = "p", date = "2026-11-07", steps = 100, pendingSync = true)
        dao.insert(sent)
        dao.insert(sent.copy(steps = 120))
        dao.acknowledgeSync(sent)
        assertEquals(120, dao.getByDate("p", sent.date)!!.steps)
        assertTrue(dao.getByDate("p", sent.date)!!.pendingSync)
        dao.acknowledgeSync(sent.copy(steps = 120))
        assertFalse(dao.getByDate("p", sent.date)!!.pendingSync)
    }

    @Test fun undoPreservesOtherSignalsAndCannotBeResurrectedByOldAckOrDownload() = runBlocking {
        val dao = db.vitalSignDao()
        val original = VitalSignEntity(remoteId = "id", userId = "p", hydrationMl = 500,
            systolicPressure = 120, diastolicPressure = 80, measuredAt = Date(10), pendingSync = true)
        val id = dao.insert(original)
        dao.undoHydration("p", 0, 100, true)
        dao.acknowledgeSync(original.copy(localId = id), "id")
        dao.cacheRemote(original.copy(pendingSync = false))
        val current = dao.getByLocalId(id)!!
        assertNull(current.hydrationMl)
        assertEquals(120, current.systolicPressure)
        assertTrue(current.hydrationUndoPending)
        assertTrue(current.pendingSync)
        dao.acknowledgeSync(current, "id")
        assertFalse(dao.getByLocalId(id)!!.pendingSync)
    }

    @Test fun waterOnlyUndoDisappearsButDurableCommandRemains() = runBlocking {
        val dao = db.vitalSignDao()
        dao.insert(VitalSignEntity(remoteId = "id", userId = "p", hydrationMl = 250, measuredAt = Date(10)))
        dao.undoHydration("p", 0, 100, true)
        assertTrue(dao.getAllOneShot("p").isEmpty())
        assertEquals(1, dao.getPendingSync().size)
        assertNotNull(dao.getPendingSync().single().deletedAt)
    }

    @Test fun readReceiptIsDurableAndDoesNotMarkFutureMessages() = runBlocking {
        val dao = db.familyDao()
        val message = FamilyMessageEntity(id = "seen", patientUserId = "p", senderName = "Other",
            senderUserId = "other", messageText = "test", iconType = "CUSTOM", sentAt = System.currentTimeMillis())
        dao.insertMessage(message)
        dao.insertMessage(message.copy(id = "future"))
        dao.markSeen("reader", listOf(message))
        assertTrue(dao.getMessageById("seen")!!.isRead)
        assertFalse(dao.getMessageById("future")!!.isRead)
        assertEquals(listOf("seen"), dao.pendingReceipts("reader").map { it.messageId })
        assertTrue(dao.pendingReceipts("other-account").isEmpty())
    }

    @Test fun upcomingConsultationsExcludeCompletedAndPast() = runBlocking {
        val dao = db.familyDao()
        val c = ConsultationEntity(id = "next", userId = "p", caregiverUserId = null,
            caregiverName = null, caregiverRelation = null, title = "Consulta", scheduledDate = Date(1000), status = "ACCEPTED")
        dao.insertConsultation(c)
        dao.insertConsultation(c.copy(id = "past", scheduledDate = Date(1)))
        dao.insertConsultation(c.copy(id = "done", status = "COMPLETED"))
        assertEquals(listOf("next"), dao.getActiveConsultationsForUser("p", 100).first().map { it.id })
    }
}
