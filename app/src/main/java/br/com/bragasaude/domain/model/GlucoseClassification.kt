package br.com.bragasaude.domain.model

/**
 * Contexto da medição de glicose
 * Define as faixas de referência apropriadas para cada situação clínica (SBD / ADA 2026).
 */
enum class GlucoseContext(
    val displayName: String,
    val description: String
) {
    FASTING(
        displayName = "Jejum",
        description = "Medição após 8-12 horas sem ingestão calórica"
    ),
    POST_PRANDIAL(
        displayName = "Pós-prandial",
        description = "Medição 1-2 horas após início da refeição"
    ),
    CGM_CONTINUOUS(
        displayName = "Monitoramento Contínuo (CGM)",
        description = "Sensor contínuo de glicose intersticial"
    ),
    RANDOM(
        displayName = "Aleatória / Casual",
        description = "Medição casual sem relação direta com refeições"
    )
}

/**
 * Classificação de Glicose baseada nas diretrizes da SBD (Sociedade Brasileira de Diabetes) e ADA 2026.
 * 
 * NOTA DE COMPLIANCE: Nunca apresentar o resultado como diagnóstico médico.
 * Sempre referir como faixas de referência e orientações de acompanhamento, citando a instituição.
 */
enum class GlucoseCategory(
    val displayName: String,
    val description: String,
    val recommendation: String,
    val institution: String = "SBD (Sociedade Brasileira de Diabetes) / ADA",
    val colorHex: Long = 0xFF16A34A
) {
    HYPOGLYCEMIA_SEVERE(
        displayName = "Hipoglicemia Severa (< 54 mg/dL)",
        description = "Glicose muito abaixo da faixa segura",
        recommendation = "Consuma carboidrato simples imediatamente e busque auxílio de saúde com urgência.",
        colorHex = 0xFFDC2626 // Red
    ),
    HYPOGLYCEMIA(
        displayName = "Abaixo da Referência (Hipoglicemia)",
        description = "Glicose abaixo de 70 mg/dL",
        recommendation = "Consuma 15g de carboidratos simples (ex: suco ou água com açúcar) e repita a medição em 15 minutos (Diretriz SBD).",
        colorHex = 0xFF2563EB // Blue
    ),
    NORMAL(
        displayName = "Dentro da Faixa de Referência",
        description = "Glicose dentro da faixa esperada para este contexto",
        recommendation = "Excelente! Seus valores estão dentro da faixa recomendada pela Sociedade Brasileira de Diabetes (SBD).",
        colorHex = 0xFF0D9488 // Teal
    ),
    PREDIABETES(
        displayName = "Faixa Moderadamente Elevada",
        description = "Glicemia acima da faixa ideal (ex: 100-125 mg/dL em jejum)",
        recommendation = "Atenção preventiva: converse com seu profissional de saúde e mantenha uma alimentação balanceada.",
        colorHex = 0xFFD97706 // Amber
    ),
    DIABETES(
        displayName = "Faixa Elevada (≥ 126 mg/dL Jejum / ≥ 200 mg/dL Pós)",
        description = "Valor substancialmente acima da faixa de referência",
        recommendation = "Recomenda-se orientação e avaliação médica com profissional de saúde (Diretriz SBD).",
        colorHex = 0xFFEA580C // Orange
    ),
    HYPERGLYCEMIA_SEVERE(
        displayName = "Atenção Imediata (≥ 300 mg/dL)",
        description = "Glicose em nível muito elevado",
        recommendation = "Mantenha boa hidratação e busque avaliação médica imediatamente.",
        colorHex = 0xFFDC2626 // Red
    )
}

/**
 * Faixas de referência para glicose baseadas no contexto
 * Valores em mg/dL
 */
data class GlucoseReferenceRanges(
    val context: GlucoseContext,
    val normalMin: Int,
    val normalMax: Int,
    val prediabetesMin: Int,
    val prediabetesMax: Int,
    val diabetesMin: Int,
    val hypoglycemiaThreshold: Int,
    val severeHypoglycemiaThreshold: Int,
    val severeHyperglycemiaThreshold: Int
)

/**
 * Resultado da classificação de glicose
 */
data class GlucoseResult(
    val value: Int,
    val context: GlucoseContext,
    val category: GlucoseCategory,
    val referenceRange: GlucoseReferenceRanges,
    val isNormal: Boolean,
    val requiresMedicalAttention: Boolean,
    val explanatoryDetail: String,
    val institution: String = "Sociedade Brasileira de Diabetes (SBD) / ADA"
)
