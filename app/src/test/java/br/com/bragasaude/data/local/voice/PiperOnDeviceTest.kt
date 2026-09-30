package br.com.bragasaude.data.local.voice

import android.content.Context
import br.com.bragasaude.data.remote.ai.NeuralAudioPlayer
import br.com.bragasaude.data.remote.auth.AuthService
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
    private val authService = mockk<AuthService>(relaxed = true)
    private val pcmPlayer = mockk<PcmStreamAudioPlayer>(relaxed = true)
    private val piperEngine = mockk<PiperOnDeviceEngine>(relaxed = true)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { context.filesDir } returns File(System.getProperty("java.io.tmpdir"), "piper_test")
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `piper engine reports missing model files gracefully without crashing`() {
        val engine = PiperOnDeviceEngine(context, pcmPlayer)
        assertFalse(engine.hasModelFiles())
        assertFalse(engine.isAvailable)
    }

    @Test
    fun `neural audio player prioritizes on-device piper synthesis when available`() = runTest(testDispatcher) {
        every { piperEngine.hasModelFiles() } returns true
        coEvery { piperEngine.playStream(any(), any(), any(), any()) } coAnswers {
            thirdArg<() -> Unit>().invoke() // onStart
            lastArg<() -> Unit>().invoke()  // onDone
            true
        }

        val player = NeuralAudioPlayer(context, authService, piperEngine)
        var started = false
        var done = false

        val played = player.playSpeech(
            text = "Lembrete: hora do seu medicamento.",
            isMale = true,
            onStart = { started = true },
            onDone = { done = true }
        )

        advanceUntilIdle()
        assertTrue(played)
        assertTrue(started)
        assertTrue(done)
        coVerify(exactly = 1) { piperEngine.playStream(any(), any(), any(), any()) }
    }

    @Test
    fun `stop in neural audio player cancels on-device synthesis`() {
        val player = NeuralAudioPlayer(context, authService, piperEngine)
        player.stop()
        verify(exactly = 1) { piperEngine.stop() }
    }
}
