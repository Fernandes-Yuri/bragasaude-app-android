package br.com.bragasaude.ui.smoking

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.inspector.WindowInspector
import java.io.File
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
        screenshot("01-apresentacao")
        verify(exactly = 0) { vm.savePlan(any(), any(), any(), any(), any(), any(), any()) }
        compose.onNodeWithText("Preparar meu plano").performScrollTo().performClick()
        verify(exactly = 0) { vm.savePlan(any(), any(), any(), any(), any(), any(), any()) }
        compose.onNodeWithText("Ativar meu apoio").performScrollTo().assertIsDisplayed()
        screenshot("02-confirmacao-plano")
        compose.onNodeWithText("Ativar meu apoio").performClick()
        verify(exactly = 1) { vm.savePlan("22/10/2026", "", "", emptySet(), any(), false, any()) }
    }
    @Test fun guestReadsHelpWithoutActivationOrSensitiveDiary() {
        val vm = vm(null)
        compose.setContent { BragasaudeTheme { SmokingSupportScreen(onBack = {}, viewModel = vm) } }
        compose.onNodeWithText("Preparar meu plano").assertDoesNotExist()
        compose.onNodeWithText("Registrar meu dia").assertDoesNotExist()
        compose.onNodeWithTag("smokingContent").performScrollToNode(hasText("Apoio e tratamento gratuito"))
        compose.onNodeWithText("Apoio e tratamento gratuito").assertIsDisplayed()
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
        screenshot("03-atividade-fonte-ampliada")
        assertTrue(layouts.isNotEmpty())
        val layout = layouts.single()
        assertEquals(value.length, layout.getLineEnd(layout.lineCount - 1, visibleEnd = true))
        // Native glyph metrics can exceed rounded integer bounds by a fraction of a pixel.
        for (line in 0 until layout.lineCount) {
            assertTrue(layout.getLineRight(line) <= layout.size.width + 1f)
            assertTrue(layout.getLineBottom(line) <= layout.size.height + 1f)
        }
        assertFalse((0 until layouts.single().lineCount).any { layouts.single().isLineEllipsized(it) })
    }
    @Test fun planConfirmationFitsDarkThemeWithLargeFont() {
        val vm = vm()
        compose.setContent {
            BragasaudeTheme(forceThemeMode = "DARK") {
                CompositionLocalProvider(LocalDensity provides Density(1f, 1.4f)) {
                    SmokingSupportScreen(onBack = {}, viewModel = vm)
                }
            }
        }
        compose.onNodeWithText("Preparar meu plano").performScrollTo().performClick()
        compose.onNodeWithText("Ativar meu apoio").performScrollTo().assertIsDisplayed()
        screenshot("04-plano-escuro-fonte-ampliada")
    }
    @Test fun pauseNeverCreatesADayAndUnknownDatesDoNotCountAsAbstinence() {
        val vm = vm()
        every { vm.snapshot } returns MutableStateFlow(SmokingSupportSnapshot(
            ownerId = "owner", loaded = true,
            settings = SmokingSupportSettings("owner", br.com.bragasaude.domain.smoking.OfferDecision.ACCEPTED, true, 1, 1, 1),
            plan = SmokingSupportPlan("owner", "2026-10-20", stage = br.com.bragasaude.domain.smoking.PreparationStage.STARTED, createdAt = 1, updatedAt = 1),
            days = listOf(SmokingDailyEntry("owner", "2026-10-20", false, 0, "UTC", 1, 1))
        ))
        compose.setContent { BragasaudeTheme { SmokingSupportScreen(onBack = {}, viewModel = vm) } }
        compose.onNodeWithText("Dias registrados sem fumar: 1").assertIsDisplayed()
        screenshot("05-painel")
        compose.onNodeWithText("Estou com vontade agora").performScrollTo().performClick()
        screenshot("06-pausa")
        compose.onNodeWithText("Pular e encerrar").performScrollTo().performClick()
        verify(exactly = 0) { vm.saveDay(any(), any(), any(), any()) }
        verify(exactly = 0) { vm.recordCraving(any(), any()) }
    }
    @Test fun pauseOffersGuidanceAndSwitchingActivityDoesNotRecordAnything() {
        var recorded = false
        var closed = false
        compose.setContent {
            BragasaudeTheme(forceThemeMode = "LIGHT") {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    SmokingPause(null, false, onClose = { closed = true }, onRecord = { recorded = true })
                }
            }
        }
        compose.onNodeWithTag("pauseActivity-WATER").performScrollTo().performClick()
        compose.onNodeWithTag("currentPauseActivity").performScrollTo().assertTextEquals("Água em pequenos goles")
        compose.onNodeWithTag("pauseGuidance").assertTextContains("Se beber água agora", substring = true)
        compose.onNodeWithText("Começar pausa de 2 minutos").performScrollTo().performClick()
        compose.onNodeWithTag("pauseCountdown").assertIsDisplayed()
        compose.onNodeWithTag("pauseActivity-WALK").performScrollTo().performClick()
        compose.onNodeWithTag("currentPauseActivity").performScrollTo().assertTextEquals("Caminhar devagar")
        compose.onNodeWithTag("pauseGuidance").assertTextContains("trecho curto e plano", substring = true)
        screenshot("07-pausa-caminhada")
        compose.runOnIdle { assertFalse(recorded); assertFalse(closed) }
        compose.onNodeWithText("Encerrar pausa").performScrollTo().performClick()
        compose.runOnIdle { assertTrue(closed); assertFalse(recorded) }
    }
    @Test fun activityCardsAndGuidanceFitDarkThemeAndLargeFont() {
        compose.setContent {
            BragasaudeTheme(forceThemeMode = "DARK") {
                CompositionLocalProvider(LocalDensity provides Density(1f, 1.4f)) {
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                        SmokingPause(null, false, onClose = {}, onRecord = {})
                    }
                }
            }
        }
        compose.onNodeWithTag("pauseActivity-HANDS").performScrollTo().performClick()
        compose.onNodeWithTag("currentPauseActivity").performScrollTo().assertTextEquals("Ocupar as mãos")
        compose.onNodeWithTag("pauseGuidance").assertIsDisplayed()
        screenshot("08-pausa-escuro-fonte-ampliada")
    }
    private fun screenshot(name: String) {
        compose.runOnIdle {
            val windows = WindowInspector.getGlobalWindowViews()
            val view = windows.first()
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            windows.forEach { it.draw(canvas) }
            val file = File("build/outputs/smoking-review/$name.png")
            requireNotNull(file.parentFile).mkdirs()
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }

}
