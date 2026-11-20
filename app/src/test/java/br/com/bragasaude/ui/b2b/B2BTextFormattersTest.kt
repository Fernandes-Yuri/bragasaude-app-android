package br.com.bragasaude.ui.b2b

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class B2BTextFormattersTest {

    @Test
    fun `plurais reais respeitam singular e plural`() {
        assertEquals("1 dia", B2BTextFormatters.days(1))
        assertEquals("2 dias", B2BTextFormatters.days(2))
        assertEquals("1 leitura confirmada", B2BTextFormatters.countLabel(1, "leitura confirmada", "leituras confirmadas"))
        assertEquals("3 leituras confirmadas", B2BTextFormatters.countLabel(3, "leitura confirmada", "leituras confirmadas"))
        assertEquals(
            "2 participantes ativos",
            B2BTextFormatters.countLabelAgreement(2, "participante", "participantes", "ativo", "ativos")
        )
        assertEquals("1 confirmação", B2BTextFormatters.countLabel(1L, "confirmação", "confirmações"))
        assertEquals("3 confirmações", B2BTextFormatters.countLabel(3L, "confirmação", "confirmações"))
    }

    @Test
    fun `datas ISO viram dd MM yyyy e timestamps pt-BR`() {
        assertEquals("19/11/2026", B2BTextFormatters.formatIsoDate("2026-11-19"))
        assertEquals("23/11/2026 às 10:30", B2BTextFormatters.formatIsoDateTime("2026-11-23T10:30:00-03:00"))
        assertEquals("texto livre", B2BTextFormatters.formatIsoDate("texto livre"))
        assertNull(B2BTextFormatters.formatIsoDate(null))
    }

    @Test
    fun `enums de ativacao nao vazam para a UI`() {
        assertEquals("Sempre no primeiro contato", B2BTextFormatters.activationModeLabel("ALWAYS_FIRST"))
        assertEquals("Fora do expediente", B2BTextFormatters.activationModeLabel("OUT_OF_HOURS_ONLY"))
        assertEquals("Sob demanda", B2BTextFormatters.activationModeLabel("MANUAL_ONLY"))
        assertEquals("Sob demanda", B2BTextFormatters.activationModeLabel(null))
    }
}
