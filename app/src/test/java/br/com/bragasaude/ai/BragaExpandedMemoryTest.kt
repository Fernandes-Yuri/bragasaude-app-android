package br.com.bragasaude.ai

import br.com.bragasaude.data.local.*
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.util.Date

class BragaExpandedMemoryTest {
    private val instant = Instant.parse("2026-10-06T15:00:00Z")
    private val zone = ZoneId.of("America/Sao_Paulo")
    private val vitals = mockk<VitalSignDao>(relaxed = true)
    private val profiles = mockk<ProfileDao>(relaxed = true)
    private val biometry = mockk<BiometryDao>(relaxed = true)
    private val medicines = mockk<MedicationDao>(relaxed = true)
    private val logs = mockk<MedicationLogDao>(relaxed = true)
    private val memory = BragaHealthMemory(vitals, profiles, { instant }, zone, biometry, medicines, logs)
    private fun reading(hours: Long = 1, owner: String = "u", sys: Int? = null, dia: Int? = null,
                        glucose: Int? = null, type: String? = null, heart: Int? = null, oxygen: Int? = null) = VitalSignEntity(
        userId = owner, systolicPressure = sys, diastolicPressure = dia, glucoseLevel = glucose,
        glucoseType = type, heartRate = heart, oxygenSaturation = oxygen, measuredAt = Date.from(instant.minusSeconds(hours * 3600)))

    @Test fun `extremos de pressao sao calculados separadamente sem fabricar uma medicao`() = runTest {
        coEvery { vitals.getLatestPressureForMemory(any(), any(), any()) } returns null
        coEvery { vitals.getPressureIntervalForMemory("u", any(), any()) } returns listOf(
            reading(sys = 110, dia = 90), reading(sys = 130, dia = 70), reading(owner = "outro", sys = 250, dia = 150), reading(hours = -1, sys = 240, dia = 140))
        val answer = memory.answerResult(HealthQuery(HealthMetric.PRESSURE, HealthPeriod.LAST_7_DAYS, HealthOperation.EXTREMES), "u")
        assertTrue(answer.hasData)
        assertTrue(answer.text, answer.text.contains("2 registros"))
        assertTrue(answer.text, answer.text.contains("110 a 130"))
        assertTrue(answer.text, answer.text.contains("70 a 90"))
        assertTrue(answer.text, answer.text.contains("não formam uma medição única"))
        assertNull(answer.referenceMeasuredAtMillis)
    }

    @Test fun `comparacao le somente ultimos registros de hoje e ontem sem diagnosticar`() = runTest {
        val today = HealthQueryInterval.forPeriod(HealthPeriod.TODAY, instant, zone)
        val yesterday = HealthQueryInterval.forPeriod(HealthPeriod.YESTERDAY, instant, zone)
        coEvery { vitals.getLatestPressureForMemory("u", today.startInclusiveMillis, today.endExclusiveMillis) } returns reading(sys = 120, dia = 75)
        coEvery { vitals.getLatestPressureForMemory("u", yesterday.startInclusiveMillis, yesterday.endExclusiveMillis) } returns reading(hours = 25, sys = 110, dia = 80)
        val answer = memory.answerResult(HealthQuery(HealthMetric.PRESSURE, HealthPeriod.TODAY, HealthOperation.COMPARE_YESTERDAY), "u")
        assertTrue(answer.hasData)
        assertTrue(answer.text, answer.text.contains("sistólica +10"))
        assertTrue(answer.text, answer.text.contains("diastólica -5"))
        assertTrue(answer.text, answer.text.contains("não identifica causa"))
        assertNull(answer.referenceMeasuredAtMillis)
        coEvery { vitals.getLatestPressureForMemory("u", yesterday.startInclusiveMillis, yesterday.endExclusiveMillis) } returns null
        val absent = memory.answerResult(HealthQuery(HealthMetric.PRESSURE, operation = HealthOperation.COMPARE_YESTERDAY), "u")
        assertFalse(absent.hasData)
        assertTrue(absent.text.contains("não equivale a valor zero"))
    }

