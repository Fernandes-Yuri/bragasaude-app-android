package br.com.bragasaude.data.medicationdoses

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class DoseProtocolTest {
    private fun group():DoseGroup = DoseGroup.parse(JSONObject().put("group_id","group").put("patient_id","patient")
        .put("patient_name","Maria").put("timezone","America/Sao_Paulo").put("local_date","2026-10-13")
        .put("local_time","08:00").put("scheduled_at","2026-10-13T11:00:00Z").put("schedule_digest","digest")
        .put("items",JSONArray((1..12).map { id -> JSONObject().put("occurrence_id","dose$id").put("medication_id","med$id")
            .put("name","Medicamento $id").put("dose_units",id%3+1).put("instruction_text",JSONObject.NULL)
            .put("schedule_revision",4).put("scheduled_at","2026-10-13T11:00:00Z").put("state","pending") })))
    @Test fun twelveDifferentQuantitiesAreKeptAndStateChangeIsAllowed() {
        val group=group();val proposal=JSONObject(group.payload)
        proposal.getJSONArray("items").getJSONObject(0).put("state","recorded").put("scheduled_at","2026-10-13T08:00:00-03:00")
        assertTrue(DoseProtocol.sameSelection(group,proposal,group.items.map { it.occurrenceId }.toSet()))
        assertEquals(12,group.items.size);assertEquals(2,group.items.first().units)
    }
    @Test fun changedQuantityRevisionPatientOrMembershipRequiresReview() {
        val group=group();val selected=group.items.map { it.occurrenceId }.toSet()
        for(field in listOf("dose_units","schedule_revision","name")) {
            val proposal=JSONObject(group.payload)
            proposal.getJSONArray("items").getJSONObject(0).put(field,if(field=="name") "Outra" else 8)
            assertFalse(DoseProtocol.sameSelection(group,proposal,selected))
        }
        assertFalse(DoseProtocol.sameSelection(group,JSONObject(group.payload).put("patient_id","other"),selected))
        assertFalse(DoseProtocol.sameSelection(group,JSONObject(group.payload),selected.drop(1).toSet()))
    }
    @Test fun partialResultNeverSaysAllRecorded() {
        val text=DoseProtocol.summary(JSONObject().put("counts",JSONObject().put("recorded",8).put("already_recorded",1)
            .put("excluded",1).put("blocked",2)))
        assertTrue(text.contains("8 registros novos"));assertTrue(text.contains("2 precisam de revisão"))
    }
}
