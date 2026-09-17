package br.com.bragasaude.ui.profile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileInputTest {

    @Test
    fun `deve falhar validacao quando nome estiver em branco`() {
        val input = ProfileInput(name = "   ", weight = 70.0, height = 175.0)
        val errors = input.validate(enableSelfCare = true)
        assertTrue(errors.containsKey("name"))
        assertEquals("Informe seu nome completo.", errors["name"])
    }

    @Test
    fun `deve falhar quando peso estiver fora dos limites clinicos`() {
        val input = ProfileInput(name = "Maria Silva", weight = 15.0, height = 160.0)
        val errors = input.validate(enableSelfCare = true)
        assertTrue(errors.containsKey("weight"))
    }

    @Test
    fun `deve falhar quando altura estiver fora dos limites clinicos`() {
        val input = ProfileInput(name = "Maria Silva", weight = 65.0, height = 300.0)
        val errors = input.validate(enableSelfCare = true)
        assertTrue(errors.containsKey("height"))
    }

    @Test
    fun `deve passar validacao quando campos estiverem corretos`() {
        val input = ProfileInput(name = "João da Silva", weight = 75.5, height = 172.0)
        val errors = input.validate(enableSelfCare = true)
        assertTrue(errors.isEmpty())
    }
}
