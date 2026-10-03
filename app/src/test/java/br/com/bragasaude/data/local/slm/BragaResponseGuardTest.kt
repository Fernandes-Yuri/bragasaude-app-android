package br.com.bragasaude.data.local.slm

import org.junit.Assert.*
import org.junit.Test

class BragaResponseGuardTest {
    private val draft = BragaResolvedContext(BragaIntent.REGISTRO_AGUA, "Preparação pendente de 300 ml", "Deixei prontos 300 mililitros.", "REGISTRAR_AGUA")
    @Test fun rejectsChangedNumbersAndFalseSaveClaims() {
        assertEquals(draft.fallback, BragaResponseGuard.accept("Preparei 500 mililitros", draft))
        assertEquals(draft.fallback, BragaResponseGuard.accept("Já registrei seus 300 mililitros", draft))
        assertEquals("Preparei 300 mililitros com carinho", BragaResponseGuard.accept("Preparei 300 mililitros com carinho", draft))
    }
    @Test fun freeConversationCannotClaimAnUnrequestedRegistration() {
        val free = BragaResolvedContext(BragaIntent.CONVERSA_LIVRE, "Acolha", "Estou aqui com você, meu bem.", factual = false)
        assertEquals(free.fallback, BragaResponseGuard.accept("Já anotei sua água", free))
        assertEquals(free.fallback, BragaResponseGuard.accept("Confira os dados e toque em salvar", free))
        assertEquals("Pode me contar mais sobre essa saudade?", BragaResponseGuard.accept("Pode me contar mais sobre essa saudade?", free))
    }
    @Test fun swappedPressureValuesAreRejected() {
        val pressure = BragaResolvedContext(BragaIntent.REGISTRO_PRESSAO, "Prepare 120 por 80", "Preparei 120 por 80.", "REGISTRAR_PRESSAO")
        assertEquals(pressure.fallback, BragaResponseGuard.accept("Preparei 80 por 120", pressure))
    }
    @Test fun clinicalHistoryCannotBeReinterpretedByModel() {
        val history = BragaResolvedContext(BragaIntent.CONSULTA_HISTORICO, "130 por 80", "Sua pressão registrada foi 130 por 80.")
        assertEquals(history.fallback, BragaResponseGuard.accept("Sua pressão 130 por 80 está normal", history))
    }
}
