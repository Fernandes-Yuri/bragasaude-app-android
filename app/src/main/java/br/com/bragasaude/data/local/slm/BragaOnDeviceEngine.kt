package br.com.bragasaude.data.local.slm

import androidx.annotation.Keep
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Keep
internal object BragaNative {
    init { System.loadLibrary("braga_slm") }
    external fun load(path: ByteArray, threads: Int): Long
    external fun generate(handle: Long, prompt: ByteArray, maxTokens: Int, callback: BragaGenerationCallback): ByteArray?
    external fun release(handle: Long)
}

@Keep
internal class BragaGenerationCallback(private val job: Job?, private val partial: (String) -> Unit) {
    fun isCancelled(): Boolean = job?.isActive == false
    fun onBytes(bytes: ByteArray) {
        if (!isCancelled()) partial(bytes.toString(Charsets.UTF_8).trimEnd('\uFFFD'))
    }
}

@Singleton
class BragaOnDeviceEngine @Inject constructor(private val store: BragaModelStore) {
    private val mutex = Mutex()
    private var handle = 0L
    val ready: Boolean get() = store.installed()

    suspend fun initialize() = withContext(Dispatchers.Default) { mutex.withLock { loadLocked() } }
    private fun loadLocked() {
        check(ready) { BragaModelStore.REQUIRED_MESSAGE }
        if (handle == 0L) {
            try {
                handle = BragaNative.load(store.model.absolutePath.toByteArray(Charsets.UTF_8),
                    Runtime.getRuntime().availableProcessors().coerceIn(1, 4))
                check(handle != 0L) { "Não foi possível carregar o Braga." }
            } catch (e: LinkageError) {
                throw IllegalStateException("Este aparelho não suporta o motor local do Braga.", e)
            }
        }
    }

    suspend fun reply(history: List<Pair<String, String>>, onPartial: (String) -> Unit = {}): String =
        withContext(Dispatchers.Default) { mutex.withLock {
            val prompt = BragaPrompt.build(history)
            currentCoroutineContext().ensureActive()
            loadLocked()
            val callback = BragaGenerationCallback(currentCoroutineContext()[Job], onPartial)
            val bytes = BragaNative.generate(handle, prompt.toByteArray(Charsets.UTF_8), 120, callback)
            currentCoroutineContext().ensureActive()
            check(bytes != null) { "A resposta local foi interrompida." }
            bytes.toString(Charsets.UTF_8).trim().also {
                check(it.isNotBlank()) { "O Braga não gerou uma resposta. Tente novamente." }
            }
        } }

    suspend fun unload() = withContext(Dispatchers.Default) { mutex.withLock {
        if (handle != 0L) BragaNative.release(handle)
        handle = 0L
    } }
}
