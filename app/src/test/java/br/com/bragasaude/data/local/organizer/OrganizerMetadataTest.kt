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
    @Test fun groupsBySectorFirstThenDatesNewestWithUndatedLast() {
        val lab1 = OrganizerDocument("1", "Hemograma", "10/10/2026", "Laboratorial", pages = 1)
        val lab2 = OrganizerDocument("2", "Glicemia", "15/10/2026", "Laboratorial", pages = 1)
        val lab0 = OrganizerDocument("3", "Ureia", "", "Laboratorial", pages = 1)
        val img = OrganizerDocument("4", "US abdome", "01/09/2026", "Imagem", pages = 1)
        val sectors = OrganizerMetadata.sections(listOf(img, lab0, lab1, lab2), newest = true)
        assertEquals(listOf("Laboratorial", "Imagem"), sectors.map { it.type })
        val laboratorial = sectors.first()
        assertEquals(listOf("15/10/2026", "10/10/2026", ""), laboratorial.groups.map { it.date })
        assertEquals(listOf("2", "1", "3"), laboratorial.groups.flatMap { it.documents }.map { it.id })
        assertEquals(listOf("4"), sectors[1].groups.single().documents.map { it.id })
        val oldestFirst = OrganizerMetadata.sections(listOf(lab0, lab1, lab2), newest = false)
        assertEquals(listOf("10/10/2026", "15/10/2026", ""), oldestFirst.single().groups.map { it.date })
    }
    @Test fun flatteningSectionsKeepsUndatedLastInBothDirections() {
        val a = OrganizerDocument("a", "A", "01/10/2026", pages = 3)
        val b = OrganizerDocument("b", "B", "02/10/2026", pages = 2)
        val c = OrganizerDocument("c", "C", pages = 4)
        val flat = OrganizerMetadata.sections(listOf(c, b, a), true).flatMap { it.groups }.flatMap { it.documents }
        assertEquals(listOf(b, a, c), flat)
        val flatAsc = OrganizerMetadata.sections(listOf(c, b, a), false).flatMap { it.groups }.flatMap { it.documents }
        assertEquals(listOf(a, b, c), flatAsc)
    }
    @Test fun doesNotConfuseUltrasoundEchogenicityWithEchocardiogram() {
        assertFalse(OrganizerMetadata.extractTopics("Nódulo hipoecoico no lobo direito, parenquima hiperecoico").contains("Ecocardiograma"))
        assertTrue(OrganizerMetadata.extractTopics("ECO com Doppler normal").contains("Ecocardiograma"))
        assertTrue(OrganizerMetadata.extractTopics("Ecocardiografia com Doppler").contains("Ecocardiograma"))
        val (title, type, _) = OrganizerMetadata.suggestWithTopics("Ecocardiografia com doppler", "x")
        assertEquals("Ecocardiograma", title)
        assertEquals("Cardiológico", type)
    }
    @Test fun keepsStrictExamDateLabelsOnly() {
        assertEquals("12/05/2026", OrganizerMetadata.suggestDate("Data do exame: 12/05/2026"))
        assertEquals("03/02/2025", OrganizerMetadata.suggestDate("data de coleta - 03/02/2025"))
        assertEquals("", OrganizerMetadata.suggestDate("Data: 12/05/2026"))
        assertEquals("", OrganizerMetadata.suggestDate("Data da realização: 12/05/2026"))
        assertEquals("", OrganizerMetadata.suggestDate("Paciente 01/01/1980 exame sem rotulo"))
    }
}
