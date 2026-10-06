package br.com.bragasaude.ai

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
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
    private val prompt: GroqDynamicPrompt,
    private val memory: BragaHealthMemory? = null
) {
    @Inject constructor(client: GroqStreamingClient, prompt: GroqDynamicPrompt, memory: BragaHealthMemory) : this(
        client as GroqStreamSource, prompt, memory
    )

    fun analyze(speech: String, channel: InputChannel = InputChannel.VOICE): NluOutput = BragaNluEngine.analisar(speech, channel)

    fun analyze(speech: String, channel: InputChannel, session: HealthQuerySession,
                userId: String, conversationId: String): NluOutput {
        val local = analyze(speech, channel)
        if (!local.isBloqueioSeguranca && !local.isEmergencia && local.intent != "entrada_linguagem_ambigua") {
            session.contextualAnswer(speech, userId, conversationId)?.let { return it }
        }
        if (!local.isBloqueioSeguranca && !local.isEmergencia && local.intent != "entrada_linguagem_ambigua") {
            BragaLocalHelp.answer(speech, session.recentTopic(userId, conversationId))?.let { return it }
        }
        if (local.isBloqueioSeguranca || local.isEmergencia || local.delegarParaNuvem ||
            local.intent == "entrada_linguagem_ambigua" || local.intent == "entrada_periodo_nao_suportado" || local.intent == "entrada_explicacao_sem_referencia" ||
            local.intent == "orientacao_cadastro_medicamento" || HealthQueryResolver.isAmbiguous(speech)) return local
        val query = session.resolve(speech, userId, conversationId) ?: local.healthQuery
        return if (query == null) local else local.copy(intent = query.intent, respostaLocal = null, healthQuery = query)
    }

    suspend fun resolveLocal(output: NluOutput, userId: String, channel: InputChannel,
                             session: HealthQuerySession? = null, conversationId: String = "", expectedTurn: Long? = null): NluOutput {
        var hasData: Boolean? = null
        var referenceMeasuredAt: Long? = null
        var referenceZone: String? = null
        val reply = if (BragaHealthMemory.supports(output.intent)) {
            val query = output.healthQuery ?: HealthQuery.forIntent(output.intent)
            if (memory == null) { hasData = false; "Não foi possível consultar seus registros locais agora." }
            else {
                val result = memory.answerResult(query, userId)
                hasData = result.hasData
                referenceMeasuredAt = result.referenceMeasuredAtMillis
                referenceZone = result.referenceZoneId
                currentCoroutineContext().ensureActive()
                if (result.hasData) session?.remember(query, userId, conversationId, expectedTurn)
                result.text
            }
        } else output.respostaLocal.orEmpty()
        return output.copy(respostaLocal = BragaInputLanguage.forChannel(reply, channel),
            hasLocalData = hasData ?: output.hasLocalData,
            referenceMeasuredAtMillis = referenceMeasuredAt ?: output.referenceMeasuredAtMillis,
            referenceZoneId = referenceZone ?: output.referenceZoneId)
    }

    fun respond(speech: String, history: List<Pair<String, String>>, channel: InputChannel = InputChannel.VOICE,
                userId: String = "anonymous", decision: NluOutput? = null): Flow<BragaHybridEvent> = flow {
        val local = decision ?: analyze(speech, channel)
        if (!local.delegarParaNuvem || local.isEmergencia || local.isBloqueioSeguranca) {
            emit(BragaHybridEvent.Local(resolveLocal(local, userId, channel)))
            return@flow
        }
        val answer = StringBuilder()
        cloud.stream(prompt.build(history, channel), speech).collect { delta ->
            answer.append(delta)
            if (channel == InputChannel.VOICE) emit(BragaHybridEvent.Delta(delta))
        }
        val safe = BragaInputLanguage.forChannel(answer.toString(), channel)
        if (channel == InputChannel.TEXT) emit(BragaHybridEvent.Delta(safe))
        emit(BragaHybridEvent.Completed(safe))
    }
}
