package br.com.bragasaude.ui.report

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.inspector.WindowInspector
import androidx.compose.material3.SnackbarHostState
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.remember
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
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

    @Test fun reportExplainsItsPurposeBeforeExportAndDoesNotShowMeasurementPanels() {
        var exports = 0
        compose.setContent {
            BragasaudeTheme(forceThemeMode = "LIGHT") {
                MedicalReportContent(false, remember { SnackbarHostState() }, {}, { exports++ })
            }
        }
        compose.onNodeWithText("Seu relatório de 30 dias").assertIsDisplayed()
        compose.onNodeWithText("Últimos registros vitais").assertDoesNotExist()
        compose.onNodeWithText("Resumo corporal").assertDoesNotExist()
        screenshot("01-por-que-usar-o-pdf")
        compose.onNodeWithText("Baixar relatório em PDF").assertIsDisplayed()
        compose.onNodeWithText("Baixar relatório em PDF").performClick()
        compose.runOnIdle { assertEquals(1, exports) }
        screenshot("02-exportar-relatorio")
    }

    @Test fun generationPreventsDuplicateExportWithoutHidingTheExplanation() {
        compose.setContent {
            BragasaudeTheme(forceThemeMode = "LIGHT") {
                MedicalReportContent(true, remember { SnackbarHostState() }, {}, {})
            }
        }
        compose.onNodeWithText("Seu relatório de 30 dias").assertIsDisplayed()
        compose.onNodeWithText("Gerando relatório...").assertIsDisplayed()
        compose.onNodeWithText("Gerando relatório...").assertIsNotEnabled()
    }

    @Test
    @Config(qualifiers = "w360dp-h640dp-mdpi")
    fun exportIsVisibleWithoutScrollingWithLargeFontAndBottomNavigationSpace() {
        compose.setContent {
            val density = androidx.compose.ui.platform.LocalDensity.current.density
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(density, 1.5f)) {
                BragasaudeTheme(forceThemeMode = "LIGHT") {
                    androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier
                        .fillMaxSize().padding(bottom = 80.dp)) {
                        MedicalReportContent(false, remember { SnackbarHostState() }, {}, {})
                    }
                }
            }
        }
        compose.onNodeWithText("Baixar relatório em PDF").assertIsDisplayed()
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
