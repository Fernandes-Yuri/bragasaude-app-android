package br.com.bragasaude.data.medicationdoses

import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

/** Business fields are immutable; confirmation/stock states can change between channels. */
data class DoseItem(val occurrenceId: String, val medicationId: String, val name: String,
    val units: Int?, val instruction: String?, val revision: Int, val scheduledAt: String,
    val state: String, val reason: String?, val status: String?, val logId: String?) {
    val eligible get() = state == "pending" && units != null && reason == null
    companion object {
        fun parse(o: JSONObject) = DoseItem(o.getString("occurrence_id"),o.getString("medication_id"),
            o.getString("name"),if(o.isNull("dose_units")) null else o.getInt("dose_units"),
            if(o.isNull("instruction_text")) null else o.getString("instruction_text"),o.getInt("schedule_revision"),
            o.getString("scheduled_at"),o.getString("state"),if(o.isNull("reason_code")) null else o.getString("reason_code"),
            if(o.isNull("status")) null else o.getString("status"),if(o.isNull("log_id")) null else o.getString("log_id"))
    }
}

data class DoseGroup(val id: String,val patientId: String,val patientName: String,val date: String,
    val time: String,val zone: String,val digest: String,val items: List<DoseItem>,val payload: String) {
    val instant get() = Instant.parse(JSONObject(payload).getString("scheduled_at"))
    companion object {
        fun parse(o: JSONObject) = DoseGroup(o.getString("group_id"),o.getString("patient_id"),o.getString("patient_name"),
            o.getString("local_date"),o.getString("local_time"),o.getString("timezone"),o.getString("schedule_digest"),
            (0 until o.getJSONArray("items").length()).map { DoseItem.parse(o.getJSONArray("items").getJSONObject(it)) },o.toString())
        fun fromResponse(response: JSONObject): List<DoseGroup> {
            val array=response.getJSONArray("groups")
            return (0 until array.length()).map { i ->
                val group=JSONObject(array.getJSONObject(i).toString())
                for(key in listOf("patient_id","patient_name","local_date","timezone")) group.put(key,response.getString(key))
                parse(group)
            }
        }
    }
}

object DoseProtocol {
    val terminal = setOf("confirmed","partially_confirmed","failed","cancelled","expired")
    fun sameSelection(group: DoseGroup, proposal: JSONObject, selected: Set<String>): Boolean {
        val items=proposal.getJSONArray("items")
        if (proposal.getString("patient_id") != group.patientId || proposal.getString("timezone") != group.zone ||
            proposal.getString("local_date") != group.date || proposal.getString("local_time") != group.time) return false
        val original=group.items.filter { it.occurrenceId in selected }.associateBy { it.occurrenceId }
        if(items.length()!=original.size) return false
        return (0 until items.length()).all { i ->
            val item=DoseItem.parse(items.getJSONObject(i)); val old=original[item.occurrenceId]
            old!=null && old.medicationId==item.medicationId && old.units==item.units && old.name==item.name &&
                old.instruction==item.instruction && old.revision==item.revision &&
                Instant.parse(old.scheduledAt)==Instant.parse(item.scheduledAt)
        }
    }
    fun selection(json: String) = JSONArray(json).let { a -> (0 until a.length()).map { a.getString(it) }.toSet() }
    fun summary(result: JSONObject): String {
        val c=result.optJSONObject("counts") ?: return "Operação ${result.getString("state")}."
        return "${c.optInt("recorded")} registros novos; ${c.optInt("already_recorded")} já registrados; " +
            "${c.optInt("excluded")} excluídos; ${c.optInt("blocked")} precisam de revisão."
    }
}
