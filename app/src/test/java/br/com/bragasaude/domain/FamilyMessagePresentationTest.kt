package br.com.bragasaude.domain

import br.com.bragasaude.data.local.FamilyMessageEntity
import org.junit.Assert.*
import org.junit.Test

class FamilyMessagePresentationTest {
    private fun message(text: String, unavailable: Boolean) = FamilyMessageEntity(
        id = "fixture", patientUserId = "patient", senderName = "Cuidador", iconType = "LOVE", messageText = text, awaitingDecryption = unavailable)

    @Test fun unreadableContentNeverLooksLikeAnEmptyQuoteOrAReadReceipt() {
        val unavailable = message("", true)
        assertTrue(FamilyMessagePresentation.preview(unavailable).contains("indisponível"))
        assertFalse(FamilyMessagePresentation.status(0, listOf(unavailable)).contains("Todas lidas"))
        assertTrue(FamilyMessagePresentation.status(2, listOf(unavailable)).contains("2 não lidas"))
        assertEquals("“Olá”", FamilyMessagePresentation.preview(message("Olá", false)))
        assertTrue(unavailable.awaitingDecryption)
        assertFalse(unavailable.isRead)
    }
}
