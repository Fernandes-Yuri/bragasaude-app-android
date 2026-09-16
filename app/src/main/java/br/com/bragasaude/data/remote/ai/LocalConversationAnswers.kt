package br.com.bragasaude.data.remote.ai

import br.com.bragasaude.domain.LocalCalendarAnswers
import br.com.bragasaude.domain.VoiceSessionCommand
import org.json.JSONObject

/** Resolve relógio e repetição também no chat escrito, sem rede e sem repetir ações. */
object LocalConversationAnswers {
    fun answer(raw: String, history: List<Pair<String, String>>): String? {
        LocalCalendarAnswers.answer(raw)?.let { return it }
        if (VoiceSessionCommand.parse(raw) != VoiceSessionCommand.REPEAT) return null
        val previous = history.lastOrNull { it.first == "assistant" }?.second
            ?: return "Ainda não tenho uma resposta nesta conversa para repetir."
        return try { JSONObject(previous).optString("fala", previous) } catch (_: Exception) { previous }
    }
}
