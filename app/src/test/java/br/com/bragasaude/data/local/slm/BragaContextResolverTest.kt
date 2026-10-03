package br.com.bragasaude.data.local.slm

import br.com.bragasaude.data.local.*
import br.com.bragasaude.data.remote.auth.AuthService
import br.com.bragasaude.data.remote.repository.FamilyBridgeRepository
import br.com.bragasaude.domain.*
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.time.*
import java.util.Date

class BragaContextResolverTest {
    private val vitals = mockk<VitalSignDao>()
    private val meds = mockk<MedicationDao>()
    private val logs = mockk<MedicationLogDao>()
    private val profiles = mockk<ProfileDao>()
    private val auth = mockk<AuthService>()
    private val family = mockk<FamilyBridgeRepository>()
    private val parser = VoiceHealthParser()
    private val now = ZonedDateTime.of(2026, 10, 3, 9, 0, 0, 0, ZoneId.of("America/Sao_Paulo"))
    private val resolver = BragaContextResolver(BragaIntentRouter(parser), parser, vitals, meds, logs, profiles, auth, family)
    init {
        every { auth.currentUserId } returns "owner"
        coEvery { profiles.getProfileOneShot("owner") } returns null
    }
    @Test fun pressureHistoryUsesOwnerAndYesterdayBoundaries() = runTest {
        val start = now.toLocalDate().minusDays(1).atStartOfDay(now.zone).toInstant().toEpochMilli()
        val end = now.toLocalDate().atStartOfDay(now.zone).toInstant().toEpochMilli()
        val at = now.minusDays(1).withHour(15).toInstant().toEpochMilli()
        coEvery { vitals.latestPressureInRange("owner", start, end) } returns VitalSignEntity(userId = "owner", systolicPressure = 130, diastolicPressure = 80, measuredAt = Date(at))
        val result = resolver.resolve("quanto deu minha pressão ontem?", now = now)
        assertTrue(result.instruction.contains("130 por 80"))
        assertTrue(result.instruction.contains("15:00"))
        assertNull(result.action)
        coVerify(exactly = 0) { vitals.insert(any()) }
    }
    @Test fun hydrationSumAndMissingGlucoseComeFromRoom() = runTest {
        coEvery { vitals.hydrationInRange("owner", any(), any()) } returns 650L
        coEvery { vitals.latestGlucoseInRange("owner", any(), any()) } returns null
        assertTrue(resolver.resolve("quanta água bebi hoje?", now = now).fallback.contains("650"))
        assertTrue(resolver.resolve("qual foi minha glicose ontem?", now = now).fallback.contains("Não encontrei"))
    }
    @Test fun draftDoesNotClaimPersistenceOrWriteToDatabase() = runTest {
        val result = resolver.resolve("bebi 300ml de água", now = now)
        assertEquals("REGISTRAR_AGUA", result.action)
        assertEquals("300", result.parameters["quantidade_ml"])
        assertTrue(result.instruction.contains("ainda está pendente"))
        coVerify(exactly = 0) { vitals.insert(any()) }
    }
    @Test fun medicationUsesRealDoseAndOnlyUnloggedSchedule() = runTest {
        coEvery { meds.getAllSync("owner") } returns listOf(MedicationEntity("med1", "owner", "Losartana", dosage = "50 mg", scheduleTimes = "08:00,20:00"))
        coEvery { logs.logsInRange("owner", any(), any()) } returns emptyList()
        val result = resolver.resolve("tomei meu remédio", now = now)
        assertEquals("REGISTRAR_MEDICAMENTO", result.action)
        assertEquals("med1", result.parameters["medication_id"])
        assertEquals("08:00", result.parameters["horario"])
        assertTrue(result.instruction.contains("50 mg"))
        val taken = MedicationLogEntity("log1", "owner", "med1", scheduledFor = "2026-10-03T08:00")
        coEvery { logs.logsInRange("owner", any(), any()) } returns listOf(taken)
        assertNull(resolver.resolve("tomei a Losartana", now = now).action)
    }
    @Test fun ambiguousMedicationsRequireClarification() = runTest {
        coEvery { meds.getAllSync("owner") } returns listOf(MedicationEntity("a", "owner", "Losartana", scheduleTime = "08:00"), MedicationEntity("b", "owner", "Outro", scheduleTime = "09:00"))
        coEvery { logs.logsInRange("owner", any(), any()) } returns emptyList()
        val result = resolver.resolve("tomei meu remédio", now = now)
        assertNull(result.action)
        assertTrue(result.fallback.contains("mais de uma"))
    }
    @Test fun unselectedFamilyNeverReadsOwnerHistoryAsFamilyHistory() = runTest {
        assertNull(resolver.resolve("qual foi a pressão da minha mãe ontem?", now = now).action)
        coVerify(exactly = 0) { vitals.latestPressureInRange(any(), any(), any()) }
    }
    @Test fun emergencyAndFreeTalkDoNotQueryClinicalDatabase() = runTest {
        assertEquals("EMERGENCIA", resolver.resolve("dor forte no peito", now = now).action)
        assertFalse(resolver.resolve("estou com saudade", now = now).factual)
        coVerify(exactly = 0) { profiles.getProfileOneShot(any()) }
    }
}
