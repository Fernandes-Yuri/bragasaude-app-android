package br.com.bragasaude.data.local.organizer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PatientNameConsistencyCheckerTest {
    @Test fun `mesmo paciente com caixa acentos e pontuacao diferentes nao gera divergencia`() {
        val names = listOf("Maria da Silva", "maria da silva", "MARIA DA SILVA", "  Maria da Silva  ")
        assertTrue(PatientNameConsistencyChecker.divergentNames(names).isEmpty())
        assertTrue(PatientNameConsistencyChecker.divergentNames(listOf("José da Silva", "jose da silva")).isEmpty())
    }

    @Test fun `pacientes diferentes sao listados uma unica vez e em ordem estavel`() {
        val names = listOf("Joaquim Ficticio Testes", "Heloisa Ficticia Exemplar", "joaquim ficticio testes")
        assertEquals(
            listOf("Heloisa Ficticia Exemplar", "Joaquim Ficticio Testes"),
            PatientNameConsistencyChecker.divergentNames(names)
        )
    }

    @Test fun `nomes ausentes nao geram falso alerta`() {
        assertTrue(PatientNameConsistencyChecker.divergentNames(listOf("", "   ")).isEmpty())
        assertTrue(PatientNameConsistencyChecker.divergentNames(listOf("", "Maria Silva")).isEmpty())
    }
}
