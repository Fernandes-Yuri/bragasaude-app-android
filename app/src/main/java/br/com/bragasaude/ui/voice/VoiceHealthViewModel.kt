package br.com.bragasaude.ui.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.bragasaude.domain.ClarificationActionType
import br.com.bragasaude.domain.ClarificationOption
import br.com.bragasaude.domain.VoiceHealthExecutor
import br.com.bragasaude.domain.VoiceHealthIntent
import br.com.bragasaude.domain.VoiceHealthParser
import br.com.bragasaude.domain.VoiceSessionControl
import br.com.bragasaude.domain.VoiceSessionCommand
import br.com.bragasaude.domain.VoiceConversationMemory
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.currentCoroutineContext
import br.com.bragasaude.domain.HydrationConversation
import br.com.bragasaude.domain.VoiceResult
import br.com.bragasaude.domain.toConfirmationDisplay
import br.com.bragasaude.data.local.ProfileDao
import br.com.bragasaude.data.remote.service.TelemetryService
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.channels.Channel
import br.com.bragasaude.ai.BragaHybridEvent
import br.com.bragasaude.ai.BragaSpeechBuffer
import br.com.bragasaude.ai.InputChannel
import br.com.bragasaude.ai.BragaHealthMemory
import br.com.bragasaude.data.remote.ai.NeuralAudioPlayer
import br.com.bragasaude.data.remote.ai.BragaLocalAiClient
import br.com.bragasaude.data.local.VitalSignDao
import br.com.bragasaude.data.local.DailyMetricsDao
import kotlinx.coroutines.withContext
import java.util.Locale
import javax.inject.Inject

/**
 * Estado UI do Módulo de Registro por Voz.
 */
sealed interface VoiceUiState {
    object Idle : VoiceUiState
    object Listening : VoiceUiState
    data class Parsed(val intent: VoiceHealthIntent) : VoiceUiState
    data class Clarifying(val intent: VoiceHealthIntent.ClarificationRequired) : VoiceUiState
    object Saving : VoiceUiState
    data class Saved(val summary: String, val isConversational: Boolean = false) : VoiceUiState
    data class Error(val message: String, val retryable: Boolean, val isSpokenOnly: Boolean = false) : VoiceUiState
}

/**
 * Eventos de navegação autônoma disparados pelo Copiloto de Voz.
 */
sealed interface VoiceNavigationEvent {
    data class NavigateToVitals(val type: String, val value: String) : VoiceNavigationEvent
    data class NavigateToHydration(val addMl: Int? = null, val openCustomDialog: Boolean = true) : VoiceNavigationEvent
    data class NavigateToNutrition(val searchFoodQuery: String? = null, val openGroceryList: Boolean = false) : VoiceNavigationEvent
    object OpenEmergencyDialog : VoiceNavigationEvent
}

/**
 * UI-V01 / D14 — Orquestrador do Copiloto de Voz Metamórfico.
 *
 * Elimina o modal bloqueante. Agora emite eventos de navegação autônoma e preenchimento
 * de formulários reais, mantendo a regra rígida de validação manual do usuário para salvar.
 */
