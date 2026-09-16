package br.com.bragasaude.domain

import br.com.bragasaude.data.local.BiometryEntity
import br.com.bragasaude.data.remote.model.RemoteVitalSign
import br.com.bragasaude.data.util.parseDate

/** Regras temporais; limites de peso seguem Contexto/01_LIMITES_CLINICOS.md, A3. */
object HealthHistoryRules {
    const val WEIGHT_WINDOW_MS = 48L * 60 * 60 * 1000

    fun weightGain(current: Float, at: Long, history: List<BiometryEntity>): Float? {
        if (!current.isFinite() || current <= 0) return null
        val baseline = history.filter {
            it.measuredAt.time in (at - WEIGHT_WINDOW_MS) until at && it.weight.isFinite() && it.weight > 0
        }.minOfOrNull { it.weight } ?: return null
        return (current - baseline).takeIf { it >= 1.5f }
    }

    /** Somente a transição para três leituras; a quarta consecutiva não repete o aviso. */
    fun startsHighTrend(
        current: RemoteVitalSign,
        history: List<RemoteVitalSign>,
        value: (RemoteVitalSign) -> Int?,
        high: (Int) -> Boolean
    ): Boolean {
        if (value(current)?.let(high) != true) return false
        val currentTime = current.measuredAt?.let { parseDate(it)?.time } ?: return false
        val previous = history.filter { it.userId == current.userId && value(it) != null }
            .filter { it.id == null || it.id != current.id }
            .mapNotNull { record -> record.measuredAt?.let { parseDate(it)?.time }?.let { record to it } }
            .filter { it.second < currentTime }
            .sortedByDescending { it.second }
            .distinctBy { it.first.id ?: "${it.second}:${value(it.first)}" }
            .take(3).map { value(it.first)!! }
        return previous.size >= 2 && previous.take(2).all(high) &&
            (previous.size < 3 || !high(previous[2]))
    }
}
