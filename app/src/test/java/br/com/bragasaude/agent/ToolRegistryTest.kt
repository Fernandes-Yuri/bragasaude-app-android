package br.com.bragasaude.agent

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ToolRegistryTest {

    private lateinit var registry: ToolRegistry

    @Before
    fun setUp() {
        registry = ToolRegistry()
    }

    @Test
    fun `registra ferramenta simples e executa com sucesso`() = runTest {
        val tool = SimpleAgentTool(
            name = "calculate_sum",
            description = "Calcula a soma de dois numeros",
            parameters = buildJsonObject {
                put("type", "object")
            }
        ) { argsJson ->
            "resultado: 42"
        }

        registry.register(tool)

        assertTrue(registry.hasTool("calculate_sum"))
        assertEquals(1, registry.size)

        val retrieved = registry.get("calculate_sum")
        assertNotNull(retrieved)
        assertEquals("calculate_sum", retrieved?.name)
        assertEquals("Calcula a soma de dois numeros", retrieved?.description)

        val output = retrieved?.execute("{}")
        assertEquals("resultado: 42", output)
    }

    @Test
    fun `registra multiplas ferramentas e gera declaracoes corretas`() {
        val toolA = SimpleAgentTool("tool_a", "Desc A") { "A" }
        val toolB = SimpleAgentTool("tool_b", "Desc B") { "B" }

        registry.registerAll(toolA, toolB)

        assertEquals(2, registry.size)
        assertTrue(registry.hasTool("tool_a"))
        assertTrue(registry.hasTool("tool_b"))

        val declarations = registry.getDeclarations()
        assertEquals(2, declarations.size)
        val names = declarations.map { it.function.name }
        assertTrue(names.contains("tool_a"))
        assertTrue(names.contains("tool_b"))
    }

    @Test
    fun `remove ferramenta e limpa catalogo`() {
        val tool = SimpleAgentTool("tool_temp", "Desc Temp") { "Temp" }
        registry.register(tool)
        assertEquals(1, registry.size)

        val removed = registry.unregister("tool_temp")
        assertNotNull(removed)
        assertFalse(registry.hasTool("tool_temp"))
        assertEquals(0, registry.size)

        registry.register(tool)
        registry.clear()
        assertEquals(0, registry.size)
        assertNull(registry.get("tool_temp"))
    }

    @Test
    fun `retorna nulo para ferramenta nao encontrada`() {
        assertNull(registry.get("inexistente"))
        assertFalse(registry.hasTool("inexistente"))
    }
}
