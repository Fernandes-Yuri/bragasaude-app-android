package br.com.bragasaude.ui.chat

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
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

    @Test fun returnToLatestCountsMessagesInsteadOfStreamingTokens() {
        val state = mutableStateOf(longConversation())
        compose.setContent { MaterialTheme { OrbChatContent(state.value) } }
        readPreviousMessages()
        compose.onNodeWithTag("chatReturnToLatest").assertIsDisplayed()
            .assertContentDescriptionEquals("Voltar ao fim")
        val before = scrollPosition()
        compose.runOnIdle { state.value = state.value.copy(isStreaming = true, partialText = "Uma resposta") }
        compose.onNodeWithTag("chatReturnToLatest").assertContentDescriptionEquals("1 nova mensagem · Voltar ao fim")
        repeat(3) { i ->
            compose.runOnIdle { state.value = state.value.copy(partialText = "Uma resposta que continua crescendo $i") }
            compose.onNodeWithTag("chatReturnToLatest").assertContentDescriptionEquals("1 nova mensagem · Voltar ao fim")
        }
        compose.runOnIdle { state.value = state.value.copy(isStreaming = false, partialText = "",
            messages = state.value.messages + ChatMessage(role = "assistant", text = "Resposta nova concluída.")) }
        compose.onNodeWithTag("chatReturnToLatest").assertContentDescriptionEquals("1 nova mensagem · Voltar ao fim")
        assertEquals(before, scrollPosition(), 0.5f)
        compose.onNodeWithTag("chatReturnToLatest").performClick()
        compose.onNodeWithTag("chatBottom").assertIsDisplayed()
        compose.onNodeWithText("Resposta nova concluída.").assertIsDisplayed()
        compose.onNodeWithTag("chatReturnToLatest").assertDoesNotExist()
        compose.runOnIdle { state.value = state.value.copy(isStreaming = true, partialText = "Agora sigo a resposta nova.") }
        compose.onNodeWithTag("chatBottom").assertIsDisplayed()
    }

    @Test fun ownMessageResumesFollowingAndOpeningConversationClearsUnread() {
        val state = mutableStateOf(longConversation())
        compose.setContent { MaterialTheme { OrbChatContent(state.value) } }
        readPreviousMessages()
        compose.runOnIdle { state.value = state.value.copy(messages = state.value.messages +
            ChatMessage(role = "assistant", text = "Mensagem recebida durante leitura.")) }
        compose.onNodeWithTag("chatReturnToLatest").assertContentDescriptionEquals("1 nova mensagem · Voltar ao fim")
        compose.runOnIdle { state.value = state.value.copy(messages = state.value.messages +
            ChatMessage(role = "user", text = "Minha nova pergunta.")) }
        compose.onNodeWithTag("chatBottom").assertIsDisplayed()
        compose.onNodeWithText("Minha nova pergunta.").assertIsDisplayed()
        readPreviousMessages()
        compose.runOnIdle { state.value = state.value.copy(showHistory = true) }
        compose.onNodeWithTag("chatReturnToLatest").assertDoesNotExist()
        compose.runOnIdle { state.value = longConversation().copy(messages = listOf(
            ChatMessage(role = "user", text = "Pergunta de outra conversa."),
            ChatMessage(role = "assistant", text = "Resposta de outra conversa."))) }
        compose.onNodeWithText("Resposta de outra conversa.").assertIsDisplayed()
        compose.onNodeWithTag("chatReturnToLatest").assertDoesNotExist()
    }

    @Test fun largeFontKeepsManualPositionAndReturnControlAccessible() {
        val state = mutableStateOf(longConversation().copy(textScale = 1.5f))
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 1.3f)) {
                MaterialTheme { OrbChatContent(state.value) }
            }
        }
        compose.onNodeWithTag("chatBottom").assertIsDisplayed()
        readPreviousMessages()
        val before = scrollPosition()
        compose.runOnIdle { state.value = state.value.copy(isStreaming = true,
            partialText = (1..80).joinToString("\n") { "Detalhe $it de uma resposta extensa." }) }
        compose.onNodeWithTag("chatReturnToLatest").assertIsDisplayed().assertHasClickAction()
            .assertContentDescriptionEquals("1 nova mensagem · Voltar ao fim")
        assertEquals(before, scrollPosition(), 0.5f)
        compose.onNodeWithTag("chatReturnToLatest").performClick()
        compose.onNodeWithTag("chatBottom").assertIsDisplayed()
    }

    @Test fun keyboardResizeKeepsFollowingAndPreservesManualReading() {
        val state = mutableStateOf(longConversation().copy(textScale = 1.5f))
        var keyboardHeight = 0
        compose.setContent {
            val density = LocalDensity.current
            keyboardHeight = WindowInsets.ime.getBottom(density)
            MaterialTheme { OrbChatContent(state.value, onInput = { state.value = state.value.copy(input = it) }) }
        }
        compose.onNodeWithTag("chatInput").performClick()
        compose.waitUntil(timeoutMillis = 10_000) { keyboardHeight > 0 }
        compose.onNodeWithTag("chatBottom").assertIsDisplayed()
        readPreviousMessages()
        val before = scrollPosition()
        compose.runOnIdle { state.value = state.value.copy(messages = state.value.messages +
            ChatMessage(role = "assistant", text = "Resposta recebida com o teclado aberto.")) }
        compose.onNodeWithTag("chatReturnToLatest").assertIsDisplayed()
        assertEquals(before, scrollPosition(), 0.5f)
        compose.onNodeWithTag("chatReturnToLatest").performClick()
        compose.onNodeWithTag("chatBottom").assertIsDisplayed()
    }

    private fun longConversation() = OrbChatUiState(showHistory = false,
        messages = (1..40).map { ChatMessage(role = if (it % 2 == 0) "assistant" else "user",
            text = "Mensagem $it.\nDetalhes da conversa anterior.") })

    private fun readPreviousMessages() {
        compose.waitForIdle()
        compose.onNodeWithTag("chatMessages").performTouchInput { swipeDown(durationMillis = 600) }
        compose.waitForIdle()
        compose.onNodeWithTag("chatBottom").assertIsNotDisplayed()
    }

    private fun scrollPosition() = compose.onNodeWithTag("chatMessages").fetchSemanticsNode()
        .config[SemanticsProperties.VerticalScrollAxisRange].value()
}
