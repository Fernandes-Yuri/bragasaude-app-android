package br.com.bragasaude.ai

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class DebugTraceStoreTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun `rotacao preserva linhas completas e limita dois arquivos`() {
        val dir = temporary.newFolder()
        val store = DebugTraceStore(dir, 12)
        store.append("primeiro")
        store.append("segundo")
        assertEquals("primeiro\n", File(dir, "events.previous.jsonl").readText())
        assertEquals("segundo\n", File(dir, "events.jsonl").readText())
        store.append("terceiro")
        assertEquals("segundo\n", File(dir, "events.previous.jsonl").readText())
        assertEquals("terceiro\n", File(dir, "events.jsonl").readText())
        assertEquals(2, dir.listFiles()!!.size)
        assertTrue(dir.listFiles()!!.all { it.length() <= 12 })
    }

    @Test fun `reinicio preserva dados recentes e remove arquivos vencidos`() {
        val dir = temporary.newFolder()
        val first = DebugTraceStore(dir)
        first.append("antigo")
        DebugTraceStore(dir).append("recente")
        assertEquals("antigo\nrecente\n", File(dir, "events.jsonl").readText())
        val past = System.currentTimeMillis() - 8L * 24 * 60 * 60 * 1000
        assertTrue(File(dir, "events.jsonl").setLastModified(past))
        File(dir, "events.previous.jsonl").apply { writeText("vencido"); assertTrue(setLastModified(past)) }
        first.append("novo")
        assertEquals("novo\n", File(dir, "events.jsonl").readText())
        assertFalse(File(dir, "events.previous.jsonl").exists())
    }
}
