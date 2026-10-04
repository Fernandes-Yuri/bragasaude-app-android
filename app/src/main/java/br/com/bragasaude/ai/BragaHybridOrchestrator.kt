package br.com.bragasaude.ai

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

sealed interface BragaHybridEvent {
    data class Local(val output: NluOutput) : BragaHybridEvent
    data class Delta(val text: String) : BragaHybridEvent
    data class Completed(val text: String) : BragaHybridEvent
}

/** A nuvem somente produz fala; ações e persistência permanecem no domínio local. */
class BragaHybridOrchestrator internal constructor(
    private val cloud: GroqStreamSource,
    private val prompt: GroqDynamicPrompt
) {
    @Inject constructor(client: GroqStreamingClient, prompt: GroqDynamicPrompt) : this(
        client as GroqStreamSource, prompt
    )

    fun analyze(speech: String): NluOutput = BragaNluEngine.analisar(speech)

    fun respond(speech: String, history: List<Pair<String, String>>): Flow<BragaHybridEvent> = flow {
        val local = analyze(speech)
        if (!local.delegarParaNuvem || local.isEmergencia || local.isBloqueioSeguranca) {
            emit(BragaHybridEvent.Local(local))
            return@flow
        }
        val answer = StringBuilder()
        cloud.stream(prompt.build(history), speech).collect { delta ->
            answer.append(delta)
            emit(BragaHybridEvent.Delta(delta))
        }
        emit(BragaHybridEvent.Completed(answer.toString()))
    }
}
