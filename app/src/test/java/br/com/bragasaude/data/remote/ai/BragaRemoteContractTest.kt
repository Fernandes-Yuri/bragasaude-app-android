package br.com.bragasaude.data.remote.ai

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class BragaRemoteContractTest {
    @Test fun everyRemoteActionAndItsAnnouncementAreRemoved() {
        for (action in listOf("EMERGENCIA", "EXAMES", "LEMBRETES", "REGISTRAR_AGUA", "REGISTRAR_PRESSAO",
                "CONSULTAR_METRICAS", "ABRIR_LISTA_COMPRAS", "AGENDAR_RASCUNHO", "INVENTADA")) {
            val content = JSONObject().put("fala", "Já abri as opções e registrei os dados.")
                .put("acao", action).put("parametros", JSONObject().put("quantidade_ml", 500)).toString()
            val safe = JSONObject(BragaRemoteContract.validate("o que significa dor no peito?", content))
            assertEquals("CONVERSA", safe.getString("acao"))
            assertEquals(0, safe.getJSONObject("parametros").length())
            assertFalse(safe.getString("fala").contains("Já abri"))
        }
    }

    @Test fun conversationAndCurrentEmergencyGuidanceSurvive() {
        val content = """{"fala":"Uma explicação educativa.","acao":"CONVERSA","parametros":{}}"""
        assertEquals("Uma explicação educativa.", JSONObject(BragaRemoteContract.validate("o que significa dor no peito?", content)).getString("fala"))
        val emergency = """{"fala":"Já abri socorro","acao":"EMERGENCIA","parametros":{}}"""
        val safe = JSONObject(BragaRemoteContract.validate("estou com dor no peito", emergency))
        assertTrue(safe.getString("fala").contains("SAMU 192"))
        assertFalse(safe.getString("fala").contains("Já abri"))
    }

    @Test fun metadataIncludesOriginalTextNegationEntityAndQuantity() {
        val raw = "não bebi 500 ml de suco"
        val context = BragaRemoteContract.context(raw)
        assertEquals(raw, context.getString("original_text"))
        assertTrue(context.getBoolean("conversation_only"))
        val mention = context.getJSONArray("beverages").getJSONObject(0)
        assertTrue(mention.getBoolean("negated"))
        assertEquals("suco", mention.getString("beverage"))
        assertEquals(500, mention.getInt("quantity_ml"))
        assertEquals("denial", mention.getString("modality"))
    }
    @Test fun conversationCannotAnnounceUnexecutedActions() {
        for (fala in listOf("Já abri as opções de socorro.", "Salvei 500 ml de água.", "Entendi, já avisei seus contatos.")) {
            val content = JSONObject().put("fala", fala).put("acao", "CONVERSA").put("parametros", JSONObject()).toString()
            val safe = JSONObject(BragaRemoteContract.validate("o que significa dor no peito?", content))
            assertNotEquals(fala, safe.getString("fala"))
            assertEquals("CONVERSA", safe.getString("acao"))
        }
    }
}
