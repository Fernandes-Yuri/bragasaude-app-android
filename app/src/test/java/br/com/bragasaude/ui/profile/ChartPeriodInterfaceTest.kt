package br.com.bragasaude.ui.profile

import android.app.Application
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import br.com.bragasaude.ui.components.SimpleTrendChart
import br.com.bragasaude.ui.theme.BragasaudeTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w360dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ChartPeriodInterfaceTest {
    @get:Rule val compose = createComposeRule()
    @Test fun legacyOffsetsRemainCalendarDaysAndTheSelectorFitsLargeText() {
        var scale by mutableFloatStateOf(1f)
        val dates = (7L downTo 0L).map { LocalDate.now().minusDays(it).toString() + "T09:00:00-0300" }
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, scale)) {
                BragasaudeTheme(forceThemeMode = "LIGHT") {
                    SimpleTrendChart(List(8) { 120.0 + it }, timestamps = dates, secondaryData = List(8) { 70.0 + it },
                        label = "Pressão", secondaryLabel = "Diastólica", unit = "mmHg", showPeriodSelector = true)
                }
            }
        }
        compose.onNodeWithText("7D").assertIsDisplayed()
        compose.onNodeWithText("30D").assertIsDisplayed().performClick()
        compose.onNodeWithText("Registro 7").assertDoesNotExist()
        compose.onNodeWithContentDescription("127/77 mmHg", substring = true).assertIsDisplayed()
        compose.runOnIdle { scale = 2f }
        compose.onNodeWithText("30D").assertIsDisplayed()
    }
}
