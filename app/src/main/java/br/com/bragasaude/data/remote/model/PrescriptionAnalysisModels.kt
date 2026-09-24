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
    val divergenceReason: String? = null,
    val sourcePages: List<Int> = emptyList() // Páginas de origem (ex: [1, 2] se veio em 1ª e 2ª via)
)

data class PrescriptionAnalysisResponseDto(
    val status: String,
    val prescriptionImageUrl: String? = null,
    val totalPagesAnalyzed: Int = 1,
    val duplicatesMergedCount: Int = 0, // Cópias/vias repetidas da farmácia que foram fundidas
    val medications: List<AnalyzedMedicationItemDto> = emptyList(),
    val rawOcrSnippet: String? = null
)

data class MedicationBatchItemCreateDto(
    val name: String,
    val dosageMg: Double? = null,
    val scheduleTimes: List<String>, // ["08:00"]
    val totalUnits: Int = 30,
    val eanBarcode: String? = null,
    val confirmedWithPrescription: Boolean = true,
    val photoReferenceUrl: String? = null
)

data class MedicationBatchCreateRequest(
    val medications: List<MedicationBatchItemCreateDto>
)

data class MedicationBatchResponseDto(
    val status: String,
    val patientId: String,
    val createdCount: Int
)
