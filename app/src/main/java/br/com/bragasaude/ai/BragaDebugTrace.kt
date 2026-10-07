package br.com.bragasaude.ai

import android.content.Context
import android.util.Log
import br.com.bragasaude.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

/** Diagnóstico temporário local. Nenhum dado é enviado à rede ou escrito em release. */
object BragaDebugTrace {
    private const val TAG = "BRAGA_NLU_DEBUG"
    private val run = UUID.randomUUID().toString()
    private val sequence = AtomicLong()
    private val dropped = AtomicLong()
    private val queue = Channel<() -> String>(128)
    @Volatile private var ready = false

    @Synchronized fun initialize(context: Context) {
        if (!BuildConfig.DEBUG || ready) return
        val store = DebugTraceStore(File(context.filesDir, "nlu-debug"))
        ready = true
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            for (line in queue) {
                try { store.append(line()) }
                catch (_: Exception) { Log.w(TAG, "Falha ao gravar diagnóstico temporário") }
            }
        }
        event("START", detail = "debug=${BuildConfig.VERSION_NAME}; schema=1")
    }

    fun event(name: String, channel: InputChannel? = null, input: String? = null,
              output: NluOutput? = null, reply: String? = null, detail: String? = null,
              owner: String? = null, conversation: String? = null, turn: Long? = null) {
        if (!BuildConfig.DEBUG || !ready) return
        val id = sequence.incrementAndGet()
        val time = System.currentTimeMillis()
        val lost = dropped.getAndSet(0)
        val accepted = queue.trySend {
            JSONObject().apply {
                put("schema", 1); put("run", run); put("sequence", id); put("timeMillis", time)
                put("event", name); put("channel", channel?.name); put("turn", turn)
                put("ownerAlias", owner?.let(::alias)); put("conversationAlias", conversation?.let(::alias))
                put("droppedBefore", lost)
                input?.let {
                    put("input", it.take(16000)); put("inputTruncated", it.length > 16000)
                    put("recognized", BragaLanguageRecovery.recognize(it).take(16000))
                }
                put("reply", reply?.take(16000)); put("replyTruncated", (reply?.length ?: 0) > 16000)
                put("detail", detail?.take(2000))
                output?.let {
                    put("intent", it.intent); put("route", it.route.name)
                    put("cloud", it.delegarParaNuvem); put("emergency", it.isEmergencia)
                    put("blocked", it.isBloqueioSeguranca); put("hasLocalData", it.hasLocalData)
                    put("nluMs", it.tempoMs); put("referenceMeasuredAtMillis", it.referenceMeasuredAtMillis)
                    it.healthQuery?.let { q ->
                        put("query", JSONObject().put("metric", q.metric.name).put("period", q.period.name)
                            .put("operation", q.operation.name).put("glucoseType", q.glucoseType)
                            .put("medicationName", q.medicationName))
                    }
                }
            }.toString()
        }.isSuccess
        if (!accepted) dropped.addAndGet(lost + 1)
        // O conteúdo completo está no arquivo privado; logcat só indica progresso.
        Log.i(TAG, "event=$name sequence=$id channel=${channel?.name} queued=$accepted")
    }

    private fun alias(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest((run + value).toByteArray(Charsets.UTF_8)).take(8).joinToString("") { "%02x".format(it) }
}

/** Dois arquivos limitados, com retenção de sete dias e gravação fora da thread da UI. */
internal class DebugTraceStore(private val directory: File, private val limitBytes: Long = 2L * 1024 * 1024,
                               private val now: () -> Long = { System.currentTimeMillis() }) {
    fun append(line: String) {
        check(directory.isDirectory || directory.mkdirs())
        val current = File(directory, "events.jsonl")
        val previous = File(directory, "events.previous.jsonl")
        for (file in listOf(current, previous)) {
            if (file.exists() && now() - file.lastModified() > 7L * 24 * 60 * 60 * 1000) check(file.delete())
        }
        val bytes = (line + "\n").toByteArray(Charsets.UTF_8)
        require(bytes.size <= limitBytes)
        if (current.length() + bytes.size > limitBytes) {
            if (previous.exists()) check(previous.delete())
            check(current.renameTo(previous))
        }
        current.appendBytes(bytes)
    }
}
