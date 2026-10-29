package br.com.bragasaude.data.medicationdoses

import java.time.Instant
import java.time.ZoneId

/** Uses canonical occurrences, never an interval inferred from stock logs. */
data class MedicationStockDose(
    val group: DoseGroup? = null,
    val item: DoseItem? = null,
    val label: String = "Ver horários",
    val detail: String = "Confira a programação deste medicamento.",
    val canConfirm: Boolean = false
)

fun medicationStockDose(
    medicationId: String,
    groups: List<DoseGroup>,
    reserved: Set<String>,
    now: Instant
): MedicationStockDose {
    val occurrences = groups.sortedBy { it.instant }.flatMap { group ->
        group.items.filter { it.medicationId == medicationId }.map { group to it }
    }
    val today = occurrences.filter { (group, _) -> group.date == now.atZone(ZoneId.of(group.zone)).toLocalDate().toString() }
    val pending = today.firstOrNull { (_, item) -> item.state != "recorded" && item.occurrenceId !in reserved }
    if (pending != null) {
        val (group, item) = pending
        return when {
            !item.eligible -> MedicationStockDose(label = "Revisar doses", detail = "Confira a quantidade e a programação antes de registrar.")
            group.instant.isAfter(now) -> MedicationStockDose(label = "Próxima dose: ${group.time}", detail = "A confirmação será liberada no horário da dose.")
            else -> MedicationStockDose(group, item, "Confirmar Dose", "Dose das ${group.time}: ${item.units} unidade(s).", true)
        }
    }
    if (today.any { (_, item) -> item.state != "recorded" && item.occurrenceId in reserved }) {
        return MedicationStockDose(detail = "Confirmação salva; aguardando sincronização.")
    }
    val next = occurrences.firstOrNull { (group, item) -> group.instant.isAfter(now) && item.eligible && item.occurrenceId !in reserved }
    val upToDate = today.isNotEmpty() && today.all { (_, item) -> item.state == "recorded" }
    return MedicationStockDose(
        label = next?.let { "Próxima dose: ${it.first.time}" } ?: "Ver horários",
        detail = if (upToDate) "Dose de hoje em dia" else "Nenhuma dose pendente para hoje. Confira a programação."
    )
}
