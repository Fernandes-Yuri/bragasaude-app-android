package br.com.bragasaude.data.local.voice

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class VoiceModelStoreTest {
    @get:Rule val temporary = TemporaryFolder()
    private fun model(dir: File, id: String) {
        dir.mkdirs()
        File(dir, "model.onnx").writeBytes(ByteArray(100_001))
        File(dir, "tokens.txt").writeText("a".repeat(51))
        File(dir, "espeak-ng-data").mkdirs()
        listOf("phontab", "phondata", "phonindex").forEach { File(dir, "espeak-ng-data/$it").writeText("data") }
        File(dir, "voice_id").writeText(id)
    }
    @Test fun `falha antes de confirmar restaura voz anterior ao reiniciar`() {
        val store = VoiceModelStore(temporary.newFolder())
        model(store.current, "faber")
        model(store.prepare(), "cadu")
        store.begin("cadu")
        store.recover("faber")
        assertEquals("faber", VoiceModelStore.id(store.current))
        assertFalse(store.previous.exists())
        assertFalse(store.staging.exists())
    }
    @Test fun `reinicio apos commit limpa anterior sem desfazer selecao`() {
        val store = VoiceModelStore(temporary.newFolder())
        model(store.current, "faber")
        model(store.prepare(), "cadu")
        store.begin("cadu")
        store.recover("cadu")
        assertEquals("cadu", VoiceModelStore.id(store.current))
        assertFalse(store.previous.exists())
    }
    @Test fun `falha de inicializacao permite rollback`() {
        val store = VoiceModelStore(temporary.newFolder())
        model(store.current, "faber")
        model(store.prepare(), "cadu")
        store.begin("cadu")
        store.rollback()
        assertEquals("faber", VoiceModelStore.id(store.current))
    }
    @Test fun `primeira instalacao interrompida e removida quando sistema segue selecionado`() {
        val store = VoiceModelStore(temporary.newFolder())
        model(store.prepare(), "faber")
        store.begin("faber")
        store.recover(VoiceCatalog.SYSTEM_ID)
        assertFalse(store.current.exists())
    }
    @Test fun `pacote incompleto nao substitui voz ativa`() {
        val store = VoiceModelStore(temporary.newFolder())
        model(store.current, "faber")
        store.prepare()
        assertThrows(IllegalStateException::class.java) { store.begin("cadu") }
        assertEquals("faber", VoiceModelStore.id(store.current))
    }
    @Test fun `sistema remove todos os modelos apos reinicio`() {
        val store = VoiceModelStore(temporary.newFolder())
        model(store.current, "cadu")
        model(store.previous, "faber")
        model(store.staging, "cadu")
        store.recover(VoiceCatalog.SYSTEM_ID)
        assertFalse(store.current.exists())
        assertFalse(store.previous.exists())
        assertFalse(store.staging.exists())
    }
}
