package br.com.bragasaude.ai

import android.util.Log
import br.com.bragasaude.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * Registro estruturado de cada decisão de roteamento do assistente (NLU Local vs Groq LLM).
 */
data class RoutingTiming(
    val nluMs: Double,
    val roomMs: Double? = null,
    val firstTextMs: Double? = null,
    val completedMs: Double? = null,
    val cadenceMs: Long = 0,
    val firstAudioMs: Double? = null,
    val interruptionMs: Double? = null
)

data class RoutingLogEntry(
    val id: String = UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val channel: InputChannel,
    val input: String,
    val decision: RoutingDecision,
    val intent: String,
    val reason: String,
    val durationMs: Long,
    val previewResponse: String,
    val timing: RoutingTiming? = null
) {
    enum class RoutingDecision { LOCAL_NLU, CLOUD_LLM }

    val formattedTime: String
        get() = SimpleDateFormat("HH:mm:ss", Locale("pt", "BR")).format(Date(timestamp))

    val formattedDateTime: String
        get() = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale("pt", "BR")).format(Date(timestamp))
}

/**
 * Singleton de observabilidade e diagnóstico de roteamento para testes e benchmarks no dispositivo.
 */
object BragaRoutingLogger {
    private const val TAG = "BRAGA_ROUTING"
    private const val MAX_LOGS = 200

    private val _logs = MutableStateFlow<List<RoutingLogEntry>>(emptyList())
    val logs: StateFlow<List<RoutingLogEntry>> = _logs.asStateFlow()

    @Synchronized
    fun record(
        channel: InputChannel,
        input: String,
        decision: RoutingLogEntry.RoutingDecision,
        intent: String,
        reason: String,
        durationMs: Long,
        previewResponse: String,
        timing: RoutingTiming? = null
    ) {
        val entry = RoutingLogEntry(
            channel = channel,
            input = input.trim(),
            decision = decision,
            intent = intent,
            reason = reason,
            durationMs = durationMs,
            previewResponse = previewResponse.trim(),
            timing = timing
        )

        val updated = (listOf(entry) + _logs.value).take(MAX_LOGS)
        _logs.value = updated

        // Logcat com tag dedicada: detalhes completos de mensagem apenas em builds DEBUG de desenvolvimento
        if (BuildConfig.DEBUG) {
            Log.i(
                TAG,
                "[$decision] [${channel.name}] (${durationMs}ms) intent=$intent | input=\"${entry.input}\" | reason=\"$reason\" | preview=\"${entry.previewResponse.take(90)}\" | timing=$timing"
            )
        }
    }

    @Synchronized
    fun clear() {
        _logs.value = emptyList()
    }

    fun exportReport(): String {
        val list = _logs.value
        val total = list.size
        val localCount = list.count { it.decision == RoutingLogEntry.RoutingDecision.LOCAL_NLU }
        val cloudCount = list.count { it.decision == RoutingLogEntry.RoutingDecision.CLOUD_LLM }
        val localPercentage = if (total > 0) (localCount * 100.0 / total) else 0.0

        val sb = StringBuilder()
        sb.appendLine("=== RELATÓRIO DE ROTEAMENTO BRAGA HÍBRIDO ===")
        sb.appendLine("Total de Interações: $total")
        sb.appendLine("Locais (NLU On-Device): $localCount (%.1f%%)".format(Locale.ROOT, localPercentage))
        sb.appendLine("Nuvem (Groq LLM): $cloudCount")
        sb.appendLine("------------------------------------------")

        list.forEachIndexed { index, item ->
            sb.appendLine("[#${total - index}] ${item.formattedTime} [${item.decision}] [${item.channel}]")
            sb.appendLine("  Entrada: \"${item.input}\"")
            sb.appendLine("  Intent: ${item.intent} (${item.durationMs}ms)")
            sb.appendLine("  Motivo: ${item.reason}")
            item.timing?.let { sb.appendLine("  Tempos por etapa: $it") }
            sb.appendLine("  Resposta: \"${item.previewResponse.take(120)}\"")
            sb.appendLine()
        }

        return sb.toString()
    }
}
