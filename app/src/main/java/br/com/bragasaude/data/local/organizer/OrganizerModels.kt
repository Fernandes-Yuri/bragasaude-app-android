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
    // Cor de ticket por setor, fonte única para índice, capas, divisórias e tela.
    fun pdfColor(type: String): Triple<Int, Int, Int> = when (type.lowercase()) {
        "laboratorial" -> Triple(0, 137, 123)
        "imagem" -> Triple(2, 132, 199)
        "cardiológico", "cardiologico" -> Triple(124, 58, 237)
        else -> Triple(100, 116, 139)
    }
    data class DateGroup(val date: String, val documents: List<OrganizerDocument>)
    data class Sector(val type: String, val groups: List<DateGroup>)

    /** Setores na ordem fixa; dentro de cada setor, grupos por data (mais recentes primeiro) e sem-data por último. */
    fun sections(documents: List<OrganizerDocument>, newest: Boolean): List<Sector> =
        types.map { sectorType -> documents.filter { it.type == sectorType } }
            .filter { it.isNotEmpty() }
            .map { docs ->
                val sorted = docs.sortedWith { a, b ->
                    val da = dateKey(a.date); val db = dateKey(b.date)
                    val c = when {
                        da == null && db != null -> 1
                        da != null && db == null -> -1
                        else -> if (newest) compareValues(db, da) else compareValues(da, db)
                    }
                    if (c != 0) c else compareValuesBy(a, b, { it.title.lowercase() }, { it.id })
                }
                val groups = sorted.groupBy { it.date }.entries
                    .sortedWith { x, y ->
                        val dx = dateKey(x.key); val dy = dateKey(y.key)
                        when {
                            dx == null && dy != null -> 1
                            dx != null && dy == null -> -1
                            else -> if (newest) compareValues(dy, dx) else compareValues(dx, dy)
                        }
                    }
                    .map { DateGroup(it.key, it.value) }
                Sector(docs.first().type, groups)
            }
    private fun hasWord(plain: String, word: String): Boolean =
        Regex("\\b${Regex.escape(word)}\\b").containsMatchIn(plain)
    fun extractTopics(text: String): List<String> {
        val plain = Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{M}"), "")
        val detected = mutableListOf<String>()
        if (listOf("hemograma", "leucocit", "eritrocit", "plaqueta").any { it in plain }) detected.add("Hemograma")
        if (listOf("glicose", "glicemia", "insulina").any { it in plain }) detected.add("Glicemia")
        if (listOf("colesterol", "hdl", "ldl", "triglicerid").any { it in plain }) detected.add("Perfil Lipídico")
        if (listOf("creatinina", "ureia", "acido urico").any { it in plain }) detected.add("Função Renal")
        if (listOf("tgo", "tgp", "transaminase", "gama gt", "bilirrubina").any { it in plain }) detected.add("Função Hepática")
        // palavras curtas exigem fronteira de palavra: "t3" jamais casa com "t34", "ut3g" etc.
        if (listOf("tsh", "t4 livre", "tireoide").any { it in plain } || hasWord(plain, "t3")) detected.add("Tireoide")
        // "eas" so vale isolada: "osseas", "ideias" e afins nao sao exame de urina
        if (listOf("urina", "sedimentoscopia", "urocultura").any { it in plain } || hasWord(plain, "eas")) detected.add("Urina (EAS)")
        if ("eletrocardiograma" in plain || hasWord(plain, "ecg")) detected.add("Eletrocardiograma")
        if (plain.contains("ecocardiograma") || Regex("\\b(?:ecocardiograf\\w*|ecocardio\\w*|eco)\\b").containsMatchIn(plain))
            detected.add("Ecocardiograma")
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
            "ecocardiogra" in plain -> "Ecocardiograma"
            "mamografia" in plain -> "Mamografia"
            topics.isNotEmpty() -> topics.first()
            else -> {
                // OCR/textraiu texto mas nao conhecemos o exame (ex.: termo de alta):
                // em vez do nome de arquivo, sugira as 2 primeiras linhas uteis do documento.
                val fromText = usefulOcrLines(text).joinToString(" — ").take(80).trim()
                if (fromText.isNotBlank()) fromText else fallback.substringBeforeLast('.').take(80).ifBlank { "Documento de exame" }
            }
        }
        val type = when {
            // "mapa" so vale isolada (exame MAPA); "mapeamento"/"mapamental" nao sao cardiologia
            listOf("eletrocardiograma", "ecocardiogra", "holter", "ergometrico").any { it in plain } || hasWord(plain, "mapa") -> "Cardiológico"
            listOf("ultrass", "ressonancia", "tomografia", "mamografia", "radiografia", "raio-x").any { it in plain } -> "Imagem"
            listOf("hemograma", "glicose", "colesterol", "creatinina", "tsh", "urina", "leucocit").any { it in plain } -> "Laboratorial"
            else -> "Outros"
        }
        return Triple(title, type, topics)
    }
    /** Linhas que parecem conteudo real para servir de titulo sugerido (descarta numeracao, carimbos e simbolos). */
    private fun usefulOcrLines(text: String): List<String> =
        text.lineSequence()
            .map { it.trim().replace(Regex("\\s+"), " ") }
            .filter { line -> line.length >= 6 && line.count { it.isLetter() } >= 4 }
            .take(2).toList()

    fun suggest(text: String, fallback: String): Pair<String, String> {
        val (title, type, _) = suggestWithTopics(text, fallback)
        return title to type
    }
    // Data do exame em cascata: (1) rotulos estritos "Data do exame"/"Data de coleta" — o padrao
    // original dos arquivos digitais; (2) contexto relaxado para documentos escaneados/fotos
    // reais (termos de alta, pedidos, receituarios): qualquer dd/mm/aaaa cuja janela anterior de
    // ~40 caracteres traga "data", "emissao", "realizacao", "coleta", "atendimento", "alta" ou
    // "internacao". Janelas com "nasc" (nascimento) sao rejeitadas: aniversario nao e data de exame.
    fun suggestDate(text: String): String {
        val strict = Regex("(?i)(?:data\\s+(?:do\\s+exame|de\\s+coleta)|coleta)\\s*[:\\-]?\\s*(\\d{2}/\\d{2}/\\d{4})")
        strict.find(text)?.groupValues?.get(1)?.takeIf(::validDate)?.let { return it }
        val plain = Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{M}"), "")
        val anyDate = Regex("\\b(\\d{2}/\\d{2}/\\d{4})\\b")
        // "alta" so vale como palavra (evita "alteracoes"); os demais sao raizes longas seguras
        val context = Regex("data|emiss|realiz|colet|atendiment|intern|\\balta\\b")
        anyDate.findAll(plain).forEach { found ->
            val date = found.groupValues[1]
            if (!validDate(date)) return@forEach
            val window = plain.substring(maxOf(0, found.range.first - 40), found.range.first)
            // anti-nascimento SOH sobre os ultimos caracteres colados na data: rejeitar a linha
            // inteira (janela de 40) mataria a propria "Emissão" quando ela convive com o nascimento
            if (window.takeLast(18).contains("nasc")) return@forEach
            if (context.containsMatchIn(window)) return date
        }
        return ""
    }
}

class OrganizerProblem(message: String) : IllegalStateException(message)
