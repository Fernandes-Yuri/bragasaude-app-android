package br.com.bragasaude.data.util

import android.graphics.Bitmap

/**
 * Contrato do Gatekeeper de Qualidade Visual On-Device.
 * Conforme Seção 1 do Caderno de Contratos (08_CADERNO_DE_CONTRATOS_EXAMES_E_DOSSIE.md) e Decisão D49.
 */
interface IImageQualityGatekeeper {
    /**
     * Avalia a nitidez do laudo fotográfico em escala de cinza utilizando a Variância Laplaciana.
     * Execução síncrona/on-device estritamente < 100ms.
     */
    fun evaluateImageQuality(bitmap: Bitmap): QualityEvaluationResult
}

sealed interface QualityEvaluationResult {
    /**
     * Imagem aprovada. Atende aos requisitos mínimos de legibilidade médica.
     * @param sharpnessScore Valor numérico da variância (mínimo de 85.0).
     */
    data class Approved(val sharpnessScore: Double) : QualityEvaluationResult

    /**
     * Imagem reprovada. O botão de envio deve permanecer DESABILITADO.
     * @param reason Código enum do motivo da rejeição.
     * @param userMessage Mensagem amigável explicativa para o paciente.
     * @param currentScore Pontuação calculada (útil para telemetria/debug).
     */
    data class Rejected(
        val reason: RejectionReason,
        val userMessage: String,
        val currentScore: Double
    ) : QualityEvaluationResult
}

enum class RejectionReason {
    BLUR_DETECTED,       // Foto borrada, tremida ou fora de foco
    LOW_RESOLUTION,      // Resolução inferior a 1080x1080
    EXTREME_EXPOSURE     // Superexposta (muito clara) ou subexposta (muito escura)
}

object ImageQualityGatekeeper : IImageQualityGatekeeper {

    const val MIN_RESOLUTION_WIDTH = 1080
    const val MIN_RESOLUTION_HEIGHT = 1080
    const val MIN_SHARPNESS_THRESHOLD = 85.0

    private const val MIN_ACCEPTABLE_LUMINANCE = 20.0
    private const val MAX_ACCEPTABLE_LUMINANCE = 245.0

    override fun evaluateImageQuality(bitmap: Bitmap): QualityEvaluationResult {
        val width = bitmap.width
        val height = bitmap.height

        if (width < MIN_RESOLUTION_WIDTH || height < MIN_RESOLUTION_HEIGHT) {
            return QualityEvaluationResult.Rejected(
                reason = RejectionReason.LOW_RESOLUTION,
                userMessage = "Resolução insuficiente (${width}x${height}). Fotografe o documento com resolução mínima de ${MIN_RESOLUTION_WIDTH}x${MIN_RESOLUTION_HEIGHT}.",
                currentScore = 0.0
            )
        }

        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        return evaluatePixels(pixels, width, height)
    }

    /**
     * Avaliação pura sobre buffer de pixels ARGB_8888.
     * Permite testes unitários na JVM pura sem depender de mocks nativos do Android Bitmap.
     */
    fun evaluatePixels(pixels: IntArray, width: Int, height: Int): QualityEvaluationResult {
        if (width < MIN_RESOLUTION_WIDTH || height < MIN_RESOLUTION_HEIGHT) {
            return QualityEvaluationResult.Rejected(
                reason = RejectionReason.LOW_RESOLUTION,
                userMessage = "Resolução insuficiente (${width}x${height}). Fotografe o documento com resolução mínima de ${MIN_RESOLUTION_WIDTH}x${MIN_RESOLUTION_HEIGHT}.",
                currentScore = 0.0
            )
        }

        // Converte para escala de cinza (luminância ITU-R BT.601)
        val gray = FloatArray(width * height)
        var luminanceSum = 0.0

        for (i in pixels.indices) {
            val p = pixels[i]
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            val lum = 0.299f * r + 0.587f * g + 0.114f * b
            gray[i] = lum
            luminanceSum += lum
        }

        val meanLuminance = luminanceSum / pixels.size
        if (meanLuminance < MIN_ACCEPTABLE_LUMINANCE || meanLuminance > MAX_ACCEPTABLE_LUMINANCE) {
            return QualityEvaluationResult.Rejected(
                reason = RejectionReason.EXTREME_EXPOSURE,
                userMessage = "Iluminação inadequada. Evite reflexos intensos ou sombras escuras sobre o laudo.",
                currentScore = meanLuminance
            )
        }

        val variance = calculateLaplacianVariance(gray, width, height)

        return if (variance < MIN_SHARPNESS_THRESHOLD) {
            QualityEvaluationResult.Rejected(
                reason = RejectionReason.BLUR_DETECTED,
                userMessage = "Foto borrada ou fora de foco (Score: ${variance.toInt()}). " +
                        "Apoie o exame em local bem iluminado e mantenha o celular firme.",
                currentScore = variance
            )
        } else {
            QualityEvaluationResult.Approved(sharpnessScore = variance)
        }
    }

    /**
     * Convolução 3x3 do kernel Laplaciano:
     * [ 0  1  0 ]
     * [ 1 -4  1 ]
     * [ 0  1  0 ]
     * E cálculo da variância estatística sobre a resposta do filtro.
     */
    fun calculateLaplacianVariance(gray: FloatArray, width: Int, height: Int): Double {
        val laplacianValues = mutableListOf<Double>()
        var sum = 0.0

        // Subamostragem inteligente de passo caso a imagem seja gigante (> 1500px) para garantir execução < 100ms
        val step = if (width > 2000 || height > 2000) 2 else 1

        for (y in 1 until height - 1 step step) {
            val rowOffset = y * width
            val topOffset = (y - 1) * width
            val bottomOffset = (y + 1) * width

            for (x in 1 until width - 1 step step) {
                val center = gray[rowOffset + x]
                val top = gray[topOffset + x]
                val bottom = gray[bottomOffset + x]
                val left = gray[rowOffset + (x - 1)]
                val right = gray[rowOffset + (x + 1)]

                // L = top + bottom + left + right - 4 * center
                val lap = (top + bottom + left + right - 4.0 * center)
                laplacianValues.add(lap)
                sum += lap
            }
        }

        if (laplacianValues.isEmpty()) return 0.0

        val count = laplacianValues.size
        val mean = sum / count

        var varianceSum = 0.0
        for (i in 0 until count) {
            val diff = laplacianValues[i] - mean
            varianceSum += diff * diff
        }

        return varianceSum / count
    }
}
