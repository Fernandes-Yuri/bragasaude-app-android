package br.com.bragasaude.ui.voice

import br.com.bragasaude.data.remote.ai.NeuralAudioPlayer
import com.google.firebase.auth.FirebaseAuth
import io.mockk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class VoicePlaybackTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var player: NeuralAudioPlayer
    private lateinit var vm: VoiceHealthViewModel

    @Before fun setup() {
        Dispatchers.setMain(dispatcher)
        player = mockk(relaxed = true)
        val auth = mockk<FirebaseAuth>(relaxed = true)
        every { auth.currentUser } returns null
        vm = VoiceHealthViewModel(
            parser = mockk(relaxed = true), executor = mockk(relaxed = true),
            telemetryService = mockk(relaxed = true), profileDao = mockk(relaxed = true),
            auth = auth, localAiClient = mockk(relaxed = true), neuralAudioPlayer = player,
            vitalSignDao = mockk(relaxed = true), dailyMetricsDao = mockk(relaxed = true),
            notificationClient = mockk(relaxed = true), voiceProfileManager = mockk(relaxed = true),
            hybridOrchestrator = mockk(relaxed = true)
        )
    }

    @After fun cleanup() { vm.stopLiveMode(); Dispatchers.resetMain() }

    @Test fun `old audio completion cannot finish a newer response`() = runTest(dispatcher) {
        val finishes = mutableListOf<() -> Unit>()
        coEvery { player.playSpeech(any(), any(), any(), any()) } coAnswers {
            thirdArg<() -> Unit>().invoke()
            finishes.add(arg(3))
            true
        }
        var first = 0
        var second = 0
        vm.speak("Primeira resposta") { first++ }
        runCurrent()
        vm.speak("Segunda resposta") { second++ }
        runCurrent()
        finishes[0]()
        assertTrue(vm.isSpeaking.value)
        assertEquals(0, first)
        assertEquals(0, second)
        finishes[1]()
        assertFalse(vm.isSpeaking.value)
        assertEquals(1, second)
    }

    @Test fun `closing rejects a late playback callback`() = runTest(dispatcher) {
        var finish: (() -> Unit)? = null
        coEvery { player.playSpeech(any(), any(), any(), any()) } coAnswers { finish = arg(3); true }
        var done = 0
        vm.speak("Resposta") { done++ }
        runCurrent()
        vm.stopLiveMode()
        finish!!.invoke()
        advanceUntilIdle()
        assertEquals(0, done)
        assertFalse(vm.isLiveMode.value)
        assertEquals(VoiceUiState.Idle, vm.state.value)
    }

    @Test fun `closing during synthesis does not start offline fallback`() = runTest(dispatcher) {
        val pending = CompletableDeferred<Boolean>()
        coEvery { player.playSpeech(any(), any(), any(), any()) } coAnswers { pending.await() }
        var done = 0
        vm.speak("Resposta pendente") { done++ }
        runCurrent()
        vm.stopLiveMode()
        pending.complete(false)
        advanceUntilIdle()
        assertEquals(0, done)
        assertFalse(vm.isSpeaking.value)
        assertFalse(vm.isLiveMode.value)
    }
}
