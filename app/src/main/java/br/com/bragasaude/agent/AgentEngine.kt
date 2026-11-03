package br.com.bragasaude.agent

import android.util.Log
import br.com.bragasaude.agent.protocol.AgentMessage
import br.com.bragasaude.agent.protocol.AgentRelayClient
import br.com.bragasaude.agent.protocol.AgentRelayRequest
import br.com.bragasaude.agent.protocol.AgentRole
import br.com.bragasaude.agent.protocol.AgentUsage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Estados da máquina de execução do Mini Harness no Android.
 */
sealed class AgentState {
    data object Idle : AgentState()

    data class Thinking(
        val turn: Int,
        val maxTurns: Int
    ) : AgentState()

    data class ExecutingTool(
        val turn: Int,
        val toolName: String,
        val toolCallId: String,
        val arguments: String
    ) : AgentState()

    data class Observing(
        val turn: Int,
        val toolName: String,
        val preview: String
    ) : AgentState()

    data class Completed(
        val finalMessage: AgentMessage,
        val totalTurns: Int,
        val usage: AgentUsage? = null
    ) : AgentState()

    data class Failed(
        val reason: String,
        val turn: Int = 0,
        val error: Throwable? = null
    ) : AgentState()
}

/**
 * Resultado da execução completa de um ciclo do agente.
 */
sealed class AgentExecutionResult {
    data class Success(
        val content: String,
        val message: AgentMessage,
        val turnsUsed: Int,
        val messages: List<AgentMessage>,
        val usage: AgentUsage? = null
    ) : AgentExecutionResult()

    data class Failure(
        val reason: String,
        val turnsUsed: Int,
        val messages: List<AgentMessage>,
        val cause: Throwable? = null
    ) : AgentExecutionResult()
}

/**
 * Motor de Execução ReAct (Agent Engine) local para Android.
 *
 * Orquestra o ciclo de raciocínio, invocação de ferramentas nativas no dispositivo,
 * sanitização de privacidade on-device e atualização reativa de UI via Kotlin StateFlow.
 */
