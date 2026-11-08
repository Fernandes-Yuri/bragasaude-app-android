package br.com.bragasaude.data.remote.service

import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.tasks.await
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.security.MessageDigest
import javax.net.ssl.SSLException

/**
 * Provedor injetável de token FCM (RTL-06).
 * Permite substituição determinística em testes sem depender do Google Play Services real.
 */
fun interface FcmTokenProvider {
    suspend fun fetchToken(): String
}

object FirebaseFcmTokenProvider : FcmTokenProvider {
    override suspend fun fetchToken(): String = FirebaseMessaging.getInstance().token.await()
}

enum class FcmFailureReason {
    PLAY_SERVICES_UNAVAILABLE,
    FIS_OR_INSTANCE_ID_RESTRICTED,
    NETWORK_TRANSIENT,
    GATEWAY_REJECTED,
    SESSION_CHANGED,
    UNKNOWN
}

data class FcmRegistrationDiagnostic(
    val userIdHash: String,
    val reason: FcmFailureReason,
    val exceptionClass: String?,
    val rootCauseClass: String?,
    val errorCode: String?,
    val gmsStatus: String,
    val isTestLab: Boolean,
    val externalEnvironmentFailure: Boolean,
    val attempt: Int,
    val nextRetryDelayMs: Long,
    val recordedAtMs: Long
) {
    /**
     * Resumo seguro para log operacional: nunca inclui token FCM, UID em claro ou dados clínicos.
     */
    fun toSafeLogSummary(): String =
        "reason=$reason external=$externalEnvironmentFailure code=${errorCode ?: "NONE"} " +
            "exc=${exceptionClass ?: "NONE"} root=${rootCauseClass ?: "NONE"} " +
            "gms=$gmsStatus testLab=$isTestLab attempt=$attempt backoffMs=$nextRetryDelayMs user=$userIdHash"
}

object FcmEnvironmentInspector {

    private val KNOWN_RESTRICTED_CODES = listOf(
        "SERVICE_NOT_AVAILABLE",
        "FIS_AUTH_ERROR",
        "AUTHENTICATION_FAILED",
        "MISSING_INSTANCEID_SERVICE",
        "TOO_MANY_REGISTRATIONS",
        "PHONE_REGISTRATION_ERROR",
        "INVALID_PARAMETERS",
        "INTERNAL_SERVER_ERROR"
    )

    fun inspectGmsStatus(context: Context?): String {
        if (context == null) return "UNCHECKED"
        return try {
            val availabilityClass = Class.forName("com.google.android.gms.common.GoogleApiAvailability")
            val instance = availabilityClass.getMethod("getInstance").invoke(null)
            val code = availabilityClass
                .getMethod("isGooglePlayServicesAvailable", Context::class.java)
                .invoke(instance, context) as? Int
            when (code) {
                0 -> "AVAILABLE"
                1 -> "SERVICE_MISSING"
                2 -> "SERVICE_VERSION_UPDATE_REQUIRED"
                3 -> "SERVICE_DISABLED"
                9 -> "SERVICE_INVALID"
                18 -> "SERVICE_UPDATING"
                else -> "CODE_${code ?: "NULL"}"
            }
        } catch (_: Throwable) {
            try {
                @Suppress("DEPRECATION")
                val info = context.packageManager.getApplicationInfo("com.google.android.gms", 0)
                if (info.enabled) "PACKAGE_ENABLED" else "PACKAGE_DISABLED"
            } catch (_: PackageManager.NameNotFoundException) {
                "PACKAGE_MISSING"
            } catch (_: Throwable) {
                "UNCHECKED"
            }
        }
    }

    fun isFirebaseTestLab(context: Context?): Boolean {
        if (context == null) return false
        return try {
            Settings.System.getString(context.contentResolver, "firebase.test.lab") == "true"
        } catch (_: Throwable) {
            false
        }
    }

    fun hashUserId(userId: String): String {
        if (userId.isBlank()) return "anon"
        return try {
            val digest = MessageDigest.getInstance("SHA-256").digest(userId.toByteArray(Charsets.UTF_8))
            digest.take(4).joinToString("") { "%02x".format(it) }
        } catch (_: Exception) {
            "uid"
        }
    }

    fun diagnose(
        userId: String,
        throwable: Throwable?,
        gatewayRejected: Boolean = false,
        sessionChanged: Boolean = false,
        attempt: Int,
        nextRetryDelayMs: Long,
        nowMs: Long,
        context: Context? = null
    ): FcmRegistrationDiagnostic {
        val gmsStatus = inspectGmsStatus(context)
        val testLab = isFirebaseTestLab(context)
        val chain = buildList {
            var current: Throwable? = throwable
            while (current != null && size < 8) {
                add(current)
                current = current.cause?.takeIf { it !== current }
            }
        }
        val root = chain.lastOrNull()
        val combinedMessages = chain.mapNotNull { it.message }.joinToString(" | ").uppercase()
        val matchedCode = KNOWN_RESTRICTED_CODES.firstOrNull { code -> combinedMessages.contains(code) }
        val hasInstallationsException = chain.any {
            it.javaClass.name.contains("FirebaseInstallationsException", ignoreCase = true) ||
                it.javaClass.name.contains("FirebaseMessaging", ignoreCase = true)
        }
        val isNetworkError = chain.any {
            it is SocketTimeoutException ||
                it is UnknownHostException ||
                it is ConnectException ||
                it is SSLException
        }
        val isGmsUnavailable = gmsStatus in setOf(
            "SERVICE_MISSING",
            "SERVICE_DISABLED",
            "SERVICE_INVALID",
            "PACKAGE_MISSING",
            "PACKAGE_DISABLED"
        )

        val reason = when {
            sessionChanged -> FcmFailureReason.SESSION_CHANGED
            gatewayRejected -> FcmFailureReason.GATEWAY_REJECTED
            isGmsUnavailable -> FcmFailureReason.PLAY_SERVICES_UNAVAILABLE
            matchedCode != null || hasInstallationsException -> FcmFailureReason.FIS_OR_INSTANCE_ID_RESTRICTED
            isNetworkError -> FcmFailureReason.NETWORK_TRANSIENT
            root is IOException -> FcmFailureReason.FIS_OR_INSTANCE_ID_RESTRICTED
            else -> FcmFailureReason.UNKNOWN
        }

        val externalEnv = reason == FcmFailureReason.PLAY_SERVICES_UNAVAILABLE ||
            reason == FcmFailureReason.FIS_OR_INSTANCE_ID_RESTRICTED

        val safeCode = when {
            matchedCode != null -> matchedCode
            gatewayRejected -> "GATEWAY_HTTP_REJECTED"
            isGmsUnavailable -> gmsStatus
            sessionChanged -> "SESSION_CHANGED"
            isNetworkError -> root?.javaClass?.simpleName ?: "NETWORK_ERROR"
            else -> null
        }

        return FcmRegistrationDiagnostic(
            userIdHash = hashUserId(userId),
            reason = reason,
            exceptionClass = throwable?.javaClass?.simpleName,
            rootCauseClass = root?.javaClass?.simpleName,
            errorCode = safeCode,
            gmsStatus = gmsStatus,
            isTestLab = testLab,
            externalEnvironmentFailure = externalEnv,
            attempt = attempt,
            nextRetryDelayMs = nextRetryDelayMs,
            recordedAtMs = nowMs
        )
    }
}
