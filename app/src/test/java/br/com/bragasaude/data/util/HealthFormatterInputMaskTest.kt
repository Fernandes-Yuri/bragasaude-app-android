package br.com.bragasaude.data.util

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Testes JVM puros das máscaras de digitação de data e hora.
 *
 * As telas guardam no estado a string já formatada ("dd/MM/yyyy" e "HH:mm"), porque os
 * parses downstream esperam esse formato; por isso os testes cobrem digitação completa,
 * digitação parcial e reentrada de valor já formatado.
 */
class HealthFormatterInputMaskTest {

    @Test fun `formatDateInput insere as barras ao digitar`() {
        assertEquals("31/10/2026", HealthFormatter.formatDateInput("31102026"))
    }

    @Test fun `formatDateInput respeita digitacao parcial`() {
        assertEquals("", HealthFormatter.formatDateInput(""))
        assertEquals("3", HealthFormatter.formatDateInput("3"))
        assertEquals("31", HealthFormatter.formatDateInput("31"))
        assertEquals("31/1", HealthFormatter.formatDateInput("311"))
        assertEquals("31/10", HealthFormatter.formatDateInput("3110"))
        assertEquals("31/10/2", HealthFormatter.formatDateInput("31102"))
    }

    @Test fun `formatDateInput nao duplica barras nem passa de oito digitos`() {
        assertEquals("31/10/2026", HealthFormatter.formatDateInput("31/10/2026"))
        assertEquals("31/10/2026", HealthFormatter.formatDateInput("311020269"))
        assertEquals("31/10/2026", HealthFormatter.formatDateInput("31/10/2026/"))
    }

    @Test fun `formatTimeInput insere dois pontos ao digitar`() {
        assertEquals("08:00", HealthFormatter.formatTimeInput("0800"))
        assertEquals("08:00", HealthFormatter.formatTimeInput("08:00"))
        assertEquals("08:0", HealthFormatter.formatTimeInput("080"))
    }

    @Test fun `formatTimeInput respeita digitacao parcial`() {
        assertEquals("", HealthFormatter.formatTimeInput(""))
        assertEquals("8", HealthFormatter.formatTimeInput("8"))
        assertEquals("08", HealthFormatter.formatTimeInput("08"))
        assertEquals("08:00", HealthFormatter.formatTimeInput("08000"))
    }

    @Test fun `formatTimeListInput separa horarios com virgula e espaco`() {
        assertEquals("08:00, 20:00", HealthFormatter.formatTimeListInput("0800,2000"))
        assertEquals("08:00, 20:00", HealthFormatter.formatTimeListInput("0800, 2000"))
        assertEquals("08:00, 20:00", HealthFormatter.formatTimeListInput("08:00, 20:00"))
        assertEquals("08:00, 12:30, 20:00", HealthFormatter.formatTimeListInput("0800,1230,2000"))
    }

    @Test fun `formatTimeListInput preserva virgula final durante a digitacao`() {
        assertEquals("08:00,", HealthFormatter.formatTimeListInput("0800,"))
        assertEquals("08:00,", HealthFormatter.formatTimeListInput("08:00, "))
        assertEquals("08:00, 20", HealthFormatter.formatTimeListInput("08:00,20"))
    }

    @Test fun `formatTimeListInput descarta trechos vazios e nao lanca em entrada parcial`() {
        assertEquals("", HealthFormatter.formatTimeListInput(""))
        assertEquals("", HealthFormatter.formatTimeListInput(","))
        assertEquals("", HealthFormatter.formatTimeListInput(" , "))
        assertEquals("08:00, 20:00", HealthFormatter.formatTimeListInput("0800, ,2000"))
        assertEquals("08,", HealthFormatter.formatTimeListInput("08,"))
    }
}
