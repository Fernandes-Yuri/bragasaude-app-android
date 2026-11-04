package br.com.bragasaude.data.util

import android.app.Application
import android.graphics.Bitmap
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE, application = Application::class)
class DocumentAutoRotatorTest {

    @Test
    fun `targetSize redimensiona mantendo proporcao quando imagem excede o limite`() {
        assertEquals(1800 to 2400, DocumentAutoRotator.targetSize(3000, 4000))
        assertEquals(2400 to 1800, DocumentAutoRotator.targetSize(4000, 3000))
    }

    @Test
    fun `targetSize preserva dimensoes menores ou iguais a MAX_EDGE`() {
        assertEquals(800 to 600, DocumentAutoRotator.targetSize(800, 600))
        assertEquals(1080 to 1920, DocumentAutoRotator.targetSize(1080, 1920))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `targetSize lanca excecao para dimensoes invalidas`() {
        DocumentAutoRotator.targetSize(0, -10)
    }

    @Test
    fun `detectBestRotation retorna 0 imediatamente se rotacao 0 contem texto abundante`() = runTest {
        val dummy = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        val probe: suspend (Bitmap, Int) -> Int = { _, rot ->
            if (rot == 0) 15 else 0
        }

        val best = DocumentAutoRotator.detectBestRotation(dummy, probe)
        assertEquals(0, best)
    }

    @Test
    fun `detectBestRotation detecta rotacao 90 graus quando texto legivel esta a 90 graus`() = runTest {
        val dummy = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        val probe: suspend (Bitmap, Int) -> Int = { _, rot ->
            when (rot) {
                0 -> 1
                90 -> 22
                180 -> 0
                270 -> 2
                else -> 0
            }
        }

        val best = DocumentAutoRotator.detectBestRotation(dummy, probe)
        assertEquals(90, best)
    }

    @Test
    fun `detectBestRotation detecta rotacao 180 graus de cabeca para baixo`() = runTest {
        val dummy = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        val probe: suspend (Bitmap, Int) -> Int = { _, rot ->
            when (rot) {
                0 -> 0
                90 -> 2
                180 -> 25
                270 -> 1
                else -> 0
            }
        }

        val best = DocumentAutoRotator.detectBestRotation(dummy, probe)
        assertEquals(180, best)
    }

    @Test
    fun `detectBestRotation detecta rotacao 270 graus lateralizada`() = runTest {
        val dummy = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        val probe: suspend (Bitmap, Int) -> Int = { _, rot ->
            when (rot) {
                0 -> 2
                90 -> 1
                180 -> 0
                270 -> 19
                else -> 0
            }
        }

        val best = DocumentAutoRotator.detectBestRotation(dummy, probe)
        assertEquals(270, best)
    }

    @Test
    fun `detectBestRotation mantem 0 se nenhuma rotacao tiver texto significativo`() = runTest {
        val dummy = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        val probe: suspend (Bitmap, Int) -> Int = { _, _ -> 1 }

        val best = DocumentAutoRotator.detectBestRotation(dummy, probe)
        assertEquals(0, best)
    }

    @Test
    fun `autoRotateDocument rotaciona bitmap real quando rotacao detectada for diferente de zero`() = runTest {
        // Criar um bitmap de 200x100 (largura > altura)
        val original = Bitmap.createBitmap(200, 100, Bitmap.Config.ARGB_8888)
        val probe: suspend (Bitmap, Int) -> Int = { _, rot ->
            if (rot == 90) 20 else 0
        }

        val result = DocumentAutoRotator.autoRotateDocument(original, probe)
        assertNotNull(result)
        // Rotacionado por 90 graus: nova largura deve ser 100 e nova altura 200
        assertEquals(100, result.width)
        assertEquals(200, result.height)
    }
}
