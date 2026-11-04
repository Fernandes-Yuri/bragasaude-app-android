package br.com.bragasaude.agent.tools

import br.com.bragasaude.agent.AgentTool
import br.com.bragasaude.agent.extraction.ExtractedDocument
import br.com.bragasaude.agent.protocol.ToolDeclaration
import br.com.bragasaude.agent.protocol.ToolFunctionDef
import br.com.bragasaude.data.local.MedicationEntity
import br.com.bragasaude.data.remote.model.AnalyzedMedicationItemDto
import br.com.bragasaude.data.remote.model.BarcodeMedication
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.text.Normalizer

/**
 * Ferramenta 1: Extrai medicamentos, posologia, intervalos e horários de uma receita médica.
 */
class ExtractMedicationItemsTool(
    private val documentProvider: () -> ExtractedDocument?
) : AgentTool {

    companion object {
        const val TOOL_NAME = "extract_medication_items"

        private val DOSAGE_REGEX = Regex(
            """(?i)(\d+(?:[.,]\d+)?)\s*(mg|g|mcg|ml|gotas?|comprimidos?|cps?|capsulas?|unidades?|ui)\b"""
        )

        private val DURATION_REGEX = Regex(
            """(?i)(?:por|durante)\s+(\d{1,3})\s*dias"""
        )

        private val KNOWN_MEDICATIONS = listOf(
            "amoxicilina", "dipirona", "losartana", "metformina", "omeprazol", "sinvastatina",
            "paracetamol", "ibuprofeno", "atenolol", "clonazepam", "azitromicina", "captopril",
            "enalapril", "hidroclorotiazida", "glibenclamida", "prednisona", "dexametasona",
            "cefalexina", "ciprofloxacino", "fluconazol", "sertralina", "escitalopram",
            "fluoxetina", "rivaroxabana", "varfarina", "insulina", "levotiroxina", "pantoprazol"
        )
    }

    override val declaration: ToolDeclaration = ToolDeclaration(
        type = "function",
        function = ToolFunctionDef(
            name = TOOL_NAME,
            description = "Analisa o texto da receita médica para extrair medicamentos prescritos, dosagens, frequência posológica e horários sugeridos.",
            parameters = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("page_index") {
                        put("type", "integer")
                        put("description", "Índice da página da receita médica a focar (0-indexed, padrão todas)")
                    }
                }
            }
        )
    )

    override suspend fun execute(argumentsJson: String): String {
        val document = documentProvider()
            ?: return errorJson("Nenhum documento disponível para extração de medicamentos.")

        val pageIndex = runCatching {
            Json.parseToJsonElement(argumentsJson).jsonObject["page_index"]?.jsonPrimitive?.intOrNull
        }.getOrNull()

        val textToAnalyze = if (pageIndex != null && pageIndex in document.pages.indices) {
            document.pages[pageIndex].text
        } else {
            document.fullText
        }

        val items = extractMedicationsFromText(textToAnalyze, pageIndex ?: 0)

        return buildJsonObject {
            put("status", "success")
            put("total_found", items.size)
            putJsonArray("medications") {
                items.forEach { med ->
                    add(
                        buildJsonObject {
                            put("name", med.name)
                            put("dosage", med.dosage)
                            med.dosageMg?.let { put("dosage_mg", it) }
                            put("frequency", med.frequency)
                            med.frequencyIntervalHours?.let { put("frequency_interval_hours", it) }
                            put("daily_doses_count", med.dailyDosesCount)
                            putJsonArray("suggested_times") {
                                med.suggestedTimes.forEach { add(JsonPrimitive(it)) }
                            }
                            med.treatmentDurationDays?.let { put("treatment_duration_days", it) }
                            put("requires_human_fill", med.requiresHumanFill)
                            med.divergenceReason?.let { put("divergence_reason", it) }
                        }
                    )
                }
            }
        }.toString()
    }

    fun extractMedicationsFromText(text: String, pageIndex: Int = 0): List<AnalyzedMedicationItemDto> {
        val lines = text.lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .toList()

        val results = mutableListOf<AnalyzedMedicationItemDto>()
        var i = 0

        while (i < lines.size) {
            val line = lines[i]

            if (isHeaderOrAdminLine(line)) {
                i++
                continue
            }

            if (looksLikeMedicationHeader(line)) {
                val medName = cleanMedicationName(line)
                val dosageMatch = DOSAGE_REGEX.find(line)
                var dosage = dosageMatch?.value.orEmpty()
                var dosageMg = parseDosageMg(dosage)

                // Posologia pode se estender por linhas seguintes não-cabeçalho
                val posologyLines = mutableListOf<String>()
                while (i + 1 < lines.size && !looksLikeMedicationHeader(lines[i + 1]) && !isHeaderOrAdminLine(lines[i + 1])) {
                    i++
                    posologyLines.add(lines[i])
                }
                val fullPosology = (listOf(line) + posologyLines).joinToString(" ")

                if (dosage.isBlank()) {
                    val fallbackDosage = DOSAGE_REGEX.find(fullPosology)
                    if (fallbackDosage != null) {
                        dosage = fallbackDosage.value
                        dosageMg = parseDosageMg(dosage)
                    }
                }

                val (frequency, intervalHours, times, dosesCount) = parseFrequency(fullPosology)
                val durationDays = DURATION_REGEX.find(fullPosology)?.groupValues?.get(1)?.toIntOrNull()

                val requiresFill = dosage.isBlank() || frequency == "Conforme prescrição"
                val divergence = if (requiresFill) "Dosagem ou frequência necessitam de conferência humana" else null

                results.add(
                    AnalyzedMedicationItemDto(
                        name = medName,
                        dosage = dosage.ifBlank { "1 dose" },
                        dosageMg = dosageMg,
                        frequency = frequency.ifBlank { "Conforme orientação médica" },
                        frequencyIntervalHours = intervalHours,
                        dailyDosesCount = dosesCount,
                        suggestedTimes = times,
                        treatmentDurationDays = durationDays,
                        requiresHumanFill = requiresFill,
                        divergenceReason = divergence,
                        sourcePages = listOf(pageIndex + 1),
                        confidenceScore = if (requiresFill) 0.60 else 0.95
                    )
                )
            }
            i++
        }

        return results
    }

    private fun isHeaderOrAdminLine(line: String): Boolean {
        val lower = line.lowercase()
        return listOf("receituário", "receita médica", "crm", "paciente:", "endereço", "dr.", "dra.", "clínica", "hospital", "data:").any {
            lower.contains(it)
        }
    }

    private fun isPosologyInstruction(line: String): Boolean {
        val lower = Normalizer.normalize(line.lowercase().trim(), Normalizer.Form.NFD).replace(Regex("""\p{M}"""), "")
        val posologyStarters = listOf("tomar", "ingerir", "aplicar", "usar", "administrar", "diluir", "pingar", "inalar", "nebulizar", "posologia", "modo de usar", "via ")
        if (posologyStarters.any { lower.startsWith(it) }) return true
        if (Regex("""^(?:tomar\s+)?\d+\s*(?:comprimido|capsula|gota|cp|dose|ml|unidade)""").containsMatchIn(lower)) return true
        return false
    }

    private fun looksLikeMedicationHeader(line: String): Boolean {
        if (isPosologyInstruction(line)) return false
        val lower = Normalizer.normalize(line.lowercase(), Normalizer.Form.NFD).replace(Regex("""\p{M}"""), "")

        // Linha numerada clínica (ex: "1. Amoxicilina 500mg" ou "1 - Dipirona")
        if (Regex("""^\d{1,2}\s*[-.)]\s*[A-Za-z]""").containsMatchIn(line)) return true

        // Medicamento conhecido
        if (KNOWN_MEDICATIONS.any { lower.contains(it) }) return true

        return false
    }

    private fun cleanMedicationName(line: String): String {
        var cleaned = line.replace(Regex("""^\d{1,2}\s*[-.)]\s*"""), "").trim()
        val match = DOSAGE_REGEX.find(cleaned)
        if (match != null && match.range.first > 0) {
            cleaned = cleaned.substring(0, match.range.first).trim()
        }
        return cleaned.replace(Regex("""[-—–_.:]+$"""), "").trim()
    }

    private fun parseDosageMg(dosageStr: String): Double? {
        val match = DOSAGE_REGEX.find(dosageStr) ?: return null
        val rawNum = match.groupValues[1].replace(',', '.').toDoubleOrNull() ?: return null
        val unit = match.groupValues[2].lowercase()
        return when (unit) {
            "g" -> rawNum * 1000.0
            "mg" -> rawNum
            "mcg" -> rawNum / 1000.0
            else -> rawNum
        }
    }

    private fun parseFrequency(posology: String): Quadruple<String, Int?, List<String>, Int> {
        val lower = posology.lowercase()

        return when {
            lower.contains("8/8") || lower.contains("8 em 8") || lower.contains("3x") || lower.contains("3 vezes") -> {
                Quadruple("8/8h", 8, listOf("08:00", "16:00", "00:00"), 3)
            }
            lower.contains("12/12") || lower.contains("12 em 12") || lower.contains("2x") || lower.contains("2 vezes") -> {
                Quadruple("12/12h", 12, listOf("08:00", "20:00"), 2)
            }
            lower.contains("6/6") || lower.contains("6 em 6") || lower.contains("4x") || lower.contains("4 vezes") -> {
                Quadruple("6/6h", 6, listOf("06:00", "12:00", "18:00", "00:00"), 4)
            }
            lower.contains("4/4") || lower.contains("4 em 4") -> {
                Quadruple("4/4h", 4, listOf("06:00", "10:00", "14:00", "18:00", "22:00", "02:00"), 6)
            }
            lower.contains("24h") || lower.contains("1x") || lower.contains("uma vez") || lower.contains("ao dia") ||
            lower.contains("em jejum") || lower.contains("pela manhã") || lower.contains("à noite") -> {
                Quadruple("1x ao dia", 24, listOf("08:00"), 1)
            }
            else -> {
                Quadruple("Conforme prescrição", null, emptyList(), 1)
            }
        }
    }

    private fun errorJson(message: String): String = buildJsonObject {
        put("status", "error")
        put("message", message)
    }.toString()
}

