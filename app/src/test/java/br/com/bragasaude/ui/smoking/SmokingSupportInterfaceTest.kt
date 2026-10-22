package br.com.bragasaude.ui.smoking

import android.app.Application
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import br.com.bragasaude.data.local.smoking.*
import br.com.bragasaude.ui.profile.ProfileDataRow
import br.com.bragasaude.ui.theme.BragasaudeTheme
import io.mockk.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w320dp-h740dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SmokingSupportInterfaceTest {
    @get:Rule val compose = createComposeRule()
    private val today = LocalDate.parse("2026-10-22")
    private fun vm(owner: String? = "owner"): SmokingSupportViewModel = mockk<SmokingSupportViewModel>(relaxed = true).also {
        every { it.snapshot } returns MutableStateFlow(SmokingSupportSnapshot(ownerId = owner, loaded = true))
        every { it.today } returns MutableStateFlow(today)
        every { it.busy } returns MutableStateFlow(false)
        every { it.message } returns MutableStateFlow(null)
        every { it.offerEligible } returns MutableStateFlow(false)
        every { it.automaticOffer } returns MutableStateFlow(false)
    }
    @Test fun existingAccountCanDiscoverAndExplicitlyActivateWithoutSmokerPrecondition() {
        val vm = vm()
        compose.setContent { BragasaudeTheme { SmokingSupportScreen(onBack = {}, viewModel = vm) } }
        verify(exactly = 0) { vm.savePlan(any(), any(), any(), any(), any(), any(), any()) }
        compose.onNodeWithText("Preparar meu plano").performScrollTo().performClick()
        verify(exactly = 0) { vm.savePlan(any(), any(), any(), any(), any(), any(), any()) }
        compose.onNodeWithText("Ativar meu apoio").performScrollTo().performClick()
        verify(exactly = 1) { vm.savePlan("22/10/2026", "", "", emptySet(), any(), false, any()) }
    }
    @Test fun guestReadsHelpWithoutActivationOrSensitiveDiary() {
        val vm = vm(null)
        compose.setContent { BragasaudeTheme { SmokingSupportScreen(onBack = {}, viewModel = vm) } }
        compose.onNodeWithText("Preparar meu plano").assertDoesNotExist()
        compose.onNodeWithText("Registrar meu dia").assertDoesNotExist()
        compose.onNodeWithText("Apoio e tratamento gratuito").performScrollTo().assertIsDisplayed()
    }
    @Test fun dayRequiresExplicitAnswerAndUnknownPositiveQuantityRemainsBlank() {
        var response: Pair<Boolean, String>? = null
        compose.setContent {
            BragasaudeTheme {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    SmokingDayForm(null, today, false) { _, smoked, quantity -> response = smoked to quantity }
                }
            }
        }
        compose.onNodeWithText("Salvar meu dia").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Fumei", useUnmergedTree = true).performScrollTo().performClick()
        compose.onNodeWithText("Salvar meu dia").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(true to "", response) }
    }
    @Test fun profileActivityIsFullyVisibleOnNarrowScreenWithLargeFont() {
        val value = "Moderadamente ativo"
        compose.setContent {
            BragasaudeTheme {
                CompositionLocalProvider(LocalDensity provides Density(1f, 1.6f)) {
                    ProfileDataRow("Atividade Física", value)
                }
            }
        }
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(value).assertIsDisplayed().performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertTrue(layouts.isNotEmpty())
        assertFalse(layouts.single().hasVisualOverflow)
        assertFalse((0 until layouts.single().lineCount).any { layouts.single().isLineEllipsized(it) })
    }
}
