package br.com.bragasaude.data.util

import android.content.Context
import android.net.Uri
import br.com.bragasaude.data.remote.model.RemoteExamItem
import br.com.bragasaude.data.util.HealthFormatter
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExamExtractor @Inject constructor(
    @ApplicationContext private val context: Context
) {

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
                                        status = "PENDING"
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
