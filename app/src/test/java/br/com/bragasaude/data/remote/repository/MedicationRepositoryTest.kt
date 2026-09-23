package br.com.bragasaude.data.remote.repository

import android.content.Context
import br.com.bragasaude.data.local.CareAuditDao
import br.com.bragasaude.data.local.MedicationDao
import br.com.bragasaude.data.local.MedicationEntity
import br.com.bragasaude.data.local.MedicationLogDao
import br.com.bragasaude.data.remote.api.BragaApiClient
import br.com.bragasaude.data.remote.sync.SyncScheduler
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class MedicationRepositoryTest {
    private val context = mockk<Context>(relaxed = true)
    private val api = mockk<BragaApiClient>()
    private val medicationDao = mockk<MedicationDao>(relaxed = true)
    private val logDao = mockk<MedicationLogDao>(relaxed = true)
    private val auditDao = mockk<CareAuditDao>(relaxed = true)
    private val scheduler = mockk<SyncScheduler>(relaxed = true)
    private lateinit var repository: MedicationRepository

    private val medication = MedicationEntity(
        id = "med-1",
        userId = "patient-1",
        name = "Losartana 50mg",
        scheduleTimes = "08:00",
        totalUnits = 30,
        currentUnits = 10,
        confirmedWithPrescription = true
    )

    @Before
    fun setup() {
        repository = MedicationRepository(context, api, medicationDao, logDao, auditDao, scheduler)
        coEvery { medicationDao.getById("med-1") } returns medication
        coEvery { logDao.insertOnce(any()) } returns 1L
    }

    @Test
    fun `idempotency key is deterministic bounded and dose-specific`() {
        val first = MedicationRepository.idempotencyKey("patient-1", "med-1", "2026-09-23T08:00:00-03:00")
        val same = MedicationRepository.idempotencyKey("patient-1", "med-1", "2026-09-23T08:00:00-03:00")
        val other = MedicationRepository.idempotencyKey("patient-1", "med-1", "2026-09-24T08:00:00-03:00")
        assertEquals(first, same)
        assertNotEquals(first, other)
        assertTrue(first.length <= 100)
    }

    @Test
    fun `successful dose decrements local stock exactly once`() = runBlocking {
        coEvery { api.takeMedication(any(), any()) } returns BragaApiClient.TakeMedicationResult.Success

        val result = repository.takeDose("patient-1", "med-1", "08:00")

        assertTrue(result is BragaApiClient.TakeMedicationResult.Success)
        coVerify(exactly = 1) { medicationDao.decrementUnits("med-1", 1) }
        coVerify(exactly = 1) { logDao.markSynced(any()) }
        verify(exactly = 1) { scheduler.scheduleSync(any()) }
    }

    @Test
    fun `remote duplicate never decrements stock twice`() = runBlocking {
        coEvery { api.takeMedication(any(), any()) } returns BragaApiClient.TakeMedicationResult.AlreadyTaken
        coEvery { api.getMedicationStock("patient-1") } returns emptyList()
        coEvery { medicationDao.getAllSync("patient-1") } returns listOf(medication)

        val result = repository.takeDose("patient-1", "med-1", "08:00")

        assertTrue(result is BragaApiClient.TakeMedicationResult.AlreadyTaken)
        coVerify(exactly = 0) { medicationDao.decrementUnits(any(), any()) }
        coVerify(exactly = 1) { logDao.markSynced(any()) }
    }

    @Test
    fun `local duplicate does not call gateway`() = runBlocking {
        coEvery { logDao.insertOnce(any()) } returns -1L
        val result = repository.takeDose("patient-1", "med-1", "08:00")
        assertTrue(result is BragaApiClient.TakeMedicationResult.AlreadyTaken)
        coVerify(exactly = 0) { api.takeMedication(any(), any()) }
    }
}
