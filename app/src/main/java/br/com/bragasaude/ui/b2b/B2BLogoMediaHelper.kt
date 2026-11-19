package br.com.bragasaude.ui.b2b

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

data class PreparedLogoMedia(
    val fileName: String,
    val mimeType: String,
    val bytes: ByteArray
)

/**
 * Preparacao local de imagem da galeria para logo B2B-16B:
 * limita leitura de entrada (15 MB), aplica orientacao EXIF, redimensiona para max 512px
 * e re-encoda sem metadados (PNG prioritario, com fallback JPEG 90% se exceder 1,8 MB).
 */
object B2BLogoMediaHelper {
    private const val MAX_INPUT_READ_BYTES = 15 * 1024 * 1024
    private const val MAX_TARGET_UPLOAD_BYTES = 2 * 1024 * 1024
    private const val MAX_PNG_PREFER_BYTES = 1_800_000
    private val ALLOWED_MIME_TYPES = setOf("image/png", "image/jpeg", "image/webp")

    suspend fun prepareFromUri(context: Context, uri: Uri): PreparedLogoMedia =
        withContext(Dispatchers.IO) {
            val rawBytes = readUriBounded(context, uri)
            prepareFromBytes(rawBytes)
        }

    private fun readUriBounded(context: Context, uri: Uri): ByteArray {
        val stream = context.contentResolver.openInputStream(uri)
            ?: error("Não foi possível abrir a imagem selecionada.")
        return stream.use { input ->
            val out = ByteArrayOutputStream()
            val buf = ByteArray(16 * 1024)
            var total = 0
            while (true) {
                val read = input.read(buf)
                if (read <= 0) break
                total += read
                if (total > MAX_INPUT_READ_BYTES) {
                    error("Escolha uma imagem menor que 15 MB.")
                }
                out.write(buf, 0, read)
            }
            val result = out.toByteArray()
            if (result.isEmpty()) error("Arquivo de imagem vazio.")
            result
        }
    }

    internal fun prepareFromBytes(rawBytes: ByteArray): PreparedLogoMedia {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size, bounds)
        require(
            bounds.outWidth > 0 &&
                bounds.outHeight > 0 &&
                bounds.outWidth.toLong() * bounds.outHeight.toLong() <= 100_000_000L &&
                bounds.outMimeType in ALLOWED_MIME_TYPES
        ) {
            "Formato não suportado. Selecione uma imagem PNG, JPEG ou WebP."
        }

        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 1024) {
            sample *= 2
        }
        val decoded = BitmapFactory.decodeByteArray(
            rawBytes,
            0,
            rawBytes.size,
            BitmapFactory.Options().apply { inSampleSize = sample }
        ) ?: error("Não foi possível decodificar a imagem escolhida.")

        val oriented = applyExifOrientation(decoded, rawBytes)
        try {
            val maxDim = maxOf(oriented.width, oriented.height)
            val scaled = if (maxDim > 512) {
                val ratio = 512f / maxDim.toFloat()
                Bitmap.createScaledBitmap(
                    oriented,
                    (oriented.width * ratio).toInt().coerceAtLeast(1),
                    (oriented.height * ratio).toInt().coerceAtLeast(1),
                    true
                )
            } else {
                oriented
            }
            try {
                val pngOut = ByteArrayOutputStream()
                scaled.compress(Bitmap.CompressFormat.PNG, 100, pngOut)
                val pngBytes = pngOut.toByteArray()
                if (pngBytes.size <= MAX_PNG_PREFER_BYTES) {
                    return PreparedLogoMedia("logo.png", "image/png", pngBytes)
                }

                val jpegOut = ByteArrayOutputStream()
                scaled.compress(Bitmap.CompressFormat.JPEG, 90, jpegOut)
                val jpegBytes = jpegOut.toByteArray()
                require(jpegBytes.size <= MAX_TARGET_UPLOAD_BYTES) {
                    "A imagem excede o limite de 2 MB mesmo após compressão."
                }
                return PreparedLogoMedia("logo.jpg", "image/jpeg", jpegBytes)
            } finally {
                if (scaled !== oriented) scaled.recycle()
            }
        } finally {
            if (oriented !== decoded) oriented.recycle()
            decoded.recycle()
        }
    }

    private fun applyExifOrientation(bitmap: Bitmap, rawBytes: ByteArray): Bitmap {
        val orientation = runCatching {
            ExifInterface(ByteArrayInputStream(rawBytes)).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.setScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.setRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.setRotate(-90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(-90f)
            else -> return bitmap
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }
}
