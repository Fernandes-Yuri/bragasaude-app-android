package br.com.bragasaude.data.remote.service

import android.content.Context
import android.util.Log
import br.com.bragasaude.data.remote.auth.AuthService
import br.com.bragasaude.data.remote.network.GatewayConnection
import br.com.bragasaude.data.remote.network.GatewayTlsPolicy
import br.com.bragasaude.util.BragaConstants
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Cliente HTTP para comunicação com o Hub de Notificações no Gateway.
 *
 * Envia registros de tokens FCM consolidados por sessão (RTL-06: single-flight,
 * deduplicação de emissões equivalentes, backoff limitado e diagnóstico estruturado),
 * dispara alertas para cuidadores e aciona o canal de emergência familiar.
 */
@Singleton
class NotificationClient(
    private val authService: AuthService,
    private val httpClient: OkHttpClient = GatewayTlsPolicy.defaultClient(),
    private val tokenProvider: FcmTokenProvider = FirebaseFcmTokenProvider,
    private val clock: () -> Long = { System.currentTimeMillis() }
) {
    @Inject
    constructor(
        authService: AuthService,
        httpClient: OkHttpClient
    ) : this(
        authService = authService,
        httpClient = httpClient,
        tokenProvider = FirebaseFcmTokenProvider,
        clock = { System.currentTimeMillis() }
    )

    companion object {
        private const val TAG = "NotificationClient"
        val DEFAULT_SERVER_URL: String = br.com.bragasaude.BuildConfig.BASE_URL
        private const val TIMEOUT_MS = 5000
        internal const val REGISTRATION_TTL_MS = 900_000L // 15 minutos
        internal const val BASE_BACKOFF_MS = 15_000L      // 15 segundos
        internal const val MAX_BACKOFF_MS = 300_000L      // 5 minutos

        fun computeBackoffMs(attempt: Int): Long {
            val shift = (attempt - 1).coerceIn(0, 5)
            return (BASE_BACKOFF_MS * (1L shl shift)).coerceAtMost(MAX_BACKOFF_MS)
        }
    }

    private val registrationLock = Mutex()
    private var registeredDevice: List<String>? = null
    private var registrationTime = 0L

    private val coordinatorLock = Mutex()
    private var sessionGeneration: Long = 0L
    private var activeSessionUserId: String? = null
    private var cachedFcmToken: String? = null
    private var inFlightUserId: String? = null
    private var inFlightGeneration: Long = 0L
    private var inFlightDeferred: CompletableDeferred<Boolean>? = null
    private var pendingTargetRole: String? = null
    private var pendingTargetName: String? = null
    private var failureCount: Int = 0
    private var nextAllowedAttemptAtMs: Long = 0L

    private val _lastDiagnostic = MutableStateFlow<FcmRegistrationDiagnostic?>(null)
    val lastDiagnostic: StateFlow<FcmRegistrationDiagnostic?> = _lastDiagnostic.asStateFlow()

    var serverBaseUrl: String = DEFAULT_SERVER_URL

    /**
     * Notifica mudança de sessão ou logout: invalida estado da conta anterior e
     * impede que qualquer tentativa em andamento publique token na conta nova (RTL-06).
     */
    suspend fun onSessionChanged(newUserId: String?) {
        val normalized = newUserId?.takeIf { it.isNotBlank() && it != "anonymous" && it != BragaConstants.GUEST_UID }
        coordinatorLock.withLock {
            if (activeSessionUserId != normalized) {
                sessionGeneration++
                activeSessionUserId = normalized
                registeredDevice = null
                registrationTime = 0L
                failureCount = 0
                nextAllowedAttemptAtMs = 0L
                pendingTargetRole = null
                pendingTargetName = null
                inFlightDeferred?.cancel(CancellationException("Sessão alterada durante registro FCM."))
                inFlightDeferred = null
                inFlightUserId = null
            }
        }
    }

    /**
     * Recebe um token FCM renovado (`onNewToken`), atualiza o cache de sessão e
     * registra para a conta autenticada atual (nunca registra para "anonymous").
     */
    suspend fun onNewToken(
        newToken: String,
        userId: String?,
        role: String = "USER",
        name: String = "Usuário Braga",
        context: Context? = null,
        currentUserIdProvider: () -> String? = { userId }
    ): Boolean {
        val cleanToken = newToken.trim()
        if (cleanToken.isBlank()) return false
        coordinatorLock.withLock {
            if (cachedFcmToken != cleanToken) {
                cachedFcmToken = cleanToken
                registeredDevice = null
                registrationTime = 0L
                failureCount = 0
                nextAllowedAttemptAtMs = 0L
            }
        }
        val validUserId = userId?.takeIf { it.isNotBlank() && it != "anonymous" && it != BragaConstants.GUEST_UID }
            ?: return false
        return ensureSessionTokenRegistered(
            userId = validUserId,
            role = role,
            name = name,
            force = true,
            context = context,
            currentUserIdProvider = currentUserIdProvider
        )
    }

    /**
     * Consolida obtenção e registro do token FCM por sessão (RTL-06):
     * - Single-flight: chamadas simultâneas para a mesma conta compartilham uma única busca de token.
     * - Deduplicação antes da busca: emissões equivalentes de perfil não chamam [FcmTokenProvider.fetchToken].
     * - Backoff limitado: falhas do ambiente/rede aplicam janela de contenção sem inundar o log.
     * - Isolamento de sessão: troca de conta descarta/cancela tentativa da conta anterior.
     */
    suspend fun ensureSessionTokenRegistered(
        userId: String,
        role: String = "USER",
        name: String = "Usuário Braga",
        force: Boolean = false,
        context: Context? = null,
        currentUserIdProvider: () -> String? = { userId }
    ): Boolean {
        if (userId.isBlank() || userId == "anonymous" || userId == BragaConstants.GUEST_UID) return false
        if (currentUserIdProvider() != userId) return false

        val normalizedRole = role.ifBlank { "USER" }
        val normalizedName = name.ifBlank { "Usuário Braga" }

        val existingFlight: CompletableDeferred<Boolean>?
        val myFlight: CompletableDeferred<Boolean>?
        val capturedGeneration: Long

        coordinatorLock.withLock {
            if (activeSessionUserId != userId) {
                sessionGeneration++
                activeSessionUserId = userId
                registeredDevice = null
                registrationTime = 0L
                failureCount = 0
                nextAllowedAttemptAtMs = 0L
                inFlightDeferred?.cancel(CancellationException("Sessão alterada para nova conta."))
                inFlightDeferred = null
                inFlightUserId = null
            }

            val now = clock()
            val currentToken = cachedFcmToken
            if (!force && currentToken != null) {
                val expectedIdentity = listOf(serverBaseUrl, userId, currentToken, normalizedRole, normalizedName)
                if (expectedIdentity == registeredDevice && now - registrationTime < REGISTRATION_TTL_MS) {
                    return true
                }
            }

            if (!force && failureCount > 0 && now < nextAllowedAttemptAtMs) {
                return false
            }

            val activeFlight = inFlightDeferred
            if (activeFlight != null && activeFlight.isActive && inFlightUserId == userId) {
                pendingTargetRole = normalizedRole
                pendingTargetName = normalizedName
                existingFlight = activeFlight
                myFlight = null
                capturedGeneration = inFlightGeneration
            } else {
                val deferred = CompletableDeferred<Boolean>()
                inFlightDeferred = deferred
                inFlightUserId = userId
                inFlightGeneration = sessionGeneration
                pendingTargetRole = normalizedRole
                pendingTargetName = normalizedName
                existingFlight = null
                myFlight = deferred
                capturedGeneration = sessionGeneration
            }
        }

        if (existingFlight != null) {
            return try {
                existingFlight.await()
            } catch (_: CancellationException) {
                false
            }
        }

        val flight = myFlight ?: return false
        return try {
            val result = executeConsolidatedRegistration(
                userId = userId,
                capturedGeneration = capturedGeneration,
                force = force,
                context = context,
                currentUserIdProvider = currentUserIdProvider
            )
            flight.complete(result)
            result
        } catch (e: CancellationException) {
            flight.cancel(e)
            throw e
        } catch (t: Throwable) {
            flight.complete(false)
            false
        } finally {
            coordinatorLock.withLock {
                if (inFlightDeferred === flight) {
                    inFlightDeferred = null
                    inFlightUserId = null
                }
            }
        }
    }

    private suspend fun executeConsolidatedRegistration(
        userId: String,
        capturedGeneration: Long,
        force: Boolean,
        context: Context?,
        currentUserIdProvider: () -> String?
    ): Boolean {
        suspend fun isSessionStillValid(): Boolean {
            if (currentUserIdProvider() != userId) return false
            return coordinatorLock.withLock {
                activeSessionUserId == userId && sessionGeneration == capturedGeneration
            }
        }

        if (!isSessionStillValid()) {
            recordSessionChangedDiagnostic(userId)
            return false
        }

        val token: String = try {
            val existingToken = coordinatorLock.withLock { cachedFcmToken }
            if (existingToken != null && !force) {
                existingToken
            } else {
                existingToken ?: tokenProvider.fetchToken().trim()
            }
        } catch (e: CancellationException) {
            if (!isSessionStillValid()) {
                recordSessionChangedDiagnostic(userId)
                return false
            }
            throw e
        } catch (t: Throwable) {
            if (!isSessionStillValid()) {
                recordSessionChangedDiagnostic(userId)
                return false
            }
            recordFailureDiagnostic(userId, throwable = t, gatewayRejected = false, context = context)
            return false
        }

        if (token.isBlank()) {
            recordFailureDiagnostic(
                userId,
                throwable = IllegalStateException("Token FCM vazio"),
                gatewayRejected = false,
                context = context
            )
            return false
        }

        if (!isSessionStillValid()) {
            recordSessionChangedDiagnostic(userId)
            return false
        }

        coordinatorLock.withLock {
            cachedFcmToken = token
        }

        var lastSuccess = false
        while (true) {
            val (targetRole, targetName) = coordinatorLock.withLock {
                val r = pendingTargetRole ?: "USER"
                val n = pendingTargetName ?: "Usuário Braga"
                r to n
            }

            if (!isSessionStillValid()) {
                recordSessionChangedDiagnostic(userId)
                return false
            }

            val ok = registerDevice(
                userId = userId,
                token = token,
                role = targetRole,
                name = targetName
            )

            if (!isSessionStillValid()) {
                recordSessionChangedDiagnostic(userId)
                return false
            }

            if (!ok) {
                recordFailureDiagnostic(userId, throwable = null, gatewayRejected = true, context = context)
                return false
            }

            lastSuccess = true
            val needsFollowUp = coordinatorLock.withLock {
                failureCount = 0
                nextAllowedAttemptAtMs = 0L
                _lastDiagnostic.value = null
                val latestRole = pendingTargetRole ?: targetRole
                val latestName = pendingTargetName ?: targetName
                latestRole != targetRole || latestName != targetName
            }
            if (!needsFollowUp) break
        }

        return lastSuccess
    }

    private suspend fun recordSessionChangedDiagnostic(userId: String) {
        val now = clock()
        val diag = FcmEnvironmentInspector.diagnose(
            userId = userId,
            throwable = null,
            gatewayRejected = false,
            sessionChanged = true,
            attempt = 0,
            nextRetryDelayMs = 0L,
            nowMs = now,
            context = null
        )
        coordinatorLock.withLock {
            _lastDiagnostic.value = diag
        }
    }

    private suspend fun recordFailureDiagnostic(
        userId: String,
        throwable: Throwable?,
        gatewayRejected: Boolean,
        context: Context?
    ) {
        val now = clock()
        val diag = coordinatorLock.withLock {
            failureCount += 1
            val backoff = computeBackoffMs(failureCount)
            nextAllowedAttemptAtMs = now + backoff
            FcmEnvironmentInspector.diagnose(
                userId = userId,
                throwable = throwable,
                gatewayRejected = gatewayRejected,
                sessionChanged = false,
                attempt = failureCount,
                nextRetryDelayMs = backoff,
                nowMs = now,
                context = context
            ).also { _lastDiagnostic.value = it }
        }
        Log.w(TAG, "Diagnóstico FCM [${diag.toSafeLogSummary()}]")
    }

    /**
     * Cadastra o dispositivo atual e seu token FCM no servidor Gateway.
     */
    suspend fun registerDevice(
        userId: String, token: String, role: String = "PATIENT", name: String = "Usuário Braga"
    ): Boolean = withContext(Dispatchers.IO) {
        registrationLock.withLock {
            val identity = listOf(serverBaseUrl, userId, token, role, name)
            val now = clock()
            if (identity == registeredDevice && now - registrationTime < REGISTRATION_TTL_MS) return@withLock true
            try {
                val payload = JSONObject().put("userId", userId).put("token", token).put("role", role)
                    .put("name", name).put("platform", "android")
                val success = sendJsonPost(URL("$serverBaseUrl/api/device/register"), payload)
                if (success) {
                    registeredDevice = identity
                    registrationTime = now
                    coordinatorLock.withLock {
                        cachedFcmToken = token
                    }
                }
                success
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Falha ao registrar dispositivo no Hub: ${e.javaClass.simpleName}")
                false
            }
        }
    }

    /**
     * Desvincula o token da sessão atual no logout usando o token em cache quando disponível
     * (evita nova chamada bloqueante ao Play Services quando o ambiente já está indisponível).
     */
    suspend fun unregisterCurrentDevice(userId: String): Boolean {
        val token = coordinatorLock.withLock { cachedFcmToken }
            ?: runCatching { tokenProvider.fetchToken().trim() }.getOrNull()
        onSessionChanged(null)
        if (token.isNullOrBlank()) return false
        return unregisterDeviceToken(userId, token)
    }

    /**
     * Desvincula o token FCM do dispositivo desta conta no servidor de notificações
     * (issue #11: pushes de uma conta não devem chegar após logout em aparelho compartilhado).
     */
    suspend fun unregisterDeviceToken(
        userId: String,
        token: String
    ): Boolean = withContext(Dispatchers.IO) {
        registrationLock.withLock {
            registeredDevice = null
            registrationTime = 0L
            try {
                val endpoint = URL("$serverBaseUrl/api/device/unregister")
                val payload = JSONObject().apply {
                    put("userId", userId)
                    put("token", token)
                    put("platform", "android")
                }
                return@withContext sendJsonPost(endpoint, payload)
            } catch (e: Exception) {
                Log.w(TAG, "Falha ao desvincular dispositivo no Hub: ${e.javaClass.simpleName}")
                return@withContext false
            }
        }
    }

    /**
     * Dispara um alerta clínico ou mensagem para cuidadores/pacientes.
     */
    suspend fun sendAlert(
        type: String,
        title: String,
        message: String,
        sourceUserId: String,
        targetRole: String = "CAREGIVER",
        metricValue: String? = null
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val endpoint = URL("$serverBaseUrl/api/notify/alert")
            val payload = JSONObject().apply {
                put("type", type)
                put("title", title)
                put("message", message)
                put("sourceUserId", sourceUserId)
                put("targetRole", targetRole)
                if (metricValue != null) {
                    put("metricValue", metricValue)
                }
            }
            return@withContext sendJsonPost(endpoint, payload)
        } catch (e: Exception) {
            Log.w(TAG, "Falha ao enviar alerta clínico para o Hub: ${e.message}")
            return@withContext false
        }
    }

    /**
     * Dispara o canal de emergência de altíssima prioridade para a rede de cuidado familiar.
     */
    suspend fun triggerEmergency(
        userId: String,
        userName: String,
        details: String
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val endpoint = URL("$serverBaseUrl/api/emergency/trigger")
            val payload = JSONObject().apply {
                put("userId", userId)
                put("userName", userName)
                put("details", details)
            }
            return@withContext sendJsonPost(endpoint, payload)
        } catch (e: Exception) {
            Log.e(TAG, "Falha ao disparar emergência no Hub: ${e.message}")
            return@withContext false
        }
    }

    private fun sendJsonPost(url: URL, json: JSONObject): Boolean {
        var conn: HttpURLConnection? = null
        return try {
            conn = GatewayConnection(url, httpClient).apply {
                requestMethod = "POST"
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Accept", "application/json")
                val token = authService.getTokenBlocking()
                    ?: throw java.io.IOException("Sessão expirada.")
                setRequestProperty("Authorization", "Bearer $token")
            }
            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { writer ->
                writer.write(json.toString())
                writer.flush()
            }
            conn.responseCode in 200..299
        } catch (e: Exception) {
            Log.d(TAG, "Erro na comunicação HTTP com o Hub (${url.path}): ${e.message}")
            false
        } finally {
            try { conn?.disconnect() } catch (_: Exception) {}
        }
    }
}
