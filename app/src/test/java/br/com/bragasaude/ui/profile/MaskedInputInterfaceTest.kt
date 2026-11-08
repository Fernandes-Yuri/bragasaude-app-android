package br.com.bragasaude.ui.profile

import android.app.Application
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import br.com.bragasaude.ui.components.*
import br.com.bragasaude.ui.theme.BragasaudeTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class MaskedInputInterfaceTest {
    @get:Rule val compose = createComposeRule()

    @Test fun typingDoesNotMoveNumbersBackwardsAndEditingTheMiddlePreservesTheRest() {
        var value by mutableStateOf("")
        compose.setContent { BragasaudeTheme(forceThemeMode = "LIGHT") {
            MaskedInputField(value, { value = it }, InputMask.TIME, Modifier.testTag("time"))
        } }
        val field = compose.onNodeWithTag("time")
        for ((key, expected) in listOf("0" to "0", "3" to "03", "1" to "03:1", "4" to "03:14")) {
            field.performTextInput(key)
            compose.runOnIdle { assertEquals(expected, value) }
        }
        field.performTextInputSelection(TextRange(0, 2))
        field.performTextInput("09")
        compose.runOnIdle { assertEquals("09:14", value) }
        field.performTextClearance()
        field.performTextInput("21:45")
        compose.runOnIdle { assertEquals("21:45", value) }
    }

    @Test fun datePasteAndTimeListKeepTheirSeparatorsWithoutCorruptingTheBuffer() {
        var value by mutableStateOf("")
        compose.setContent { BragasaudeTheme(forceThemeMode = "LIGHT") {
            MaskedInputField(value, { value = it }, InputMask.TIME_LIST, Modifier.testTag("times"))
        } }
        val field = compose.onNodeWithTag("times")
        for (key in "0800,2000") field.performTextInput(key.toString())
        compose.runOnIdle { assertEquals("08:00, 20:00", value) }
        field.performTextInputSelection(TextRange(0, 2))
        field.performTextInput("07")
        compose.runOnIdle { assertEquals("07:00, 20:00", value) }
    }
}
