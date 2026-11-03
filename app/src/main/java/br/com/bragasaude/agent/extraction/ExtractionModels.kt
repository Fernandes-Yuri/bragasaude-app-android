package br.com.bragasaude.agent.extraction

import kotlinx.serialization.Serializable

/**
 * Coordenadas retangulares normalizadas (0.0f a 1.0f) relativas à largura e altura da página/imagem.
 */
@Serializable
data class NormalizedBoundingBox(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {
    init {
        require(left in 0.0f..1.0f) { "left deve estar entre 0.0 e 1.0 (recebido: $left)" }
        require(top in 0.0f..1.0f) { "top deve estar entre 0.0 e 1.0 (recebido: $top)" }
        require(right in left..1.0f) { "right deve ser >= left e <= 1.0 (recebido: $right, left: $left)" }
        require(bottom in top..1.0f) { "bottom deve ser >= top e <= 1.0 (recebido: $bottom, top: $top)" }
    }

    val width: Float get() = right - left
    val height: Float get() = bottom - top
}

/**
 * Bloco ou linha de texto extraído de um documento com sua pontuação de confiança e posição.
 */
@Serializable
data class ExtractedBlock(
    val text: String,
    val confidence: Float,
    val box: NormalizedBoundingBox? = null,
    val lineIndex: Int = 0
) {
    /**
     * Verifica se este bloco possui confiança abaixo do limiar (necessitando de desempate cirúrgico).
     */
    fun isAmbiguous(threshold: Float = DEFAULT_AMBIGUITY_THRESHOLD): Boolean =
        confidence < threshold

    companion object {
        const val DEFAULT_AMBIGUITY_THRESHOLD = 0.75f
    }
}

/**
 * Página de documento extraída.
 */
@Serializable
data class ExtractedPage(
    val pageIndex: Int,
    val text: String,
    val blocks: List<ExtractedBlock>,
    val isDigital: Boolean,
    val averageConfidence: Float = if (blocks.isEmpty()) 1.0f else blocks.map { it.confidence }.average().toFloat()
) {
    /**
     * Retorna os blocos com confiança inferior ao limiar.
     */
    fun getAmbiguousBlocks(threshold: Float = ExtractedBlock.DEFAULT_AMBIGUITY_THRESHOLD): List<ExtractedBlock> =
        blocks.filter { it.isAmbiguous(threshold) }

    /**
     * Indica se há blocos ambíguos nesta página.
     */
    fun hasAmbiguities(threshold: Float = ExtractedBlock.DEFAULT_AMBIGUITY_THRESHOLD): Boolean =
        blocks.any { it.isAmbiguous(threshold) }
}

/**
 * Documento completo processado pela cascata de extração.
 */
@Serializable
data class ExtractedDocument(
    val pages: List<ExtractedPage>,
    val fullText: String,
    val averageConfidence: Float = if (pages.isEmpty()) 1.0f else pages.map { it.averageConfidence }.average().toFloat(),
    val isFullyDigital: Boolean = pages.all { it.isDigital }
) {
    /**
     * Retorna todos os blocos ambíguos do documento indexados pelo número da página.
     */
    fun getAllAmbiguousBlocks(threshold: Float = ExtractedBlock.DEFAULT_AMBIGUITY_THRESHOLD): List<Pair<Int, ExtractedBlock>> =
        pages.flatMap { page ->
            page.getAmbiguousBlocks(threshold).map { block -> page.pageIndex to block }
        }

    val totalPages: Int get() = pages.size
}

/**
 * Resultado do recorte cirúrgico de um fragmento do documento.
 */
@Serializable
data class CropSnippetResult(
    val pageIndex: Int,
    val mimeType: String = "image/jpeg",
    val sizeBytes: Int,
    val imageBase64: String,
    val width: Int,
    val height: Int
)
