package br.com.bragasaude.testing

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.util.Base64

class TestAccountGrantPolicyTest {
    private val now = 1_800_000_000L
    private val email = "testeb2b@teste.com"

    private fun grant(account: String = email, expires: Long = now + 3_000): JSONObject {
        val claims = JSONObject().put("uid", "test-uid").put("iat", now).put("exp", expires)
            .put("claims", JSONObject().put("braga_test_login", true).put("braga_test_account", account))
        val payload = Base64.getUrlEncoder().withoutPadding().encodeToString(claims.toString().toByteArray())
        return JSONObject().put("version", 1).put("project_id", "braga-saude")
            .put("account_email", account).put("uid", "test-uid").put("expires_at", expires)
            .put("custom_token", "header.$payload.signature")
    }

    @Test fun loginOnlyActivatesInTestLabOnDebugBuildWithAnExplicitAccount() {
        for (account in TestAccountGrantPolicy.accounts) {
            assertTrue(TestAccountGrantPolicy.enabled(true, account, true))
            assertFalse(TestAccountGrantPolicy.enabled(false, account, true))
            assertFalse(TestAccountGrantPolicy.enabled(true, account, false))
        }
        assertFalse(TestAccountGrantPolicy.enabled(true, "", true))
        assertFalse(TestAccountGrantPolicy.enabled(true, "unexpected@example.test", true))
    }

    @Test fun acceptsExistingPatientAndCompanyTargetsWithoutInventingProfileFields() {
        for (account in TestAccountGrantPolicy.accounts) {
            val parsed = TestAccountGrantPolicy.parse(grant(account).toString(), account, now)
            assertEquals("test-uid", parsed.uid)
            assertFalse(parsed.toString().contains(parsed.token))
        }
    }

    @Test fun rejectsExpiredTokensAndValidityLongerThanOneHour() {
        for (expiry in listOf(now - 1, now, now + 3_601)) {
            assertThrows(IllegalArgumentException::class.java) {
                TestAccountGrantPolicy.parse(grant(expires = expiry).toString(), email, now)
            }
        }
    }

    @Test fun rejectsAnotherAccountProjectUidOrExpiryBeforeContactingFirebase() {
        for ((field, value) in listOf("account_email" to "testebraga1@proton.me",
            "project_id" to "another-project", "uid" to "another-uid", "expires_at" to now + 2_000)) {
            assertThrows(IllegalArgumentException::class.java) {
                TestAccountGrantPolicy.parse(grant().put(field, value).toString(), email, now)
            }
        }
    }

    @Test fun rejectsTamperedPurposeAndAccountInsideToken() {
        for (purpose in listOf(JSONObject().put("braga_test_login", false).put("braga_test_account", email),
            JSONObject().put("braga_test_login", true).put("braga_test_account", "testebraga1@proton.me"))) {
            val claims = JSONObject().put("uid", "test-uid").put("iat", now).put("exp", now + 3_000).put("claims", purpose)
            val token = "header." + Base64.getUrlEncoder().withoutPadding().encodeToString(claims.toString().toByteArray()) + ".signature"
            assertThrows(IllegalArgumentException::class.java) {
                TestAccountGrantPolicy.parse(grant().put("custom_token", token).toString(), email, now)
            }
        }
    }

    @Test fun rejectsOversizedOrMalformedCredentials() {
        assertThrows(Exception::class.java) { TestAccountGrantPolicy.parse("x".repeat(16_385), email, now) }
        assertThrows(Exception::class.java) { TestAccountGrantPolicy.parse("{}", email, now) }
        assertThrows(Exception::class.java) {
            TestAccountGrantPolicy.parse(grant().put("custom_token", "not-a-token").toString(), email, now)
        }
    }

    @Test fun validatesIdentityFromEmailOrCustomTokenClaim() {
        assertTrue(TestAccountGrantPolicy.matchesIdentity("u1", "u1", email, null, email))
        assertTrue(TestAccountGrantPolicy.matchesIdentity("u1", "u1", null, email, email))
        assertFalse(TestAccountGrantPolicy.matchesIdentity("u2", "u1", email, email, email))
        assertFalse(TestAccountGrantPolicy.matchesIdentity("u1", "u1", "testebraga1@proton.me", email, email))
        assertFalse(TestAccountGrantPolicy.matchesIdentity("u1", "u1", null, "testebraga1@proton.me", email))
        assertFalse(TestAccountGrantPolicy.matchesIdentity("u1", "u1", null, null, email))
    }
}
