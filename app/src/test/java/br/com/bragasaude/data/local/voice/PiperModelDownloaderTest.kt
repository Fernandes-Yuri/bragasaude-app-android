package br.com.bragasaude.data.local.voice

import android.content.Context
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class PiperModelDownloaderTest {

    private val testDispatcher = StandardTestDispatcher()
    private val context = mockk<Context>(relaxed = true)
    private val piperEngine = mockk<PiperOnDeviceEngine>(relaxed = true)
    private val okHttpClient = mockk<OkHttpClient>(relaxed = true)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        val tempDir = File(System.getProperty("java.io.tmpdir"), "piper_dl_test_${System.currentTimeMillis()}")
        tempDir.mkdirs()
        every { context.cacheDir } returns tempDir
        every { piperEngine.modelDir } returns File(tempDir, "model")
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `downloader skips download when model files already exist`() {
        every { piperEngine.hasModelFiles() } returns true

        val downloader = PiperModelDownloader(context, piperEngine, okHttpClient)
        downloader.startDownloadInBackground()

        assertEquals(PiperModelDownloader.DownloadState.Ready, downloader.downloadStatus.value)
    }

    @Test
    fun `downloader enters downloading state when files are missing`() {
        every { piperEngine.hasModelFiles() } returns false

        val downloader = PiperModelDownloader(context, piperEngine, okHttpClient)
        downloader.startDownloadInBackground()

        assertEquals(PiperModelDownloader.DownloadState.Downloading, downloader.downloadStatus.value)
    }
}
