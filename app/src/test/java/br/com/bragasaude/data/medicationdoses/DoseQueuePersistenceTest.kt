package br.com.bragasaude.data.medicationdoses

import android.app.Application
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.work.WorkManager
import br.com.bragasaude.data.local.*
import br.com.bragasaude.data.remote.api.BragaApiClient
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import io.mockk.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],manifest=Config.NONE,application=Application::class)
class DoseQueuePersistenceTest {
    private val context get()=RuntimeEnvironment.getApplication()
    private lateinit var db:BragaDatabase
    private lateinit var repo:MedicationDoseRepository
    private val api=mockk<BragaApiClient>()
    private val auth=mockk<FirebaseAuth>()
    private val user=mockk<FirebaseUser>()
    private var actor="patient"
    private val wm=mockk<WorkManager>(relaxed=true)
    private var proposal=JSONObject()
    private var committed:JSONObject?=null
    private var timeout=false
    private var reject=false
    private fun group():DoseGroup {
        val at=Instant.now().minusSeconds(3600).atZone(ZoneId.of("UTC"))
        return DoseGroup.parse(JSONObject().put("group_id","group").put("patient_id","patient").put("patient_name","Maria")
            .put("timezone","UTC").put("local_date",at.toLocalDate().toString()).put("local_time",at.toLocalTime().withSecond(0).withNano(0).toString())
            .put("scheduled_at",at.toInstant().toString()).put("schedule_digest","digest")
            .put("items",JSONArray((1..12).map { id -> JSONObject().put("occurrence_id","dose$id").put("medication_id","med$id")
                .put("name","Med $id").put("dose_units",id%3+1).put("instruction_text",JSONObject.NULL)
                .put("schedule_revision",1).put("scheduled_at",at.toInstant().toString()).put("state","pending") })))
    }
    private fun open() {
        db=Room.databaseBuilder(context,BragaDatabase::class.java,"dose-test.db").allowMainThreadQueries().addMigrations(*Migrations.ALL).build()
        repo=MedicationDoseRepository(context,db,api,auth)
    }
    @Before fun before() {
        context.deleteDatabase("dose-test.db")
        every { auth.currentUser } returns user
        every { user.uid } answers { actor }
        mockkStatic(WorkManager::class)
        every { WorkManager.getInstance(any()) } returns wm
        coEvery { api.getMedicationStock(any()) } returns emptyList()
        coEvery { api.getMedicationLogs(any(),any()) } returns emptyList()
        coEvery { api.doseRequest(any(),any(),any()) } coAnswers {
            val method=firstArg<String>();val path=secondArg<String>();val body=thirdArg<JSONObject?>()
            when {
                method=="POST" && path.endsWith("medication-dose-batches") -> {
                    if(reject) BragaApiClient.ApiResponse(JSONObject().put("detail",JSONObject().put("code","SCHEDULE_CHANGED").put("message","Cadastro alterado")),409,"Cadastro alterado")
                    else { proposal=JSONObject(groupPayload).put("batch_id","batch").put("state","prepared").put("snapshot_digest","snapshot")
                        BragaApiClient.ApiResponse(proposal,201,null) }
                }
                method=="GET" && path.endsWith("/batch") -> BragaApiClient.ApiResponse(committed ?: proposal,200,null)
                method=="POST" && path.endsWith("/confirm") -> {
                    val selected=DoseProtocol.selection(body!!.getJSONArray("selected_occurrence_ids").toString())
                    val array=JSONArray()
                    val items=proposal.getJSONArray("items")
                    for(i in 0 until items.length()) {
                        val item=JSONObject(items.getJSONObject(i).toString());val id=item.getString("occurrence_id")
                        item.put("status",if(id in selected) "recorded" else "excluded")
                        if(id in selected) item.put("log_id","log-$id")
                        array.put(item)
                    }
                    committed=JSONObject(proposal.toString()).put("state","confirmed").put("items",array)
                        .put("counts",JSONObject().put("recorded",selected.size).put("already_recorded",0)
                            .put("excluded",12-selected.size).put("blocked",0))
                    if(timeout) BragaApiClient.ApiResponse(null,0,"Timeout") else BragaApiClient.ApiResponse(committed,200,null)
                }
                else -> BragaApiClient.ApiResponse(null,0,"Offline")
            }
        }
        open()
    }
    private var groupPayload=""
    private suspend fun cache():DoseGroup {
        val group=group();groupPayload=group.payload
        db.medicationDoseDao().putGroup(DoseGroupEntity("patient",group.id,"patient",group.date,group.instant.toEpochMilli(),group.payload,System.currentTimeMillis()))
        return group
    }
    @After fun after() { db.close();context.deleteDatabase("dose-test.db");unmockkStatic(WorkManager::class) }
    @Test fun twelveDosesAndExclusionsProduceOneBatchAndOnlySelectedLogs()=runBlocking {
        val group=cache();val selected=group.items.dropLast(2).map { it.occurrenceId }.toSet()
        repo.submit(group,selected)
        assertTrue(repo.flush())
        assertEquals(10,db.medicationLogDao().getPendingSync().size + repo.reservations("patient").size)
        assertTrue(db.medicationLogDao().getPendingSync().isEmpty())
        coVerify(exactly=1) { api.doseRequest("POST",match { it.endsWith("/confirm") },any()) }
    }
    @Test fun timeoutAfterCommitAndRestartRecoverWithoutSecondConfirmation()=runBlocking {
        val group=cache();repo.submit(group,group.items.map { it.occurrenceId }.toSet())
        timeout=true
        assertFalse(repo.flush())
        db.close();open()
        assertTrue(repo.flush())
        assertEquals(12,repo.reservations("patient").size)
        coVerify(exactly=1) { api.doseRequest("POST",match { it.endsWith("/confirm") },any()) }
        assertEquals("RESOLVED",db.medicationDoseDao().intents("patient","patient").first().first().state)
    }
    @Test fun repeatedTapAndChangedAccountCannotCreateAnotherIntent()=runBlocking {
        val group=cache();val selected=group.items.map { it.occurrenceId }.toSet()
        repo.submit(group,selected)
        try { repo.submit(group,selected);fail("Duplicate accepted") } catch(_:IllegalArgumentException) { }
        actor="other"
        try { repo.submit(group,selected);fail("Old snapshot accepted by another account") } catch(_:IllegalArgumentException) { }
        assertTrue(repo.flush())
        coVerify(exactly=0) { api.doseRequest("POST",any(),any()) }
    }
    @Test fun changedScheduleIsReviewAndNeverCreatesIndividualLogs()=runBlocking {
        val group=cache();repo.submit(group,group.items.map { it.occurrenceId }.toSet());reject=true
        assertTrue(repo.flush())
        assertTrue(repo.reservations("patient").isEmpty())
        assertTrue(db.medicationLogDao().getPendingSync().isEmpty())
        coVerify(exactly=0) { api.doseRequest("POST",match { it.endsWith("/confirm") },any()) }
    }
    @Test fun upgradeFrom53KeepsOldLogsAndCreatesDurableQueue()=runBlocking {
        db.medicationLogDao().insert(MedicationLogEntity("old","patient","med",pendingSync=true))
        db.close()
        val sqlite=SQLiteDatabase.openDatabase(context.getDatabasePath("dose-test.db").path,null,SQLiteDatabase.OPEN_READWRITE)
        for(table in listOf("dose_groups","dose_intents","dose_reservations","dose_schedules")) sqlite.execSQL("DROP TABLE $table")
        sqlite.version=53;sqlite.close();open()
        assertEquals(1,db.medicationLogDao().getPendingSync().size)
        cache();assertEquals(1,db.medicationDoseDao().allGroups("patient").size)
    }
}
