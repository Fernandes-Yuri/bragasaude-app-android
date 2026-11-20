package br.com.bragasaude.domain

import br.com.bragasaude.data.local.ProfileEntity
import br.com.bragasaude.data.remote.model.ProfileLookup
import br.com.bragasaude.data.remote.model.RemoteProfile
import org.junit.Test
import org.junit.Assert.*
import java.util.Date

class LoginProfileResolutionTest {
    private val incomplete = ProfileEntity(userId = "owner", userRole = "PATIENT", consentAcceptedAt = Date(), basicProfileComplete = false, selfCareComplete = false)
    @Test fun completedCacheWorksOffline() {
        val complete = incomplete.copy(basicProfileComplete = true, selfCareComplete = true)
        assertEquals(LoginProfileResolution.Ready(complete), resolveLoginProfile(complete, ProfileLookup.Unavailable()))
    }
    @Test fun staleAndMissingCacheDoNotBecomeNewRegistrationOnNetworkFailure() {
        for (local in listOf(null, incomplete, incomplete.copy(userRole = "CAREGIVER", caregiverMode = "VIEWER_ONLY"))) {
            assertTrue(resolveLoginProfile(local, ProfileLookup.Unavailable()) is LoginProfileResolution.Unavailable)
        }
    }
    @Test fun consentAndRoleNeverBypassBasicRegistration() {
        val caregiver = incomplete.copy(userRole = "CAREGIVER", caregiverMode = "VIEWER_ONLY")
        val resolved = resolveLoginProfile(caregiver, ProfileLookup.Found(RemoteProfile(id = "owner"))) as LoginProfileResolution.Ready
        assertFalse(ProfileOnboarding.isComplete(resolved.profile?.userRole, resolved.profile?.caregiverMode, resolved.profile?.basicProfileComplete == true, false))
    }
    @Test fun confirmedMissingProfileStartsOnboarding() {
        assertEquals(LoginProfileResolution.Ready(null), resolveLoginProfile(null, ProfileLookup.NotFound))
    }
    @Test fun transientNullIsLoadingInsteadOfIncomplete() {
        assertEquals(LoginProfileResolution.Loading, resolveLoginProfile(null, null))
    }

    @Test fun businessOnlyProfileResolvesReadyWithoutPersonalBiometrics() {
        val businessOnly = ProfileEntity(
            userId = "biz-owner-1",
            consentAcceptedAt = Date(),
            basicProfileComplete = false,
            selfCareComplete = false,
            accountMode = "BUSINESS_ONLY"
        )
        assertEquals(
            LoginProfileResolution.Ready(businessOnly),
            resolveLoginProfile(businessOnly, ProfileLookup.Unavailable())
        )
    }

    @Test fun dualProfileWithoutPersonalBiometricsStillRequiresPersonalCompletion() {
        val dualIncomplete = ProfileEntity(
            userId = "dual-owner-1",
            consentAcceptedAt = Date(),
            basicProfileComplete = false,
            selfCareComplete = false,
            accountMode = "DUAL"
        )
        assertTrue(resolveLoginProfile(dualIncomplete, ProfileLookup.Unavailable()) is LoginProfileResolution.Unavailable)
    }
}
