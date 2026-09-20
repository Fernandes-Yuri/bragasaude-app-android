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
import br.com.bragasaude.data.remote.ai.BragaLocalAiClient
import br.com.bragasaude.data.remote.ai.BragaAiResult
import br.com.bragasaude.data.remote.ai.NeuralAudioPlayer
import br.com.bragasaude.domain.ConversationalAudioOrchestrator
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
    private val audioOrchestrator: ConversationalAudioOrchestrator,
    private val vitalSignDao: VitalSignDao,
    private val dailyMetricsDao: DailyMetricsDao,
    private val notificationClient: br.com.bragasaude.data.remote.service.NotificationClient
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
        audioOrchestrator.stop()
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
        currentContext = context
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
                    speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
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
            
            telemetryService.logVoiceEvent(getCurrentUserId(), "PARSE", bestMatch, null)
            
            if (bestMatch.isNullOrBlank()) {
                handleSilence()
                return
            }
            voiceSession.hasSpeech()
            _liveTranscription.value = bestMatch
            conversationMemory.selectUser(getCurrentUserId())
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
                    processUserSpeech(bestMatch)
                } finally {
                    _partialResponse.value = ""
                }
            }
        }

        private val emergencyKeywords = listOf(
            "dor no peito", "aperto no peito", "falta de ar", "falta de ar forte", "nao consigo respirar", "não consigo respirar",
            "formigamento no braco", "formigamento no braço", "formigamento", "dormencia", "dormência",
            "perdi a forca", "perdi a força", "desmaio", "desmaiei", "tontura muito forte", "tontura forte",
            "coração disparado", "taquicardia forte", "visao escura", "visão escura", "vomitando sangue",
            "socorro", "ajuda rapido", "ajuda rápido", "passando muito mal", "infarto", "avc", "derrame"
        )

        private suspend fun processUserSpeech(bestMatch: String) {
            try {
                conversationMemory.selectUser(getCurrentUserId())
                conversationMemory.recordUser(bestMatch)
                val ownerAtStart = getCurrentUserId()
                val cupPreference = if (Regex("(?i)\\bcopos?\\b").containsMatchIn(bestMatch)) {
                    localAiClient.loadCupPreference()
                } else null
                if (getCurrentUserId() != ownerAtStart) return
                val hydrationReply = hydrationConversation.respond(
                    bestMatch,
                    getCurrentUserId(),
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
                        // Reutiliza a validação existente, inclusive clarificação de volumes altos.
                        val intent = parser.parse("${hydrationReply.amountMl} ml de água", currentUserRole, currentCaregiverMode)
                        handleIntent(bestMatch, intent)
                        return
                    }
                    null -> Unit
                }
                val friendlyName = br.com.bragasaude.util.PortuguesePhoneticHelper.toTtsFriendlyName(currentUserName)
                val nameSuffix = if (friendlyName.isNotBlank()) ", $friendlyName" else ""

                // 1. Camada 1: Edge-First Local Instantâneo (0.001s)
                // Se o usuário falou um comando clínico concreto (Pressão, Glicemia, Hidratação, Refeição, Remédio),
                // ou uma saudação/gentileza (Boa noite, Bom dia, Olá, Obrigado), o parser local executa na hora com latência zero.
                val localIntent = parser.parse(
                    rawTranscript = bestMatch, 
                    userRole = currentUserRole,
                    caregiverMode = currentCaregiverMode
                )

                // Saudações e respostas conversacionais diretas: resposta calorosa instantânea (0ms), sem filler e sem rede
                if (localIntent is VoiceHealthIntent.ConversationalReply) {
                    val replyMessage = localIntent.message
                    _state.value = VoiceUiState.Saved(summary = replyMessage, isConversational = true)
                    telemetryService.logVoiceEvent(getCurrentUserId(), "CONVERSATIONAL_REPLY", bestMatch, "EDGE_LOCAL_0MS")
                    speak(replyMessage) {
                        onSpeechFinished()
                    }
                    return
                }

                // Intenções clínicas concretas (Pressão, Glicemia, Água, Refeição, etc.)
                if (localIntent !is VoiceHealthIntent.Unknown) {
                    handleIntent(bestMatch, localIntent)
                    return
                }

                // 2. Detecção Instantânea de Sintomas Agudos de Emergência (Layer 1 Heuristic)
                val isEmergency = emergencyKeywords.any { bestMatch.contains(it, ignoreCase = true) }
                if (isEmergency) {
                    _state.value = VoiceUiState.Saved(summary = "Central de Emergência Aberta", isConversational = true)
                    _navigationEvent.tryEmit(VoiceNavigationEvent.OpenEmergencyDialog)
                    telemetryService.logVoiceEvent(getCurrentUserId(), "EMERGENCY_DETECTED", bestMatch, "VOICE_HEURISTIC")
                    notificationClient.triggerEmergency(getCurrentUserId(), currentUserName ?: "Usuário", "Relatou sintomas agudos via voz: $bestMatch")

                    val emergencySpeech = "Atenção$nameSuffix! Sintomas como esse no peito e no corpo exigem avaliação médica imediata. O mais seguro e prudente é não esperar: procure um pronto atendimento ou acione o socorro, combinado? Já coloquei as opções de ajuda na sua tela!"
                    speak(emergencySpeech) {
                        onSpeechFinished()
                    }
                    return
                }

                if (currentUserRole != "CAREGIVER" || currentCaregiverMode == "HYBRID") {
                    br.com.bragasaude.domain.HealthReadingInput.spoken(bestMatch)?.let { draft ->
                        _navigationEvent.tryEmit(VoiceNavigationEvent.NavigateToVitals(draft.metric, if (draft.metric == "HEART_RATE") draft.value.toInt().toString() else draft.value.toString()))
                        speak("Confira o valor na tela e toque em salvar para registrar a medição.") {
                            onSpeechFinished()
                        }
                        return
                    }
                }

                // 3. Camada 2: Qwen Homelab (Lenovo G460) para Diálogo Livre e Dúvidas Complexas de Saúde
                _state.value = VoiceUiState.Saving

                // Inicia o fluxo conversacional em 3 Atos:
                // Ato 1: Filler Neutro 0ms (sem nome)
                // Ato 2: Gesto Vocal Condicional ("Hummm...") se a IA demorar
                // Ato 3: Chegada da resposta com re-entrada afetuosa e nome fonético
                val aiSpeechDeferred = kotlinx.coroutines.CompletableDeferred<String?>()

                audioOrchestrator.startThreeActFlow(
                    scope = viewModelScope,
                    aiSpeechDeferred = aiSpeechDeferred,
                    onSpeakingStateChanged = { speaking ->
                        _isSpeaking.value = speaking
                    },
                    onDone = {
                        onSpeechFinished()
                    }
                )

                val aiResult = try {
                    localAiClient.interpretSpeech(bestMatch, history = conversationMemory.snapshot(),
                        onPartial = { partial ->
                            _partialResponse.value = partial
                        })
                } catch (e: kotlinx.coroutines.CancellationException) {
                    aiSpeechDeferred.cancel()
                    throw e
                } catch (e: Exception) {
                    android.util.Log.w("VoiceHealthVM", "Aviso: Falha ao interpretar fala com IA local: ${e.message}. Acionando fallback gracioso.")
                    null
                }

                if (aiResult != null && aiResult.tipo != "DESCONHECIDO") {
                    currentCoroutineContext().ensureActive()
                    val finalSpeech = prepareAiSpeech(bestMatch, aiResult, friendlyName)
                    conversationMemory.recordAssistant(finalSpeech)
                    aiSpeechDeferred.complete(finalSpeech)
                    applyAiUiState(bestMatch, aiResult)
                    return
                }

                // 4. Fallback gracioso: Se o homelab estiver offline ou demorar, acolher com o parser local
                val fallbackIntent = parser.parse(
                    rawTranscript = bestMatch, 
                    userRole = currentUserRole,
                    caregiverMode = currentCaregiverMode
                )
                val fallbackSpeech = getFallbackSpeech(fallbackIntent, friendlyName)
                conversationMemory.recordAssistant(fallbackSpeech)
                aiSpeechDeferred.complete(fallbackSpeech)
                applyIntentUiState(bestMatch, fallbackIntent)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                android.util.Log.w("VoiceHealthVM", "Falha no processamento de voz, acionando fallback: ${e.message}")
                val fallbackIntent = parser.parse(
                    rawTranscript = bestMatch, 
                    userRole = currentUserRole,
                    caregiverMode = currentCaregiverMode
                )
                handleIntent(bestMatch, fallbackIntent)
            }
        }

        private fun formatConversationalReentry(speech: String, friendlyName: String? = null): String {
            return speech.trim()
        }

        private suspend fun prepareAiSpeech(rawTranscript: String, aiResult: BragaAiResult, friendlyName: String?): String {
            val rawFala = aiResult.fala
            return when (aiResult.tipo) {
                "CONVERSA" -> {
                    val baseFala = if (rawFala.isNotBlank()) rawFala else "Estou aqui com você. Como posso te ajudar?"
                    formatConversationalReentry(baseFala, friendlyName)
                }
                "CONSULTAR_METRICAS" -> {
                    val summary = generateMetricsSummary(aiResult.tipoMetrica)
                    formatConversationalReentry(summary, friendlyName)
                }
                "PRESSAO" -> {
                    val systolic = aiResult.sistolica ?: 120
                    val diastolic = aiResult.diastolica ?: 80
                    val sysFmt = if (systolic > 30) systolic / 10 else systolic
                    val diaFmt = if (diastolic > 20) diastolic / 10 else diastolic
                    if (rawFala.isNotBlank()) rawFala
                    else "Já preenchi $sysFmt por $diaFmt aqui para você! A sua pressão está anotada na tela, confira os valores e toque em salvar."
                }
                "GLICEMIA" -> {
                    val glyc = aiResult.glicemia ?: 100
                    if (rawFala.isNotBlank()) rawFala
                    else "Já preenchi $glyc de glicemia para você! Dá uma olhadinha na tela e toque em salvar."
                }
                "AGUA" -> {
                    val ml = aiResult.quantidadeMl ?: 250
                    "Preparei $ml ml de água na tela. Toque em Adicionar para confirmar."
                }
                "ALIMENTO", "REFEICAO", "REGISTRAR_REFEICAO", "LISTA_COMPRAS", "COMPRAS", "ADICIONAR_COMPRAS", "SUGERIR_ALIMENTO", "SUGESTAO" -> {
                    "Eu faço apenas o preenchimento de água, pressão e glicemia por voz. Para suas refeições e lista de compras, você pode usar a tela de Alimentação!"
                }
                "EMERGENCIA", "ACIONAR_EMERGENCIA" -> {
                    val nameSuffix = if (!friendlyName.isNullOrBlank()) ", $friendlyName" else ""
                    if (rawFala.isNotBlank()) rawFala
                    else "Atenção$nameSuffix! Sintomas agudos exigem avaliação médica urgente. O mais seguro e prudente é procurar atendimento imediato!"
                }
                else -> {
                    if (rawFala.isNotBlank()) formatConversationalReentry(rawFala, friendlyName)
                    else "Estou aqui para te ajudar."
                }
            }
        }

        private fun applyAiUiState(rawTranscript: String, aiResult: BragaAiResult) {
            telemetryService.logAiConversation(
                userId = getCurrentUserId(),
                userPrompt = rawTranscript,
                aiResponse = aiResult.fala,
                detectedIntent = aiResult.tipo,
                isConfirmed = false,
                rawPayload = aiResult.rawResponse
            )

            when (aiResult.tipo) {
                "PRESSAO" -> {
                    if (aiResult.sistolica != null && aiResult.diastolica != null) {
                        val systolic = aiResult.sistolica!!
                        val diastolic = aiResult.diastolica!!
                        telemetryService.logVoiceEvent(getCurrentUserId(), "PARSED", rawTranscript, "BloodPressure")
                        _navigationEvent.tryEmit(VoiceNavigationEvent.NavigateToVitals("PRESSURE", "$systolic/$diastolic"))
                        _state.value = VoiceUiState.Saved(summary = "Pressão $systolic/$diastolic", isConversational = false)
                    }
                }
                "GLICEMIA" -> {
                    if (aiResult.glicemia != null) {
                        val glyc = aiResult.glicemia!!
                        telemetryService.logVoiceEvent(getCurrentUserId(), "PARSED", rawTranscript, "Glucose")
                        _navigationEvent.tryEmit(VoiceNavigationEvent.NavigateToVitals("GLUCOSE", "$glyc"))
                        _state.value = VoiceUiState.Saved(summary = "Glicemia $glyc", isConversational = false)
                    }
                }
                "AGUA" -> {
                    val ml = aiResult.quantidadeMl ?: 250
                    telemetryService.logVoiceEvent(getCurrentUserId(), "PARSED", rawTranscript, "Hydration")
                    _navigationEvent.tryEmit(VoiceNavigationEvent.NavigateToHydration(addMl = ml, openCustomDialog = true))
                    _state.value = VoiceUiState.Saved(summary = "Água ${ml}ml", isConversational = false)
                }
                "ALIMENTO", "REFEICAO", "REGISTRAR_REFEICAO", "LISTA_COMPRAS", "COMPRAS", "ADICIONAR_COMPRAS", "SUGERIR_ALIMENTO", "SUGESTAO" -> {
                    telemetryService.logVoiceEvent(getCurrentUserId(), "PARSED", rawTranscript, "NutritionRedirect")
                    _navigationEvent.tryEmit(VoiceNavigationEvent.NavigateToNutrition(searchFoodQuery = null, openGroceryList = false))
                    _state.value = VoiceUiState.Saved(summary = "Alimentação", isConversational = true)
                }
                "CONSULTAR_METRICAS" -> {
                    _state.value = VoiceUiState.Saved(summary = "Métricas", isConversational = true)
                    telemetryService.logVoiceEvent(getCurrentUserId(), "TTS_COMPLETE", null, "METRICS_QUERY")
                }
                "CONVERSA" -> {
                    _state.value = VoiceUiState.Saved(summary = "", isConversational = true)
                    telemetryService.logVoiceEvent(getCurrentUserId(), "TTS_COMPLETE", null, "CONVERSATIONAL_AI")
                }
                "EMERGENCIA", "ACIONAR_EMERGENCIA" -> {
                    _state.value = VoiceUiState.Saved(summary = "Central de Emergência", isConversational = true)
                    _navigationEvent.tryEmit(VoiceNavigationEvent.OpenEmergencyDialog)
                    telemetryService.logVoiceEvent(getCurrentUserId(), "EMERGENCY_TRIGGERED", rawTranscript, "AI_EMERGENCY")
                    viewModelScope.launch {
                        notificationClient.triggerEmergency(getCurrentUserId(), currentUserName ?: "Usuário", "Acionado socorro via IA: $rawTranscript")
                    }
                }
            }
        }

        private fun applyIntentUiState(bestMatch: String, intent: VoiceHealthIntent) {
            when (intent) {
                is VoiceHealthIntent.BloodPressure -> {
                    val systolic = intent.systolic
                    val diastolic = intent.diastolic
                    telemetryService.logVoiceEvent(getCurrentUserId(), "PARSED", bestMatch, "BloodPressure")
                    _navigationEvent.tryEmit(VoiceNavigationEvent.NavigateToVitals("PRESSURE", "$systolic/$diastolic"))
                    _state.value = VoiceUiState.Saved(summary = "Pressão $systolic/$diastolic", isConversational = false)
                }
                is VoiceHealthIntent.Glucose -> {
                    val glyc = intent.glucoseMgDl
                    telemetryService.logVoiceEvent(getCurrentUserId(), "PARSED", bestMatch, "Glucose")
                    _navigationEvent.tryEmit(VoiceNavigationEvent.NavigateToVitals("GLUCOSE", "$glyc"))
                    _state.value = VoiceUiState.Saved(summary = "Glicemia $glyc", isConversational = false)
                }
                is VoiceHealthIntent.HeartRate -> {
                    val bpm = intent.heartRateBpm
                    telemetryService.logVoiceEvent(getCurrentUserId(), "PARSED", bestMatch, "HeartRate")
                    _navigationEvent.tryEmit(VoiceNavigationEvent.NavigateToVitals("HEART_RATE", "$bpm"))
                    _state.value = VoiceUiState.Saved(summary = "Batimentos $bpm bpm", isConversational = false)
                }
                is VoiceHealthIntent.OxygenSaturation -> {
                    val spo2 = intent.oxygenPercent
                    telemetryService.logVoiceEvent(getCurrentUserId(), "PARSED", bestMatch, "OxygenSaturation")
                    _navigationEvent.tryEmit(VoiceNavigationEvent.NavigateToVitals("OXYGEN_SATURATION", "$spo2"))
                    _state.value = VoiceUiState.Saved(summary = "SpO2 $spo2%", isConversational = false)
                }
                is VoiceHealthIntent.Hydration -> {
                    val ml = intent.amountMl
                    telemetryService.logVoiceEvent(getCurrentUserId(), "PARSED", bestMatch, "Hydration")
                    _navigationEvent.tryEmit(VoiceNavigationEvent.NavigateToHydration(addMl = ml, openCustomDialog = true))
                    _state.value = VoiceUiState.Saved(summary = "Água ${ml}ml", isConversational = false)
                }
                else -> {
                    _state.value = VoiceUiState.Saved(summary = "", isConversational = true)
                }
            }
        }

        private fun getFallbackSpeech(intent: VoiceHealthIntent, friendlyName: String?): String {
            return when (intent) {
                is VoiceHealthIntent.BloodPressure -> {
                    val sys = if (intent.systolic > 30) intent.systolic / 10 else intent.systolic
                    val dia = if (intent.diastolic > 20) intent.diastolic / 10 else intent.diastolic
                    "Já preenchi $sys por $dia aqui para você. Confira os valores e toque em salvar!"
                }
                is VoiceHealthIntent.Glucose -> {
                    "Já preenchi ${intent.glucoseMgDl} de glicemia para você. Confira e toque em salvar!"
                }
                is VoiceHealthIntent.HeartRate -> {
                    "Já preenchi ${intent.heartRateBpm} batimentos por minuto para você. Confira e toque em salvar!"
                }
                is VoiceHealthIntent.OxygenSaturation -> {
                    "Já preenchi ${intent.oxygenPercent} por cento de saturação para você. Confira e toque em salvar!"
                }
                is VoiceHealthIntent.Hydration -> {
                    "Já preparei mais ${intent.amountMl} ml de água para você. Toque em Adicionar para confirmar!"
                }
                is VoiceHealthIntent.ConversationalReply -> {
                    intent.message
                }
                else -> {
                    formatConversationalReentry("Estou aqui com você. Como posso te ajudar com sua pressão, glicemia ou água?", friendlyName)
                }
            }
        }

        private fun generateConversationalFiller(query: String): String {
            val lower = query.lowercase(Locale.getDefault())

            return when {
                lower.contains("pressão") || lower.contains("glicemia") || lower.contains("exame") || lower.contains("histórico") || lower.contains("métrica") || lower.contains("sinais") -> {
                    listOf(
                        "Certo! Deixa eu consultar seu histórico aqui, só um segundinho...",
                        "Com certeza! Deixa eu verificar suas anotações com carinho...",
                        "Um instante, estou dando uma olhadinha nos seus registros..."
                    ).random()
                }
                lower.contains("comer") || lower.contains("comida") || lower.contains("almoço") || lower.contains("jantar") || lower.contains("dieta") || lower.contains("alimento") || lower.contains("fruta") -> {
                    listOf(
                        "Boa pergunta! Deixa eu pensar em uma boa opção saudável para você...",
                        "Com certeza! Deixa eu dar uma olhadinha no que é mais recomendado...",
                        "Só um momentinho, estou verificando as opções de alimentação..."
                    ).random()
                }
                lower.contains("sinto") || lower.contains("dor") || lower.contains("tontura") || lower.contains("remédio") || lower.contains("sintoma") || lower.contains("mal") -> {
                    listOf(
                        "Entendi. Deixa eu checar com atenção as orientações de cuidado...",
                        "Compreendo. Só um instante enquanto organizo as informações para você...",
                        "Certo, estou aqui com você. Deixa eu verificar isso direitinho..."
                    ).random()
                }
                else -> {
                    listOf(
                        "Entendido! Deixa eu organizar essas informações com carinho...",
                        "Com certeza! Já estou buscando as melhores orientações para você...",
                        "Só um instante, estou consultando isso com atenção para te responder..."
                    ).random()
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
                    "Conforme orientações da Organização Mundial da Saúde e do Ministério da Saúde, a hidratação adequada é essencial. Você já registrou $waterConsumed ml hoje, atingindo $percent% da sua meta sugerida de $waterTarget ml. Sugiro validar com seu profissional de saúde o volume ideal para o seu organismo!"
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

        private fun handleAiResult(rawTranscript: String, aiResult: BragaAiResult) {
            // Registrar diálogo na telemetria para o dataset de Fine-Tuning
            telemetryService.logAiConversation(
                userId = getCurrentUserId(),
                userPrompt = rawTranscript,
                aiResponse = aiResult.fala,
                detectedIntent = aiResult.tipo,
                isConfirmed = false,
                rawPayload = aiResult.rawResponse
            )

            when (aiResult.tipo) {
                "PRESSAO" -> {
                    if (aiResult.sistolica != null && aiResult.diastolica != null) {
                        // AUD-AN01: a IA pode devolver pressão em escala coloquial
                        // ("12/8" em vez de 120/80). O parser local e a tela normalizam;
                        // este caminho usava o valor cru. Normalizar ANTES de navegar,
                        // gravar e anunciar — senão 12/8 salvo dispara falso alerta
                        // clínico no HealthEngine (value < 90).
                        var systolic = aiResult.sistolica!!
                        var diastolic = aiResult.diastolica!!
                        if (systolic > 30) systolic /= 10
                        if (diastolic > 20) diastolic /= 10
                        telemetryService.logVoiceEvent(getCurrentUserId(), "PARSED", rawTranscript, "BloodPressure")

                        _navigationEvent.tryEmit(VoiceNavigationEvent.NavigateToVitals("PRESSURE", "$systolic/$diastolic"))
                        _state.value = VoiceUiState.Saved(summary = "Pressão $systolic/$diastolic", isConversational = false)
                        val spokenPrompt = if (aiResult.fala.isNotBlank()) aiResult.fala
                            else "Já preenchi $systolic por $diastolic aqui para você! A sua pressão está ótima e dentro da faixa normal. Dá uma conferida certinha nos valores e é só tocar em salvar!"
                        speak(spokenPrompt) {
                        onSpeechFinished()
                    }
                    } else {
                        val fallbackIntent = parser.parse(rawTranscript, currentUserRole, currentCaregiverMode)
                        handleIntent(rawTranscript, fallbackIntent)
                    }
                }
                "GLICEMIA" -> {
                    if (aiResult.glicemia != null) {
                        val glyc = aiResult.glicemia!!
                        telemetryService.logVoiceEvent(getCurrentUserId(), "PARSED", rawTranscript, "Glucose")
                        
                        _navigationEvent.tryEmit(VoiceNavigationEvent.NavigateToVitals("GLUCOSE", "$glyc"))
                        _state.value = VoiceUiState.Saved(summary = "Glicemia $glyc", isConversational = false)
                        val spokenPrompt = if (aiResult.fala.isNotBlank()) aiResult.fala
                            else "Já preenchi $glyc de glicemia para você! Está dentro do padrão recomendado. Dá uma olhadinha e é só tocar em salvar!"
                        speak(spokenPrompt) {
                        onSpeechFinished()
                    }
                    } else {
                        val fallbackIntent = parser.parse(rawTranscript, currentUserRole, currentCaregiverMode)
                        handleIntent(rawTranscript, fallbackIntent)
                    }
                }
                "AGUA" -> {
                    val ml = aiResult.quantidadeMl ?: 250
                    telemetryService.logVoiceEvent(getCurrentUserId(), "PARSED", rawTranscript, "Hydration")
                    
                    _navigationEvent.tryEmit(VoiceNavigationEvent.NavigateToHydration(addMl = ml, openCustomDialog = true))
                    _state.value = VoiceUiState.Saved(summary = "Água ${ml}ml", isConversational = false)
                    val spokenPrompt = if (aiResult.fala.isNotBlank()) aiResult.fala
                        else "Já preparei a anotação de mais $ml ml de água para você. É só você tocar em Adicionar para confirmar!"
                    speak(spokenPrompt) {
                        onSpeechFinished()
                    }
                }
                "ALIMENTO", "REFEICAO", "REGISTRAR_REFEICAO", "LISTA_COMPRAS", "COMPRAS", "ADICIONAR_COMPRAS", "SUGERIR_ALIMENTO", "SUGESTAO" -> {
                    telemetryService.logVoiceEvent(getCurrentUserId(), "PARSED", rawTranscript, "NutritionRedirect")
                    _navigationEvent.tryEmit(VoiceNavigationEvent.NavigateToNutrition(searchFoodQuery = null, openGroceryList = false))
                    _state.value = VoiceUiState.Saved(summary = "Alimentação", isConversational = true)
                    val spokenPrompt = "Eu faço apenas o preenchimento de água, pressão e glicemia por voz. Abri a tela de Alimentação para você!"
                    speak(spokenPrompt) {
                        onSpeechFinished()
                    }
                }
                "CONSULTAR_METRICAS" -> {
                    viewModelScope.launch {
                        _state.value = VoiceUiState.Saved(summary = "Métricas", isConversational = true)
                        val summary = generateMetricsSummary(aiResult.tipoMetrica)
                        speak(summary) {
                        onSpeechFinished()
                    }
                        telemetryService.logVoiceEvent(getCurrentUserId(), "TTS_COMPLETE", null, "METRICS_QUERY")
                    }
                }
                "CONVERSA" -> {
                    _state.value = VoiceUiState.Saved(summary = "", isConversational = true)
                    val fala = if (aiResult.fala.isNotBlank()) aiResult.fala else "Estou aqui com você. Como posso te ajudar?"
                    speak(fala) {
                        onSpeechFinished()
                    }
                    telemetryService.logVoiceEvent(getCurrentUserId(), "TTS_COMPLETE", null, "CONVERSATIONAL_AI")
                }
                "EMERGENCIA", "ACIONAR_EMERGENCIA" -> {
                    _state.value = VoiceUiState.Saved(summary = "Central de Emergência", isConversational = true)
                    _navigationEvent.tryEmit(VoiceNavigationEvent.OpenEmergencyDialog)
                    telemetryService.logVoiceEvent(getCurrentUserId(), "EMERGENCY_TRIGGERED", rawTranscript, "AI_EMERGENCY")
                    viewModelScope.launch {
                        notificationClient.triggerEmergency(getCurrentUserId(), currentUserName ?: "Usuário", "Acionado socorro via IA: $rawTranscript")
                    }

                    val friendlyName = br.com.bragasaude.util.PortuguesePhoneticHelper.toTtsFriendlyName(currentUserName)
                    val nameSuffix = if (friendlyName.isNotBlank()) ", $friendlyName" else ""
                    // D-EMERG1: em emergência, NÃO usa o texto gerado pela IA — ele pode
                    // alucinar cidade/endereço e ainda consome tokens num momento crítico.
                    // Orienta sempre pelo botão "UPA mais próxima" (GPS real do aparelho),
                    // que já está aberto na tela do usuário: zero alucinação e zero latência.
                    val fala = "Atenção$nameSuffix! Sintomas agudos exigem avaliação médica urgente, não espere. Toque no botão \"UPA mais próxima\" que está na sua tela: o mapa usa sua localização real e te leva direto. Já abri as opções de ajuda!"
                    speak(fala) {
                        onSpeechFinished()
                    }
                }
                else -> {
                    val fallbackIntent = parser.parse(rawTranscript, currentUserRole, currentCaregiverMode)
                    handleIntent(rawTranscript, fallbackIntent)
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
