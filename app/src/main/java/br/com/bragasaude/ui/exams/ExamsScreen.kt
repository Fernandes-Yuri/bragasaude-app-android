package br.com.bragasaude.ui.exams

import br.com.bragasaude.ui.components.BragaAlertDialog

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import br.com.bragasaude.domain.ExamCloudState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.bragasaude.R
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import br.com.bragasaude.data.remote.model.RemoteExam
import br.com.bragasaude.data.remote.model.RemoteExamItem
import br.com.bragasaude.data.util.HealthFormatter
import br.com.bragasaude.ui.components.BragaBadgeType
import br.com.bragasaude.ui.components.BragaStatusBadge
import br.com.bragasaude.ui.components.EmeraldHeaderBanner
import br.com.bragasaude.ui.exams.components.AddExamBottomSheet
import br.com.bragasaude.ui.report.ReportViewModel
import br.com.bragasaude.ui.theme.BragaBackground
import br.com.bragasaude.ui.theme.BragaCardBorder
import br.com.bragasaude.ui.theme.BragaCardSurface
import br.com.bragasaude.ui.theme.BragaEmerald
import br.com.bragasaude.ui.theme.BragaMint
import br.com.bragasaude.ui.theme.BragaMintBorder
import br.com.bragasaude.ui.theme.BragaTextPrimary
import br.com.bragasaude.ui.theme.BragaTextSecondary
import java.io.File
import java.util.*

enum class ExamsSubFlow {
    LIST,
    CAPTURE_MULTIPAGE,
    MANUAL_ENTRY
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExamsScreen(
    viewModel: ExamsViewModel = hiltViewModel(),
    reportViewModel: ReportViewModel = hiltViewModel(),
    onBack: () -> Unit,
    onNavigateToEvolution: () -> Unit
) {
    val exams by viewModel.exams.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val uploadProgress by viewModel.uploadProgress.collectAsState()
    var showUploadProgress by remember { mutableStateOf(true) }
    LaunchedEffect(uploadProgress == null) { if (uploadProgress == null) showUploadProgress = true }
    val pendingValidation by viewModel.pendingExamValidation.collectAsState()
    var showAddBottomSheet by remember { mutableStateOf(false) }
    var currentFlow by remember { mutableStateOf(ExamsSubFlow.LIST) }
    
    val manualExamSaved by viewModel.manualExamSaved.collectAsState()
    LaunchedEffect(manualExamSaved) {
        if (manualExamSaved) {
            currentFlow = ExamsSubFlow.LIST
            viewModel.clearManualExamSaved()
        }
    }

    // Consentimento específico por envio
    val showCloudConsentDialog by viewModel.showCloudConsentDialog.collectAsState()
    var examToDelete by remember { mutableStateOf<RemoteExam?>(null) }
    var cloudCopyToRemove by remember { mutableStateOf<String?>(null) }
    var selectedExamId by remember { mutableStateOf<String?>(null) }
    var showStorageInfo by remember { mutableStateOf(false) }
    var search by remember { mutableStateOf("") }
    var storageFilter by remember { mutableStateOf("Todos") }
    var shareExamIds by remember { mutableStateOf<Set<String>?>(null) }
    val examItems by viewModel.examItems.collectAsState()
    val originalToOpen by viewModel.originalToOpen.collectAsState()
    val shareDossier by viewModel.shareDossier.collectAsState()
    val visibleExams = exams.filter { exam ->
        (exam.title.contains(search, true) || exam.category.orEmpty().contains(search, true)) && when (storageFilter) {
            "Neste aparelho" -> exam.cloudState == ExamCloudState.LOCAL_ONLY
            "Na nuvem" -> exam.hasCloudCopy
            "Envio pendente" -> exam.cloudState in setOf(ExamCloudState.PENDING, ExamCloudState.SENDING, ExamCloudState.ERROR)
            else -> true
        }
    }

    // Exportação dos registros e arquivos originais
    val isCompilingDossier by viewModel.isCompilingDossier.collectAsState()
    val compiledDossierResult by viewModel.compiledDossierResult.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()

    val context = LocalContext.current
    val pdfFile by reportViewModel.pdfFile.collectAsState(initial = null)

    LaunchedEffect(statusMessage) {
        statusMessage?.let { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            viewModel.clearStatusMessage()
        }
    }

    LaunchedEffect(compiledDossierResult) {
        compiledDossierResult?.let { result ->
            try {
                val uri: Uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    result.pdfFile
                )
                val intent = Intent(if (shareDossier) Intent.ACTION_SEND else Intent.ACTION_VIEW).apply {
                    if (shareDossier) {
                        type = "application/pdf"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        clipData = android.content.ClipData.newRawUri("Exames", uri)
                    } else setDataAndType(uri, "application/pdf")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(intent, if (shareDossier) "Compartilhar PDF dos exames" else "Abrir PDF dos exames"))
            } catch (e: Exception) {
                Toast.makeText(context, "Arquivo de exames gerado com sucesso (${result.totalPagesCount} páginas).", Toast.LENGTH_LONG).show()
                e.printStackTrace()
            }
            viewModel.clearCompiledDossier()
        }
    }

