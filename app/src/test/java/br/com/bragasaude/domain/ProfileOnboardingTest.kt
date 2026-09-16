package br.com.bragasaude.domain

import org.junit.Assert.*
import org.junit.Test

class ProfileOnboardingTest {
    @Test fun `adding caregiving preserves completed self care`() {
        assertEquals("HYBRID", ProfileOnboarding.modeAfterAddingCaregiving(true, "VIEWER_ONLY"))
        assertTrue(ProfileOnboarding.isComplete("CAREGIVER", ProfileOnboarding.modeAfterAddingCaregiving(true, "VIEWER_ONLY"), true, true))
    }

    @Test fun `new caregiver keeps selected mode until personal setup is complete`() {
        assertEquals("VIEWER_ONLY", ProfileOnboarding.modeAfterAddingCaregiving(false, "VIEWER_ONLY"))
        assertEquals("HYBRID", ProfileOnboarding.modeAfterAddingCaregiving(false, "HYBRID"))
    }
    @Test fun `companion can finish without personal biometrics`() {
        assertTrue(ProfileOnboarding.isComplete("CAREGIVER", "VIEWER_ONLY", true, false))
        assertFalse(ProfileOnboarding.needsSelfCare("CAREGIVER", "VIEWER_ONLY", true, false))
    }

    @Test fun `hybrid must complete personal setup after family registration`() {
        assertFalse(ProfileOnboarding.isComplete("CAREGIVER", "HYBRID", true, false))
        assertTrue(ProfileOnboarding.needsSelfCare("CAREGIVER", "HYBRID", true, false))
        assertTrue(ProfileOnboarding.isComplete("CAREGIVER", "HYBRID", true, true))
    }

    @Test fun `incomplete and unknown roles never enter main app`() {
        for (role in listOf(null, "PATIENT", "CAREGIVER", "ADMIN")) {
            assertFalse(ProfileOnboarding.isComplete(role, null, false, false))
        }
        assertFalse(ProfileOnboarding.isComplete("CAREGIVER", null, true, true))
        assertFalse(ProfileOnboarding.isComplete("ADMIN", null, true, true))
        assertFalse(ProfileOnboarding.isComplete("PATIENT", null, true, false))
        assertTrue(ProfileOnboarding.isComplete("PATIENT", null, true, true))
    }
}
