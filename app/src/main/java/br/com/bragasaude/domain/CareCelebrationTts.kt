package br.com.bragasaude.domain

import android.content.Context
import br.com.bragasaude.data.remote.ai.NeuralAudioPlayer
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Cena C38 — Celebração positiva por voz.
 *
 * Utiliza o canal de áudio unificado do NeuralAudioPlayer / AndroidSystemTts.
 */
@Singleton
class CareCelebrationTts @Inject constructor(
    @ApplicationContext private val context: Context,
    private val neuralAudioPlayer: NeuralAudioPlayer
) {
    /**
     * Interação por voz de celebração desativada por diretriz de produto (invasiva ao paciente).
     */
    suspend fun celebrate(patientName: String?, onStart: () -> Unit = {}, onDone: () -> Unit = {}) {
        onDone()
    }

    /** Fala um texto livre em pt-BR utilizando o mecanismo unificado de síntese do aplicativo. */
    suspend fun speak(text: String, onStart: () -> Unit = {}, onDone: () -> Unit = {}) {
        if (text.isBlank()) return
        neuralAudioPlayer.playSpeech(text, isMale = true, onStart = onStart, onDone = onDone)
    }

    /** Libera recursos (compatibilidade de ciclo de vida com ViewModels). */
    fun shutdown() {
        // Áudio gerenciado de forma centralizada pelo NeuralAudioPlayer
    }
}
