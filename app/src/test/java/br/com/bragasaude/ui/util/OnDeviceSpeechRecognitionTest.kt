package br.com.bragasaude.ui.util

import android.speech.SpeechRecognizer
import org.junit.Assert.*
import org.junit.Test

class OnDeviceSpeechRecognitionTest {
    @Test fun `older Android and absent local service offer text explicitly`() {
        assertTrue(OnDeviceSpeechRecognition.unavailableReason(30, true)!!.contains("texto"))
        assertTrue(OnDeviceSpeechRecognition.unavailableReason(31, false)!!.contains("texto"))
        assertNull(OnDeviceSpeechRecognition.unavailableReason(31, true))
    }

    @Test fun `only installed Brazilian Portuguese or unspecified Portuguese passes language check`() {
        assertNull(OnDeviceSpeechRecognition.languageUnavailableReason(listOf("pt-BR", "en-US")))
        assertNull(OnDeviceSpeechRecognition.languageUnavailableReason(listOf("pt_BR")))
        assertNull(OnDeviceSpeechRecognition.languageUnavailableReason(listOf("pt")))
        assertNotNull(OnDeviceSpeechRecognition.languageUnavailableReason(listOf("pt-PT")))
        assertNotNull(OnDeviceSpeechRecognition.languageUnavailableReason(emptyList()))
    }

    @Test fun `missing package and technical failures never suggest remote recognition`() {
        listOf(SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED, SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE,
            SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_SERVER).forEach { code ->
            val message = OnDeviceSpeechRecognition.errorMessage(code)
            assertTrue(message.contains("texto"))
            assertFalse(message.contains("Tente offline"))
            assertFalse(message.contains("internet"))
        }
    }
}
