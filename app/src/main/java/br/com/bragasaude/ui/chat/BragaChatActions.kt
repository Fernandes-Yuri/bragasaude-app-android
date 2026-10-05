package br.com.bragasaude.ui.chat

import br.com.bragasaude.domain.VoiceHealthIntent
import br.com.bragasaude.data.remote.ai.OrbReply
import org.json.JSONArray
import org.json.JSONObject

/** Cards locais: dados só são persistidos pelo executor após a revisão do usuário. */
internal object BragaChatActions {
    fun reply(intent: VoiceHealthIntent): OrbReply? {
        val params = JSONObject()
        val action = when (intent) {
            is VoiceHealthIntent.BloodPressure -> {
                params.put("sistolica", intent.systolic).put("diastolica", intent.diastolic)
                "REGISTRAR_PRESSAO"
            }
            is VoiceHealthIntent.Glucose -> { params.put("glicemia", intent.glucoseMgDl); "REGISTRAR_GLICEMIA" }
            is VoiceHealthIntent.Hydration -> { params.put("quantidade_ml", intent.amountMl); "REGISTRAR_AGUA" }
            is VoiceHealthIntent.HeartRate -> { params.put("batimentos", intent.heartRateBpm); "REGISTRAR_BATIMENTOS" }
            is VoiceHealthIntent.OxygenSaturation -> { params.put("saturacao", intent.oxygenPercent); "REGISTRAR_OXIGENACAO" }
            is VoiceHealthIntent.Grocery -> { params.put("items", JSONArray(intent.items)); "ABRIR_LISTA_COMPRAS" }
            is VoiceHealthIntent.QueryPatientStatus -> {
                if (intent.targetName != null) return OrbReply(JSONObject()
                    .put("fala", "Selecione seu familiar na área Família para consultar os registros da pessoa certa.")
                    .put("acao", "ABRIR_FAMILIA").put("parametros", params).toString())
                if (intent.metric == "status geral") return null
                "CONSULTAR_METRICAS"
            }
            else -> return null
        }
        return OrbReply(JSONObject().put("fala", "Confira os dados na tela de ${actionLabel(action)} antes de salvar.")
            .put("acao", action).put("parametros", params).toString(), "Card preparado no dispositivo; 0 tokens de nuvem")
    }
}
