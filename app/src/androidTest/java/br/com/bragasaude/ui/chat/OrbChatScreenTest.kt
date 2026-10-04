package br.com.bragasaude.ui.chat

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.semantics.SemanticsProperties
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertEquals

class OrbChatScreenTest {
    @get:Rule val compose = createComposeRule()
    @Test fun test_messages_display() {
        compose.setContent { MaterialTheme { OrbChatContent(OrbChatUiState(showHistory = false, messages = listOf(
            ChatMessage(role = "user", text = "Minha pergunta"), ChatMessage(role = "assistant", text = "Minha resposta")))) } }
        compose.onNodeWithText("Minha pergunta").assertIsDisplayed()
        compose.onNodeWithText("Minha resposta").assertIsDisplayed()
    }
    @Test fun test_input_sendsMessage() {
        var sent = ""
        compose.setContent { MaterialTheme { OrbChatContent(OrbChatUiState(showHistory = false, input = "Olá"), onSend = { sent = it }) } }
        compose.onNodeWithContentDescription("Enviar mensagem").performClick()
        assertEquals("Olá", sent)
    }
    @Test fun test_typingIndicator_shows() {
        compose.setContent { MaterialTheme { OrbChatContent(OrbChatUiState(showHistory = false, isStreaming = true)) } }
        compose.onNodeWithTag("typingIndicator").assertIsDisplayed()
    }
    @Test fun test_streamingText_updates() {
        val state = mutableStateOf(OrbChatUiState(showHistory = false, isStreaming = true, partialText = "Olá"))
        compose.setContent { MaterialTheme { OrbChatContent(state.value) } }
        compose.onNodeWithTag("streamingText").assertTextEquals("Olá")
        compose.runOnIdle { state.value = state.value.copy(partialText = "Olá, tudo bem?") }
        compose.onNodeWithTag("streamingText").assertTextEquals("Olá, tudo bem?")
    }
    @Test fun test_error_showsSnackbar() {
        compose.setContent { MaterialTheme { OrbChatContent(OrbChatUiState(showHistory = false, error = "Sem conexão")) } }
        compose.onNodeWithText("Sem conexão").assertIsDisplayed()
    }

    @Test fun streamingLongerThanViewportKeepsBottomVisible() {
        val state = mutableStateOf(OrbChatUiState(showHistory = false, isStreaming = true,
            messages = listOf(ChatMessage(role = "user", text = "Pergunta recente"))))
        compose.setContent { MaterialTheme { OrbChatContent(state.value) } }
        compose.runOnIdle { state.value = state.value.copy(partialText = (1..80).joinToString("\n") { "Detalhe $it da resposta em streaming." }) }
        compose.waitForIdle()
        compose.onNodeWithTag("chatBottom").assertIsDisplayed()
        compose.runOnIdle { state.value = state.value.copy(isStreaming = false, partialText = "",
            messages = state.value.messages + ChatMessage(role = "assistant", text = "Última resposta.")) }
        compose.onNodeWithText("Última resposta.").assertIsDisplayed()
    }

    @Test fun manualReadingIsNotInterruptedByStreaming() {
        val state = mutableStateOf(OrbChatUiState(showHistory = false, isStreaming = true,
            messages = (1..40).map { ChatMessage(role = if (it % 2 == 0) "assistant" else "user", text = "Mensagem $it.\nDetalhes da conversa anterior.") }))
        compose.setContent { MaterialTheme { OrbChatContent(state.value) } }
        compose.waitForIdle()
        compose.onNodeWithTag("chatMessages").performTouchInput { swipeDown(durationMillis = 600) }
        compose.waitForIdle()
        compose.onNodeWithTag("chatBottom").assertIsNotDisplayed()
        fun position() = compose.onNodeWithTag("chatMessages").fetchSemanticsNode()
            .config[SemanticsProperties.VerticalScrollAxisRange].value()
        val before = position()
        compose.runOnIdle { state.value = state.value.copy(partialText = "Uma resposta nova crescendo enquanto você lê.") }
        compose.waitForIdle()
        assertEquals(before, position(), 0.5f)
        compose.onNodeWithTag("chatBottom").assertIsNotDisplayed()
    }
}
