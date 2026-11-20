package br.com.bragasaude.ui.b2b

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.inspector.WindowInspector
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import br.com.bragasaude.domain.b2b.PartnerInvitePreviewDto
import br.com.bragasaude.domain.b2b.ProgramExperienceConfigDto
import br.com.bragasaude.ui.auth.AuthViewModel
import br.com.bragasaude.ui.auth.LoginScreen
import br.com.bragasaude.ui.navigation.PartnerInviteNavigation
import br.com.bragasaude.ui.theme.BragasaudeTheme
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Before
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
class PartnerBrandAppearanceTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private val prefs get() = context.getSharedPreferences("braga_prefs", Context.MODE_PRIVATE)
    private lateinit var auth: AuthViewModel
    private val invite = PartnerInvitePreviewDto("preview", "Horizonte", "Horizonte Cuida",
        primaryColor = "#6750A4", inviteCode = "HORI-TEST")

    @Before fun setup() {
        prefs.edit().clear().commit()
        PartnerInviteNavigation.pendingInviteCode.value = null
        auth = mockk(relaxed = true)
        every { auth.authState } returns MutableStateFlow(AuthViewModel.AuthState.Idle)
        coEvery { auth.lookupPartnerInvitePreview(any()) } returns invite
    }

    private fun login(dark: Boolean = false, fontScale: Float = 1f) {
        compose.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, fontScale)) {
                BragasaudeTheme(forceThemeMode = if (dark) "DARK" else "LIGHT") {
                    LoginScreen(viewModel = auth, onLoginSuccess = {})
                }
            }
        }
    }

    @Test fun neutralLoginPrioritizesAccessAndKeepsOnlyOneFooterCredit() {
        login()
        compose.onNodeWithText("Acesse sua conta").assertIsDisplayed()
        compose.onNodeWithText("Braga Saúde").assertDoesNotExist()
        compose.onNodeWithTag("login_button").assertIsDisplayed()
        screenshot("01-login-neutro")
        compose.onNodeWithText("E-mail").performTextInput("person@example.test")
        compose.onNodeWithText("Senha", substring = false).performTextInput("example-password")
        compose.onNodeWithTag("login_button").performClick()
        verify { auth.signInWithEmail("person@example.test", "example-password") }
        compose.onNodeWithTag("login_content").performScrollToNode(hasTestTag("technology_credit"))
        compose.onNodeWithTag("technology_credit").assertIsDisplayed()
        compose.onAllNodesWithText("Tecnologia Braga Saúde").assertCountEquals(1)
        screenshot("02-login-rodape")
    }

    @Test fun companyShortcutKeepsBusinessSignupAndDiscreetBranding() {
        login()
        compose.onNodeWithTag("company_shortcut_button").performScrollTo().performClick()
        // Entrada única e neutra: o modo empresarial aparece como escolha de uso,
        // sem nome, logo ou cor de parceiro antes da autenticação.
        compose.onNodeWithText("Para minha empresa").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Preparar programa", substring = true).performScrollTo().assertIsDisplayed()
        screenshot("03-cadastro-empresa")
        compose.onNodeWithText("E-mail").performScrollTo().performTextInput("owner@example.test")
        compose.onNodeWithText("Senha", substring = false).performScrollTo().performTextInput("example-password")
        compose.onNodeWithText("Confirmar senha", substring = false).performScrollTo().performTextInput("example-password")
        compose.onNodeWithTag("login_button").performScrollTo().performClick()
        verify { auth.signUpWithEmail("owner@example.test", "example-password", businessAccount = true) }
    }

    // Robolectric #8460: Dialog + TextField oscila acima de w320dp. Demais capturas usam w360dp.
    // https://github.com/robolectric/robolectric/issues/8460
    @Config(qualifiers = "w320dp-h800dp-mdpi")
    @Test fun storedInviteCodeKeepsLoginNeutralAndPreservesProgramForAfterSignIn() {
        prefs.edit().putString("pending_b2b_invite_code", invite.inviteCode)
            .putBoolean("pending_b2b_company_onboarding", true).commit()
        login()
        // Login 100% neutro: nenhum nome/logo de parceiro antes da autenticação.
        compose.onNodeWithText("Acesse sua conta").assertIsDisplayed()
        compose.onNodeWithText("Horizonte").assertDoesNotExist()
        compose.onNodeWithText("Horizonte Cuida", substring = false).assertDoesNotExist()
        // O convite é guardado para conferência após o login, sem forçar cadastro empresarial.
        assertEquals(invite.inviteCode, prefs.getString("pending_b2b_invite_code", null))
        assertFalse(prefs.getBoolean("pending_b2b_company_onboarding", false))
        screenshot("04-login-neutro-convite-guardado")
    }

    @Test fun darkLoginAndLargeTypeRemainScrollableThroughLegalAndCredit() {
        login(dark = true, fontScale = 1.5f)
        compose.onNodeWithText("Acesse sua conta").assertIsDisplayed()
        screenshot("05-login-escuro-fonte-ampliada")
        compose.onNodeWithTag("login_button").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("login_content").performScrollToNode(hasTestTag("technology_credit"))
        compose.onNodeWithTag("technology_credit").assertIsDisplayed()
        compose.onNodeWithText("Privacidade").assertIsDisplayed()
        screenshot("08-login-escuro-rodape")
    }

    @Test fun studioEntryPreviewStaysNeutralEvenWithPartnerBrandConfigured() {
        compose.setContent {
            BragasaudeTheme(forceThemeMode = "LIGHT") {
                PartnerBrandTheme(invite.primaryColor) {
                    val colors = partnerBrandColors()
                    PartnerPreviewTabContent(invite.programName, invite.companyName, null, invite.primaryColor,
                        ProgramExperienceConfigDto(), colors.primary, colors.onPrimary, colors.accent,
                        colors.softSurface, BorderStroke(1.dp, colors.softBorder), {})
                }
            }
        }
        compose.onNodeWithText("Entrada", substring = false).assertIsDisplayed()
        // A prévia de entrada é a mesma tela neutra do login real: sem marca do parceiro.
        compose.onNodeWithText("Acesse sua conta").assertIsDisplayed()
        compose.onNodeWithText("Horizonte").assertDoesNotExist()
        screenshot("06-estudio-previa-entrada-neutra")
        compose.onNodeWithText("Ver prévia em modo escuro").performClick()
        screenshot("07-estudio-previa-escura")
    }

    @Test fun partnerPaletteKeepsContrastSemanticErrorsAndAppThemePreference() {
        var color by mutableStateOf("#FFEB3B")
        var dark by mutableStateOf(false)
        var result: PartnerBrandColors? = null
        var background = Color.Unspecified
        var errorBefore = Color.Unspecified
        var errorAfter = Color.Unspecified
        compose.setContent {
            BragasaudeTheme(forceThemeMode = if (dark) "DARK" else "LIGHT") {
                errorBefore = MaterialTheme.colorScheme.error
                PartnerBrandTheme(color) {
                    result = partnerBrandColors()
                    background = MaterialTheme.colorScheme.background
                    errorAfter = MaterialTheme.colorScheme.error
                }
            }
        }
        for (mode in listOf(false, true)) {
            for (hex in listOf("#FFEB3B", "#FFFFFF", "#000000", "#6750A4", "#00695C")) {
                compose.runOnIdle { dark = mode; color = hex }
                compose.waitForIdle()
                compose.runOnIdle {
                    val palette = requireNotNull(result)
                    assertTrue(contrastRatio(palette.primary, palette.onPrimary) >= 4.5f)
                    assertEquals(mode, background.luminance() < 0.5f)
                    assertEquals(errorBefore, errorAfter)
                    assertTrue(contrastRatio(readableBrandAccent(palette.primary, palette.softSurface), palette.softSurface) >= 4.5f)
                }
            }
        }
    }

    private fun screenshot(name: String) = compose.runOnIdle {
        val windows = WindowInspector.getGlobalWindowViews()
        val view = windows.first()
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        windows.forEach { it.draw(canvas) }
        val file = File("build/outputs/b2b-brand-review/$name.png")
        requireNotNull(file.parentFile).mkdirs()
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}
