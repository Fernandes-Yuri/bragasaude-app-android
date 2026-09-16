package br.com.bragasaude.ui.chat

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
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
}
