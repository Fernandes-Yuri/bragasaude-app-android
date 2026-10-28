package br.com.bragasaude.domain.model

/** Faixas de referência para registros de pressão; não constituem diagnóstico. */
enum class BloodPressureCategory(
    val displayName: String,
    val description: String,
    val recommendation: String,
    val institution: String = "Diretriz Brasileira de Hipertensão Arterial 2025",
    val colorHex: Long = 0xFF16A34A // Default Green
) {
    HYPOTENSION(
        displayName = "Pressão Abaixo da Faixa (Hipotensão)",
        description = "Sistólica < 90 mmHg ou Diastólica < 60 mmHg",
        recommendation = "Valor abaixo da faixa habitual. Converse com um profissional de saúde, especialmente se sentir sintomas.",
        colorHex = 0xFF2563EB // Blue
    ),
    OPTIMAL(
        displayName = "Faixa de referência normal",
        description = "Sistólica < 120 mmHg e Diastólica < 80 mmHg",
        recommendation = "Acompanhe seus registros com um profissional de saúde.",
        colorHex = 0xFF0D9488 // Teal Primary
    ),
    PREHYPERTENSION(
        displayName = "Faixa de pré-hipertensão",
        description = "Sistólica 120-139 mmHg e/ou Diastólica 80-89 mmHg",
        recommendation = "Faixa de atenção preventiva da SBC. Hábitos saudáveis, redução de sódio e monitoramento ajudam a manter o equilíbrio.",
        colorHex = 0xFFD97706 // Amber
    ),
    STAGE_1_HYPERTENSION(
        displayName = "Faixa de pressão elevada: estágio 1",
        description = "Sistólica 140-159 mmHg e/ou Diastólica 90-99 mmHg",
        recommendation = "Valores acima da faixa recomendada pela SBC. Monitore regularmente e converse com seu médico para acompanhamento.",
        colorHex = 0xFFEA580C // Orange
    ),
    STAGE_2_HYPERTENSION(
        displayName = "Faixa de pressão elevada: estágio 2",
        description = "Sistólica 160-179 mmHg e/ou Diastólica 100-109 mmHg",
        recommendation = "Valores significativamente elevados segundo a SBC. Recomenda-se orientação e acompanhamento médico.",
        colorHex = 0xFFE11D48 // Rose
    ),
    STAGE_3_HYPERTENSION(
        displayName = "Faixa de pressão elevada: estágio 3",
        description = "Sistólica ≥ 180 mmHg e/ou Diastólica ≥ 110 mmHg",
        recommendation = "Valores muito elevados. Se houver sintomas como dor de cabeça intensa ou visão turva, procure atendimento médico imediatamente.",
        colorHex = 0xFFDC2626 // Red
    )
}

/**
 * Resultado completo da classificação de pressão arterial
 */
data class BloodPressureResult(
    val systolic: Int,
    val diastolic: Int,
    val category: BloodPressureCategory,
    val isNormal: Boolean,
    val requiresMedicalAttention: Boolean,
    val explanatoryDetail: String,
    val institution: String = "Diretriz Brasileira de Hipertensão Arterial 2025"
)
