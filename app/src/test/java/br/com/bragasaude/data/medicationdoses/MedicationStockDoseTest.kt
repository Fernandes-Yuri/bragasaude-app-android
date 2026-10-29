package br.com.bragasaude.data.medicationdoses

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class MedicationStockDoseTest {
    private val now = Instant.parse("2026-10-29T14:00:00Z")
    private fun group(time: String = "08:00", date: String = "2026-10-29"): DoseGroup = DoseGroup.parse(
        JSONObject().put("group_id", "$date-$time").put("patient_id", "patient").put("patient_name", "Maria")
            .put("timezone", "America/Sao_Paulo").put("local_date", date).put("local_time", time)
            .put("scheduled_at", "${date}T$time:00-03:00").put("schedule_digest", "digest")
            .put("items", JSONArray((1..3).map { id ->
                JSONObject().put("occurrence_id", "$date-$time-dose$id").put("medication_id", "med$id")
                    .put("name", "Medicamento $id").put("dose_units", id).put("schedule_revision", 1)
                    .put("scheduled_at", "${date}T$time:00-03:00").put("state", "pending")
            }))
    )

    @Test fun selectsOnlyTheRequestedMedicationAndEarliestDueOccurrence() {
        val morning = group()
        val result = medicationStockDose("med2", listOf(group("20:00"), morning), emptySet(), now)
        assertTrue(result.canConfirm)
        assertEquals("med2", result.item?.medicationId)
        assertEquals(2, result.item?.units)
        assertEquals(morning.id, result.group?.id)
    }

    @Test fun futureAndPreviousDayDosesCannotBeConfirmedFromStock() {
        assertFalse(medicationStockDose("med1", listOf(group("20:00")), emptySet(), now).canConfirm)
        assertFalse(medicationStockDose("med1", listOf(group(date = "2026-10-28")), emptySet(), now).canConfirm)
        // Still yesterday in the patient's timezone, despite the UTC date changing.
        assertFalse(medicationStockDose("med1", listOf(group()), emptySet(), Instant.parse("2026-10-29T01:00:00Z")).canConfirm)
    }

    @Test fun reservationDoesNotClaimThatAdministrationHasBeenConfirmed() {
        val group = group()
        val result = medicationStockDose("med2", listOf(group), setOf(group.items[1].occurrenceId), now)
        assertFalse(result.canConfirm)
        assertTrue(result.detail.contains("aguardando sincronização"))
    }

    @Test fun recordedMedicationIsUpToDateEvenWhenOtherMedicationIsPending() {
        val original = group()
        val recorded = original.copy(items = original.items.map { if (it.medicationId == "med2") it.copy(state = "recorded") else it })
        val result = medicationStockDose("med2", listOf(recorded, group(date = "2026-10-30")), emptySet(), now)
        assertFalse(result.canConfirm)
        assertEquals("Ver Histórico", medicationStockDose("med2", listOf(recorded), emptySet(), now).label)
        assertEquals("Dose de hoje em dia", result.detail)
        assertEquals("Próxima dose: 08:00", result.label)
    }

    @Test fun missingQuantityRequiresReviewAndUnknownMedicationIsNotMarkedAsTaken() {
        val original = group()
        val invalid = original.copy(items = original.items.map { it.copy(units = null, reason = "quantity_required") })
        assertEquals("Revisar doses", medicationStockDose("med1", listOf(invalid), emptySet(), now).label)
        assertFalse(medicationStockDose("missing", listOf(original), emptySet(), now).detail.contains("em dia"))
    }

    @Test fun partialConfirmationGivesFriendlyReviewFeedback() {
        val result = JSONObject().put("counts", JSONObject().put("recorded", 1).put("blocked", 1))
        assertTrue(DoseProtocol.friendlySummary(result).contains("revisão"))
        assertFalse(DoseProtocol.friendlySummary(result).contains("registros novos"))
    }
}
