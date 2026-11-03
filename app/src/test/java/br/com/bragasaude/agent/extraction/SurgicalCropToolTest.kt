package br.com.bragasaude.agent.extraction

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SurgicalCropToolTest {

    private fun createTestBitmap(width: Int = 1000, height: Int = 1000): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)

        val paint = Paint().apply {
            color = Color.BLACK
            textSize = 24f
        }
        canvas.drawText("Dipirona 500mg 1 comp 6/6h", 100f, 250f, paint)
        return bitmap
    }

    @Test
    fun `valida nome e declaracao da ferramenta de recorte cirurgico`() {
        val tool = SurgicalCropTool { null }
        assertEquals(SurgicalCropTool.TOOL_NAME, tool.name)
        assertEquals(SurgicalCropTool.TOOL_NAME, tool.declaration.function.name)
        assertTrue(tool.description.contains("50 KB"))
    }

    @Test
    fun `executa recorte cirurgico e gera payload estritamente inferior a 50 KB`() = runTest {
        val testBitmap = createTestBitmap()
        val tool = SurgicalCropTool { pageIndex ->
            if (pageIndex == 0) testBitmap else null
        }

        // Solicita recorte das coordenadas da linha de medicamento
        val argsJson = """
            {
                "page_index": 0,
                "left": 0.08,
                "top": 0.20,
                "right": 0.45,
                "bottom": 0.30
            }
        """.trimIndent()

        val resultJson = tool.execute(argsJson)
        val json = Json.parseToJsonElement(resultJson).jsonObject

        assertEquals("success", json["status"]?.jsonPrimitive?.content)
        assertEquals("image/jpeg", json["mime_type"]?.jsonPrimitive?.content)
        assertEquals(0, json["page_index"]?.jsonPrimitive?.int)

        val sizeBytes = json["size_bytes"]?.jsonPrimitive?.int ?: 0
        assertTrue("Tamanho do recorte deve ser menor que 50 KB", sizeBytes < SurgicalCropTool.MAX_PAYLOAD_BYTES)
        assertTrue("Tamanho do recorte deve ser razoável para imagem válida (> 100 bytes)", sizeBytes > 100)

        val imageBase64 = json["image_base64"]?.jsonPrimitive?.content
        assertNotNull(imageBase64)
        assertTrue(imageBase64?.isNotEmpty() == true)
    }

    @Test
    fun `retorna erro gracioso quando a pagina solicitada nao existe`() = runTest {
        val tool = SurgicalCropTool { null }

        val argsJson = """
            {
                "page_index": 99,
                "left": 0.1,
                "top": 0.1,
                "right": 0.5,
                "bottom": 0.5
            }
        """.trimIndent()

        val resultJson = tool.execute(argsJson)
        val json = Json.parseToJsonElement(resultJson).jsonObject

        assertEquals("error", json["status"]?.jsonPrimitive?.content)
        assertTrue(json["message"]?.jsonPrimitive?.content?.contains("não pôde ser renderizada") == true)
    }

    @Test
    fun `retorna erro gracioso quando o JSON de argumentos e invalido`() = runTest {
        val tool = SurgicalCropTool { null }

        val resultJson = tool.execute("json-invalido")
        val json = Json.parseToJsonElement(resultJson).jsonObject

        assertEquals("error", json["status"]?.jsonPrimitive?.content)
        assertTrue(json["message"]?.jsonPrimitive?.content?.contains("Falha ao recortar") == true)
    }
}
