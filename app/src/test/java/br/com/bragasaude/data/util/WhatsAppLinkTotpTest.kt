package br.com.bragasaude.data.util

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.SecureRandom

class WhatsAppLinkTotpTest {

    private val base32Alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567".toSet()

    @Test
    fun test_generateSecret_returns_32_characters_valid_base32() {
        repeat(50) {
            val secret = WhatsAppLinkTotp.generateSecret()
            assertEquals("Secret must be exactly 32 characters", 32, secret.length)
            assertTrue("Secret must contain only valid Base32 characters: $secret", secret.all { it in base32Alphabet })
        }
    }

    @Test
    fun test_base32_encode_decode_roundtrip() {
        val random = SecureRandom()

        // 1. Array de 20 bytes aleatorios
        repeat(20) {
            val originalBytes = ByteArray(20)
            random.nextBytes(originalBytes)
            val encoded = WhatsAppLinkTotp.base32Encode(originalBytes)
            assertEquals("Base32 encoding of 20 bytes must produce 32 chars", 32, encoded.length)
            assertTrue("Encoded characters must be valid Base32", encoded.all { it in base32Alphabet })

            val decoded = WhatsAppLinkTotp.base32Decode(encoded)
            assertArrayEquals("Decoded bytes must match original bytes", originalBytes, decoded)
        }

        // 2. Bytes limites: todos zeros e todos 0xFF
        val allZeros = ByteArray(20)
        assertArrayEquals(allZeros, WhatsAppLinkTotp.base32Decode(WhatsAppLinkTotp.base32Encode(allZeros)))

        val allOnes = ByteArray(20) { 0xFF.toByte() }
        assertArrayEquals(allOnes, WhatsAppLinkTotp.base32Decode(WhatsAppLinkTotp.base32Encode(allOnes)))
    }

    @Test
    fun test_currentCode_returns_6_digits() {
        val secret = WhatsAppLinkTotp.generateSecret()
        val code = WhatsAppLinkTotp.currentCode(secret)
        assertEquals("Code must be 6 digits", 6, code.length)
        assertTrue("Code must contain only digits: $code", code.all { it.isDigit() })

        // Testa janelas de tempo diferentes
        val codeAtZero = WhatsAppLinkTotp.currentCode(secret, atMillis = 0L)
        assertEquals(6, codeAtZero.length)
        assertTrue(codeAtZero.all { it.isDigit() })

        val codeAt180s = WhatsAppLinkTotp.currentCode(secret, atMillis = 180_000L)
        assertEquals(6, codeAt180s.length)
        assertTrue(codeAt180s.all { it.isDigit() })

        // Validacao da contagem regressiva da janela
        val seconds = WhatsAppLinkTotp.secondsUntilRotation(atMillis = 1000L)
        assertTrue("secondsUntilRotation must be within 1..180", seconds in 1L..180L)
    }
}
