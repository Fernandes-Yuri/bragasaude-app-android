package br.com.bragasaude.ui.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import javax.inject.Inject

/** A perda de foco encerra a reprodução; concessões tardias não retomam a conversa. */
class VoicePlaybackFocus @Inject constructor() {
    private var manager: AudioManager? = null
    private var activeRequest: AudioFocusRequest? = null
    private var generation = 0L

    fun request(context: Context, onLost: () -> Unit): Boolean {
        release()
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return false
        manager = audio
        val ticket = generation
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            .setWillPauseWhenDucked(true)
            .setOnAudioFocusChangeListener { change ->
                if (ticket == generation && (change == AudioManager.AUDIOFOCUS_LOSS ||
                    change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT ||
                    change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK)) onLost()
            }.build()
        activeRequest = request
        val granted = runCatching { audio.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED }.getOrDefault(false)
        if (!granted) release()
        return granted
    }

    fun release() {
        generation++
        val previous = activeRequest
        activeRequest = null
        if (previous != null) runCatching { manager?.abandonAudioFocusRequest(previous) }
    }
}
