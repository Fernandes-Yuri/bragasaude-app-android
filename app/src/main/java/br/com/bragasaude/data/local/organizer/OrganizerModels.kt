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
    val confirmed: Boolean = false
)
data class OrganizerSession(val id: String, val createdAt: Long, val documents: List<OrganizerDocument> = emptyList()) {
    val expiresAt: Long get() = createdAt + SESSION_DURATION
    fun expired(now: Long) = now >= expiresAt || now < createdAt
    companion object { const val SESSION_DURATION = 24 * 60 * 60 * 1000L }
}
object OrganizerMetadata {
    val types = listOf("Laboratorial", "Imagem", "Outros")
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
    fun suggest(text: String, fallback: String): Pair<String, String> {
        val plain = Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{M}"), "")
        val title = when {
            "hemograma" in plain -> "Hemograma"
            "ultrass" in plain -> "Ultrassonografia"
            "ressonancia" in plain -> "Ressonância magnética"
            "tomografia" in plain -> "Tomografia"
            "eletrocardiograma" in plain -> "Eletrocardiograma"
            "mamografia" in plain -> "Mamografia"
            else -> fallback.substringBeforeLast('.').take(80).ifBlank { "Documento de exame" }
        }
        val type = when {
            listOf("ultrass", "ressonancia", "tomografia", "mamografia", "radiografia").any { it in plain } -> "Imagem"
            listOf("hemograma", "glicose", "colesterol", "creatinina").any { it in plain } -> "Laboratorial"
            else -> "Outros"
        }
        return title to type
    }
    // Only collection/exam date labels; never use the first date, which may be a birth date.
    fun suggestDate(text: String): String = Regex("(?i)(?:data\\s+(?:do\\s+exame|de\\s+coleta)|coleta)\\s*[:\\-]?\\s*(\\d{2}/\\d{2}/\\d{4})")
        .find(text)?.groupValues?.get(1)?.takeIf(::validDate).orEmpty()
}
