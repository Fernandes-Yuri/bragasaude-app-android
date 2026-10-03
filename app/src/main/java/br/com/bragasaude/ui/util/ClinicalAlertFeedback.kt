package br.com.bragasaude.ui.util

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator

/** Alerta breve, respeitando volume/modo silencioso do aparelho. Não faz chamadas. */
object ClinicalAlertFeedback {
    fun play(context: Context) {
        try {
            val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            if (audio.ringerMode == AudioManager.RINGER_MODE_NORMAL) {
                val tone = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 70)
                tone.startTone(ToneGenerator.TONE_PROP_BEEP, 300)
                Handler(Looper.getMainLooper()).postDelayed({ tone.release() }, 500)
            }
            @Suppress("DEPRECATION")
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            vibrator?.vibrate(VibrationEffect.createOneShot(350, VibrationEffect.DEFAULT_AMPLITUDE))
        } catch (_: Exception) { /* A orientação e os botões continuam disponíveis. */ }
    }
}
