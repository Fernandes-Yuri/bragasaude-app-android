package br.com.bragasaude.domain

import java.text.Normalizer
import java.util.Locale

data class HealthReadingDraft(val metric: String, val value: Double)

object HealthReadingInput {
    const val HEART = "HEART_RATE"
    const val OXYGEN = "OXYGEN_SATURATION"
    const val MANUAL = "braga.manual"
    const val VOICE = "braga.voice"
    fun value(metric: String, raw: String): Double? {
        val number = raw.trim().replace(',', '.').toDoubleOrNull() ?: return null
        if (!number.isFinite()) return null
        return when (metric) {
            HEART -> number.takeIf { it in 1.0..300.0 && it % 1.0 == 0.0 }
            OXYGEN -> number.takeIf { it > 0 && it <= 100.0 }
            else -> null
        }
    }
    fun origin(source: String) = when (source) { MANUAL -> "Manual"; VOICE -> "Voz, conferida por você"; else -> "Health Connect" }
    fun spoken(raw: String, expectedMetric: String? = null): HealthReadingDraft? {
        var text = Normalizer.normalize(raw, Normalizer.Form.NFD).replace(Regex("\\p{M}"), "").lowercase(Locale.ROOT)
        if (Regex("\\b(qual|normal|deveria|seria|exemplo|se)\\b").containsMatchIn(text)) return null
        if (Regex("\\b(meu pai|minha mae|meu filho|minha filha|meu familiar|paciente)\\b").containsMatchIn(text)) return null
        val oxygen = Regex("\\b(spo\\s*2|oxigenacao|saturacao|oxigenio|oximetro)\\b").containsMatchIn(text)
        val heart = Regex("\\b(batimentos?|frequencia cardiaca|pulso|bpm)\\b").containsMatchIn(text)
        if (oxygen && heart) return null
        val metric = if (oxygen) OXYGEN else if (heart) HEART else expectedMetric ?: return null
        if (expectedMetric != null && metric != expectedMetric) return null
        text = text.replace(Regex("spo\\s*2"), "saturacao")
        val digits = Regex("-?\\d+(?:[.,]\\d+)?").findAll(text).map { it.value }.toList()
        val numberText = if (digits.size == 1) digits.single() else if (digits.isNotEmpty()) return null else {
            val numbers = mapOf("zero" to 0, "um" to 1, "uma" to 1, "dois" to 2, "duas" to 2,
                "tres" to 3, "quatro" to 4, "cinco" to 5, "seis" to 6, "sete" to 7, "oito" to 8, "nove" to 9,
                "dez" to 10, "onze" to 11, "doze" to 12, "treze" to 13, "quatorze" to 14, "quinze" to 15,
                "dezesseis" to 16, "dezessete" to 17, "dezoito" to 18, "dezenove" to 19, "vinte" to 20,
                "trinta" to 30, "quarenta" to 40, "cinquenta" to 50, "sessenta" to 60, "setenta" to 70,
                "oitenta" to 80, "noventa" to 90, "cem" to 100, "cento" to 100, "duzentos" to 200, "trezentos" to 300)
            val tokens = text.split(Regex("\\s+"))
            val start = tokens.indexOfFirst { it in numbers }
            if (start < 0) return null
            val sequence = tokens.drop(start).takeWhile { it in numbers || it == "e" }
            if (tokens.drop(start + sequence.size).any { it in numbers }) return null
            sequence.mapNotNull { numbers[it] }.sum().toString()
        }
        return value(metric, numberText)?.let { HealthReadingDraft(metric, it) }
    }
}
