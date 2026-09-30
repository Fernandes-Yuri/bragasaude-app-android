package br.com.bragasaude.data.local.voice

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class ActiveVoiceState(
    val activeId: String = VoiceCatalog.SYSTEM_ID,
    val preparingId: String? = null,
    val phase: String? = "Verificando voz",
    val progress: Float? = null,
    val error: String? = null,
    val hasChosenVoice: Boolean = false
) { val busy: Boolean get() = phase != null }

@Singleton
class VoiceProfileManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val downloader: PiperModelDownloader,
    private val engine: PiperOnDeviceEngine,
    private val system: AndroidSystemTtsFallback
) {
    private val preferences = context.getSharedPreferences("voice_profile", Context.MODE_PRIVATE)
    private val store = VoiceModelStore(File(context.filesDir, "voices"))
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val selection = Mutex()
    private val playback = Mutex()
    private val speechGeneration = java.util.concurrent.atomic.AtomicLong()
    private val mutableState = MutableStateFlow(ActiveVoiceState())
    val state = mutableState.asStateFlow()
    private var selectionJob: Job? = null

    init {
        scope.launch { selection.withLock {
            try {
                var id = preferences.getString("active_voice_id", null)
                val explicitlyConfigured = preferences.getBoolean("has_chosen_voice", false)
                // Preserva o Faber já instalado pela versão anterior, sem baixar novamente.
                val legacy = File(context.filesDir, "piper_voice")
                if (id == null && VoiceModelStore.valid(legacy)) {
                    store.current.parentFile?.mkdirs()
                    if (!store.current.exists()) check(legacy.renameTo(store.current))
                    File(store.current, "voice_id").writeText("faber")
                    persist("faber", markChosen = true)
                    id = "faber"
                }
                if (id == null && VoiceModelStore.id(store.current) == "faber" && VoiceModelStore.valid(store.current)) {
                    persist("faber", markChosen = true)
                    id = "faber"
                }
                val selected = id?.takeIf { VoiceCatalog.find(it) != null } ?: VoiceCatalog.SYSTEM_ID
                store.recover(selected)
                val usable = selected != VoiceCatalog.SYSTEM_ID &&
                    VoiceModelStore.id(store.current) == selected && VoiceModelStore.valid(store.current)
                if (!usable) {
                    persist(VoiceCatalog.SYSTEM_ID, markChosen = explicitlyConfigured)
                    store.removeModels()
                }
                VoiceModelStore.clear(legacy)
                val hasChosen = explicitlyConfigured || (id != null && usable)
                mutableState.value = ActiveVoiceState(
                    activeId = if (usable) selected else VoiceCatalog.SYSTEM_ID,
                    phase = null,
                    hasChosenVoice = hasChosen
                )
            } catch (_: Exception) {
                mutableState.value = ActiveVoiceState(phase = null, error = "Não foi possível recuperar a voz. O dispositivo será usado até uma nova seleção.")
            }
        } }
    }

    fun select(id: String) {
        val option = VoiceCatalog.find(id) ?: return
        if (state.value.busy || selectionJob?.isActive == true) return
        mutableState.value = state.value.copy(preparingId = id, phase = "Preparando voz", error = null)
        selectionJob = scope.launch { selection.withLock {
            val oldId = state.value.activeId
            var committed = false
            try {
                if (option.isNeural) {
                    store.recover(oldId)
                    val staging = store.prepare()
                    check(staging.usableSpace > 180_000_000) { "Espaço insuficiente" }
                    downloader.prepare(option, staging) { phase, progress ->
                        mutableState.value = state.value.copy(phase = phase, progress = progress)
                    }
                    mutableState.value = state.value.copy(phase = "Carregando voz", progress = null)
                    // Depois de iniciar a troca, conclui commit ou rollback mesmo com cancelamento.
                    withContext(NonCancellable) {
                        stop()
                        playback.withLock {
                            check(engine.replaceModel(
                                install = { store.begin(id) },
                                commit = { persist(id) },
                                rollback = { store.rollback() }
                            )) { "Não foi possível carregar a voz" }
                            committed = true
                        }
                    }
                    store.finish()
                } else {
                    withContext(NonCancellable) {
                        stop()
                        playback.withLock {
                            engine.unload()
                            persist(id)
                            committed = true
                            store.removeModels()
                        }
                    }
                }
                mutableState.value = ActiveVoiceState(activeId = id, phase = null, hasChosenVoice = true)
                // A confirmação usa a voz realmente selecionada.
                playSpeech("Olá! Estou pronto para ajudar.", {}, {})
            } catch (e: CancellationException) {
                mutableState.value = ActiveVoiceState(activeId = if (committed) id else oldId, phase = null, hasChosenVoice = state.value.hasChosenVoice)
                throw e
            } catch (_: Exception) {
                mutableState.value = ActiveVoiceState(
                    activeId = if (committed) id else oldId, phase = null,
                    hasChosenVoice = if (committed) true else state.value.hasChosenVoice,
                    error = if (committed) "Voz selecionada. A limpeza será retomada ao abrir o aplicativo." else
                        "Não foi possível preparar a voz. Verifique a conexão e o espaço livre e tente novamente. Sua seleção anterior foi mantida."
                )
            } finally {
                withContext(NonCancellable) { runCatching { VoiceModelStore.clear(store.staging) } }
            }
        } }
        selectionJob?.invokeOnCompletion {
            if (state.value.busy && state.value.preparingId == id) {
                mutableState.value = state.value.copy(preparingId = null, phase = null, progress = null)
            }
        }
    }

    fun selectSystemExplicit() {
        persist(VoiceCatalog.SYSTEM_ID, markChosen = true)
        mutableState.value = state.value.copy(
            activeId = VoiceCatalog.SYSTEM_ID,
            phase = null,
            hasChosenVoice = true
        )
    }

    fun needsOnboarding(): Boolean = !state.value.hasChosenVoice

    fun cancelDownload() {
        if (state.value.phase != "Carregando voz") selectionJob?.cancel()
    }

    fun dismissError() { mutableState.value = state.value.copy(error = null) }

    private fun persist(id: String, markChosen: Boolean = true) {
        val editor = preferences.edit().putString("active_voice_id", id)
        if (markChosen) {
            editor.putBoolean("has_chosen_voice", true)
        }
        check(editor.commit()) { "Falha ao salvar seleção" }
    }

    suspend fun playSpeech(text: String, onStart: () -> Unit, onDone: () -> Unit): Boolean {
        val ticket = speechGeneration.get()
        val played = playback.withLock {
            currentCoroutineContext().ensureActive()
            if (ticket != speechGeneration.get()) throw CancellationException("Fala interrompida")
            if (state.value.activeId != VoiceCatalog.SYSTEM_ID && state.value.phase != "Carregando voz")
                engine.playStream(text, onStart = onStart, onDone = onDone)
            else false
        }
        currentCoroutineContext().ensureActive()
        if (ticket != speechGeneration.get()) throw CancellationException("Fala interrompida")
        return played || system.speak(text, onStart, onDone)
    }

    suspend fun previewSystem(): Boolean = system.speak("Olá! Estou pronto para ajudar.")
    fun stop() { speechGeneration.incrementAndGet(); engine.stop(); system.stop() }
}
