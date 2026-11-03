package br.com.bragasaude.agent.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * Protocolo de comunicação agêntica para Function Calling agnóstico de modelo.
 * Alinhado 100% com os contratos de braga-contracts (/api/ai/agent-relay).
 */

@Serializable
enum class AgentRole {
    @SerialName("system") SYSTEM,
    @SerialName("user") USER,
    @SerialName("assistant") ASSISTANT,
    @SerialName("tool") TOOL
}

@Serializable
data class ToolCallFunction(
    val name: String,
    val arguments: String
)

@Serializable
data class ToolCall(
    val id: String,
    val type: String = "function",
    val function: ToolCallFunction
)

@Serializable
data class AgentMessage(
    val role: AgentRole,
    val content: String? = null,
    @SerialName("tool_calls") val toolCalls: List<ToolCall>? = null,
    @SerialName("tool_call_id") val toolCallId: String? = null,
    val name: String? = null
)

@Serializable
data class ToolFunctionDef(
    val name: String,
    val description: String,
    val parameters: JsonObject
)

@Serializable
data class ToolDeclaration(
    val type: String = "function",
    val function: ToolFunctionDef
)

@Serializable
data class AgentRelayRequest(
    @SerialName("session_id") val sessionId: String? = null,
    val model: String? = null,
    val messages: List<AgentMessage>,
    val tools: List<ToolDeclaration>? = null,
    val temperature: Float? = 0.0f,
    @SerialName("max_tokens") val maxTokens: Int? = 1024
)

@Serializable
data class AgentUsage(
    @SerialName("prompt_tokens") val promptTokens: Int = 0,
    @SerialName("completion_tokens") val completionTokens: Int = 0,
    @SerialName("total_tokens") val totalTokens: Int = 0
)

@Serializable
data class AgentRelayResponse(
    val status: String,
    val message: AgentMessage,
    @SerialName("finish_reason") val finishReason: String? = null,
    val usage: AgentUsage? = null,
    val provider: String? = null
)
