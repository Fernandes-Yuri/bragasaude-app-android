package br.com.bragasaude.data.local.slm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import br.com.bragasaude.R
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

@HiltWorker
class BragaModelWorker @AssistedInject constructor(
    @Assisted context: Context, @Assisted params: WorkerParameters,
    private val store: BragaModelStore, private val engine: BragaOnDeviceEngine
) : CoroutineWorker(context, params) {
    override suspend fun getForegroundInfo(): ForegroundInfo {
        applicationContext.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel("braga_model", "Instalação do Braga local", NotificationManager.IMPORTANCE_LOW))
        val notification = NotificationCompat.Builder(applicationContext, "braga_model")
            .setSmallIcon(R.drawable.ic_shield_ecg).setContentTitle("Preparando Braga local")
            .setContentText("Baixando o modelo para conversar no aparelho.")
            .setProgress(0, 0, true).setOngoing(true).setSilent(true).build()
        return ForegroundInfo(7310, notification,
            if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0)
    }

    override suspend fun doWork(): Result = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        try {
            setForeground(getForegroundInfo())
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
                                setProgress(workDataOf("percent" to percent, "phase" to "Baixando Braga: $percent%"))
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
            setProgress(workDataOf("percent" to 100, "phase" to "Carregando Braga no aparelho"))
            engine.initialize()
            Result.success()
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { Result.failure(workDataOf("error" to (e.message ?: "Falha na instalação do Braga."))) }
        finally { store.staging.delete() }
    }
}
