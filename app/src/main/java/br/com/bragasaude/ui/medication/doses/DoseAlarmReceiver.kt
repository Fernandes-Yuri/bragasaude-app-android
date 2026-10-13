package br.com.bragasaude.ui.medication.doses

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import br.com.bragasaude.data.local.BragaDatabase
import br.com.bragasaude.data.medicationdoses.MedicationDoseRepository
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.*

@EntryPoint
@InstallIn(SingletonComponent::class)
interface DoseAlarmEntryPoint {
    fun database():BragaDatabase
    fun doses():MedicationDoseRepository
    fun auth():FirebaseAuth
}
class DoseAlarmReceiver:BroadcastReceiver() {
    override fun onReceive(context:Context,intent:Intent) {
        val pending=goAsync()
        CoroutineScope(SupervisorJob()+Dispatchers.IO).launch {
            try {
                val deps=EntryPointAccessors.fromApplication(context.applicationContext,DoseAlarmEntryPoint::class.java)
                val actor=deps.auth().currentUser?.uid
                if(intent.action in setOf(Intent.ACTION_BOOT_COMPLETED,Intent.ACTION_TIME_CHANGED,Intent.ACTION_TIMEZONE_CHANGED,
                    "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED","REFRESH")) {
                    deps.doses().reschedule();deps.doses().enqueue();return@launch
                }
                val owner=intent.getStringExtra("actor") ?: return@launch
                val id=intent.getStringExtra("group") ?: return@launch
                if(owner!=actor) { deps.doses().reschedule();return@launch }
                val row=deps.database().medicationDoseDao().group(owner,id) ?: return@launch
                if(row.patientId!=owner) return@launch
                if(intent.action=="TAKE") {
                    val snapshot=intent.getStringExtra("snapshot") ?: return@launch
                    val group=br.com.bragasaude.data.medicationdoses.DoseGroup.parse(org.json.JSONObject(snapshot))
                    val selected=group.items.filter { it.eligible && it.occurrenceId !in deps.doses().reservations(group.patientId) }.map { it.occurrenceId }.toSet()
                    if(selected.isNotEmpty()) deps.doses().submit(group,selected)
                } else if(intent.action=="SNOOZE") DoseAlarmScheduler.snooze(context,owner,id)
                else DoseAlarmScheduler.show(context,row,intent.action=="PRE",deps.doses().reservations(row.patientId))
            } catch(e:CancellationException) { throw e
            } catch(_:Exception) {
                // Reopen the full list; an old snapshot never confirms a changed medication set.
                val deps=EntryPointAccessors.fromApplication(context.applicationContext,DoseAlarmEntryPoint::class.java)
                val owner=intent.getStringExtra("actor");val groupId=intent.getStringExtra("group")
                if(owner!=null && groupId!=null && deps.auth().currentUser?.uid==owner) {
                    deps.database().medicationDoseDao().group(owner,groupId)?.let {
                        DoseAlarmScheduler.show(context,it,false,deps.doses().reservations(it.patientId))
                    }
                }
            } finally { pending.finish() }
        }
    }
}
