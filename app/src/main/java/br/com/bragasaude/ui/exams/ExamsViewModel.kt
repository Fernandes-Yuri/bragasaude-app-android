package br.com.bragasaude.ui.exams

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.bragasaude.data.remote.model.RemoteExam
import br.com.bragasaude.data.remote.model.RemoteExamItem
import br.com.bragasaude.data.remote.repository.ExamsRepository
import br.com.bragasaude.data.util.ExamExtractor
import br.com.bragasaude.data.util.toRemote
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
    private val profileRepository: br.com.bragasaude.data.remote.repository.ProfileRepository,
    private val fileStore: br.com.bragasaude.data.local.security.ExamFileStore,
    private val auth: FirebaseAuth
) : ViewModel() {

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing = _isRefreshing.asStateFlow()


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
    private var draftOriginal: String? = null
    private val _examItems = MutableStateFlow<List<RemoteExamItem>>(emptyList())
    val examItems = _examItems.asStateFlow()
    private val _originalToOpen = MutableStateFlow<java.io.File?>(null)
    val originalToOpen = _originalToOpen.asStateFlow()
    private val _shareDossier = MutableStateFlow(false)
    val shareDossier = _shareDossier.asStateFlow()
    private val _manualExamSaved = MutableStateFlow(false)
    val manualExamSaved = _manualExamSaved.asStateFlow()
    fun clearManualExamSaved() { _manualExamSaved.value = false }

    // FASE 4: Nuvem de Exames Dinâmico
    private val _isCompilingDossier = MutableStateFlow(false)
    val isCompilingDossier = _isCompilingDossier.asStateFlow()

    private val _compiledDossierResult = MutableStateFlow<br.com.bragasaude.domain.MedicalDossierCompiler.CompilationResult?>(null)
    val compiledDossierResult = _compiledDossierResult.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage = _statusMessage.asStateFlow()

    init {
        val userId = auth.currentUser?.uid ?: BragaConstants.GUEST_UID
        viewModelScope.launch {
            repository.getExamItems(userId).collectLatest { _examItems.value = it.map { item -> item.toRemote() } }
        }
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
                    _statusMessage.value = "Exame excluído com sucesso."
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

    fun compileMedicalDossier(examIds: Set<String>? = null, share: Boolean = false) {
        if (_isCompilingDossier.value) return
        _shareDossier.value = share
        viewModelScope.launch {
            _isCompilingDossier.value = true
            try {
                val userId = auth.currentUser?.uid ?: BragaConstants.GUEST_UID
                val profile = profileRepository.getProfile(userId).firstOrNull()?.toRemote() 
                    ?: br.com.bragasaude.data.remote.model.RemoteProfile(id = userId, fullName = "Usuário")

                val examEntities = (repository.getExams(userId).firstOrNull() ?: emptyList()).filter { examIds == null || it.remoteId in examIds }
                val itemEntities = repository.getExamItems(userId).firstOrNull() ?: emptyList()

                val compiler = br.com.bragasaude.domain.MedicalDossierCompiler(context)
                val temporaryOriginals = mutableListOf<java.io.File>()
                try {
                    val result = compiler.compileDossier(profile, examEntities, itemEntities) { path ->
                        if (path.startsWith("https://") || path.startsWith("http://")) {
                            val bytes = repository.downloadExamOriginal(path)
                            val extension = android.net.Uri.parse(path).lastPathSegment?.substringAfterLast('.', "pdf") ?: "pdf"
                            val directory = java.io.File(context.cacheDir, "exam_originals").apply { mkdirs() }
                            val file = java.io.File(directory, "${UUID.randomUUID()}.$extension")
                            temporaryOriginals.add(file)
                            file.writeBytes(bytes)
                            file
                        } else {
                            val file = fileStore.materialize(path)
                            temporaryOriginals.add(file)
                            file
                        }
                    }
                    _compiledDossierResult.value = result
                } finally { temporaryOriginals.forEach { it.delete() } }
            } catch (e: Exception) {
                android.util.Log.e("ExamsViewModel", "Erro ao exportar exames: ${e.message}", e)
                _statusMessage.value = "Não foi possível exportar os exames: ${e.message}"
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
        if (_isLoading.value || _showCloudConsentDialog.value) return
        saveValidatedExam(exam, items)
    }

    fun sendExamToCloud(examId: String) {
        if (_isLoading.value || _showCloudConsentDialog.value) return
        val userId = auth.currentUser?.uid
        if (userId == null) {
            _statusMessage.value = "Entre na sua conta para salvar na nuvem. Seu exame continua neste aparelho."
            return
        }
        promptCloudConsent { accepted ->
            if (accepted) viewModelScope.launch {
                try {
                    repository.authorizeCloud(examId, userId)
                    _statusMessage.value = "Envio autorizado. Seu exame continua salvo neste aparelho."
                } catch (e: Exception) {
                    _statusMessage.value = "Não foi possível autorizar o envio. Seu exame continua neste aparelho."
                }
            }
        }
    }

    fun pauseCloud(examId: String) {
        viewModelScope.launch {
            repository.pauseCloud(examId, auth.currentUser?.uid ?: BragaConstants.GUEST_UID)
            _statusMessage.value = "Novos envios interrompidos. Cópias já enviadas continuam na nuvem."
        }
    }

    fun editExam(exam: RemoteExam) {
        if (exam.userId != (auth.currentUser?.uid ?: BragaConstants.GUEST_UID)) return
        _pendingExamValidation.value = exam to _examItems.value.filter { it.examId == exam.id }
    }

    fun openOriginal(exam: RemoteExam) {
        if (exam.userId != (auth.currentUser?.uid ?: BragaConstants.GUEST_UID)) return
        viewModelScope.launch {
            try {
                val local = exam.localFilePath ?: exam.fileUrl?.takeUnless { it.startsWith("http") }
                _originalToOpen.value = if (local != null) fileStore.materialize(local, share = true) else {
                    val url = requireNotNull(exam.fileUrl) { "Exame digitado, sem arquivo anexado." }
                    val bytes = repository.downloadExamOriginal(url)
                    val path = fileStore.store(bytes, android.net.Uri.parse(url).lastPathSegment?.substringAfterLast('.', "pdf") ?: "pdf")
                    // Recuperação para visualização sem alterar a política do exame.
                    try { fileStore.materialize(path, share = true) } finally { fileStore.delete(path) }
                }
            } catch (e: Exception) {
                _statusMessage.value = "Não foi possível abrir o original. Verifique a disponibilidade do arquivo."
            }
        }
    }
    fun clearOriginalToOpen() { _originalToOpen.value = null }

    private fun saveValidatedExam(exam: RemoteExam, items: List<RemoteExamItem>) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val userId = auth.currentUser?.uid ?: BragaConstants.GUEST_UID
                val confirmedExam = exam.copy(status = "confirmed", userId = userId)
                val confirmedItems = items.map {
                    it.copy(
                        status = "confirmed",
                        userId = userId,
                        examId = confirmedExam.id ?: it.examId
                    )
                }
                repository.saveExam(confirmedExam, confirmedItems)


                draftOriginal = null
                _statusMessage.value = "Exame salvo neste aparelho. Você pode consultá-lo em Meus exames."
                _pendingExamValidation.value = null
            } catch (e: Exception) {
                _statusMessage.value = e.message ?: "Não foi possível salvar os exames. Tente novamente."
                e.printStackTrace()
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Salva exames inseridos diretamente pelo usuário via formulário estruturado.
     * Gravação imediata nos seus exames com status 'confirmed'.
     */
    fun saveManualExam(title: String, category: String, examDate: String, items: List<RemoteExamItem>) {
        if (_isLoading.value || _showCloudConsentDialog.value) return
        saveConsentedManualExam(title, category, examDate, items)
    }

    private fun saveConsentedManualExam(title: String, category: String, examDate: String, items: List<RemoteExamItem>) {
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
                _manualExamSaved.value = true
                _statusMessage.value = "Exame salvo neste aparelho."
            } catch (e: Exception) {
                _statusMessage.value = e.message ?: "Não foi possível salvar os exames. Tente novamente."
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

                val originalFile = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    val directory = java.io.File(context.filesDir, "exams").apply { mkdirs() }
                    val file = java.io.File(directory, "$examId.pdf")
                    val document = android.graphics.pdf.PdfDocument()
                    try {
                        pages.forEachIndexed { index, bitmap ->
                            val page = document.startPage(android.graphics.pdf.PdfDocument.PageInfo.Builder(595, 842, index + 1).create())
                            val scale = minOf(595f / bitmap.width, 842f / bitmap.height)
                            val width = bitmap.width * scale
                            val height = bitmap.height * scale
                            val left = (595f - width) / 2
                            val top = (842f - height) / 2
                            page.canvas.drawColor(android.graphics.Color.WHITE)
                            page.canvas.drawBitmap(bitmap, null, android.graphics.RectF(left, top, left + width, top + height),
                                android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG))
                            document.finishPage(page)
                        }
                        file.outputStream().use { document.writeTo(it) }
                        file
                    } finally { document.close() }
                }

                val protectedPath = try { fileStore.store(originalFile.readBytes(), "pdf") } finally { originalFile.delete() }
                draftOriginal = protectedPath
                val exam = RemoteExam(
                    id = examId,
                    userId = userId,
                    title = title,
                    category = category,
                    examDate = dateStr,
                    fileUrl = protectedPath,
                    status = "analyzed"
                )

                _pendingExamValidation.value = Pair(exam, extractedItems)
            } catch (e: Exception) {
                _statusMessage.value = "Não foi possível preparar o exame capturado. Tente novamente."
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
        if (_isLoading.value || _showCloudConsentDialog.value) return
        processConsentedFile(title, category, date, fileUri, fileName)
    }

    private fun processConsentedFile(title: String, category: String, date: Date, fileUri: Uri, fileName: String) {
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
                val fileBytes = context.contentResolver.openInputStream(fileUri)?.use { input ->
                    val bytes = java.io.ByteArrayOutputStream()
                    val buffer = ByteArray(8192)
                    var count = input.read(buffer)
                    while (count != -1) {
                        check(bytes.size() + count <= 35 * 1024 * 1024) { "O exame deve ter no máximo 35 MB." }
                        bytes.write(buffer, 0, count)
                        count = input.read(buffer)
                    }
                    bytes.toByteArray()
                } ?: error("Não foi possível ler o exame. Selecione o arquivo novamente.")
                val extension = if (isPdf) "pdf" else when (mimeType) {
                    "image/png" -> "png"
                    "image/webp" -> "webp"
                    else -> "jpg"
                }
                val fileUrl = fileStore.store(fileBytes, extension)
                draftOriginal = fileUrl

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
                _statusMessage.value = e.message ?: "Não foi possível preparar o exame. Tente novamente."
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
        draftOriginal?.let { fileStore.delete(it) }
        draftOriginal = null
        _pendingExamValidation.value = null
    }

    fun refresh() {
        val userId = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                repository.pullAndMergeExams(userId)
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isRefreshing.value = false
            }
        }
    }

}