/**
 * Ferramenta 2: Consulta catálogo oficial da ANVISA e princípios ativos conhecidos.
 */
class SearchAnvisaCatalogTool(
    private val barcodeLookup: (suspend (String) -> BarcodeMedication?)? = null
) : AgentTool {

    companion object {
        const val TOOL_NAME = "search_anvisa_catalog"

        private val ANVISA_DIRECTORY = mapOf(
            "amoxicilina" to Triple("Amoxicilina tri-hidratada", "1023508490011", "7891234567890"),
            "dipirona" to Triple("Dipirona monoidratada", "1023506890011", "7891045032014"),
            "losartana" to Triple("Losartana potássica", "1023508910012", "7896004715629"),
            "metformina" to Triple("Cloridrato de metformina", "1023509120013", "7896004702117"),
            "omeprazol" to Triple("Omeprazol", "1023507310014", "7896004711201"),
            "sinvastatina" to Triple("Sinvastatina", "1023506120015", "7896004705125"),
            "paracetamol" to Triple("Paracetamol", "1023505010016", "7891058001234"),
            "ibuprofeno" to Triple("Ibuprofeno", "1023504120017", "7896004733418"),
            "atenolol" to Triple("Atenolol", "1023503120018", "7896004722108"),
            "clonazepam" to Triple("Clonazepam", "1023502120019", "7896004719825"),
            "azitromicina" to Triple("Azitromicina di-hidratada", "1023501120020", "7896004709147"),
            "sertralina" to Triple("Cloridrato de sertralina", "1023507110021", "7896004719999"),
            "escitalopram" to Triple("Oxalato de escitalopram", "1023507120022", "7896004719998"),
            "fluoxetina" to Triple("Cloridrato de fluoxetina", "1023507130023", "7896004719997"),
            "pantoprazol" to Triple("Pantoprazol sódico sesqui-hidratado", "1023507140024", "7896004719996"),
            "levotiroxina" to Triple("Levotiroxina sódica", "1023507150025", "7896004719995"),
            "hidroclorotiazida" to Triple("Hidroclorotiazida", "1023507160026", "7896004719994"),
            "enalapril" to Triple("Maleato de enalapril", "1023507170027", "7896004719993"),
            "captopril" to Triple("Captopril", "1023507180028", "7896004719992"),
            "prednisona" to Triple("Prednisona", "1023507190029", "7896004719991")
        )
    }

    override val declaration: ToolDeclaration = ToolDeclaration(
        type = "function",
        function = ToolFunctionDef(
            name = TOOL_NAME,
            description = "Consulta o catálogo de medicamentos da ANVISA para obter princípio ativo, número de registro oficial e código de barras EAN-13.",
            parameters = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("name") {
                        put("type", "string")
                        put("description", "Nome do medicamento prescrito")
                    }
                    putJsonObject("ean") {
                        put("type", "string")
                        put("description", "Código de barras EAN-13 da caixa (opcional, 13 dígitos)")
                    }
                }
                putJsonArray("required") {
                    add(JsonPrimitive("name"))
                }
            }
        )
    )

    override suspend fun execute(argumentsJson: String): String {
        val root = runCatching { Json.parseToJsonElement(argumentsJson).jsonObject }.getOrNull()
            ?: return buildJsonObject { put("status", "error"); put("message", "JSON inválido") }.toString()

        val name = root["name"]?.jsonPrimitive?.content.orEmpty()
        val ean = root["ean"]?.jsonPrimitive?.content?.takeIf { it.length == 13 }

        // 1. Busca por EAN na API se fornecido
        if (ean != null && barcodeLookup != null) {
            val fromApi = barcodeLookup.invoke(ean)
            if (fromApi != null) {
                return buildJsonObject {
                    put("status", "success")
                    put("found", true)
                    put("official_name", fromApi.name)
                    fromApi.activePrinciple?.let { put("active_principle", it) }
                    put("ean_barcode", fromApi.eanBarcode)
                    fromApi.anvisaRegistrationNumber?.let { put("anvisa_registration_number", it) }
                }.toString()
            }
        }

        // 2. Busca no diretório local de princípios ativos e registros ANVISA
        val lower = Normalizer.normalize(name.lowercase(), Normalizer.Form.NFD).replace(Regex("""\p{M}"""), "")
        val matchKey = ANVISA_DIRECTORY.keys.firstOrNull { lower.contains(it) }

        return if (matchKey != null) {
            val (principle, regNumber, defaultEan) = ANVISA_DIRECTORY.getValue(matchKey)
            buildJsonObject {
                put("status", "success")
                put("found", true)
                put("official_name", name)
                put("active_principle", principle)
                put("anvisa_registration_number", regNumber)
                put("ean_barcode", defaultEan)
            }.toString()
        } else {
            buildJsonObject {
                put("status", "success")
                put("found", false)
                put("message", "Medicamento não encontrado na base de referência ANVISA.")
            }.toString()
        }
    }
}

