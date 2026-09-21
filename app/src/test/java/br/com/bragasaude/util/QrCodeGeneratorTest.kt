package br.com.bragasaude.util

import org.junit.Assert.*
import org.junit.Test

/**
 * Suíte de testes do utilitário de QR Code e formatação de expiração.
 *
 * Apenas `formatExpiration` é testável em JVM puro; `generateQrCode` depende de
 * `android.graphics.Bitmap` e é coberto pelos testes instrumentados.
 *
 * O `now` e fixo em todos os testes: antes eles somavam a um
 * System.currentTimeMillis() lido no teste enquanto a implementacao lia de novo
 * — uma race que fazia o CI falhar (flaky) perto da virada do dia.
 */
class QrCodeGeneratorTest {

    private val now = 1_700_000_000_000L  // instante fixo e arbitrario

    // ------------------------------------------------------------------
    // [1] EXPIRADO — timestamp no passado
    // ------------------------------------------------------------------
    @Test
    fun `formatExpiration returns Expirado when timestamp is in the past`() {
        val past = now - 1000L
        assertEquals("Expirado", QrCodeGenerator.formatExpiration(past, now))
    }

    // ------------------------------------------------------------------
    // [2] DIAS — plural e singular
    // ------------------------------------------------------------------
    @Test
    fun `formatExpiration returns days in plural when more than one day left`() {
        val threeDays = now + (3 * 24 * 60 * 60 * 1000L)
        assertEquals("Expira em 3 dias", QrCodeGenerator.formatExpiration(threeDays, now))
    }

    @Test
    fun `formatExpiration returns day in singular when exactly one day left`() {
        val oneDay = now + (24 * 60 * 60 * 1000L)
        assertEquals("Expira em 1 dia", QrCodeGenerator.formatExpiration(oneDay, now))
    }

    // ------------------------------------------------------------------
    // [3] HORAS — plural e singular
    // ------------------------------------------------------------------
    @Test
    fun `formatExpiration returns hours when less than a day left`() {
        val fiveHours = now + (5 * 60 * 60 * 1000L)
        assertEquals("Expira em 5 horas", QrCodeGenerator.formatExpiration(fiveHours, now))
    }

    @Test
    fun `formatExpiration returns hour in singular when exactly one hour left`() {
        val oneHour = now + (60 * 60 * 1000L)
        assertEquals("Expira em 1 hora", QrCodeGenerator.formatExpiration(oneHour, now))
    }

    // ------------------------------------------------------------------
    // [4] MENOS DE 1 HORA
    // ------------------------------------------------------------------
    @Test
    fun `formatExpiration returns fallback when less than one hour left`() {
        val thirtyMinutes = now + (30 * 60 * 1000L)
        assertEquals("Expira em menos de 1 hora", QrCodeGenerator.formatExpiration(thirtyMinutes, now))
    }
}
