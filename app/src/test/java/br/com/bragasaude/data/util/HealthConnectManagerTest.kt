package br.com.bragasaude.data.util

import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.OxygenSaturationRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import br.com.bragasaude.data.local.*
import br.com.bragasaude.data.remote.sync.SyncScheduler
import com.google.firebase.auth.FirebaseAuth
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class HealthConnectManagerTest {
    private val access = mockk<HealthConnectAccess>()
    private val client = mockk<HealthConnectClient>(relaxed = true)
    private val database = mockk<BragaDatabase>()
    private val dao = mockk<WearableReadingDao>(relaxed = true)
    private val dailyDao = mockk<DailyMetricsDao>(relaxed = true)
    private val auth = mockk<FirebaseAuth>(relaxed = true)
    private val scheduler = mockk<SyncScheduler>(relaxed = true)
    private fun manager(): HealthConnectManager {
        every { access.availability() } returns HealthConnectClient.SDK_AVAILABLE
        every { access.client() } returns client
        every { auth.currentUser?.uid } returns "user-a"
        every { database.wearableReadingDao() } returns dao
        return HealthConnectManager(access, database, dailyDao, auth, scheduler)
    }

    @Test fun `no permission does not read or write health records`() = runTest {
        val manager = manager()
        coEvery { client.permissionController.getGrantedPermissions() } returns emptySet()
        assertFalse(manager.syncHealthConnectData(force = true))
        coVerify(exactly = 0) { dao.upsert(any()) }
        assertFalse(manager.syncState.value.running)
    }

    @Test fun `oxygen only permission imports all pages without requiring heart rate`() = runTest {
        val manager = manager()
        coEvery { client.permissionController.getGrantedPermissions() } returns setOf(manager.oxygenPermission)
        val sample = mockk<OxygenSaturationRecord>(relaxed = true)
        every { sample.metadata.id } returns "record-1"
        every { sample.metadata.dataOrigin.packageName } returns "watch.app"
        every { sample.metadata.device?.model } returns "Watch"
        every { sample.time } returns Instant.parse("2026-09-09T01:00:00Z")
        every { sample.percentage.value } returns 98.0
        coEvery { client.readRecords(any<ReadRecordsRequest<OxygenSaturationRecord>>()) } returnsMany listOf(
            mockk { every { records } returns listOf(sample); every { pageToken } returns "next-page" },
            mockk { every { records } returns emptyList(); every { pageToken } returns null }
        )
        assertTrue(manager.checkHasPermissions())
        assertTrue(manager.syncHealthConnectData(force = true))
        coVerify(exactly = 2) { client.readRecords(any<ReadRecordsRequest<OxygenSaturationRecord>>()) }
        coVerify { dao.upsert(match { rows -> rows.size == 1 && rows[0].userId == "user-a" && rows[0].value == 98.0 && rows[0].sourcePackage == "watch.app" && rows[0].recordKey == "oxygen:record-1" }) }
        coVerify(exactly = 0) { dailyDao.mergeDeviceSteps(any(), any(), any()) }
    }

    @Test fun `revoked permission reports recoverable error and keeps saved readings`() = runTest {
        val manager = manager()
        coEvery { client.permissionController.getGrantedPermissions() } throws SecurityException()
        assertFalse(manager.syncHealthConnectData(force = true))
        assertTrue(manager.syncState.value.message.contains("permissões"))
        coVerify(exactly = 0) { dao.upsert(any()) }
    }

    @Test fun `provider becomes available after installation without recreating manager`() = runTest {
        val manager = manager()
        every { access.availability() } returnsMany listOf(HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED, HealthConnectClient.SDK_AVAILABLE)
        assertFalse(manager.isAvailable())
        assertTrue(manager.isAvailable())
    }
}
