package br.com.bragasaude.domain

import org.junit.Assert.*
import org.junit.Test

class FamilyConnectionCodeTest {
    @Test fun acceptsOnlyCompleteConnectionCodes() {
        assertEquals("A1B2C3D4", FamilyConnectionCode.parse(" a1b2c3d4 "))
        listOf(null, "", "1234567", "123456789", "ABCDEFGH-more", "https://example.com/A1B2C3D4", "AB CD EF", "ÁBCDEFGH").forEach { assertNull(FamilyConnectionCode.parse(it)) }
    }
}
