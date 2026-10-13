package br.com.bragasaude.data.medicationdoses

import android.content.Context
import androidx.room.withTransaction
import androidx.work.*
import br.com.bragasaude.data.local.*
import br.com.bragasaude.data.remote.api.BragaApiClient
import br.com.bragasaude.ui.medication.doses.DoseAlarmScheduler
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.time.*
import java.util.Date
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

class DoseApiException(val code:String,val http:Int,override val message:String):Exception(message)

@Singleton
class MedicationDoseRepository @Inject constructor(@ApplicationContext private val context:Context,
    private val database:BragaDatabase,private val api:BragaApiClient,private val auth:FirebaseAuth) {
    private val dao get()=database.medicationDoseDao()
    private val lock=Mutex()
    fun actor()=auth.currentUser?.uid ?: throw IllegalStateException("Entre na sua conta para registrar doses.")
    fun groups(patient:String)=dao.groups(actor(),patient)
    fun intents(patient:String)=dao.intents(actor(),patient)
    suspend fun reservations(patient:String)=dao.reserved(patient).toSet()
    private suspend fun request(method:String,path:String,body:JSONObject?=null):JSONObject {
        val result=api.doseRequest(method,path,body)
        if(result.code !in 200..299 || result.body==null) throw DoseApiException(
            result.body?.optJSONObject("detail")?.optString("code") ?: "NETWORK",result.code,
            result.errorDetail ?: "Não foi possível concluir esta operação.")
        return result.body
    }
    fun enqueue() {
        val actor=auth.currentUser?.uid ?: return
        val wm=WorkManager.getInstance(context)
        val work=OneTimeWorkRequestBuilder<MedicationDoseWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL,30,java.util.concurrent.TimeUnit.SECONDS).build()
        wm.enqueueUniqueWork("medication-dose-sync-$actor",ExistingWorkPolicy.APPEND_OR_REPLACE,work)
    }
    suspend fun refresh(patient:String,horizon:Int=7) {
        val actor=actor()
        val capabilities=request("GET","/api/medication-dose-capabilities")
        if(!capabilities.optBoolean("enabled")) throw DoseApiException("CONTRACT_DISABLED",503,"Confirmação coletiva temporariamente indisponível.")
        var today:LocalDate?=null
        for(offset in 0 until horizon) {
            val query=if(today==null) "" else "?date=${today!!.plusDays(offset.toLong())}"
            val response=request("GET","/api/family/patients/$patient/medication-dose-groups$query")
            check(auth.currentUser?.uid==actor)
            if(today==null) today=LocalDate.parse(response.getString("local_date"))
            val groups=DoseGroup.fromResponse(response)
            database.withTransaction {
                dao.clearDay(actor,patient,response.getString("local_date"))
                for(group in groups) dao.putGroup(DoseGroupEntity(actor,group.id,patient,group.date,
                    group.instant.toEpochMilli(),group.payload,System.currentTimeMillis()))
            }
            // Keep malformed schedules visible in the editor/list, never invent groups.
            context.getSharedPreferences("dose_preferences",Context.MODE_PRIVATE).edit()
                .putString("review:$actor:$patient",response.optJSONArray("review_items")?.toString() ?: "[]").apply()
        }
        reschedule()
    }
    suspend fun schedule(patient:String,medicationId:String):JSONObject {
        val actor=actor()
        return try {
            val body=request("GET","/api/family/patients/$patient/medications/$medicationId/dose-schedule")
            check(auth.currentUser?.uid==actor)
            val old=dao.schedule(actor,medicationId)
            dao.putSchedule(DoseScheduleEntity(actor,medicationId,patient,body.toString(),old?.draft))
            if(old?.draft!=null) JSONObject(body.toString()).put("slots",JSONObject(old.draft).getJSONArray("slots"))
                .put("schedule_revision",JSONObject(old.draft).getInt("expected_revision")) else body
        } catch(e:DoseApiException) {
            if(e.http in listOf(401,403,404)) throw e
            dao.schedule(actor,medicationId)?.let { cached -> JSONObject(cached.payload).apply { cached.draft?.let { put("slots",JSONObject(it).getJSONArray("slots"));put("schedule_revision",JSONObject(it).getInt("expected_revision")) } } } ?: throw e
        }
    }
    suspend fun discardDraft(medicationId:String) {
        val actor=actor();dao.schedule(actor,medicationId)?.let { dao.putSchedule(it.copy(draft=null)) }
    }
    suspend fun saveSchedule(patient:String,medicationId:String,revision:Int,slots:JSONArray):String {
        val actor=actor()
        val payload=JSONObject().put("expected_revision",revision).put("slots",slots)
        val old=dao.schedule(actor,medicationId) ?: throw IllegalStateException("Atualize a programação antes de salvar.")
        dao.putSchedule(old.copy(draft=payload.toString()))
        val body=request("PUT","/api/family/patients/$patient/medications/$medicationId/dose-schedule",payload)
        check(auth.currentUser?.uid==actor)
        dao.putSchedule(old.copy(payload=body.toString(),draft=null))
        refresh(patient)
        return "Programação salva."
    }
    suspend fun submit(group:DoseGroup,selected:Set<String>):String=lock.withLock {
        val actor=actor()
        require(selected.isNotEmpty())
        require(selected.all { id -> group.items.any { it.occurrenceId==id && it.eligible } })
        require(!group.instant.isAfter(Instant.now())) { "Este horário ainda não chegou." }
        require(dao.group(actor,group.id)?.let { DoseGroup.parse(JSONObject(it.payload)).digest==group.digest }==true) { "A conta ou a lista mudou. Atualize os horários." }
        val id=UUID.randomUUID().toString()
        database.withTransaction {
            val reserved=dao.reserved(group.patientId).toSet()
            require(selected.none { it in reserved }) { "Já existe uma confirmação salva para esta dose." }
            for(occurrence in selected) dao.reserve(DoseReservationEntity(group.patientId,occurrence,id))
            val candidates=group.items.filter { it.eligible && it.occurrenceId !in reserved }.map { it.occurrenceId }.toSet()
            val items=JSONObject(group.payload).getJSONArray("items")
            val exactSnapshot=JSONObject(group.payload).put("items",JSONArray((0 until items.length()).map { items.getJSONObject(it) }
                .filter { it.getString("occurrence_id") in candidates })).toString()
            dao.putIntent(DoseIntentEntity(id,actor,group.patientId,group.id,exactSnapshot,
                JSONArray(selected.sorted()).toString(),id,UUID.randomUUID().toString(),Instant.now().toString()))
        }
        enqueue(); reschedule()
        "Confirmação salva; aguardando sincronização."
    }
    suspend fun cancel(id:String)=lock.withLock {
        val intent=dao.intent(id) ?: return@withLock
        require(intent.actorId==actor())
        if(intent.state in setOf("PENDING","WAITING")) dao.putIntent(intent.copy(state="CANCEL_REQUESTED"))
        enqueue()
    }
    private suspend fun resolved(intent:DoseIntentEntity,result:JSONObject) {
        val resultItems=result.optJSONArray("items") ?: JSONArray()
        val authoritativeLogs=try { api.getMedicationLogs(intent.patientId,90).orEmpty().associateBy { it.id } }
            catch(e:kotlinx.coroutines.CancellationException) { throw e } catch(_:Exception) { emptyMap() }
        val previousLogs=database.medicationLogDao().getAllLogs(intent.patientId).first()
        database.withTransaction {
            dao.release(intent.id)
            for(index in 0 until resultItems.length()) {
                val item=resultItems.getJSONObject(index)
                if(item.optString("status") !in setOf("recorded","already_recorded")) continue
                val occurrence=item.getString("occurrence_id")
                dao.reserve(DoseReservationEntity(intent.patientId,occurrence,intent.id))
                val instant=Instant.parse(item.getString("scheduled_at"))
                val local=instant.atZone(ZoneId.of(result.getString("timezone")))
                val units=item.optInt("recorded_units",item.optInt("dose_units",1))
                val logId=item.getString("log_id")
                val remote=authoritativeLogs[logId]
                for(old in previousLogs.filter { it.medicationId==item.getString("medication_id") }) {
                    val oldInstant=runCatching { Instant.parse(old.careOsScheduledFor ?: old.scheduledFor) }.getOrElse {
                        runCatching { LocalDateTime.parse(old.scheduledFor).atZone(ZoneId.of(result.getString("timezone"))).toInstant() }.getOrNull()
                    }
                    if(old.id!=logId && oldInstant==instant) database.medicationLogDao().deleteCanonicalCacheLog(intent.patientId,old.id)
                }
                database.medicationLogDao().insertOnce(MedicationLogEntity(
                    id=logId,userId=intent.patientId,medicationId=item.getString("medication_id"),
                    takenAt=Date.from(runCatching { Instant.parse(remote?.takenAt) }.getOrDefault(Instant.parse(intent.declaredAt))),scheduledFor="${local.toLocalDate()}T${local.toLocalTime()}",
                    actorId=remote?.actorId ?: if(item.getString("status")=="recorded") intent.actorId else null,unitsTaken=remote?.unitsTaken ?: units,careOsScheduledFor=instant.toString(),pendingSync=false))
            }
            dao.putIntent(intent.copy(state="RESOLVED",result=result.toString(),message=DoseProtocol.summary(result)))
        }
        try {
            api.getMedicationStock(intent.patientId).forEach { item -> item.id?.let { database.medicationDao().applyAuthoritativeStock(it,item.currentUnits) } }
        } catch(e:kotlinx.coroutines.CancellationException) { throw e } catch(_:Exception) { }
    }
    suspend fun flush():Boolean=lock.withLock {
        val actor=auth.currentUser?.uid ?: return@withLock true
        var complete=true
        for(original in dao.pending(actor)) {
            if(auth.currentUser?.uid!=actor) return@withLock false
            var intent=original
            try {
                var remote=intent.remoteBatch?.let(::JSONObject)
                if(remote!=null) {
                    remote=request("GET","/api/medication-dose-batches/${remote.getString("batch_id")}")
                    if(remote.getString("state") in DoseProtocol.terminal && remote.getString("state")!="expired") {
                        resolved(intent,remote); continue
                    }
                }
                if(intent.state=="CANCEL_REQUESTED") {
                    if(remote!=null && remote.getString("state")=="prepared") remote=request("POST",
                        "/api/medication-dose-batches/${remote.getString("batch_id")}/cancel",JSONObject())
                    database.withTransaction { dao.release(intent.id); dao.putIntent(intent.copy(state="CANCELLED",message="Proposta cancelada.")) }
                    continue
                }
                val group=DoseGroup.parse(JSONObject(intent.snapshot))
                val selected=DoseProtocol.selection(intent.selection)
                if(remote?.getString("state")=="expired") {
                    intent=intent.copy(clientBatchId=UUID.randomUUID().toString(),confirmationId=UUID.randomUUID().toString(),remoteBatch=null)
                    dao.putIntent(intent); remote=null
                }
                if(remote==null) {
                    remote=request("POST","/api/family/patients/${intent.patientId}/medication-dose-batches",JSONObject()
                        .put("client_batch_id",intent.clientBatchId).put("group_id",group.id)
                        .put("expected_schedule_digest",group.digest).put("occurrence_ids",JSONArray(group.items.filter { it.eligible }.map { it.occurrenceId }.sorted())).put("channel","app"))
                    intent=intent.copy(remoteBatch=remote.toString(),state="WAITING"); dao.putIntent(intent)
                }
                require(DoseProtocol.sameSelection(group,remote,group.items.filter { it.eligible }.map { it.occurrenceId }.toSet())) { "A proposta mudou. Confira a lista novamente." }
                check(auth.currentUser?.uid==actor)
                val result=request("POST","/api/medication-dose-batches/${remote.getString("batch_id")}/confirm",JSONObject()
                    .put("confirmation_id",intent.confirmationId).put("snapshot_digest",remote.getString("snapshot_digest"))
                    .put("selected_occurrence_ids",JSONArray(selected.sorted())).put("declared_taken_at",intent.declaredAt))
                resolved(intent,result)
            } catch(e: kotlinx.coroutines.CancellationException) { throw e
            } catch(e:Exception) {
                val retry=e is DoseApiException && (e.http==0 || e.http>=500 || e.code=="PROPOSAL_EXPIRED")
                if(retry) { complete=false; dao.putIntent(intent.copy(message=e.message)) }
                else database.withTransaction {
                    dao.release(intent.id)
                    dao.putIntent(intent.copy(state=if(e is DoseApiException && e.http in listOf(401,403)) "ACCESS_LOST" else "REVIEW",
                        message=e.message ?: "Confira a lista novamente."))
                }
            }
        }
        val patients=(listOf(actor)+dao.allGroups(actor).map { it.patientId }).distinct()
        for(patient in patients) try { refresh(patient) } catch(e:kotlinx.coroutines.CancellationException) { throw e } catch(_:Exception) { }
        reschedule(); complete
    }
    suspend fun reschedule() {
        val actor=auth.currentUser?.uid
        val rows=if(actor==null) emptyList() else dao.allGroups(actor)
        val reserved=rows.map { it.patientId }.distinct().associateWith { dao.reserved(it).toSet() }
        val previous=context.getSharedPreferences("dose_preferences",Context.MODE_PRIVATE).getString("last_actor",null)
        for(owner in listOfNotNull(actor,previous).distinct()) {
            val meds=database.medicationDao().getAllSync(owner)
            br.com.bragasaude.ui.util.MedicationAlarmReceiver.cancelAllAlarms(context,meds)
            meds.forEach { br.com.bragasaude.ui.medication.MedicationNotificationScheduler.cancelMedicationAlarms(context,it.id) }
        }
        context.getSharedPreferences("dose_preferences",Context.MODE_PRIVATE).edit().putString("last_actor",actor).apply()
        DoseAlarmScheduler.reconcile(context,rows,actor,reserved)
    }
}
