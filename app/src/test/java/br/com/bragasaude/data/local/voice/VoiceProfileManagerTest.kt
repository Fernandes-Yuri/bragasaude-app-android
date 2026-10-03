package br.com.bragasaude.data.local.voice

import android.content.Context
import android.content.SharedPreferences
import androidx.work.WorkManager
import io.mockk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class VoiceProfileManagerTest {
    @get:Rule val temporary = TemporaryFolder()
    private val context = mockk<Context>()
    private val preferences = mockk<SharedPreferences>()
    private val editor = mockk<SharedPreferences.Editor>()
    private val downloader = mockk<PiperModelDownloader>()
    private val engine = mockk<PiperOnDeviceEngine>(relaxed = true)
    private val system = mockk<AndroidSystemTtsFallback>(relaxed = true)
    private val workManager = mockk<WorkManager>(relaxed = true)
    private val notifications = mockk<VoicePreparationNotifications>(relaxed = true)
    private val assistantInstaller = mockk<br.com.bragasaude.data.local.slm.BragaModelInstaller>(relaxed = true)
    private var saved: String? = null
    private var background: String? = null

    private fun manager(): VoiceProfileManager {
        every { context.filesDir } returns temporary.root
        every { context.getSharedPreferences("voice_profile", Context.MODE_PRIVATE) } returns preferences
        every { preferences.getString(any(), null) } answers {
            if (firstArg<String>() == "background_voice_id") background else saved
        }
        every { preferences.getBoolean("has_chosen_voice", false) } answers { saved != null }
        every { preferences.edit() } returns editor
        every { editor.putString(any(), any()) } answers {
            if (firstArg<String>() == "background_voice_id") background = secondArg() else saved = secondArg()
            editor
        }
        every { editor.remove("background_voice_id") } answers { background = null; editor }
        every { editor.putBoolean("has_chosen_voice", any()) } returns editor
        every { editor.commit() } returns true
        every { assistantInstaller.ready } returns true
        return VoiceProfileManager(context, downloader, engine, system, workManager, notifications, assistantInstaller)
    }
    private suspend fun ready(manager: VoiceProfileManager) = withTimeout(5000) { manager.state.first { !it.busy } }

    @Test fun `instalacao nova usa sistema sem baixar modelo`() = runBlocking {
        val manager = manager()
        assertEquals(VoiceCatalog.SYSTEM_ID, ready(manager).activeId)
        coVerify(exactly = 0) { downloader.prepare(any(), any(), any()) }
        coVerify(exactly = 0) { assistantInstaller.prepare(any()) }
        manager.playSpeech("Teste", {}, {})
        coVerify(exactly = 0) { engine.playStream(any(), any(), any(), any()) }
        coVerify(exactly = 1) { system.speak("Teste", any(), any()) }
    }
    @Test fun `download com falha preserva selecao`() = runBlocking {
        val manager = manager()
        ready(manager)
        coEvery { downloader.prepare(any(), any(), any()) } throws IllegalStateException("Sem rede")
        assertFalse(manager.prepareSelection("cadu"))
        assertEquals(VoiceCatalog.SYSTEM_ID, manager.state.value.activeId)
        assertNotNull(manager.state.value.error)
        coVerify(exactly = 0) { engine.replaceModel(any(), any(), any()) }
        verify(exactly = 0) { notifications.result(any(), any()) }
    }
    @Test fun `escolha da voz so libera conversa depois da segunda instalacao`() = runBlocking {
        val manager = manager()
        ready(manager)
        every { assistantInstaller.ready } returns false
        val started = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<Unit>()
        coEvery { assistantInstaller.prepare(any()) } coAnswers {
            started.complete(Unit)
            finish.await()
            every { assistantInstaller.ready } returns true
        }
        val job = launch(Dispatchers.IO) { manager.prepareSelection(VoiceCatalog.SYSTEM_ID) }
        withTimeout(5000) { started.await() }
        assertTrue(manager.state.value.busy)
        assertTrue(manager.needsOnboarding())
        finish.complete(Unit)
        withTimeout(5000) { job.join() }
        assertFalse(manager.state.value.busy)
        assertFalse(manager.needsOnboarding())
        coVerify(exactly = 1) { assistantInstaller.prepare(any()) }
    }
    @Test fun `cancelamento libera interface e remove staging sem notificar sucesso`() = runBlocking {
        val manager = manager()
        ready(manager)
        val started = CompletableDeferred<Unit>()
        coEvery { downloader.prepare(any(), any(), any()) } coAnswers { started.complete(Unit); awaitCancellation() }
        val job = launch(Dispatchers.IO) { manager.prepareSelection("faber") }
        withTimeout(5000) { started.await() }
        assertTrue(manager.minimizePreparation("faber"))
        manager.cancelDownload()
        withTimeout(5000) { job.join() }
        assertFalse(manager.state.value.busy)
        assertFalse(File(temporary.root, "voices/staging").exists())
        verify(exactly = 0) { notifications.result(any(), any()) }
    }
    private fun model(dir: File) {
        File(dir, "model.onnx").writeBytes(ByteArray(100_001))
        File(dir, "tokens.txt").writeText("a".repeat(51))
        File(dir, "espeak-ng-data").mkdirs()
        listOf("phontab", "phondata", "phonindex").forEach { File(dir, "espeak-ng-data/$it").writeText("data") }
    }
    @Test fun `minimizar mantem tarefa ativa e notifica quando modelo confirma`() = runBlocking {
        val manager = manager()
        ready(manager)
        val started = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<Unit>()
        coEvery { downloader.prepare(any(), any(), any()) } coAnswers {
            started.complete(Unit); finish.await(); model(secondArg())
        }
        coEvery { engine.replaceModel(any(), any(), any()) } coAnswers {
            firstArg<() -> Unit>().invoke(); secondArg<() -> Unit>().invoke(); true
        }
        val job = launch(Dispatchers.IO) { manager.prepareSelection("faber") }
        withTimeout(5000) { started.await() }
        assertTrue(manager.minimizePreparation("faber"))
        assertTrue(job.isActive)
        finish.complete(Unit)
        withTimeout(5000) { job.join() }
        assertEquals("faber", saved)
        verify(exactly = 1) { notifications.result("Faber", true) }
        coVerify(exactly = 0) { engine.playStream(any(), any(), any(), any()) }
        assertNull(background)
    }
    @Test fun `falha apos minimizar avisa sem substituir voz anterior`() = runBlocking {
        val manager = manager()
        ready(manager)
        val started = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<Unit>()
        coEvery { downloader.prepare(any(), any(), any()) } coAnswers {
            started.complete(Unit); finish.await(); error("Sem rede")
        }
        val job = launch(Dispatchers.IO) { manager.prepareSelection("cadu") }
        withTimeout(5000) { started.await() }
        manager.minimizePreparation("cadu")
        finish.complete(Unit)
        withTimeout(5000) { job.join() }
        assertEquals(VoiceCatalog.SYSTEM_ID, saved)
        verify(exactly = 1) { notifications.result("Cadu", false) }
        assertNull(background)
    }
    @Test fun `conclusao entre clique e minimizacao tambem gera aviso`() = runBlocking {
        val manager = manager()
        ready(manager)
        assertTrue(manager.prepareSelection(VoiceCatalog.SYSTEM_ID))
        assertTrue(manager.minimizePreparation(VoiceCatalog.SYSTEM_ID))
        verify(exactly = 1) { notifications.result("Voz do dispositivo", true) }
    }
}
