package br.com.bragasaude.data.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageQualityGatekeeperTest {

    @Test
    fun `rejeita imagem com resolucao menor que 1080x1080`() {
        val width = 800
        val height = 600
        val pixels = IntArray(width * height) { 0xFF808080.toInt() }

        val result = ImageQualityGatekeeper.evaluatePixels(pixels, width, height)

        assertTrue("Deveria ser Rejected", result is QualityEvaluationResult.Rejected)
        val rejected = result as QualityEvaluationResult.Rejected
        assertEquals(RejectionReason.LOW_RESOLUTION, rejected.reason)
        assertTrue(rejected.userMessage.contains("Resolução insuficiente"))
    }

    @Test
    fun `rejeita imagem borrada ou uniforme sem bordas definidas`() {
        val width = 1080
        val height = 1080
        // Imagem cinza sólida (variância zero)
        val pixels = IntArray(width * height) { 0xFF808080.toInt() }

        val result = ImageQualityGatekeeper.evaluatePixels(pixels, width, height)

        assertTrue("Deveria ser Rejected por blur", result is QualityEvaluationResult.Rejected)
        val rejected = result as QualityEvaluationResult.Rejected
        assertEquals(RejectionReason.BLUR_DETECTED, rejected.reason)
        assertTrue(rejected.currentScore < 85.0)
    }

    @Test
    fun `aprova imagem nitida com alto contraste e bordas bem definidas`() {
        val width = 1080
        val height = 1080
        val pixels = IntArray(width * height)

        // Simula laudo médico com texto nítido: linhas pretas e brancas alternadas com bordas de alto gradiente
        for (y in 0 until height) {
            for (x in 0 until width) {
                val isDark = ((x / 4) % 2 == 0) && ((y / 4) % 2 == 0)
                pixels[y * width + x] = if (isDark) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
            }
        }

        val result = ImageQualityGatekeeper.evaluatePixels(pixels, width, height)

        assertTrue("Deveria ser Approved", result is QualityEvaluationResult.Approved)
        val approved = result as QualityEvaluationResult.Approved
        assertTrue("Score deve ser >= 85.0, foi ${approved.sharpnessScore}", approved.sharpnessScore >= 85.0)
    }

    @Test
    fun `rejeita imagem superexposta ou subexposta`() {
        val width = 1080
        val height = 1080

        // Subexposta (quase preta, média luminância < 20)
        val darkPixels = IntArray(width * height) { 0xFF050505.toInt() }
        val darkResult = ImageQualityGatekeeper.evaluatePixels(darkPixels, width, height)

        assertTrue("Deveria rejeitar por exposição extrema", darkResult is QualityEvaluationResult.Rejected)
        assertEquals(RejectionReason.EXTREME_EXPOSURE, (darkResult as QualityEvaluationResult.Rejected).reason)

        // Superexposta (quase branca pura, média luminância > 245)
        val brightPixels = IntArray(width * height) { 0xFFFAFAFA.toInt() }
        val brightResult = ImageQualityGatekeeper.evaluatePixels(brightPixels, width, height)

        assertTrue("Deveria rejeitar por superexposição", brightResult is QualityEvaluationResult.Rejected)
        assertEquals(RejectionReason.EXTREME_EXPOSURE, (brightResult as QualityEvaluationResult.Rejected).reason)
    }
}
