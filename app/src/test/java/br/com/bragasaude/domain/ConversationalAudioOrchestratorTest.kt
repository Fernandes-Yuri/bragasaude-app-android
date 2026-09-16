package br.com.bragasaude.domain

import br.com.bragasaude.data.remote.ai.NeuralAudioPlayer
import io.mockk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ConversationalAudioOrchestratorTest {
    @Test fun `cancelled waiting generation never signals normal completion`() = runTest {
        val player = mockk<NeuralAudioPlayer>(relaxed = true)
        val flow = ConversationalAudioOrchestrator(player)
        val answer = CompletableDeferred<String?>()
        var completions = 0
        flow.startThreeActFlow(this, answer, {}, { completions++ })
        runCurrent()
        flow.stop()
        answer.complete("resposta atrasada")
        runCurrent()
        assertEquals(0, completions)
        coVerify(exactly = 0) { player.playSpeech(any(), any(), any(), any()) }
    }

    @Test fun `replacing generation does not stop or complete new playback`() = runTest {
        val player = mockk<NeuralAudioPlayer>(relaxed = true)
        val flow = ConversationalAudioOrchestrator(player)
        var finish: (() -> Unit)? = null
        coEvery { player.playSpeech(any(), any(), any(), any()) } coAnswers {
            thirdArg<() -> Unit>().invoke()
            finish = arg(3)
            true
        }
        var oldDone = 0
        var newDone = 0
        flow.startThreeActFlow(this, CompletableDeferred(), {}, { oldDone++ })
        runCurrent()
        flow.startThreeActFlow(this, CompletableDeferred("nova resposta"), {}, { newDone++ })
        runCurrent()
        assertEquals(0, oldDone)
        verify(exactly = 2) { player.stop() }
        finish!!.invoke()
        runCurrent()
        assertEquals(1, newDone)
        assertEquals(0, oldDone)
    }

    @Test fun `cancelled active playback ignores its late completion`() = runTest {
        val player = mockk<NeuralAudioPlayer>(relaxed = true)
        var finish: (() -> Unit)? = null
        coEvery { player.playSpeech(any(), any(), any(), any()) } coAnswers { finish = arg(3); true }
        val flow = ConversationalAudioOrchestrator(player)
        var done = 0
        flow.startThreeActFlow(this, CompletableDeferred("resposta"), {}, { done++ })
        runCurrent()
        flow.stop()
        finish!!.invoke()
        runCurrent()
        assertEquals(0, done)
    }
}
