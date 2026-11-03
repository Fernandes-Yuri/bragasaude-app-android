package br.com.bragasaude.agent.tools

import br.com.bragasaude.agent.AgentTool
import br.com.bragasaude.agent.extraction.ExtractedDocument
import br.com.bragasaude.agent.protocol.ToolDeclaration
import br.com.bragasaude.agent.protocol.ToolFunctionDef
import br.com.bragasaude.data.local.organizer.OrganizerMetadata
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.text.Normalizer
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Ferramenta 1: Inspeciona páginas e metadados de fidelidade do exame.
 */
class InspectExamPagesTool(
    private val documentProvider: () -> ExtractedDocument?
) : AgentTool {

    companion object {
        const val TOOL_NAME = "inspect_exam_pages"
    }

    override val declaration: ToolDeclaration = ToolDeclaration(
        type = "function",
        function = ToolFunctionDef(
            name = TOOL_NAME,
            description = "Lista o total de páginas, modo de extração (digital ou OCR) e prévia do conteúdo textual de cada página do exame médico.",
            parameters = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("max_preview_length") {
                        put("type", "integer")
                        put("description", "Tamanho máximo da prévia de texto por página (padrão 250)")
                    }
                }
            }
        )
    )

    override suspend fun execute(argumentsJson: String): String {
        val document = documentProvider()
            ?: return errorJson("Nenhum documento carregado para inspeção.")

        val maxPreview = runCatching {
            Json.parseToJsonElement(argumentsJson).jsonObject["max_preview_length"]?.jsonPrimitive?.int
        }.getOrNull() ?: 250

        return buildJsonObject {
            put("status", "success")
            put("total_pages", document.totalPages)
            put("is_fully_digital", document.isFullyDigital)
            put("average_confidence", document.averageConfidence)
            putJsonArray("pages") {
                document.pages.forEach { page ->
                    add(
                        buildJsonObject {
                            put("page_index", page.pageIndex)
                            put("is_digital", page.isDigital)
                            put("average_confidence", page.averageConfidence)
                            put("text_preview", page.text.take(maxPreview))
                            put("has_ambiguities", page.hasAmbiguities())
                        }
                    )
                }
            }
        }.toString()
    }

    private fun errorJson(message: String): String = buildJsonObject {
        put("status", "error")
        put("message", message)
    }.toString()
}

/**
 * Ferramenta 2: Captura cabeçalho clínico (tipo do exame, laboratório/clínica e data de coleta/realização).
 */
class ExtractExamHeaderTool(
    private val documentProvider: () -> ExtractedDocument?
) : AgentTool {

    companion object {
        const val TOOL_NAME = "extract_exam_header"

        private val STRICT_DATE_REGEX = Regex(
            """(?i)(?:data\s+(?:do\s+exame|de\s+coleta)|coleta|emiss[aã]o|realiza[cç][aã]o)\s*[:\-]?\s*(\d{2}/\d{2}/\d{4})"""
        )
        private val GENERIC_DATE_REGEX = Regex("""\b(\d{2}/\d{2}/\d{4})\b""")
        private val LAB_PATTERNS = Regex(
            """(?i)\b((?:laborat[oó]rio|hospital|cl[ií]nica|instituto|centro\s+de\s+diagn[oó]stico|diagn[oó]sticos\s+da\s+am[eé]rica|dasa|fleury|sabin|hermes\s+pardini)\s+[A-Za-zÀ-ÖØ-öø-ÿ0-9\s.,'-]+)"""
        )

        fun isValidDate(value: String): Boolean = runCatching {
            LocalDate.parse(
                value,
                DateTimeFormatter.ofPattern("dd/MM/uuuu").withResolverStyle(java.time.format.ResolverStyle.STRICT)
            )
        }.isSuccess
    }

    override val declaration: ToolDeclaration = ToolDeclaration(
        type = "function",
        function = ToolFunctionDef(
            name = TOOL_NAME,
            description = "Analisa as páginas para extrair o título/tipo do exame, laboratório ou clínica emissora e a data de coleta ou realização (DD/MM/AAAA).",
            parameters = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("page_index") {
                        put("type", "integer")
                        put("description", "Índice da página para foco da extração (0-indexed, padrão 0)")
                    }
                }
            }
        )
    )

    override suspend fun execute(argumentsJson: String): String {
        val document = documentProvider()
            ?: return errorJson("Nenhum documento disponível para extração de cabeçalho.")

        val pageIndex = runCatching {
            Json.parseToJsonElement(argumentsJson).jsonObject["page_index"]?.jsonPrimitive?.int
        }.getOrNull() ?: 0

        val targetPage = document.pages.getOrNull(pageIndex) ?: document.pages.firstOrNull()
        val text = targetPage?.text ?: document.fullText

        val title = extractTitle(text)
        val lab = extractLaboratory(text)
        val date = extractExamDate(text)
        val topics = extractClinicalTopics(text)

        return buildJsonObject {
            put("status", "success")
            put("page_index", targetPage?.pageIndex ?: 0)
            put("title", title)
            put("laboratory", lab)
            put("exam_date", date)
            putJsonArray("topics") {
                topics.forEach { add(JsonPrimitive(it)) }
            }
        }.toString()
    }

    private fun extractTitle(text: String): String {
        val plain = Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD).replace(Regex("""\p{M}"""), "")
        val topics = extractClinicalTopics(text)

        return when {
            "hemograma" in plain -> "Hemograma Completo"
            "ecocardiogra" in plain -> "Ecocardiograma"
            "eletrocardiogra" in plain || Regex("""\becg\b""").containsMatchIn(plain) -> "Eletrocardiograma"
            "ultrass" in plain -> "Ultrassonografia"
            "ressonancia" in plain -> "Ressonância Magnética"
            "tomografia" in plain -> "Tomografia"
            "mamografia" in plain -> "Mamografia"
            "radiografia" in plain || "raio-x" in plain || "raiox" in plain -> "Radiografia"
            topics.isNotEmpty() -> topics.first()
            else -> OrganizerMetadata.suggestWithTopics(text, "Documento de Exame").first
        }
    }

    private fun extractLaboratory(text: String): String {
        val match = LAB_PATTERNS.find(text)
        return match?.groupValues?.get(1)?.lines()?.firstOrNull()?.take(60)?.trim() ?: ""
    }

    private fun extractExamDate(text: String): String {
        val metadataDate = OrganizerMetadata.suggestDate(text)
        if (metadataDate.isNotBlank()) return metadataDate

        // Tenta rótulo explícito (coleta, realização, emissão)
        val strictMatch = STRICT_DATE_REGEX.find(text)
        val candidateStrict = strictMatch?.groupValues?.get(1)
        if (candidateStrict != null && isValidDate(candidateStrict)) {
            return candidateStrict
        }

        // Busca contextual ignorando data de nascimento
        val plain = text.lowercase()
        for (match in GENERIC_DATE_REGEX.findAll(text)) {
            val candidate = match.groupValues[1]
            if (!isValidDate(candidate)) continue

            val start = maxOf(0, match.range.first - 40)
            val window = plain.substring(start, match.range.first)

            if (window.contains("nasc")) continue

            if (window.contains("data") || window.contains("emiss") || window.contains("colet") ||
                window.contains("atend") || window.contains("realiz")
            ) {
                return candidate
            }
        }
        return ""
    }

    private fun extractClinicalTopics(text: String): List<String> =
        OrganizerMetadata.extractTopics(text)

    private fun errorJson(message: String): String = buildJsonObject {
        put("status", "error")
        put("message", message)
    }.toString()
}

