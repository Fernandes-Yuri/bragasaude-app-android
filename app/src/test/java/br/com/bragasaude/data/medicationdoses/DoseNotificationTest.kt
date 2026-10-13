package br.com.bragasaude.data.medicationdoses

import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import br.com.bragasaude.data.local.DoseGroupEntity
import br.com.bragasaude.ui.medication.doses.DoseAlarmScheduler
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
class DoseNotificationTest {
    private val context get()=RuntimeEnvironment.getApplication()
    private val manager get()=context.getSystemService(NotificationManager::class.java)
    private fun row():DoseGroupEntity {
        val instant=Instant.now().atZone(ZoneId.of("UTC")).toLocalDate().atStartOfDay(ZoneId.of("UTC")).toInstant();val local=instant.atZone(ZoneId.of("UTC"))
        val payload=JSONObject().put("group_id","group").put("patient_id","patient").put("patient_name","Maria")
            .put("timezone","UTC").put("local_date",local.toLocalDate().toString()).put("local_time",local.toLocalTime().withSecond(0).withNano(0).toString())
            .put("scheduled_at",instant.toString()).put("schedule_digest","digest")
            .put("items",JSONArray((1..12).map { i -> JSONObject().put("occurrence_id","dose$i").put("medication_id","med$i")
                .put("name","Medicamento $i").put("dose_units",2).put("instruction_text",JSONObject.NULL)
                .put("schedule_revision",1).put("scheduled_at",instant.toString()).put("state","pending") })).toString()
        return DoseGroupEntity("patient","group","patient",local.toLocalDate().toString(),instant.toEpochMilli(),payload,System.currentTimeMillis())
    }
    @Before fun before() { manager.cancelAll();context.getSharedPreferences("dose_alarms",Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("dose_preferences",Context.MODE_PRIVATE).edit().clear().commit() }
    @Test fun twelveMedicationsProduceOneNotificationAndPartialOnlyShowsRemaining() {
        val row=row();val resolved=(1..10).map { "dose$it" }.toSet()
        DoseAlarmScheduler.reconcile(context,listOf(row),"patient",mapOf("patient" to resolved),setOf("group"))
        assertEquals(1,manager.activeNotifications.size)
        assertTrue(manager.activeNotifications.first().notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString().startsWith("2 medicamentos"))
        DoseAlarmScheduler.reconcile(context,listOf(row),"patient",mapOf("patient" to resolved))
        assertEquals(1,manager.activeNotifications.size)
    }
    @Test fun snoozeIsNotCancelledByBackgroundReconciliation() {
        val row=row()
        DoseAlarmScheduler.reconcile(context,listOf(row),"patient",emptyMap(),setOf("group"))
        assertEquals(1,manager.activeNotifications.size)
        DoseAlarmScheduler.snooze(context,"patient","group")
        DoseAlarmScheduler.reconcile(context,listOf(row),"patient",emptyMap())
        assertTrue(manager.activeNotifications.isEmpty())
    }
    @Test fun changedAccountCancelsOldNotification() {
        DoseAlarmScheduler.reconcile(context,listOf(row()),"patient",emptyMap(),setOf("group"))
        DoseAlarmScheduler.reconcile(context,emptyList(),"other",emptyMap())
        assertTrue(manager.activeNotifications.isEmpty())
    }
    @Test fun quickConfirmationRequiresExactlyReviewedDigest() {
        val row=row()
        context.getSharedPreferences("dose_preferences",Context.MODE_PRIVATE).edit().putString(DoseProtocol.reviewKey("patient",DoseGroup.parse(JSONObject(row.payload))),DoseProtocol.reviewDigest(DoseGroup.parse(JSONObject(row.payload)))).commit()
        DoseAlarmScheduler.reconcile(context,listOf(row),"patient",emptyMap(),setOf("group"))
        assertTrue(manager.activeNotifications.first().notification.actions.any { it.title.toString()=="Tomei todos" })
        val payload=JSONObject(row.payload).put("schedule_digest","changed")
        payload.getJSONArray("items").getJSONObject(0).put("schedule_revision",2)
        val changed=row.copy(payload=payload.toString())
        DoseAlarmScheduler.reconcile(context,listOf(changed),"patient",emptyMap())
        assertFalse(manager.activeNotifications.first().notification.actions.any { it.title.toString()=="Tomei todos" })
    }
}
