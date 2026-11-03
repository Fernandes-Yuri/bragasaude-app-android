package br.com.bragasaude.agent

import br.com.bragasaude.agent.protocol.AgentMessage
import br.com.bragasaude.agent.protocol.AgentRelayClient
import br.com.bragasaude.agent.protocol.AgentRelayRequest
import br.com.bragasaude.agent.protocol.AgentRelayResponse
import br.com.bragasaude.agent.protocol.AgentRole
import br.com.bragasaude.agent.protocol.AgentUsage
import br.com.bragasaude.agent.protocol.ToolCall
import br.com.bragasaude.agent.protocol.ToolCallFunction
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AgentEngineTest {

    private lateinit var relayClient: AgentRelayClient
    private lateinit var toolRegistry: ToolRegistry
    private lateinit var privacySanitizer: PrivacySanitizer

    @Before
    fun setUp() {
        relayClient = mockk()
        toolRegistry = ToolRegistry()
        privacySanitizer = PrivacySanitizer()
    }

    @Test
    fun `executa resposta direta em um turno com sucesso sem ferramentas`() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val engine = AgentEngine(relayClient, toolRegistry, privacySanitizer, testDispatcher)

        val assistantMessage = AgentMessage(
            role = AgentRole.ASSISTANT,
            content = "Olá! Como posso ajudar você hoje?"
        )
        val expectedResponse = AgentRelayResponse(
            status = "success",
            message = assistantMessage,
            finishReason = "stop",
            usage = AgentUsage(10, 20, 30)
        )

        coEvery { relayClient.sendTurn(any()) } returns expectedResponse

        val states = mutableListOf<AgentState>()
        val collectJob = launch(testDispatcher) {
            engine.state.toList(states)
        }

        val result = engine.run(
            userPrompt = "Olá",
            systemPrompt = "Você é um assistente de saúde."
        )

        advanceUntilIdle()
        collectJob.cancel()

        assertTrue(result is AgentExecutionResult.Success)
        val success = result as AgentExecutionResult.Success
        assertEquals("Olá! Como posso ajudar você hoje?", success.content)
        assertEquals(1, success.turnsUsed)
        assertEquals(30, success.usage?.totalTokens)

        // Verifica transições de estado
        assertTrue(states.any { it is AgentState.Idle })
        assertTrue(states.any { it is AgentState.Thinking && it.turn == 1 })
        assertTrue(states.any { it is AgentState.Completed })
    }

    @Test
    fun `executa loop multi-turnos com invocacao e execucao de ferramenta`() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val engine = AgentEngine(relayClient, toolRegistry, privacySanitizer, testDispatcher)

        // Registra ferramenta nativa de consulta
        toolRegistry.register(
            SimpleAgentTool("consultar_medicamento", "Consulta dados de bula") { args ->
                "Medicamento: Amoxicilina 500mg, Uso: Antibacteriano oral."
            }
        )

        val toolCall = ToolCall(
            id = "call_abc123",
            function = ToolCallFunction(
                name = "consultar_medicamento",
                arguments = "{\"nome\":\"Amoxicilina\"}"
            )
        )

        val turn1Response = AgentRelayResponse(
            status = "success",
            message = AgentMessage(
                role = AgentRole.ASSISTANT,
                content = null,
                toolCalls = listOf(toolCall)
            )
        )

        val turn2Response = AgentRelayResponse(
            status = "success",
            message = AgentMessage(
                role = AgentRole.ASSISTANT,
                content = "A Amoxicilina 500mg é um antibiótico para infecções bacterianas."
            ),
            finishReason = "stop"
        )

        coEvery { relayClient.sendTurn(any()) } returnsMany listOf(turn1Response, turn2Response)

        val states = mutableListOf<AgentState>()
        val collectJob = launch(testDispatcher) {
            engine.state.toList(states)
        }

        val result = engine.run(userPrompt = "Para que serve a amoxicilina?")

        advanceUntilIdle()
        collectJob.cancel()

        assertTrue(result is AgentExecutionResult.Success)
        val success = result as AgentExecutionResult.Success
        assertEquals("A Amoxicilina 500mg é um antibiótico para infecções bacterianas.", success.content)
        assertEquals(2, success.turnsUsed)

        // Verifica histórico completo de mensagens (User -> Assistant com toolCalls -> Tool Observation -> Assistant final)
        assertEquals(4, success.messages.size)
        assertEquals(AgentRole.USER, success.messages[0].role)
        assertEquals(AgentRole.ASSISTANT, success.messages[1].role)
        assertEquals(AgentRole.TOOL, success.messages[2].role)
        assertEquals("call_abc123", success.messages[2].toolCallId)
        assertEquals(AgentRole.ASSISTANT, success.messages[3].role)

        // Verifica máquina de estados completa
        assertTrue(states.any { it is AgentState.Thinking && it.turn == 1 })
        assertTrue(states.any { it is AgentState.ExecutingTool && it.toolName == "consultar_medicamento" })
        assertTrue(states.any { it is AgentState.Observing && it.toolName == "consultar_medicamento" })
        assertTrue(states.any { it is AgentState.Thinking && it.turn == 2 })
        assertTrue(states.any { it is AgentState.Completed })
    }

    @Test
    fun `sanitiza dados pessoais da saida da ferramenta antes de enviar ao modelo no proximo turno`() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val engine = AgentEngine(relayClient, toolRegistry, privacySanitizer, testDispatcher)

        // Ferramenta que retorna dados do paciente com PII
        toolRegistry.register(
            SimpleAgentTool("ler_cadastro_paciente", "Lê ficha médica") {
                "Paciente: João Silva, CPF: 123.456.789-00, Telefone: (11) 98765-4321, Alergia: Penicilina."
            }
        )

        val toolCall = ToolCall(
            id = "call_pii_01",
            function = ToolCallFunction(
                name = "ler_cadastro_paciente",
                arguments = "{}"
            )
        )

        val turn1Response = AgentRelayResponse(
            status = "success",
            message = AgentMessage(
                role = AgentRole.ASSISTANT,
                content = null,
                toolCalls = listOf(toolCall)
            )
        )

        val turn2Response = AgentRelayResponse(
            status = "success",
            message = AgentMessage(
                role = AgentRole.ASSISTANT,
                content = "O paciente possui alergia a Penicilina."
            )
        )

        val capturedRequests = mutableListOf<AgentRelayRequest>()
        coEvery { relayClient.sendTurn(capture(capturedRequests)) } returnsMany listOf(turn1Response, turn2Response)

        val result = engine.run(userPrompt = "Verifique alergias do paciente")

        advanceUntilIdle()

        assertTrue(result is AgentExecutionResult.Success)
        assertEquals(2, capturedRequests.size)

        // Analisa a requisição enviada no turno 2 (que contém o Tool Observation)
        val turn2Request = capturedRequests[1]
        val toolMessage = turn2Request.messages.find { it.role == AgentRole.TOOL }
        assertNotNull(toolMessage)

        val toolContent = toolMessage?.content.orEmpty()
        // PII deve estar estritamente ofuscada
        assertFalse(toolContent.contains("123.456.789-00"))
        assertFalse(toolContent.contains("98765-4321"))
        assertTrue(toolContent.contains(PrivacySanitizer.REDACTED_CPF))
        assertTrue(toolContent.contains(PrivacySanitizer.REDACTED_PHONE))
        // Dado clínico deve estar íntegro
        assertTrue(toolContent.contains("Alergia: Penicilina"))
    }

    @Test
    fun `limita loop infinito quando orcamento de turnos e excedido`() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val engine = AgentEngine(relayClient, toolRegistry, privacySanitizer, testDispatcher)

        toolRegistry.register(SimpleAgentTool("loop_tool", "Tool que repete") { "ok" })

        val loopingToolCall = ToolCall(
            id = "call_loop",
            function = ToolCallFunction(name = "loop_tool", arguments = "{}")
        )
        val loopingResponse = AgentRelayResponse(
            status = "success",
            message = AgentMessage(
                role = AgentRole.ASSISTANT,
                content = null,
                toolCalls = listOf(loopingToolCall)
            )
        )

        // Responde sempre com tool call para induzir loop
        coEvery { relayClient.sendTurn(any()) } returns loopingResponse

        val budget = AgentTurnBudget(maxTurns = 2)
        val result = engine.run(userPrompt = "Faça algo em loop", budget = budget)

        advanceUntilIdle()

        assertTrue(result is AgentExecutionResult.Failure)
        val failure = result as AgentExecutionResult.Failure
        assertEquals(2, failure.turnsUsed)
        assertTrue(failure.reason.contains("Limite de 2 turnos atingido") || failure.reason.contains("Limite de turnos excedido"))
        assertTrue(engine.state.value is AgentState.Failed)
    }

    @Test
    fun `trata falha de rede graciosamente e transiciona para Failed`() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val engine = AgentEngine(relayClient, toolRegistry, privacySanitizer, testDispatcher)

        coEvery { relayClient.sendTurn(any()) } returns null

        val result = engine.run(userPrompt = "Pergunta")

        advanceUntilIdle()

        assertTrue(result is AgentExecutionResult.Failure)
        val failure = result as AgentExecutionResult.Failure
        assertTrue(failure.reason.contains("Relay retornou resposta nula"))
        assertTrue(engine.state.value is AgentState.Failed)
    }

    @Test
    fun `captura excecao de execucao de ferramenta e repassa como observacao de erro sem derrubar o harness`() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val engine = AgentEngine(relayClient, toolRegistry, privacySanitizer, testDispatcher)

        toolRegistry.register(
            SimpleAgentTool("sensor_falho", "Lê sensor") {
                throw IllegalStateException("Hardware indisponível no momento")
            }
        )

        val toolCall = ToolCall(
            id = "call_sensor_err",
            function = ToolCallFunction(name = "sensor_falho", arguments = "{}")
        )

        val turn1Response = AgentRelayResponse(
            status = "success",
            message = AgentMessage(
                role = AgentRole.ASSISTANT,
                content = null,
                toolCalls = listOf(toolCall)
            )
        )

        val turn2Response = AgentRelayResponse(
            status = "success",
            message = AgentMessage(
                role = AgentRole.ASSISTANT,
                content = "Não consegui ler o sensor porque o hardware está indisponível."
            )
        )

        val capturedRequests = mutableListOf<AgentRelayRequest>()
        coEvery { relayClient.sendTurn(capture(capturedRequests)) } returnsMany listOf(turn1Response, turn2Response)

        val result = engine.run(userPrompt = "Leia o sensor")

        advanceUntilIdle()

        assertTrue(result is AgentExecutionResult.Success)
        val toolMessage = capturedRequests[1].messages.find { it.role == AgentRole.TOOL }
        assertNotNull(toolMessage)
        assertTrue(toolMessage?.content?.contains("Hardware indisponível no momento") == true)
    }

    @Test
    fun `reseta estado do agente para Idle`() {
        val engine = AgentEngine(relayClient, toolRegistry, privacySanitizer)
        engine.reset()
        assertEquals(AgentState.Idle, engine.state.value)
    }
}