/**
 * Ferramenta 3: Classifica o exame em um dos 4 setores canônicos (Laboratorial, Imagem, Cardiológico ou Outros).
 */
class DetectExamSectorsTool : AgentTool {

    companion object {
        const val TOOL_NAME = "detect_exam_sectors"
        val VALID_SECTORS = listOf("Laboratorial", "Imagem", "Cardiológico", "Outros")
    }

    override val declaration: ToolDeclaration = ToolDeclaration(
        type = "function",
        function = ToolFunctionDef(
            name = TOOL_NAME,
            description = "Classifica o exame médico no setor clínico apropriado ('Laboratorial', 'Imagem', 'Cardiológico' ou 'Outros') a partir do título e termos clínicos.",
            parameters = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("title") {
                        put("type", "string")
                        put("description", "Título ou nome do exame")
                    }
                    putJsonObject("topics") {
                        put("type", "array")
                        putJsonObject("items") {
                            put("type", "string")
                        }
                        put("description", "Lista de tópicos ou procedimentos detectados no exame")
                    }
                }
                putJsonArray("required") {
                    add(JsonPrimitive("title"))
                }
            }
        )
    )

    override suspend fun execute(argumentsJson: String): String {
        return try {
            val root = Json.parseToJsonElement(argumentsJson).jsonObject
            val title = root["title"]?.jsonPrimitive?.content.orEmpty()
            val topics = root["topics"]?.jsonArray?.map { it.jsonPrimitive.content }.orEmpty()

            val combined = Normalizer.normalize(
                "$title ${topics.joinToString(" ")}".lowercase(),
                Normalizer.Form.NFD
            ).replace(Regex("""\p{M}"""), "")

            val sector = when {
                listOf("eletrocardiograma", "ecocardiogra", "holter", "ergometrico").any { it in combined } ||
                listOf("mapa", "ecg", "eco").any { Regex("""\b${Regex.escape(it)}\b""").containsMatchIn(combined) } -> "Cardiológico"

                listOf("ultrass", "ressonancia", "tomografia", "mamografia", "radiografia", "raio-x", "densitometria").any {
                    it in combined
                } -> "Imagem"

                listOf("hemograma", "glicose", "glicemia", "colesterol", "creatinina", "tsh", "urina", "leucocit", "plaqueta", "eas", "sorologia").any {
                    it in combined
                } -> "Laboratorial"

                else -> "Outros"
            }

            buildJsonObject {
                put("status", "success")
                put("sector", sector)
                put("confidence", 0.95)
                putJsonArray("valid_sectors") {
                    VALID_SECTORS.forEach { add(JsonPrimitive(it)) }
                }
            }.toString()
        } catch (e: Exception) {
            buildJsonObject {
                put("status", "error")
                put("message", "Falha na classificação: ${e.message}")
            }.toString()
        }
    }
}
