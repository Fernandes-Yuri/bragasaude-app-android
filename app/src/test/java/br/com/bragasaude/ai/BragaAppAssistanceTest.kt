package br.com.bragasaude.ai

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class BragaAppAssistanceTest {
    private val hybrid = BragaHybridOrchestrator(GroqStreamSource { _, _ -> error("Ajuda local não chama a nuvem") }, GroqDynamicPrompt())

    @Test fun `tarefas reais do app recebem ajuda nos dois canais`() = runTest {
        val cases = mapOf(
            "Como vejo o tutorial de novo?" to "tutorial_app",
            "Como falo com os desenvolvedores?" to "feedback_app",
            "Como excluo minha conta?" to "excluir_conta",
            "Como ativo as notificações de saúde?" to "notificacoes_app",
            "Por que meu lembrete não tocou?" to "lembrete_sem_aviso",
            "Como registro que tomei meu remédio?" to "registrar_dose_app",
            "Como desfaço a água que registrei agora?" to "desfazer_agua",
            "Posso enviar foto ou PDF?" to "formatos_exame",
            "O valor lido da foto está errado, como corrijo?" to "revisar_exame",
            "Meus exames estão só neste celular?" to "armazenamento_exame",
            "Como retiro a cópia do exame da nuvem?" to "remover_nuvem_exame",
            "Como mando o relatório para o médico?" to "compartilhar_relatorio",
            "Pode me mostrar onde fica meu histórico?" to "historico_indireto",
            "Consigo usar sem internet?" to "uso_sem_internet",
            "Como conecto meu cuidador?" to "conectar_familiar",
            "Como retiro o acesso de um familiar?" to "revogar_familiar",
            "Meu cuidador consegue ver quais dados?" to "dados_familiar",
            "Como mando um recado para minha mãe?" to "recado_familiar",
            "Como adiciono um alimento à lista de compras?" to "compras_app",
            "Onde vejo as receitas da minha lista?" to "receitas_lista",
            "Como informo uma alergia alimentar?" to "alergia_perfil",
            "Como conecto meu relógio?" to "conectar_relogio",
            "Por que meus passos não atualizaram?" to "passos_sem_atualizacao",
            "Como altero minha meta de passos?" to "meta_passos",
            "Como mudo a voz do Braga?" to "voz_assistente",
            "Qual a diferença entre glicemia em jejum e depois de comer?" to "contexto_glicemia",
            "Chá conta como água?" to "bebida_agua_app"
        )
        for (channel in InputChannel.entries) cases.forEach { (text, intent) ->
            val output = (hybrid.respond(text, emptyList(), channel).toList().single() as BragaHybridEvent.Local).output
            assertEquals(text, "ajuda_$intent", output.intent)
            assertFalse(text, BragaActionGate.canParse(text, output))
            assertNull(text, output.healthQuery)
        }
        val prefixed = hybrid.analyze("Intendi, mas como conecto meu relógio?")
        assertEquals("ajuda_conectar_relogio", prefixed.intent)
    }

    @Test fun `ajuda preserva limites reais e nao afirma executar operacoes`() {
        assertTrue(hybrid.analyze("Como excluo minha conta?").respostaLocal.orEmpty().contains("não exclui"))
        assertTrue(hybrid.analyze("Como retiro a cópia do exame da nuvem?").respostaLocal.orEmpty().contains("não removeu"))
        assertTrue(hybrid.analyze("Meus exames estão só neste celular?").respostaLocal.orEmpty().contains("expiram em 24 horas"))
        assertTrue(hybrid.analyze("Como conecto meu relógio?").respostaLocal.orEmpty().contains("autorizar não garante"))
        assertTrue(hybrid.analyze("Como registro que tomei meu remédio?").respostaLocal.orEmpty().contains("Registrar dose"))
        for (channel in InputChannel.entries) {
            assertTrue(hybrid.analyze("Como excluo minha conta? Estou com dor no peito", channel).isEmergencia)
            assertEquals(BragaRoute.CLOUD, hybrid.analyze("Como minha pressão afeta os rins?", channel).route)
            assertFalse(BragaActionGate.canParse("Não quero excluir minha conta", hybrid.analyze("Não quero excluir minha conta", channel)))
            assertNotEquals("ajuda_remover_nuvem_exame", hybrid.analyze("Como retiro a cópia do exame da nuvem e o que significa o resultado?", channel).intent)
        }
    }

    @Test fun `novas consultas carregam entidades tipadas e nao preparam registros`() {
        val cases = mapOf(
            "Qual foi a maior e a menor pressão da semana?" to HealthQuery(HealthMetric.PRESSURE, HealthPeriod.LAST_7_DAYS, HealthOperation.EXTREMES),
            "Minha pressão aumentou em relação a ontem?" to HealthQuery(HealthMetric.PRESSURE, HealthPeriod.TODAY, HealthOperation.COMPARE_YESTERDAY),
            "Há quantos dias não registro glicemia?" to HealthQuery(HealthMetric.GLUCOSE, operation = HealthOperation.AGE),
            "Qual é minha meta de água e quanto falta?" to HealthQuery(HealthMetric.WATER, HealthPeriod.TODAY, HealthOperation.SUMMARY),
            "Qual foi meu último batimento?" to HealthQuery(HealthMetric.HEART_RATE),
            "Qual foi minha última saturação ontem?" to HealthQuery(HealthMetric.OXYGEN, HealthPeriod.YESTERDAY),
            "Qual foi meu último peso?" to HealthQuery(HealthMetric.WEIGHT),
            "Quanto de losartana ainda tenho?" to HealthQuery(HealthMetric.MEDICATION_STOCK, medicationName = "losartana"),
            "Eu já registrei minha dose de losartana hoje?" to HealthQuery(HealthMetric.MEDICATION_DOSES, HealthPeriod.TODAY, medicationName = "losartana"),
            "Já registrei a dose de losartana hoje?" to HealthQuery(HealthMetric.MEDICATION_DOSES, HealthPeriod.TODAY, medicationName = "losartana")
        )
        for (channel in InputChannel.entries) cases.forEach { (text, query) ->
            val output = hybrid.analyze(text, channel)
            assertEquals(text, query, output.healthQuery)
            assertEquals(text, BragaRoute.HEALTH_MEMORY, output.route)
            assertFalse(text, BragaActionGate.canParse(text, output))
        }
        assertEquals("lousartana", hybrid.analyze("Quanto de lousartana ainda tenho?").healthQuery?.medicationName)
        assertEquals("entrada_medicamento_sem_nome", hybrid.analyze("Quanto remédio ainda tenho?").intent)
        assertEquals("entrada_medicamento_sem_nome", hybrid.analyze("Já registrei a dose de hoje?").intent)
        assertEquals("entrada_meta_ambigua", hybrid.analyze("Qual é minha meta e quanto falta?").intent)
        assertNull(HealthQueryResolver.explicit("Quanto de losartana meu pai ainda tem?"))
    }

    @Test fun `meta sem nome usa apenas contexto recente de agua e novas ajudas podem ser reformuladas`() {
        val session = HealthQuerySession { 1_000L }
        val ticket = session.advanceTurn("u", "c")
        session.remember(HealthQuery(HealthMetric.WATER, HealthPeriod.TODAY), "u", "c", ticket)
        val result = hybrid.analyze("Qual é minha meta e quanto falta?", InputChannel.TEXT, session, "u", "c")
        assertEquals(HealthMetric.WATER, result.healthQuery?.metric)
        session.clear()
        val helpTicket = session.advanceTurn("u", "c")
        val help = hybrid.analyze("Como conecto meu relógio?", InputChannel.TEXT)
        session.rememberReply(help, "u", "c", helpTicket)
        session.advanceTurn("u", "c")
        val repair = hybrid.analyze("Não entendi", InputChannel.TEXT, session, "u", "c")
        assertEquals("ajuda_reformulacao_contextual", repair.intent)
        assertTrue(repair.respostaLocal.orEmpty().contains("Health Connect"))
        assertFalse(BragaActionGate.canParse("Não entendi", repair))
    }
}
