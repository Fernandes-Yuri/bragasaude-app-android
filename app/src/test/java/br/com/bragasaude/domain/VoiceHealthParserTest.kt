package br.com.bragasaude.domain

import br.com.bragasaude.data.local.FoodEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class VoiceHealthParserTest {

    private lateinit var parser: VoiceHealthParser
    private val testCatalog = listOf(
        FoodEntity(
            remoteId = "1",
            name = "Filé de Frango Grelhado",
            category = "Proteínas",
            kcal = 165.0,
            isDiabetesSafe = true,
            isHypertensionSafe = true
        ),
        FoodEntity(
            remoteId = "2",
            name = "Arroz Integral Cozido",
            category = "Cereais & Grãos",
            kcal = 124.0,
            isDiabetesSafe = true,
            isHypertensionSafe = true
        ),
        FoodEntity(
            remoteId = "3",
            name = "Feijão Preto Cozido",
            category = "Leguminosas",
            kcal = 77.0,
            isDiabetesSafe = true,
            isHypertensionSafe = true
        ),
        FoodEntity(
            remoteId = "4",
            name = "Banana Prata",
            category = "Frutas",
            kcal = 98.0,
            isDiabetesSafe = true,
            isHypertensionSafe = true
        ),
        FoodEntity(
            remoteId = "5",
            name = "Leite Desnatado",
            category = "Laticínios",
            kcal = 35.0,
            isDiabetesSafe = true,
            isHypertensionSafe = true
        ),
        FoodEntity(
            remoteId = "6",
            name = "Farelo de Aveia",
            category = "Cereais & Fibras",
            kcal = 246.0,
            isDiabetesSafe = true,
            isHypertensionSafe = true,
            functionalTags = listOf("fibra_soluvel", "baixo_ig", "beta_glucana"),
            healthBenefits = "Rico em beta-glucana que modula a glicose.",
            servingUnit = "1 colher de sopa cheia (20g)"
        ),
        FoodEntity(
            remoteId = "7",
            name = "Semente de Chia",
            category = "Sementes & Grãos",
            kcal = 486.0,
            isDiabetesSafe = true,
            isHypertensionSafe = true,
            functionalTags = listOf("omega3", "fibra_soluvel"),
            healthBenefits = "Rico em fibras solúveis e ômega-3.",
            servingUnit = "1 colher de sopa (15g)"
        ),
        FoodEntity(
            remoteId = "8",
            name = "Maçã Gala",
            category = "Frutas",
            kcal = 52.0,
            isDiabetesSafe = true,
            isHypertensionSafe = true,
            functionalTags = listOf("fibra_soluvel"),
            healthBenefits = "Excelente fonte de pectina para saciedade.",
            servingUnit = "1 unidade média"
        ),
        FoodEntity(
            remoteId = "9",
            name = "Suco de Laranja Natural",
            category = "Bebidas Hidratantes",
            kcal = 45.0,
            isDiabetesSafe = false,
            isHypertensionSafe = true,
            servingUnit = "150 ml (1 copo pequeno)"
        ),
        FoodEntity(
            remoteId = "10",
            name = "Beterraba Cozida",
            category = "Legumes & Raízes",
            kcal = 44.0,
            isDiabetesSafe = true,
            isHypertensionSafe = true,
            functionalTags = listOf("nitrato_natural", "potassio"),
            healthBenefits = "Rica em nitratos naturais que apoiam a vasodilatação.",
            servingUnit = "1 porção (100g)"
        )
    )

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
        assertTrue(reply.message.contains("Alimentação"))
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
        assertTrue(reply.message.contains("Alimentação"))
    }

    @Test
    fun `detect adding food to shopping list with direct trigger redirects to nutrition screen`() {
        val result = parser.parse("adicione chia na lista de compras")
        assertTrue(result is VoiceHealthIntent.ConversationalReply)
        val reply = result as VoiceHealthIntent.ConversationalReply
        assertTrue(reply.message.contains("Alimentação"))
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

    @Test
    fun `clinical suggestion for high glucose cites SBD and low GI food`() {
        val vitalGlucose = br.com.bragasaude.data.local.VitalSignEntity(
            userId = "u1",
            remoteId = "v1",
            measuredAt = java.util.Date(),
            glucoseLevel = 145
        )
        val suggestion = NutritionSuggestionEngine.suggestFoodForUser(
            profile = null,
            vitals = listOf(vitalGlucose),
            exams = emptyList(),
            catalog = testCatalog,
            userWeightKg = 70.0
        )
        assertEquals("GLUCOSE_HIGH", suggestion.targetCondition)
        assertTrue(suggestion.clinicalSociety.contains("SBD", ignoreCase = true) || suggestion.clinicalSociety.contains("Diabetes", ignoreCase = true))
        assertTrue(suggestion.food.isDiabetesSafe)
        assertTrue(!suggestion.spokenPrompt.contains("acima do peso", ignoreCase = true))
    }

    @Test
    fun `clinical suggestion for low glucose cites SBD 15g rule and fast carb`() {
        val vitalGlucose = br.com.bragasaude.data.local.VitalSignEntity(
            userId = "u1",
            remoteId = "v2",
            measuredAt = java.util.Date(),
            glucoseLevel = 58
        )
        val suggestion = NutritionSuggestionEngine.suggestFoodForUser(
            profile = null,
            vitals = listOf(vitalGlucose),
            exams = emptyList(),
            catalog = testCatalog,
            userWeightKg = 70.0
        )
        assertEquals("GLUCOSE_LOW", suggestion.targetCondition)
        assertTrue(suggestion.clinicalSociety.contains("SBD", ignoreCase = true) || suggestion.clinicalSociety.contains("Diabetes", ignoreCase = true))
        assertTrue(suggestion.spokenPrompt.contains("15", ignoreCase = true))
    }

    @Test
    fun `clinical suggestion for hypertension cites SBC and DASH`() {
        val vitalBp = br.com.bragasaude.data.local.VitalSignEntity(
            userId = "u1",
            remoteId = "v3",
            measuredAt = java.util.Date(),
            systolicPressure = 140,
            diastolicPressure = 90
        )
        val suggestion = NutritionSuggestionEngine.suggestFoodForUser(
            profile = null,
            vitals = listOf(vitalBp),
            exams = emptyList(),
            catalog = testCatalog,
            userWeightKg = 75.0
        )
        assertEquals("HYPERTENSION", suggestion.targetCondition)
        assertTrue(suggestion.clinicalSociety.contains("SBC", ignoreCase = true) || suggestion.clinicalSociety.contains("Cardiologia", ignoreCase = true))
        assertTrue(suggestion.food.isHypertensionSafe)
    }

    @Test
    fun `clinical suggestion for elevated BMI recommends satiety food WITHOUT saying acima do peso`() {
        val profile = br.com.bragasaude.data.local.ProfileEntity(
            userId = "u1",
            fullName = "Paciente Teste",
            height = 1.65,
            weight = 96.0 // IMC = 96 / (1.65^2) = 35.26 (Elevado)
        )
        val suggestion = NutritionSuggestionEngine.suggestFoodForUser(
            profile = profile,
            vitals = emptyList(),
            exams = emptyList(),
            catalog = testCatalog,
            userWeightKg = 96.0
        )
        assertEquals("WEIGHT_MANAGEMENT", suggestion.targetCondition)
        // Regra de Ouro: NUNCA verbalizar que o usuário está acima do peso
        val fullText = "${suggestion.reason} ${suggestion.spokenPrompt}".lowercase()
        assertTrue(!fullText.contains("acima do peso"))
        assertTrue(!fullText.contains("sobrepeso"))
        assertTrue(!fullText.contains("gordo"))
        assertTrue(!fullText.contains("obes"))
        assertTrue(fullText.contains("saciedade") || fullText.contains("leveza") || fullText.contains("fibras"))
    }
}
