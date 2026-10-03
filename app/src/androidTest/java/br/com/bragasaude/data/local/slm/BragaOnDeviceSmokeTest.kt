package br.com.bragasaude.data.local.slm

import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import br.com.bragasaude.BragaApplication
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test

/** Usa o GGUF instalado pelo fluxo do app; nunca baixa nem envia conteúdo a APIs. */
class BragaOnDeviceSmokeTest {
    @Test fun generatesPortugueseOnDeviceAndResetsConversation() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = BragaModelStore(context)
        val app = context.applicationContext as BragaApplication
        val allowInstall = InstrumentationRegistry.getArguments().getString("installModels") == "true"
        if (!store.installed() && allowInstall) {
            InstrumentationRegistry.getInstrumentation().startActivitySync(
                android.content.Intent(context, br.com.bragasaude.MainActivity::class.java)
                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
            val voices = app.voiceProfileManager
            withTimeout(30_000L) { voices.state.first { !it.busy } }
            voices.select("faber")
            val prepared = withTimeout(900_000L) {
                voices.state.first { !it.busy && ((it.hasChosenVoice && app.bragaEngine.ready) || it.error != null) }
            }
            assertNull(prepared.error)
            Log.i("BragaSmoke", "INSTALACAO_COMBINADA_OK")
        }
        assertTrue("Instale primeiro o Braga nas configurações do app", store.installed())
        val engine = app.bragaEngine
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
            if (allowInstall) {
                val voices = app.voiceProfileManager
                withTimeout(30_000L) { voices.state.first { !it.busy } }
                if (voices.state.value.activeId == br.com.bragasaude.data.local.voice.VoiceCatalog.SYSTEM_ID) {
                    voices.select("faber")
                    withTimeout(600_000L) {
                        voices.state.first { !it.busy && (it.activeId == "faber" || it.error != null) }
                    }
                }
                assertNull(voices.state.value.error)
                assertNotEquals(br.com.bragasaude.data.local.voice.VoiceCatalog.SYSTEM_ID, voices.state.value.activeId)
                val finished = CompletableDeferred<Unit>()
                assertTrue(voices.playSpeech(answer, {}, { finished.complete(Unit) }))
                withTimeout(90_000L) { finished.await() }
                Log.i("BragaSmoke", "PIPER_LOCAL_OK")
            }
        } finally { engine.unload() }
    }
}
