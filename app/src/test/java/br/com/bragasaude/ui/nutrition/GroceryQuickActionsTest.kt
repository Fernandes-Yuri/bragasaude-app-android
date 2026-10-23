package br.com.bragasaude.ui.nutrition

import android.app.Application
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import br.com.bragasaude.ui.theme.BragasaudeTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w360dp-h800dp-mdpi")
class GroceryQuickActionsTest {
    @get:Rule val compose = createComposeRule()
    @Test fun periodAndActionsStayAccessibleInOneRow() {
        var days = 7
        var added = false
        var planned = false
        compose.setContent { BragasaudeTheme(forceThemeMode = "LIGHT") {
            Box(Modifier.width(320.dp)) {
                GroceryQuickActions(7, false, { days = it }, {}, { added = true }, { planned = true })
            }
        } }
        compose.onNodeWithText("14 dias").assertIsDisplayed().performClick()
        compose.onNodeWithText("Adicionar").assertIsDisplayed().performClick()
        compose.onNodeWithContentDescription("Opções de planejamento com IA").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(14, days); assertTrue(added); assertTrue(planned) }
    }
    @Test fun narrowScreenAndLargeFontKeepActionsWithCompactPeriodSelector() {
        var chooser = false
        compose.setContent { BragasaudeTheme(forceThemeMode = "LIGHT") {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, 1.5f)) {
                Box(Modifier.width(280.dp)) { GroceryQuickActions(7, false, {}, { chooser = true }, {}, {}) }
            }
        } }
        compose.onNodeWithText("7 dias").assertIsDisplayed().performClick()
        compose.onNodeWithText("Adicionar").assertIsDisplayed()
        compose.onNodeWithContentDescription("Opções de planejamento com IA").assertIsDisplayed()
        compose.onNodeWithText("30 dias").assertDoesNotExist()
        compose.runOnIdle { assertTrue(chooser) }
    }
}
