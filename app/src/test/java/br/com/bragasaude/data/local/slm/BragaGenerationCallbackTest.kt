package br.com.bragasaude.data.local.slm

import kotlinx.coroutines.Job
import org.junit.Assert.*
import org.junit.Test

class BragaGenerationCallbackTest {
    @Test fun deltasAppendWithoutDuplicatingPreviousText() {
        val snapshots = mutableListOf<String>()
        val callback = BragaGenerationCallback(null) { snapshots.add(it) }
        callback.onBytes("Oi ".toByteArray())
        callback.onBytes("tudo bem?".toByteArray())
        assertEquals(listOf("Oi ", "Oi tudo bem?"), snapshots)
    }

    @Test fun splitUtf8DoesNotPublishReplacementCharacters() {
        val snapshots = mutableListOf<String>()
        val callback = BragaGenerationCallback(null) { snapshots.add(it) }
        val bytes = "coração".toByteArray(Charsets.UTF_8)
        callback.onBytes(bytes.copyOfRange(0, 5))
        callback.onBytes(bytes.copyOfRange(5, bytes.size))
        assertEquals(listOf("cora", "coração"), snapshots)
        assertFalse(snapshots.any { '\uFFFD' in it })
    }

    @Test fun fourByteCharacterCanSpanMultipleBatches() {
        val snapshots = mutableListOf<String>()
        val callback = BragaGenerationCallback(null) { snapshots.add(it) }
        val expected = "\uD83D\uDE00"
        expected.toByteArray(Charsets.UTF_8).forEach { callback.onBytes(byteArrayOf(it)) }
        assertEquals(listOf(expected), snapshots)
    }

    @Test fun cancelledGenerationPublishesNothing() {
        val job = Job().also { it.cancel() }
        val callback = BragaGenerationCallback(job) { fail("Não deve publicar depois do cancelamento") }
        callback.onBytes("Resposta atrasada".toByteArray())
        assertTrue(callback.isCancelled())
    }
}
