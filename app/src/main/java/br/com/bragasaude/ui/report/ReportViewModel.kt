package br.com.bragasaude.ui.report

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.bragasaude.data.local.DailyMetricsDao
import br.com.bragasaude.data.remote.model.*
import br.com.bragasaude.data.remote.repository.*
import br.com.bragasaude.data.util.toRemote
import br.com.bragasaude.domain.PdfReportGenerator
import dagger.hilt.android.lifecycle.HiltViewModel
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class ReportViewModel @Inject constructor(
    private val biometryRepository: BiometryRepository,
    private val vitalsRepository: VitalsRepository,
    private val examsRepository: ExamsRepository,
    private val profileRepository: ProfileRepository,
    private val dailyMetricsDao: DailyMetricsDao,
    private val database: br.com.bragasaude.data.local.BragaDatabase,
    private val auth: FirebaseAuth,
    private val pdfGenerator: PdfReportGenerator
) : ViewModel() {

    private val _latestBiometry = MutableStateFlow<RemoteBiometry?>(null)
    val latestBiometry = _latestBiometry.asStateFlow()

    private val _vitalsHistory = MutableStateFlow<List<RemoteVitalSign>>(emptyList())
    val vitalsHistory = _vitalsHistory.asStateFlow()

    private val _examItemsHistory = MutableStateFlow<List<RemoteExamItem>>(emptyList())
    val examItemsHistory = _examItemsHistory.asStateFlow()

    private val _pdfFile = MutableSharedFlow<File?>()
    val pdfFile = _pdfFile.asSharedFlow()

    private val _isGeneratingPdf = MutableStateFlow(false)
    val isGeneratingPdf = _isGeneratingPdf.asStateFlow()

    private val _pdfErrorMessage = MutableSharedFlow<String>()
    val pdfErrorMessage = _pdfErrorMessage.asSharedFlow()

    init {
        val userId = auth.currentUser?.uid ?: "00000000-0000-0000-0000-000000000000"
        
        // Observe Biometry
        viewModelScope.launch {
            biometryRepository.getBiometry(userId).collectLatest { entities ->
                _latestBiometry.value = entities.firstOrNull()?.toRemote()
            }
        }

        // Observe Daily Vitals (for charts)
        viewModelScope.launch {
            vitalsRepository.getVitalSigns(userId).collectLatest { entities ->
                _vitalsHistory.value = entities.map { it.toRemote() }.sortedBy { it.measuredAt }
            }
        }

        // Observe Routine Lab Tests (for charts)
        viewModelScope.launch {
            examsRepository.getExamItems(userId).collectLatest { entities ->
                _examItemsHistory.value = entities.map { it.toRemote() }.sortedBy { it.measuredAt }
            }
        }
    }

    fun generatePdfReport() {
        val userId = auth.currentUser?.uid ?: "00000000-0000-0000-0000-000000000000"
        viewModelScope.launch {
            _isGeneratingPdf.value = true
            try {
                val currentProfileEntity = profileRepository.getProfile(userId).firstOrNull()
                val profile = currentProfileEntity?.toRemote() ?: RemoteProfile(id = userId, fullName = "Visitante")
                
                val allVitals = vitalsRepository.getVitalSignsSync(userId)
                val dailyMetrics = dailyMetricsDao.getRecent30Days(userId).firstOrNull() ?: emptyList()
                
                val wearableReadings = database.wearableReadingDao().getForReport(userId, System.currentTimeMillis() - 30L * 86400_000L)
                val file = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    pdfGenerator.generateReport(profile, allVitals, dailyMetrics, wearableReadings)
                }
                _pdfFile.emit(file)
            } catch (e: Exception) {
                e.printStackTrace()
                _pdfErrorMessage.emit("Erro ao gerar PDF: ${e.localizedMessage}")
            } finally {
                _isGeneratingPdf.value = false
            }
        }
    }
}
