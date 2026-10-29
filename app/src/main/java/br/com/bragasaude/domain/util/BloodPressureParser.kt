package br.com.bragasaude.domain.util

import br.com.bragasaude.domain.model.BloodPressureCategory
import br.com.bragasaude.domain.model.BloodPressureResult

/**
 * Normaliza valores informados e compara registros com as faixas de consultório
 * para adultos da Diretriz Brasileira de Hipertensão Arterial de 2025.
 * Uma medição isolada não estabelece diagnóstico.
 */
object BloodPressureParser {
    
    private const val BRAZILIAN_SCALE_THRESHOLD = 30
    private const val BRAZILIAN_TO_INTERNATIONAL_MULTIPLIER = 10
    
    /**
     * Normaliza um valor individual de pressão arterial.
     * Se o valor for menor que 30, assume escala brasileira coloquial (ex: 11 → 110, 12 → 120, 6 → 60, 8 → 80, 10 → 100).
     */
    fun normalizePressure(rawValue: Int): Int {
        return if (rawValue < BRAZILIAN_SCALE_THRESHOLD && rawValue > 0) {
            rawValue * BRAZILIAN_TO_INTERNATIONAL_MULTIPLIER
        } else {
            rawValue
        }
    }

    /**
     * Parseia um único valor numérico digitado em campos separados (Sistólica ou Diastólica).
     */
    fun parseSingleValue(input: String): Int? {
        val cleaned = input.trim()
        val num = cleaned.toIntOrNull() ?: return null
        return normalizePressure(num)
    }
    
    /**
     * Parseia uma string de pressão arterial no formato "X/Y", "XxY" ou "X Y".
     * @param input String no formato "12/8", "120/80", "12x8", etc.
     * @return Pair de (sistólica, diastólica) normalizados em mmHg
     */
    fun parsePressureString(input: String): Pair<Int, Int>? {
        return try {
            val delimiter = when {
                input.contains("/") -> "/"
                input.contains("x", ignoreCase = true) -> if (input.contains("X")) "X" else "x"
                input.contains(" ") -> " "
                input.contains("-") -> "-"
                else -> return null
            }
            
            val parts = input.split(delimiter).filter { it.isNotBlank() }
            if (parts.size != 2) return null
            
            val systolic = normalizePressure(parts[0].trim().toInt())
            val diastolic = normalizePressure(parts[1].trim().toInt())
            
            // Validação de limites biológicos aceitáveis
            if (systolic !in 40..300 || diastolic !in 20..200) {
                return null
            }
            
            Pair(systolic, diastolic)
        } catch (e: NumberFormatException) {
            null
        }
    }
    
    /**
     * Compara os valores com as faixas de consultório para adultos da diretriz de 2025.
     */
    fun classify(systolic: Int, diastolic: Int): BloodPressureResult {
        // Classificação canônica conforme Diretrizes Brasileiras de Hipertensão Arterial (SBC)
        val category = when {
            systolic < 90 || diastolic < 60 -> BloodPressureCategory.HYPOTENSION
            systolic >= 180 || diastolic >= 110 -> BloodPressureCategory.STAGE_3_HYPERTENSION
            systolic >= 160 || diastolic >= 100 -> BloodPressureCategory.STAGE_2_HYPERTENSION
            systolic >= 140 || diastolic >= 90 -> BloodPressureCategory.STAGE_1_HYPERTENSION
            systolic >= 130 || diastolic >= 85 -> BloodPressureCategory.PREHYPERTENSION
            systolic >= 120 || diastolic >= 80 -> BloodPressureCategory.NORMAL
            else -> BloodPressureCategory.OPTIMAL
        }
        val isNormal = category == BloodPressureCategory.OPTIMAL || category == BloodPressureCategory.NORMAL
        return BloodPressureResult(
            systolic = systolic,
            diastolic = diastolic,
            category = category,
            isNormal = isNormal,
            requiresMedicalAttention = category in listOf(
                BloodPressureCategory.STAGE_1_HYPERTENSION,
                BloodPressureCategory.STAGE_2_HYPERTENSION,
                BloodPressureCategory.STAGE_3_HYPERTENSION
            ),
            explanatoryDetail = "Registro de $systolic/$diastolic mmHg. Classificado como ${category.displayName} conforme as Diretrizes Brasileiras de Hipertensão Arterial (SBC). Uma medição isolada não estabelece diagnóstico. Converse com um profissional de saúde sobre seus registros.",
            institution = "Diretriz Brasileira de Hipertensão Arterial (SBC)"
        )
    }

    /**
     * Parseia e classifica pressão arterial a partir de string
     */
    fun parseAndClassify(input: String): BloodPressureResult? {
        val result = parsePressureString(input) ?: return null
        return classify(result.first, result.second)
    }
    
    /**
     * Valida se os valores de pressão são consistentes
     */
    fun isValidPressure(systolic: Int, diastolic: Int): Boolean {
        return systolic in 40..300 && 
               diastolic in 20..200 && 
               systolic > diastolic
    }
}
