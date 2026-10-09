package br.com.bragasaude.data.local.organizer
import org.junit.Assert.*
import org.junit.Test
class OrganizerMetadataTest {
    @Test fun rejectsImpossibleDatesAndDoesNotUseBirthDate() {
        assertFalse(OrganizerMetadata.validDate("31/02/2026"))
        assertTrue(OrganizerMetadata.validDate("29/02/2024"))
        assertEquals("", OrganizerMetadata.suggestDate("Nascimento: 01/01/1980 Emissão: 01/10/2026"))
        assertEquals("02/10/2026", OrganizerMetadata.suggestDate("Nascimento: 01/01/1980 Coleta: 02/10/2026"))
    }
    @Test fun keepsUndatedDocumentsLastInBothDirectionsAndAllPages() {
        val a = OrganizerDocument("a", "A", "01/10/2026", pages = 3)
        val b = OrganizerDocument("b", "B", "02/10/2026", pages = 2)
        val c = OrganizerDocument("c", "C", pages = 4)
        assertEquals(listOf(a,b,c), OrganizerMetadata.ordered(listOf(c,b,a), false))
        assertEquals(listOf(b,a,c), OrganizerMetadata.ordered(listOf(c,b,a), true))
        assertEquals(9, OrganizerMetadata.ordered(listOf(c,b,a), true).sumOf { it.pages })
    }
    @Test fun deniesExpiredSessionsIncludingClockGoingBackward() {
        val session = OrganizerSession("id", 1000)
        assertFalse(session.expired(1001))
        assertTrue(session.expired(session.expiresAt))
        assertTrue(session.expired(999))
    }
}
