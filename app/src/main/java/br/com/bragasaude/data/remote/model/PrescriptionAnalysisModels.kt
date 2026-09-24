package br.com.bragasaude.data.remote.model

data class AnalyzedMedicationItemDto(
    val name: String = "",
    val nameDivergent: Boolean = false,
    val dosage: String = "",
    val dosageDivergent: Boolean = false,
    val dosageMg: Double? = null,
    val frequency: String = "",
    val frequencyDivergent: Boolean = false,
    val suggestedTimes: List<String> = emptyList(),
    val eanBarcode: String? = null,
    val activePrinciple: String? = null,
    val confidenceScore: Double = 0.0,
    val requiresHumanFill: Boolean = false,
    val divergenceReason: String? = null
)

data class PrescriptionAnalysisResponseDto(
    val status: String,
    val prescriptionImageUrl: String? = null,
    val medications: List<AnalyzedMedicationItemDto> = emptyList(),
    val rawOcrSnippet: String? = null
)
