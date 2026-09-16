package br.com.bragasaude.data.remote.ai

import br.com.bragasaude.data.local.*
import br.com.bragasaude.data.remote.api.BragaApiClient
import br.com.bragasaude.data.remote.model.*
import br.com.bragasaude.data.remote.repository.ProfileRepository
import br.com.bragasaude.data.remote.sync.SyncScheduler
import io.mockk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.Assert.*

class ProfileRepositoryRefreshTest {
    @Test fun authoritativeFlagsAreCachedWithOriginalSnapshot() = runTest {
        val api = mockk<BragaApiClient>()
        val dao = mockk<ProfileDao>(relaxed = true)
        val old = ProfileEntity(userId = "owner", pendingSync = false)
        coEvery { dao.getProfileOneShot("owner") } returns old
        coEvery { api.getProfileLookup("owner") } returns ProfileLookup.Found(
            RemoteProfile(id = "owner", basicProfileComplete = true, selfCareComplete = true))
        val repo = ProfileRepository(api, dao, mockk<SyncScheduler>(relaxed = true))
        repo.refreshProfileForLogin("owner")
        coVerify { dao.cacheRemoteProfile(match { it.basicProfileComplete && it.selfCareComplete && !it.pendingSync }, old) }
        coVerify(exactly = 0) { api.syncProfile(any()) }
    }
    @Test fun failedFetchNeverWritesLocalProfile() = runTest {
        val api = mockk<BragaApiClient>()
        val dao = mockk<ProfileDao>(relaxed = true)
        coEvery { api.getProfileLookup("owner") } returns ProfileLookup.Unavailable(503)
        val repo = ProfileRepository(api, dao, mockk(relaxed = true))
        assertEquals(ProfileLookup.Unavailable(503), repo.refreshProfileForLogin("owner"))
        coVerify(exactly = 0) { dao.cacheRemoteProfile(any(), any()) }
    }
    @Test fun cancellationIsNotConvertedToMissingProfile() = runTest {
        val api = mockk<BragaApiClient>()
        val dao = mockk<ProfileDao>(relaxed = true)
        coEvery { api.getProfileLookup("owner") } throws CancellationException()
        val repo = ProfileRepository(api, dao, mockk(relaxed = true))
        try { repo.refreshProfileForLogin("owner"); fail("Expected cancellation") }
        catch (_: CancellationException) { }
        coVerify(exactly = 0) { dao.cacheRemoteProfile(any(), any()) }
    }
}
