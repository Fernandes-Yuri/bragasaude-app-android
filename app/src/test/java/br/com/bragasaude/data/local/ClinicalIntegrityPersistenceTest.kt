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

    @Test fun legacyManualAndVoiceMigrationIsIdempotentAndSurvivesInterruptedAck() = runBlocking {
        val wearableDao = db.wearableReadingDao()
        val vitalDao = db.vitalSignDao()
        val uuid = "11111111-2222-4333-8444-555555555555"
        wearableDao.upsert(
            listOf(
                WearableReading("mariana", "manual:$uuid", "HEART_RATE", 103.0, 10_000L, "braga.manual", null),
                WearableReading("mariana", "legacy-non-uuid-spo2", "OXYGEN_SATURATION", 98.5, 20_000L, "braga.voice", null),
                WearableReading("mariana", "hc-1", "HEART_RATE", 71.0, 30_000L, "com.google.android.apps.fitness", "Watch")
            )
        )
        val firstRun = vitalDao.migrateLegacyManualReadings("mariana")
        assertEquals(2, firstRun.size)
        assertTrue(wearableDao.getLegacyManualAndVoiceReadingsForUser("mariana").isEmpty())
        assertEquals(1, wearableDao.getForReport("mariana", 0L).size)

        // Re-inserting legacy rows to simulate interrupted deletion or repeated migration must not duplicate records
        wearableDao.upsert(
            listOf(
                WearableReading("mariana", "manual:$uuid", "HEART_RATE", 103.0, 10_000L, "braga.manual", null),
                WearableReading("mariana", "legacy-non-uuid-spo2", "OXYGEN_SATURATION", 98.5, 20_000L, "braga.voice", null)
            )
        )
        val secondRun = vitalDao.migrateLegacyManualReadings("mariana")
        assertEquals(2, secondRun.size)
        val allVitals = vitalDao.getAllOneShot("mariana")
        assertEquals(2, allVitals.size)
        assertEquals(98.5, allVitals.first { it.oxygenSaturation != null }.oxygenSaturation!!, 0.001)
        assertEquals("braga.voice", allVitals.first { it.oxygenSaturation != null }.confirmedVia)
        assertEquals(103, allVitals.first { it.heartRate != null }.heartRate)
        assertEquals("braga.manual", allVitals.first { it.heartRate != null }.confirmedVia)
    }

    @Test fun fullCycleSaveSyncLogoutClearAndReloginRestoresDecimalOxygenAndOrigin() = runBlocking {
        val serverRecords = linkedMapOf<String, org.json.JSONObject>()
        var revisionCounter = 0L
        val server = okhttp3.mockwebserver.MockWebServer()
        server.dispatcher = object : okhttp3.mockwebserver.Dispatcher() {
            override fun dispatch(request: okhttp3.mockwebserver.RecordedRequest): okhttp3.mockwebserver.MockResponse {
                val path = request.path.orEmpty()
                return when {
                    request.method == "POST" && path == "/api/sync/vitals" -> {
                        val body = org.json.JSONObject(request.body.readUtf8())
                        val id = body.getString("id")
                        revisionCounter++
                        val stored = org.json.JSONObject().apply {
                            put("id", id)
                            put("user_id", body.getString("userId"))
                            if (body.has("heartRate")) put("heart_rate", body.getInt("heartRate")) else put("heart_rate", org.json.JSONObject.NULL)
                            if (body.has("oxygenSaturation")) put("oxygen_saturation", body.getDouble("oxygenSaturation")) else put("oxygen_saturation", org.json.JSONObject.NULL)
                            put("systolic_pressure", org.json.JSONObject.NULL)
                            put("diastolic_pressure", org.json.JSONObject.NULL)
                            put("glucose_level", org.json.JSONObject.NULL)
                            put("glucose_type", org.json.JSONObject.NULL)
                            put("hydration_ml", org.json.JSONObject.NULL)
                            put("measured_at", body.getString("measuredAt"))
                            put("confirmed_via", body.optString("confirmedVia", "app"))
                            put("confirmation_code", org.json.JSONObject.NULL)
                            put("deleted_at", org.json.JSONObject.NULL)
                            put("revision", revisionCounter)
                        }
                        serverRecords[id] = stored
                        okhttp3.mockwebserver.MockResponse().setResponseCode(200)
                            .setBody("""{"status":"success","id":"$id","vitalsContract":2}""")
                    }
                    request.method == "GET" && path.startsWith("/api/vitals/mariana/changes") -> {
                        if (request.getHeader("X-Braga-Vitals-Contract") != "2" && !path.contains("contract=2")) {
                            return okhttp3.mockwebserver.MockResponse().setResponseCode(409)
                                .setBody("""{"detail":"Atualize para contract=2"}""")
                        }
                        val changes = org.json.JSONArray()
                        for (record in serverRecords.values) {
                            changes.put(org.json.JSONObject().apply {
                                put("revision", record.getLong("revision"))
                                put("vital", record)
                            })
                        }
                        val resp = org.json.JSONObject().apply {
                            put("vitalsContract", 2)
                            put("changes", changes)
                            put("nextCursor", revisionCounter)
                            put("hasMore", false)
                        }
                        okhttp3.mockwebserver.MockResponse().setResponseCode(200).setBody(resp.toString())
                    }
                    else -> okhttp3.mockwebserver.MockResponse().setResponseCode(404)
                }
            }
        }
        server.start()
        try {
            val authService = io.mockk.mockk<br.com.bragasaude.data.remote.auth.AuthService>(relaxed = true) {
                io.mockk.coEvery { getFreshToken(any()) } returns "token-mariana"
                io.mockk.every { getTokenBlocking(any(), any()) } returns "token-mariana"
                io.mockk.every { cachedTokenNow() } returns "token-mariana"
                io.mockk.every { currentUserId } returns "mariana"
            }
            val api = br.com.bragasaude.data.remote.api.BragaApiClient(
                context = RuntimeEnvironment.getApplication(),
                _baseUrl = server.url("/").toString().removeSuffix("/"),
                authService = authService,
                httpClient = okhttp3.OkHttpClient(),
                familyE2eeManager = io.mockk.mockk(relaxed = true)
            )
            val syncScheduler = io.mockk.mockk<br.com.bragasaude.data.remote.sync.SyncScheduler>(relaxed = true)
            val repo = br.com.bragasaude.data.remote.repository.VitalsRepository(
                apiClient = api,
                vitalSignDao = db.vitalSignDao(),
                profileDao = db.profileDao(),
                familyDao = db.familyDao(),
                vitalAlertLogDao = db.vitalAlertLogDao(),
                syncScheduler = syncScheduler,
                appContext = RuntimeEnvironment.getApplication()
            )

            // 1. Save 103 bpm (manual) and 98,5% (voice)
            val heartRes = repo.saveManualHealthReading("mariana", br.com.bragasaude.domain.HealthReadingInput.HEART, "103", fromVoice = false, measuredAtMillis = 1_762_600_000_000L)
            val oxygenRes = repo.saveManualHealthReading("mariana", br.com.bragasaude.domain.HealthReadingInput.OXYGEN, "98,5", fromVoice = true, measuredAtMillis = 1_762_600_060_000L)
            assertEquals(br.com.bragasaude.domain.HealthReadingSaveStatus.SYNCED, heartRes.status)
            assertEquals(br.com.bragasaude.domain.HealthReadingSaveStatus.SYNCED, oxygenRes.status)

            // Retry with the same UUID (simulating lost ack) must not create duplicate on server or locally
            val retryRes = repo.saveManualHealthReading(
                userId = "mariana",
                metric = br.com.bragasaude.domain.HealthReadingInput.OXYGEN,
                rawInput = "98,5",
                fromVoice = true,
                measuredAtMillis = 1_762_600_060_000L,
                recordId = oxygenRes.remoteId!!
            )
            assertEquals(br.com.bragasaude.domain.HealthReadingSaveStatus.SYNCED, retryRes.status)
            assertEquals(2, serverRecords.size)
            assertTrue(db.vitalSignDao().getPendingSync().isEmpty())

            // 2. Simulate logout (clearAllTables) after confirming no pending data
            db.clearAllTables()
            assertTrue(db.vitalSignDao().getAllOneShot("mariana").isEmpty())

            // 3. Simulate re-login and delta restore from server
            val page = api.getVitalChanges("mariana", 0L)
            requireNotNull(page)
            db.vitalSignDao().cachePage("mariana", "mariana", page.records, page.nextCursor)

            val restoredVitals = db.vitalSignDao().getAllOneShot("mariana")
            assertEquals(2, restoredVitals.size)
            val heartHistory = br.com.bragasaude.domain.HealthReadingHistory.merge(
                "mariana", br.com.bragasaude.domain.HealthReadingInput.HEART, emptyList(), restoredVitals
            )
            val oxygenHistory = br.com.bragasaude.domain.HealthReadingHistory.merge(
                "mariana", br.com.bragasaude.domain.HealthReadingInput.OXYGEN, emptyList(), restoredVitals
            )
            assertEquals(103.0, heartHistory.single().value, 0.001)
            assertEquals(br.com.bragasaude.domain.HealthReadingInput.MANUAL, heartHistory.single().sourcePackage)
            assertEquals(98.5, oxygenHistory.single().value, 0.001)
            assertEquals(br.com.bragasaude.domain.HealthReadingInput.VOICE, oxygenHistory.single().sourcePackage)
            assertNull(oxygenHistory.single().deviceModel)
        } finally {
            server.shutdown()
        }
    }
}