@Singleton
class AgentEngine @Inject constructor(
    private val relayClient: AgentRelayClient,
    private val toolRegistry: ToolRegistry,
    private val privacySanitizer: PrivacySanitizer,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    companion object {
        private const val TAG = "AgentEngine"
        private const val MAX_OBSERVATION_PREVIEW_LENGTH = 120
    }

    private val _state = MutableStateFlow<AgentState>(AgentState.Idle)
    val state: StateFlow<AgentState> = _state.asStateFlow()

    /**
     * Reseta o estado do agente para Idle.
     */
    fun reset() {
        _state.value = AgentState.Idle
    }

    /**
     * Executa uma sessão agêntica a partir de um prompt do usuário.
     */
    suspend fun run(
        userPrompt: String,
        systemPrompt: String? = null,
        sessionId: String? = null,
        budget: AgentTurnBudget = AgentTurnBudget(),
        model: String? = null,
        temperature: Float = 0.0f
    ): AgentExecutionResult = withContext(ioDispatcher) {
        val initialMessages = mutableListOf<AgentMessage>()
        if (!systemPrompt.isNullOrBlank()) {
            initialMessages.add(AgentMessage(role = AgentRole.SYSTEM, content = systemPrompt))
        }
        initialMessages.add(AgentMessage(role = AgentRole.USER, content = userPrompt))

        runSession(
            initialMessages = initialMessages,
            sessionId = sessionId,
            budget = budget,
            model = model,
            temperature = temperature
        )
    }

    /**
     * Executa uma sessão agêntica com histórico de mensagens fornecido.
     */
    suspend fun runSession(
        initialMessages: List<AgentMessage>,
        sessionId: String? = null,
        budget: AgentTurnBudget = AgentTurnBudget(),
        model: String? = null,
        temperature: Float = 0.0f
    ): AgentExecutionResult = withContext(ioDispatcher) {
        _state.value = AgentState.Idle
        budget.start()

        val conversation = initialMessages.toMutableList()
        var lastUsage: AgentUsage? = null

        while (budget.canProceed()) {
            val turn = try {
                budget.consumeTurn()
            } catch (e: BudgetExceededException) {
                val failState = AgentState.Failed(
                    reason = e.message ?: "Orçamento de turnos excedido",
                    turn = budget.currentTurn,
                    error = e
                )
                _state.value = failState
                return@withContext AgentExecutionResult.Failure(
                    reason = failState.reason,
                    turnsUsed = budget.currentTurn,
                    messages = conversation.toList(),
                    cause = e
                )
            }

            _state.value = AgentState.Thinking(turn = turn, maxTurns = budget.maxTurns)

            val toolDeclarations = toolRegistry.getDeclarations().ifEmpty { null }
            val request = AgentRelayRequest(
                sessionId = sessionId,
                model = model,
                messages = conversation.toList(),
                tools = toolDeclarations,
                temperature = temperature
            )

            val response = try {
                withTimeout(budget.turnTimeoutMs) {
                    relayClient.sendTurn(request)
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: Exception) {
                val reason = "Falha de rede no turno $turn: ${e.message}"
                Log.w(TAG, reason, e)
                val failState = AgentState.Failed(reason = reason, turn = turn, error = e)
                _state.value = failState
                return@withContext AgentExecutionResult.Failure(
                    reason = reason,
                    turnsUsed = turn,
                    messages = conversation.toList(),
                    cause = e
                )
            }

            if (response == null || response.status != "success") {
                val reason = "Relay retornou resposta nula ou com status de erro no turno $turn"
                Log.w(TAG, reason)
                val failState = AgentState.Failed(reason = reason, turn = turn)
                _state.value = failState
                return@withContext AgentExecutionResult.Failure(
                    reason = reason,
                    turnsUsed = turn,
                    messages = conversation.toList()
                )
            }

            if (response.usage != null) {
                lastUsage = response.usage
            }

            val assistantMessage = response.message
            conversation.add(assistantMessage)

            val toolCalls = assistantMessage.toolCalls
            if (toolCalls.isNullOrEmpty()) {
                // Modelo concluiu o raciocínio sem solicitar novas ferramentas
                val completedState = AgentState.Completed(
                    finalMessage = assistantMessage,
                    totalTurns = turn,
                    usage = lastUsage
                )
                _state.value = completedState
                return@withContext AgentExecutionResult.Success(
                    content = assistantMessage.content.orEmpty(),
                    message = assistantMessage,
                    turnsUsed = turn,
                    messages = conversation.toList(),
                    usage = lastUsage
                )
            }

            // Executa as ferramentas solicitadas pelo modelo no dispositivo
            for (toolCall in toolCalls) {
                val toolName = toolCall.function.name
                val toolArgs = toolCall.function.arguments

                _state.value = AgentState.ExecutingTool(
                    turn = turn,
                    toolName = toolName,
                    toolCallId = toolCall.id,
                    arguments = toolArgs
                )

                val rawOutput = try {
                    val tool = toolRegistry.get(toolName)
                    if (tool != null) {
                        tool.execute(toolArgs)
                    } else {
                        "Erro: Ferramenta '$toolName' não registrada no dispositivo."
                    }
                } catch (ce: CancellationException) {
                    throw ce
                } catch (e: Exception) {
                    Log.w(TAG, "Exceção ao executar ferramenta '$toolName'", e)
                    "Erro ao executar '$toolName': ${e.message}"
                }

                // Sanitização estrita de privacidade on-device antes do retorno ao modelo
                val sanitizedOutput = privacySanitizer.sanitize(rawOutput)

                _state.value = AgentState.Observing(
                    turn = turn,
                    toolName = toolName,
                    preview = sanitizedOutput.take(MAX_OBSERVATION_PREVIEW_LENGTH)
                )

                conversation.add(
                    AgentMessage(
                        role = AgentRole.TOOL,
                        content = sanitizedOutput,
                        toolCallId = toolCall.id,
                        name = toolName
                    )
                )
            }
        }

        // Se sair do loop sem completar
        val exhaustedReason = if (budget.isSessionTimedOut()) {
            "Tempo total da sessão esgotado (${budget.sessionTimeoutMs} ms)"
        } else {
            "Limite de ${budget.maxTurns} turnos atingido sem conclusão pelo modelo"
        }
        val failState = AgentState.Failed(reason = exhaustedReason, turn = budget.currentTurn)
        _state.value = failState
        return@withContext AgentExecutionResult.Failure(
            reason = exhaustedReason,
            turnsUsed = budget.currentTurn,
            messages = conversation.toList()
        )
    }
}
