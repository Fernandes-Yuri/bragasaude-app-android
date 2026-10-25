package br.com.bragasaude.data.avatar

import org.junit.Assert.*
import org.junit.Test

class AvatarPhotoRulesTest {
    @Test fun regularPortraitAndHarmlessPrintedTextAreAllowed() {
        assertNull(AvatarPhotoRules.rejection(512, 512, AvatarVisualFindings(1, "Rio 2026")))
        assertNull(AvatarPhotoRules.rejection(96, 96, AvatarVisualFindings(1, "")))
        assertNull(AvatarPhotoRules.rejection(512, 512, AvatarVisualFindings(2, "")))
    }
    @Test fun faceInDocumentDoesNotMakeItAProfilePhoto() {
        assertNotNull(AvatarPhotoRules.rejection(512, 512, AvatarVisualFindings(1, "CPF 123.456.789-09")))
        assertNotNull(AvatarPhotoRules.rejection(512, 512, AvatarVisualFindings(1, "República\nFederativa do Brasil\nRegistro Geral")))
    }
    @Test fun arbitraryNumbersAndCpfWordAloneAreNotDocumentEvidence() {
        assertNull(AvatarPhotoRules.rejection(512, 512, AvatarVisualFindings(1, "CPF")))
        assertNull(AvatarPhotoRules.rejection(512, 512, AvatarVisualFindings(1, "CPF 11111111111")))
    }
    @Test fun missingFaceAndVerySmallImageHaveActionableReasons() {
        assertTrue(AvatarPhotoRules.rejection(512, 512, AvatarVisualFindings(0, ""))!!.contains("rosto"))
        assertTrue(AvatarPhotoRules.rejection(32, 32, AvatarVisualFindings(1, ""))!!.contains("resolução"))
    }
}
