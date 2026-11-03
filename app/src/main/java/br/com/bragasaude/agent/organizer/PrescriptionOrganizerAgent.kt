package br.com.bragasaude.agent.organizer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import br.com.bragasaude.agent.extraction.CascadeTextExtractor
import br.com.bragasaude.agent.extraction.ExtractedDocument
import br.com.bragasaude.agent.extraction.ExtractedPage
import br.com.bragasaude.agent.tools.ExtractMedicationItemsTool
import br.com.bragasaude.agent.tools.GetPatientMedicationsTool
import br.com.bragasaude.agent.tools.SearchAnvisaCatalogTool
import br.com.bragasaude.data.local.Daos
import br.com.bragasaude.data.local.MedicationDao
import br.com.bragasaude.data.remote.api.BragaApiClient
import br.com.bragasaude.data.remote.model.AnalyzedMedicationItemDto
import br.com.bragasaude.data.remote.model.PrescriptionAnalysisResponseDto
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.text.Normalizer
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Agente Conciliador de Receitas Médicas On-Device (Zero File Egress).
 *
 * Executa o fluxo agêntico local de extração de medicamentos, validação contra o catálogo
 * oficial da ANVISA e verificação de duplicidades no banco de dados local Room do paciente.
 */
@Singleton
class PrescriptionOrganizerAgent @Inject constructor(
    @ApplicationContext private val context: Context,
    private val cascadeExtractor: CascadeTextExtractor,
    private val medicationDao: MedicationDao,
    private val apiClient: BragaApiClient? = null
) {

    suspend fun analyzePrescription(
        patientId: String,
        uri: Uri,
        onProgress: ((String) -> Unit)? = null
    ): PrescriptionAnalysisResponseDto? = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val isPdf = runCatching {
            resolver.openInputStream(uri)?.use { stream ->
                val header = ByteArray(5)
                val read = stream.read(header)
                read == 5 && String(header, Charsets.US_ASCII) == "%PDF-"
            }
        }.getOrNull() ?: false

        val extractedDoc = if (isPdf) {
            onProgress?.invoke("Lendo receita médica em formato PDF...")
            val tempFile = File(context.cacheDir, "presc-agent-${UUID.randomUUID()}.pdf")
            try {
                resolver.openInputStream(uri)?.use { input ->
                    tempFile.outputStream().use { output -> input.copyTo(output) }
                }
                cascadeExtractor.extractFromPdf(tempFile)
            } finally {
                tempFile.delete()
            }
        } else {
            onProgress?.invoke("Processando imagem da receita via OCR on-device...")
            val bitmap = resolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream)
            } ?: return@withContext null
            try {
                val page = cascadeExtractor.extractFromBitmap(bitmap, pageIndex = 0)
                ExtractedDocument(pages = listOf(page), fullText = page.text)
            } finally {
                bitmap.recycle()
            }
        }

        analyzeExtractedDocument(patientId, extractedDoc, onProgress)
    }

    suspend fun analyzeExtractedDocument(
        patientId: String,
        extractedDoc: ExtractedDocument,
        onProgress: ((String) -> Unit)? = null
    ): PrescriptionAnalysisResponseDto = withContext(Dispatchers.IO) {
        onProgress?.invoke("Extraindo medicamentos, posologias e horários...")

        var activeDoc: ExtractedDocument? = extractedDoc
        val extractionTool = ExtractMedicationItemsTool { activeDoc }
        val rawItems = extractionTool.extractMedicationsFromText(extractedDoc.fullText, pageIndex = 0)

        onProgress?.invoke("Consultando catálogo oficial da ANVISA...")
        val anvisaTool = SearchAnvisaCatalogTool { ean ->
            runCatching { apiClient?.lookupBarcode(ean) }.getOrNull()
        }

        onProgress?.invoke("Conciliando tratamentos ativos do paciente...")
        val patientMedsTool = GetPatientMedicationsTool { id ->
            runCatching { medicationDao.getAllSync(id) }.getOrElse { emptyList() }
        }
        val activePatientMeds = runCatching { medicationDao.getAllSync(patientId) }.getOrElse { emptyList() }

        val enrichedList = mutableListOf<AnalyzedMedicationItemDto>()
        var duplicatesMerged = 0

        for (item in rawItems) {
            val anvisaJson = anvisaTool.execute("""{"name": "${item.name}"}""")
            val anvisaObj = runCatching { Json.parseToJsonElement(anvisaJson).jsonObject }.getOrNull()
            val foundAnvisa = anvisaObj?.get("found")?.jsonPrimitive?.booleanOrNull ?: false

            var activePrinciple = item.activePrinciple
            var anvisaReg = item.anvisaRegistrationNumber
            var ean = item.eanBarcode

            if (foundAnvisa) {
                activePrinciple = anvisaObj?.get("active_principle")?.jsonPrimitive?.content ?: activePrinciple
                anvisaReg = anvisaObj?.get("anvisa_registration_number")?.jsonPrimitive?.content ?: anvisaReg
                ean = anvisaObj?.get("ean_barcode")?.jsonPrimitive?.content ?: ean
            }

            // Checagem de duplicidade contra o prontuário ativo do paciente no Room
            val cleanItemName = clean(item.name)
            val alreadyActive = activePatientMeds.firstOrNull { activeMed ->
                clean(activeMed.name).contains(cleanItemName) ||
                cleanItemName.contains(clean(activeMed.name)) ||
                (activePrinciple != null && activeMed.activePrinciple != null &&
                 clean(activeMed.activePrinciple).contains(clean(activePrinciple)))
            }

            val divergenceReason = when {
                alreadyActive != null -> "Paciente já possui tratamento ativo com este medicamento (${alreadyActive.name})"
                item.divergenceReason != null -> item.divergenceReason
                else -> null
            }

            val requiresHumanFill = item.requiresHumanFill || alreadyActive != null

            val enriched = item.copy(
                activePrinciple = activePrinciple,
                anvisaRegistrationNumber = anvisaReg,
                eanBarcode = ean,
                divergenceReason = divergenceReason,
                requiresHumanFill = requiresHumanFill
            )

            // Deduplicação contra itens já presentes na mesma receita (1ª e 2ª via)
            val existingIndex = enrichedList.indexOfFirst {
                clean(it.name) == clean(enriched.name) && it.dosage == enriched.dosage
            }

            if (existingIndex >= 0) {
                val existing = enrichedList[existingIndex]
                val mergedPages = (existing.sourcePages + enriched.sourcePages).distinct().sorted()
                enrichedList[existingIndex] = existing.copy(sourcePages = mergedPages)
                duplicatesMerged++
            } else {
                enrichedList.add(enriched)
            }
        }

        onProgress?.invoke("Receita conciliada com sucesso.")

        PrescriptionAnalysisResponseDto(
            status = "success",
            totalPagesAnalyzed = extractedDoc.totalPages,
            duplicatesMergedCount = duplicatesMerged,
            medications = enrichedList,
            rawOcrSnippet = extractedDoc.fullText.take(500)
        )
    }

    private fun clean(s: String?): String {
        if (s == null) return ""
        return Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD)
            .replace(Regex("""\p{M}"""), "")
            .trim()
    }
}
