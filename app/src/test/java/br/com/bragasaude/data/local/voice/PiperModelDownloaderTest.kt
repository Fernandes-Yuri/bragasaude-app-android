package br.com.bragasaude.data.local.voice

import kotlinx.coroutines.test.runTest
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorOutputStream
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class PiperModelDownloaderTest {
    @get:Rule val temporary = TemporaryFolder()
    private val downloader = PiperModelDownloader()

    @Test fun `checksum rejeita arquivo corrompido`() {
        val file = temporary.newFile().apply { writeText("abc") }
        downloader.verifyChecksum(file, "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad")
        assertThrows(IllegalStateException::class.java) { downloader.verifyChecksum(file, "0".repeat(64)) }
    }

    private fun archive(name: String, link: Boolean = false): File {
        val file = temporary.newFile()
        TarArchiveOutputStream(BZip2CompressorOutputStream(file.outputStream())).use { tar ->
            val entry = if (link) TarArchiveEntry(name, '2'.code.toByte()).apply { linkName = "../outside" }
                else TarArchiveEntry(name).apply { size = 3 }
            tar.putArchiveEntry(entry)
            if (!link) tar.write(byteArrayOf(1, 2, 3))
            tar.closeArchiveEntry()
        }
        return file
    }

    @Test fun `extracao rejeita travessia de diretorios`() = runTest {
        val target = temporary.newFolder("target")
        try {
            downloader.extract(archive("voice/../../outside"), target, "voice") {}
            fail("Deveria rejeitar caminho externo")
        } catch (_: IllegalStateException) { }
        assertFalse(File(temporary.root, "outside").exists())
    }

    @Test fun `extracao rejeita links simbolicos`() = runTest {
        try {
            downloader.extract(archive("voice/link", true), temporary.newFolder(), "voice") {}
            fail("Deveria rejeitar link")
        } catch (_: IllegalStateException) { }
    }

    @Test fun `extracao normaliza nome do modelo e informa progresso`() = runTest {
        val target = temporary.newFolder()
        val progress = mutableListOf<Float>()
        downloader.extract(archive("voice/pt_BR-test.onnx"), target, "voice") { progress.add(it) }
        assertArrayEquals(byteArrayOf(1, 2, 3), File(target, "model.onnx").readBytes())
        assertEquals(1f, progress.last())
    }
}
