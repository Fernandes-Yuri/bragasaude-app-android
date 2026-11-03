package br.com.bragasaude.agent

import br.com.bragasaude.agent.protocol.ToolDeclaration
import br.com.bragasaude.agent.protocol.ToolFunctionDef
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Interface que representa uma ferramenta executável nativamente no dispositivo Android.
 */
interface AgentTool {
    val declaration: ToolDeclaration
    val name: String get() = declaration.function.name
    val description: String get() = declaration.function.description

    /**
     * Executa a ferramenta com os argumentos JSON fornecidos pelo modelo.
     * Retorna a saída bruta em texto/JSON a ser sanitizada pelo harness.
     */
    suspend fun execute(argumentsJson: String): String
}

/**
 * Implementação utilitária para registrar ferramentas baseadas em lambdas/funções simples.
 */
open class SimpleAgentTool(
    name: String,
    description: String,
    parameters: JsonObject = buildJsonObject { },
    private val handler: suspend (argumentsJson: String) -> String
) : AgentTool {
    override val declaration: ToolDeclaration = ToolDeclaration(
        type = "function",
        function = ToolFunctionDef(
            name = name,
            description = description,
            parameters = parameters
        )
    )

    override suspend fun execute(argumentsJson: String): String = handler(argumentsJson)
}

/**
 * Gerenciador central de registro e catálogo de ferramentas disponíveis para o Mini Harness.
 */
@Singleton
class ToolRegistry @Inject constructor() {
    private val tools = ConcurrentHashMap<String, AgentTool>()

    /**
     * Registra uma ferramenta nativa.
     */
    fun register(tool: AgentTool) {
        tools[tool.name] = tool
    }

    /**
     * Registra múltiplas ferramentas.
     */
    fun registerAll(vararg newTools: AgentTool) {
        newTools.forEach { register(it) }
    }

    /**
     * Registra uma coleção de ferramentas.
     */
    fun registerAll(newTools: Collection<AgentTool>) {
        newTools.forEach { register(it) }
    }

    /**
     * Remove uma ferramenta pelo nome.
     */
    fun unregister(name: String): AgentTool? = tools.remove(name)

    /**
     * Obtém uma ferramenta registrada.
     */
    fun get(name: String): AgentTool? = tools[name]

    /**
     * Verifica se a ferramenta está cadastrada.
     */
    fun hasTool(name: String): Boolean = tools.containsKey(name)

    /**
     * Retorna todas as declarações de ferramentas no formato aceito pelo Relay/LLM.
     */
    fun getDeclarations(): List<ToolDeclaration> = tools.values.map { it.declaration }

    /**
     * Retorna os nomes de todas as ferramentas cadastradas.
     */
    fun listToolNames(): List<String> = tools.keys().toList()

    /**
     * Limpa todas as ferramentas cadastradas.
     */
    fun clear() {
        tools.clear()
    }

    /**
     * Quantidade de ferramentas cadastradas.
     */
    val size: Int get() = tools.size
}
