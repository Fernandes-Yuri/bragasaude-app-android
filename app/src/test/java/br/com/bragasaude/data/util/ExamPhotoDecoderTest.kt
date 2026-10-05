package br.com.bragasaude.data.util

import org.junit.Assert.assertEquals
import org.junit.Test

class ExamPhotoDecoderTest {
    @Test fun `limita foto de alta resolucao mantendo proporcao e legibilidade`() {
        assertEquals(1800 to 2400, ExamPhotoDecoder.targetSize(3000, 4000))
        assertEquals(2400 to 1800, ExamPhotoDecoder.targetSize(4000, 3000))
    }
    @Test fun `nao amplia miniatura nem reduz documento que ja cabe no limite`() {
        assertEquals(320 to 240, ExamPhotoDecoder.targetSize(320, 240))
        assertEquals(1080 to 1920, ExamPhotoDecoder.targetSize(1080, 1920))
    }
    @Test(expected = IllegalArgumentException::class)
    fun `rejeita dimensoes invalidas de arquivo ilegivel`() {
        ExamPhotoDecoder.targetSize(0, -1)
    }
}
