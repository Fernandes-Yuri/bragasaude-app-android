package br.com.bragasaude.data.local.slm

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** Segunda instalação no mesmo preparo da voz; nunca inicia um download por conta própria. */
@Singleton
class BragaModelInstaller @Inject constructor(private val store: BragaModelStore, private val engine: BragaOnDeviceEngine) {
    private val mutex = Mutex()
    val ready: Boolean get() = store.installed()
    suspend fun prepare(onProgress: suspend (Int) -> Unit) = withContext(Dispatchers.IO) { mutex.withLock {
        try {
            if (!store.installed()) {
                store.prepare()
                val client = OkHttpClient.Builder().connectTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(60, TimeUnit.SECONDS).build()
                val digest = MessageDigest.getInstance("SHA-256")
                client.newCall(Request.Builder().url(BragaModelStore.URL).build()).execute().use { response ->
                    check(response.isSuccessful) { "Falha no download do Braga (${response.code}). Tente novamente." }
                    val body = checkNotNull(response.body)
                    val length = body.contentLength()
                    check(length == -1L || length == BragaModelStore.SIZE) { "Tamanho de modelo inválido." }
                    body.byteStream().use { input -> store.staging.outputStream().use { output ->
                        val buffer = ByteArray(128 * 1024)
                        var total = 0L
                        var lastPercent = -1
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val count = input.read(buffer)
                            if (count < 0) break
                            total += count
                            check(total <= BragaModelStore.SIZE) { "Download maior que o modelo esperado." }
                            output.write(buffer, 0, count)
                            digest.update(buffer, 0, count)
                            val percent = (100 * total / BragaModelStore.SIZE).toInt()
                            if (percent != lastPercent) {
                                onProgress(percent)
                                lastPercent = percent
                            }
                        }
                        check(total == BragaModelStore.SIZE) { "Download incompleto. Tente novamente." }
                        output.fd.sync()
                    } }
                }
                val hash = digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
                check(hash == BragaModelStore.SHA256) { "O modelo falhou na verificação. Tente baixar novamente." }
                currentCoroutineContext().ensureActive()
                store.commit()
            }
            onProgress(100)
            engine.initialize()
        } finally { store.staging.delete() }
    } }
}
