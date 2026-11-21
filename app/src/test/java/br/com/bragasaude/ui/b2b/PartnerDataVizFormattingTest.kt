package br.com.bragasaude.ui.b2b

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Formatação/contraste dos gráficos do B2B, sem depender de instrumentação. */
class PartnerDataVizFormattingTest {

    @Test
    fun `rotulo central do anel usa percentual inteiro`() {
        assertEquals("100%", ringPercentLabel(1, 1))
        assertEquals("50%", ringPercentLabel(1, 2))
        assertEquals("33%", ringPercentLabel(1, 3))
        assertEquals("", ringPercentLabel(0, 0))
    }

    @Test
    fun `sparkline resume maximo e ultimo dia`() {
        assertEquals("máx. 2 · último dia 0", sparklineSummaryLabel(listOf(0, 1, 2, 0)))
        assertEquals("máx. 2 · último dia 2", sparklineSummaryLabel(listOf(2)))
        assertNull(sparklineSummaryLabel(emptyList()))
    }

    @Test
    fun `trilha de progresso alcanca 3 para 1 sobre superficie clara e escura`() {
        val lightTrack = trackColorFor(Color(0xFF49454F), Color.White)
        assertTrue(
            "trilha clara deve ter contraste >= 3:1",
            contrastRatio(lightTrack, Color.White) >= 3f
        )
        val darkTrack = trackColorFor(Color(0xFFCAC4D0), Color(0xFF1C1B1F))
        assertTrue(
            "trilha escura deve ter contraste >= 3:1",
            contrastRatio(darkTrack, Color(0xFF1C1B1F)) >= 3f
        )
    }

    @Test
    fun `descricao da serie diaria traz valores ausentes e media`() {
        val spoken = dailyBarsDescription(listOf(0L, null, 2L), 1.0)
        assertTrue(spoken.contains("3 dias"))
        assertTrue(spoken.contains("sem registro"))
        assertTrue(spoken.contains("2"))
        assertTrue(spoken.contains("Média de 1"))
        assertTrue(spoken.contains("por dia"))
    }

    @Test
    fun `serie diaria vazia fala sem registros`() {
        assertEquals("Sem registros no período.", dailyBarsDescription(emptyList(), 0.0))
    }
}
