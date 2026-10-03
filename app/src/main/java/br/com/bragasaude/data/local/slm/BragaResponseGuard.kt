package br.com.bragasaude.data.local.slm

/** Valores e estado de gravação continuam controlados pelo Kotlin após a humanização. */
object BragaResponseGuard {
    fun accept(response: String, context: BragaResolvedContext): String {
        val text = response.trim()
        if (text.isBlank()) return context.fallback
        if (!context.factual) return text
        val normalized = java.text.Normalizer.normalize(text.lowercase(), java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{M}"), "")
        val numberPattern = Regex("[0-9]+(?:[.,][0-9]+)?")
        val numbers = numberPattern.findAll(text).map { it.value }.toSet()
        val allowed = numberPattern.findAll(context.fallback).map { it.value }.toSet()
        if (!allowed.containsAll(numbers) || (allowed.isNotEmpty() && !numbers.containsAll(allowed))) return context.fallback
        if (context.action != null && listOf("salvei", "salvo", "registrei", "registrado", "registrada", "anotei", "anotado", "anotada", "marcado", "marcada", "confirmado", "confirmada", "guardei").any { it in normalized }) return context.fallback
        if (context.type == BragaIntent.CONSULTA_HISTORICO || context.type == BragaIntent.REGISTRO_MEDICAMENTO) return context.fallback
        if (listOf("normal", " alta", " alto", " baixa", " baixo", "dobr", "aumente", "diminua", "dose", "tomar").any { it in normalized }) return context.fallback
        return text
    }
}
