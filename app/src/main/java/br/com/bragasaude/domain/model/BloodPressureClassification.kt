package br.com.bragasaude.domain.model

/**
 * Classificação de Pressão Arterial baseada nas Diretrizes Brasileiras de Hipertensão Arterial da
 * SBC (Sociedade Brasileira de Cardiologia) e OMS (Organização Mundial da Saúde).
 * 
 * NOTA DE COMPLIANCE: Os textos descrevem faixas de referência e orientações de autocuidado e monitoramento,
 * sempre indicando consulta a um profissional de saúde e citando a instituição de referência.
 */
enum class BloodPressureCategory(
    val displayName: String,
    val description: String,
    val recommendation: String,
    val institution: String = "SBC (Sociedade Brasileira de Cardiologia) / OMS",
    val colorHex: Long = 0xFF16A34A // Default Green
) {
    HYPOTENSION(
        displayName = "Pressão Abaixo da Faixa (Hipotensão)",
        description = "Sistólica < 90 mmHg ou Diastólica < 60 mmHg",
        recommendation = "Valores abaixo do habitual segundo a SBC/OMS. Mantenha boa hidratação e descanse se sentir tontura.",
        colorHex = 0xFF2563EB // Blue
    ),
    ASYMMETRIC_OBSERVATION(
        displayName = "Pressão em Observação (Diastólica Limítrofe / Diferencial)",
        description = "Sistólica dentro do padrão (≥ 120 mmHg) com Diastólica no limiar inferior (≤ 60 mmHg)",
        recommendation = "Atenção: amplitude aumentada entre os valores segundo a SBC/OMS. Mantenha boa hidratação e relate ao seu médico se sentir tontura ao se levantar.",
        colorHex = 0xFFD97706 // Amber
    ),
    OPTIMAL(
        displayName = "Pressão Ótima",
        description = "Sistólica < 120 mmHg e Diastólica < 80 mmHg",
        recommendation = "Excelente! Seus valores estão na faixa ótima de referência da Sociedade Brasileira de Cardiologia (SBC).",
        colorHex = 0xFF0D9488 // Teal Primary
    ),
    NORMAL(
        displayName = "Pressão Normal",
        description = "Sistólica 120-129 mmHg e/ou Diastólica 80-84 mmHg",
        recommendation = "Valores dentro da faixa normal de referência da SBC. Continue com hábitos saudáveis de vida.",
        colorHex = 0xFF16A34A // Green
    ),
    PREHYPERTENSION(
        displayName = "Pré-Hipertensão",
        description = "Sistólica 130-139 mmHg e/ou Diastólica 85-89 mmHg",
        recommendation = "Faixa de atenção preventiva da SBC. Hábitos saudáveis, redução de sódio e monitoramento ajudam a manter o equilíbrio.",
        colorHex = 0xFFD97706 // Amber
    ),
    STAGE_1_HYPERTENSION(
        displayName = "Hipertensão Estágio 1",
        description = "Sistólica 140-159 mmHg e/ou Diastólica 90-99 mmHg",
        recommendation = "Valores acima da faixa recomendada pela SBC. Monitore regularmente e converse com seu médico para acompanhamento.",
        colorHex = 0xFFEA580C // Orange
    ),
    STAGE_1_TO_2_TRANSITION(
        displayName = "Faixa entre Estágio 1 e Estágio 2",
        description = "Valores em transição/oscilação entre Estágio 1 e Estágio 2",
        recommendation = "Atenção: seus parâmetros estão na fronteira entre os Estágios 1 e 2 da SBC. Recomenda-se acompanhamento com um profissional de saúde.",
        colorHex = 0xFFE11D48 // Rose
    ),
    STAGE_2_HYPERTENSION(
        displayName = "Hipertensão Estágio 2",
        description = "Sistólica 160-179 mmHg e/ou Diastólica 100-109 mmHg",
        recommendation = "Valores significativamente elevados segundo a SBC. Recomenda-se orientação e acompanhamento médico.",
        colorHex = 0xFFE11D48 // Rose
    ),
    STAGE_2_TO_3_TRANSITION(
        displayName = "Faixa entre Estágio 2 e Estágio 3",
        description = "Valores em transição/oscilação entre Estágio 2 e Estágio 3",
        recommendation = "Alerta: valores no limite superior do Estágio 2 para o Estágio 3 da SBC. Procure avaliação de um profissional de saúde.",
        colorHex = 0xFFDC2626 // Red
    ),
    STAGE_3_HYPERTENSION(
        displayName = "Hipertensão Estágio 3 (Atenção Imediata)",
        description = "Sistólica ≥ 180 mmHg e/ou Diastólica ≥ 110 mmHg",
        recommendation = "Valores muito elevados segundo a SBC/OMS. Se houver sintomas como dor de cabeça intensa ou visão turva, procure atendimento médico imediatamente.",
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
    val institution: String = "Sociedade Brasileira de Cardiologia (SBC) / OMS"
)
