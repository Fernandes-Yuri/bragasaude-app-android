package br.com.bragasaude.ui.organizer

import br.com.bragasaude.data.local.organizer.OcrAvailability
import br.com.bragasaude.data.local.organizer.OcrCapability
import br.com.bragasaude.data.local.organizer.OrganizerSession
import br.com.bragasaude.data.local.organizer.OrganizerStore
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OrganizerViewModelTest {
    @Test fun `ocr indisponivel fica no estado e a sessao segue funcionando`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val session = OrganizerSession("s", System.currentTimeMillis())
            val store = mockk<OrganizerStore>(relaxed = true)
            val capability = mockk<OcrCapability>()
            every { capability.probe() } returns OcrAvailability.Unavailable
            coEvery { store.restore() } returns session
            val viewModel = OrganizerViewModel(store, capability)
            viewModel.probeDispatcher = StandardTestDispatcher(testScheduler)
            viewModel.probeOcr()
            advanceUntilIdle()
            assertEquals(OcrAvailability.Unavailable, viewModel.state.value.ocrAvailability)
            assertEquals(session, viewModel.state.value.session)
            coVerify(exactly = 1) { store.restore() }
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test fun `ocr precisando de atualizacao e sinalizado sem derrubar a area`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val store = mockk<OrganizerStore>(relaxed = true)
            val capability = mockk<OcrCapability>()
            every { capability.probe() } returns OcrAvailability.NeedsUpdate(null)
            coEvery { store.restore() } returns null
            val viewModel = OrganizerViewModel(store, capability)
            viewModel.probeDispatcher = StandardTestDispatcher(testScheduler)
            viewModel.probeOcr()
            advanceUntilIdle()
            val availability = viewModel.state.value.ocrAvailability
            assertTrue(availability is OcrAvailability.NeedsUpdate)
            assertEquals(null, viewModel.state.value.session)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test fun `chamadas consecutivas de refresh sao throttled para evitar restauracao redundante`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val session = OrganizerSession("s", System.currentTimeMillis())
            val store = mockk<OrganizerStore>(relaxed = true)
            val capability = mockk<OcrCapability>()
            every { capability.probe() } returns OcrAvailability.Ready
            coEvery { store.restore() } returns session
            val viewModel = OrganizerViewModel(store, capability)
            advanceUntilIdle()
            coVerify(exactly = 1) { store.restore() }

            viewModel.refresh()
            advanceUntilIdle()
            coVerify(exactly = 1) { store.restore() }

            viewModel.refresh(force = true)
            advanceUntilIdle()
            coVerify(exactly = 2) { store.restore() }
        } finally {
            Dispatchers.resetMain()
        }
    }
}
