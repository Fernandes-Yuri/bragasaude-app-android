package br.com.bragasaude.domain.util

import br.com.bragasaude.domain.model.BloodPressureCategory
import br.com.bragasaude.domain.model.BloodPressureResult

/**
 * Parser e Classificador de Pressão Arterial
 * 
 * Baseado estritamente nas Diretrizes Brasileiras de Hipertensão Arterial da
 * SBC (Sociedade Brasileira de Cardiologia) e OMS (Organização Mundial da Saúde).
 * 
 * REGRAS DE CLASSIFICAÇÃO DA SBC:
 * - Hipotensão: Sistólica < 90 mmHg ou Diastólica < 60 mmHg
 * - Observação / Diferencial Amplo: Sistólica normal (≥ 120 mmHg) com Diastólica no limiar inferior (≤ 60 mmHg, ex: 12/6 → 120/60 mmHg)
 * - Ótima: Sistólica < 120 mmHg e Diastólica < 80 mmHg (ex: 11/7 → 110/70 mmHg, 119/79 mmHg)
 * - Normal: Sistólica 120-129 mmHg e/ou Diastólica 80-84 mmHg (ex: 12/8 → 120/80 mmHg)
 * - Pré-Hipertensão: Sistólica 130-139 mmHg e/ou Diastólica 85-89 mmHg
 * - Hipertensão Estágio 1: Sistólica 140-159 mmHg e/ou Diastólica 90-99 mmHg
 * - Hipertensão Estágio 2: Sistólica 160-179 mmHg e/ou Diastólica 100-109 mmHg
 * - Hipertensão Estágio 3 (Atenção Imediata): Sistólica ≥ 180 mmHg e/ou Diastólica ≥ 110 mmHg
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
     * Classifica a pressão arterial segundo a SBC / OMS com análise de cruzamento, assimetria e transição.
     */
    fun classify(systolic: Int, diastolic: Int): BloodPressureResult {
        // Referência para medidas de consultório em adultos; uma medida isolada não é diagnóstico.
        val category = when {
            systolic >= 180 || diastolic >= 110 -> BloodPressureCategory.STAGE_3_HYPERTENSION
            systolic >= 160 || diastolic >= 100 -> BloodPressureCategory.STAGE_2_HYPERTENSION
            systolic >= 140 || diastolic >= 90 -> BloodPressureCategory.STAGE_1_HYPERTENSION
            systolic >= 120 || diastolic >= 80 -> BloodPressureCategory.PREHYPERTENSION
            systolic < 90 || diastolic < 60 -> BloodPressureCategory.HYPOTENSION
            else -> BloodPressureCategory.OPTIMAL
        }
        return BloodPressureResult(
            systolic = systolic,
            diastolic = diastolic,
            category = category,
            isNormal = category == BloodPressureCategory.OPTIMAL,
            requiresMedicalAttention = category in listOf(
                BloodPressureCategory.STAGE_1_HYPERTENSION,
                BloodPressureCategory.STAGE_2_HYPERTENSION,
                BloodPressureCategory.STAGE_3_HYPERTENSION
            ),
            explanatoryDetail = "Registro de $systolic/$diastolic mmHg. Referência para adultos em consultório; uma medição isolada não estabelece diagnóstico. Converse com um profissional de saúde sobre seus registros.",
            institution = "Diretriz Brasileira de Hipertensão Arterial 2025"
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
