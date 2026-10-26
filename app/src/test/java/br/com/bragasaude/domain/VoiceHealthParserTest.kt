package br.com.bragasaude.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class VoiceHealthParserTest {

    private lateinit var parser: VoiceHealthParser
    @Before
    fun setup() {
        parser = VoiceHealthParser()
    }

    @Test
    fun `detect blood pressure shorthand 12 por 8`() {
        val result = parser.parse("pressão 12 por 8")
        assertTrue(result is VoiceHealthIntent.BloodPressure)
        val bp = result as VoiceHealthIntent.BloodPressure
        assertEquals(120, bp.systolic)
        assertEquals(80, bp.diastolic)
    }

    @Test
    fun `detect blood pressure with half modifier 13 por 8 e meio`() {
        val result = parser.parse("13 por 8 e meio")
        assertTrue(result is VoiceHealthIntent.BloodPressure)
        val bp = result as VoiceHealthIntent.BloodPressure
        assertEquals(130, bp.systolic)
        assertEquals(85, bp.diastolic)
    }

    @Test
    fun `detect weight 72 quilos e meio`() {
        val result = parser.parse("meu peso deu 72 quilos e meio")
        assertTrue(result is VoiceHealthIntent.Weight)
        val weight = result as VoiceHealthIntent.Weight
        assertEquals(72.5, weight.weightKg, 0.01)
    }

    @Test
    fun `detect glucose 105`() {
        val result = parser.parse("minha glicemia deu 105")
        assertTrue(result is VoiceHealthIntent.Glucose)
        val glucose = result as VoiceHealthIntent.Glucose
        assertEquals(105, glucose.glucoseMgDl)
    }

    @Test
    fun `detect hydration um copo de agua`() {
        val result = parser.parse("tomei um copo de água")
        assertTrue(result is VoiceHealthIntent.Hydration)
        val hydration = result as VoiceHealthIntent.Hydration
        assertEquals(250, hydration.amountMl)
    }

    // D-VOZ1: os infinitivos "beber"/"tomar" são a forma natural de falar no uso real.
    // Antes deste fix a frase escapava do parser local, ia ao servidor e voltava como
    // CONVERSA (modo orientador) — o assistente entendia mas não acionava a tela.
    @Test
    fun `detect hydration infinitivo beber agua`() {
        val result = parser.parse("beber água")
        assertTrue(result is VoiceHealthIntent.Hydration)
        val hydration = result as VoiceHealthIntent.Hydration
        assertEquals(250, hydration.amountMl)
    }

    @Test
    fun `detect hydration infinitivo tomar copos`() {
        val result = parser.parse("tomar dois copos de água")
        assertTrue(result is VoiceHealthIntent.Hydration)
        val hydration = result as VoiceHealthIntent.Hydration
        assertEquals(500, hydration.amountMl)
    }

    @Test
    fun `beber outra bebida nao vira hidratacao`() {
        assertTrue(parser.parse("beber um copo de suco") is VoiceHealthIntent.Unknown)
        assertTrue(parser.parse("tomar um café agora") is VoiceHealthIntent.Unknown)
    }

    @Test
    fun `detect consumed meal continues to conversation`() {
        val result = parser.parse("comi filé de frango no almoço")
        assertTrue(result is VoiceHealthIntent.Unknown)
    }

    @Test
    fun `detect grocery list when purchase intent is present continues to conversation`() {
        val result = parser.parse("preciso comprar banana e leite desnatado no mercado")
        assertTrue(result is VoiceHealthIntent.Unknown)
    }

    @Test
    fun `detect food mentioned ambiguously continues to conversation`() {
        val result = parser.parse("filé de frango")
        assertTrue(result is VoiceHealthIntent.Unknown)
    }

    @Test
    fun `return unknown for unrelated speech`() {
        val result = parser.parse("o carro azul estacionou na garagem")
        assertTrue(result is VoiceHealthIntent.Unknown)
    }

    @Test
    fun `detect conversational reply for greeting`() {
        val result = parser.parse("bom dia como você está")
        assertTrue(result is VoiceHealthIntent.ConversationalReply)
        val reply = result as VoiceHealthIntent.ConversationalReply
        assertTrue(reply.message.contains("Como", ignoreCase = true))
    }

    @Test
    fun `detect conversational reply for identity question`() {
        val result = parser.parse("quem é você")
        assertTrue(result is VoiceHealthIntent.ConversationalReply)
        val reply = result as VoiceHealthIntent.ConversationalReply
        assertTrue(reply.message.contains("assistente virtual", ignoreCase = true))
    }

    @Test
    fun `detect conversational reply for thanks`() {
        val result = parser.parse("muito obrigado")
        assertTrue(result is VoiceHealthIntent.ConversationalReply)
        val reply = result as VoiceHealthIntent.ConversationalReply
        assertTrue(reply.message == "De nada!")
    }

    @Test
    fun `detect conversational reply for good night`() {
        val result = parser.parse("boa noite")
        assertTrue(result is VoiceHealthIntent.ConversationalReply)
        val reply = result as VoiceHealthIntent.ConversationalReply
        assertTrue(reply.message.contains("Boa noite", ignoreCase = true))
    }

    @Test
    fun `detect conversational reply for good morning`() {
        val result = parser.parse("bom dia")
        assertTrue(result is VoiceHealthIntent.ConversationalReply)
        val reply = result as VoiceHealthIntent.ConversationalReply
        assertTrue(reply.message.contains("Bom dia", ignoreCase = true))
    }

    @Test
    fun `detect conversational reply for time query`() {
        val result = parser.parse("que horas são")
        assertTrue(result is VoiceHealthIntent.ConversationalReply)
        val reply = result as VoiceHealthIntent.ConversationalReply
        assertTrue(reply.message.contains("hora", ignoreCase = true))
    }

    @Test
    fun `detect conversational reply for date query`() {
        val result = parser.parse("que dia é hoje")
        assertTrue(result is VoiceHealthIntent.ConversationalReply)
        val reply = result as VoiceHealthIntent.ConversationalReply
        assertTrue(reply.message.contains("Hoje é", ignoreCase = true))
    }

    @Test
    fun `detect query patient metrics for blood pressure`() {
        val result = parser.parse("como está minha pressão")
        assertTrue(result is VoiceHealthIntent.QueryPatientStatus)
        val query = result as VoiceHealthIntent.QueryPatientStatus
        assertEquals("pressão", query.metric)
    }

    @Test
    fun `detect direct grocery list request redirects user to nutrition screen`() {
        val result = parser.parse("abrir lista de compras")
        assertTrue(result is VoiceHealthIntent.ConversationalReply)
        val reply = result as VoiceHealthIntent.ConversationalReply
        assertTrue(reply.message.contains("descontinuado"))
    }

    @Test
    fun `exclusive caregiver cannot register personal blood pressure`() {
        val result = parser.parse(
            rawTranscript = "minha pressão deu 12 por 8",
            userRole = "CAREGIVER",
            caregiverMode = "VIEWER_ONLY"
        )
        assertTrue(result is VoiceHealthIntent.ConversationalReply)
        val reply = result as VoiceHealthIntent.ConversationalReply
        assertTrue(reply.message.contains("Acompanhante", ignoreCase = true))
    }

    @Test
    fun `exclusive caregiver cannot register personal glucose`() {
        val result = parser.parse(
            rawTranscript = "glicemia 110",
            userRole = "CAREGIVER",
            caregiverMode = "VIEWER_ONLY"
        )
        assertTrue(result is VoiceHealthIntent.ConversationalReply)
        val reply = result as VoiceHealthIntent.ConversationalReply
        assertTrue(reply.message.contains("Acompanhante", ignoreCase = true))
    }

    @Test
    fun `exclusive caregiver can send family medication reminder`() {
        val result = parser.parse(
            rawTranscript = "lembrar meu pai de tomar o remédio às 8 horas",
            userRole = "CAREGIVER",
            caregiverMode = "VIEWER_ONLY"
        )
        assertTrue(result is VoiceHealthIntent.FamilyMedicationReminder)
        val reminder = result as VoiceHealthIntent.FamilyMedicationReminder
        assertEquals("pai", reminder.targetName)
        assertEquals("08:00", reminder.timeHint)
    }

    @Test
    fun `exclusive caregiver can query family patient status`() {
        val result = parser.parse(
            rawTranscript = "como está a pressão do meu pai",
            userRole = "CAREGIVER",
            caregiverMode = "VIEWER_ONLY"
        )
        assertTrue(result is VoiceHealthIntent.QueryPatientStatus)
        val query = result as VoiceHealthIntent.QueryPatientStatus
        assertEquals("pressão", query.metric)
        assertEquals("pai", query.targetName)
    }

    @Test
    fun `exclusive caregiver receives friendly greeting for good night`() {
        val result = parser.parse(
            rawTranscript = "boa noite",
            userRole = "CAREGIVER",
            caregiverMode = "VIEWER_ONLY"
        )
        assertTrue(result is VoiceHealthIntent.ConversationalReply)
        val reply = result as VoiceHealthIntent.ConversationalReply
        assertTrue(reply.message.contains("Boa noite", ignoreCase = true))
    }

    @Test
    fun `hybrid caregiver can register personal blood pressure`() {
        val result = parser.parse(
            rawTranscript = "pressão 12 por 8",
            userRole = "CAREGIVER",
            caregiverMode = "HYBRID"
        )
        assertTrue(result is VoiceHealthIntent.BloodPressure)
        val bp = result as VoiceHealthIntent.BloodPressure
        assertEquals(120, bp.systolic)
        assertEquals(80, bp.diastolic)
    }

    @Test
    fun `detect adding food to daily meal goal redirects to nutrition screen`() {
        val result = parser.parse("adicione farelo de aveia na minha meta diária de alimentação")
        assertTrue(result is VoiceHealthIntent.ConversationalReply)
        val reply = result as VoiceHealthIntent.ConversationalReply
        assertTrue(reply.message.contains("descontinuado"))
    }

    @Test
    fun `detect adding food to shopping list with direct trigger redirects to nutrition screen`() {
        val result = parser.parse("adicione chia na lista de compras")
        assertTrue(result is VoiceHealthIntent.ConversationalReply)
        val reply = result as VoiceHealthIntent.ConversationalReply
        assertTrue(reply.message.contains("descontinuado"))
    }

    @Test
    fun `detect casual food conversation continues to conversation`() {
        val result = parser.parse("você gosta de maçã")
        assertTrue(result is VoiceHealthIntent.Unknown)
    }

    @Test
    fun `detect food suggestion request continues to conversation`() {
        val result = parser.parse("o que posso comer hoje")
        assertTrue(result is VoiceHealthIntent.Unknown)
    }

    @Test
    fun `detect food matching for accented food name maca continues to conversation`() {
        val result = parser.parse("comi maçã no café da manhã")
        assertTrue(result is VoiceHealthIntent.Unknown)
    }








}
