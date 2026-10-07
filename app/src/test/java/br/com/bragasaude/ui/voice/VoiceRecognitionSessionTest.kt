package br.com.bragasaude.ui.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.SpeechRecognizer
import br.com.bragasaude.ai.*
import br.com.bragasaude.data.remote.ai.NeuralAudioPlayer
import br.com.bragasaude.domain.VoiceHealthIntent
import br.com.bragasaude.domain.VoiceHealthParser
import br.com.bragasaude.ui.util.OnDeviceRecognitionUnavailable
import br.com.bragasaude.ui.util.OnDeviceSpeechRecognition
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import io.mockk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class VoiceRecognitionSessionTest {
    @Test fun `unknown speech reaches cloud only after local parser fails`() = runTest(dispatcher) {
        every { hybrid.analyze(any(), InputChannel.VOICE, any(), any(), any()) } returns
            NluOutput("entrada_sem_clareza", "Repita")
        every { hybrid.respond(any(), any(), any(), any(), any()) } returns
            kotlinx.coroutines.flow.flowOf(BragaHybridEvent.Completed("Resposta contextual da API"))
        vm.startListening(context)
        runCurrent()
        listeners.single().onResults(result("tem certeza disso"))
        runCurrent()
        verify(exactly = 1) { parser.parse("tem certeza disso", any(), any()) }
        verify(exactly = 1) { hybrid.respond("tem certeza disso", any(), InputChannel.VOICE, "conta-a",
            match { it?.delegarParaNuvem == true && it.fallbackFromIntent == "entrada_sem_clareza" }) }
    }

    @Test fun `emergency speech bypasses parser and remote fallback`() = runTest(dispatcher) {
        every { hybrid.analyze(any(), InputChannel.VOICE, any(), any(), any()) } returns
            BragaNluEngine.analisar("não consigo respirar")
        vm.startListening(context)
        runCurrent()
        listeners.single().onResults(result("não consigo respirar"))
        runCurrent()
        verify(exactly = 0) { parser.parse(any(), any(), any()) }
        verify(exactly = 0) { hybrid.respond(any(), any(), any(), any(), any()) }
        assertTrue((vm.state.value as VoiceUiState.Saved).summary.contains("SAMU 192"))
    }

    private val dispatcher = StandardTestDispatcher()
    private val listeners = mutableListOf<RecognitionListener>()
    private lateinit var recognizer: SpeechRecognizer
    private lateinit var context: Context
    private lateinit var auth: FirebaseAuth
    private lateinit var user: FirebaseUser
    private lateinit var authListener: CapturingSlot<FirebaseAuth.AuthStateListener>
    private lateinit var player: NeuralAudioPlayer
    private lateinit var focus: VoicePlaybackFocus
    private lateinit var hybrid: BragaHybridOrchestrator
    private lateinit var parser: VoiceHealthParser
    private lateinit var vm: VoiceHealthViewModel

    @Before fun setup() {
        Dispatchers.setMain(dispatcher)
        mockkObject(OnDeviceSpeechRecognition)
        context = mockk(relaxed = true)
        every { context.applicationContext } returns context
        recognizer = mockk(relaxed = true)
        every { recognizer.setRecognitionListener(capture(listeners)) } just Runs
        every { OnDeviceSpeechRecognition.create(any()) } returns recognizer
        every { OnDeviceSpeechRecognition.intent(any()) } returns mockk<Intent>(relaxed = true)
        coEvery { OnDeviceSpeechRecognition.checkPortugueseSupport(any(), any(), any()) } just Runs
        auth = mockk(relaxed = true)
        user = mockk(relaxed = true)
        every { user.uid } returns "conta-a"
        every { auth.currentUser } returns user
        authListener = slot()
        every { auth.addAuthStateListener(capture(authListener)) } just Runs
        player = mockk(relaxed = true)
        coEvery { player.playSpeech(any(), any(), any(), any()) } returns true
        focus = mockk(relaxed = true)
        every { focus.request(any(), any()) } returns true
        hybrid = mockk(relaxed = true)
        every { hybrid.analyze(any(), InputChannel.VOICE, any(), any(), any()) } returns
            NluOutput("saudacao", "Olá, como posso ajudar?")
        parser = mockk(relaxed = true)
        every { parser.parse(any(), any(), any()) } returns VoiceHealthIntent.Unknown("fala", "detalhe")
        vm = VoiceHealthViewModel(parser, mockk(relaxed = true), mockk(relaxed = true),
            mockk(relaxed = true), auth, mockk(relaxed = true), player, mockk(relaxed = true),
            mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true), hybrid, focus)
    }

    @After fun cleanup() {
        vm.stopLiveMode()
        unmockkObject(OnDeviceSpeechRecognition)
        Dispatchers.resetMain()
    }

    private fun result(text: String): Bundle = mockk<Bundle>().also {
        every { it.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION) } returns arrayListOf(text)
    }

    @Test fun `interruption rejects transcription and callbacks from the destroyed listener`() = runTest(dispatcher) {
        vm.startListening(context)
        runCurrent()
        val old = listeners.single()
        vm.interruptAndListen(context)
        runCurrent()
        old.onResults(result("fala antiga"))
        old.onPartialResults(result("transcrição antiga"))
        old.onRmsChanged(20f)
        runCurrent()
        assertEquals("", vm.liveTranscription.value)
        assertEquals(-2f, vm.audioRmsDb.value)
        verify(exactly = 0) { hybrid.analyze(any(), any(), any(), any(), any()) }
        listeners.last().onResults(result("olá"))
        runCurrent()
        verify(exactly = 1) { hybrid.analyze("olá", InputChannel.VOICE, any(), "conta-a", any()) }
        verify(atLeast = 1) { recognizer.destroy() }
    }

    @Test fun `interruption cancels streaming and its queued second phrase`() = runTest(dispatcher) {
        val decision = NluOutput("duvida_complexa_saude", null, delegarParaNuvem = true)
        every { hybrid.analyze(any(), any(), any(), any(), any()) } returns decision
        coEvery { hybrid.respond(any(), any(), any(), any(), any()) } returns flow {
            emit(BragaHybridEvent.Delta("Primeira frase. Segunda frase. "))
            awaitCancellation()
        }
        var oldFinished: (() -> Unit)? = null
        coEvery { player.playSpeech(any(), any(), any(), any()) } coAnswers {
            thirdArg<() -> Unit>().invoke()
            oldFinished = arg(3)
            true
        }
        vm.startListening(context)
        runCurrent()
        listeners.single().onResults(result("por que minha pressão afeta os rins?"))
        runCurrent()
        assertTrue(vm.isSpeaking.value)
        assertNotNull(oldFinished)
        vm.interruptAndListen(context)
        runCurrent()
        oldFinished!!.invoke()
        runCurrent()
        assertEquals("", vm.partialResponse.value)
        assertFalse(vm.isSpeaking.value)
        assertEquals(VoiceUiState.Listening, vm.state.value)
        coVerify(exactly = 1) { player.playSpeech(any(), any(), any(), any()) }
        coVerify(exactly = 1) { hybrid.respond(any(), any(), InputChannel.VOICE, "conta-a", decision) }
        verify(exactly = 0) { parser.parse(any(), any(), any()) }
    }

    @Test fun `account switch stops audio and ignores the previous account result`() = runTest(dispatcher) {
        var finish: (() -> Unit)? = null
        var completed = 0
        coEvery { player.playSpeech(any(), any(), any(), any()) } coAnswers {
            thirdArg<() -> Unit>().invoke()
            finish = arg(3)
            true
        }
        vm.startListening(context)
        runCurrent()
        val previous = listeners.single()
        vm.speak("Registro da conta anterior") { completed++ }
        runCurrent()
        every { user.uid } returns "conta-b"
        authListener.captured.onAuthStateChanged(auth)
        runCurrent()
        finish!!.invoke()
        previous.onResults(result("quanto foi minha pressão"))
        runCurrent()
        assertEquals(0, completed)
        assertFalse(vm.isLiveMode.value)
        assertFalse(vm.isSpeaking.value)
        assertEquals(VoiceUiState.Idle, vm.state.value)
        verify(exactly = 0) { hybrid.analyze(any(), any(), any(), any(), any()) }
    }

    @Test fun `host stop prevents late audio completion and clears microphone`() = runTest(dispatcher) {
        vm.startListening(context)
        runCurrent()
        val previous = listeners.single()
        vm.onHostStopped()
        previous.onResults(result("olá"))
        previous.onError(SpeechRecognizer.ERROR_NO_MATCH)
        advanceUntilIdle()
        assertFalse(vm.isLiveMode.value)
        assertEquals(VoiceUiState.Idle, vm.state.value)
        verify(exactly = 1) { OnDeviceSpeechRecognition.create(any()) }
    }

    @Test fun `absent on device service offers text without restarting recognition`() = runTest(dispatcher) {
        every { OnDeviceSpeechRecognition.create(any()) } throws
            OnDeviceRecognitionUnavailable("Voz no dispositivo indisponível. Continue pelo texto.")
        vm.startListening(context)
        advanceUntilIdle()
        assertFalse(vm.isLiveMode.value)
        val error = vm.state.value as VoiceUiState.Error
        assertTrue(error.message.contains("texto"))
        assertFalse(error.retryable)
        verify(exactly = 1) { OnDeviceSpeechRecognition.create(any()) }
        coVerify(exactly = 0) { hybrid.respond(any(), any(), any(), any(), any()) }
    }

    @Test fun `missing installed language rejects listening before microphone start`() = runTest(dispatcher) {
        coEvery { OnDeviceSpeechRecognition.checkPortugueseSupport(any(), any(), any()) } throws
            OnDeviceRecognitionUnavailable("Português não instalado. Continue pelo texto.")
        vm.startListening(context)
        advanceUntilIdle()
        assertTrue(vm.state.value is VoiceUiState.Error)
        assertFalse(vm.isLiveMode.value)
        verify(exactly = 0) { recognizer.startListening(any()) }
        verify(atLeast = 1) { recognizer.destroy() }
    }

    @Test fun `language error terminates local capture without retry loops`() = runTest(dispatcher) {
        every { OnDeviceSpeechRecognition.errorMessage(any()) } returns "Idioma indisponível. Continue pelo texto."
        vm.startListening(context)
        runCurrent()
        listeners.single().onError(SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE)
        advanceUntilIdle()
        assertFalse(vm.isLiveMode.value)
        assertTrue(vm.state.value is VoiceUiState.Error)
        verify(exactly = 1) { OnDeviceSpeechRecognition.create(any()) }
    }

    @Test fun `loss of playback focus cancels conversation rather than automatically resuming`() = runTest(dispatcher) {
        var lost: (() -> Unit)? = null
        every { focus.request(any(), any()) } answers { lost = secondArg(); true }
        vm.startListening(context)
        runCurrent()
        vm.speak("Resposta em voz")
        runCurrent()
        lost!!.invoke()
        advanceUntilIdle()
        assertFalse(vm.isLiveMode.value)
        assertFalse(vm.isSpeaking.value)
        assertEquals(VoiceUiState.Idle, vm.state.value)
        verify(exactly = 1) { OnDeviceSpeechRecognition.create(any()) }
        verify(atLeast = 1) { focus.release() }
    }
}
