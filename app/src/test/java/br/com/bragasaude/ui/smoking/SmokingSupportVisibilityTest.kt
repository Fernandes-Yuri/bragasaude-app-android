package br.com.bragasaude.ui.smoking

import androidx.lifecycle.ViewModelStore
import br.com.bragasaude.data.local.BragaDatabase
import br.com.bragasaude.data.local.ProfileEntity
import br.com.bragasaude.data.local.smoking.*
import br.com.bragasaude.domain.smoking.PreparationStage
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import java.time.Clock
import java.time.ZoneId
import javax.inject.Provider

@OptIn(ExperimentalCoroutinesApi::class)
class SmokingSupportVisibilityTest {
    @Test fun entryFollowsSmokerProfileAndPreservesExistingPlanAfterQuitting() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = ViewModelStore()
        try {
            val snapshot = MutableStateFlow(SmokingSupportSnapshot(ownerId = "owner", loaded = true))
            val profile = MutableStateFlow<ProfileEntity?>(ProfileEntity(userId = "owner", isSmoker = false))
            val repository = mockk<SmokingSupportRepository>(relaxed = true)
            every { repository.snapshot } returns snapshot
            every { repository.offerRequest } returns MutableStateFlow(null)
            val session = mockk<SmokingSupportSession>(relaxed = true)
            every { session.owners } returns MutableStateFlow("owner")
            val database = mockk<BragaDatabase>()
            every { database.profileDao().getProfile("owner") } returns profile
            val vm = SmokingSupportViewModel(repository, session, database, Clock.systemUTC(), Provider { ZoneId.of("UTC") })
            store.put("smoking", vm)
            runCurrent()
            assertFalse(vm.entryVisible.value)
            profile.value = profile.value!!.copy(isSmoker = true)
            runCurrent()
            assertTrue(vm.entryVisible.value)
            profile.value = profile.value!!.copy(isSmoker = false)
            snapshot.value = snapshot.value.copy(plan = SmokingSupportPlan("owner", "2026-10-27",
                stage = PreparationStage.STARTED, createdAt = 1, updatedAt = 1))
            runCurrent()
            assertTrue(vm.entryVisible.value)
            profile.value = ProfileEntity(userId = "other", isSmoker = true)
            runCurrent()
            assertFalse(vm.entryVisible.value)
        } finally {
            store.clear()
            runCurrent()
            Dispatchers.resetMain()
        }
    }
}
