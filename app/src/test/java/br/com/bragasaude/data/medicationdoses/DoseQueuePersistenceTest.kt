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
import kotlinx.coroutines.*
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
    private var denied=false
    private var expireFirstConfirmation=false
    private var expired=false
    private var prepareGate:CompletableDeferred<Unit>?=null
    private var prepareStarted:CompletableDeferred<Unit>?=null
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
        every { auth.addAuthStateListener(any()) } just Runs
        every { auth.currentUser } returns user
        every { user.uid } answers { actor }
        mockkObject(WorkManager.Companion)
        every { WorkManager.getInstance(context) } returns wm
        coEvery { api.getMedicationStock(any()) } returns emptyList()
        coEvery { api.getMedicationLogs(any(),any()) } returns emptyList()
        coEvery { api.doseRequest(any(),any(),any()) } coAnswers {
            val method=firstArg<String>();val path=secondArg<String>();val body=thirdArg<JSONObject?>()
            when {
                method=="POST" && path.endsWith("medication-dose-batches") -> {
                    prepareStarted?.complete(Unit);prepareGate?.await()
                    if(denied) BragaApiClient.ApiResponse(JSONObject().put("detail",JSONObject().put("code","PERMISSION_DENIED")),403,"Vínculo revogado")
                    else if(reject) BragaApiClient.ApiResponse(JSONObject().put("detail",JSONObject().put("code","SCHEDULE_CHANGED").put("message","Cadastro alterado")),409,"Cadastro alterado")
                    else { expired=false;proposal=JSONObject(groupPayload).put("batch_id","batch").put("state","prepared").put("snapshot_digest","snapshot")
                        BragaApiClient.ApiResponse(proposal,201,null) }
                }
                method=="POST" && path.endsWith("/cancel") -> BragaApiClient.ApiResponse(JSONObject(proposal.toString()).put("state","cancelled"),200,null)
                method=="GET" && path.endsWith("/batch") -> BragaApiClient.ApiResponse(committed ?: if(expired) JSONObject(proposal.toString()).put("state","expired") else proposal,200,null)
                method=="POST" && path.endsWith("/confirm") -> {
                    if(expireFirstConfirmation) {
                        expireFirstConfirmation=false;expired=true
                        return@coAnswers BragaApiClient.ApiResponse(JSONObject().put("detail",JSONObject().put("code","PROPOSAL_EXPIRED")),409,"Proposta expirada")
                    }
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
    @After fun after() { if(this::db.isInitialized) db.close();context.deleteDatabase("dose-test.db");unmockkObject(WorkManager.Companion) }
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
    @Test fun cancellationDuringPreparationDoesNotSendConfirmation()=runBlocking {
        val group=cache();repo.submit(group,group.items.map { it.occurrenceId }.toSet())
        val id=db.medicationDoseDao().pending("patient").single().id
        prepareGate=CompletableDeferred();prepareStarted=CompletableDeferred()
        val sending=async(Dispatchers.Default) { repo.flush() }
        prepareStarted!!.await()
        withTimeout(5000) { assertTrue(repo.cancel(id).contains("solicitado")) }
        prepareGate!!.complete(Unit)
        assertFalse(sending.await())
        assertTrue(repo.flush())
        assertEquals("CANCELLED",db.medicationDoseDao().intent(id)!!.state)
        coVerify(exactly=0) { api.doseRequest("POST",match { it.endsWith("/confirm") },any()) }
    }
    @Test fun slowNetworkDoesNotBlockSavingAnotherOfflineIntent()=runBlocking {
        val group=cache();repo.submit(group,group.items.map { it.occurrenceId }.toSet())
        prepareGate=CompletableDeferred();prepareStarted=CompletableDeferred()
        val sending=async(Dispatchers.Default) { repo.flush() }
        prepareStarted!!.await()
        val payload=JSONObject(group.payload).put("group_id","otherGroup")
        val items=payload.getJSONArray("items")
        for(i in 0 until items.length()) items.getJSONObject(i).put("occurrence_id","second$i").put("medication_id","otherMed$i")
        val other=DoseGroup.parse(payload)
        db.medicationDoseDao().putGroup(DoseGroupEntity("patient",other.id,"patient",other.date,other.instant.toEpochMilli(),other.payload,System.currentTimeMillis()))
        withTimeout(5000) { repo.submit(other,other.items.map { it.occurrenceId }.toSet()) }
        assertEquals(2,db.medicationDoseDao().pending("patient").size)
        sending.cancelAndJoin()
    }
    @Test fun upgradeFromPublished53KeepsOldLogsAndCreatesDurableQueue()=runBlocking {
        db.close();context.deleteDatabase("dose-test.db")
        val file=listOf(java.io.File("schemas/br.com.bragasaude.data.local.BragaDatabase/53.json"),
            java.io.File("app/schemas/br.com.bragasaude.data.local.BragaDatabase/53.json")).first { it.exists() }
        val schema=JSONObject(file.readText()).getJSONObject("database")
        val sqlite=SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath("dose-test.db"),null)
        val entities=schema.getJSONArray("entities")
        for(i in 0 until entities.length()) {
            val entity=entities.getJSONObject(i);val table=entity.getString("tableName")
            val placeholder="${'$'}{TABLE_NAME}"
            sqlite.execSQL(entity.getString("createSql").replace(placeholder,table))
            val indices=entity.optJSONArray("indices") ?: org.json.JSONArray()
            for(j in 0 until indices.length()) sqlite.execSQL(indices.getJSONObject(j).getString("createSql").replace(placeholder,table))
        }
        val setup=schema.getJSONArray("setupQueries")
        for(i in 0 until setup.length()) sqlite.execSQL(setup.getString(i))
        sqlite.execSQL("INSERT INTO medication_logs_local (id,userId,medicationId,takenAt,unitsTaken,pendingSync) VALUES('old','patient','med',0,1,1)")
        sqlite.version=53;sqlite.close();open()
        assertEquals(1,db.medicationLogDao().getPendingSync().size)
        cache();assertEquals(1,db.medicationDoseDao().allGroups("patient").size)
    }
    @Test fun expiryRepreparesSameSelectionWithNewIds()=runBlocking {
        val group=cache();repo.submit(group,group.items.map { it.occurrenceId }.toSet())
        val old=db.medicationDoseDao().pending("patient").single()
        expireFirstConfirmation=true
        assertFalse(repo.flush())
        assertTrue(repo.flush())
        val resolved=db.medicationDoseDao().intent(old.id)!!
        assertEquals("RESOLVED",resolved.state)
        assertNotEquals(old.clientBatchId,resolved.clientBatchId)
        assertEquals(old.selection,resolved.selection)
        assertEquals(12,repo.reservations("patient").size)
    }
    @Test fun revokedBindingIsTerminalAccessLossWithoutLogs()=runBlocking {
        val group=cache();repo.submit(group,group.items.map { it.occurrenceId }.toSet())
        val old=db.medicationDoseDao().pending("patient").single();denied=true
        assertTrue(repo.flush())
        assertEquals("ACCESS_LOST",db.medicationDoseDao().intent(old.id)!!.state)
        assertTrue(repo.reservations("patient").isEmpty())
        coVerify(exactly=0) { api.doseRequest("POST",match { it.endsWith("/confirm") },any()) }
    }

}
