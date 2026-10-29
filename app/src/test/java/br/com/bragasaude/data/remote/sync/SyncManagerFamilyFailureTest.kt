package br.com.bragasaude.data.remote.sync

import android.util.Log
import br.com.bragasaude.data.local.*
import br.com.bragasaude.data.remote.api.BragaApiClient
import br.com.bragasaude.data.remote.repository.FamilyBridgeRepository
import br.com.bragasaude.data.util.MovementManager
import io.mockk.*
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import javax.inject.Provider

class SyncManagerFamilyFailureTest {
    @Test fun `family failure keeps global sync retryable and recovery records success`() = runBlocking {
        mockkStatic(Log::class)
        try {
            every { Log.d(any(), any<String>()) } returns 0
            every { Log.w(any(), any<String>()) } returns 0
            every { Log.e(any(), any<String>()) } returns 0
            val api = mockk<BragaApiClient>(relaxed = true)
            val prefs = mockk<SyncPreferences>(relaxed = true)
            val family = mockk<FamilyBridgeRepository>(relaxed = true)
            val movement = mockk<MovementManager>(relaxed = true)
            val provider = mockk<Provider<MovementManager>>()
            every { provider.get() } returns movement
            coEvery { api.getProfile(any()) } returns null
            coEvery { api.getVitalSigns(any()) } returns emptyList()
            coEvery { api.getBiometry(any()) } returns emptyList()
            coEvery { api.getDailyMetrics(any(), any()) } returns emptyList()
            every { family.getActiveBindingsForCaregiver(any()) } returns flowOf(emptyList())
            coEvery { family.syncBindingsForPatient(any()) } returnsMany listOf(false, true)
            coEvery { family.syncBindingsForCaregiver(any()) } returns true
            val manager = SyncManager(api, mockk(relaxed = true), mockk(relaxed = true),
                mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true),
                mockk(relaxed = true), prefs, family, provider)
            assertTrue(manager.downloadIncrementalUserData("user", true).isFailure)
            assertTrue(manager.syncState.value is SyncState.Error)
            verify(exactly = 0) { prefs.recordSyncSuccess("user") }
            assertTrue(manager.downloadIncrementalUserData("user", true).isSuccess)
            verify(exactly = 1) { prefs.recordSyncSuccess("user") }
        } finally { unmockkStatic(Log::class) }
    }
}
