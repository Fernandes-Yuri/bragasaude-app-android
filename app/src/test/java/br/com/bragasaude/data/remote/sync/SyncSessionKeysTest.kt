package br.com.bragasaude.data.remote.sync

import br.com.bragasaude.data.remote.api.BragaApiClient
import br.com.bragasaude.data.remote.repository.FamilyBridgeRepository
import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class SyncSessionKeysTest {
    @Test fun failedPublicationStopsInitialRestoreBeforeOtherDownloads() = runBlocking {
        val api = mockk<BragaApiClient>(relaxed=true)
        val family = mockk<FamilyBridgeRepository>(relaxed=true)
        coEvery { family.prepareSessionKeys("u") } throws java.io.IOException("offline")
        val sync = SyncManager(api,mockk(relaxed=true),mockk(relaxed=true),mockk(relaxed=true),
            mockk(relaxed=true),mockk(relaxed=true),mockk(relaxed=true),mockk(relaxed=true),family,mockk(relaxed=true))
        assertTrue(sync.downloadAllUserData("u").isFailure)
        coVerify(exactly=1) { family.prepareSessionKeys("u") }
        coVerify(exactly=0) { api.getProfile(any()) }
    }
}
