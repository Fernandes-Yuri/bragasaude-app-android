package br.com.bragasaude.agent.extraction

import android.graphics.Bitmap
import android.util.Base64
import br.com.bragasaude.agent.AgentTool
import br.com.bragasaude.agent.protocol.ToolDeclaration
import br.com.bragasaude.agent.protocol.ToolFunctionDef
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.float
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.io.ByteArrayOutputStream

/**
 * Provedor de Bitmaps para páginas de documentos sob demanda.
 */
fun interface PageImageProvider {
    suspend fun getPageBitmap(pageIndex: Int): Bitmap?
}

/**
 * Ferramenta agêntica de Recorte Cirúrgico (Camada 3 da cascata de extração).
 *
 * Registrada como `inspect_crop_vision`. Quando o modelo ou o harness encontra
 * uma linha ambígua (ex: caligrafia médica com confiança < 0.75), recorta a área exata
 * em alta resolução e gera um mini payload JPEG (< 50 KB) em base64.
 *
 * Garante Zero File Egress: o documento inteiro NUNCA é transmitido.
 */
class SurgicalCropTool(
    private val imageProvider: PageImageProvider
) : AgentTool {

    companion object {
        const val TOOL_NAME = "inspect_crop_vision"
        const val MAX_PAYLOAD_BYTES = 50 * 1024 // Limite estrito de 50 KB
        private const val MAX_DIMENSION = 640
        private const val DEFAULT_JPEG_QUALITY = 80
        private const val FALLBACK_JPEG_QUALITY = 55
    }

    override val declaration: ToolDeclaration = ToolDeclaration(
        type = "function",
        function = ToolFunctionDef(
            name = TOOL_NAME,
            description = "Recorta uma área retangular de baixa confiança ou caligrafia ambígua em uma página do documento para análise visual cirúrgica. Retorna um mini JPEG em base64 estritamente menor que 50 KB.",
            parameters = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("page_index") {
                        put("type", "integer")
                        put("description", "Índice da página (0-indexed)")
                    }
                    putJsonObject("left") {
                        put("type", "number")
                        put("description", "Coordenada esquerda normalizada (0.0 a 1.0)")
                    }
                    putJsonObject("top") {
                        put("type", "number")
                        put("description", "Coordenada superior normalizada (0.0 a 1.0)")
                    }
                    putJsonObject("right") {
                        put("type", "number")
                        put("description", "Coordenada direita normalizada (0.0 a 1.0)")
                    }
                    putJsonObject("bottom") {
                        put("type", "number")
                        put("description", "Coordenada inferior normalizada (0.0 a 1.0)")
                    }
                }
                putJsonArray("required") {
                    add(JsonPrimitive("page_index"))
                    add(JsonPrimitive("left"))
                    add(JsonPrimitive("top"))
                    add(JsonPrimitive("right"))
                    add(JsonPrimitive("bottom"))
                }
            }
        )
    )

    override suspend fun execute(argumentsJson: String): String {
        return try {
            val root = Json.parseToJsonElement(argumentsJson).jsonObject

            val pageIndex = root["page_index"]?.jsonPrimitive?.int ?: 0
            val left = root["left"]?.jsonPrimitive?.float?.coerceIn(0.0f, 1.0f) ?: 0.0f
            val top = root["top"]?.jsonPrimitive?.float?.coerceIn(0.0f, 1.0f) ?: 0.0f
            val right = root["right"]?.jsonPrimitive?.float?.coerceIn(left, 1.0f) ?: 1.0f
            val bottom = root["bottom"]?.jsonPrimitive?.float?.coerceIn(top, 1.0f) ?: 1.0f

            val sourceBitmap = imageProvider.getPageBitmap(pageIndex)
                ?: return errorJson("Página $pageIndex não pôde ser renderizada ou não existe.")

            val bmpWidth = sourceBitmap.width
            val bmpHeight = sourceBitmap.height

            // Margem de segurança de 4% para não cortar traçados ascendentes/descendentes da caligrafia
            val padX = (right - left) * 0.04f
            val padY = (bottom - top) * 0.08f

            val clampedLeft = (left - padX).coerceIn(0.0f, 1.0f)
            val clampedTop = (top - padY).coerceIn(0.0f, 1.0f)
            val clampedRight = (right + padX).coerceIn(clampedLeft, 1.0f)
            val clampedBottom = (bottom + padY).coerceIn(clampedTop, 1.0f)

            val pxLeft = (clampedLeft * bmpWidth).toInt().coerceIn(0, bmpWidth - 1)
            val pxTop = (clampedTop * bmpHeight).toInt().coerceIn(0, bmpHeight - 1)
            val pxRight = (clampedRight * bmpWidth).toInt().coerceIn(pxLeft + 1, bmpWidth)
            val pxBottom = (clampedBottom * bmpHeight).toInt().coerceIn(pxTop + 1, bmpHeight)

            val cropWidth = (pxRight - pxLeft).coerceAtLeast(1)
            val cropHeight = (pxBottom - pxTop).coerceAtLeast(1)

            val cropped = Bitmap.createBitmap(sourceBitmap, pxLeft, pxTop, cropWidth, cropHeight)

            // Redimensionamento se exceder a dimensão máxima
            val scaled = if (cropped.width > MAX_DIMENSION || cropped.height > MAX_DIMENSION) {
                val factor = minOf(
                    MAX_DIMENSION.toFloat() / cropped.width,
                    MAX_DIMENSION.toFloat() / cropped.height
                )
                val sw = (cropped.width * factor).toInt().coerceAtLeast(1)
                val sh = (cropped.height * factor).toInt().coerceAtLeast(1)
                Bitmap.createScaledBitmap(cropped, sw, sh, true).also {
                    if (it !== cropped) cropped.recycle()
                }
            } else {
                cropped
            }

            // Compressão JPEG garantindo payload < 50 KB
            var outputStream = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, DEFAULT_JPEG_QUALITY, outputStream)
            var bytes = outputStream.toByteArray()

            if (bytes.size > (MAX_PAYLOAD_BYTES - 2048)) {
                // Segunda passada com compressão maior se necessário
                outputStream = ByteArrayOutputStream()
                scaled.compress(Bitmap.CompressFormat.JPEG, FALLBACK_JPEG_QUALITY, outputStream)
                bytes = outputStream.toByteArray()
            }

            val finalWidth = scaled.width
            val finalHeight = scaled.height
            scaled.recycle()

            val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)

            buildJsonObject {
                put("status", "success")
                put("page_index", pageIndex)
                put("mime_type", "image/jpeg")
                put("size_bytes", bytes.size)
                put("width", finalWidth)
                put("height", finalHeight)
                put("image_base64", base64)
            }.toString()

        } catch (e: Exception) {
            errorJson("Falha ao recortar fragmento: ${e.message}")
        }
    }

    private fun errorJson(message: String): String = buildJsonObject {
        put("status", "error")
        put("message", message)
    }.toString()
}
