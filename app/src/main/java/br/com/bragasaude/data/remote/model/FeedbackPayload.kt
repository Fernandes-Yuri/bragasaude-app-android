package br.com.bragasaude.data.remote.model

import br.com.bragasaude.data.local.FeedbackEntity
import org.json.JSONObject
import java.util.UUID

/** Um único contrato para envio imediato e WorkManager, inclusive filas legadas. */
fun feedbackPayload(feedback: FeedbackEntity): JSONObject = JSONObject().apply {
    // Versões anteriores criavam "fb-<uuid>"; conserva a identidade na retentativa.
    put("id", UUID.fromString(feedback.id.removePrefix("fb-")).toString())
    put("userId", feedback.userId)
    put("userEmail", feedback.userEmail)
    put("userName", feedback.userName)
    put("category", feedback.category)
    put("title", feedback.title)
    put("message", feedback.message)
    put("inputMethod", feedback.inputMethod)
    put("appVersion", feedback.appVersion)
    put("deviceInfo", feedback.deviceInfo)
    put("screenshotBase64", feedback.screenshotBase64)
}
