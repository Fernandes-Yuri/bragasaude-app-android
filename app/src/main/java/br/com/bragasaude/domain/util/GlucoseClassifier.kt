package br.com.bragasaude.domain.util

import br.com.bragasaude.domain.model.GlucoseCategory
import br.com.bragasaude.domain.model.GlucoseContext
import br.com.bragasaude.domain.model.GlucoseReferenceRanges
import br.com.bragasaude.domain.model.GlucoseResult

/**
 * Classificador de Glicose com Contexto Metodológico
 * 
 * Baseado nas diretrizes da SBD (Sociedade Brasileira de Diabetes) e ADA 2026.
 * Considera os contextos de medição para determinar a faixa de referência aplicável.
 */
object GlucoseClassifier {
    
    /**
     * Faixas de referência para glicose em Jejum (8-12h) - SBD / ADA 2026.
     * Faixa de Referência esperada: 70 a 99 mg/dL.
     */
    private val FASTING_RANGES = GlucoseReferenceRanges(
        context = GlucoseContext.FASTING,
        normalMin = 70,
        normalMax = 99,
        prediabetesMin = 100,
        prediabetesMax = 125,
        diabetesMin = 126,
        hypoglycemiaThreshold = 70,
        severeHypoglycemiaThreshold = 54,
        severeHyperglycemiaThreshold = 300
    )
    
    /**
     * Faixas de referência para glicose Pós-prandial (1-2 horas após refeição) - SBD / ADA 2026.
     * Faixa esperada: até 140 mg/dL.
     */
    private val POST_PRANDIAL_RANGES = GlucoseReferenceRanges(
        context = GlucoseContext.POST_PRANDIAL,
        normalMin = 70,
        normalMax = 140,
        prediabetesMin = 141,
        prediabetesMax = 199,
        diabetesMin = 200,
        hypoglycemiaThreshold = 70,
        severeHypoglycemiaThreshold = 54,
        severeHyperglycemiaThreshold = 300
    )
    
    /**
     * Faixas de referência para Monitoramento Contínuo de Glicose (CGM) - SBD / ADA 2026.
     * Time in Range (TIR): 70 a 180 mg/dL.
     */
    private val CGM_RANGES = GlucoseReferenceRanges(
        context = GlucoseContext.CGM_CONTINUOUS,
        normalMin = 70,
        normalMax = 180,
        prediabetesMin = 181,
        prediabetesMax = 250,
        diabetesMin = 251,
        hypoglycemiaThreshold = 70,
        severeHypoglycemiaThreshold = 54,
        severeHyperglycemiaThreshold = 300
    )
    
    /**
     * Faixas de referência para medição aleatória / casual.
     */
    private val RANDOM_RANGES = GlucoseReferenceRanges(
        context = GlucoseContext.RANDOM,
        normalMin = 70,
        normalMax = 140,
        prediabetesMin = 141,
        prediabetesMax = 199,
        diabetesMin = 200,
        hypoglycemiaThreshold = 70,
        severeHypoglycemiaThreshold = 54,
        severeHyperglycemiaThreshold = 300
    )
    
    /**
     * Obtém as faixas de referência apropriadas para o contexto selecionado.
     */
    fun getReferenceRanges(context: GlucoseContext): GlucoseReferenceRanges {
        return when (context) {
            GlucoseContext.FASTING -> FASTING_RANGES
            GlucoseContext.POST_PRANDIAL -> POST_PRANDIAL_RANGES
            GlucoseContext.CGM_CONTINUOUS -> CGM_RANGES
            GlucoseContext.RANDOM -> RANDOM_RANGES
        }
    }
    
