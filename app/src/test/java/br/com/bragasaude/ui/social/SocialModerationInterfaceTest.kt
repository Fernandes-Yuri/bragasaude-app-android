package br.com.bragasaude.ui.social

import android.app.Application
import androidx.compose.runtime.mutableStateOf
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
class SocialModerationInterfaceTest {
    @get:Rule val compose = createComposeRule()

    @Test fun reportRequiresAReasonAndKeepsInputForRetryAfterFailure() {
        val state = mutableStateOf(SocialFeedViewModel.ReportState())
        val sent = mutableListOf<Pair<String, String>>()
        compose.setContent {
            BragasaudeTheme(forceThemeMode = "LIGHT") {
                ReportPostDialog(state.value, {}, { reason, details ->
                    sent += reason to details
                    state.value = SocialFeedViewModel.ReportState(sending = true)
                })
            }
        }
        compose.onNodeWithText("Enviar denúncia").assertIsNotEnabled()
        for (label in listOf("Ofensa ou desrespeito", "Informação médica prejudicial ou falsa", "Spam ou propaganda", "Outro motivo")) {
            compose.onNodeWithText(label).assertExists()
        }
        compose.onNodeWithText("Informação médica prejudicial ou falsa").performScrollTo().performClick()
        compose.onNode(hasSetTextAction()).performScrollTo().performTextInput("Informação perigosa")
        compose.onNodeWithText("Enviar denúncia").performClick()
        compose.onNodeWithText("Enviando denúncia...").assertIsNotEnabled()
        compose.onNodeWithText("Cancelar").assertIsNotEnabled()
        compose.runOnIdle { state.value = SocialFeedViewModel.ReportState(error = "Sem conexão. Tente novamente.") }
        compose.onNodeWithText("Sem conexão. Tente novamente.").assertExists()
        compose.onNodeWithText("Enviar denúncia").performClick()
        compose.runOnIdle {
            assertEquals(listOf("misinformation" to "Informação perigosa", "misinformation" to "Informação perigosa"), sent)
        }
    }

    @Test fun ownPostKeepsEditDeleteAndOtherAuthorsHaveReportBlock() {
        val owner = mutableStateOf(true)
        var reports = 0
        var blocks = 0
        compose.setContent {
            BragasaudeTheme(forceThemeMode = "LIGHT") {
                SocialPostMenu(
                    isOwner = owner.value, onEdit = {}, onDelete = {},
                    onReport = { reports++ }, onBlock = { blocks++ }
                )
            }
        }
        compose.onNodeWithContentDescription("Opções da publicação").performClick()
        compose.onNodeWithText("Editar publicação").assertIsDisplayed()
        compose.onNodeWithText("Excluir publicação").assertIsDisplayed()
        compose.onNodeWithText("Denunciar publicação").assertDoesNotExist()
        compose.onNodeWithText("Editar publicação").performClick()
        compose.runOnIdle { owner.value = false }
        compose.onNodeWithContentDescription("Opções da publicação").performClick()
        compose.onNodeWithText("Editar publicação").assertDoesNotExist()
        compose.onNodeWithText("Excluir publicação").assertDoesNotExist()
        compose.onNodeWithText("Bloquear usuário").assertIsDisplayed()
        compose.onNodeWithText("Denunciar publicação").performClick()
        compose.onNodeWithContentDescription("Opções da publicação").performClick()
        compose.onNodeWithText("Bloquear usuário").performClick()
        compose.runOnIdle { assertEquals(1, reports); assertEquals(1, blocks) }
    }
}
