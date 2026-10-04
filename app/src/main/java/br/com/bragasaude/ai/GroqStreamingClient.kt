package br.com.bragasaude.ai

import br.com.bragasaude.BuildConfig
import br.com.bragasaude.data.remote.ai.OrbRejectedException
import br.com.bragasaude.data.remote.ai.OrbWebSocket
import br.com.bragasaude.data.remote.auth.AuthService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.io.IOException
import javax.inject.Inject

fun interface GroqStreamSource {
    fun stream(systemPrompt: String, speech: String): Flow<String>
}

/** Usa o WebSocket OkHttp existente. Credencial Groq e escolha do modelo ficam no gateway. */
class GroqStreamingClient @Inject constructor(
    private val socket: OrbWebSocket,
    private val auth: AuthService
) : GroqStreamSource {
    override fun stream(systemPrompt: String, speech: String): Flow<String> = callbackFlow {
        val owner = auth.currentUserId
        if (owner == null) {
            close(IOException("Entre na sua conta para continuar"))
            return@callbackFlow
        }
        val session = socket.openSession(this, BuildConfig.BASE_URL, maxFailures = 2) { force ->
            if (auth.currentUserId != owner) throw OrbRejectedException("Sessão alterada")
            auth.getFreshToken(force) ?: throw OrbRejectedException("Sessão indisponível")
        }
        val task = launch {
            try {
                var preview = ""
                fun deliver(text: String) {
                    if (auth.currentUserId != owner) throw CancellationException("Sessão alterada")
                    if (text.length > 6000 || !text.startsWith(preview))
                        throw IOException("Resposta inconsistente")
                    val delta = text.removePrefix(preview)
                    if (delta.isNotEmpty() && trySend(delta).isFailure)
                        throw IOException("Fluxo de resposta indisponível")
                    preview = text
                }
                val reply = withTimeout(45_000) {
                    // O gateway descarta roles system por segurança. O resumo segue como
                    // contexto de conversa; o system prompt soberano continua no servidor.
                    session.chat(listOf("assistant" to systemPrompt, "user" to speech),
                        onPartial = ::deliver)
                }
                val finalSpeech = JSONObject(reply.content).getString("fala")
                if (finalSpeech.isBlank()) throw IOException("Resposta vazia")
                deliver(finalSpeech)
                close()
            } catch (_: TimeoutCancellationException) {
                close(IOException("Tempo de resposta esgotado"))
            } catch (e: CancellationException) {
                close(e)
                throw e
            } catch (_: Exception) {
                close(IOException("Não foi possível concluir a resposta"))
            } finally {
                session.close()
            }
        }
        awaitClose { task.cancel(); session.close() }
    }
}