    LaunchedEffect(originalToOpen) {
        originalToOpen?.let { file ->
            try {
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                val mime = android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension) ?: "application/pdf"
                context.startActivity(Intent.createChooser(Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, mime)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }, "Abrir original do exame"))
            } catch (e: Exception) {
                Toast.makeText(context, "Não foi possível abrir o arquivo. Verifique se possui um leitor instalado.", Toast.LENGTH_LONG).show()
            }
            viewModel.clearOriginalToOpen()
        }
    }

    LaunchedEffect(pdfFile) {
        pdfFile?.let { file ->
            try {
                val uri: Uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "application/pdf")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(intent, "Abrir Relatório"))
            } catch (e: Exception) {
                Toast.makeText(context, "Não foi possível abrir o PDF. Verifique se possui um leitor instalado.", Toast.LENGTH_LONG).show()
                e.printStackTrace()
            }
        }
    }

    val fileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            val name = context.contentResolver.query(it, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (cursor.moveToFirst()) cursor.getString(nameIndex) else null
            } ?: "exame_anexo"
            val cleanTitle = name.substringBeforeLast(".").replace("_", " ").replace("-", " ")
            viewModel.processAttachedFile(
                title = if (cleanTitle.isNotBlank()) cleanTitle else "Exame Anexado",
                category = "Laboratorial",
                date = Date(),
                fileUri = it,
                fileName = name
            )
        }
    }

    // Termo específico, renovado para cada envio
    if (showCloudConsentDialog) {
        br.com.bragasaude.ui.exams.components.CloudConsentDialog(
            onConfirm = { accepted ->
                viewModel.onCloudConsentDecision(accepted)
            },
            onDismiss = {
                viewModel.onCloudConsentDismissed()
            }
        )
    }

    // 1. Conferência Humana Obrigatória (Tela Completa)
    if (pendingValidation != null) {
        val (exam, items) = pendingValidation!!
        ExamReviewScreen(
            exam = exam,
            initialItems = items,
            onBack = { viewModel.onValidationCancelled() },
            onConfirm = { confirmedExam, confirmedItems ->
                viewModel.onValidationConfirmed(confirmedExam, confirmedItems)
            },
            onOpenOriginal = { viewModel.openOriginal(exam) }
        )
        return
    }

    // 2. Assistente de Captura Multipage com Trava de Nitidez
    if (currentFlow == ExamsSubFlow.CAPTURE_MULTIPAGE) {
        MultipageCaptureScreen(
            onBack = { currentFlow = ExamsSubFlow.LIST },
            onDocumentCompleted = { title, category, date, pages ->
                currentFlow = ExamsSubFlow.LIST
                viewModel.processCapturedPages(title, category, date, pages)
            }
        )
        return
    }

    // 3. Entrada Manual Estruturada
    if (currentFlow == ExamsSubFlow.MANUAL_ENTRY) {
        ManualExamEntryScreen(
            onBack = { currentFlow = ExamsSubFlow.LIST },
            onSave = { title, category, examDate, items ->
                viewModel.saveManualExam(title, category, examDate, items)
            }
        )
        return
    }

    val selectedExam = exams.firstOrNull { it.id == selectedExamId }
    if (selectedExam != null) {
        ExamDetailScreen(
            exam = selectedExam,
            items = examItems.filter { it.examId == selectedExam.id },
            busy = isLoading || isCompilingDossier,
            onBack = { selectedExamId = null },
            onOpen = { viewModel.openOriginal(selectedExam) },
            onEdit = { viewModel.editExam(selectedExam) },
            onPdf = { selectedExam.id?.let { viewModel.compileMedicalDossier(setOf(it)) } },
            onShare = { selectedExam.id?.let { shareExamIds = setOf(it) } },
            onCloud = { selectedExam.id?.let { viewModel.sendExamToCloud(it) } },
            onPause = { selectedExam.id?.let { viewModel.pauseCloud(it) } },
            onRemoveCloud = { cloudCopyToRemove = selectedExam.id }
        )
        if (cloudCopyToRemove != null) BragaAlertDialog(
            onDismissRequest = { cloudCopyToRemove = null },
            title = { Text("Remover cópia da nuvem") },
            text = { Text("O exame e seus resultados serão mantidos neste aparelho. A cópia ativa no servidor será removida após confirmação com conexão. PDFs compartilhados e backups sujeitos à política de retenção não são apagados por esta ação.") },
            confirmButton = { TextButton(onClick = { cloudCopyToRemove?.let { viewModel.removeCloudCopy(it) }; cloudCopyToRemove = null }) { Text("Remover cópia da nuvem") } },
            dismissButton = { TextButton(onClick = { cloudCopyToRemove = null }) { Text("Cancelar") } }
        )
        if (shareExamIds != null) ShareExamsDialog(
            onDismiss = { shareExamIds = null },
            onConfirm = { viewModel.compileMedicalDossier(shareExamIds, share = true); shareExamIds = null }
        )
        return
    }

    Scaffold(
        containerColor = BragaBackground,
        topBar = {
            EmeraldHeaderBanner(
                title = "Meus Exames",
                subtitle = "Histórico e Laudos Médicos",
                onBack = onBack,
                trailingContent = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onNavigateToEvolution) {
                            Icon(Icons.Default.BarChart, contentDescription = "Evolução", tint = Color.White)
                        }
                        if (isCompilingDossier) {
                            CircularProgressIndicator(
                                color = Color.White,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(20.dp).padding(2.dp)
                            )
                        } else {
                            IconButton(onClick = { viewModel.compileMedicalDossier() }) {
                                Icon(Icons.Default.PictureAsPdf, contentDescription = "Gerar PDF dos exames", tint = Color.White)
                            }
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        val pullToRefreshState = rememberPullToRefreshState()
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = { viewModel.refresh() },
            state = pullToRefreshState,
            modifier = Modifier.padding(innerPadding)
        ) {
            if (isLoading && exams.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                // Card de upload em destaque com borda tracejada (padrão 110918/112059)
                item {
                    Spacer(Modifier.height(4.dp))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("${exams.size} exames", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text("${exams.count { it.cloudState == ExamCloudState.LOCAL_ONLY }} locais · ${exams.count { it.hasCloudCopy }} na nuvem", style = MaterialTheme.typography.bodySmall, color = BragaTextSecondary)
                        }
                        IconButton(onClick = { showStorageInfo = true }) {
                            Icon(Icons.Default.Info, contentDescription = "Sobre o armazenamento", tint = BragaEmerald)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    DashedUploadCard(onClick = { showAddBottomSheet = true })
                    TextButton(onClick = { showStorageInfo = true }) {
                        Icon(Icons.Default.PhoneAndroid, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Salvos no aparelho. Nuvem opcional.", style = MaterialTheme.typography.labelMedium)
                    }
                    if (exams.isNotEmpty()) {
                        OutlinedTextField(value = search, onValueChange = { search = it }, label = { Text("Buscar exame") }, modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(14.dp))
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("Todos", "Neste aparelho", "Na nuvem", "Envio pendente").forEach { filter ->
                                FilterChip(selected = storageFilter == filter, onClick = { storageFilter = filter }, label = { Text(filter) })
                            }
                        }
                        OutlinedButton(onClick = { shareExamIds = exams.mapNotNull { it.id }.toSet() }, enabled = !isCompilingDossier, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(if (isCompilingDossier) "Preparando PDF..." else "Compartilhar exames em PDF")
                        }
                    }
                }

                if (visibleExams.isEmpty() && exams.isNotEmpty()) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Nenhum exame encontrado para este filtro.", color = MaterialTheme.colorScheme.outline)
                        }
                    }
                }

                items(visibleExams) { exam ->
                    ExamCard(
                        exam = exam,
                        onOpen = { selectedExamId = exam.id },
                        onDelete = { examToDelete = exam }
                    )
                }
                
                item { Spacer(Modifier.height(80.dp)) }
            }
        }
        } // fecha PullToRefreshBox
    } // fecha Scaffold

    if (showStorageInfo) {
        BragaAlertDialog(
            onDismissRequest = { showStorageInfo = false },
            title = { Text("Seus exames, sua escolha") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Os novos exames ficam neste aparelho. Uma cópia na nuvem só é enviada quando você autoriza, no detalhe do exame.")
                    Text("Sem uma cópia fora do aparelho, você pode perder todos os exames ao desinstalar o app, apagar seus dados ou perder acesso ao celular. Você pode gerar e guardar um PDF.")
                    Text("Confira os valores transcritos antes de salvar. O app organiza exames; não interpreta resultados nem faz diagnóstico.")
                }
            },
            confirmButton = { TextButton(onClick = { showStorageInfo = false }) { Text("Entendi") } }
        )
    }

    if (showAddBottomSheet) {
        AddExamBottomSheet(
            onDismiss = { showAddBottomSheet = false },
            onTakePhoto = {
                showAddBottomSheet = false
                currentFlow = ExamsSubFlow.CAPTURE_MULTIPAGE
            },
            onAttachFile = {
                showAddBottomSheet = false
                try {
                    fileLauncher.launch(arrayOf("application/pdf", "image/*"))
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            },
            onManualEntry = {
                showAddBottomSheet = false
                currentFlow = ExamsSubFlow.MANUAL_ENTRY
            }
        )
    }

    if (shareExamIds != null) ShareExamsDialog(
        onDismiss = { shareExamIds = null },
        onConfirm = { viewModel.compileMedicalDossier(shareExamIds, share = true); shareExamIds = null }
    )

    // Progress Overlay
    uploadProgress?.takeIf { showUploadProgress }?.let { progress ->
        BragaAlertDialog(
            onDismissRequest = { showUploadProgress = false },
            confirmButton = { TextButton(onClick = { showUploadProgress = false }) { Text("Fechar aviso") } },
            title = { Text("Processando Exame...") },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("${(progress * 100).toInt()}%")
                }
            }
        )
    }
    // Diálogo de Exclusão Definitiva (LGPD Art. 18)
    if (examToDelete != null) {
        BragaAlertDialog(
            onDismissRequest = { examToDelete = null },
            icon = {
                Icon(
                    imageVector = Icons.Default.DeleteForever,
                    contentDescription = null,
                    tint = Color(0xFFE11D48),
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text("Excluir exame", fontWeight = FontWeight.Bold)
            },
            text = {
                Text(
                    "Deseja excluir o exame \"${examToDelete?.title}\" e seus resultados? Se houver cópia na nuvem, a exclusão dependerá de conexão e confirmação do servidor. PDFs já compartilhados não serão apagados.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF475569),
                    lineHeight = 20.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        examToDelete?.id?.let { id ->
                            viewModel.deleteExamAtomically(id)
                        }
                        examToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE11D48))
                ) {
                    Text("Excluir Permanentemente", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { examToDelete = null }) {
                    Text("Cancelar")
                }
            }
        )
    }


}

