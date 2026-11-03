package br.com.bragasaude.agent.extraction

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Abstração para o motor óptico de reconhecimento de caracteres (OCR).
 */
interface OcrEngine {
    /**
     * Processa um [Bitmap] e retorna os blocos de texto extraídos com confiança e posicionamento normalizado.
     */
    suspend fun recognize(bitmap: Bitmap): List<ExtractedBlock>
}

/**
 * Implementação do [OcrEngine] usando Google ML Kit Text Recognition on-device.
 */
@Singleton
class MlKitOcrEngine @Inject constructor() : OcrEngine {

    companion object {
        /**
         * Estima a pontuação de confiança de uma linha de texto reconhecida.
         * Detecta anomalias típicas de caligrafia ambígua ou artefatos de ruído.
         */
        fun estimateLineConfidence(lineText: String): Float {
            if (lineText.isBlank()) return 0.0f
            val clean = lineText.trim()

            // Proporção de caracteres médicos/alfanuméricos válidos
            val validChars = clean.count { it.isLetterOrDigit() || it.isWhitespace() || it in ".,-/:()%" }
            val ratio = validChars.toFloat() / clean.length.toFloat()

            return when {
                ratio < 0.60f -> 0.45f
                ratio < 0.75f -> 0.65f
                ratio < 0.88f -> 0.80f
                else -> 0.92f
            }
        }
    }

    override suspend fun recognize(bitmap: Bitmap): List<ExtractedBlock> {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        return try {
            suspendCancellableCoroutine { continuation ->
                val inputImage = InputImage.fromBitmap(bitmap, 0)
                recognizer.process(inputImage)
                    .addOnSuccessListener { visionText ->
                        val blocks = mutableListOf<ExtractedBlock>()
                        val bmpWidth = bitmap.width.toFloat().coerceAtLeast(1f)
                        val bmpHeight = bitmap.height.toFloat().coerceAtLeast(1f)

                        var lineCounter = 0
                        for (textBlock in visionText.textBlocks) {
                            for (line in textBlock.lines) {
                                val box = line.boundingBox
                                val normalizedBox = if (box != null) {
                                    val left = (box.left.toFloat() / bmpWidth).coerceIn(0.0f, 1.0f)
                                    val top = (box.top.toFloat() / bmpHeight).coerceIn(0.0f, 1.0f)
                                    val right = (box.right.toFloat() / bmpWidth).coerceIn(left, 1.0f)
                                    val bottom = (box.bottom.toFloat() / bmpHeight).coerceIn(top, 1.0f)
                                    NormalizedBoundingBox(left, top, right, bottom)
                                } else null

                                // Tenta extrair confiança nativa se suportada pela versão do ML Kit
                                val nativeConfidence = runCatching {
                                    val method = line.elements.firstOrNull()?.javaClass?.getMethod("getConfidence")
                                    line.elements.mapNotNull { method?.invoke(it) as? Float }.average().toFloat()
                                }.getOrNull()

                                val confidence = nativeConfidence ?: estimateLineConfidence(line.text)

                                blocks.add(
                                    ExtractedBlock(
                                        text = line.text,
                                        confidence = confidence,
                                        box = normalizedBox,
                                        lineIndex = lineCounter++
                                    )
                                )
                            }
                        }
                        continuation.resume(blocks)
                    }
                    .addOnFailureListener { error ->
                        continuation.resumeWithException(error)
                    }
            }
        } finally {
            recognizer.close()
        }
    }
}
