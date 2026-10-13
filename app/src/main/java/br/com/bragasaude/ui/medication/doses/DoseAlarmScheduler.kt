package br.com.bragasaude.ui.medication.doses

import android.app.*
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import br.com.bragasaude.data.local.DoseGroupEntity
import br.com.bragasaude.data.medicationdoses.DoseGroup
import org.json.JSONObject
import java.time.Instant

/** One persisted identity per actor/patient/date/hour; never one alarm per medication. */
object DoseAlarmScheduler {
    const val CHANNEL="medication_dose_groups"
    private fun identity(actor:String,group:String,kind:String)=Uri.parse("braga-dose://$kind/${Uri.encode(actor)}/$group")
    private fun pending(context:Context,actor:String,group:String,kind:String,flags:Int=PendingIntent.FLAG_UPDATE_CURRENT):PendingIntent? =
        PendingIntent.getBroadcast(context,0,Intent(context,DoseAlarmReceiver::class.java).apply {
            data=identity(actor,group,kind); action=kind; putExtra("actor",actor);putExtra("group",group)
        },flags or PendingIntent.FLAG_IMMUTABLE)
    private fun schedule(context:Context,at:Long,pending:PendingIntent) {
        val alarms=context.getSystemService(AlarmManager::class.java)
        if(Build.VERSION.SDK_INT<31 || alarms.canScheduleExactAlarms()) alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,at,pending)
        else alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,at,pending)
    }
    fun reconcile(context:Context,rows:List<DoseGroupEntity>,actor:String?,reserved:Map<String,Set<String>>,reopen:Set<String> = emptySet()) {
        val prefs=context.getSharedPreferences("dose_alarms",Context.MODE_PRIVATE)
        val manager=context.getSystemService(NotificationManager::class.java)
        if(Build.VERSION.SDK_INT>=26) manager.createNotificationChannel(NotificationChannel(CHANNEL,"Medicamentos por horário",NotificationManager.IMPORTANCE_HIGH))
        val prior=prefs.getStringSet("keys",emptySet()).orEmpty().toSet()
        val current=mutableSetOf<String>()
        for(row in rows) {
            if(row.actorId!=actor || row.patientId!=actor) continue // Family alerts require explicit preference in a later coordination phase.
            val group=DoseGroup.parse(JSONObject(row.payload))
            val pendingItems=group.items.filter { it.state!="recorded" && it.occurrenceId !in reserved[row.patientId].orEmpty() }
            if(pendingItems.isEmpty()) continue
            val key="${row.actorId}|${row.groupId}"
            val at=group.instant.toEpochMilli()
            if(at>System.currentTimeMillis()) {
                current.add(key)
                pending(context,row.actorId,row.groupId,"REMIND")?.let { schedule(context,at,it) }
                if(context.getSharedPreferences("dose_preferences",Context.MODE_PRIVATE).getBoolean("pre_alert",false)) {
                    val early=at-15*60_000L
                    if(early>System.currentTimeMillis()) pending(context,row.actorId,row.groupId,"PRE")?.let { schedule(context,early,it) }
                }
            } else if((key in prior || group.id in reopen) && group.instant.atZone(java.time.ZoneId.of(group.zone)).toLocalDate()==
                Instant.now().atZone(java.time.ZoneId.of(group.zone)).toLocalDate()) {
                current.add(key)
                val until=context.getSharedPreferences("dose_alarms",Context.MODE_PRIVATE).getLong("snooze:$key",0)
                if(until<=System.currentTimeMillis()) show(context,row,false,reserved[row.patientId].orEmpty())
            }
        }
        for(key in prior-current) {
            val actorKey=key.substringBefore('|');val group=key.substringAfter('|')
            for(kind in listOf("REMIND","PRE","SNOOZE")) pending(context,actorKey,group,kind,PendingIntent.FLAG_NO_CREATE)?.let {
                context.getSystemService(AlarmManager::class.java).cancel(it);it.cancel()
            }
            manager.cancel(key,0)
        }
        prefs.edit().putStringSet("keys",current).apply()
    }
    fun show(context:Context,row:DoseGroupEntity,pre:Boolean,reserved:Set<String>) {
        val group=DoseGroup.parse(JSONObject(row.payload))
        val remaining=group.items.filter { it.state!="recorded" && it.occurrenceId !in reserved }
        if(remaining.isEmpty()) return
        val open=PendingIntent.getActivity(context,0,Intent(context,MedicationDoseActivity::class.java).apply {
            data=identity(row.actorId,row.groupId,"OPEN");putExtra("patient",group.patientId);putExtra("group",group.id)
        },PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val message=remaining.joinToString("\n") { "${it.name}: ${it.units?.let { n -> "$n unidade(s)" } ?: "conferir quantidade"}${it.instruction?.let { text -> " - $text" } ?: ""}" }
        val builder=NotificationCompat.Builder(context,CHANNEL).setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("${group.patientName} - ${group.time}").setContentText("${remaining.size} medicamentos${if(pre) " em 15 minutos" else " previstos"}")
            .setOnlyAlertOnce(true).setStyle(NotificationCompat.BigTextStyle().bigText(message)).setContentIntent(open).setAutoCancel(false)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
        if(!pre) {
            // Full review is always one collective acceptance. Avoid accepting truncated notification contents.
            val direct=context.getSharedPreferences("dose_preferences",Context.MODE_PRIVATE).getString("direct:${row.actorId}:${group.id}",null)==group.digest
            if(direct && remaining.all { it.eligible }) {
                val take=PendingIntent.getBroadcast(context,0,Intent(context,DoseAlarmReceiver::class.java).apply {
                    action="TAKE";data=identity(row.actorId,group.id,"TAKE")
                    putExtra("actor",row.actorId);putExtra("group",group.id);putExtra("snapshot",row.payload)
                },PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                builder.addAction(0,"Tomei todos",take)
            }
            builder.addAction(0,"Escolher",open)
            pending(context,row.actorId,row.groupId,"SNOOZE")?.let { builder.addAction(0,"Lembrar depois",it) }
        }
        try { context.getSystemService(NotificationManager::class.java).notify("${row.actorId}|${row.groupId}",0,builder.build()) }
        catch(_:SecurityException) { }
    }
    fun snooze(context:Context,actor:String,group:String) {
        context.getSharedPreferences("dose_alarms",Context.MODE_PRIVATE).edit()
            .putLong("snooze:$actor|$group",System.currentTimeMillis()+10*60_000L).apply()
        pending(context,actor,group,"REMIND")?.let { schedule(context,System.currentTimeMillis()+10*60_000L,it) }
        context.getSystemService(NotificationManager::class.java).cancel("$actor|$group",0)
    }
}
