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
            else -> suggestOcrTitle(text, fallback)
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

    // ---- captura de titulo por TERMOS RELEVANTES (feedback: "UdDlLd"/CPF no titulo = ruim) ----
    private fun plainOf(text: String): String =
        Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{M}"), "")

    // linhas de identificacao/administrativas nunca viram titulo (e CPF/documentos nao vazam no indice)
    private val PERSONAL_OR_ADMIN_LINE = Regex(
        "paciente|nasciment|cpf|cnpj|razao social|endereco|identidade|\\brg\\b|convenio\\b|\\bnome\\s*:"
    )
    private val ID_NUMBER_PATTERN = Regex("\\d{3}\\.\\d{3}\\.\\d{3}-\\d{2}|\\d{2}\\.\\d{3}\\.\\d{3}/\\d{4}-\\d{2}|\\b\\d{7,}\\b")

    private fun isPersonalOrAdminData(line: String): Boolean {
        val plain = plainOf(line)
        return PERSONAL_OR_ADMIN_LINE.containsMatchIn(plain) || ID_NUMBER_PATTERN.containsMatchIn(plain)
    }

    // ruido OCR: alternancia maisc/minusc dentro da palavra, aglomerado de consoantes, quase sem vogal
    private fun looksLikeJunkToken(token: String): Boolean {
        val letters = token.filter { it.isLetter() }
        if (letters.length < 5) return false
        var flips = 0
        for (i in 1 until letters.length) if (letters[i].isUpperCase() != letters[i - 1].isUpperCase()) flips++
        if (flips >= 2) return true
        val lower = letters.lowercase()
        if (Regex("[^aeiou]{4,}").containsMatchIn(lower)) return true
        val vowels = lower.count { it == 'a' || it == 'e' || it == 'i' || it == 'o' || it == 'u' }
        return letters.length >= 6 && vowels.toFloat() / letters.length < 0.15f
    }

    private fun lineLooksJunk(line: String): Boolean {
        val tokens = line.split(Regex("\\s+")).filter { it.isNotBlank() }
        if (tokens.isEmpty()) return true
        val junk = tokens.count(::looksLikeJunkToken)
        return (tokens.size == 1 && junk == 1) || junk > tokens.size / 2
    }

    private fun isOnlyLabel(line: String): Boolean =
        line.endsWith(":") && line.substringBefore(':').split(' ').count { it.isNotBlank() } <= 2

    private fun isInstitutionalHeader(line: String): Boolean {
        if (line.length !in 8..45 || line.contains(':') || line.any { it.isDigit() }) return false
        return line.count { it.isLetter() }.toFloat() / line.length >= 0.6f
    }

    /**
     * Titulo sugerido a partir de termos realmente relevantes do documento:
     * (1) linha(s) com vocabulario de saude do proprio documento (tipo/finalidade), juntando
     *     a instituição quando ela aparece logo atras; (2) cabecalho institucional plausivel;
     *     (3) titulo neutro — OCR nao inventa. Identificacao do paciente/admin nunca entra.
     */
    private fun suggestOcrTitle(text: String, fallback: String): String {
        if (!looksLikeHealthDocument(text)) return "Foto de exame"
        val cleaned = text.lineSequence()
            .map { it.trim().replace(Regex("\\s+"), " ") }
            .filter { it.length >= 6 && it.count { c -> c.isLetter() } >= 4 }
            .filterNot(::isOnlyLabel)
            .filterNot(::isPersonalOrAdminData)
            .filterNot(::lineLooksJunk)
            .distinctBy { it.lowercase() }
            .toList()
        val healthLines = cleaned.filter { HEALTH_DOCUMENT_WORDS.containsMatchIn(plainOf(it)) }
        val joined = when {
            healthLines.size >= 2 -> healthLines.take(2).joinToString(" — ")
            healthLines.size == 1 -> {
                val head = cleaned.take(cleaned.indexOf(healthLines.first())).lastOrNull(::isInstitutionalHeader)
                listOfNotNull(head, healthLines[0]).joinToString(" — ")
            }
            else -> ""
        }.take(80).trim()
        if (joined.isNotBlank()) return joined
        return cleaned.firstOrNull(::isInstitutionalHeader)?.take(80)?.trim()
            ?: fallback.substringBeforeLast('.').take(80).ifBlank { "Documento de exame" }
    }

    /**
     * Portao de confianca para sugestoes a partir de OCR: 1 palavra de vocabulario de
     * documento de saude basta. Fotos de objetos/cartazes produzem so lixo OCR — nesses
     * casos o app NAO finge leitura: titulo neutro e data em branco, o usuario digita.
     */
    private val HEALTH_DOCUMENT_WORDS = Regex(
        "hospital|clinic|laborator|laudo|paciente|exame|resultado|amostra|colet|receit|atestado|" +
        "prontuari|termo|medic|enferm|intern|consulta|sorolog|sangue|saude|tomograf|ultrassom|" +
        "ressonanc|mamograf|radiograf|\\balta\\b"
    )

    fun looksLikeHealthDocument(text: String): Boolean {
        val plain = Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{M}"), "")
        return HEALTH_DOCUMENT_WORDS.containsMatchIn(plain)
    }

    fun suggest(text: String, fallback: String): Pair<String, String> {
        val (title, type, _) = suggestWithTopics(text, fallback)
        return title to type
    }
    // Data do exame em cascata: (1) rotulos estritos "Data do exame"/"Data de coleta" — o padrao
    // original dos arquivos digitais; (2) contexto relaxado para documentos escaneados/fotos
    // reais (termos de alta, pedidos, receituarios): qualquer dd/mm/aaaa cuja janela anterior de
    // ~40 caracteres traga "data", "emissao", "realizacao", "coleta", "atendimento", "alta" ou
    // "internacao". Janelas com "nasc" (nascimento) sao rejeitadas: aniversario nao e data de exame.
    // O (2) so roda quando o texto passa no portão de confiança (vocabulário de saúde ou keyword
    // de exame conhecida) — fora disso o OCR estava lendo cenário (calendário, cartaz...) e o
    // app deixa em branco para o usuário conferir manualmente.
    fun suggestDate(text: String): String {
        val strict = Regex("(?i)(?:data\\s+(?:do\\s+exame|de\\s+coleta)|coleta)\\s*[:\\-]?\\s*(\\d{2}/\\d{2}/\\d{4})")
        strict.find(text)?.groupValues?.get(1)?.takeIf(::validDate)?.let { return it }
        if (extractTopics(text).isEmpty() && !looksLikeHealthDocument(text)) return ""
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