    @Test fun `idade do registro nao afirma que usuario deixou de medir fora do app`() = runTest {
        coEvery { vitals.getLatestGlucoseForMemory("u", any(), any(), any(), any()) } returns reading(hours = 49, glucose = 105, type = "jejum")
        val answer = memory.answerResult(HealthQuery(HealthMetric.GLUCOSE, operation = HealthOperation.AGE), "u")
        assertTrue(answer.text.contains("2 dias completos"))
        assertTrue(answer.text.contains("não medições feitas fora"))
        assertEquals(instant.minusSeconds(49 * 3600).toEpochMilli(), answer.referenceMeasuredAtMillis)
    }

    @Test fun `glicemias de contextos diferentes nao recebem diferenca automatica`() = runTest {
        val today = HealthQueryInterval.forPeriod(HealthPeriod.TODAY, instant, zone)
        val yesterday = HealthQueryInterval.forPeriod(HealthPeriod.YESTERDAY, instant, zone)
        coEvery { vitals.getLatestGlucoseForMemory("u", today.startInclusiveMillis, today.endExclusiveMillis, false, any()) } returns reading(glucose = 160, type = "post_prandial")
        coEvery { vitals.getLatestGlucoseForMemory("u", yesterday.startInclusiveMillis, yesterday.endExclusiveMillis, false, any()) } returns reading(hours = 25, glucose = 100, type = "jejum")
        val answer = memory.answerResult(HealthQuery(HealthMetric.GLUCOSE, operation = HealthOperation.COMPARE_YESTERDAY), "u")
        assertTrue(answer.hasData)
        assertTrue(answer.text.contains("não comparo"))
        assertFalse(answer.text.contains("Diferença registrada"))
    }

    @Test fun `batimentos saturacao e peso usam fontes locais e datas reais`() = runTest {
        coEvery { vitals.getLatestAuxiliaryForMemory("u", "HEART_RATE", any(), any()) } returns reading(heart = 72)
        coEvery { vitals.getLatestAuxiliaryForMemory("u", "OXYGEN", any(), any()) } returns reading(oxygen = 97)
        coEvery { biometry.getLatestWeightForMemory("u", any(), any()) } returns BiometryEntity(userId = "u", weight = 80.5f, height = 170f, imc = 27.8f, measuredAt = Date.from(instant.minusSeconds(3600)))
        assertTrue(memory.answerResult(HealthQuery(HealthMetric.HEART_RATE), "u").text.contains("72 bpm"))
        assertTrue(memory.answerResult(HealthQuery(HealthMetric.OXYGEN), "u").text.contains("97%"))
        val weight = memory.answerResult(HealthQuery(HealthMetric.WEIGHT), "u")
        assertTrue(weight.text.contains("80.5 kg"))
        assertEquals(instant.minusSeconds(3600).toEpochMilli(), weight.referenceMeasuredAtMillis)
        coVerify(exactly = 0) { profiles.getProfileOneShot(any()) }
        coEvery { vitals.getLatestAuxiliaryForMemory("u", "OXYGEN", any(), any()) } returns reading(oxygen = 120)
        assertFalse(memory.answerResult(HealthQuery(HealthMetric.OXYGEN), "u").hasData)
        coEvery { biometry.getLatestWeightForMemory("u", any(), any()) } returns BiometryEntity(userId = "outro", weight = 99f, height = 170f, imc = 30f)
        assertFalse(memory.answerResult(HealthQuery(HealthMetric.WEIGHT), "u").hasData)
    }

    @Test fun `medicamento exige identidade exata e nao aproxima nomes ou cadastros duplicados`() = runTest {
        coEvery { medicines.getAllSync("u") } returns listOf(MedicationEntity("m", "u", "Losartana", totalUnits = 30, currentUnits = 12), MedicationEntity("outro", "outro", "Losartana", totalUnits = 50, currentUnits = 50))
        val query = HealthQuery(HealthMetric.MEDICATION_STOCK, medicationName = "losartana")
        assertTrue(memory.answerResult(query, "u").text.contains("12 unidades"))
        assertFalse(memory.answerResult(query.copy(medicationName = "lousartana"), "u").hasData)
        coEvery { medicines.getAllSync("u") } returns listOf(MedicationEntity("a", "u", "Losartana"), MedicationEntity("b", "u", "Losartana"))
        val duplicate = memory.answerResult(query, "u")
        assertFalse(duplicate.hasData)
        assertTrue(duplicate.text.contains("mais de um cadastro"))
        coEvery { medicines.getAllSync("u") } returns listOf(MedicationEntity("m", "u", "Losartana"))
        assertFalse(memory.answerResult(query, "u").hasData)
        coVerify(exactly = 0) { medicines.decrementUnits(any(), any()) }
    }

