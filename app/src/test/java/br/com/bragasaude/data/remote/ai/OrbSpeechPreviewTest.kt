package br.com.bragasaude.data.remote.ai

import org.junit.Assert.assertEquals
import org.junit.Test

class OrbSpeechPreviewTest {
    @Test fun partialJsonDoesNotExposeActionMetadata() {
        assertEquals("Olá", OrbSpeechPreview.extract("{\"fala\":\"Olá"))
        assertEquals("Olá", OrbSpeechPreview.extract("{\"fala\":\"Olá\",\"acao\":\"CONVERSA\"}"))
        assertEquals("", OrbSpeechPreview.extract("{\"acao\":\"CONVERSA\","))
    }

    @Test fun incompleteEscapesWaitForNextChunk() {
        assertEquals("Ol", OrbSpeechPreview.extract("{\"fala\":\"Ol\\u00"))
        assertEquals("Olá\n\"sim\"", OrbSpeechPreview.extract("{\"fala\":\"Ol\\u00e1\\n\\\"sim\\\""))
        assertEquals("Oi", OrbSpeechPreview.extract("{\"fala\":\"Oi\\"))
    }
}
