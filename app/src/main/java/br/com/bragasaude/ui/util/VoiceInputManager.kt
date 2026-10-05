package br.com.bragasaude.ui.util

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Mantém o contrato launch(intent) das telas, sem abrir uma atividade de reconhecimento remoto. */
class OnDeviceVoiceInputLauncher internal constructor(
    private val context: Context,
    private val scope: CoroutineScope,
    private val onResult: (String) -> Unit
) {
    internal var requestPermission: (() -> Unit)? = null
    private var pendingIntent: Intent? = null
    private var recognizer: SpeechRecognizer? = null
    private var startJob: Job? = null
    private var generation = 0L

    fun launch(intent: Intent) {
        cancel()
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            pendingIntent = intent
            requestPermission?.invoke()
        } else start(intent)
    }

    internal fun permissionResult(granted: Boolean) {
        val pending = pendingIntent
        pendingIntent = null
        if (granted && pending != null) start(pending)
        else if (!granted) showMessage(OnDeviceSpeechRecognition.errorMessage(SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS))
    }

    private fun start(request: Intent) {
        val ticket = ++generation
        startJob = scope.launch {
            try {
                val local = OnDeviceSpeechRecognition.create(context)
                recognizer = local
                fun current() = ticket == generation && recognizer === local
                local.setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) = Unit
                    override fun onBeginningOfSpeech() = Unit
                    override fun onRmsChanged(rmsdB: Float) = Unit
                    override fun onBufferReceived(buffer: ByteArray?) = Unit
                    override fun onEndOfSpeech() = Unit
                    override fun onPartialResults(partialResults: Bundle?) = Unit
                    override fun onEvent(eventType: Int, params: Bundle?) = Unit
                    override fun onError(error: Int) {
                        if (!current()) return
                        cancel()
                        showMessage(OnDeviceSpeechRecognition.errorMessage(error))
                    }
                    override fun onResults(results: Bundle?) {
                        if (!current()) return
                        val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                        cancel()
                        if (!text.isNullOrBlank()) onResult(text)
                    }
                })
                // Preserve prompt da tela, mas fixe idioma e modo local independentemente do Intent recebido.
                val localIntent = OnDeviceSpeechRecognition.intent(partialResults = false).apply {
                    request.getStringExtra(RecognizerIntent.EXTRA_PROMPT)?.let { putExtra(RecognizerIntent.EXTRA_PROMPT, it) }
                }
                OnDeviceSpeechRecognition.checkPortugueseSupport(context, local, localIntent)
                if (current()) local.startListening(localIntent)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (ticket == generation) {
                    cancel()
                    showMessage(error.message ?: OnDeviceSpeechRecognition.errorMessage(SpeechRecognizer.ERROR_CLIENT))
                }
            }
        }
    }

    fun cancel() {
        generation++
        pendingIntent = null
        startJob?.cancel()
        startJob = null
        val old = recognizer
        recognizer = null
        runCatching { old?.cancel() }
        runCatching { old?.destroy() }
    }

    private fun showMessage(message: String) = Toast.makeText(context, message, Toast.LENGTH_LONG).show()
}

@Composable
fun rememberVoiceInputLauncher(onResult: (String) -> Unit): OnDeviceVoiceInputLauncher {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val callback = rememberUpdatedState(onResult)
    val launcher = remember(context, scope) { OnDeviceVoiceInputLauncher(context.applicationContext, scope) { callback.value(it) } }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission(), launcher::permissionResult)
    launcher.requestPermission = { permission.launch(Manifest.permission.RECORD_AUDIO) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner, launcher) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) launcher.cancel() }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer); launcher.cancel() }
    }
    return launcher
}

fun createVoiceInputIntent(context: String = "GERAL"): Intent {
    val prompt = when (context) {
        "PRESSURE" -> "Fale sua pressão (ex: 12 por 8)"
        "GLUCOSE" -> "Fale seu nível de glicose"
        "HEART_RATE" -> "Fale seus batimentos (exemplo: 72)"
        "OXYGEN_SATURATION" -> "Fale sua saturação (exemplo: 98)"
        "HYDRATION" -> "Quantos ml de água você bebeu?"
        "CHECKIN" -> "Como foi sua noite e como você está se sentindo hoje?"
        else -> "Fale os valores de saúde"
    }
    return OnDeviceSpeechRecognition.intent(partialResults = false).apply {
        putExtra(RecognizerIntent.EXTRA_PROMPT, prompt)
    }
}
