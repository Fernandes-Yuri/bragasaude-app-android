package br.com.bragasaude.data.remote.family

import br.com.bragasaude.data.local.FamilyMessageEntity
import org.junit.Assert.*
import org.junit.Test

class FamilyChatWebSocketClientTest {

    @Test
    fun handleIncomingJson_parsesFamilyMessageEvent() {
        val client = FamilyChatWebSocketClient("https://api.bragasaude.online")
        var receivedMsg: FamilyMessageEntity? = null
        val listener = object : FamilyWsListener {
            override fun onMessageReceived(msg: FamilyMessageEntity) {
                receivedMsg = msg
            }
            override fun onMessageDeleted(messageId: String) {}
        }

        client.startSession("patient-123", { "dummy-token" }, listener)

        val jsonEvent = """
            {
                "type": "FAMILY_MESSAGE",
                "data": {
                    "id": "msg-abc-123",
                    "patient_user_id": "patient-123",
                    "sender_user_id": "caregiver-456",
                    "sender_name": "Dra. Maria",
                    "message_text": "Lembrete: tomar a medicação às 18h",
                    "icon_type": "MED",
                    "created_at": 1730740000,
                    "expires_at": 1730826400,
                    "deleted_at": null,
                    "encryption_version": 1
                }
            }
        """.trimIndent()

        client.handleIncomingJson(jsonEvent)

        assertNotNull(receivedMsg)
        assertEquals("msg-abc-123", receivedMsg?.id)
        assertEquals("patient-123", receivedMsg?.patientUserId)
        assertEquals("caregiver-456", receivedMsg?.senderUserId)
        assertEquals("Dra. Maria", receivedMsg?.senderName)
        assertEquals("Lembrete: tomar a medicação às 18h", receivedMsg?.messageText)
        assertEquals("MED", receivedMsg?.iconType)
        assertEquals(1730740000000L, receivedMsg?.sentAt)
        assertEquals(1730826400000L, receivedMsg?.expiresAt)
        assertNull(receivedMsg?.deletedAt)
    }

    @Test
    fun handleIncomingJson_parsesE2EEEncryptedBody() {
        val client = FamilyChatWebSocketClient("https://api.bragasaude.online")
        var receivedMsg: FamilyMessageEntity? = null
        val listener = object : FamilyWsListener {
            override fun onMessageReceived(msg: FamilyMessageEntity) {
                receivedMsg = msg
            }
            override fun onMessageDeleted(messageId: String) {}
        }

        client.startSession("patient-123", { "dummy-token" }, listener)

        val jsonEvent = """
            {
                "type": "FAMILY_MESSAGE",
                "data": {
                    "id": "msg-e2ee-1",
                    "patientId": "patient-123",
                    "senderId": "caregiver-456",
                    "senderName": "Filho",
                    "messageText": "conteudo legado",
                    "bodyEncrypted": "{\"v\":1,\"iv\":\"dGVzdA==\",\"ciphertext\":\"Y2lwaGVydGV4dA==\"}",
                    "iconType": "LOVE",
                    "sentAt": "2026-11-04T18:00:00Z"
                }
            }
        """.trimIndent()

        client.handleIncomingJson(jsonEvent)

        assertNotNull(receivedMsg)
        assertEquals("msg-e2ee-1", receivedMsg?.id)
        assertEquals("{\"v\":1,\"iv\":\"dGVzdA==\",\"ciphertext\":\"Y2lwaGVydGV4dA==\"}", receivedMsg?.messageText)
    }

    @Test
    fun handleIncomingJson_parsesMessageDeletedEvent() {
        val client = FamilyChatWebSocketClient("https://api.bragasaude.online")
        var deletedId: String? = null
        val listener = object : FamilyWsListener {
            override fun onMessageReceived(msg: FamilyMessageEntity) {}
            override fun onMessageDeleted(messageId: String) {
                deletedId = messageId
            }
        }

        client.startSession("patient-123", { "dummy-token" }, listener)

        val jsonEvent = """
            {
                "type": "MESSAGE_DELETED",
                "data": {
                    "message_id": "msg-to-delete-999"
                }
            }
        """.trimIndent()

        client.handleIncomingJson(jsonEvent)

        assertEquals("msg-to-delete-999", deletedId)
    }

    @Test
    fun handleIncomingJson_parsesMessageReadEvent() {
        val client = FamilyChatWebSocketClient("https://api.bragasaude.online")
        var readPatientId: String? = null
        val listener = object : FamilyWsListener {
            override fun onMessageReceived(msg: FamilyMessageEntity) {}
            override fun onMessageDeleted(messageId: String) {}
            override fun onMessagesRead(patientId: String) {
                readPatientId = patientId
            }
        }

        client.startSession("patient-123", { "dummy-token" }, listener)

        val jsonEvent = """
            {
                "type": "MESSAGE_READ",
                "data": {
                    "patient_id": "patient-123"
                }
            }
        """.trimIndent()

        client.handleIncomingJson(jsonEvent)

        assertEquals("patient-123", readPatientId)
    }

    @Test
    fun handleIncomingJson_filtersOutMessagesForOtherPatients() {
        val client = FamilyChatWebSocketClient("https://api.bragasaude.online")
        var received = false
        val listener = object : FamilyWsListener {
            override fun onMessageReceived(msg: FamilyMessageEntity) {
                received = true
            }
            override fun onMessageDeleted(messageId: String) {}
        }

        client.startSession("patient-A", { "token" }, listener)

        val jsonEvent = """
            {
                "type": "FAMILY_MESSAGE",
                "data": {
                    "id": "msg-other",
                    "patient_user_id": "patient-B",
                    "message_text": "Mensagem para outro paciente"
                }
            }
        """.trimIndent()

        client.handleIncomingJson(jsonEvent)

        assertFalse(received)
    }

    @Test
    fun buildWsUrl_includesBothTokenAndPatientIdWithEncoding() {
        val client = FamilyChatWebSocketClient("https://api.bragasaude.online")
        val url = client.buildWsUrl("https://api.bragasaude.online", "test_token_123", "patient_abc_456")

        assertEquals("wss://api.bragasaude.online/ws/family?token=test_token_123&patientId=patient_abc_456", url)
    }

    @Test
    fun buildWsUrl_handlesHttpSchemeAndTrailingSlashes() {
        val client = FamilyChatWebSocketClient("http://10.0.2.2:8081/")
        val url = client.buildWsUrl("http://10.0.2.2:8081/", "tok", "pat-1")

        assertEquals("ws://10.0.2.2:8081/ws/family?token=tok&patientId=pat-1", url)
    }

    @Test
    fun buildWsUrl_encodesSpecialCharactersInParameters() {
        val client = FamilyChatWebSocketClient("https://api.bragasaude.online")
        val url = client.buildWsUrl("https://api.bragasaude.online", "token with spaces", "patient+special@id")

        assertTrue(url.contains("token=token+with+spaces"))
        assertTrue(url.contains("patientId=patient%2Bspecial%40id"))
    }
}
