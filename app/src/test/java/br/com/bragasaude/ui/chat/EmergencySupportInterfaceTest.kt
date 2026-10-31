package br.com.bragasaude.ui.chat

import android.app.Application
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.inspector.WindowInspector
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import br.com.bragasaude.ai.BragaRoutingPolicy
import br.com.bragasaude.ui.components.EmergencyActionButtons
import br.com.bragasaude.ui.theme.BragasaudeTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w320dp-h740dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class EmergencySupportInterfaceTest {
    @get:Rule val compose = createComposeRule()

    @Test fun crisisCardDialsBothServicesInOneTapWithoutConfirmation() {
        compose.setContent {
            BragasaudeTheme {
                ChatBubble(ChatMessage(role = "assistant", text = "Você merece cuidado e apoio.",
                    emergencyType = BragaRoutingPolicy.MENTAL_HEALTH_EMERGENCY),
                    emergencyActions = { EmergencyActionButtons(it, "11999990000", "Maria") })
            }
        }
        compose.onNodeWithText("Confirmar").assertDoesNotExist()
        val cvv = compose.onNodeWithTag("emergencyDial188").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val samu = compose.onNodeWithTag("emergencyDial192").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertTrue(cvv.bottom <= samu.top)
        compose.onNodeWithTag("emergencyDial188").performClick()
        assertDial("tel:188")
        compose.onNodeWithTag("emergencyDial192").performClick()
        assertDial("tel:192")
        compose.onNodeWithText("Ligar para Maria").performClick()
        assertDial("tel:11999990000")
        compose.onNodeWithText("Localizar UPA mais próxima").assertHasClickAction().performClick()
        val maps = shadowOf(RuntimeEnvironment.getApplication()).nextStartedActivity
        assertEquals(Intent.ACTION_VIEW, maps.action)
        assertTrue(maps.dataString!!.startsWith("geo:"))
        screenshot("socorro-chat")
    }

    @Test fun physicalEmergencyFocusesSamuAndLargeTextFitsButtons() {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.5f)) {
                BragasaudeTheme {
                    Box(Modifier.width(280.dp).padding(16.dp)) {
                        EmergencyActionButtons(mentalHealthFirst = false, requestInitialFocus = true)
                    }
                }
            }
        }
        compose.onNodeWithTag("emergencyDial192").assertIsFocused().assertIsDisplayed()
        compose.onNodeWithTag("emergencyDial188").assertIsDisplayed()
        val cvv = compose.onNodeWithTag("emergencyDial188").fetchSemanticsNode().boundsInRoot
        val samu = compose.onNodeWithTag("emergencyDial192").fetchSemanticsNode().boundsInRoot
        assertTrue(samu.bottom <= cvv.top)
        screenshot("socorro-fonte-ampliada")
    }

    @Test fun returnControlHasItsOwnSpaceBelowMessageViewport() {
        compose.setContent {
            BragasaudeTheme {
                OrbChatContent(OrbChatUiState(showHistory = false, textScale = 1.5f,
                    messages = (1..30).map { ChatMessage(role = "assistant", text = "Mensagem $it. Seu apoio está aqui.") }))
            }
        }
        compose.onNodeWithTag("chatMessages").performTouchInput { swipeDown(durationMillis = 600) }
        val control = compose.onNodeWithTag("chatReturnToLatest").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val viewport = compose.onNodeWithTag("chatMessages").fetchSemanticsNode().boundsInRoot
        assertTrue("O botão deve ficar abaixo da área de leitura", viewport.bottom <= control.top)
        compose.onNodeWithTag("chatReturnToLatest").performClick()
        compose.onNodeWithTag("chatBottom").assertIsDisplayed()
    }

    private fun assertDial(uri: String) {
        val intent = shadowOf(RuntimeEnvironment.getApplication()).nextStartedActivity
        assertEquals(Intent.ACTION_DIAL, intent.action)
        assertEquals(uri, intent.dataString)
    }

    private fun screenshot(name: String) = compose.runOnIdle {
        val view = WindowInspector.getGlobalWindowViews().first()
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        val file = File("build/outputs/ui-review/$name.png")
        file.parentFile.mkdirs()
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}
