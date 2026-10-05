package br.com.bragasaude.ai

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class HealthQueryTest {
    @Test fun `consultas pessoais tipadas preservam periodo e operacao`() {
        val pressure = HealthQueryResolver.explicit("Qual foi minha última pressão ontem?")!!
        assertEquals(HealthMetric.PRESSURE, pressure.metric)
        assertEquals(HealthPeriod.YESTERDAY, pressure.period)
        assertEquals(HealthOperation.LAST, pressure.operation)
        assertEquals(BragaHealthMemory.PRESSURE, pressure.intent)
        val average = HealthQueryResolver.explicit("Qual foi a média da minha glicemia em jejum na semana?")!!
        assertEquals(HealthMetric.GLUCOSE, average.metric)
        assertEquals(HealthPeriod.LAST_7_DAYS, average.period)
        assertEquals(HealthOperation.AVERAGE, average.operation)
        assertEquals("fasting", average.glucoseType)
        val water = HealthQueryResolver.explicit("Quanta água tomei hoje?")!!
        assertEquals(HealthMetric.WATER, water.metric)
        assertEquals(HealthPeriod.TODAY, water.period)
        assertEquals(HealthOperation.SUMMARY, water.operation)
    }

    @Test fun `consulta nao confunde explicacao registro familiar ou duas metricas`() {
        listOf("Como minha pressão afeta os rins?", "Minha glicemia é normal?",
            "Como está a pressão da minha mãe?", "Qual foi minha pressão e glicemia?",
            "Registre minha glicemia em jejum", "Como ficou a pressão do meu pai?",
            "Quero saber a pressão dela", "Como baixar minha pressão?").forEach {
            assertNull(it, HealthQueryResolver.explicit(it))
        }
        assertTrue(HealthQueryResolver.isAmbiguous("Qual foi minha pressão e glicemia?"))
        assertNotNull(HealthQueryResolver.explicit("Qual o histórico da minha pressão?"))
    }

    @Test fun `followup herda periodo muda metrica e nao inventa tipo`() {
        val last = HealthQuery(HealthMetric.PRESSURE, HealthPeriod.TODAY, HealthOperation.LAST)
        assertEquals(last.copy(period = HealthPeriod.YESTERDAY), HealthQueryResolver.followUp("E ontem?", last))
        assertEquals(last.copy(period = HealthPeriod.LAST_7_DAYS, operation = HealthOperation.AVERAGE),
            HealthQueryResolver.followUp("E a média da semana?", last))
        assertEquals(last.copy(metric = HealthMetric.GLUCOSE), HealthQueryResolver.followUp("E minha glicemia?", last))
        val fasting = HealthQuery(HealthMetric.GLUCOSE, HealthPeriod.LAST_7_DAYS, HealthOperation.AVERAGE, "fasting")
        assertEquals(fasting.copy(period = HealthPeriod.YESTERDAY), HealthQueryResolver.followUp("E ontem?", fasting))
        assertNull(HealthQueryResolver.followUp("E depois?", fasting))
        assertNull(HealthQueryResolver.followUp("E a pressão da minha mãe?", last))
        assertNull(HealthQueryResolver.followUp("E como a pressão afeta os rins?", last))
    }

    @Test fun `intervalos usam fuso inicio inclusivo fim exclusivo e teto atual`() {
        val now = Instant.parse("2026-10-04T03:00:00Z")
        val zone = ZoneId.of("America/Sao_Paulo")
        val yesterday = HealthQueryInterval.forPeriod(HealthPeriod.YESTERDAY, now, zone)
        assertEquals(Instant.parse("2026-10-03T03:00:00Z").toEpochMilli(), yesterday.startInclusiveMillis)
        assertEquals(now.toEpochMilli(), yesterday.endExclusiveMillis)
        val today = HealthQueryInterval.forPeriod(HealthPeriod.TODAY, now, zone)
        assertEquals(now.toEpochMilli(), today.startInclusiveMillis)
        assertEquals(now.toEpochMilli() + 1, today.endExclusiveMillis)
        val week = HealthQueryInterval.forPeriod(HealthPeriod.LAST_7_DAYS, now, zone)
        assertEquals(Instant.parse("2026-09-28T03:00:00Z").toEpochMilli(), week.startInclusiveMillis)
    }

    @Test fun `intervalo ontem considera dia de 23 horas no horario de verao`() {
        val zone = ZoneId.of("America/New_York")
        val interval = HealthQueryInterval.forPeriod(HealthPeriod.YESTERDAY,
            Instant.parse("2026-03-09T12:00:00Z"), zone)
        assertEquals(23 * 3_600_000L, interval.endExclusiveMillis - interval.startInclusiveMillis)
    }
}

class HealthQuerySessionTest {
    private var clock = 1_000_000L
    private val session = HealthQuerySession { clock }
    private val pressure = HealthQuery(HealthMetric.PRESSURE, operation = HealthOperation.SUMMARY)
    private fun remember() {
        session.advanceTurn("owner", "conversation")
        session.remember(pressure, "owner", "conversation")
    }

    @Test fun `resolve e idempotente sem consumir os dois turnos`() {
        remember()
        session.advanceTurn("owner", "conversation")
        repeat(3) { assertEquals(HealthPeriod.YESTERDAY,
            session.resolve("e ontem", "owner", "conversation")!!.period) }
        session.advanceTurn("owner", "conversation")
        assertNotNull(session.resolve("e ontem", "owner", "conversation"))
        session.advanceTurn("owner", "conversation")
        assertNull(session.resolve("e ontem", "owner", "conversation"))
    }

    @Test fun `contexto vence em cinco minutos mas consulta explicita continua`() {
        remember()
        clock += 299_999
        assertNotNull(session.resolve("e ontem", "owner", "conversation"))
        clock++
        assertNull(session.resolve("e ontem", "owner", "conversation"))
        assertNotNull(session.resolve("qual foi minha última pressão ontem", "owner", "conversation"))
    }

    @Test fun `trocar conta conversa e apagar sessao remove contexto`() {
        remember()
        assertNull(session.resolve("e ontem", "other", "conversation"))
        assertNull(session.resolve("e ontem", "owner", "conversation"))
        remember()
        assertNull(session.resolve("e ontem", "owner", "another"))
        remember()
        session.clear()
        session.remember(pressure, "owner", "conversation")
        assertNull(session.resolve("e ontem", "owner", "conversation"))
    }

    @Test fun `followup bem sucedido renova contexto sem guardar valores`() {
        remember()
        session.advanceTurn("owner", "conversation")
        val yesterday = session.resolve("e ontem", "owner", "conversation")!!
        session.remember(yesterday, "owner", "conversation")
        session.advanceTurn("owner", "conversation")
        val glucose = session.resolve("e minha glicemia", "owner", "conversation")!!
        assertEquals(HealthMetric.GLUCOSE, glucose.metric)
        assertEquals(HealthPeriod.YESTERDAY, glucose.period)
        assertNull(glucose.glucoseType)
    }

    @Test fun `resposta antiga nao reabre contexto de outra conversa`() {
        remember()
        session.advanceTurn("owner", "new")
        session.remember(pressure, "owner", "conversation")
        assertNull(session.resolve("e ontem", "owner", "new"))
        assertNull(session.resolve("e a média da semana", "owner", "new"))
    }

    @Test fun `relogio que retrocede invalida contexto`() {
        remember()
        clock--
        assertNull(session.resolve("e ontem", "owner", "conversation"))
    }
}
