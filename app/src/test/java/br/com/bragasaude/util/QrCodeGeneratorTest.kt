package br.com.bragasaude.util

import org.junit.Assert.*
import org.junit.Test

/**
 * Suíte de testes do utilitário de QR Code e formatação de expiração.
 *
 * Apenas `formatExpiration` é testável em JVM puro; `generateQrCode` depende de
 * `android.graphics.Bitmap` e é coberto pelos testes instrumentados.
 */
class QrCodeGeneratorTest {

    // ------------------------------------------------------------------
    // [1] EXPIRADO — timestamp no passado
    // ------------------------------------------------------------------
    @Test
    fun `formatExpiration returns Expirado when timestamp is in the past`() {
        val past = System.currentTimeMillis() - 1000L
        assertEquals("Expirado", QrCodeGenerator.formatExpiration(past))
    }

    // ------------------------------------------------------------------
    // [2] DIAS — plural e singular
    // ------------------------------------------------------------------
    @Test
    fun `formatExpiration returns days in plural when more than one day left`() {
        val threeDays = System.currentTimeMillis() + (3 * 24 * 60 * 60 * 1000L)
        assertEquals("Expira em 3 dias", QrCodeGenerator.formatExpiration(threeDays))
    }

    @Test
    fun `formatExpiration returns day in singular when exactly one day left`() {
        val oneDay = System.currentTimeMillis() + (24 * 60 * 60 * 1000L) + (60 * 1000L)
        assertEquals("Expira em 1 dia", QrCodeGenerator.formatExpiration(oneDay))
    }

    // ------------------------------------------------------------------
    // [3] HORAS — plural e singular
    // ------------------------------------------------------------------
    @Test
    fun `formatExpiration returns hours when less than a day left`() {
        val fiveHours = System.currentTimeMillis() + (5 * 60 * 60 * 1000L)
        assertEquals("Expira em 5 horas", QrCodeGenerator.formatExpiration(fiveHours))
    }

    @Test
    fun `formatExpiration returns hour in singular when exactly one hour left`() {
        val oneHour = System.currentTimeMillis() + (60 * 60 * 1000L) + (30 * 1000L)
        assertEquals("Expira em 1 hora", QrCodeGenerator.formatExpiration(oneHour))
    }

    // ------------------------------------------------------------------
    // [4] MENOS DE 1 HORA
    // ------------------------------------------------------------------
    @Test
    fun `formatExpiration returns fallback when less than one hour left`() {
        val thirtyMinutes = System.currentTimeMillis() + (30 * 60 * 1000L)
        assertEquals("Expira em menos de 1 hora", QrCodeGenerator.formatExpiration(thirtyMinutes))
    }
}