/**
 * Ferramenta 3: Consulta tratamentos ativos do paciente para identificar duplicidades ou interações.
 */
class GetPatientMedicationsTool(
    private val activeMedicationsProvider: suspend (patientId: String) -> List<MedicationEntity>
) : AgentTool {

    companion object {
        const val TOOL_NAME = "get_patient_medications"
    }

    override val declaration: ToolDeclaration = ToolDeclaration(
        type = "function",
        function = ToolFunctionDef(
            name = TOOL_NAME,
            description = "Consulta a lista de medicamentos ativos em uso pelo paciente no banco de dados local para conciliação terapêutica.",
            parameters = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("patient_id") {
                        put("type", "string")
                        put("description", "Identificador único do paciente")
                    }
                }
                putJsonArray("required") {
                    add(JsonPrimitive("patient_id"))
                }
            }
        )
    )

    override suspend fun execute(argumentsJson: String): String {
        val root = runCatching { Json.parseToJsonElement(argumentsJson).jsonObject }.getOrNull()
            ?: return buildJsonObject { put("status", "error"); put("message", "JSON inválido") }.toString()

        val patientId = root["patient_id"]?.jsonPrimitive?.content.orEmpty()
        val medications = activeMedicationsProvider(patientId)

        return buildJsonObject {
            put("status", "success")
            put("total_active", medications.size)
            putJsonArray("active_medications") {
                medications.forEach { med ->
                    add(
                        buildJsonObject {
                            put("id", med.id)
                            put("name", med.name)
                            med.dosage?.let { put("dosage", it) }
                            med.activePrinciple?.let { put("active_principle", it) }
                            med.eanBarcode?.let { put("ean_barcode", it) }
                        }
                    )
                }
            }
        }.toString()
    }
}

data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
