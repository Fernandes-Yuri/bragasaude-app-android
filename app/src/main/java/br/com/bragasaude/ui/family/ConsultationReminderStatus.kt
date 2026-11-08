package br.com.bragasaude.ui.family

internal data class ConsultationReminderStatus(
    val scheduledTags: Set<String> = emptySet(),
    val appEnabled: Boolean = true,
    val deviceEnabled: Boolean = true,
    val loading: Boolean = true,
    val unavailable: Boolean = false
) {
    val active: Boolean get() = !loading && !unavailable && appEnabled && deviceEnabled && scheduledTags.isNotEmpty()

    val timingLabel: String get() = listOf("24h", "1h").filter { it in scheduledTags }.joinToString(" e ") + " antes"

    val accessibilityLabel: String get() = if (active) {
        "Avisos programados neste aparelho: " + listOf("24h" to "24 horas", "1h" to "1 hora")
            .filter { it.first in scheduledTags }.joinToString(" e ") { it.second } + " antes da consulta."
    } else label

    val label: String get() = when {
        loading -> "Verificando avisos…"
        unavailable -> "Avisos: verificação indisponível"
        !appEnabled -> "Avisos desativados no app"
        !deviceEnabled -> "Avisos bloqueados no aparelho"
        "24h" in scheduledTags && "1h" in scheduledTags -> "Avisos: 24h e 1h antes"
        "24h" in scheduledTags -> "Aviso: 24h antes"
        "1h" in scheduledTags -> "Aviso: 1h antes"
        else -> "Avisos não agendados"
    }
}
