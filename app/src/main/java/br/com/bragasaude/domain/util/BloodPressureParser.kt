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
        val sysLevel = getSystolicLevel(systolic)
        val diaLevel = getDiastolicLevel(diastolic)
        
        val category: BloodPressureCategory
        val detail: String

        when {
            // 1. Estágio 3 (Sistólica >= 180 ou Diastólica >= 110)
            sysLevel == 6 || diaLevel == 6 -> {
                category = BloodPressureCategory.STAGE_3_HYPERTENSION
                detail = "Atenção Imediata: $systolic/$diastolic mmHg atinge a faixa de Hipertensão Estágio 3 da SBC (≥ 180 ou ≥ 110 mmHg). Recomenda-se atendimento médico."
            }

            // 2. Transição Estágio 2 para Estágio 3 (ex: 175/108 ou 160/110).
            // AUD-AN06: este ramo era MORTO — a condição interna testava
            // `sysLevel == 6 || diaLevel == 6`, mas a regra 1 acima JÁ captura
            // exatamente esses níveis. STAGE_2_TO_3_TRANSITION nunca era
            // atribuído. A transição real é Estágio 2 puro (nível 5) em um
            // parâmetro e Estágio 3 (nível 6) no OUTRO — ex: 170/110.
            (sysLevel == 5 && diaLevel == 6) || (sysLevel == 6 && diaLevel == 5) -> {
                category = BloodPressureCategory.STAGE_2_TO_3_TRANSITION
                detail = "Valores de $systolic/$diastolic mmHg estão oscilando na transição entre Estágio 2 e Estágio 3 segundo a SBC."
            }

            // 3. Transição Estágio 1 para Estágio 2 (ex: 140/100, 160/92)
            (sysLevel == 4 && diaLevel == 5) || (sysLevel == 5 && diaLevel == 4) -> {
                category = BloodPressureCategory.STAGE_1_TO_2_TRANSITION
                val higherParam = if (diaLevel == 5) "Diastólica ($diastolic mmHg em Estágio 2)" else "Sistólica ($systolic mmHg em Estágio 2)"
                detail = "Faixa de transição entre Estágio 1 e Estágio 2: $higherParam pela Diretriz da SBC."
            }

            // 4. Estágio 2 puro ou por um dos parâmetros
            sysLevel == 5 || diaLevel == 5 -> {
                category = BloodPressureCategory.STAGE_2_HYPERTENSION
                val reason = if (sysLevel == 5 && diaLevel < 5) "Sistólica em Estágio 2 ($systolic mmHg)"
                else if (diaLevel == 5 && sysLevel < 5) "Diastólica em Estágio 2 ($diastolic mmHg)"
                else "Ambos os valores em Estágio 2"
                detail = "Classificada em Hipertensão Estágio 2 da SBC por critério de $reason ($systolic/$diastolic mmHg)."
            }

            // 5. Estágio 1 (ex: 140/90, ou 120/90 - sistólica normal mas diastólica em estágio 1)
            sysLevel == 4 || diaLevel == 4 -> {
                category = BloodPressureCategory.STAGE_1_HYPERTENSION
                val reason = if (sysLevel == 4 && diaLevel < 4) "Sistólica em Estágio 1 ($systolic mmHg)"
                else if (diaLevel == 4 && sysLevel < 4) "Sistólica normal ($systolic mmHg), porém Diastólica em Estágio 1 ($diastolic mmHg)"
                else "Valores dentro do Estágio 1 da SBC (140-159 / 90-99 mmHg)"
                detail = "$reason ($systolic/$diastolic mmHg - Diretriz SBC)."
            }

            // 6. Pré-Hipertensão (ex: 130-139 ou 85-89)
            sysLevel == 3 || diaLevel == 3 -> {
                category = BloodPressureCategory.PREHYPERTENSION
                detail = "Valores de $systolic/$diastolic mmHg enquadram-se em Pré-Hipertensão segundo a SBC (130-139 / 85-89 mmHg)."
            }

            // 7. Assimetria / Observação preventiva (ex: 12/6 -> 120/60 mmHg onde sistólica está em 120 e diastólica no limiar de 60 mmHg)
            (systolic in 118..130 && diastolic in 55..62) || (sysLevel in 2..3 && diastolic <= 60) -> {
                category = BloodPressureCategory.ASYMMETRIC_OBSERVATION
                detail = "Valores de $systolic/$diastolic mmHg apresentam Sistólica normal ($systolic mmHg) com Diastólica no limiar inferior ($diastolic mmHg) — amplitude diferencial em observação segundo a SBC/OMS."
            }

            // 8. Hipotensão (Ambos ou um baixo sem o outro estar alto)
            (sysLevel == 0 || diaLevel == 0) && sysLevel <= 1 && diaLevel <= 1 -> {
                category = BloodPressureCategory.HYPOTENSION
                detail = "Valores de $systolic/$diastolic mmHg estão abaixo da faixa geral de referência (< 90/60 mmHg, Diretriz SBC/OMS)."
            }

            // 9. Normal (ex: 120-129 e/ou 80-84, como o 120/80)
            sysLevel == 2 || diaLevel == 2 -> {
                category = BloodPressureCategory.NORMAL
                detail = "Valores de $systolic/$diastolic mmHg estão dentro da faixa Normal segundo a SBC (120-129 / 80-84 mmHg)."
            }

            // 10. Ótima (ex: 110/70, 119/79)
            else -> {
                category = BloodPressureCategory.OPTIMAL
                detail = "Excelente! $systolic/$diastolic mmHg está dentro da faixa de Pressão Ótima (< 120/80 mmHg) segundo a Sociedade Brasileira de Cardiologia (SBC)."
            }
        }
        
        return BloodPressureResult(
            systolic = systolic,
            diastolic = diastolic,
            category = category,
            isNormal = category == BloodPressureCategory.OPTIMAL || category == BloodPressureCategory.NORMAL,
            requiresMedicalAttention = category in listOf(
                BloodPressureCategory.STAGE_1_HYPERTENSION,
                BloodPressureCategory.STAGE_1_TO_2_TRANSITION,
                BloodPressureCategory.STAGE_2_HYPERTENSION,
                BloodPressureCategory.STAGE_2_TO_3_TRANSITION,
                BloodPressureCategory.STAGE_3_HYPERTENSION
            ),
            explanatoryDetail = detail,
            institution = "Sociedade Brasileira de Cardiologia (SBC) / OMS"
        )
    }

    private fun getSystolicLevel(sys: Int): Int {
        return when {
            sys < 90 -> 0      // Hipotensão
            sys < 120 -> 1     // Ótima
            sys in 120..129 -> 2 // Normal
            sys in 130..139 -> 3 // Pré-hipertensão
            sys in 140..159 -> 4 // Estágio 1
            sys in 160..179 -> 5 // Estágio 2
            else -> 6          // Estágio 3 (>= 180)
        }
    }

    private fun getDiastolicLevel(dia: Int): Int {
        return when {
            dia < 60 -> 0      // Hipotensão
            dia < 80 -> 1      // Ótima
            dia in 80..84 -> 2  // Normal
            dia in 85..89 -> 3  // Pré-hipertensão
            dia in 90..99 -> 4  // Estágio 1
            dia in 100..109 -> 5 // Estágio 2
            else -> 6          // Estágio 3 (>= 110)
        }
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
