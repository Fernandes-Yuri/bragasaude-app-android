package br.com.bragasaude.testing

import android.content.Context
import br.com.bragasaude.BuildConfig
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.util.Base64

/** Credencial efêmera recebida no início do teste, nunca embutida no APK. */
internal class TestAccountGrant(val uid: String, val token: String) {
    override fun toString() = "TestAccountGrant(redacted)"
}

internal object TestAccountGrantPolicy {
    val accounts = setOf("testebraga1@proton.me", "testebraga2@proton.me", "testeb2b@teste.com")

    fun enabled(debug: Boolean, account: String, testLab: Boolean) = debug && testLab && account in accounts

    fun matchesIdentity(
        uid: String?,
        expectedUid: String,
        email: String?,
        claimAccount: String?,
        expectedAccount: String
    ): Boolean {
        if (uid != expectedUid || expectedAccount !in accounts) return false
        if (email != null) return email.equals(expectedAccount, ignoreCase = true)
        return claimAccount != null && claimAccount.equals(expectedAccount, ignoreCase = true)
    }

    fun parse(json: String, expectedAccount: String, nowSeconds: Long): TestAccountGrant {
        require(expectedAccount in accounts && json.toByteArray().size <= 16_384)
        val value = JSONObject(json)
        require(value.getInt("version") == 1 && value.getString("project_id") == "braga-saude")
        require(value.getString("account_email") == expectedAccount)
        val uid = value.getString("uid")
        require(uid.isNotBlank() && uid.length <= 128)
        val expires = value.getLong("expires_at")
        require(expires > nowSeconds && expires <= nowSeconds + 3_600)
        val token = value.getString("custom_token")
        val segments = token.split('.')
        require(segments.size == 3)
        val claims = JSONObject(String(Base64.getUrlDecoder().decode(segments[1]), Charsets.UTF_8))
        require(claims.getString("uid") == uid && claims.getLong("exp") == expires)
        require(claims.getLong("iat") <= nowSeconds + 60 && claims.getLong("iat") >= nowSeconds - 3_600)
        val purpose = claims.getJSONObject("claims")
        require(purpose.getBoolean("braga_test_login") && purpose.getString("braga_test_account") == expectedAccount)
        // A assinatura e a identidade são verificadas pelo Firebase, não por este parser.
        return TestAccountGrant(uid, token)
    }
}

object TestAccountBootstrap {
    const val FILE_NAME = "braga-test-login.json"

    private suspend fun verifyUserMatches(user: FirebaseUser?, expectedUid: String?, expectedAccount: String): Boolean {
        if (user == null) return false
        if (expectedUid != null && user.uid != expectedUid) return false
        if (user.email == null) {
            runCatching { user.reload().await() }
        }
        val claimAccount = runCatching {
            user.getIdToken(false).await().claims["braga_test_account"] as? String
        }.getOrNull()
        return TestAccountGrantPolicy.matchesIdentity(
            uid = user.uid,
            expectedUid = expectedUid ?: user.uid,
            email = user.email,
            claimAccount = claimAccount,
            expectedAccount = expectedAccount
        )
    }

    suspend fun signIn(context: Context, auth: FirebaseAuth): Boolean {
        if (!BuildConfig.DEBUG || BuildConfig.TEST_AUTO_LOGIN_ACCOUNT.isEmpty()) return false
        val expected = BuildConfig.TEST_AUTO_LOGIN_ACCOUNT
        val isTestLab = runCatching {
            android.provider.Settings.System.getString(context.contentResolver, "firebase.test.lab") == "true"
        }.getOrDefault(false)
        if (!TestAccountGrantPolicy.enabled(BuildConfig.DEBUG, expected, isTestLab)) return false
        val existing = auth.currentUser
        if (existing != null) {
            if (!verifyUserMatches(existing, expectedUid = null, expectedAccount = expected)) {
                auth.signOut()
                error("Identidade de teste divergente")
            }
            return true
        }
        val grant = withContext(Dispatchers.IO) {
            val candidates = listOfNotNull(
                File(context.filesDir, FILE_NAME),
                context.getExternalFilesDir(null)?.let { File(it, FILE_NAME) },
                File("/sdcard/Android/data/${context.packageName}/files", FILE_NAME)
            ).distinctBy { it.absolutePath }
            val file = candidates.firstOrNull { it.isFile } ?: return@withContext null
            try {
                require(file.length() in 1L..16_384L)
                TestAccountGrantPolicy.parse(file.readText(), expected, System.currentTimeMillis() / 1_000)
            } finally {
                // O Firebase mantém a sessão. O arquivo de preparação não fica no aparelho.
                runCatching { file.delete() }
            }
        } ?: return false
        val user = auth.signInWithCustomToken(grant.token).await().user
        if (!verifyUserMatches(user, expectedUid = grant.uid, expectedAccount = expected)) {
            auth.signOut()
            error("Identidade de teste divergente")
        }
        return true
    }
}
