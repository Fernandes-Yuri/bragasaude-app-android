package br.com.bragasaude.ui.report

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.inspector.WindowInspector
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.remember
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import br.com.bragasaude.data.remote.model.RemoteBiometry
import br.com.bragasaude.data.remote.model.RemoteVitalSign
import br.com.bragasaude.ui.theme.BragasaudeTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w360dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MedicalReportInterfaceTest {
    @get:Rule val compose = createComposeRule()

    @Test fun reportShowsRealRecordsAndExportRemainsAccessible() {
        var exports = 0
        compose.setContent {
            BragasaudeTheme(forceThemeMode = "LIGHT") {
                MedicalReportContent(listOf(RemoteVitalSign(userId = "u", systolicPressure = 120, diastolicPressure = 80,
                    glucoseLevel = 95, heartRate = 65, hydrationMl = 250, measuredAt = "2026-10-23T10:00:00Z")),
                    RemoteBiometry(userId = "u", weight = 72f, height = 165f, imc = 26.4f, measuredAt = "2026-10-22T10:00:00Z"),
                    false, remember { SnackbarHostState() }, {}, { exports++ }, birthDate = "1990-01-01")
            }
        }
        compose.onNodeWithText("120/80 mmHg").assertIsDisplayed()
        compose.onNodeWithText("95 mg/dL").assertIsDisplayed()
        compose.onNodeWithText("Faixa de atenção").assertIsDisplayed()
        screenshot("01-registros-vitais")
        compose.onNodeWithTag("reportContent").performScrollToNode(hasText("Peso: 72,0 kg"))
        compose.onNodeWithText("Peso: 72,0 kg").assertIsDisplayed()
        compose.onNodeWithText("IMC calculado: 26,4 kg/m²").assertIsDisplayed()
        screenshot("02-resumo-corporal")
        compose.onNodeWithTag("reportContent").performScrollToNode(hasText("Baixar Relatório em PDF"))
        compose.onNodeWithText("Baixar Relatório em PDF").performClick()
        compose.runOnIdle { assertEquals(1, exports) }
    }

    @Test fun emptyReportExplainsMissingDataWithoutZeroMeasurements() {
        compose.setContent {
            BragasaudeTheme(forceThemeMode = "LIGHT") {
                MedicalReportContent(emptyList(), null, true, remember { SnackbarHostState() }, {}, {})
            }
        }
        compose.onAllNodesWithText("Sem registro").assertCountEquals(4)
        compose.onNodeWithContentDescription("Exportar PDF de 30 Dias").assertDoesNotExist()
        compose.onNodeWithTag("reportContent").performScrollToNode(hasText("Nenhuma medida corporal registrada.", substring = true))
        compose.onNodeWithText("Nenhuma medida corporal registrada.", substring = true).assertIsDisplayed()
        screenshot("03-sem-registros")
    }

    private fun screenshot(name: String) {
        compose.runOnIdle {
            val windows = WindowInspector.getGlobalWindowViews()
            val view = windows.first()
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            windows.forEach { it.draw(canvas) }
            val file = File("build/outputs/report-review/$name.png")
            requireNotNull(file.parentFile).mkdirs()
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }
}
