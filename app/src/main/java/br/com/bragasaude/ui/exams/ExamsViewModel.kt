package br.com.bragasaude.ui.exams

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.bragasaude.R
import br.com.bragasaude.data.remote.model.RemoteExam
import br.com.bragasaude.data.remote.model.RemoteExamItem
import br.com.bragasaude.data.remote.repository.ExamsRepository
import br.com.bragasaude.data.util.ExamExtractor
import br.com.bragasaude.data.util.toRemote
import br.com.bragasaude.domain.HealthEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import br.com.bragasaude.data.remote.sync.SyncManager
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import br.com.bragasaude.util.BragaConstants

@HiltViewModel
class ExamsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: ExamsRepository,
    private val examExtractor: ExamExtractor,
    private val syncManager: SyncManager,
    private val healthEngine: HealthEngine,
    private val profileRepository: br.com.bragasaude.data.remote.repository.ProfileRepository,
    private val auth: FirebaseAuth
) : ViewModel() {

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing = _isRefreshing.asStateFlow()

    private val _showGlucosePrompt = MutableStateFlow(false)
    val showGlucosePrompt = _showGlucosePrompt.asStateFlow()

    private val _exams = MutableStateFlow<List<RemoteExam>>(emptyList())
    val exams = _exams.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _uploadProgress = MutableStateFlow<Float?>(null)
    val uploadProgress = _uploadProgress.asStateFlow()

    private val _pendingExamValidation = MutableStateFlow<Pair<RemoteExam, List<RemoteExamItem>>?>(null)
    val pendingExamValidation = _pendingExamValidation.asStateFlow()

    // FASE 3: Governança LGPD e Consentimento de Nuvem
    private val _showCloudConsentDialog = MutableStateFlow(false)
    val showCloudConsentDialog = _showCloudConsentDialog.asStateFlow()

    private var pendingConsentCallback: ((Boolean) -> Unit)? = null

    // FASE 4: Dossiê Médico Dinâmico
    private val _isCompilingDossier = MutableStateFlow(false)
    val isCompilingDossier = _isCompilingDossier.asStateFlow()

    private val _compiledDossierResult = MutableStateFlow<br.com.bragasaude.domain.MedicalDossierCompiler.CompilationResult?>(null)
    val compiledDossierResult = _compiledDossierResult.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage = _statusMessage.asStateFlow()

    init {
        val userId = auth.currentUser?.uid ?: BragaConstants.GUEST_UID
        viewModelScope.launch {
            repository.getExams(userId).collectLatest { entities ->
                _exams.value = entities.map { it.toRemote() }
            }
        }
    }

    fun promptCloudConsent(onDecision: (Boolean) -> Unit) {
        pendingConsentCallback = onDecision
        _showCloudConsentDialog.value = true
    }

    fun onCloudConsentDecision(acceptedCloud: Boolean) {
        _showCloudConsentDialog.value = false
        pendingConsentCallback?.invoke(acceptedCloud)
        pendingConsentCallback = null
    }

    fun onCloudConsentDismissed() {
        _showCloudConsentDialog.value = false
        pendingConsentCallback = null
    }

    fun deleteExamAtomically(examId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val userId = auth.currentUser?.uid ?: BragaConstants.GUEST_UID
                val success = repository.deleteExamAtomically(examId, userId)
                if (success) {
                    _statusMessage.value = "Exame excluído com sucesso (LGPD Art. 18)."
                } else {
                    _statusMessage.value = "Exame excluído localmente."
                }
            } catch (e: Exception) {
                _statusMessage.value = "Falha ao excluir exame: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun compileMedicalDossier() {
        viewModelScope.launch {
            _isCompilingDossier.value = true
            try {
                val userId = auth.currentUser?.uid ?: BragaConstants.GUEST_UID
                val profile = profileRepository.getProfile(userId).firstOrNull()?.toRemote() 
                    ?: br.com.bragasaude.data.remote.model.RemoteProfile(id = userId, fullName = "Paciente")

                val examEntities = repository.getExams(userId).firstOrNull() ?: emptyList()
                val itemEntities = repository.getExamItems(userId).firstOrNull() ?: emptyList()

                val compiler = br.com.bragasaude.domain.MedicalDossierCompiler(context)
                val result = compiler.compileDossier(profile, examEntities, itemEntities)
                _compiledDossierResult.value = result
            } catch (e: Exception) {
                android.util.Log.e("ExamsViewModel", "Erro ao compilar dossiê: ${e.message}", e)
                _statusMessage.value = "Erro ao compilar dossiê em PDF: ${e.message}"
            } finally {
                _isCompilingDossier.value = false
            }
        }
    }

    fun clearCompiledDossier() {
        _compiledDossierResult.value = null
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }

    fun onValidationConfirmed(exam: RemoteExam, items: List<RemoteExamItem>) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val userId = auth.currentUser?.uid ?: BragaConstants.GUEST_UID
                val confirmedExam = exam.copy(status = "confirmed")
                val confirmedItems = items.map {
                    it.copy(
                        status = "confirmed",
                        userId = userId,
                        examId = confirmedExam.id ?: it.examId
                    )
                }
                repository.saveExam(confirmedExam, confirmedItems)

                // ANALISA OS ITENS (CÉREBRO)
                healthEngine.analyzeExamItems(userId, confirmedItems)
                checkGlucoseItems(confirmedItems, userId)

                _pendingExamValidation.value = null
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Salva exames inseridos diretamente pelo usuário via formulário estruturado.
     * Gravação imediata no prontuário com status 'confirmed'.
     */
    fun saveManualExam(title: String, category: String, examDate: String, items: List<RemoteExamItem>) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val userId = auth.currentUser?.uid ?: BragaConstants.GUEST_UID
                val examId = UUID.randomUUID().toString()
                val exam = RemoteExam(
                    id = examId,
                    userId = userId,
                    title = title,
                    category = category,
                    examDate = examDate,
                    status = "confirmed"
                )
                val confirmedItems = items.map {
                    it.copy(
                        id = it.id ?: UUID.randomUUID().toString(),
                        examId = examId,
                        userId = userId,
                        status = "confirmed"
                    )
                }
                repository.saveExam(exam, confirmedItems)
                healthEngine.analyzeExamItems(userId, confirmedItems)
                checkGlucoseItems(confirmedItems, userId)
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Processa páginas consecutivas capturadas com a câmera on-device (1 a 5 páginas).
     * Extrai OCR via ML Kit, higieniza com LGPD e abre tela de conferência humana obrigatória.
     */
    fun processCapturedPages(title: String, category: String, date: Date, pages: List<android.graphics.Bitmap>) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val userId = auth.currentUser?.uid ?: BragaConstants.GUEST_UID
                val examId = UUID.randomUUID().toString()
                val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                val dateStr = dateFormat.format(date)

                val rawText = examExtractor.extractTextFromBitmaps(pages)
                val sanitizedText = examExtractor.sanitizeDocumentText(rawText)
                val extractedItems = examExtractor.parseToExamItems(sanitizedText, userId, examId)

                val exam = RemoteExam(
                    id = examId,
                    userId = userId,
                    title = title,
                    category = category,
                    examDate = dateStr,
                    status = "analyzed"
                )

                _pendingExamValidation.value = Pair(exam, extractedItems)
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Processa arquivo anexado (PDF ou Imagem da galeria) com extração OCR e triagem.
     */
    fun processAttachedFile(title: String, category: String, date: Date, fileUri: Uri, fileName: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _uploadProgress.value = 0.2f
            try {
                val userId = auth.currentUser?.uid ?: BragaConstants.GUEST_UID
                val examId = UUID.randomUUID().toString()
                val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                val dateStr = dateFormat.format(date)

                val mimeType = context.contentResolver.getType(fileUri)
                val isPdf = mimeType?.contains("pdf", true) == true || fileName.endsWith(".pdf", true)

                val rawText = if (isPdf) {
                    examExtractor.extractTextFromPdf(fileUri)
                } else {
                    examExtractor.extractTextFromImageUri(fileUri)
                }

                val sanitizedText = examExtractor.sanitizeDocumentText(rawText)
                val extractedItems = examExtractor.parseToExamItems(sanitizedText, userId, examId)

                _uploadProgress.value = 0.6f
                var fileUrl: String? = null
                try {
                    val fileBytes = context.contentResolver.openInputStream(fileUri)?.use { it.readBytes() }
                    if (fileBytes != null) {
                        fileUrl = repository.uploadExamFile(userId, fileName, fileBytes)
                    }
                } catch (uploadEx: Exception) {
                    uploadEx.printStackTrace()
                }

                val exam = RemoteExam(
                    id = examId,
                    userId = userId,
                    title = title,
                    category = category,
                    examDate = dateStr,
                    fileUrl = fileUrl,
                    status = "analyzed"
                )

                _pendingExamValidation.value = Pair(exam, extractedItems)
                _uploadProgress.value = 1f
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isLoading.value = false
                _uploadProgress.value = null
            }
        }
    }

    /**
     * O usuário conferiu os valores extraídos pela IA e confirma a transcrição.
     * Muda o status do exame e de cada item de "analyzed" para "confirmed".
     */
    fun confirmExam(itemId: String?) {
        if (itemId == null) return
        viewModelScope.launch {
            _isLoading.value = true
            try {
                repository.confirmExamItem(itemId)
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Confirma TODOS os itens de um exame analisado em lote.
     */
    fun confirmAllExamItems(examId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                repository.confirmAllItemsForExam(examId)
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun onValidationCancelled() {
        _pendingExamValidation.value = null
    }

    fun uploadAndSaveExam(title: String, category: String, date: Date, fileUri: Uri?, fileName: String?) {
        android.util.Log.d("ExamsViewModel", "uploadAndSaveExam: $title, uri: $fileUri, file: $fileName")
        viewModelScope.launch {
            _isLoading.value = true
            _uploadProgress.value = 0f
            try {
                val userId = auth.currentUser?.uid ?: BragaConstants.GUEST_UID
                val examId = UUID.randomUUID().toString()
                
                var fileUrl: String? = null
                var extractedItems = emptyList<RemoteExamItem>()

                if (fileUri != null && fileName != null) {
                    _uploadProgress.value = 0.2f
                    val mimeType = context.contentResolver.getType(fileUri)
                    val isPdf = mimeType?.contains("pdf", true) == true || fileName.endsWith(".pdf", true)

                    // DECISOES.md (D1): apenas PDF no fluxo de exames. Defesa em profundidade
                    // (a UI já restringe o picker); qualquer não-PDF é rejeitado aqui.
                    if (!isPdf) {
                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                            android.widget.Toast.makeText(context, context.getString(R.string.exam_pdf_only_error), android.widget.Toast.LENGTH_LONG).show()
                        }
                        return@launch
                    }

                    android.util.Log.d("ExamsViewModel", "Extracting text. isPdf: $isPdf")

                    val rawText = examExtractor.extractTextFromPdf(fileUri)
                    android.util.Log.d("ExamsViewModel", "Raw text length: ${rawText.length}")

                    // Trava 1: Rejeição de PDF não textual (Scans/Fotos/Imagens convertidas em PDF)
                    if (rawText.isBlank() || rawText.trim().length < 50) {
                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                            android.widget.Toast.makeText(
                                context,
                                "Este PDF não possui texto digital legível (parece ser uma foto ou imagem escaneada). Por favor, anexe o laudo original em PDF fornecido pelo laboratório.",
                                android.widget.Toast.LENGTH_LONG
                            ).show()
                        }
                        return@launch
                    }

                    extractedItems = examExtractor.parseToExamItems(rawText, userId, examId)
                    android.util.Log.d("ExamsViewModel", "Extracted items count: ${extractedItems.size}")

                    // Trava 2: Rejeição de PDFs sem parâmetros clínicos reconhecidos
                    if (extractedItems.isEmpty()) {
                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                            android.widget.Toast.makeText(
                                context,
                                "Nenhum parâmetro de exame laboratorial reconhecido no documento. Certifique-se de anexar um laudo válido.",
                                android.widget.Toast.LENGTH_LONG
                            ).show()
                        }
                        return@launch
                    }

                    checkGlucoseItems(extractedItems, userId)

                    _uploadProgress.value = 0.5f
                    val fileBytes = context.contentResolver.openInputStream(fileUri)?.use { it.readBytes() }
                    if (fileBytes != null) {
                        fileUrl = repository.uploadExamFile(userId, fileName, fileBytes)
                    }
                }

                val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                val exam = RemoteExam(
                    id = examId,
                    userId = userId,
                    title = title,
                    category = category,
                    examDate = dateFormat.format(date),
                    fileUrl = fileUrl,
                    // Fluxo de status: "uploaded" → "analyzed" (IA extrai) → "confirmed" (usuário confere)
                    status = if (fileUrl != null) "uploaded" else "confirmed"
                )
                
                if (fileUrl != null) {
                    _pendingExamValidation.value = Pair(exam, extractedItems)
                } else {
                    repository.saveExam(exam)
                }
                
                _uploadProgress.value = 1f
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isLoading.value = false
                _uploadProgress.value = null
            }
        }
    }

    fun refresh() {
        val userId = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                syncManager.syncUserData(userId, force = true)
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    /**
     * Se o exame extraído contém parâmetros de glicose (glicose em jejum, HbA1c)
     * e o usuário não tem diabetes marcado no perfil, oferecer a ativação do
     * monitoramento de glicose.
     */
    private fun checkGlucoseItems(items: List<RemoteExamItem>, userId: String) {
        viewModelScope.launch {
            try {
                val glucoseKeys = setOf("glucose", "hba1c")
                val hasGlucoseItem = items.any { it.itemKey in glucoseKeys }
                if (!hasGlucoseItem) return@launch

                val profile = profileRepository.getProfile(userId).firstOrNull()?.toRemote() ?: return@launch
                if (!profile.hasDiabetes) {
                    _showGlucosePrompt.value = true
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun dismissGlucosePrompt() {
        _showGlucosePrompt.value = false
    }

    fun enableDiabetesMonitoring() {
        viewModelScope.launch {
            try {
                val userId = auth.currentUser?.uid ?: return@launch
                val current = profileRepository.getProfile(userId).firstOrNull()
                val profile = (current?.toRemote() ?: br.com.bragasaude.data.remote.model.RemoteProfile(id = userId))
                    .copy(hasDiabetes = true)
                profileRepository.saveProfile(profile)
                _showGlucosePrompt.value = false
            } catch (e: Exception) {
                e.printStackTrace()
                _showGlucosePrompt.value = false
            }
        }
    }
}
