package br.com.bragasaude.data.avatar

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.ExifInterface
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34], application=Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LocalAvatarPhotoProcessorTest {
    private fun source(width: Int, height: Int, format: Bitmap.CompressFormat = Bitmap.CompressFormat.PNG): ByteArray {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        return try { ByteArrayOutputStream().use { bitmap.compress(format, 90, it); it.toByteArray() } }
            finally { bitmap.recycle() }
    }
    @Test fun validInputIsAnalyzedThenReducedToJpeg() = runBlocking {
        var analyzed = false
        val processor = LocalAvatarPhotoProcessor(object : AvatarImageAnalyzer {
            override suspend fun analyze(bitmap: Bitmap): AvatarVisualFindings {
                assertTrue(maxOf(bitmap.width, bitmap.height) <= 1024)
                analyzed = true
                return AvatarVisualFindings(1, "")
            }
        })
        val jpeg = processor.prepare(source(1600, 1000))
        assertTrue(analyzed)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds=true }
        BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, bounds)
        assertEquals("image/jpeg", bounds.outMimeType)
        assertTrue(maxOf(bounds.outWidth, bounds.outHeight) <= 512)
    }
    @Test fun invalidFileNeverRunsVision() = runBlocking {
        val processor = LocalAvatarPhotoProcessor(object : AvatarImageAnalyzer {
            override suspend fun analyze(bitmap: Bitmap): AvatarVisualFindings = error("Não deve ser executado")
        })
        assertTrue(runCatching { processor.prepare("arquivo inválido".toByteArray()) }.isFailure)
    }
    @Test fun faceInDocumentIsRejectedBeforeReturningUploadBytes() = runBlocking {
        val processor = LocalAvatarPhotoProcessor(object : AvatarImageAnalyzer {
            override suspend fun analyze(bitmap: Bitmap) = AvatarVisualFindings(1, "CARTEIRA NACIONAL DE HABILITAÇÃO")
        })
        assertTrue(runCatching { processor.prepare(source(512, 512)) }.exceptionOrNull()!!.message!!.contains("documento"))
    }
    @Test fun rotatesExifBeforeAnalysisAndStripsSourceMetadata() = runBlocking {
        val file = File(RuntimeEnvironment.getApplication().cacheDir, "fixture-avatar.jpg")
        try {
            file.writeBytes(source(200, 100, Bitmap.CompressFormat.JPEG))
            ExifInterface(file.path).apply {
                setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_ROTATE_90.toString())
                setAttribute(ExifInterface.TAG_MAKE, "fixture-camera")
                saveAttributes()
            }
            val processor = LocalAvatarPhotoProcessor(object : AvatarImageAnalyzer {
                override suspend fun analyze(bitmap: Bitmap): AvatarVisualFindings {
                    assertEquals(100, bitmap.width)
                    assertEquals(200, bitmap.height)
                    return AvatarVisualFindings(1, "")
                }
            })
            val jpeg = processor.prepare(file.readBytes())
            file.writeBytes(jpeg)
            assertNull(ExifInterface(file.path).getAttribute(ExifInterface.TAG_MAKE))
        } finally { file.delete() }
    }
}
