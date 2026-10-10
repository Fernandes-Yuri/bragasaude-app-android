package br.com.bragasaude.data.local.organizer

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.text.Normalizer

data class OrganizerDocument(
    val id: String,
    val title: String,
    val date: String = "",
    val type: String = "Outros",
    val pages: Int,
    val confirmed: Boolean = false,
    val photoOnly: Boolean = false,
    val sourceDigest: String = "",
    val possibleDuplicate: Boolean = false,
    val topics: List<String> = emptyList()
)
data class OrganizerSession(val id: String, val createdAt: Long, val documents: List<OrganizerDocument> = emptyList()) {
    val expiresAt: Long get() = createdAt + SESSION_DURATION
    fun expired(now: Long) = now >= expiresAt || now < createdAt
    companion object { const val SESSION_DURATION = 24 * 60 * 60 * 1000L }
}
object OrganizerMetadata {
    val types = listOf("Laboratorial", "Imagem", "Cardiológico", "Outros")
    fun validDate(value: String): Boolean = value.isBlank() || runCatching {
        LocalDate.parse(value, DateTimeFormatter.ofPattern("dd/MM/uuuu").withResolverStyle(java.time.format.ResolverStyle.STRICT))
    }.isSuccess
    fun dateKey(value: String): LocalDate? = runCatching {
        LocalDate.parse(value, DateTimeFormatter.ofPattern("dd/MM/uuuu").withResolverStyle(java.time.format.ResolverStyle.STRICT))
    }.getOrNull()
    fun ordered(documents: List<OrganizerDocument>, newestFirst: Boolean): List<OrganizerDocument> = documents.sortedWith { a, b ->
        val da = dateKey(a.date); val db = dateKey(b.date)
        when {
            da == null && db != null -> 1
            da != null && db == null -> -1
            else -> (if (newestFirst) compareValues(db, da) else compareValues(da, db))
                .takeIf { it != 0 } ?: compareValuesBy(a, b, { it.type }, { it.title.lowercase() }, { it.id })
        }
    }
    fun extractTopics(text: String): List<String> {
        val plain = Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{M}"), "")
        val detected = mutableListOf<String>()
        if (listOf("hemograma", "leucocit", "eritrocit", "plaqueta").any { it in plain }) detected.add("Hemograma")
        if (listOf("glicose", "glicemia", "insulina").any { it in plain }) detected.add("Glicemia")
        if (listOf("colesterol", "hdl", "ldl", "triglicerid").any { it in plain }) detected.add("Perfil Lipídico")
        if (listOf("creatinina", "ureia", "acido urico").any { it in plain }) detected.add("Função Renal")
        if (listOf("tgo", "tgp", "transaminase", "gama gt", "bilirrubina").any { it in plain }) detected.add("Função Hepática")
        if (listOf("tsh", "t4 livre", "t3", "tireoide").any { it in plain }) detected.add("Tireoide")
        if (listOf("urina", "eas", "sedimentoscopia", "urocultura").any { it in plain }) detected.add("Urina (EAS)")
        if (listOf("eletrocardiograma", "ecg").any { it in plain }) detected.add("Eletrocardiograma")
        if (listOf("ecocardiograma", "eco").any { it in plain }) detected.add("Ecocardiograma")
        if (listOf("ultrassonografia", "ultrassom").any { it in plain }) detected.add("Ultrassonografia")
        if (listOf("tomografia").any { it in plain }) detected.add("Tomografia")
        if (listOf("ressonancia").any { it in plain }) detected.add("Ressonância Magnética")
        if (listOf("radiografia", "raio-x", "raiox").any { it in plain }) detected.add("Radiografia")
        if (listOf("mamografia").any { it in plain }) detected.add("Mamografia")
        return detected.distinct()
    }
    fun suggestWithTopics(text: String, fallback: String): Triple<String, String, List<String>> {
        val plain = Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{M}"), "")
        val topics = extractTopics(text)
        val title = when {
            "hemograma" in plain -> "Hemograma"
            "ultrass" in plain -> "Ultrassonografia"
            "ressonancia" in plain -> "Ressonância magnética"
            "tomografia" in plain -> "Tomografia"
            "eletrocardiograma" in plain -> "Eletrocardiograma"
            "ecocardiograma" in plain -> "Ecocardiograma"
            "mamografia" in plain -> "Mamografia"
            topics.isNotEmpty() -> topics.first()
            else -> fallback.substringBeforeLast('.').take(80).ifBlank { "Documento de exame" }
        }
        val type = when {
            listOf("eletrocardiograma", "ecocardiograma", "holter", "mapa", "ergometrico").any { it in plain } -> "Cardiológico"
            listOf("ultrass", "ressonancia", "tomografia", "mamografia", "radiografia", "raio-x").any { it in plain } -> "Imagem"
            listOf("hemograma", "glicose", "colesterol", "creatinina", "tsh", "urina", "leucocit").any { it in plain } -> "Laboratorial"
            else -> "Outros"
        }
        return Triple(title, type, topics)
    }
    fun suggest(text: String, fallback: String): Pair<String, String> {
        val (title, type, _) = suggestWithTopics(text, fallback)
        return title to type
    }
    // Only collection/exam date labels; never use the first date, which may be a birth date.
    fun suggestDate(text: String): String = Regex("(?i)(?:data\\s+(?:do\\s+exame|de\\s+coleta)|coleta)\\s*[:\\-]?\\s*(\\d{2}/\\d{2}/\\d{4})")
        .find(text)?.groupValues?.get(1)?.takeIf(::validDate).orEmpty()
}

class OrganizerProblem(message: String) : IllegalStateException(message)
