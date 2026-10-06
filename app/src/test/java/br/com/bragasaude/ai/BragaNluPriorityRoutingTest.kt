package br.com.bragasaude.ai

import br.com.bragasaude.domain.VoiceHealthIntent
import br.com.bragasaude.domain.VoiceHealthParser
import br.com.bragasaude.domain.HydrationConversation
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class BragaNluPriorityRoutingTest {
    private fun hybrid() = BragaHybridOrchestrator(GroqStreamSource { _, _ ->
        error("A decisão local não deve abrir inferência externa")
    }, GroqDynamicPrompt())

    @Test fun `sintoma atual preserva emergencia mesmo quando usuario pergunta por que`() = runTest {
        val current = listOf("Por que estou com dor no peito?", "Por que não consigo respirar?",
            "Por que meu pai está com falta de ar?", "O que significa essa dor no peito que estou sentindo agora?",
            "Não tenho dor no peito, mas por que não consigo respirar?",
            "Ontem tive dor no peito e agora voltou", "Por que caí no chão?")
        for (channel in InputChannel.entries) current.forEach { text ->
            val events = hybrid().respond(text, emptyList(), channel).toList()
            val output = (events.single() as BragaHybridEvent.Local).output
            assertEquals(text, BragaRoute.EMERGENCY, output.route)
            val emergencyNumber = if (channel == InputChannel.VOICE) "cento e noventa e dois" else "192"
            assertTrue(text, output.respostaLocal.orEmpty().contains(emergencyNumber))
            assertFalse(text, BragaActionGate.canParse(text, output))
        }
        listOf("O que significa dor no peito?", "O que é falta de ar?", "O que é dor no peito?",
            "Por que pode ocorrer falta de ar?",
            "Por que algumas pessoas têm falta de ar?",
            "Se eu tiver dor no peito, o que devo fazer?", "Li a frase dor no peito num livro",
            "Não estou com dor no peito", "Ontem tive dor no peito").forEach { text ->
            assertFalse(text, BragaNluEngine.analisar(text).isEmergencia)
        }
    }

    @Test fun `tarefas do app sao locais e preservam o pedido depois de agradecimento`() = runTest {
        val expected = mapOf(
            "Como adiciono um exame?" to "ajuda_anexar_exame",
            "Entendi, como faço para anexar meu exame?" to "ajuda_anexar_exame",
            "Obrigado, mas como adiciono um exame?" to "ajuda_anexar_exame",
            "Por que meu exame não aparece?" to "ajuda_exame_sem_dados",
            "Por que minha glicemia não aparece?" to "ajuda_registro_sem_dados",
            "Por que minha pressão não apareceu?" to "ajuda_registro_sem_dados",
            "Minha glicemia está errada, como corrijo?" to "ajuda_corrigir_registro",
            "Obrigado, mas como corrijo minha glicemia?" to "ajuda_corrigir_registro",
            "Como corrijo a pressão que registrei?" to "ajuda_corrigir_registro",
            "Obrigado, mas me explica melhor" to "entrada_explicacao_sem_referencia",
            "Obrigado, como vejo meus passos?" to "entrada_pedido_nao_resolvido",
            "Como adiciono um exame e o que significa o resultado?" to "entrada_pedido_misto")
        for (channel in InputChannel.entries) expected.forEach { (text, intent) ->
            val output = (hybrid().respond(text, emptyList(), channel).toList().single() as BragaHybridEvent.Local).output
            assertEquals(text, intent, output.intent)
            assertFalse(text, output.delegarParaNuvem)
            assertFalse(text, BragaActionGate.canParse(text, output))
            assertNull(text, output.healthQuery)
        }
        assertEquals(BragaRoute.CLOUD, BragaNluEngine.analisar("O que significa meu exame de sangue?").route)
        assertEquals(BragaRoute.CLOUD, BragaNluEngine.analisar("Como minha pressão afeta os rins?").route)
        assertNotEquals("ajuda_corrigir_registro", BragaNluEngine.analisar("Como corrijo pressão alta com remédio?").intent)
    }

    @Test fun `periodo nao suportado nunca se transforma em outra consulta`() {
        val texts = listOf("Qual foi minha pressão anteontem?", "Qual foi minha pressão em setembro?",
            "Qual foi minha glicemia no dia 02/10/2026?", "Qual foi minha pressão semana passada?",
            "Qual foi minha glicemia nos últimos 14 dias?", "Qual foi minha pressão ontem às 8 horas?",
            "Qual foi minha pressão no mês passado?", "Qual foi minha pressão em 2025?",
            "Qual foi minha pressão na segunda-feira?", "Qual foi minha pressão entre ontem e hoje?",
            "Qual foi minha pressão desde ontem?", "Minha pressão semana passada",
            "Qual foi minha pressão hoje de manhã?", "E no ano 2025?", "E anteontem?", "E em setembro?")
        val session = HealthQuerySession { 1_000L }
        session.advanceTurn("u", "c")
        session.remember(HealthQuery(HealthMetric.PRESSURE), "u", "c")
        for (channel in InputChannel.entries) texts.forEach { text ->
            val output = hybrid().analyze(text, channel, session, "u", "c")
            assertEquals(text, "entrada_periodo_nao_suportado", output.intent)
            assertNull(text, output.healthQuery)
            assertFalse(text, output.delegarParaNuvem)
            assertFalse(text, BragaActionGate.canParse(text, output))
            assertNull(text, HealthQueryResolver.explicit(text))
            assertNull(text, HealthQueryResolver.followUp(text, HealthQuery(HealthMetric.PRESSURE)))
        }
        listOf("Qual foi minha pressão ontem?", "Qual foi minha pressão hoje?",
            "Qual foi a média da minha glicemia na semana?", "Quanta água tomei nos últimos 7 dias?",
            "Qual foi minha pressão nos últimos 30 dias?", "Quanta água tomei hoje com meta de 2000 ml?").forEach { text ->
            assertNotNull(text, HealthQueryResolver.explicit(text))
        }
        assertEquals(BragaRoute.CLOUD, BragaNluEngine.analisar("Por que minha pressão subiu ontem?").route)
        assertTrue(BragaNluEngine.analisar("Qual a capital de Portugal em 2025?").isBloqueioSeguranca)
        assertTrue(BragaNluEngine.analisar("Qual foi o último jogo em 2025?").isBloqueioSeguranca)
    }

    @Test fun `perguntas e negacoes nao chegam ao parser de registro nos dois canais`() {
        val parser = VoiceHealthParser()
        val unsafe = listOf("Como registro pressão 120 por 80?", "Como anoto glicemia 105?",
            "Não tomei meu remédio das 8 horas", "Não bebi 500 ml de água",
            "Obrigado, mas como registro 250 ml de água?", "Não anota pressão 12 por 8",
            "Meu registro de glicemia 105 está errado, como corrijo?",
            "Como adiciono um exame de glicemia 105?")
        for (channel in InputChannel.entries) unsafe.forEach { text ->
            val output = BragaNluEngine.analisar(text, channel)
            val prepared = if (BragaActionGate.canParse(text, output)) parser.parse(text) else null
            assertNull(text, prepared)
            assertNull(text, BragaActionGate.readOnlyQuery(text, output, parser))
        }
        for (channel in InputChannel.entries) {
            val water = "Bebi 500 ml de água"
            assertTrue(BragaActionGate.canParse(water, BragaNluEngine.analisar(water, channel)))
            assertTrue(parser.parse(water) is VoiceHealthIntent.Hydration)
            val pressure = "Pressão 12 por 8"
            assertTrue(BragaActionGate.canParse(pressure, BragaNluEngine.analisar(pressure, channel)))
            assertTrue(parser.parse(pressure) is VoiceHealthIntent.BloodPressure)
            val slashPressure = "Minha pressão 12/8"
            assertFalse(HealthQueryResolver.hasUnsupportedPeriod(slashPressure))
            assertTrue(BragaActionGate.canParse(slashPressure, BragaNluEngine.analisar(slashPressure, channel)))
            val familyQuery = "Como está a pressão do meu pai?"
            val readOnly = BragaActionGate.readOnlyQuery(familyQuery,
                BragaNluEngine.analisar(familyQuery, channel), parser)
            assertNotNull(readOnly)
            assertEquals("pai", readOnly!!.targetName)
        }
    }

    @Test fun `negacao curta pode cancelar rascunho de agua sem preparar consumo`() {
        val draft = HydrationConversation()
        assertTrue(draft.respond("bebi dois copos de água", "u") is HydrationConversation.Reply.Say)
        val text = "não quero mais"
        assertTrue(BragaActionGate.canParse(text, BragaNluEngine.analisar(text)))
        val reply = draft.respond(text, "u") as HydrationConversation.Reply.Say
        assertTrue(reply.text.contains("Nada foi salvo"))
        assertFalse(VoiceHealthParser().parse(text) is VoiceHealthIntent.Hydration)
    }
}
