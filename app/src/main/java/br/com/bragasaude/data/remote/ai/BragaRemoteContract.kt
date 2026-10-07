package br.com.bragasaude.data.remote.ai

import br.com.bragasaude.ai.BragaNluEngine
import br.com.bragasaude.ai.BragaRoutingPolicy
import br.com.bragasaude.ai.BragaTurnMeaning
import org.json.JSONArray
import org.json.JSONObject

/** A nuvem responde; o pedido atual e o domínio local autorizam ações. */
internal object BragaRemoteContract {
    fun context(text: String): JSONObject {
        val local = BragaNluEngine.analisar(text)
        val mentions = JSONArray()
        BragaTurnMeaning.beverages(text).forEach { mention ->
            mentions.put(JSONObject().put("beverage", mention.entity ?: JSONObject.NULL)
                .put("quantity_ml", mention.quantityMl ?: JSONObject.NULL)
                .put("negated", mention.negated).put("modality", mention.modality))
        }
        return JSONObject().put("version", 1).put("original_text", text)
            .put("intent", local.intent).put("emergency", local.isEmergencia)
            .put("modality", if (local.isEmergencia) "current_report" else if (local.intent == "duvida_clinica_complexa") "educational" else "unresolved")
            .put("conversation_only", true).put("beverages", mentions)
    }

    fun validate(text: String, content: String): String {
        val envelope = try { JSONObject(content) } catch (_: Exception) {
            JSONObject().put("fala", content).put("acao", "CONVERSA").put("parametros", JSONObject())
        }
        val action = envelope.optString("acao", envelope.optString("tipo", "CONVERSA"))
        // Aplica a mesma regra a todas as delegações e aos dois transportes.
        // Emergência real já tem atendimento local prioritário; preserva a orientação caso chegue aqui.
        val localReply = BragaTurnMeaning.localReply(text)
        val executionClaim = Regex("""(?:^|[.!?,])\s*(?:ja\s+)?(?:eu\s+)?(?:abri|salvei|registrei|anotei|avisei|enviei|agendei|deixei as opcoes)\b""")
            .containsMatchIn(BragaRoutingPolicy.normalize(envelope.optString("fala")))
        if (localReply != null || action != "CONVERSA" || executionClaim) {
            val emergency = BragaRoutingPolicy.emergency(BragaRoutingPolicy.normalize(text)).intent != null
            val fala = if (emergency) "Acione o SAMU 192 ou procure atendimento imediato."
                else localReply ?: "Posso explicar sua dúvida sem abrir opções ou preparar registros. Qual detalhe deseja entender?"
            return JSONObject().put("fala", fala).put("acao", "CONVERSA")
                .put("parametros", JSONObject()).toString()
        }
        // Parâmetros de registro nunca acompanham uma resposta apenas conversacional.
        return envelope.put("acao", "CONVERSA").put("parametros", JSONObject()).toString()
    }
}
