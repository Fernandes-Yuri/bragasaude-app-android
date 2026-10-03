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
    @Test fun clinicalHistoryCannotBeReinterpretedByModel() {
        val history = BragaResolvedContext(BragaIntent.CONSULTA_HISTORICO, "130 por 80", "Sua pressão registrada foi 130 por 80.")
        assertEquals(history.fallback, BragaResponseGuard.accept("Sua pressão 130 por 80 está normal", history))
    }
}
