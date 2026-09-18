package br.com.bragasaude.data.util

import android.util.Base64
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Vínculo de WhatsApp por TOTP — espelho do gateway (wa_link_totp.py).
 *
 * Arquitetura temporária, até a Meta liberar o template de autenticação.
 * A conta nasce com um segredo TOTP "sob o capo". No ato do clique no botão
 * de vincular, o app calcula o código ATUAL localmente (sem chamar o servidor)
 * e abre o WhatsApp com a mensagem pronta. O gateway recomputa o mesmo código
 * a partir do segredo que guardou e vincula o número (verificado pela Meta).
 *
 * Janela de 180s, sem tolerância: ou está dentro, ou o usuário volta no app
 * e pega um código novo.
 */
object WhatsAppLinkTotp {

    private const val SECRET_BYTES = 20
    private const val DIGITS = 6
    const val STEP_SECONDS = 180L

    /** Gera um segredo TOTP aleatório em base32 (o app manda isso na sincronização). */
    fun generateSecret(): String {
        val raw = ByteArray(SECRET_BYTES)
        SecureRandom().nextBytes(raw)
        return Base64.encodeToString(raw, Base64.NO_WRAP)
            .trimEnd('=')
            .replace('+', 'A') // mantém só o alfabeto base32 canônico
            .uppercase()
    }

    /** Código TOTP atual para o segredo (deve concordar com o gateway). */
    fun currentCode(secretBase32: String, atMillis: Long = System.currentTimeMillis()): String {
        val counter = (atMillis / 1000L) / STEP_SECONDS
        return hotp(secretBase32, counter)
    }

    /** Segundos restantes da janela atual (para a UI mostrar urgência). */
    fun secondsUntilRotation(atMillis: Long = System.currentTimeMillis()): Long {
        val nowSec = atMillis / 1000L
        return STEP_SECONDS - (nowSec % STEP_SECONDS)
    }

    private fun hotp(secretBase32: String, counter: Long): String {
        val key = base32Decode(secretBase32)
        val mac = Mac.getInstance("HmacSHA1")
        mac.init(SecretKeySpec(key, "HmacSHA1"))
        val counterBytes = ByteArray(8)
        var c = counter
        for (i in 7 downTo 0) {
            counterBytes[i] = (c and 0xFF).toByte()
            c = c shr 8
        }
        val digest = mac.doFinal(counterBytes)
        val offset = (digest[digest.size - 1].toInt() and 0x0F)
        var codeInt = ((digest[offset].toInt() and 0x7F) shl 24) or
                ((digest[offset + 1].toInt() and 0xFF) shl 16) or
                ((digest[offset + 2].toInt() and 0xFF) shl 8) or
                (digest[offset + 3].toInt() and 0xFF)
        codeInt = Math.floorMod(codeInt, 1_000_000)
        return codeInt.toString().padStart(DIGITS, '0')
    }

    private fun base32Decode(s: String): ByteArray {
        val cleaned = s.trim().uppercase().replace("=", "")
        val lookup = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
        val out = ArrayList<Byte>()
        var buffer = 0
        var bitsLeft = 0
        for (ch in cleaned) {
            val v = lookup.indexOf(ch)
            if (v < 0) continue
            buffer = (buffer shl 5) or v
            bitsLeft += 5
            if (bitsLeft >= 8) {
                bitsLeft -= 8
                out.add(((buffer shr bitsLeft) and 0xFF).toByte())
            }
        }
        return out.toByteArray()
    }
}
