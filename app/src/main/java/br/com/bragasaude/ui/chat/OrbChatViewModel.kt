package br.com.bragasaude.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.bragasaude.data.local.*
import br.com.bragasaude.data.remote.ai.*
import br.com.bragasaude.data.remote.repository.FamilyBridgeRepository
import br.com.bragasaude.data.remote.service.NotificationClient
import br.com.bragasaude.domain.VoiceHealthIntent
import br.com.bragasaude.domain.VoiceHealthParser
import br.com.bragasaude.ui.util.Screen
import br.com.bragasaude.ai.*
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.json.JSONObject
import java.util.UUID
import javax.inject.Inject

@Serializable
data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val role: String,
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val status: String = "sent",
    val action: String? = null,
    val parameters: String = "{}",
    val actionStatus: String = "pending",
    val rawContent: String? = null,
    val metrics: String? = null
)

data class OrbChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val isStreaming: Boolean = false,
    val partialText: String = "",
    val input: String = "",
    val error: String? = null,
    val conversations: List<OrbConversation> = emptyList(),
    val showHistory: Boolean = true,
    val speakingId: String? = null,
    val textScale: Float = 1f
)

sealed interface OrbChatEvent {
    data class Navigate(val screen: Screen) : OrbChatEvent
    data object Emergency : OrbChatEvent
}

