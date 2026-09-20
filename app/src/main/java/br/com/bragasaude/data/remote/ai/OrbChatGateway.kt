package br.com.bragasaude.data.remote.ai

import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.tasks.await
import java.io.IOException
import javax.inject.Inject
import org.json.JSONObject

class OrbChatGateway @Inject constructor(
    private val socket: OrbWebSocket,
    private val rest: BragaLocalAiClient,
    private val auth: FirebaseAuth
) {
    private var session: OrbWebSocket.Session? = null
    private var observer: Job? = null
    private val status = MutableStateFlow(OrbConnectionState.CLOSED)
    val connection: StateFlow<OrbConnectionState> = status.asStateFlow()

    fun open(scope: CoroutineScope) {
        if (session != null) return
        val uid = auth.currentUser?.uid ?: return
        session = socket.openSession(scope, rest.serverBaseUrl) { force ->
            val user = auth.currentUser
            if (user == null || user.uid != uid) throw OrbRejectedException("Entre na sua conta novamente.")
            user.getIdToken(force).await().token ?: throw OrbRejectedException("Sessão indisponível.")
        }
        observer = scope.launch { session?.state?.collect { status.value = it } }
    }

    suspend fun send(history: List<Pair<String, String>>,
                     actingAs: String? = null, patientId: String? = null,
                     onPartial: (String) -> Unit): OrbReply {
        LocalConversationAnswers.answer(history.lastOrNull()?.second.orEmpty(), history)?.let {
            return OrbReply(JSONObject().put("fala", it).put("acao", "CONVERSA")
                .put("parametros", JSONObject()).toString())
        }
        try {
            return session?.chat(history, actingAs, patientId, onPartial)
                ?: throw IOException("Sem conexão")
        } catch (e: OrbRejectedException) {
            throw e
        } catch (_: TimeoutCancellationException) {
            currentCoroutineContext().ensureActive()
        } catch (e: CancellationException) {
            throw e
        } catch (_: IOException) {
            // Provider errors and transport failure can use the REST gateway once.
        }
        onPartial("")
        // AUD-AN05: history.last() crashava com NoSuchElementException se o
        // histórico estiver vazio (primeira fala após login/limpeza). A própria
        // linha 35 já usa lastOrNull; aqui era inconsistente.
        val lastMessage = history.lastOrNull()?.second.orEmpty()
        val result = rest.interpretSpeech(lastMessage, preferWebSocket = false,
                                          history = history,
                                          actingAs = actingAs, patientId = patientId,
                                          onPartial = onPartial)
            ?: throw IOException("Não foi possível obter resposta. Tente novamente.")
        val raw = result.rawResponse
        val structured = try { JSONObject(raw ?: "").has("fala") } catch (_: Exception) { false }
        val content = if (structured) raw!! else JSONObject().put("fala", result.fala)
            .put("acao", "CONVERSA").put("parametros", JSONObject()).toString()
        return OrbReply(content, "Resposta via REST")
    }

    /**
     * Agente B1: recibo de executor (melhor-esforço). Retorna a fala de
     * fechamento do servidor ou null.
     */
    suspend fun sendReceipt(action: String, params: JSONObject, idempotencyKey: String): String? {
        return try {
            session?.receipt(action, params, idempotencyKey)
        } catch (_: Exception) {
            null
        }
    }

    fun retry() { session?.retry() }
    fun close() {
        observer?.cancel()
        session?.close()
        session = null
        status.value = OrbConnectionState.CLOSED
    }
}
