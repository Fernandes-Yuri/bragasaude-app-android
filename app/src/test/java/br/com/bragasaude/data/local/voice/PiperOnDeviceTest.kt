package br.com.bragasaude.data.local.voice

import android.content.Context
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class PiperOnDeviceTest {

    private val testDispatcher = StandardTestDispatcher()
    private val context = mockk<Context>(relaxed = true)
    private val pcmPlayer = mockk<PcmStreamAudioPlayer>(relaxed = true)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `piper engine reports missing model files gracefully without crashing`() {
        val engine = PiperOnDeviceEngine(context, pcmPlayer)
        engine.customModelDir = File(System.getProperty("java.io.tmpdir"), "piper_missing_${System.currentTimeMillis()}")
        assertFalse(engine.hasModelFiles())
        assertFalse(engine.isAvailable)
    }

    @Test
    fun `piper engine returns false when attempting to stream without model files`() = runTest(testDispatcher) {
        val engine = PiperOnDeviceEngine(context, pcmPlayer)
        engine.customModelDir = File(System.getProperty("java.io.tmpdir"), "piper_missing_${System.currentTimeMillis()}")
        var startCalled = false
        var doneCalled = false

        val success = engine.playStream(
            text = "Teste de sintese local",
            onStart = { startCalled = true },
            onDone = { doneCalled = true }
        )

        assertFalse(success)
        assertFalse(startCalled)
        assertFalse(doneCalled)
    }

    @Test
    fun `pcm stream audio player handles stop cleanly`() {
        pcmPlayer.stop()
        verify(exactly = 1) { pcmPlayer.stop() }
    }

    @Test
    fun `piper engine stop delegates to pcm stream audio player`() {
        val engine = PiperOnDeviceEngine(context, pcmPlayer)
        engine.stop()
        verify(exactly = 1) { pcmPlayer.stop() }
    }
}
