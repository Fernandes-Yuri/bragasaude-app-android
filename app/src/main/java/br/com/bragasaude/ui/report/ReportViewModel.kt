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
import br.com.bragasaude.data.remote.sync.userIds
import java.io.File
import javax.inject.Inject
import br.com.bragasaude.util.BragaConstants

@HiltViewModel
class ReportViewModel @Inject constructor(
    private val biometryRepository: BiometryRepository,
    private val vitalsRepository: VitalsRepository,
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


    private val _pdfFile = MutableSharedFlow<File?>()
    val pdfFile = _pdfFile.asSharedFlow()

    private val _isGeneratingPdf = MutableStateFlow(false)
    val isGeneratingPdf = _isGeneratingPdf.asStateFlow()

    private val _pdfErrorMessage = MutableSharedFlow<String>()
    val pdfErrorMessage = _pdfErrorMessage.asSharedFlow()

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val recordedDays = auth.userIds().flatMapLatest { owner ->
        combine(vitalsRepository.getVitalSigns(owner), dailyMetricsDao.getRecent30Days(owner),
            biometryRepository.getBiometry(owner)) { vitals, metrics, biometry ->
            val today = java.time.LocalDate.now()
            val start = today.minusDays(29)
            val dates = vitals.map { it.measuredAt.toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDate() } +
                metrics.mapNotNull { runCatching { java.time.LocalDate.parse(it.date) }.getOrNull() } +
                biometry.map { it.measuredAt.toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDate() }
            dates.filter { !it.isBefore(start) && !it.isAfter(today) }.toSet().size
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    init {
        viewModelScope.launch {
            auth.userIds().collectLatest { userId ->
                _latestBiometry.value = null
                _vitalsHistory.value = emptyList()
                kotlinx.coroutines.coroutineScope {
                    launch { biometryRepository.getBiometry(userId).collectLatest { _latestBiometry.value = it.firstOrNull()?.toRemote() } }
                    launch { vitalsRepository.getVitalSigns(userId).collectLatest { _vitalsHistory.value = it.map { e -> e.toRemote() }.sortedBy { e -> e.measuredAt } } }
                }
            }
        }
    }

    fun generatePdfReport() {
        if (_isGeneratingPdf.value) return
        _isGeneratingPdf.value = true
        val userId = auth.currentUser?.uid ?: BragaConstants.GUEST_UID
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
                if ((auth.currentUser?.uid ?: BragaConstants.GUEST_UID) == userId) _pdfFile.emit(file) else file?.delete()
            } catch (e: Exception) {
                e.printStackTrace()
                _pdfErrorMessage.emit("Erro ao gerar PDF: ${e.localizedMessage}")
            } finally {
                _isGeneratingPdf.value = false
            }
        }
    }
}