    /**
     * Classifica o valor de glicose baseado no contexto da medição e nas diretrizes SBD / ADA 2026.
     * 
     * @param value Valor de glicose em mg/dL
     * @param context Contexto da medição
     */
    fun classify(value: Int, context: GlucoseContext): GlucoseResult {
        val ranges = getReferenceRanges(context)
        
        val category: GlucoseCategory
        val detail: String

        when {
            // Hipoglicemia Grave
            value < ranges.severeHypoglycemiaThreshold -> {
                category = GlucoseCategory.HYPOGLYCEMIA_SEVERE
                detail = "Alerta: $value mg/dL é um valor criticamente baixo (< 54 mg/dL). Consuma carboidratos simples imediatamente (Diretriz SBD)."
            }
            
            // Hipoglicemia
            value < ranges.hypoglycemiaThreshold -> {
                category = GlucoseCategory.HYPOGLYCEMIA
                detail = "Atenção: $value mg/dL está abaixo da faixa de referência (< 70 mg/dL). Recomenda-se ingestão de 15g de carboidratos rápidos (Diretriz SBD)."
            }
            
            // Normal (dentro da faixa alvo)
            value in ranges.normalMin..ranges.normalMax -> {
                category = GlucoseCategory.NORMAL
                detail = "Excelente! $value mg/dL está dentro da faixa esperada para ${context.displayName.lowercase()} (${ranges.normalMin}-${ranges.normalMax} mg/dL, Diretriz SBD/ADA)."
            }
            
            // Hiperglicemia de emergência
            value >= ranges.severeHyperglycemiaThreshold -> {
                category = GlucoseCategory.HYPERGLYCEMIA_SEVERE
                detail = "Alerta Imediato: $value mg/dL é um valor muito elevado (≥ 300 mg/dL). Busque avaliação médica com urgência."
            }
            
            // Faixa Substancialmente Elevada
            value >= ranges.diabetesMin -> {
                category = GlucoseCategory.DIABETES
                detail = "Valores de $value mg/dL em ${context.displayName.lowercase()} estão acima da faixa de referência (≥ ${ranges.diabetesMin} mg/dL segundo a SBD). Recomenda-se orientação médica."
            }
            
            // Faixa Moderadamente Elevada
            value in ranges.prediabetesMin..ranges.prediabetesMax -> {
                category = GlucoseCategory.PREDIABETES
                detail = "Valores de $value mg/dL em ${context.displayName.lowercase()} estão moderadamente elevados (${ranges.prediabetesMin}-${ranges.prediabetesMax} mg/dL segundo a SBD). Monitore seus hábitos alimentares."
            }
            
            // Caso intermediário
            else -> {
                category = GlucoseCategory.NORMAL
                detail = "Valor de $value mg/dL em ${context.displayName.lowercase()} dentro dos limites de observação (Diretriz SBD/ADA)."
            }
        }
        
        return GlucoseResult(
            value = value,
            context = context,
            category = category,
            referenceRange = ranges,
            isNormal = category == GlucoseCategory.NORMAL,
            requiresMedicalAttention = category in listOf(
                GlucoseCategory.HYPOGLYCEMIA,
                GlucoseCategory.HYPOGLYCEMIA_SEVERE,
                GlucoseCategory.DIABETES,
                GlucoseCategory.HYPERGLYCEMIA_SEVERE
            ),
            explanatoryDetail = detail,
            institution = "Sociedade Brasileira de Diabetes (SBD) / ADA"
        )
    }
    
    fun classifyFasting(value: Int): GlucoseResult = classify(value, GlucoseContext.FASTING)
    fun classifyPostPrandial(value: Int): GlucoseResult = classify(value, GlucoseContext.POST_PRANDIAL)
    fun classifyCGM(value: Int): GlucoseResult = classify(value, GlucoseContext.CGM_CONTINUOUS)
    
    /**
     * Interpreta o tipo de glicose a partir de string
     */
    fun parseGlucoseType(glucoseType: String?): GlucoseContext {
        return when (glucoseType?.lowercase()) {
            "jejum", "fasting", "fast" -> GlucoseContext.FASTING
            "pos-prandial", "postprandial", "pos", "after_meal" -> GlucoseContext.POST_PRANDIAL
            "cgm", "continuous", "sensor" -> GlucoseContext.CGM_CONTINUOUS
            "aleatoria", "random", "casual" -> GlucoseContext.RANDOM
            else -> GlucoseContext.FASTING
        }
    }
    
    fun classifyWithType(value: Int, glucoseType: String?): GlucoseResult {
        val context = parseGlucoseType(glucoseType)
        return classify(value, context)
    }
}
