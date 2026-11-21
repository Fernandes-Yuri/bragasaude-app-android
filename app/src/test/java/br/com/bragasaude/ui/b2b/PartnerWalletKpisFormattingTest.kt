package br.com.bragasaude.ui.b2b

import org.junit.Assert.assertEquals
import org.junit.Test

/** Textos dos cards de KPI: delta nulo nunca vira "0" nem "comparação indisponível". */
class PartnerWalletKpisFormattingTest {

    @Test
    fun `delta ausente fala sem base na semana anterior`() {
        assertEquals("Sem base na semana anterior", appointmentsDeltaLabel(null))
        assertEquals("Sem base na semana anterior", appointmentsDeltaSpoken(null))
    }

    @Test
    fun `delta zero indica igualdade`() {
        assertEquals("Igual à semana anterior", appointmentsDeltaLabel(0))
        assertEquals("Igual à semana anterior", appointmentsDeltaSpoken(0))
    }

    @Test
    fun `delta positivo usa seta para cima e leitura de aumento`() {
        assertEquals("▲ +2 em relação à semana anterior", appointmentsDeltaLabel(2))
        assertEquals("Aumento de 2 em relação à semana anterior", appointmentsDeltaSpoken(2))
    }

    @Test
    fun `delta negativo usa seta para baixo e leitura de reducao`() {
        assertEquals("▼ -2 em relação à semana anterior", appointmentsDeltaLabel(-2))
        assertEquals("Redução de 2 em relação à semana anterior", appointmentsDeltaSpoken(-2))
    }
}
