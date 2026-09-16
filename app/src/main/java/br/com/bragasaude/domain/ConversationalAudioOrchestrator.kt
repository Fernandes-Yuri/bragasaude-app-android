package br.com.bragasaude.domain

import android.util.Log
import br.com.bragasaude.data.remote.ai.NeuralAudioPlayer
import kotlinx.coroutines.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Orquestrador de Áudio Conversacional (Braga Saúde).
 *
 * Fluxo simplificado:
 * 1. Orb visual indica "pensando" (sem áudio de filler).
 * 2. Quando a IA responde, fala a resposta diretamente.
 *
 * Elimina o problema de soletração de letras causado por áudios pré-gravados
 * e TTS processando texto indevidamente.
 */
@Singleton
class ConversationalAudioOrchestrator @Inject constructor(
    private val audioPlayer: NeuralAudioPlayer
) {
    companion object {
        private const val TAG = "AudioOrchestrator"
    }

    private var orchestratorJob: Job? = null
    private var generation = 0L

    /**
     * Executa o fluxo de áudio: aguarda a resposta da IA e fala quando chegar.
     *
     * @param scope Escopo da corrotina (geralmente viewModelScope).
     * @param aiSpeechDeferred Deferred contendo a fala final da IA quando a rede responder.
     * @param onSpeakingStateChanged Notifica quando o assistente começa ou para de falar.
     * @param onDone Callback chamado quando o áudio for concluído.
     */
    fun startThreeActFlow(
        scope: CoroutineScope,
        aiSpeechDeferred: Deferred<String?>,
        onSpeakingStateChanged: (Boolean) -> Unit,
        onDone: () -> Unit
    ) {
        stop()
        val turn = generation

        orchestratorJob = scope.launch {
            try {
                // Indicacao visual apenas — a Orb mostra estado "pensando"
                Log.d(TAG, "Aguardando resposta da IA (sem filler de áudio)...")

                // Aguarda a resposta da IA
                val aiSpeech = aiSpeechDeferred.await()

                if (!aiSpeech.isNullOrBlank()) {
                    Log.d(TAG, "Recebida resposta da IA: ${aiSpeech.take(50)}...")
                    val aiCompleted = CompletableDeferred<Unit>()

                    val speechPlayed = audioPlayer.playSpeech(
                        text = aiSpeech,
                        isMale = true,
                        onStart = { if (turn == generation) onSpeakingStateChanged(true) },
                        onDone = {
                            Log.d(TAG, "Resposta da IA concluída.")
                            aiCompleted.complete(Unit)
                        }
                    )

                    if (speechPlayed) {
                        aiCompleted.await()
                    }
                }
            } catch (e: CancellationException) {
                Log.d(TAG, "Orquestrador cancelado pelo usuário.")
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Erro no orquestrador conversacional: ${e.message}", e)
            } finally {
                if (turn == generation && currentCoroutineContext().isActive) {
                    onSpeakingStateChanged(false)
                    onDone()
                }
            }
        }
    }

    /**
     * Interrompe imediatamente qualquer áudio em reprodução e cancela o fluxo.
     */
    fun stop() {
        generation++
        orchestratorJob?.cancel()
        orchestratorJob = null
        audioPlayer.stop()
    }
}
