package br.com.bragasaude.ui.util

import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import java.util.Locale

@Composable
fun rememberVoiceInputLauncher(onResult: (String) -> Unit) =
    rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val data = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            data?.firstOrNull()?.let { onResult(it) }
        }
    }

fun createVoiceInputIntent(context: String = "GERAL"): Intent {
    val prompt = when(context) {
        "PRESSURE" -> "Fale sua pressão (ex: 12 por 8)"
        "GLUCOSE" -> "Fale seu nível de glicose"
        "HEART_RATE" -> "Fale seus batimentos (exemplo: 72)"
        "OXYGEN_SATURATION" -> "Fale sua saturação (exemplo: 98)"
        "HYDRATION" -> "Quantos ml de água você bebeu?"
        "CHECKIN" -> "Como foi sua noite e como você está se sentindo hoje?"
        else -> "Fale os valores de saúde"
    }
    
    return Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
        putExtra(RecognizerIntent.EXTRA_PROMPT, prompt)
    }
}
