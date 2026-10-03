package br.com.bragasaude.data.remote.ai

import br.com.bragasaude.data.local.slm.BragaOnDeviceEngine
import br.com.bragasaude.data.local.slm.BragaModelStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import javax.inject.Inject

/** Mantém o contrato de UI do chat; não abre transporte para inferência remota. */
class OrbChatGateway @Inject constructor(
    private val rest: BragaLocalAiClient,
    private val engine: BragaOnDeviceEngine
) {
    private val status = MutableStateFlow(OrbConnectionState.CLOSED)
    val connection: StateFlow<OrbConnectionState> = status.asStateFlow()
    val ready: Boolean get() = engine.ready
    private var observer: Job? = null

    fun open(scope: CoroutineScope) {
        observer?.cancel()
        status.value = OrbConnectionState.CONNECTING
        observer = scope.launch {
            try {
                engine.initialize()
                status.value = OrbConnectionState.CONNECTED
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (_: Exception) { status.value = OrbConnectionState.FAILED }
        }
    }

    fun isEmergency(text: String): Boolean = rest.isEmergency(text)

    fun immediateResponse(text: String) = rest.immediateResponse(text)

    suspend fun send(history: List<Pair<String, String>>, actingAs: String? = null,
                     patientId: String? = null, onPartial: (String) -> Unit): OrbReply {
        immediateResponse(history.lastOrNull()?.second.orEmpty())?.let {
            onPartial(it.text)
            return OrbReply(JSONObject().put("fala", it.text).put("acao", "TRIAGEM")
                .put("gravidade", it.severity.name).put("parametros", JSONObject()).toString())
        }
        check(ready) { BragaModelStore.REQUIRED_MESSAGE }
        LocalConversationAnswers.answer(history.lastOrNull()?.second.orEmpty(), history)?.let {
            return OrbReply(JSONObject().put("fala", it).put("acao", "CONVERSA")
                .put("parametros", JSONObject()).toString())
        }
        val result = rest.interpretSpeech(history.lastOrNull()?.second.orEmpty(), history = history,
            actingAs = actingAs, patientId = patientId, onPartial = onPartial)
        return OrbReply(JSONObject().put("fala", result.fala).put("acao", result.action ?: "CONVERSA")
            .put("parametros", JSONObject(result.parameters)).toString(), "Braga V2.1 no aparelho")
    }

    // Os registros continuam sob confirmação e validação local do app.
    suspend fun sendReceipt(action: String, params: JSONObject, idempotencyKey: String): String? = null
    fun retry() { status.value = if (ready) OrbConnectionState.CONNECTED else OrbConnectionState.FAILED }
    fun close() { observer?.cancel(); observer = null; status.value = OrbConnectionState.CLOSED }
}
