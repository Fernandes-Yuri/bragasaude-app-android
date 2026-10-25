package br.com.bragasaude.domain.smoking

import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId

enum class OfferDecision { UNSEEN, DECLINED, ACCEPTED }
enum class CravingIntensity { LOW, MEDIUM, HIGH }
enum class SmokingTrigger { STRESS, COFFEE, AFTER_MEALS, SOCIAL, ALCOHOL }
enum class PreparationStage { PREPARING, STARTED }

/** Pure rules: missing dates and unknown quantities never imply abstinence. */
class SmokingSupportRules(private val clock: Clock, private val zone: ZoneId) {
    fun today(): LocalDate = LocalDate.now(clock.withZone(zone))
    fun validatePlan(startedOn: LocalDate, target: LocalDate?, reason: String?, triggers: Set<SmokingTrigger>) {
        require(!startedOn.isAfter(today())) { "O início não pode estar no futuro." }
        require(target == null || !target.isBefore(startedOn)) { "A meta deve ser a partir do início." }
        require(reason == null || reason.length <= 300) { "Use até 300 caracteres." }
        require(triggers.size <= SmokingTrigger.entries.size)
    }
    fun validateEntry(date: LocalDate, startedOn: LocalDate, smoked: Boolean, count: Int?) {
        require(!date.isBefore(startedOn) && !date.isAfter(today())) { "Data fora do acompanhamento." }
        require(if (smoked) count == null || count > 0 else count == 0) { "Quantidade inválida." }
    }
    fun daysRecordedWithoutSmoking(startedOn: LocalDate, entries: Map<LocalDate, Boolean>): Int =
        entries.count { (date, smoked) -> !smoked && !date.isBefore(startedOn) && !date.isAfter(today()) }
}
