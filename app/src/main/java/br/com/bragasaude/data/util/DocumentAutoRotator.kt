package br.com.bragasaude.data.util

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/**
 * Utilitário de Auto-Rotação e Normalização de Documentos Médicos (Receitas e Exames).
 *
 * 1. Respeita e corrige metadados EXIF da câmera / galeria.
 * 2. Analisa o ângulo do documento físico e corrige rotações de 90°, 180° e 270°
 *    (fotos tiradas lateralizadas ou de cabeça para baixo).
 */
object DocumentAutoRotator {

    private const val MAX_EDGE = 2400
    private const val PREVIEW_MAX_EDGE = 800

    /**
     * Decodifica a imagem a partir de uma [Uri], aplicando correção EXIF e auto-rotação on-device.
     */
    suspend fun decodeAndRotate(
        resolver: ContentResolver,
        uri: Uri,
        probe: (suspend (Bitmap, Int) -> Int)? = null
    ): Bitmap = withContext(Dispatchers.IO) {
        val baseBitmap = decodeExifCorrected(resolver, uri)
        val rotated = autoRotateDocument(baseBitmap, probe)
        if (rotated !== baseBitmap) {
            baseBitmap.recycle()
        }
        rotated
    }

    /**
     * Decodifica a imagem garantindo que a orientação EXIF seja respeitada.
     */
    fun decodeExifCorrected(resolver: ContentResolver, uri: Uri): Bitmap {
        val orientation = runCatching {
            resolver.openInputStream(uri)?.use { stream ->
                ExifInterface(stream).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
            }
        }.getOrNull() ?: ExifInterface.ORIENTATION_NORMAL

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(resolver, uri)
            return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                val (w, h) = targetSize(info.size.width, info.size.height)
                decoder.setTargetSize(w, h)
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                decoder.isMutableRequired = true
            }
        }

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Foto ilegível ou dimensões inválidas" }

        val sampleSize = maxOf(bounds.outWidth, bounds.outHeight).div(MAX_EDGE).coerceAtLeast(1)
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val raw = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
            ?: error("Não foi possível decodificar a foto")

        val (targetW, targetH) = targetSize(raw.width, raw.height)
        val scaled = Bitmap.createScaledBitmap(raw, targetW, targetH, true)
        if (scaled !== raw) raw.recycle()

        val matrix = exifMatrix(orientation)
        if (matrix.isIdentity) {
            return scaled
        }

        val oriented = Bitmap.createBitmap(scaled, 0, 0, scaled.width, scaled.height, matrix, true)
        if (oriented !== scaled) scaled.recycle()
        return oriented
    }

    private fun exifMatrix(orientation: Int): Matrix = Matrix().apply {
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { postRotate(90f); postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_TRANSVERSE -> { postRotate(270f); postScale(-1f, 1f) }
        }
    }

    /**
     * Analisa o documento e rotaciona para o ângulo legível ideal (0°, 90°, 180° ou 270°).
     */
    suspend fun autoRotateDocument(
        bitmap: Bitmap,
        probe: (suspend (Bitmap, Int) -> Int)? = null
    ): Bitmap = withContext(Dispatchers.IO) {
        val (preview, shouldRecyclePreview) = createPreview(bitmap)
        try {
            val bestRotation = detectBestRotation(preview, probe)
            if (bestRotation == 0) {
                bitmap
            } else {
                val matrix = Matrix().apply { postRotate(bestRotation.toFloat()) }
                Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            }
        } finally {
            if (shouldRecyclePreview) {
                preview.recycle()
            }
        }
    }

    internal suspend fun detectBestRotation(
        preview: Bitmap,
        customProbe: (suspend (Bitmap, Int) -> Int)? = null
    ): Int {
        val probe = customProbe ?: ::defaultMlKitProbe

        // Passo 1: Avalia rotação 0°
        val words0 = runCatching { probe(preview, 0) }.getOrDefault(0)

        // Se encontrou texto abundante já em 0° (>= 8 palavras legíveis), está na orientação correta!
        if (words0 >= 8) {
            return 0
        }

        // Passo 2: Se 0° for insuficiente, testa 90°, 180° e 270°
        val words90 = runCatching { probe(preview, 90) }.getOrDefault(0)
        val words180 = runCatching { probe(preview, 180) }.getOrDefault(0)
        val words270 = runCatching { probe(preview, 270) }.getOrDefault(0)

        val scores = listOf(
            0 to words0,
            90 to words90,
            180 to words180,
            270 to words270
        )

        val winner = scores.maxByOrNull { it.second } ?: (0 to words0)

        // Só rotaciona se o ângulo vencedor for significativamente melhor que 0°
        return if (winner.first != 0 && winner.second >= 5 && winner.second > words0 + 3) {
            winner.first
        } else {
            0
        }
    }

    private suspend fun defaultMlKitProbe(preview: Bitmap, rotationDegrees: Int): Int {
        return runCatching {
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            val inputImage = InputImage.fromBitmap(preview, rotationDegrees)
            suspendCancellableCoroutine { continuation ->
                recognizer.process(inputImage)
                    .addOnSuccessListener { visionText ->
                        val wordCount = visionText.textBlocks
                            .flatMap { it.lines }
                            .flatMap { it.text.split(Regex("""\s+""")) }
                            .count { word -> word.length >= 3 && word.any { it.isLetter() } }
                        continuation.resume(wordCount)
                    }
                    .addOnFailureListener {
                        continuation.resume(0)
                    }
            }
        }.getOrDefault(0)
    }

    private fun createPreview(bitmap: Bitmap): Pair<Bitmap, Boolean> {
        val maxDim = maxOf(bitmap.width, bitmap.height)
        if (maxDim <= PREVIEW_MAX_EDGE) return bitmap to false
        val scale = PREVIEW_MAX_EDGE.toFloat() / maxDim
        val w = (bitmap.width * scale).toInt().coerceAtLeast(1)
        val h = (bitmap.height * scale).toInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(bitmap, w, h, true)
        return scaled to (scaled !== bitmap)
    }

    internal fun targetSize(width: Int, height: Int): Pair<Int, Int> {
        require(width > 0 && height > 0)
        val scale = minOf(1.0, MAX_EDGE.toDouble() / maxOf(width, height))
        return (width * scale).toInt().coerceAtLeast(1) to (height * scale).toInt().coerceAtLeast(1)
    }
}
