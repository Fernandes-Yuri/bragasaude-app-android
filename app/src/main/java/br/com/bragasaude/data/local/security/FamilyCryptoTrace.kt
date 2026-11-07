package br.com.bragasaude.data.local.security

import android.util.Log
import br.com.bragasaude.BuildConfig
import java.security.MessageDigest
import java.util.Base64

/** Diagnóstico do APK interno: somente referências, fingerprints públicos e resultados. */
internal object FamilyCryptoTrace {
    private val messageIdPattern = Regex("[0-9a-fA-F-]{36}")
    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it) }

    fun reference(id: String?): String = id?.takeIf { it.isNotBlank() }
        ?.let { sha256(it.toByteArray(Charsets.UTF_8)).take(12) } ?: "none"

    fun publicKey(key: String?): String = if (key.isNullOrBlank()) "none" else
        runCatching { sha256(Base64.getDecoder().decode(key.trim())) }.getOrDefault("invalid")

    fun envelope(payload: String): String {
        val e = FamilyE2eeManager.E2eeEnvelope.fromJsonStringOrNull(payload) ?: return "legacy"
        return sha256("${e.v}|${e.iv}|${e.ciphertext}".toByteArray(Charsets.UTF_8))
    }

    fun message(id: String?): String = id?.takeIf { messageIdPattern.matches(it) } ?: "untracked"

    fun record(stage: String, fields: Map<String, String>) {
        if (!BuildConfig.DEBUG) return
        // Callers pass fingerprints/IDs only, never payloads, exception messages or secret keys.
        Log.d("FamilyCrypto", stage + " " + fields.entries.joinToString(" ") { (key, value) ->
            key + "=" + value.replace(Regex("[^A-Za-z0-9._:-]"), "_").take(160)
        })
    }
}
