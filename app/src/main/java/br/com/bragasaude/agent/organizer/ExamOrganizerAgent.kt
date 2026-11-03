package br.com.bragasaude.agent.organizer

import android.graphics.Bitmap
import br.com.bragasaude.agent.extraction.CascadeTextExtractor
import br.com.bragasaude.agent.extraction.ExtractedDocument
import br.com.bragasaude.agent.tools.DetectExamSectorsTool
import br.com.bragasaude.agent.tools.ExtractExamHeaderTool
import br.com.bragasaude.agent.tools.InspectExamPagesTool
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Resultado da análise estruturada do exame pelo agente.
 */
data class ExamAnalysisResult(
    val title: String,
    val date: String,
    val type: String, // Setor canônico: Laboratorial, Imagem, Cardiológico ou Outros
    val topics: List<String>,
    val pages: Int,
    val confidence: Float,
    val isDigital: Boolean
)

/**
 * Agente Organizador de Exames on-device.
 *
 * Utiliza o pipeline de extração em cascata e as ferramentas de inspeção clínica
 * para identificar tipo, laboratório, data e setor do exame com Zero File Egress.
 */
@Singleton
class ExamOrganizerAgent @Inject constructor(
    private val cascadeExtractor: CascadeTextExtractor
) {
    /**
     * Analisa um arquivo de exame (PDF) e retorna os metadados organizados.
     */
    suspend fun analyzeExam(
        pdfFile: File,
        fallbackName: String,
        onProgress: ((String) -> Unit)? = null
    ): ExamAnalysisResult = withContext(Dispatchers.IO) {
        onProgress?.invoke("Lendo documento digital ou escaneado...")
        val extractedDoc = cascadeExtractor.extractFromPdf(pdfFile)

        onProgress?.invoke("Identificando tipo de exame e laboratório...")
        var activeDoc: ExtractedDocument? = extractedDoc

        val headerTool = ExtractExamHeaderTool { activeDoc }
        val headerJson = headerTool.execute("{}")
        val headerRoot = runCatching { Json.parseToJsonElement(headerJson).jsonObject }.getOrNull()

        var title = headerRoot?.get("title")?.jsonPrimitive?.content.orEmpty()
        if (title.isBlank() || title == "Documento de Exame") {
            title = fallbackName.substringBeforeLast('.').take(60).ifBlank { "Documento de Exame" }
        }

        val date = headerRoot?.get("date")?.jsonPrimitive?.content
            ?: headerRoot?.get("exam_date")?.jsonPrimitive?.content.orEmpty()

        val topics = headerRoot?.get("topics")?.jsonArray?.map { it.jsonPrimitive.content }.orEmpty()

        onProgress?.invoke("Classificando setor clínico do exame...")
        val sectorTool = DetectExamSectorsTool()
        val sectorArgs = """{"title": "$title"}"""
        val sectorJson = sectorTool.execute(sectorArgs)
        val sectorRoot = runCatching { Json.parseToJsonElement(sectorJson).jsonObject }.getOrNull()
        val sector = sectorRoot?.get("sector")?.jsonPrimitive?.content ?: "Outros"

        ExamAnalysisResult(
            title = title,
            date = date,
            type = sector,
            topics = topics,
            pages = extractedDoc.totalPages,
            confidence = extractedDoc.averageConfidence,
            isDigital = extractedDoc.isFullyDigital
        )
    }

    /**
     * Analisa uma foto/imagem de exame já carregada em [Bitmap].
     */
    suspend fun analyzeBitmap(
        bitmap: Bitmap,
        fallbackName: String,
        onProgress: ((String) -> Unit)? = null
    ): ExamAnalysisResult = withContext(Dispatchers.IO) {
        onProgress?.invoke("Executando leitura assistida on-device (ML Kit)...")
        val page = cascadeExtractor.extractFromBitmap(bitmap, pageIndex = 0)
        val extractedDoc = ExtractedDocument(pages = listOf(page), fullText = page.text)

        onProgress?.invoke("Identificando tipo de exame e data...")
        val headerTool = ExtractExamHeaderTool { extractedDoc }
        val headerJson = headerTool.execute("{}")
        val headerRoot = runCatching { Json.parseToJsonElement(headerJson).jsonObject }.getOrNull()

        var title = headerRoot?.get("title")?.jsonPrimitive?.content.orEmpty()
        if (title.isBlank() || title == "Documento de Exame") {
            title = fallbackName.substringBeforeLast('.').take(60).ifBlank { "Foto de Exame" }
        }

        val date = headerRoot?.get("exam_date")?.jsonPrimitive?.content.orEmpty()
        val topics = headerRoot?.get("topics")?.jsonArray?.map { it.jsonPrimitive.content }.orEmpty()

        onProgress?.invoke("Classificando setor clínico...")
        val sectorTool = DetectExamSectorsTool()
        val sectorArgs = """{"title": "$title"}"""
        val sectorJson = sectorTool.execute(sectorArgs)
        val sectorRoot = runCatching { Json.parseToJsonElement(sectorJson).jsonObject }.getOrNull()
        val sector = sectorRoot?.get("sector")?.jsonPrimitive?.content ?: "Outros"

        ExamAnalysisResult(
            title = title,
            date = date,
            type = sector,
            topics = topics,
            pages = 1,
            confidence = page.averageConfidence,
            isDigital = false
        )
    }
}
