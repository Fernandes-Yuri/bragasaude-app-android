package br.com.bragasaude.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName="dose_groups",primaryKeys=["actorId","groupId"])
data class DoseGroupEntity(val actorId:String,val groupId:String,val patientId:String,val localDate:String,
    val scheduledAt:Long,val payload:String,val fetchedAt:Long)
@Entity(tableName="dose_intents",indices=[Index("actorId","state")])
data class DoseIntentEntity(@PrimaryKey val id:String,val actorId:String,val patientId:String,val groupId:String,
    val snapshot:String,val selection:String,val clientBatchId:String,val confirmationId:String,
    val declaredAt:String,val remoteBatch:String?=null,val state:String="PENDING",val result:String?=null,
    val message:String?=null,val createdAt:Long=System.currentTimeMillis())
@Entity(tableName="dose_reservations",primaryKeys=["patientId","occurrenceId"])
data class DoseReservationEntity(val patientId:String,val occurrenceId:String,val intentId:String)
@Entity(tableName="dose_schedules",primaryKeys=["actorId","medicationId"])
data class DoseScheduleEntity(val actorId:String,val medicationId:String,val patientId:String,val payload:String,
    val draft:String?=null)

@Dao
interface MedicationDoseDao {
    @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun putGroup(row:DoseGroupEntity)
    @Query("SELECT * FROM dose_groups WHERE actorId=:actor AND patientId=:patient ORDER BY scheduledAt")
    fun groups(actor:String,patient:String):Flow<List<DoseGroupEntity>>
    @Query("SELECT * FROM dose_groups WHERE actorId=:actor") suspend fun allGroups(actor:String):List<DoseGroupEntity>
    @Query("SELECT * FROM dose_groups WHERE actorId=:actor AND groupId=:groupId") suspend fun group(actor:String,groupId:String):DoseGroupEntity?
    @Query("DELETE FROM dose_groups WHERE actorId=:actor AND patientId=:patient AND localDate=:date") suspend fun clearDay(actor:String,patient:String,date:String)
    @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun putIntent(row:DoseIntentEntity)
    @Query("SELECT * FROM dose_intents WHERE actorId=:actor AND patientId=:patient ORDER BY createdAt DESC")
    fun intents(actor:String,patient:String):Flow<List<DoseIntentEntity>>
    @Query("SELECT * FROM dose_intents WHERE actorId=:actor AND state IN ('PENDING','WAITING','CONFIRMING','CANCEL_REQUESTED') ORDER BY createdAt")
    suspend fun pending(actor:String):List<DoseIntentEntity>
    @Query("SELECT * FROM dose_intents WHERE id=:id") suspend fun intent(id:String):DoseIntentEntity?
    @Insert(onConflict=OnConflictStrategy.ABORT) suspend fun reserve(row:DoseReservationEntity)
    @Query("SELECT occurrenceId FROM dose_reservations WHERE patientId=:patient") suspend fun reserved(patient:String):List<String>
    @Query("DELETE FROM dose_reservations WHERE intentId=:id") suspend fun release(id:String)
    @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun putSchedule(row:DoseScheduleEntity)
    @Query("SELECT * FROM dose_schedules WHERE actorId=:actor AND medicationId=:medicationId") suspend fun schedule(actor:String,medicationId:String):DoseScheduleEntity?
}
