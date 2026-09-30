package br.com.bragasaude.data.local.voice

import android.content.Context
import android.content.SharedPreferences
import io.mockk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class VoiceProfileManagerTest {
    @get:Rule val temporary = TemporaryFolder()
    private val context = mockk<Context>()
    private val preferences = mockk<SharedPreferences>()
    private val editor = mockk<SharedPreferences.Editor>()
    private val downloader = mockk<PiperModelDownloader>()
    private val engine = mockk<PiperOnDeviceEngine>(relaxed = true)
    private val system = mockk<AndroidSystemTtsFallback>(relaxed = true)
    private var saved: String? = null
    private fun manager(): VoiceProfileManager {
        every { context.filesDir } returns temporary.root
        every { context.getSharedPreferences("voice_profile", Context.MODE_PRIVATE) } returns preferences
        every { preferences.getString("active_voice_id", null) } answers { saved }
        every { preferences.edit() } returns editor
        every { editor.putString("active_voice_id", any()) } answers { saved = secondArg(); editor }
        every { editor.commit() } returns true
        return VoiceProfileManager(context, downloader, engine, system)
    }
    private suspend fun ready(manager: VoiceProfileManager) = withTimeout(5000) { manager.state.first { !it.busy } }

    @Test fun `instalacao nova usa sistema sem baixar modelo`() = runBlocking {
        val manager = manager()
        assertEquals(VoiceCatalog.SYSTEM_ID, ready(manager).activeId)
        coVerify(exactly = 0) { downloader.prepare(any(), any(), any()) }
        manager.playSpeech("Teste", {}, {})
        coVerify(exactly = 0) { engine.playStream(any(), any(), any(), any()) }
        coVerify(exactly = 1) { system.speak("Teste", any(), any()) }
    }
    @Test fun `download com falha preserva selecao e permite tentar novamente`() = runBlocking {
        val manager = manager()
        ready(manager)
        coEvery { downloader.prepare(any(), any(), any()) } throws IllegalStateException("Sem rede")
        manager.select("cadu")
        val failed = ready(manager)
        assertEquals(VoiceCatalog.SYSTEM_ID, failed.activeId)
        assertNotNull(failed.error)
        assertEquals(VoiceCatalog.SYSTEM_ID, saved)
        coVerify(exactly = 0) { engine.replaceModel(any(), any(), any()) }
    }
    @Test fun `cancelamento libera interface e remove staging`() = runBlocking {
        val manager = manager()
        ready(manager)
        val started = CompletableDeferred<Unit>()
        coEvery { downloader.prepare(any(), any(), any()) } coAnswers {
            started.complete(Unit)
            awaitCancellation()
        }
        manager.select("edresson")
        withTimeout(5000) { started.await() }
        manager.cancelDownload()
        assertEquals(VoiceCatalog.SYSTEM_ID, ready(manager).activeId)
        withTimeout(5000) {
            while (java.io.File(temporary.root, "voices/staging").exists()) delay(10)
        }
        assertEquals(VoiceCatalog.SYSTEM_ID, saved)
    }
}
