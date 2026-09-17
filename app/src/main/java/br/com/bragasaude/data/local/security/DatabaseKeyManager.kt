package br.com.bragasaude.data.local.security

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DatabaseKeyManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val PREFS_FILE_NAME = "braga_secure_db_prefs"
        private const val KEY_DB_PASSPHRASE = "db_passphrase_bytes"
        private const val KEY_SIZE_BYTES = 32 // 256 bits
    }

    /**
     * Retorna a frase-chave do SQLCipher. Se ainda não existir, gera e persiste de forma criptografada.
     */
    fun getOrCreatePassphrase(): ByteArray {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        val securePrefs = EncryptedSharedPreferences.create(
            context,
            PREFS_FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )

        val existingHex = securePrefs.getString(KEY_DB_PASSPHRASE, null)
        if (existingHex != null) {
            return hexToBytes(existingHex)
        }

        // Gera nova chave criptográfica segura de 32 bytes
        val random = SecureRandom()
        val newKey = ByteArray(KEY_SIZE_BYTES)
        random.nextBytes(newKey)

        val newHex = bytesToHex(newKey)
        securePrefs.edit().putString(KEY_DB_PASSPHRASE, newHex).apply()
        return newKey
    }

    private fun bytesToHex(bytes: ByteArray): String {
        val sb = StringBuilder()
        for (b in bytes) {
            sb.append(String.format("%02x", b))
        }
        return sb.toString()
    }

    private fun hexToBytes(hex: String): ByteArray {
        val len = hex.length
        val data = ByteArray(len / 2)
        var i = 0
        while (i < len) {
            data[i / 2] = ((Character.digit(hex[i], 16) shl 4) +
                    Character.digit(hex[i + 1], 16)).toByte()
            i += 2
        }
        return data
    }
}
