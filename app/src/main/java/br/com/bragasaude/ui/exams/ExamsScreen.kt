package br.com.bragasaude.ui.exams

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
    val showGlucosePrompt by viewModel.showGlucosePrompt.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    
    val context = LocalContext.current
    val pdfFile by reportViewModel.pdfFile.collectAsState(initial = null)

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

    Scaffold(
        containerColor = BragaBackground,
        topBar = {
            EmeraldHeaderBanner(
                title = "Meus Exames",
                subtitle = "Histórico e Laudos Médicos",
                onBack = onBack,
                trailingContent = {
                    Row {
                        IconButton(onClick = onNavigateToEvolution) {
                            Icon(Icons.Default.BarChart, contentDescription = "Evolução", tint = Color.White)
                        }
                        IconButton(onClick = { reportViewModel.generatePdfReport() }) {
                            Icon(Icons.Default.PictureAsPdf, contentDescription = "Gerar PDF", tint = Color.White)
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
                    DashedUploadCard(onClick = { showAddDialog = true })
                    Spacer(Modifier.height(12.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = BragaMint.copy(alpha = 0.6f)),
                        border = BorderStroke(1.dp, BragaMintBorder)
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = BragaEmerald, modifier = Modifier.size(22.dp))
                            Spacer(Modifier.width(10.dp))
                            Text(
                                "A IA transcreve os valores do PDF automaticamente e você confere cada um antes de salvar no seu prontuário permanente.",
                                style = MaterialTheme.typography.bodySmall,
                                color = BragaTextPrimary,
                                lineHeight = 18.sp
                            )
                        }
                    }
                }

                if (exams.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth().height(200.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Nenhum exame cadastrado.", color = MaterialTheme.colorScheme.outline)
                        }
                    }
                }

                items(exams) { exam ->
                    ExamCard(exam)
                }
                
                item { Spacer(Modifier.height(80.dp)) }
            }
        }
        } // fecha PullToRefreshBox
    } // fecha Scaffold

    if (showAddDialog) {
        AddExamDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { title, category, uri, fileName ->
                viewModel.uploadAndSaveExam(title, category, Date(), uri, fileName)
                showAddDialog = false
            }
        )
    }

    pendingValidation?.let { (exam, items) ->
        ValidationDialog(
            exam = exam,
            initialItems = items,
            onDismiss = { viewModel.onValidationCancelled() },
            onConfirm = { validatedExam, validatedItems ->
                viewModel.onValidationConfirmed(validatedExam, validatedItems)
            }
        )
    }

    if (showGlucosePrompt) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissGlucosePrompt() },
            title = { Text("Monitoramento de Glicose", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Detectamos parâmetros de glicose neste exame (glicose/HbA1c). " +
                    "Deseja ativar o monitoramento de glicose no aplicativo?"
                )
            },
            confirmButton = {
                Button(onClick = { viewModel.enableDiabetesMonitoring() }) {
                    Text("Ativar")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissGlucosePrompt() }) {
                    Text("Agora não")
                }
            }
        )
    }

    // Progress Overlay
    uploadProgress?.takeIf { showUploadProgress }?.let { progress ->
        AlertDialog(
            onDismissRequest = { showUploadProgress = false },
            confirmButton = { TextButton(onClick = { showUploadProgress = false }) { Text("Fechar aviso") } },
            title = { Text("Enviando Exame...") },
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
}

@Composable
fun AddExamDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String, Uri?, String?) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Geral") }
    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var fileName by remember { mutableStateOf<String?>(null) }

    val context = LocalContext.current

    // DECISOES.md (D1): apenas PDF. Foto do papel saiu do fluxo (extração menos
    // confiável e criaria necessidade de avaliação humana).
    val fileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val name = context.contentResolver.query(it, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (cursor.moveToFirst()) cursor.getString(nameIndex) else null
            }
            if (name != null && !name.endsWith(".pdf", ignoreCase = true)) {
                Toast.makeText(context, context.getString(R.string.exam_pdf_only_error), Toast.LENGTH_LONG).show()
            } else {
                selectedUri = it
                fileName = name ?: "arquivo_exame.pdf"
                android.util.Log.d("ExamsScreen", "File selected: $fileName, uri: $it")
                Toast.makeText(context, "Arquivo selecionado!", Toast.LENGTH_SHORT).show()
            }
        } ?: run {
            android.util.Log.d("ExamsScreen", "No file selected")
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Novo Exame") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Título do Exame (ex: Hemograma)") },
                    modifier = Modifier.fillMaxWidth()
                )
                
                OutlinedTextField(
                    value = category,
                    onValueChange = { category = it },
                    label = { Text("Categoria") },
                    modifier = Modifier.fillMaxWidth()
                )
                
                Button(
                    onClick = {
                        android.util.Log.d("ExamsScreen", "Button 'PDF' clicked")
                        try {
                            // D1: restrito a PDF
                            fileLauncher.launch("application/pdf")
                        } catch (e: Exception) {
                            android.util.Log.e("ExamsScreen", "Error launching file picker", e)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer)
                ) {
                    Icon(Icons.Default.PictureAsPdf, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Selecionar PDF")
                }
                Text(
                    context.getString(R.string.exam_pdf_only_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray
                )
                
                if (selectedUri != null) {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                        Row(modifier = Modifier.padding(8.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            val isPdf = fileName?.endsWith(".pdf") == true
                            Icon(if (isPdf) Icons.Default.PictureAsPdf else Icons.Default.Image, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(fileName ?: "Selecionado", style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
                            IconButton(onClick = { selectedUri = null; fileName = null }) {
                                Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(title, category, selectedUri, fileName) },
                enabled = title.isNotEmpty() && selectedUri != null
            ) {
                Text("Enviar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

@Composable
fun ValidationDialog(
    exam: RemoteExam,
    initialItems: List<RemoteExamItem>,
    onDismiss: () -> Unit,
    onConfirm: (RemoteExam, List<RemoteExamItem>) -> Unit
) {
    val items = remember { SnapshotStateList<RemoteExamItem>().apply { addAll(initialItems) } }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Conferência da Transcrição por IA") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Nossa IA extraiu os valores do seu PDF. Confira e ajuste os dados abaixo antes de confirmar no seu prontuário permanente:",
                    style = MaterialTheme.typography.bodySmall
                )
                
                LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                    items(items.size) { index ->
                        val item = items[index]
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(item.itemName, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                            OutlinedTextField(
                                value = item.valueText ?: "",
                                onValueChange = { 
                                    items[index] = item.copy(
                                        valueText = it, 
                                        valueNumeric = HealthFormatter.parseDouble(it)
                                    ) 
                                },
                                modifier = Modifier.width(100.dp),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                            )
                        }
                    }
                }
                
                if (items.isEmpty()) {
                    Text("Nenhum dado extraído automaticamente.", color = MaterialTheme.colorScheme.error)
                }
                
                Spacer(Modifier.height(8.dp))
                Text(
                    "Este app não é dispositivo médico. Os dados são autorreportados e devem ser validados por profissional de saúde.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(exam, items.toList()) }) {
                Text("Salvar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

@Composable
fun ExamCard(exam: RemoteExam) {
    val badge = examStatusBadge(exam.status)

    Card(
        modifier = Modifier.fillMaxWidth(),
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
                            color = BragaTextPrimary
                        )
                        Text(
                            exam.examDate,
                            style = MaterialTheme.typography.bodySmall,
                            color = BragaTextSecondary
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                BragaStatusBadge(text = badge.first, type = badge.second)
            }

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
        "uploaded" -> "Aguardando análise da IA"
        "analyzed", "processing" -> "Transcrição concluída — confira os valores antes de salvar"
        "confirmed", "validated", "user_confirmed" -> "Valores conferidos e salvos no seu prontuário"
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
            .padding(vertical = 24.dp, horizontal = 20.dp),
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
                        Icons.Default.CloudUpload,
                        contentDescription = null,
                        tint = BragaEmerald,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                "+ Enviar novo exame",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = BragaEmerald
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Anexe o PDF do seu laudo para transcrição automática",
                style = MaterialTheme.typography.bodySmall,
                color = BragaTextSecondary,
                textAlign = TextAlign.Center
            )
        }
    }
}