@HiltViewModel
class VoiceHealthViewModel @Inject constructor(
    private val parser: VoiceHealthParser,
    private val executor: VoiceHealthExecutor,
    private val telemetryService: TelemetryService,
    private val profileDao: ProfileDao,
    private val auth: FirebaseAuth,
    private val localAiClient: BragaLocalAiClient,
    private val neuralAudioPlayer: NeuralAudioPlayer,
    private val vitalSignDao: VitalSignDao,
    private val dailyMetricsDao: DailyMetricsDao,
    private val notificationClient: br.com.bragasaude.data.remote.service.NotificationClient,
    val voiceProfileManager: br.com.bragasaude.data.local.voice.VoiceProfileManager,
    private val hybridOrchestrator: br.com.bragasaude.ai.BragaHybridOrchestrator
) : ViewModel() {

    private var currentUserRole: String? = null
    private var currentCaregiverMode: String? = null
    private var currentUserGender: String? = null
    private var currentUserName: String? = null
    
    private fun getCurrentUserId(): String {
        return auth.currentUser?.uid ?: "anonymous"
    }

    private val _state = MutableStateFlow<VoiceUiState>(VoiceUiState.Idle)
    val state: StateFlow<VoiceUiState> = _state.asStateFlow()
    private val _partialResponse = MutableStateFlow("")
    val partialResponse: StateFlow<String> = _partialResponse.asStateFlow()
    private var speechJob: kotlinx.coroutines.Job? = null
    private val hydrationConversation = HydrationConversation()

    // Modo Live Streaming Contínuo (ChatGPT / Gemini Live style)
    private val _isLiveMode = MutableStateFlow(false)
    val isLiveMode: StateFlow<Boolean> = _isLiveMode.asStateFlow()

    private val _events = MutableSharedFlow<VoiceEvent>()
    val events: SharedFlow<VoiceEvent> = _events.asSharedFlow()

    private val _navigationEvent = MutableSharedFlow<VoiceNavigationEvent>(extraBufferCapacity = 1)
    val navigationEvent: SharedFlow<VoiceNavigationEvent> = _navigationEvent.asSharedFlow()

    // Nível de áudio em dB vindo do SpeechRecognizer (alimenta o Orb audio-reativo)
    private val _audioRmsDb = MutableStateFlow(-2f)
    val audioRmsDb: StateFlow<Float> = _audioRmsDb.asStateFlow()

    // Transcrição parcial ao vivo enquanto o usuário fala
    private val _liveTranscription = MutableStateFlow("")
    val liveTranscription: StateFlow<String> = _liveTranscription.asStateFlow()

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val _closeEvent = MutableSharedFlow<Unit>()
    val closeEvent: SharedFlow<Unit> = _closeEvent.asSharedFlow()

    private val voiceSession = VoiceSessionControl()
    private var restartJob: kotlinx.coroutines.Job? = null
    private var playbackJob: kotlinx.coroutines.Job? = null
    private var speechGeneration = 0L
    private var activeTtsId: String? = null
    private val conversationMemory = VoiceConversationMemory()
    private var onTtsDoneListener: (() -> Unit)? = null

    private var speechRecognizer: SpeechRecognizer? = null
    private var currentContext: Context? = null
    private var textToSpeech: TextToSpeech? = null
    private var isTtsReady: Boolean = false

    init {
        viewModelScope.launch {
            try {
                val uid = auth.currentUser?.uid
                if (uid != null) {
                    val p = profileDao.getProfileOneShot(uid)
                    currentUserRole = p?.userRole
                    currentCaregiverMode = p?.caregiverMode
                    currentUserGender = p?.gender
                    currentUserName = p?.fullName
                }
            } catch (e: Exception) {
                android.util.Log.w("VoiceHealthVM", "Falha ao carregar perfil para assistente de voz: ${e.message}")
            }
        }
    }

    /**
     * Chamado sempre que qualquer fala da assistente termina (ou é cancelada).
     * No modo Live Streaming, retoma a escuta automaticamente após um breve intervalo.
     */
    fun onSpeechFinished() {
        if (_isLiveMode.value) scheduleListeningRestart(350)
        else _state.value = VoiceUiState.Idle
    }

    private fun scheduleListeningRestart(waitMs: Long) {
        restartJob?.cancel()
        val ticket = speechGeneration
        restartJob = viewModelScope.launch(Dispatchers.Main) {
            delay(waitMs)
            if (_isLiveMode.value && ticket == speechGeneration) {
                restartJob = null
                currentContext?.let { startListening(it) }
            }
        }
    }

    fun interruptAndListen(context: Context) {
        speechJob?.cancel()
        _partialResponse.value = ""
        voiceSession.invalidate()
        stopSpeaking()
        startListening(context)
    }

    private fun pauseConversation() {
        _isLiveMode.value = false
        voiceSession.reset()
        speechJob?.cancel()
        stopSpeaking()
        try { speechRecognizer?.cancel(); speechRecognizer?.destroy() } catch (_: Exception) { }
        speechRecognizer = null
        _state.value = VoiceUiState.Idle
        speak("Conversa pausada. Toque no orbe quando quiser continuar.", remember = false)
    }

    private fun handleSilence() {
        if (voiceSession.shouldPauseAfterSilence()) pauseConversation()
        else scheduleListeningRestart(400)
    }

    /**
     * Encerra o modo Live Streaming explicitamente (acionado pelo botão 'X' ou comando de voz).
     */
    fun stopLiveMode() {
        _isLiveMode.value = false
        cancel()
    }

    private fun initTts(context: Context) {
        if (textToSpeech == null) {
            textToSpeech = TextToSpeech(context.applicationContext) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    val result = textToSpeech?.setLanguage(Locale("pt", "BR"))
                    isTtsReady = (result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED)
                    textToSpeech?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) {
                            viewModelScope.launch(Dispatchers.Main) {
                                if (utteranceId != null && utteranceId == activeTtsId) _isSpeaking.value = true
                            }
                        }
                        override fun onDone(utteranceId: String?) { finishLocalTts(utteranceId) }
                        override fun onError(utteranceId: String?) { finishLocalTts(utteranceId) }
                    })
                }
            }
        }
    }

    private fun finishLocalTts(id: String?) {
        viewModelScope.launch(Dispatchers.Main) {
            if (id == null || id != activeTtsId) return@launch
            activeTtsId = null
            _isSpeaking.value = false
            val callback = onTtsDoneListener
            onTtsDoneListener = null
            callback?.invoke()
        }
    }

    fun speak(text: String, isMale: Boolean = true, remember: Boolean = true, onDone: (() -> Unit)? = null) {
        stopSpeaking()
        val sanitized = br.com.bragasaude.util.PortuguesePhoneticHelper.cleanTextForTts(text)
        if (sanitized.isBlank()) { onDone?.invoke(); return }
        conversationMemory.selectUser(getCurrentUserId())
        if (remember) conversationMemory.recordAssistant(sanitized)
        val ticket = speechGeneration
        onTtsDoneListener = onDone
        playbackJob = viewModelScope.launch {
            val played = neuralAudioPlayer.playSpeech(sanitized, true,
                onStart = { if (ticket == speechGeneration) _isSpeaking.value = true },
                onDone = {
                    if (ticket == speechGeneration) {
                        _isSpeaking.value = false
                        val callback = onTtsDoneListener
                        onTtsDoneListener = null
                        callback?.invoke()
                    }
                })
            currentCoroutineContext().ensureActive()
            if (!played && ticket == speechGeneration) speakLocalTts(sanitized, ticket)
        }
    }

    private suspend fun speakLocalTts(text: String, ticket: Long) {
        var retries = 0
        while (!isTtsReady && retries++ < 15) delay(100)
        currentCoroutineContext().ensureActive()
        if (ticket != speechGeneration) return
        val id = "braga_tts_$ticket"
        activeTtsId = id
        try {
            if (isTtsReady && textToSpeech != null) {
                val params = Bundle().apply { putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f) }
                _isSpeaking.value = true
                if (textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, params, id) == TextToSpeech.ERROR) finishLocalTts(id)
            } else finishLocalTts(id)
        } catch (e: Exception) {
            finishLocalTts(id)
        }
    }

    fun stopSpeaking() {
        speechGeneration++
        restartJob?.cancel()
        restartJob = null
        playbackJob?.cancel()
        playbackJob = null
        activeTtsId = null
        onTtsDoneListener = null
        _isSpeaking.value = false
        neuralAudioPlayer.stop()
        try { textToSpeech?.stop() } catch (_: Exception) { }
    }

    /**
     * Inicia a escuta por voz do idoso.
     * Deve ser chamado com um Context válido (Activity ou Application).
     */
    fun startListening(context: Context) {
        conversationMemory.selectUser(getCurrentUserId())
        if (!_isLiveMode.value) voiceSession.reset()
        _isLiveMode.value = true
        val listenTicket = voiceSession.begin()
        // AUD-AN08: antes guardava a Activity direto — o ViewModel sobrevive à
        // rotação, então a Activity destruída era retida até a próxima
        // startListening (leak de memória de um objeto pesado com view tree).
        // O applicationContext cumpre o mesmo papel (TTS e SpeechRecognizer
        // funcionam com ele) e nunca é destruído.
        currentContext = context.applicationContext
        initTts(context)
        
        // Parar qualquer áudio residual
        stopSpeaking()
        onTtsDoneListener = null
        
        // Limpar buffers
        _audioRmsDb.value = -2f
        _liveTranscription.value = ""
        _state.value = VoiceUiState.Listening
        
        viewModelScope.launch(Dispatchers.Main) {
            try {
                if (speechRecognizer == null) {
                    android.util.Log.i("VoiceHealthVM", "Criando nova instância de SpeechRecognizer")
                    // AUD-AN08: applicationContext — nunca retém a Activity.
                    speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context.applicationContext)
                } else {
                    speechRecognizer?.cancel()
                }
                
                if (!_isLiveMode.value || !voiceSession.accepts(listenTicket)) return@launch
                speechRecognizer?.setRecognitionListener(VoiceRecognitionListener(listenTicket))
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pt-BR")
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                }
                android.util.Log.i("VoiceHealthVM", "Iniciando escuta no SpeechRecognizer...")
                speechRecognizer?.startListening(intent)
            } catch (e: Exception) {
                android.util.Log.e("VoiceHealthVM", "Falha ao iniciar SpeechRecognizer: ${e.message}", e)
                _state.value = VoiceUiState.Error("Não foi possível iniciar o microfone.", true, isSpokenOnly = false)
            }
        }
    }

    /**
     * Para a escuta manualmente (quando o idoso solta o botão).
     */
    fun stopListening() {
        try {
            speechRecognizer?.stopListening()
        } catch (_: Exception) { }
    }

    /**
     * Descarta o estado atual e volta para Idle.
     */
    fun reset() {
        conversationMemory.clear()
        voiceSession.reset()
        hydrationConversation.reset()
        speechJob?.cancel()
        _partialResponse.value = ""
        stopSpeaking()
        _state.value = VoiceUiState.Idle
    }

    /**
     * Cancela o reconhecimento e volta para Idle.
     */
    fun cancel() {
        _isLiveMode.value = false
        voiceSession.reset()
        conversationMemory.clear()
        hydrationConversation.reset()
        speechJob?.cancel()
        _partialResponse.value = ""
        telemetryService.logVoiceEvent(getCurrentUserId(), "CANCEL", null, null)
        try { speechRecognizer?.cancel(); speechRecognizer?.destroy() } catch (_: Exception) {}
        speechRecognizer = null
        stopSpeaking()
        onTtsDoneListener = null
        _liveTranscription.value = ""
        _state.value = VoiceUiState.Idle
    }

    /**
     * Confirma a intenção parseada e persiste nos repositórios.
     */
    fun confirm(intent: VoiceHealthIntent) {
        telemetryService.logVoiceEvent(getCurrentUserId(), "CONFIRM", null, intent.javaClass.simpleName)
        if (_state.value is VoiceUiState.Saving || _state.value is VoiceUiState.Saved) return
        stopSpeaking()
        viewModelScope.launch {
            _state.value = VoiceUiState.Saving
            try {
                android.util.Log.i("VoiceHealthVM", "Persistindo intenção de voz em segundo plano: ${intent.javaClass.simpleName}")
                val summary = withContext(Dispatchers.IO) {
                    executor.execute(intent)
                }
                _state.value = VoiceUiState.Saved(summary)
                speak(summary) {
                    onSpeechFinished()
                }
            } catch (e: Exception) {
                android.util.Log.e("VoiceHealthVM", "Falha ao salvar dados de voz: ${e.message}", e)
                _state.value = VoiceUiState.Error(
                    message = "Não consegui salvar: ${e.message}",
                    retryable = true
                )
            }
        }
    }

    /**
     * Resolução de intenção ambígua após esclarecimento (diálogo multi-turno).
     */
    fun resolveClarification(option: ClarificationOption) {
        stopSpeaking()
        val currentState = _state.value
        if (currentState !is VoiceUiState.Clarifying) return

        when (option.actionType) {
            ClarificationActionType.LOG_HYDRATION_CUPS -> {
                val ml = Regex("(\\d+)\\s*ml").find(option.label)?.groupValues?.get(1)?.toIntOrNull() ?: 1000
                _navigationEvent.tryEmit(VoiceNavigationEvent.NavigateToHydration(addMl = ml, openCustomDialog = true))
                _state.value = VoiceUiState.Saved(summary = "Água ${ml}ml", isConversational = false)
                speak("Já preparei a anotação de mais $ml ml de água para você. É só tocar em Adicionar para confirmar!") {
                    onSpeechFinished()
                }
            }
            ClarificationActionType.LOG_HYDRATION_FULL -> {
                val ml = Regex("(\\d+)\\s*ml").find(option.label)?.groupValues?.get(1)?.toIntOrNull() ?: 2000
                _navigationEvent.tryEmit(VoiceNavigationEvent.NavigateToHydration(addMl = ml, openCustomDialog = true))
                _state.value = VoiceUiState.Saved(summary = "Água ${ml}ml", isConversational = false)
                speak("Já preparei a anotação de mais $ml ml de água para você. É só tocar em Adicionar para confirmar!") {
                    onSpeechFinished()
                }
            }
            ClarificationActionType.LOG_MEAL,
            ClarificationActionType.ADD_TO_GROCERY,
            ClarificationActionType.CANCEL -> {
                cancel()
            }
        }
    }

    override fun onCleared() {
        stopLiveMode()
        super.onCleared()
        try {
            speechRecognizer?.destroy()
        } catch (_: Exception) { }
        speechRecognizer = null
        try {
            neuralAudioPlayer.stop()
            textToSpeech?.stop()
            textToSpeech?.shutdown()
        } catch (_: Exception) { }
        textToSpeech = null
        currentContext = null
    }

    // ==================== RECONHECIMENTO ====================

    private inner class VoiceRecognitionListener(private val ticket: Long) : RecognitionListener {
        private fun current() = _isLiveMode.value && voiceSession.accepts(ticket)
        override fun onReadyForSpeech(params: Bundle?) {
            if (!current()) return
            _state.value = VoiceUiState.Listening
        }

        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {
            if (!current()) return
            _audioRmsDb.value = rmsdB
        }
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}

        override fun onError(error: Int) {
            if (!current()) return
            // Cancelamentos rápidos ao reabrir o modal não devem travar nem emitir erro
            if (error == SpeechRecognizer.ERROR_CLIENT) {
                return
            }
            
            android.util.Log.w("VoiceHealthVM", "VoiceRecognitionListener.onError code=$error isLiveMode=${_isLiveMode.value}")
            if (error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT || error == SpeechRecognizer.ERROR_NO_MATCH) {
                if (voiceSession.consume(ticket)) handleSilence()
                return
            }
            voiceSession.consume(ticket)

            // Outros erros técnicos reais (áudio/permissão): definir erro SEM falar
            val message = when (error) {
                SpeechRecognizer.ERROR_AUDIO -> "Problema de áudio. Verifique o microfone."
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Permissão de microfone negada."
                SpeechRecognizer.ERROR_NETWORK -> "Problema de internet. Tente offline."
                SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Tempo limite da rede."
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Reconhecedor ocupado. Aguarde um instante."
                SpeechRecognizer.ERROR_SERVER -> "Erro do servidor de voz."
                else -> "Não foi possível processar. Tente novamente."
            }
            _state.value = VoiceUiState.Error(message, retryable = true, isSpokenOnly = false)
        }

        override fun onResults(results: Bundle?) {
            if (!_isLiveMode.value || !voiceSession.consume(ticket)) return
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val bestMatch = matches?.firstOrNull()
            
            
            if (bestMatch.isNullOrBlank()) {
                handleSilence()
                return
            }
            voiceSession.hasSpeech()
            _liveTranscription.value = bestMatch
            conversationMemory.selectUser(getCurrentUserId())
            val nlu = hybridOrchestrator.analyze(bestMatch, InputChannel.VOICE)
            if (nlu.isBloqueioSeguranca) {
                speechJob?.cancel()
                speechJob = viewModelScope.launch { processUserSpeech(bestMatch, nlu) }
                return
            }
            when (VoiceSessionCommand.parse(bestMatch)) {
                VoiceSessionCommand.CLOSE -> {
                    stopLiveMode()
                    viewModelScope.launch { _closeEvent.emit(Unit) }
                    return
                }
                VoiceSessionCommand.PAUSE -> { pauseConversation(); return }
                VoiceSessionCommand.REPEAT -> {
                    speak(conversationMemory.lastResponse.ifBlank { "Ainda não tenho uma resposta nesta conversa para repetir." }, remember = false) { onSpeechFinished() }
                    return
                }
                null -> Unit
            }

            speechJob?.cancel()
            speechJob = viewModelScope.launch {
                try {
                    processUserSpeech(bestMatch, nlu)
                } finally {
                    if (currentCoroutineContext()[kotlinx.coroutines.Job] === speechJob) {
                        _partialResponse.value = ""
                    }
                }
            }
        }

        private fun showLocalResponse(output: br.com.bragasaude.ai.NluOutput) {
            val reply = output.respostaLocal.orEmpty()
            _state.value = VoiceUiState.Saved(reply, isConversational = true)
            if (output.isEmergencia) {
                _navigationEvent.tryEmit(VoiceNavigationEvent.OpenEmergencyDialog)
            }
            val reason = when {
                output.isBloqueioSeguranca -> "Muralha de segurança (voz)"
                output.isEmergencia -> "Emergência SAMU 192 (voz)"
                br.com.bragasaude.ai.BragaHealthMemory.supports(output.intent) -> "Consulta local Room (histórico de saúde - voz)"
                else -> "NLU nativo on-device (voz)"
            }
            br.com.bragasaude.ai.BragaRoutingLogger.record(
                channel = br.com.bragasaude.ai.InputChannel.VOICE,
                input = conversationMemory.snapshot().lastOrNull()?.second.orEmpty(),
                decision = br.com.bragasaude.ai.RoutingLogEntry.RoutingDecision.LOCAL_NLU,
                intent = output.intent,
                reason = reason,
                durationMs = output.tempoMs.toLong(),
                previewResponse = reply
            )
            speak(reply) { onSpeechFinished() }
        }

        private suspend fun processUserSpeech(bestMatch: String, local: br.com.bragasaude.ai.NluOutput) {
            val owner = getCurrentUserId()
            conversationMemory.selectUser(owner)
            // Antes de preferências, parser, telemetria de conversa ou qualquer rede.
            if (local.isBloqueioSeguranca) {
                hydrationConversation.reset()
                showLocalResponse(local)
                return
            }
            val history = conversationMemory.snapshot()
            conversationMemory.recordUser(bestMatch)
            if (local.isEmergencia || local.intent == "orientacao_cadastro_medicamento") {
                hydrationConversation.reset()
                showLocalResponse(local)
                return
            }
            try {
                if (BragaHealthMemory.supports(local.intent)) {
                    val reply = hybridOrchestrator.resolveLocal(local, owner, InputChannel.VOICE)
                    currentCoroutineContext().ensureActive()
                    if (owner == getCurrentUserId()) showLocalResponse(reply)
                    return
                }
                // Mantém a preferência confirmada existente, apenas para registro de copos.
                // A muralha e as emergências já retornaram antes desta consulta ao gateway.
                val cupPreference = if (Regex("(?i)\\bcopos?\\b").containsMatchIn(bestMatch) &&
                    Regex("(?i)\\b(bebi|tomei|registra|registre|anota|anote)\\b").containsMatchIn(bestMatch)) {
                    localAiClient.loadCupPreference()
                } else null
                currentCoroutineContext().ensureActive()
                if (owner != getCurrentUserId()) return
                val hydrationReply = hydrationConversation.respond(
                    bestMatch, owner,
                    allowed = currentUserRole != "CAREGIVER" || currentCaregiverMode == "HYBRID",
                    defaultCupMl = cupPreference
                )
                when (hydrationReply) {
                    is HydrationConversation.Reply.Say -> {
                        _state.value = VoiceUiState.Saved(hydrationReply.text, isConversational = true)
                        speak(hydrationReply.text) { onSpeechFinished() }
                        return
                    }
                    is HydrationConversation.Reply.Review -> {
                        val intent = parser.parse("${hydrationReply.amountMl} ml de água", currentUserRole, currentCaregiverMode)
                        handleIntent(bestMatch, intent)
                        return
                    }
                    null -> Unit
                }
                // Registros concretos continuam preparados localmente para confirmação.
                val intent = parser.parse(bestMatch, currentUserRole, currentCaregiverMode)
                if (intent !is VoiceHealthIntent.Unknown && intent !is VoiceHealthIntent.ConversationalReply) {
                    handleIntent(bestMatch, intent)
                    return
                }
                if (!local.delegarParaNuvem) {
                    showLocalResponse(local)
                    return
                }
                if (currentUserRole != "CAREGIVER" || currentCaregiverMode == "HYBRID") {
                    br.com.bragasaude.domain.HealthReadingInput.spoken(bestMatch)?.let { draft ->
                        _navigationEvent.tryEmit(VoiceNavigationEvent.NavigateToVitals(draft.metric,
                            if (draft.metric == "HEART_RATE") draft.value.toInt().toString() else draft.value.toString()))
                        speak("Confira o valor na tela e toque em salvar para registrar a medição.") { onSpeechFinished() }
                        return
                    }
                }
                streamHybridResponse(bestMatch, history, owner)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) {
                currentCoroutineContext().ensureActive()
                if (owner != getCurrentUserId()) return
                stopSpeaking()
                val reply = "Não consegui concluir a resposta agora. Você pode tentar novamente ou procurar orientação de um profissional de saúde."
                _state.value = VoiceUiState.Error(reply, retryable = true, isSpokenOnly = true)
                speak(reply) { onSpeechFinished() }
            }
        }

        private suspend fun streamHybridResponse(
            speech: String, history: List<Pair<String, String>>, owner: String
        ) = coroutineScope {
            stopSpeaking()
            val generation = speechGeneration
            _partialResponse.value = ""
            _state.value = VoiceUiState.Saving
            val phrases = Channel<String>(32)
            val buffer = BragaSpeechBuffer()
            var completed = ""
            val player = launch {
                for (phrase in phrases) {
                    currentCoroutineContext().ensureActive()
                    if (owner != getCurrentUserId()) throw kotlinx.coroutines.CancellationException("Sessão alterada")
                    val finished = kotlinx.coroutines.CompletableDeferred<Unit>()
                    val played = neuralAudioPlayer.playSpeech(phrase,
                        onStart = { viewModelScope.launch {
                            if (generation == speechGeneration) _isSpeaking.value = true
                        } },
                        onDone = { finished.complete(Unit) })
                    if (played) finished.await()
                }
            }
            playbackJob = player
            try {
                hybridOrchestrator.respond(speech, history, InputChannel.VOICE, owner).collect { event ->
                    currentCoroutineContext().ensureActive()
                    if (owner != getCurrentUserId()) throw kotlinx.coroutines.CancellationException("Sessão alterada")
                    when (event) {
                        is BragaHybridEvent.Local -> showLocalResponse(event.output)
                        is BragaHybridEvent.Delta -> {
                            _partialResponse.value += event.text
                            buffer.append(event.text).forEach { phrases.send(it) }
                        }
                        is BragaHybridEvent.Completed -> {
                            completed = event.text
                            buffer.finish().takeIf { it.isNotBlank() }?.let { phrases.send(it) }
                        }
                    }
                }
                phrases.close()
                player.join()
                if (completed.isNotBlank() && generation == speechGeneration && owner == getCurrentUserId()) {
                    br.com.bragasaude.ai.BragaRoutingLogger.record(
                        channel = br.com.bragasaude.ai.InputChannel.VOICE,
                        input = speech,
                        decision = br.com.bragasaude.ai.RoutingLogEntry.RoutingDecision.CLOUD_LLM,
                        intent = "conversa_incompreendida_fallback",
                        reason = "Streaming de voz em nuvem (Groq)",
                        durationMs = 0L,
                        previewResponse = completed
                    )
                    conversationMemory.recordAssistant(completed)
                    _state.value = VoiceUiState.Saved(completed, isConversational = true)
                    _isSpeaking.value = false
                    onSpeechFinished()
                }
            } finally {
                phrases.cancel()
                player.cancel()
                if (playbackJob === player) playbackJob = null
                if (generation == speechGeneration) {
                    neuralAudioPlayer.stop()
                    _isSpeaking.value = false
                }
            }
        }

        private suspend fun generateMetricsSummary(tipoMetrica: String?): String = withContext(Dispatchers.IO) {
            val uid = getCurrentUserId()
            val allVitals = try { vitalSignDao.getAllOneShot(uid) } catch (_: Exception) { emptyList() }
            val todayStart = java.util.Calendar.getInstance().apply {
                set(java.util.Calendar.HOUR_OF_DAY, 0)
                set(java.util.Calendar.MINUTE, 0)
                set(java.util.Calendar.SECOND, 0)
                set(java.util.Calendar.MILLISECOND, 0)
            }.timeInMillis
            val waterConsumed = allVitals
                .filter { it.hydrationMl != null && it.hydrationMl > 0 && it.measuredAt.time >= todayStart }
                .sumOf { it.hydrationMl ?: 0 }
            val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
            val todayMetrics = try { dailyMetricsDao.getByDate(uid, today) } catch (_: Exception) { null }
            val profile = try { profileDao.getProfileOneShot(uid) } catch (_: Exception) { null }

            when (tipoMetrica?.uppercase()) {
                "PRESSAO", "PRESSÃO" -> {
                    val latestBp = allVitals.firstOrNull { it.systolicPressure != null && it.diastolicPressure != null }
                    if (latestBp != null) {
                        val sys = latestBp.systolicPressure!!
                        val dia = latestBp.diastolicPressure!!
                        val sysFmt = if (sys > 30) sys / 10 else sys
                        val diaFmt = if (dia > 20) dia / 10 else dia
                        val status = when {
                            sys < 120 && dia < 80 -> "Segundo as diretrizes da Sociedade Brasileira de Cardiologia, valores em torno de 12 por 8 costumam ser considerados ótimos."
                            sys in 120..129 && dia < 80 -> "Pela Sociedade Brasileira de Cardiologia, essa faixa sugere atenção preventiva. Uma boa prática é manter seus hábitos saudáveis."
                            sys in 130..139 || dia in 80..89 -> "Pelas diretrizes da Sociedade Brasileira de Cardiologia, está em faixa de atenção. Sugiro levar essas anotações para conversar com seu médico."
                            else -> "Pela Sociedade Brasileira de Cardiologia, esse valor está acima do padrão habitual. Recomendo consultar seu médico para uma avaliação individual."
                        }
                        "Sua última pressão registrada foi $sysFmt por $diaFmt. $status Lembrando que estes dados são informativos para apoiar sua consulta médica!"
                    } else {
                        "Você ainda não registrou sua pressão hoje. Quando medir, é só me dizer, por exemplo: minha pressão deu 12 por 8!"
                    }
                }
                "GLICEMIA", "GLICOSE" -> {
                    val latestGlucose = allVitals.firstOrNull { it.glucoseLevel != null }
                    if (latestGlucose != null) {
                        val glyc = latestGlucose.glucoseLevel!!
                        val status = when {
                            glyc < 70 -> "De acordo com a Sociedade Brasileira de Diabetes, valores abaixo de 70 sugerem atenção para hipoglicemia. Se sentir tontura, considere buscar orientação médica."
                            glyc in 70..99 -> "Conforme a Sociedade Brasileira de Diabetes, essa é a faixa de referência usual de jejum para adultos saudáveis."
                            glyc in 100..125 -> "Pela Sociedade Brasileira de Diabetes, essa faixa é considerada de atenção. Sugiro compartilhar esses registros com seu profissional de saúde."
                            else -> "Pela Sociedade Brasileira de Diabetes, esse valor está acima do intervalo de referência. Sugiro manter o registro e apresentar ao seu médico."
                        }
                        "Sua última glicemia registrada foi de $glyc miligramas por decilitro. $status"
                    } else {
                        "Não encontrei registros de glicemia recentes. Você pode me dizer 'minha glicemia deu 105' para eu anotar para você!"
                    }
                }
                "AGUA", "ÁGUA", "HIDRATACAO", "HIDRATAÇÃO" -> {
                    val waterTarget = profile?.hydrationTargetMl ?: 2000
                    val percent = if (waterTarget > 0) (waterConsumed * 100) / waterTarget else 0
                    if (waterConsumed <= 0) {
                        "Você ainda não registrou água hoje. Que tal beber um copo de água agora para começar a cuidar da sua hidratação? Sua meta é de $waterTarget ml."
                    } else {
                        "Você já registrou $waterConsumed ml de água hoje, alcançando $percent% da sua meta diária de $waterTarget ml. Continue assim!"
                    }
                }
                else -> {
                    val latestBp = allVitals.firstOrNull { it.systolicPressure != null && it.diastolicPressure != null }
                    val steps = todayMetrics?.steps ?: 0
                    val bpPart = if (latestBp != null) {
                        val sysFmt = if (latestBp.systolicPressure!! > 30) latestBp.systolicPressure!! / 10 else latestBp.systolicPressure!!
                        val diaFmt = if (latestBp.diastolicPressure!! > 20) latestBp.diastolicPressure!! / 10 else latestBp.diastolicPressure!!
                        "Sua última pressão foi $sysFmt por $diaFmt"
                    } else "Ainda não registrou pressão hoje"

                    "Resumo dos seus registros: $bpPart, $waterConsumed ml de água e $steps passos. Segundo o Ministério da Saúde e a OMS, a constância em hábitos saudáveis é a melhor prevenção. Continue registrando para compartilhar com seu médico!"
                }
            }
        }

        private fun handleIntent(bestMatch: String, intent: VoiceHealthIntent) {
            when (intent) {
                is VoiceHealthIntent.BloodPressure -> {
                    val systolic = intent.systolic
                    val diastolic = intent.diastolic
                    val sysFmt = if (systolic > 30) systolic / 10 else systolic
                    val diaFmt = if (diastolic > 20) diastolic / 10 else diastolic
                    telemetryService.logVoiceEvent(getCurrentUserId(), "PARSED", bestMatch, "BloodPressure")
                    
                    _navigationEvent.tryEmit(VoiceNavigationEvent.NavigateToVitals("PRESSURE", "$systolic/$diastolic"))
                    _state.value = VoiceUiState.Saved(summary = "Pressão $systolic/$diastolic", isConversational = false)
                    
                    // Alerta Clínico para Cuidadores se pressão fora de faixa
                    if (systolic >= 140 || diastolic >= 90) {
                        viewModelScope.launch {
                            notificationClient.sendAlert(
                                type = "ALERT_CRITICAL",
                                title = "Aviso de Pressão Arterial",
                                message = "Registro de pressão elevada detectado: $sysFmt por $diaFmt mmHg.",
                                sourceUserId = getCurrentUserId(),
                                targetRole = "CAREGIVER",
                                metricValue = "$systolic/$diastolic"
                            )
                        }
                    }

                    val spokenPrompt = "Já preenchi $sysFmt por $diaFmt aqui para você. De acordo com a Sociedade Brasileira de Cardiologia, o acompanhamento frequente é fundamental para apoiar seu médico. Confira os valores e toque em salvar!"
                    speak(spokenPrompt) {
                        onSpeechFinished()
                    }
                }
                is VoiceHealthIntent.Glucose -> {
                    val glyc = intent.glucoseMgDl
                    telemetryService.logVoiceEvent(getCurrentUserId(), "PARSED", bestMatch, "Glucose")
                    
                    _navigationEvent.tryEmit(VoiceNavigationEvent.NavigateToVitals("GLUCOSE", "$glyc"))
                    _state.value = VoiceUiState.Saved(summary = "Glicemia $glyc", isConversational = false)

                    // Alerta Clínico para Cuidadores se glicemia fora de faixa
                    if (glyc < 70 || glyc > 200) {
                        viewModelScope.launch {
                            notificationClient.sendAlert(
                                type = "ALERT_CRITICAL",
                                title = "Aviso de Glicemia",
                                message = "Registro de glicemia fora da faixa: $glyc mg/dL.",
                                sourceUserId = getCurrentUserId(),
                                targetRole = "CAREGIVER",
                                metricValue = "$glyc"
                            )
                        }
                    }
                    val spokenPrompt = "Já preenchi $glyc de glicemia para você. A Sociedade Brasileira de Diabetes sugere manter esse registro regular para o seu acompanhamento médico. Confira e toque em salvar!"
                    speak(spokenPrompt) {
                        onSpeechFinished()
                    }
                }
                is VoiceHealthIntent.Hydration -> {
                    val ml = intent.amountMl
                    telemetryService.logVoiceEvent(getCurrentUserId(), "PARSED", bestMatch, "Hydration")
                    
                    _navigationEvent.tryEmit(VoiceNavigationEvent.NavigateToHydration(addMl = ml, openCustomDialog = true))
                    _state.value = VoiceUiState.Saved(summary = "Água ${ml}ml", isConversational = false)
                    val spokenPrompt = "Preparei $ml ml de água na tela. Toque em Adicionar para confirmar."
                    speak(spokenPrompt) {
                        onSpeechFinished()
                    }
                }
                is VoiceHealthIntent.QueryPatientStatus -> {
                    viewModelScope.launch {
                        _state.value = VoiceUiState.Saved(summary = "Métricas", isConversational = true)
                        val summary = withContext(Dispatchers.IO) {
                            if (currentUserRole == "CAREGIVER") {
                                executor.execute(intent)
                            } else {
                                generateMetricsSummary(intent.metric)
                            }
                        }
                        speak(summary) {
                            onSpeechFinished()
                        }
                        telemetryService.logVoiceEvent(getCurrentUserId(), "TTS_COMPLETE", null, "METRICS_QUERY")
                    }
                }
                is VoiceHealthIntent.ConversationalReply -> {
                    _state.value = VoiceUiState.Saved(summary = "", isConversational = true)
                    speak(intent.message) {
                        onSpeechFinished()
                    }
                    telemetryService.logVoiceEvent(getCurrentUserId(), "TTS_COMPLETE", null, "CONVERSATIONAL")
                }
                is VoiceHealthIntent.ClarificationRequired -> {
                    _state.value = VoiceUiState.Clarifying(intent)
                    speak(intent.questionPrompt)
                }
                is VoiceHealthIntent.Unknown -> {
                    val prompt = "Não entendi bem. Diga por exemplo: \"minha pressão deu 12 por 8\", \"minha glicemia deu 105\" ou \"bebi um copo de água\"."
                    telemetryService.logVoiceEvent(getCurrentUserId(), "UNKNOWN", bestMatch, null)
                    _state.value = VoiceUiState.Error(
                        message = prompt,
                        retryable = true,
                        isSpokenOnly = true
                    )
                    speak(prompt) {
                        onSpeechFinished()
                    }
                }
                else -> {
                    telemetryService.logVoiceEvent(getCurrentUserId(), "PARSED", bestMatch, intent.javaClass.simpleName)
                    _state.value = VoiceUiState.Parsed(intent)
                    val display = intent.toConfirmationDisplay()
                    speak(display.spokenQuestion) {
                        onSpeechFinished()
                    }
                }
            }
        }

        override fun onPartialResults(partialResults: Bundle?) {
            if (!current()) return
            val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val partial = matches?.firstOrNull()
            if (!partial.isNullOrBlank()) {
                _liveTranscription.value = partial
            }
        }
        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    sealed interface VoiceEvent {
        data class ShowToast(val message: String) : VoiceEvent
    }
}
