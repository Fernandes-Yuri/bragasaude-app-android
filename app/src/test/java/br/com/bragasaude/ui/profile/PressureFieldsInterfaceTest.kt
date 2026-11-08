package br.com.bragasaude.ui.profile

import android.app.Application
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import br.com.bragasaude.ui.vitals.PressureNumericFields
import br.com.bragasaude.ui.theme.BragasaudeTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w360dp-h800dp-mdpi")
class PressureFieldsInterfaceTest {
    @get:Rule val compose = createComposeRule()
    @Test fun largeFontStacksAccessiblePressureFieldsAndValuesRemainEditable() {
        var systolic by mutableStateOf("")
        var diastolic by mutableStateOf("")
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 2f)) {
                BragasaudeTheme(forceThemeMode = "LIGHT") {
                    PressureNumericFields(systolic, diastolic, { systolic = it }, { diastolic = it })
                }
            }
        }
        val maximum = compose.onNodeWithContentDescription("Máxima, pressão sistólica, mmHg")
        val minimum = compose.onNodeWithContentDescription("Mínima, pressão diastólica, mmHg")
        maximum.assertIsDisplayed().performTextInput("120")
        minimum.assertIsDisplayed().performTextInput("80")
        compose.runOnIdle { assertEquals("120", systolic); assertEquals("80", diastolic) }
        assertTrue(minimum.fetchSemanticsNode().boundsInRoot.top >= maximum.fetchSemanticsNode().boundsInRoot.bottom)
    }
}
