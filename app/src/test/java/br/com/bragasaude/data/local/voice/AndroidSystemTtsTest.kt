package br.com.bragasaude.data.local.voice

import android.content.Context
import io.mockk.mockk
import org.junit.Assert.assertFalse
import org.junit.Test

class AndroidSystemTtsTest {
    @Test
    fun `isAvailable is false when tts not initialized`() {
        val context = mockk<Context>(relaxed = true)
        val tts = AndroidSystemTts(context)
        assertFalse(tts.isAvailable)
    }
}
