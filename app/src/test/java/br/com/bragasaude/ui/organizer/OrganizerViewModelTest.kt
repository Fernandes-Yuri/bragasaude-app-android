package br.com.bragasaude.ui.organizer

import android.net.Uri
import br.com.bragasaude.data.local.organizer.BatchImportProgress
import br.com.bragasaude.data.local.organizer.OcrAvailability
import br.com.bragasaude.data.local.organizer.OcrCapability
import br.com.bragasaude.data.local.organizer.OrganizerDocument
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
            viewModel.probeDispatcher = StandardTestDispatcher(testScheduler)
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

    @Test fun `divergencia de pacientes exige confirmacao antes de gerar e aceite retoma fluxo`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val session = patientSession("Joaquim Ficticio Testes", "Heloisa Ficticia Exemplar")
            val store = mockk<OrganizerStore>(relaxed = true)
            val capability = mockk<OcrCapability>()
            every { capability.probe() } returns OcrAvailability.Ready
            coEvery { store.restore() } returns session
            val viewModel = OrganizerViewModel(store, capability)
            advanceUntilIdle()

            viewModel.generate()
            advanceUntilIdle()

            assertEquals(
                listOf("Heloisa Ficticia Exemplar", "Joaquim Ficticio Testes"),
                viewModel.state.value.patientWarningNames
            )
            coVerify(exactly = 0) { store.generate(any(), any(), any()) }

            viewModel.dismissPatientWarning()
            assertTrue(viewModel.state.value.patientWarningNames.isEmpty())
            viewModel.generate()
            advanceUntilIdle()
            assertTrue(viewModel.state.value.patientWarningNames.isNotEmpty())

            viewModel.acceptPatientDivergence()
            advanceUntilIdle()
            assertTrue(viewModel.state.value.patientWarningNames.isEmpty())
            assertTrue(viewModel.state.value.ready)
            coVerify(exactly = 1) { store.generate("s", true, any()) }
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test fun `lote com um arquivo usa apenas progresso inline`() = verifyInlineBatch(1)

    @Test fun `lote com dois arquivos usa apenas progresso inline`() = verifyInlineBatch(2)

    @Test fun `lote com tres ou mais arquivos expoe progresso modal e limpa ao concluir`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val session = OrganizerSession("s", System.currentTimeMillis())
            val store = mockk<OrganizerStore>(relaxed = true)
            val capability = mockk<OcrCapability>()
            val observed = mutableListOf<BatchImportProgress?>()
            val expected = BatchImportProgress(3, 5, "exame-3.pdf", "Classificando setor clínico...")
            every { capability.probe() } returns OcrAvailability.Ready
            coEvery { store.restore() } returns session
            lateinit var viewModel: OrganizerViewModel
            coEvery { store.import("s", any(), any(), any()) } answers {
                val callback = arg<(BatchImportProgress) -> Unit>(3)
                callback(expected)
                observed += viewModel.state.value.batchProgress
                session to emptyList()
            }
            viewModel = OrganizerViewModel(store, capability)
            advanceUntilIdle()

            viewModel.import(List(5) { mockk<Uri>(relaxed = true) })
            advanceUntilIdle()

            assertEquals(listOf(expected), observed)
            assertEquals(0.6f, observed.single()?.progressFraction)
            assertEquals(null, viewModel.state.value.batchProgress)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test fun `mesmo paciente normalizado gera sem aviso`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val session = patientSession("José da Silva", "jose da silva")
            val store = mockk<OrganizerStore>(relaxed = true)
            val capability = mockk<OcrCapability>()
            every { capability.probe() } returns OcrAvailability.Ready
            coEvery { store.restore() } returns session
            val viewModel = OrganizerViewModel(store, capability)
            advanceUntilIdle()

            viewModel.generate()
            advanceUntilIdle()

            assertTrue(viewModel.state.value.patientWarningNames.isEmpty())
            assertTrue(viewModel.state.value.ready)
            coVerify(exactly = 1) { store.generate("s", true, any()) }
        } finally {
            Dispatchers.resetMain()
        }
    }

    private fun verifyInlineBatch(fileCount: Int) = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val session = OrganizerSession("s", System.currentTimeMillis())
            val store = mockk<OrganizerStore>(relaxed = true)
            val capability = mockk<OcrCapability>()
            val observed = mutableListOf<OrganizerUiState>()
            every { capability.probe() } returns OcrAvailability.Ready
            coEvery { store.restore() } returns session
            lateinit var viewModel: OrganizerViewModel
            coEvery { store.import("s", any(), any(), any()) } answers {
                val callback = arg<(BatchImportProgress) -> Unit>(3)
                callback(BatchImportProgress(1, fileCount, "exame.pdf", "Lendo documento..."))
                observed += viewModel.state.value
                session to emptyList()
            }
            viewModel = OrganizerViewModel(store, capability)
            advanceUntilIdle()

            viewModel.import(List(fileCount) { mockk<Uri>(relaxed = true) })
            advanceUntilIdle()

            assertEquals(1, observed.size)
            assertEquals(null, observed.single().batchProgress)
            assertTrue(observed.single().progressMessage?.contains("exame.pdf") == true)
            assertEquals(null, viewModel.state.value.batchProgress)
        } finally {
            Dispatchers.resetMain()
        }
    }

    private fun patientSession(vararg names: String): OrganizerSession = OrganizerSession(
        id = "s",
        createdAt = System.currentTimeMillis(),
        documents = names.mapIndexed { index, name ->
            OrganizerDocument(
                id = "d$index",
                title = "Exame $index",
                pages = 1,
                confirmed = true,
                patientNames = listOf(name)
            )
        }
    )
}