@Composable
fun ExamCard(
    exam: RemoteExam,
    onDelete: () -> Unit = {},
    onOpen: () -> Unit = {}
) {
    val badge = examStatusBadge(exam.status)

    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = BragaCardSurface),
        border = BorderStroke(1.dp, BragaMintBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Surface(
                        shape = CircleShape,
                        color = BragaMint,
                        modifier = Modifier.size(44.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.Description,
                                contentDescription = null,
                                tint = BragaEmerald,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            exam.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = BragaTextPrimary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            exam.examDate,
                            style = MaterialTheme.typography.bodySmall,
                            color = BragaTextSecondary
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BragaStatusBadge(text = badge.first, type = badge.second)
                    Spacer(Modifier.width(6.dp))
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "Excluir exame",
                            tint = Color(0xFF94A3B8),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            Text(ExamCloudState.label(exam.cloudState), style = MaterialTheme.typography.bodySmall, color = BragaTextSecondary)
            Text(if (exam.localFilePath != null) "Original salvo neste aparelho" else if (exam.fileUrl != null) "Original disponível na nuvem" else "Exame digitado, sem arquivo anexado", style = MaterialTheme.typography.bodySmall)
            examStatusHint(exam.status)?.let { hint ->
                Spacer(Modifier.height(10.dp))
                Surface(
                    color = BragaMint,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        hint,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = BragaEmerald,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

private fun examStatusBadge(status: String): Pair<String, BragaBadgeType> {
    return when (status) {
        "confirmed", "validated", "user_confirmed" -> "Confirmado" to BragaBadgeType.CONFIRMED
        "analyzed" -> "Em Revisão" to BragaBadgeType.REVIEW
        "processing" -> "Analisando" to BragaBadgeType.REVIEW
        "uploaded" -> "Pendente" to BragaBadgeType.PENDING
        "rejected" -> "Não Processado" to BragaBadgeType.PENDING
        else -> status to BragaBadgeType.NEUTRAL
    }
}

private fun examStatusHint(status: String): String? {
    return when (status) {
        "uploaded" -> "Arquivo recebido — aguardando transcrição"
        "analyzed", "processing" -> "Transcrição concluída — confira os valores antes de salvar"
        "confirmed", "validated", "user_confirmed" -> "Valores conferidos e salvos nos seus exames"
        else -> null
    }
}

/**
 * Card de upload em destaque com borda tracejada, fundo menta e ícone de nuvem.
 * Padrão visual dos wireframes 110918 / 112059.
 */
@Composable
private fun DashedUploadCard(onClick: () -> Unit) {
    val dashColor = BragaMintBorder
    val strokeWidth = 2.dp
    val cornerRadius = 18.dp

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(cornerRadius))
            .background(BragaMint)
            .drawBehind {
                val strokeWidthPx = strokeWidth.toPx()
                val cornerRadiusPx = cornerRadius.toPx() - strokeWidthPx / 2f
                val dashWidth = with(density) { 16.dp.toPx() }
                val gapWidth = with(density) { 12.dp.toPx() }
                drawRoundRect(
                    color = dashColor,
                    topLeft = Offset(strokeWidthPx / 2f, strokeWidthPx / 2f),
                    size = Size(size.width - strokeWidthPx, size.height - strokeWidthPx),
                    cornerRadius = CornerRadius(cornerRadiusPx),
                    style = Stroke(
                        width = strokeWidthPx,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(dashWidth, gapWidth), 0f)
                    )
                )
            }
            .clickable { onClick() }
            .padding(vertical = 18.dp, horizontal = 20.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Surface(
                shape = CircleShape,
                color = BragaCardSurface,
                modifier = Modifier.size(52.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = null,
                        tint = BragaEmerald,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                "Adicionar exame",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = BragaEmerald
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Foto, arquivo ou preenchimento manual",
                style = MaterialTheme.typography.bodySmall,
                color = BragaTextSecondary,
                textAlign = TextAlign.Center
            )
        }
    }
}
