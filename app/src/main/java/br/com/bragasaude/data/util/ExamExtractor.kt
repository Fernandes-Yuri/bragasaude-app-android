package br.com.bragasaude.data.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import br.com.bragasaude.data.remote.model.RemoteExamItem
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Categoria funcional do exame detectada via heurística on-device.
 * Conforme Decisão D49 e Seção 2 do Caderno de Contratos (Doc 08).
 */
enum class ExamCategoryType {
    STRUCTURED_LAB,       // Tipo A: Exame laboratorial estruturado (Glicose, Colesterol, Hemograma)
    UNSTRUCTURED_DOCUMENT // Tipo B: Imagem / Laudo dissertativo (Ultrassom, Tomografia, ECG, Raio-X)
}

@Singleton
class ExamExtractor @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val textRecognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    init {
        try {
            PDFBoxResourceLoader.init(context)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun extractTextFromPdf(uri: Uri): String = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                PDDocument.load(inputStream).use { document ->
                    val stripper = PDFTextStripper()
                    stripper.getText(document)
                }
            } ?: ""
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }
    }

    /**
     * Extração de texto on-device a partir de Bitmap utilizando Google ML Kit Text Recognition.
     */
    suspend fun extractTextFromBitmap(bitmap: Bitmap): String = withContext(Dispatchers.Default) {
        try {
            val image = InputImage.fromBitmap(bitmap, 0)
            val result = textRecognizer.process(image).await()
            result.text
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }
    }

    /**
     * Extração de texto on-device para laudos multipage (1 a 5 páginas consecutivas).
     */
    suspend fun extractTextFromBitmaps(bitmaps: List<Bitmap>): String = withContext(Dispatchers.Default) {
        val sb = StringBuilder()
        for (bitmap in bitmaps) {
            val pageText = extractTextFromBitmap(bitmap)
            if (pageText.isNotBlank()) {
                sb.append(pageText).append("\n\n")
            }
        }
        sb.toString()
    }

    /**
     * Extração de texto on-device a partir de Uri de imagem utilizando Google ML Kit Text Recognition.
     */
    suspend fun extractTextFromImageUri(uri: Uri): String = withContext(Dispatchers.IO) {
        try {
            val image = InputImage.fromFilePath(context, uri)
            val result = textRecognizer.process(image).await()
            result.text
        } catch (e: Exception) {
            // Fallback via decodificação de stream
            try {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    val bitmap = BitmapFactory.decodeStream(stream)
                    if (bitmap != null) {
                        extractTextFromBitmap(bitmap)
                    } else ""
                } ?: ""
            } catch (fallbackEx: Exception) {
                fallbackEx.printStackTrace()
                ""
            }
        }
    }

    /**
     * Classifica o laudo entre Tipo A (STRUCTURED_LAB) e Tipo B (UNSTRUCTURED_DOCUMENT).
     * Analisa palavras-chave dissertativas/radiológicas e marcadores de pares laboratoriais.
     */
    fun detectExamType(rawText: String): ExamCategoryType {
        val lowerText = rawText.lowercase()

        // Termos que caracterizam laudos de imagem / não estruturados (Tipo B)
        val imageKeywords = listOf(
            "ultrassonografia", "ultrassom", "ecografia", "ecocardiograma",
            "tomografia", "tomografia computadorizada", "ressonância magnética", "ressonancia magnetica",
            "raio-x", "radiografia", "mamografia", "densitometria óssea", "densitometria ossea",
            "eletrocardiograma", "holter", "mapa 24h", "endoscopia", "colonoscopia",
            "biópsia", "biopsia", "anatomopatológico", "anatomopatologico",
            "laudo descritivo", "impressão diagnóstica", "conclusão radiológica"
        )

        // Termos laboratoriais padrão (Tipo A)
        val labKeywords = listOf(
            "glicose", "glicemia", "hemoglobina", "hematócrito", "hematocrito",
            "colesterol", "hdl", "ldl", "vldl", "triglicerídeos", "triglicerides",
            "creatinina", "ureia", "uréia", "ácido úrico", "acido urico",
            "leucócitos", "leucocitos", "plaquetas", "tsh", "t4 livre",
            "vitamina d", "vitamina b12", "pcr", "ferritina", "ferro sérico",
            "transaminase", "tgo", "tgp", "sódio", "potássio", "cálcio"
        )

        val imageMatches = imageKeywords.count { lowerText.contains(it) }
        val labMatches = labKeywords.count { lowerText.contains(it) }

        // Se houver termos explícitos de imagem e poucos ou nenhum termo laboratorial: Tipo B
        if (imageMatches > 0 && labMatches == 0) {
            return ExamCategoryType.UNSTRUCTURED_DOCUMENT
        }

        // Se houver mais de 1 termo de imagem forte mesmo com termos dispersos: Tipo B
        if (imageMatches >= 2 && labMatches < 3) {
            return ExamCategoryType.UNSTRUCTURED_DOCUMENT
        }

        // Se encontrou termos laboratoriais: Tipo A
        if (labMatches >= 1) {
            return ExamCategoryType.STRUCTURED_LAB
        }

        // Padrão conservador caso não encontre parâmetros analíticos discretos
        return ExamCategoryType.UNSTRUCTURED_DOCUMENT
    }

    /**
     * Sanitização LGPD On-Device.
     * Expurgar CPF, RG, telefones, CNPJ, CRM e dados de convênio do paciente
     * antes de persistência ou tráfego de dados.
     */
    fun sanitizeDocumentText(raw: String): String {
        if (raw.isBlank()) return raw

        var sanitized = raw

        // 1. CPF (formatado 000.000.000-00 ou 11 dígitos com label)
        sanitized = sanitized.replace(Regex("""\b\d{3}\.\d{3}\.\d{3}-\d{2}\b"""), "[CPF_PROTEGIDO_LGPD]")
        sanitized = sanitized.replace(Regex("""(?i)\b(?:cpf|cic)[:\s]*\d{11}\b"""), "[CPF_PROTEGIDO_LGPD]")

        // 2. RG (formatado 00.000.000-0 ou com label RG:)
        sanitized = sanitized.replace(Regex("""\b\d{1,2}\.\d{3}\.\d{3}-[\d|X|x]\b"""), "[RG_PROTEGIDO_LGPD]")
        sanitized = sanitized.replace(Regex("""(?i)\b(?:rg|identidade|registro\s+geral)[:\s]*[0-9A-Za-z\.\-\/]{5,15}\b"""), "[RG_PROTEGIDO_LGPD]")

        // 3. Telefones brasileiros com DDD: (XX) XXXX-XXXX ou (XX) 9XXXX-XXXX
        sanitized = sanitized.replace(Regex("""(?:\+?55\s?)?(?:\(?\d{2}\)?\s?)?(?:9\d{4}[-\s]?\d{4}|\d{4}[-\s]?\d{4})\b"""), "[TELEFONE_PROTEGIDO_LGPD]")

        // 4. CNPJ de laboratórios/clínicas
        sanitized = sanitized.replace(Regex("""\b\d{2}\.\d{3}\.\d{3}/\d{4}-\d{2}\b"""), "[CNPJ_PROTEGIDO_LGPD]")

        // 5. CRM do médico solicitante
        sanitized = sanitized.replace(Regex("""(?i)\b(?:crm|crm-[a-z]{2})[:\s]*\d+[-]?[\d|a-z]*\b"""), "[CRM_PROTEGIDO_LGPD]")

        // 6. Matrícula / Convênio
        sanitized = sanitized.replace(Regex("""(?i)\b(?:carteira|carteirinha|matr[íi]cula|conv[êe]nio)[:\s]+[A-Za-z0-9\.\-\/]{4,25}\b"""), "[CONVENIO_PROTEGIDO_LGPD]")

        return sanitized
    }

    fun parseToExamItems(rawText: String, userId: String, examId: String): List<RemoteExamItem> {
        val items = mutableListOf<RemoteExamItem>()
        
        // Dicionário ampliado de exames laboratoriais brasileiros
        data class ExamDef(
            val key: String,
            val name: String,
            val defaultUnit: String,
            val keywords: List<String>
        )

        val catalog = listOf(
            ExamDef("glucose", "Glicose em Jejum", "mg/dL", listOf("glicose em jejum", "glicemia de jejum", "glicose", "glicemia")),
            ExamDef("hba1c", "Hemoglobina Glicada (HbA1c)", "%", listOf("hemoglobina glicada", "hba1c", "hba1", "glicohemoglobina")),
            ExamDef("total_cholesterol", "Colesterol Total", "mg/dL", listOf("colesterol total", "colesterol")),
            ExamDef("hdl", "Colesterol HDL", "mg/dL", listOf("colesterol hdl", "hdl - colesterol", "hdl colesterol", "hdl")),
            ExamDef("ldl", "Colesterol LDL", "mg/dL", listOf("colesterol ldl", "ldl - colesterol", "ldl colesterol", "ldl")),
            ExamDef("vldl", "Colesterol VLDL", "mg/dL", listOf("colesterol vldl", "vldl - colesterol", "vldl")),
            ExamDef("triglycerides", "Triglicerídeos", "mg/dL", listOf("triglicerídeos", "triglicerides", "triglicerideos")),
            ExamDef("hemoglobin", "Hemoglobina", "g/dL", listOf("hemoglobina", "hb")),
            ExamDef("hematocrit", "Hematócrito", "%", listOf("hematócrito", "hematocrito", "ht")),
            ExamDef("platelets", "Plaquetas", "/mm³", listOf("plaquetas", "contagem de plaquetas")),
            ExamDef("leukocytes", "Leucócitos Totais", "/mm³", listOf("leucócitos", "leucocitos", "leucócitos totais", "glóbulos brancos")),
            ExamDef("creatinine", "Creatinina", "mg/dL", listOf("creatinina")),
            ExamDef("urea", "Ureia", "mg/dL", listOf("uréia", "ureia")),
            ExamDef("uric_acid", "Ácido Úrico", "mg/dL", listOf("ácido úrico", "acido urico")),
            ExamDef("tsh", "TSH Ultra Sensível", "µUI/mL", listOf("tsh ultra sensível", "tsh ultra-sensivel", "tsh")),
            ExamDef("t4_free", "T4 Livre", "ng/dL", listOf("t4 livre", "tiroxina livre", "t4l")),
            ExamDef("vitamin_d", "Vitamina D (25-OH)", "ng/mL", listOf("vitamina d", "25-hidroxivitamina d", "vit d")),
            ExamDef("vitamin_b12", "Vitamina B12", "pg/mL", listOf("vitamina b12", "cobalamina", "b12")),
            ExamDef("pcr", "Proteína C Reativa (PCR)", "mg/L", listOf("proteína c reativa", "proteina c reativa", "pcr ultra sensível", "pcr")),
            ExamDef("ast_tgo", "TGO / AST", "U/L", listOf("transaminase oxalacética", "tgo / ast", "tgo", "ast")),
            ExamDef("alt_tgp", "TGP / ALT", "U/L", listOf("transaminase pirúvica", "tgp / alt", "tgp", "alt")),
            ExamDef("sodium", "Sódio", "mEq/L", listOf("sódio", "sodio", "na+")),
            ExamDef("potassium", "Potássio", "mEq/L", listOf("potássio", "potassio", "k+")),
            ExamDef("calcium", "Cálcio Total", "mg/dL", listOf("cálcio total", "calcio total", "cálcio", "calcio")),
            ExamDef("iron", "Ferro Sérico", "µg/dL", listOf("ferro sérico", "ferro serico", "ferro")),
            ExamDef("ferritin", "Ferritina", "ng/mL", listOf("ferritina sérica", "ferritina"))
        )

        val lines = rawText.lowercase().split("\n")
        val addedKeys = mutableSetOf<String>()

        catalog.forEach { def ->
            if (addedKeys.contains(def.key)) return@forEach
            
            for (keyword in def.keywords) {
                for (line in lines) {
                    if (line.contains(keyword)) {
                        val afterKeyword = line.substringAfter(keyword)
                        // Procura número após o nome do exame (ex: ": 125,5" ou "125.5 mg/dl" ou " 125 ")
                        val regex = """[:\s=]*(\d+[,.]?\d*)""".toRegex()
                        val match = regex.find(afterKeyword)
                        if (match != null) {
                            val numStr = match.groupValues[1]
                            val parsedVal = HealthFormatter.parseDouble(numStr)
                            if (parsedVal != null && parsedVal > 0) {
                                items.add(
                                    RemoteExamItem(
                                        examId = examId,
                                        userId = userId,
                                        itemKey = def.key,
                                        itemName = def.name,
                                        valueNumeric = parsedVal,
                                        valueText = numStr.replace(".", ","),
                                        unit = def.defaultUnit,
                                        status = "analyzed"
                                    )
                                )
                                addedKeys.add(def.key)
                                break
                            }
                        }
                    }
                }
                if (addedKeys.contains(def.key)) break
            }
        }
        
        return items
    }
}
