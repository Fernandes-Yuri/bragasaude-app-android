package br.com.bragasaude.agent

/**
 * Orçamento e cotas de execução para o harness do agente no Android.
 * Previne loops infinitos, controla consumo de bateria e garante timeouts seguros.
 */
data class AgentTurnBudget(
    val maxTurns: Int = DEFAULT_MAX_TURNS,
    val turnTimeoutMs: Long = DEFAULT_TURN_TIMEOUT_MS,
    val sessionTimeoutMs: Long = DEFAULT_SESSION_TIMEOUT_MS,
    private val timeProvider: () -> Long = { System.currentTimeMillis() }
) {
    companion object {
        const val DEFAULT_MAX_TURNS = 5
        const val DEFAULT_TURN_TIMEOUT_MS = 30_000L // 30 segundos por turno de rede
        const val DEFAULT_SESSION_TIMEOUT_MS = 120_000L // 2 minutos para a sessão total
    }

    init {
        require(maxTurns > 0) { "maxTurns deve ser maior que zero (recebido: $maxTurns)" }
        require(turnTimeoutMs > 0) { "turnTimeoutMs deve ser maior que zero (recebido: $turnTimeoutMs)" }
        require(sessionTimeoutMs >= turnTimeoutMs) {
            "sessionTimeoutMs ($sessionTimeoutMs) deve ser >= turnTimeoutMs ($turnTimeoutMs)"
        }
    }

    private var currentTurnCount = 0
    private var sessionStartTimeMs: Long = 0L

    /**
     * Inicia a contagem de tempo da sessão e zera os turnos.
     */
    fun start() {
        currentTurnCount = 0
        sessionStartTimeMs = timeProvider()
    }

    /**
     * Verifica se ainda há orçamento para realizar o próximo turno.
     */
    fun canProceed(): Boolean {
        if (currentTurnCount >= maxTurns) return false
        if (sessionStartTimeMs > 0L && (timeProvider() - sessionStartTimeMs) > sessionTimeoutMs) {
            return false
        }
        return true
    }

    /**
     * Consome um turno do orçamento.
     * @return O número do turno atual (1..maxTurns).
     * @throws BudgetExceededException se o limite de turnos ou tempo de sessão for ultrapassado.
     */
    fun consumeTurn(): Int {
        if (!canProceed()) {
            if (isSessionTimedOut()) {
                throw BudgetExceededException("Tempo total da sessão esgotado ($sessionTimeoutMs ms)")
            }
            throw BudgetExceededException("Limite de turnos excedido: $currentTurnCount >= $maxTurns")
        }
        currentTurnCount++
        return currentTurnCount
    }

    /**
     * Turno atual já consumido.
     */
    val currentTurn: Int get() = currentTurnCount

    /**
     * Quantidade de turnos restantes.
     */
    val remainingTurns: Int get() = (maxTurns - currentTurnCount).coerceAtLeast(0)

    /**
     * Verifica se o tempo total da sessão expirou.
     */
    fun isSessionTimedOut(): Boolean {
        if (sessionStartTimeMs == 0L) return false
        return (timeProvider() - sessionStartTimeMs) > sessionTimeoutMs
    }

    /**
     * Reseta a contagem de turnos e o cronômetro da sessão.
     */
    fun reset() {
        currentTurnCount = 0
        sessionStartTimeMs = 0L
    }
}

/**
 * Exceção lançada quando as cotas de turnos ou tempo da sessão são excedidas.
 */
class BudgetExceededException(message: String) : IllegalStateException(message)
