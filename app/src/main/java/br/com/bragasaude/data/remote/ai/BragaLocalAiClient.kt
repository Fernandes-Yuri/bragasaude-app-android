package br.com.bragasaude.data.remote.ai

import android.util.Log
import br.com.bragasaude.BuildConfig
import br.com.bragasaude.data.local.slm.BragaOnDeviceEngine
import br.com.bragasaude.data.local.slm.BragaModelStore
import br.com.bragasaude.data.remote.auth.AuthService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

/** Conversa no Braga local; preferência de copo permanece no serviço da conta. */
@Singleton
class BragaLocalAiClient @Inject constructor(
    private val engine: BragaOnDeviceEngine,
    private val authService: AuthService,
    private val router: br.com.bragasaude.data.local.slm.BragaIntentRouter,
    private val resolver: br.com.bragasaude.data.local.slm.BragaContextResolver
) {
    companion object {
        val DEFAULT_SERVER_URL = BuildConfig.BASE_URL
        private const val TAG = "BragaLocalAiClient"
    }
    var serverBaseUrl: String = DEFAULT_SERVER_URL
    val isOnDeviceReady: Boolean get() = engine.ready

    suspend fun loadCupPreference(): Int? = withContext(Dispatchers.IO) {
        val userId = authService.currentUserId ?: return@withContext null
        var connection: HttpURLConnection? = null
        try {
            val token = authService.getFreshToken() ?: return@withContext null
            connection = (URL("$serverBaseUrl/api/assistant/preferences").openConnection() as HttpURLConnection).apply {
                connectTimeout = 2500
                readTimeout = 2500
                setRequestProperty("Authorization", "Bearer $token")
            }
            if (connection.responseCode != 200) return@withContext null
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            currentCoroutineContext().ensureActive()
            if (authService.currentUserId != userId) return@withContext null
            JSONObject(body).optJSONObject("preferences")?.optInt("cup_ml")?.takeIf { it in 50..2000 }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        } finally {
            connection?.disconnect()
        }
    }


    fun isHistoryQuery(text: String): Boolean = router.route(text).type == br.com.bragasaude.data.local.slm.BragaIntent.CONSULTA_HISTORICO

    fun isEmergency(text: String): Boolean = router.route(text).type == br.com.bragasaude.data.local.slm.BragaIntent.EMERGENCIA

    suspend fun interpretSpeech(userSpeech: String, preferWebSocket: Boolean = true,
                                history: List<Pair<String, String>> = emptyList(),
                                actingAs: String? = null, patientId: String? = null,
                                onPartial: (String) -> Unit = {}): BragaAiResult {
        check(engine.ready) { BragaModelStore.REQUIRED_MESSAGE }
        LocalConversationAnswers.answer(userSpeech, history)?.let {
            return BragaAiResult(tipo = "CONVERSA", fala = it)
        }
        val owner = authService.currentUserId
        val resolved = resolver.resolve(userSpeech, actingAs, patientId)
        val messages = mutableListOf<Pair<String, String>>()
        if (!resolved.factual) {
            var includeTurn = false
            history.forEach { (role, content) ->
                if (role == "user") includeTurn = router.route(content).type == br.com.bragasaude.data.local.slm.BragaIntent.CONVERSA_LIVRE
                if (includeTurn && role in listOf("user", "assistant")) {
                    val speech = if (role == "assistant") runCatching { JSONObject(content).optString("fala", content) }.getOrDefault(content) else content
                    messages.add(role to speech)
                }
            }
        }
        if (messages.lastOrNull() != ("user" to userSpeech)) messages.add("user" to userSpeech)
        val speech = if (isEmergency(userSpeech)) resolved.fallback else try {
            val generated = engine.reply(messages, resolved.instruction) { partial ->
                if (!resolved.factual && authService.currentUserId == owner) onPartial(br.com.bragasaude.data.local.slm.BragaResponseGuard.accept(partial, resolved))
            }
            br.com.bragasaude.data.local.slm.BragaResponseGuard.accept(generated, resolved)
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { resolved.fallback }
        check(authService.currentUserId == owner) { "A conta mudou. Abra novamente a conversa." }
        onPartial(speech)
        val intent = resolved.draft
        return BragaAiResult(tipo = when (resolved.action) {
            "REGISTRAR_PRESSAO" -> "PRESSAO"
            "REGISTRAR_GLICEMIA" -> "GLICEMIA"
            "REGISTRAR_AGUA" -> "AGUA"
            "REGISTRAR_MEDICAMENTO" -> "MEDICAMENTO"
            "EMERGENCIA" -> "EMERGENCIA"
            else -> "CONVERSA"
        }, fala = speech,
            sistolica = (intent as? br.com.bragasaude.domain.VoiceHealthIntent.BloodPressure)?.systolic,
            diastolica = (intent as? br.com.bragasaude.domain.VoiceHealthIntent.BloodPressure)?.diastolic,
            glicemia = (intent as? br.com.bragasaude.domain.VoiceHealthIntent.Glucose)?.glucoseMgDl,
            quantidadeMl = (intent as? br.com.bragasaude.domain.VoiceHealthIntent.Hydration)?.amountMl,
            action = resolved.action, parameters = resolved.parameters, localIntent = intent)

    }
}