    @Test fun `consulta de dose filtra conta medicamento periodo e duplicatas sem afirmar consumo`() = runTest {
        coEvery { medicines.getAllSync("u") } returns listOf(MedicationEntity("m", "u", "Losartana"))
        val log = MedicationLogEntity("l", "u", "m", Date.from(instant.minusSeconds(3600)))
        coEvery { logs.getForMemory("u", "m", any(), any()) } returns listOf(log, log, log.copy(id = "other", userId = "outro"), log.copy(id = "drug", medicationId = "outro"), log.copy(id = "future", takenAt = Date.from(instant.plusSeconds(3600))))
        val query = HealthQuery(HealthMetric.MEDICATION_DOSES, HealthPeriod.TODAY, medicationName = "losartana")
        val answer = memory.answerResult(query, "u")
        assertTrue(answer.hasData)
        assertTrue(answer.text.contains("1 registro"))
        assertTrue(answer.text.contains("não confirma que todas"))
        coEvery { logs.getForMemory("u", "m", any(), any()) } returns emptyList()
        val absent = memory.answerResult(query, "u")
        assertFalse(absent.hasData)
        assertTrue(absent.text.contains("não confirma que você não tomou"))
        coVerify(exactly = 0) { logs.insert(any()) }
    }

    @Test fun `doses so sao consultadas depois de encontrar medicamento cadastrado na propria conta`() = runTest {
        val query = HealthQuery(HealthMetric.MEDICATION_DOSES, HealthPeriod.TODAY, medicationName = "losartana")
        coEvery { medicines.getAllSync("u") } returns emptyList()
        val missing = memory.answerResult(query, "u")
        assertFalse(missing.hasData)
        assertTrue(missing.text.contains("Não encontrei um medicamento"))
        coEvery { medicines.getAllSync("u") } returns listOf(MedicationEntity("m", "outro", "Losartana"))
        assertFalse(memory.answerResult(query, "u").hasData)
        coEvery { medicines.getAllSync("u") } returns listOf(MedicationEntity("m", "u", "Lousartana"))
        assertFalse(memory.answerResult(query, "u").hasData)
        coVerify(exactly = 0) { logs.getForMemory(any(), any(), any(), any()) }
        coEvery { medicines.getAllSync("u") } returns listOf(MedicationEntity("m", "u", "Losartana"))
        coEvery { logs.getForMemory("u", "m", any(), any()) } returns emptyList()
        val registered = memory.answerResult(query, "u")
        assertFalse(registered.hasData)
        assertTrue(registered.text.contains("Não encontrei registros de dose"))
        coVerify(exactly = 1) { logs.getForMemory("u", "m", any(), any()) }
        coVerify(exactly = 0) { logs.insert(any()) }
    }
    @Test fun `operacoes nao implementadas e login ausente nao inventam dados`() = runTest {
        assertFalse(memory.answerResult(HealthQuery(HealthMetric.HEART_RATE, operation = HealthOperation.AVERAGE), "u").hasData)
        assertFalse(memory.answerResult(HealthQuery(HealthMetric.WEIGHT, operation = HealthOperation.EXTREMES), "u").hasData)
        assertFalse(memory.answerResult(HealthQuery(HealthMetric.MEDICATION_STOCK, HealthPeriod.YESTERDAY, medicationName = "losartana"), "u").hasData)
        val anonymous = memory.answerResult(HealthQuery(HealthMetric.MEDICATION_STOCK, medicationName = "losartana"), "anonymous")
        assertFalse(anonymous.hasData)
        assertTrue(anonymous.text.contains("conta"))
    }
}
