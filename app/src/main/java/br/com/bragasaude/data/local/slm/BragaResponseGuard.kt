package br.com.bragasaude.data.local.slm

/** Valores e estado de gravação continuam controlados pelo Kotlin após a humanização. */
object BragaResponseGuard {
    fun accept(response: String, context: BragaResolvedContext): String {
        val text = response.trim()
        if (text.isBlank()) return context.fallback
        val normalized = java.text.Normalizer.normalize(text.lowercase(), java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{M}"), "")
        val saveClaims = listOf("salvei", "salvo", "registrei", "registrado", "registrada", "anotei", "anotado", "anotada", "marcado", "marcada", "confirmado", "confirmada", "guardei")
        if (!context.factual) {
            if (saveClaims.any { it in normalized } || listOf("toque em confirmar", "toque em salvar", "preencha", "confira os dados").any { it in normalized }) return context.fallback
            return text
        }
        val numberPattern = Regex("[0-9]+(?:[.,][0-9]+)?")
        val numbers = numberPattern.findAll(text).map { it.value }.toList()
        val allowed = numberPattern.findAll(context.fallback).map { it.value }.toList()
        if (numbers != allowed) return context.fallback
        if (context.action != null && saveClaims.any { it in normalized }) return context.fallback
        if (context.type == BragaIntent.CONSULTA_HISTORICO || context.type == BragaIntent.REGISTRO_MEDICAMENTO) return context.fallback
        if (listOf("normal", " alta", " alto", " baixa", " baixo", "dobr", "aumente", "diminua", "dose", "tomar").any { it in normalized }) return context.fallback
        return text
    }
}
