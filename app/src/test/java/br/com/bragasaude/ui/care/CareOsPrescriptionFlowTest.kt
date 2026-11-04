package br.com.bragasaude.ui.care

import android.net.Uri
import br.com.bragasaude.data.local.BragaDatabase
import br.com.bragasaude.data.local.DailyMetricsDao
import br.com.bragasaude.data.local.FamilyDao
import br.com.bragasaude.data.remote.api.BragaApiClient
import br.com.bragasaude.data.remote.repository.CareOsRepository
import br.com.bragasaude.data.remote.repository.MedicationRepository
import br.com.bragasaude.data.remote.repository.ProfileRepository
import br.com.bragasaude.data.remote.repository.VitalsRepository
import br.com.bragasaude.domain.CareCelebrationTts
import br.com.bragasaude.domain.PdfReportGenerator
import com.google.firebase.auth.FirebaseAuth
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CareOsPrescriptionFlowTest {

    private val medicationRepository = mockk<MedicationRepository>(relaxed = true)
    private val careOsRepository = mockk<CareOsRepository>(relaxed = true)
    private val celebrationTts = mockk<CareCelebrationTts>(relaxed = true)
    private val profileRepository = mockk<ProfileRepository>(relaxed = true)
    private val vitalsRepository = mockk<VitalsRepository>(relaxed = true)
    private val dailyMetricsDao = mockk<DailyMetricsDao>(relaxed = true)
    private val familyDao = mockk<FamilyDao>(relaxed = true)
    private val database = mockk<BragaDatabase>(relaxed = true)
    private val pdfGenerator = mockk<PdfReportGenerator>(relaxed = true)
    private val auth = mockk<FirebaseAuth>(relaxed = true)
    private val apiClient = mockk<BragaApiClient>(relaxed = true)

    private lateinit var viewModel: CareOsViewModel

    @Before
    fun setUp() {
        viewModel = CareOsViewModel(
            medicationRepository,
            careOsRepository,
            celebrationTts,
            profileRepository,
            vitalsRepository,
            dailyMetricsDao,
            familyDao,
            database,
            pdfGenerator,
            auth,
            apiClient
        )
    }

    @Test
    fun `requestBarcodeScanner e consumeBarcodeScannerRequest gerenciam abertura automatica`() {
        assertFalse(viewModel.consumeBarcodeScannerRequest())
        viewModel.requestBarcodeScanner()
        assertTrue(viewModel.consumeBarcodeScannerRequest())
        assertFalse(viewModel.consumeBarcodeScannerRequest())
    }

    @Test
    fun `setPendingPrescriptionUri e consumePendingPrescriptionUri entregam a foto ou arquivo diretamente`() {
        assertNull(viewModel.consumePendingPrescriptionUri())
        val mockUri = mockk<Uri>()
        viewModel.setPendingPrescriptionUri(mockUri)
        assertEquals(mockUri, viewModel.consumePendingPrescriptionUri())
        assertNull(viewModel.consumePendingPrescriptionUri())
    }
}
