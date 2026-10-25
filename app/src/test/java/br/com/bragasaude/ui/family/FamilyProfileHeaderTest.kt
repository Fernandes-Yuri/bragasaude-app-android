package br.com.bragasaude.ui.family

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.inspector.WindowInspector
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import br.com.bragasaude.ui.components.SharedProfileAvatar
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
@Config(sdk=[34], application=Application::class, qualifiers="w320dp-h740dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FamilyProfileHeaderTest {
    @get:Rule val compose = createComposeRule()
    @Test fun completeNameWrapsAtLargeFontWithoutEllipsis() {
        val name = "Yuri Fernandes dos Santos de Oliveira"
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.5f)) {
                BragasaudeTheme {
                    Box(Modifier.width(320.dp).padding(16.dp)) {
                        PatientHeaderCard(CaregiverDashboardState(patientUserId="fixture", patientName=name,
                            caregiverRelation="esposa"), avatarContent = { SharedProfileAvatar(name, null, null, 52.dp) })
                    }
                }
            }
        }
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(name).assertIsDisplayed().performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertFalse(layouts.single().hasVisualOverflow)
        assertTrue(layouts.single().lineCount > 1)
        compose.onNodeWithText("Ativo").assertIsDisplayed()
        compose.runOnIdle {
            val view = WindowInspector.getGlobalWindowViews().first()
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            val file = File("build/outputs/family-review/nome-completo-fonte-ampliada.png")
            file.parentFile.mkdirs()
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }
}
