package br.com.bragasaude.data.avatar

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.inject.Inject

interface AvatarImageAnalyzer {
    suspend fun analyze(bitmap: Bitmap): AvatarVisualFindings
}

class BundledAvatarImageAnalyzer @Inject constructor() : AvatarImageAnalyzer {
    override suspend fun analyze(bitmap: Bitmap): AvatarVisualFindings = withContext(NonCancellable) {
        val detector = FaceDetection.getClient(FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE).build())
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val scale = maxOf(1f, 480f / maxOf(bitmap.width, bitmap.height))
        val inputBitmap = if (scale > 1f) Bitmap.createScaledBitmap(bitmap,
            (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true) else bitmap
        try {
            val image = InputImage.fromBitmap(inputBitmap, 0)
            val faces = detector.process(image).await()
            val text = recognizer.process(image).await().text
            AvatarVisualFindings(faces.size, text)
        } finally {
            detector.close()
            recognizer.close()
            if (inputBitmap !== bitmap) inputBitmap.recycle()
        }
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class AvatarImageAnalyzerModule {
    @Binds abstract fun analyzer(implementation: BundledAvatarImageAnalyzer): AvatarImageAnalyzer
}

/** Mesma preparação local para galeria e foto Google; nenhum resultado OCR é enviado ou persistido. */
class LocalAvatarPhotoProcessor @Inject constructor(private val analyzer: AvatarImageAnalyzer) {
    suspend fun prepare(bytes: ByteArray): ByteArray = withContext(Dispatchers.Default) {
        require(bytes.isNotEmpty() && bytes.size <= MAX_SOURCE_BYTES) { "Escolha uma imagem de até 20 MB." }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0 &&
            bounds.outWidth.toLong() * bounds.outHeight <= 120_000_000 &&
            bounds.outMimeType in setOf("image/jpeg", "image/png", "image/webp")) {
            "Foto inválida. Escolha uma imagem JPEG, PNG ou WebP."
        }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 1024) sample *= 2
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size,
            BitmapFactory.Options().apply { inSampleSize = sample }) ?: error("Não foi possível abrir a foto.")
        val orientation = runCatching { ExifInterface(ByteArrayInputStream(bytes))
            .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }
            .getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        val matrix = Matrix().apply {
            when (orientation) {
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> setScale(-1f, 1f)
                ExifInterface.ORIENTATION_ROTATE_180 -> setRotate(180f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> setScale(1f, -1f)
                ExifInterface.ORIENTATION_TRANSPOSE -> { setRotate(90f); postScale(-1f, 1f) }
                ExifInterface.ORIENTATION_ROTATE_90 -> setRotate(90f)
                ExifInterface.ORIENTATION_TRANSVERSE -> { setRotate(-90f); postScale(-1f, 1f) }
                ExifInterface.ORIENTATION_ROTATE_270 -> setRotate(-90f)
            }
        }
        val bitmap = try { Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true) }
            catch (e: Exception) { decoded.recycle(); throw e }
        try {
            val findings = try { analyzer.analyze(bitmap) }
            catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (_: Exception) { error("Não foi possível conferir a foto neste aparelho. Tente novamente.") }
            currentCoroutineContext().ensureActive()
            AvatarPhotoRules.rejection(bounds.outWidth, bounds.outHeight, findings)?.let { error(it) }
            val scale = minOf(1f, 512f / maxOf(bitmap.width, bitmap.height))
            val small = if (scale < 1f) Bitmap.createScaledBitmap(bitmap,
                (bitmap.width * scale).toInt().coerceAtLeast(1), (bitmap.height * scale).toInt().coerceAtLeast(1), true) else bitmap
            try {
                ByteArrayOutputStream().use { output ->
                    check(small.compress(Bitmap.CompressFormat.JPEG, 85, output)) { "Não foi possível preparar a foto." }
                    output.toByteArray()
                }
            } finally { if (small !== bitmap) small.recycle() }
        } finally {
            if (bitmap !== decoded) bitmap.recycle()
            decoded.recycle()
        }
    }

    companion object {
        const val MAX_SOURCE_BYTES = 20 * 1024 * 1024
        fun readSource(context: Context, uri: Uri): ByteArray {
            val input = context.contentResolver.openInputStream(uri) ?: error("Não foi possível abrir a foto.")
            return input.use {
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    val count = it.read(buffer)
                    if (count < 0) break
                    require(output.size() + count <= MAX_SOURCE_BYTES) { "Escolha uma imagem de até 20 MB." }
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            }
        }
    }
}
