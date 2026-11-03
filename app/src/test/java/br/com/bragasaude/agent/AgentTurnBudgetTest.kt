package br.com.bragasaude.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class AgentTurnBudgetTest {

    @Test
    fun `inicializa com valores padrao corretos`() {
        val budget = AgentTurnBudget()
        assertEquals(AgentTurnBudget.DEFAULT_MAX_TURNS, budget.maxTurns)
        assertEquals(AgentTurnBudget.DEFAULT_TURN_TIMEOUT_MS, budget.turnTimeoutMs)
        assertEquals(AgentTurnBudget.DEFAULT_SESSION_TIMEOUT_MS, budget.sessionTimeoutMs)
        assertEquals(0, budget.currentTurn)
        assertEquals(5, budget.remainingTurns)
        assertTrue(budget.canProceed())
    }

    @Test
    fun `consome turnos sequencialmente e atualiza contadores`() {
        val budget = AgentTurnBudget(maxTurns = 3)
        budget.start()

        assertEquals(3, budget.remainingTurns)
        assertEquals(1, budget.consumeTurn())
        assertEquals(1, budget.currentTurn)
        assertEquals(2, budget.remainingTurns)
        assertTrue(budget.canProceed())

        assertEquals(2, budget.consumeTurn())
        assertEquals(2, budget.currentTurn)
        assertEquals(1, budget.remainingTurns)
        assertTrue(budget.canProceed())

        assertEquals(3, budget.consumeTurn())
        assertEquals(3, budget.currentTurn)
        assertEquals(0, budget.remainingTurns)
        assertFalse(budget.canProceed())
    }

    @Test
    fun `dispara BudgetExceededException ao exceder o limite de turnos`() {
        val budget = AgentTurnBudget(maxTurns = 2)
        budget.start()

        budget.consumeTurn()
        budget.consumeTurn()

        try {
            budget.consumeTurn()
            fail("Deveria ter disparado BudgetExceededException")
        } catch (e: BudgetExceededException) {
            assertTrue(e.message?.contains("Limite de turnos excedido") == true)
        }
    }

    @Test
    fun `detecta timeout de sessao com provedor de tempo customizado`() {
        var currentTime = 1000L
        val budget = AgentTurnBudget(
            maxTurns = 5,
            sessionTimeoutMs = 10_000L,
            timeProvider = { currentTime }
        )

        budget.start()
        assertTrue(budget.canProceed())

        // Avança o tempo além do limite da sessão
        currentTime += 15_000L

        assertFalse(budget.canProceed())
        assertTrue(budget.isSessionTimedOut())

        try {
            budget.consumeTurn()
            fail("Deveria ter disparado BudgetExceededException por timeout")
        } catch (e: BudgetExceededException) {
            assertTrue(e.message?.contains("Tempo total da sessão esgotado") == true)
        }
    }

    @Test
    fun `reset restaura estado inicial do orcamento`() {
        val budget = AgentTurnBudget(maxTurns = 2)
        budget.start()
        budget.consumeTurn()
        budget.consumeTurn()
        assertFalse(budget.canProceed())

        budget.reset()
        budget.start()
        assertEquals(0, budget.currentTurn)
        assertEquals(2, budget.remainingTurns)
        assertTrue(budget.canProceed())
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejeita maxTurns menor ou igual a zero`() {
        AgentTurnBudget(maxTurns = 0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejeita sessionTimeout menor que turnTimeout`() {
        AgentTurnBudget(turnTimeoutMs = 5000L, sessionTimeoutMs = 2000L)
    }
}
