package br.com.bragasaude.data.local.slm

import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

/** Usa o GGUF instalado pelo fluxo do app; nunca baixa nem envia conteúdo a APIs. */
class BragaOnDeviceSmokeTest {
    @Test fun generatesPortugueseOnDeviceAndResetsConversation() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = BragaModelStore(context)
        assertTrue("Instale primeiro o Braga nas configurações do app", store.installed())
        val engine = BragaOnDeviceEngine(store)
        try {
            val start = android.os.SystemClock.elapsedRealtime()
            var chunks = 0
            val answer = engine.reply(listOf("user" to "Olá Braga, estou me sentindo sozinho hoje.")) { chunks++ }
            Log.i("BragaSmoke", "INFERENCIA_LOCAL_MS=${android.os.SystemClock.elapsedRealtime() - start}; CHUNKS=$chunks; RESPOSTA=$answer")
            assertTrue(answer.isNotBlank())
            assertTrue(chunks > 0)
            assertFalse(answer.contains("<|"))
            val second = engine.reply(listOf("user" to "Como posso lembrar de beber água durante o dia?"))
            Log.i("BragaSmoke", "SEGUNDA_RESPOSTA=$second")
            assertTrue(second.isNotBlank())
        } finally { engine.unload() }
    }
}