@HiltViewModel
class OrbChatViewModel @Inject constructor(
    private val gateway: OrbChatGateway,
    private val store: OrbChatStore,
    private val auth: FirebaseAuth,
    private val audio: NeuralAudioPlayer,
    private val notifications: NotificationClient,
    private val profiles: ProfileDao,
    private val familyRepository: FamilyBridgeRepository,
    private val voiceParser: VoiceHealthParser = VoiceHealthParser(),
    private val hybrid: BragaHybridOrchestrator,
    private val telemetry: br.com.bragasaude.data.remote.service.TelemetryService? = null
) : ViewModel() {
    private val mutable = MutableStateFlow(OrbChatUiState())
    val state: StateFlow<OrbChatUiState> = mutable.asStateFlow()
    val connection = gateway.connection
    private val navigation = Channel<OrbChatEvent>(Channel.BUFFERED)
    val events = navigation.receiveAsFlow()
    private val json = Json { ignoreUnknownKeys = true }
    private var conversationId = UUID.randomUUID().toString()
    private var uid = auth.currentUser?.uid
    private var generation: Job? = null
    private var historyJob: Job? = null
    private var audioJob: Job? = null
    private var revision = 0
    private var visible = false
    private var inputChannel = InputChannel.TEXT
    private val healthQuerySession = HealthQuerySession()
    var nluResponseDelayMs: Long = 500L
    internal fun textCadenceMs(channel: InputChannel, isEmergencia: Boolean, isBloqueio: Boolean): Long {
        // Cadência apenas no chat de texto. A voz usa o tempo do Piper e não recebe atraso extra.
        if (channel != InputChannel.TEXT || isEmergencia || isBloqueio || nluResponseDelayMs <= 0) return 0L
        return nluResponseDelayMs
    }
    private val authListener = FirebaseAuth.AuthStateListener {
        val next = it.currentUser?.uid
        if (uid != next) {
            leaveScreen()
            uid = next
            mutable.value = OrbChatUiState()
            conversationId = UUID.randomUUID().toString()
            observeHistory()
        }
    }

    init {
        auth.addAuthStateListener(authListener)
        observeHistory()
    }

    private fun observeHistory() {
        historyJob?.cancel()
        val owner = uid ?: return
        historyJob = viewModelScope.launch {
            store.observe(owner).catch { showError("Não foi possível ler o histórico.") }
                .collect { list -> mutable.update { it.copy(conversations = list) } }
        }
    }

    fun enterScreen() { visible = true }
    fun leaveScreen() {
        visible = false
        healthQuerySession.clear()
        cancelGeneration()
        stopAudio()
        gateway.close()
    }
    fun retryConnection() { gateway.open(viewModelScope); gateway.retry() }
    fun updateInput(text: String) { inputChannel = InputChannel.TEXT; mutable.update { it.copy(input = text) } }
    fun updateVoiceInput(text: String) { inputChannel = InputChannel.VOICE; mutable.update { it.copy(input = text) } }
    fun sendInput(text: String) = sendMessage(text, if (text == state.value.input) inputChannel else InputChannel.TEXT)
    fun showError(message: String) { mutable.update { it.copy(error = message) } }
    fun clearError() { mutable.update { it.copy(error = null) } }
    fun changeTextScale() { mutable.update { it.copy(textScale = if (it.textScale >= 1.4f) 1f else it.textScale + .2f) } }
    fun setTextScale(scale: Float) { mutable.update { it.copy(textScale = scale.coerceIn(0.8f, 2.0f)) } }

    fun newConversation() {
        healthQuerySession.clear()
        cancelGeneration()
        stopAudio()
        conversationId = UUID.randomUUID().toString()
        mutable.update { OrbChatUiState(conversations = it.conversations, showHistory = false, textScale = it.textScale) }
    }

    fun clearCurrentMessages() {
        healthQuerySession.clear()
        cancelGeneration()
        stopAudio()
        val owner = uid
        val currentId = conversationId
        mutable.update { it.copy(messages = emptyList(), partialText = "", isStreaming = false, input = "") }
        if (owner != null) {
            viewModelScope.launch {
                try { store.delete(currentId, owner) } catch (_: Exception) {}
            }
        }
    }

    fun deleteConversation(id: String) {
        val owner = uid ?: return
        viewModelScope.launch {
            try {
                store.delete(id, owner)
                if (conversationId == id) {
                    newConversation()
                }
            } catch (_: Exception) {
                showError("Não foi possível excluir a conversa.")
            }
        }
    }

    fun deleteAllConversations() {
        val owner = uid ?: return
        cancelGeneration()
        stopAudio()
        viewModelScope.launch {
            try {
                store.deleteAll(owner)
                newConversation()
            } catch (_: Exception) {
                showError("Não foi possível excluir o histórico.")
            }
        }
    }

    fun showHistory() { healthQuerySession.clear(); cancelGeneration(); mutable.update { it.copy(showHistory = true, input = "") } }
    fun openConversation(conversation: OrbConversation) {
        if (conversation.userId != uid) return
        healthQuerySession.clear()
        cancelGeneration()
        stopAudio()
        try {
            val messages = json.decodeFromString<List<ChatMessage>>(conversation.messagesJson)
            conversationId = conversation.id
            mutable.update { it.copy(messages = messages, showHistory = false, input = "", error = null) }
        } catch (_: Exception) { showError("Esta conversa não pôde ser aberta.") }
    }

    private fun save() {
        val owner = uid ?: return
        val snapshot = state.value.messages
        if (snapshot.isEmpty()) return
        val entry = OrbConversation(conversationId, owner, snapshot.first().text.take(70),
            System.currentTimeMillis(), json.encodeToString(snapshot))
        viewModelScope.launch {
            try { store.save(entry) }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { showError("Não foi possível salvar esta conversa no dispositivo.") }
        }
    }

    fun sendMessage(text: String = state.value.input, channel: InputChannel = InputChannel.TEXT) {
        val value = text.trim()
        if (value.isEmpty() || state.value.isStreaming) return
        if (value.length > 16000) { showError("Sua mensagem é muito longa. Use até 16.000 caracteres."); return }
        if (uid == null || auth.currentUser?.uid != uid) { showError("Entre na sua conta para conversar."); return }
        stopAudio()
        val user = ChatMessage(role = "user", text = value)
        mutable.update { it.copy(messages = it.messages + user, input = "", isStreaming = true,
            partialText = "", error = null, showHistory = false) }
        save()
        val version = ++revision
        val history = state.value.messages.filter { it.status !in listOf("cancelled", "error", "blocked") }
            .takeLast(30).map { it.role to it.text }
        val previousGeneration = generation
        generation = viewModelScope.launch {
            val startedAt = System.nanoTime()
            fun elapsedMs() = (System.nanoTime() - startedAt) / 1_000_000.0
            var roomMs: Double? = null
            var firstTextMs: Double? = null
            var cadenceMs = 0L
            try {
                previousGeneration?.join()
                currentCoroutineContext().ensureActive()
                val owner = uid ?: throw CancellationException("Sessão alterada")
                val contextTurn = healthQuerySession.advanceTurn(owner, conversationId)
                br.com.bragasaude.ai.BragaDebugTrace.event("RECEIVED", channel, input = value,
                    owner = owner, conversation = conversationId, turn = contextTurn)
                val nluStartedAt = System.nanoTime()
                var local = hybrid.analyze(text, channel, healthQuerySession, owner, conversationId)
                val nluMs = (System.nanoTime() - nluStartedAt) / 1_000_000.0
                br.com.bragasaude.ai.BragaDebugTrace.event("DECISION", channel, input = value, output = local,
                    owner = owner, conversation = conversationId, turn = contextTurn)
                val concrete = if (br.com.bragasaude.ai.BragaActionGate.canParse(value, local))
                    voiceParser.parse(value, null, null) else
                    br.com.bragasaude.ai.BragaActionGate.readOnlyIntent(value, local, voiceParser)
                br.com.bragasaude.ai.BragaDebugTrace.event("PARSE", channel, input = value,
                    detail = "parser=${concrete?.javaClass?.simpleName ?: "NONE"}",
                    owner = owner, conversation = conversationId, turn = contextTurn)
                val shortcut = if (local.isBloqueioSeguranca) null else when (value.lowercase()) {
                    "/pressão", "/pressao" -> "REGISTRAR_PRESSAO"
                    "/remédio", "/remedio" -> "LEMBRETES"
                    "/exame" -> "EXAMES"
                    "/emergência", "/emergencia" -> "EMERGENCIA"
                    else -> null
                }
                val actionReply = if (concrete != null) BragaChatActions.reply(concrete) else null
                if (concrete is br.com.bragasaude.domain.VoiceHealthIntent.ConversationalReply &&
                    local.route == br.com.bragasaude.ai.BragaRoute.CLARIFICATION) {
                    local = br.com.bragasaude.ai.NluOutput("conversa_parser_local", concrete.message)
                }
                local = br.com.bragasaude.ai.BragaCloudFallback.afterLocal(value, local,
                    handledLocally = actionReply != null || shortcut != null)
                if (local.fallbackFromIntent != null) br.com.bragasaude.ai.BragaDebugTrace.event(
                    "FALLBACK", channel, input = value, output = local, detail = "parser=NONE; local_exhausted",
                    owner = owner, conversation = conversationId, turn = contextTurn)
                val useLocal = local.isBloqueioSeguranca || local.isEmergencia ||
                    local.intent == "orientacao_cadastro_medicamento" || BragaHealthMemory.supports(local.intent) ||
                    (!local.delegarParaNuvem && actionReply == null)
                var deliveredLocal: br.com.bragasaude.ai.NluOutput? = null
                val reply = if (shortcut != null) {
                    val cadence = if (shortcut != "EMERGENCIA") textCadenceMs(channel, false, false) else 0L
                    if (cadence > 0) {
                        cadenceMs = cadence
                        delay(cadenceMs)
                    }
                    OrbReply(JSONObject().put("fala", "Abrir ${actionLabel(shortcut)}.")
                        .put("acao", shortcut).put("parametros", JSONObject()).toString())
                } else if (actionReply != null && !local.isBloqueioSeguranca && !local.isEmergencia &&
                    local.intent != "orientacao_cadastro_medicamento" && !BragaHealthMemory.supports(local.intent)) {
                    actionReply
                } else if (useLocal) {
                    val roomStartedAt = System.nanoTime()
                    val resolved = hybrid.resolveLocal(local, owner, channel, healthQuerySession, conversationId, contextTurn)
                    deliveredLocal = resolved
                    if (BragaHealthMemory.supports(local.intent)) roomMs = (System.nanoTime() - roomStartedAt) / 1_000_000.0
                    // Cadência mínima só no chat de texto para experiência humana; voz usa o Piper.
                    val cadence = textCadenceMs(channel, local.isEmergencia, local.isBloqueioSeguranca)
                    if (cadence > 0) {
                        cadenceMs = cadence
                        delay(cadenceMs)
                    }
                    if (auth.currentUser?.uid != owner || uid != owner || version != revision) return@launch
                    if (local.isEmergencia) navigation.send(OrbChatEvent.Emergency)
                    OrbReply(JSONObject().put("fala", resolved.respostaLocal.orEmpty())
                        .put("acao", "CONVERSA").put("parametros", JSONObject()).toString(), "Resposta local; 0 tokens de nuvem")
                } else {
                    // Mantém o escopo e os cards B1. Só abre transporte após o filtro local.
                    check(local.delegarParaNuvem) { "A entrada precisa de esclarecimento local." }
                    val scope = caregiverScope()
                    if (version != revision) return@launch
                    healthQuerySession.forgetReferences(owner, conversationId, contextTurn)
                    br.com.bragasaude.ai.BragaDebugTrace.event("CLOUD_REQUEST", channel, input = value, output = local,
                        detail = "historyTurns=${history.dropLast(1).size}", owner = owner, conversation = conversationId, turn = contextTurn)
                    gateway.open(viewModelScope)
                    val context = listOf("assistant" to GroqDynamicPrompt().build(history.dropLast(1), channel, local)) + history.takeLast(10)
                    gateway.sendRemote(context, actingAs = scope?.first, patientId = scope?.second) { partial ->
                        if (version == revision) {
                            if (partial.isNotBlank() && firstTextMs == null) firstTextMs = elapsedMs()
                            mutable.update { it.copy(partialText = BragaInputLanguage.forChannel(partial, channel)) }
                        }
                    }
                }
                if (version != revision) return@launch
                val safeContent = if (local.delegarParaNuvem) BragaRemoteContract.validate(value, reply.content) else reply.content
                val parsed = JSONObject(safeContent)
                val rawAction = parsed.optString("acao", "CONVERSA").takeUnless { it == "CONVERSA" }
                val rawParams = parsed.optJSONObject("parametros") ?: JSONObject()
                val (action, finalParams) = if (shortcut != null) {
                    shortcut to rawParams
                } else if (local.delegarParaNuvem) {
                    null to JSONObject()
                } else {
                    resolveHealthAction(value, rawAction, rawParams)
                }
                val answerText = BragaInputLanguage.forChannel(parsed.getString("fala"), channel)

                val totalMs = elapsedMs()
                val elapsedMs = totalMs.toLong()
                val timing = RoutingTiming(nluMs, roomMs, firstTextMs ?: totalMs, totalMs, cadenceMs)
                val diagnostic = JSONObject().put("nluMs", nluMs).put("roomMs", roomMs)
                    .put("firstTextMs", firstTextMs ?: totalMs).put("completedMs", totalMs).put("cadenceMs", cadenceMs).toString()
                if (shortcut != null) {
                    BragaRoutingLogger.record(channel, value, RoutingLogEntry.RoutingDecision.LOCAL_NLU, "atalho_chat", "Atalho executado no dispositivo", elapsedMs, answerText, timing)
                } else if (actionReply != null) {
                    BragaRoutingLogger.record(channel, value, RoutingLogEntry.RoutingDecision.LOCAL_NLU,
                        "acao_local", "Ação preparada localmente para revisão", elapsedMs, answerText, timing)
                } else if (useLocal) {
                    val reason = when {
                        local.isBloqueioSeguranca -> "Muralha de segurança local (0 tokens)"
                        local.isEmergencia -> "Emergência detectada localmente (SAMU 192)"
                        BragaHealthMemory.supports(local.intent) -> "Consulta local Room (histórico de saúde)"
                        else -> local.routingReason
                    }
                    BragaRoutingLogger.record(channel, value, RoutingLogEntry.RoutingDecision.LOCAL_NLU, local.intent, reason, elapsedMs, answerText, timing)
                } else {
                    BragaRoutingLogger.record(channel, value, RoutingLogEntry.RoutingDecision.CLOUD_LLM, local.intent, "Roteado para Groq Cloud LLM", elapsedMs, answerText, timing)
                }

                val answer = ChatMessage(role = "assistant", text = answerText, status = "received",
                    action = action, parameters = finalParams.toString(),
                    rawContent = if (channel == InputChannel.TEXT) JSONObject(safeContent)
                        .put("fala", answerText).toString() else safeContent,
                    metrics = listOfNotNull(reply.metrics, diagnostic).joinToString("\n"))
                mutable.update { it.copy(messages = it.messages.map { m -> if (m.id == user.id) m.copy(status = if (local.isBloqueioSeguranca) "blocked" else "received") else m } + answer,
                    isStreaming = false, partialText = "") }
                br.com.bragasaude.ai.BragaDebugTrace.event("DELIVERED", channel, input = value,
                    output = deliveredLocal ?: local, reply = answerText, detail = "action=$action; local=$useLocal",
                    owner = owner, conversation = conversationId, turn = contextTurn)
                if (useLocal && shortcut == null && actionReply == null) {
                    healthQuerySession.rememberReply(local.copy(respostaLocal = answerText,
                        hasLocalData = deliveredLocal?.hasLocalData,
                        referenceMeasuredAtMillis = deliveredLocal?.referenceMeasuredAtMillis,
                        referenceZoneId = deliveredLocal?.referenceZoneId), owner, conversationId, contextTurn)
                } else if (local.delegarParaNuvem && shortcut == null && actionReply == null) {
                    healthQuerySession.rememberExternalReply(answerText, owner, conversationId, contextTurn)
                    // Amostra da nuvem para ampliar a cobertura local e mitigar custo.
                    // Só turnos entregues pela nuvem; tabela e sync já existem no pipeline de auditoria.
                    telemetry?.logAiConversation(
                        userId = owner,
                        userPrompt = value.take(2000),
                        aiResponse = answerText.take(2000),
                        detectedIntent = listOfNotNull(local.fallbackFromIntent, local.intent).joinToString(">"),
                        rawPayload = JSONObject()
                            .put("channel", channel.name).put("route", local.route.name)
                            .put("durationMs", elapsedMs).put("validatedAction", action)
                            .put("remoteAction", rawAction).put("local", false).toString()
                    )
                } else healthQuerySession.forgetReferences(owner, conversationId, contextTurn)
                save()
                if (shortcut != null) confirmAction(answer.id)
            } catch (e: CancellationException) {
                br.com.bragasaude.ai.BragaDebugTrace.event("CANCELLED", channel, input = value, detail = "version=$version")
                throw e
            } catch (e: Exception) {
                br.com.bragasaude.ai.BragaDebugTrace.event("ERROR", channel, input = value, detail = e.javaClass.simpleName)
                if (version == revision) {
                    mutable.update { it.copy(isStreaming = false, partialText = "", error = BragaInputLanguage.forChannel(e.message ?: "Falha ao obter resposta.", channel),
                        messages = it.messages.map { m -> if (m.id == user.id) m.copy(status = "error") else m }) }
                    save()
                }
            }
        }
    }

    /**
     * D50/D51: resolve o escopo de papel (actingAs, patientId) do usuario atual.
     * Devolve "caregiver" + o patientId quando o usuario e cuidador ativo de
     * alguem; devolve null no autocuidado puro (nao envia nada ao gateway).
     */
    private suspend fun caregiverScope(): Pair<String, String>? {
        val owner = uid ?: return null
        val profile = profiles.getProfileOneShot(owner) ?: return null
        if (profile.userRole != "CAREGIVER") return null
        return try {
            familyRepository.getActiveBindingsForCaregiver(owner).first()
                .firstOrNull { it.status.equals("ACTIVE", ignoreCase = true) }
                ?.patientUserId?.takeIf { it.isNotBlank() }
                ?.let { "caregiver" to it }
        } catch (_: Exception) { null }
    }

    /** Converte ISO-8601 (do gateway) em epoch millis; fallback = agora. */
    private fun parseIsoToEpoch(iso: String): Long = try {
        if (iso.isBlank()) System.currentTimeMillis()
        else java.time.Instant.parse(iso).toEpochMilli()
    } catch (_: Exception) {
        try { java.time.LocalDateTime.parse(iso)
            .atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        } catch (_: Exception) { System.currentTimeMillis() }
    }

    fun cancelGeneration() {
        healthQuerySession.clear()
        ++revision
        generation?.cancel()
        if (state.value.isStreaming) {
            mutable.update { current -> current.copy(isStreaming = false, partialText = "",
                messages = current.messages + ChatMessage(role = "assistant",
                    text = current.partialText.ifBlank { "Resposta cancelada." }, status = "cancelled")) }
            save()
        }
    }

    fun ignoreAction(id: String) {
        mutable.update { it.copy(messages = it.messages.map { m -> if (m.id == id && m.actionStatus == "pending") m.copy(actionStatus = "cancelled") else m }) }
        save()
    }

    fun confirmAction(id: String) {
        val message = state.value.messages.find { it.id == id && it.actionStatus == "pending" } ?: return
        val action = message.action ?: return
        mutable.update { it.copy(messages = it.messages.map { m -> if (m.id == id) m.copy(actionStatus = "running") else m }) }
        viewModelScope.launch {
            try {
                val owner = uid ?: error("Entre novamente.")
                check(auth.currentUser?.uid == owner)
                val profile = profiles.getProfileOneShot(owner)
                if (uid != owner || auth.currentUser?.uid != owner) return@launch
                if (action in listOf("REGISTRAR_PRESSAO", "REGISTRAR_GLICEMIA", "REGISTRAR_AGUA", "REGISTRAR_BATIMENTOS", "REGISTRAR_OXIGENACAO", "EXAMES", "LEMBRETES") &&
                    profile?.userRole == "CAREGIVER" && profile.caregiverMode != "HYBRID") {
                    error("Ative o autocuidado no perfil para registrar seus dados.")
                }
                val p = JSONObject(message.parameters)
                val route: Screen? = when (action) {
                    "REGISTRAR_PRESSAO" -> Screen.Vitals("PRESSURE", if (p.has("sistolica") && p.has("diastolica"))
                        "${p.optInt("sistolica")}/${p.optInt("diastolica")}" else null)
                    "REGISTRAR_GLICEMIA" -> Screen.Vitals("GLUCOSE", p.optString("glicemia").takeIf { it.isNotBlank() })
                    "REGISTRAR_BATIMENTOS" -> Screen.HealthReadings("HEART_RATE", p.optString("batimentos").takeIf { it.isNotBlank() })
                    "REGISTRAR_OXIGENACAO" -> Screen.HealthReadings("OXYGEN_SATURATION", p.optString("saturacao").takeIf { it.isNotBlank() })
                    "REGISTRAR_AGUA" -> Screen.Hydration(p.optInt("quantidade_ml", 250).coerceIn(1, 5000), true)
                    "BUSCAR_ALIMENTO" -> Screen.Nutrition(p.optString("alimento"))
                    "ABRIR_LISTA_COMPRAS" -> {
                        val items = mutableListOf<String>()
                        val arr = p.optJSONArray("items")
                        if (arr != null) {
                            for (i in 0 until arr.length()) {
                                arr.optString(i)?.takeIf { it.isNotBlank() }?.let { items.add(it) }
                            }
                        }
                        Screen.Nutrition(openGroceryList = true, groceryItems = items)
                    }
                    "CONSULTAR_METRICAS" -> Screen.Report
                    "ABRIR_FAMILIA" -> Screen.FamilyConnect
                    "LEMBRETES" -> Screen.Reminders
                    "EXAMES" -> Screen.Exams
                    // D51: confirma o rascunho -> grava a consulta no dispositivo
                    // (espelha o que o gateway ja gravou ao confirmar por voz).
                    "AGENDAR_RASCUNHO" -> {
                        val owner = uid ?: error("Entre novamente.")
                        val pid = p.optString("id").takeIf { it.isNotBlank() } ?: error("Rascunho inválido.")
                        try {
                            val scope = caregiverScope()
                            val epoch = parseIsoToEpoch(p.optString("scheduledDate"))
                            familyRepository.createConsultation(
                                userId = scope?.second ?: owner,
                                title = p.optString("title").ifBlank { "Consulta" },
                                scheduledDate = epoch,
                                caregiverUserId = owner,
                                caregiverName = profile?.fullName,
                                caregiverRelation = "Cuidador",
                            )
                        } catch (_: Exception) {
                            // O gateway ja gravou; o espelho local e melhor-esforco.
                        }
                        Screen.Reminders
                    }
                    "EMERGENCIA" -> {
                        navigation.send(OrbChatEvent.Emergency)
                        if (!notifications.triggerEmergency(owner, profile?.fullName ?: "Usuário Braga", "Alerta confirmado pelo usuário no chat Braga."))
                            showError("Não foi possível avisar seus contatos. Use as opções de socorro na tela.")
                        null
                    }
                    else -> error("Ação não disponível.")
                }
                mutable.update { it.copy(messages = it.messages.map { m -> if (m.id == id) m.copy(actionStatus = "confirmed") else m }) }
                save()
                if (route != null) navigation.send(OrbChatEvent.Navigate(route))
                // Agente B1: recibo do executor; o servidor devolve a fala de
                // fechamento montada do recibo (melhor-esforço, navegação já valeu).
                try {
                    val closing = gateway.sendReceipt(action, p, UUID.randomUUID().toString())
                    if (closing != null) {
                        mutable.update { it.copy(messages = it.messages +
                            ChatMessage(role = "assistant", text = closing, status = "received")) }
                        save()
                    }
                } catch (_: Exception) { }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                mutable.update { it.copy(messages = it.messages.map { m -> if (m.id == id) m.copy(actionStatus = "pending") else m }) }
                showError(e.message ?: "Não foi possível executar a ação.")
            }
        }
    }

    fun toggleAudio(message: ChatMessage) {
        val stopping = state.value.speakingId == message.id
        stopAudio()
        if (stopping) return
        mutable.update { it.copy(speakingId = message.id) }
        audioJob = viewModelScope.launch {
            val played = audio.playSpeech(message.text, onStart = {}, onDone = {
                mutable.update { if (it.speakingId == message.id) it.copy(speakingId = null) else it }
            })
            if (!played) { mutable.update { it.copy(speakingId = null) }; showError("Áudio indisponível. Tente novamente.") }
        }
    }
    private fun stopAudio() { audioJob?.cancel(); audio.stop(); mutable.update { it.copy(speakingId = null) } }

    fun exportText(): String = state.value.messages.joinToString("\n\n") {
        "${if (it.role == "user") "Você" else "Braga"} — ${java.text.DateFormat.getDateTimeInstance().format(java.util.Date(it.timestamp))}\n${it.text}"
    }
    private fun resolveHealthAction(
        userText: String,
        rawAction: String?,
        rawParams: JSONObject
    ): Pair<String?, JSONObject> {
        // Agente B1: ações com executor real no app. Registro clínico abre
        // tela pré-preenchida (o usuário confirma lá); lista/relatório navegam.
        return when (rawAction) {
            "EMERGENCIA", "LEMBRETES", "EXAMES", "CONSULTAR_METRICAS", "ABRIR_FAMILIA", "ABRIR_LISTA_COMPRAS",
            "REGISTRAR_PRESSAO", "REGISTRAR_GLICEMIA", "REGISTRAR_AGUA",
            "REGISTRAR_BATIMENTOS", "REGISTRAR_OXIGENACAO",
            // D51: rascunho de agendamento por voz — card com botao Confirmar
            "AGENDAR_RASCUNHO" -> rawAction to rawParams
            else -> null to JSONObject()
        }
    }

    override fun onCleared() { auth.removeAuthStateListener(authListener); gateway.close(); audio.stop(); super.onCleared() }
}

fun actionLabel(action: String): String = when (action) {
    "REGISTRAR_PRESSAO" -> "pressão arterial"
    "REGISTRAR_GLICEMIA" -> "glicemia"
    "REGISTRAR_BATIMENTOS" -> "batimentos cardíacos"
    "REGISTRAR_OXIGENACAO" -> "saturação de oxigênio"
    "REGISTRAR_AGUA" -> "hidratação"
    "BUSCAR_ALIMENTO" -> "alimentação"
    "ABRIR_LISTA_COMPRAS" -> "lista de compras"
    "CONSULTAR_METRICAS" -> "relatório de saúde"
    "ABRIR_FAMILIA" -> "Família"
    "LEMBRETES" -> "lembretes de remédios"
    "EXAMES" -> "exames"
    "EMERGENCIA" -> "opções de socorro e avisar contatos"
    else -> action
}
