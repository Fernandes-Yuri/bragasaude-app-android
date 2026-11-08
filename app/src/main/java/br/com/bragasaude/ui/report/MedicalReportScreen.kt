package br.com.bragasaude.ui.report

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.MedicalInformation
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import br.com.bragasaude.ui.components.SimpleTrendChart
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MedicalReportScreen(
    onBack: () -> Unit,
    viewModel: ReportViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val isGeneratingPdf by viewModel.isGeneratingPdf.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.pdfFile.collectLatest { file ->
            if (file != null) {
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
                    context.startActivity(Intent.createChooser(intent, "Abrir relatório dos últimos 30 dias"))
                } catch (e: Exception) {
                    Toast.makeText(context, "Não foi possível abrir o PDF. Verifique se possui um leitor instalado.", Toast.LENGTH_LONG).show()
                    e.printStackTrace()
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.pdfErrorMessage.collectLatest { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    val recordedDays by viewModel.recordedDays.collectAsState()
    MedicalReportContent(isGeneratingPdf, snackbarHostState, onBack, viewModel::generatePdfReport, recordedDays)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MedicalReportContent(
    isGeneratingPdf: Boolean,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onExport: () -> Unit,
    recordedDays: Int = 0
) {
    var expanded by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    val today = java.time.LocalDate.now()
    val format = java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")
    Scaffold(
        topBar = { CenterAlignedTopAppBar(title = { Text("Dados e relatórios") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar") } }) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            Surface(tonalElevation = 2.dp) {
                Button(onClick = onExport, enabled = !isGeneratingPdf,
                    modifier = Modifier.fillMaxWidth().padding(16.dp).heightIn(min = 52.dp)) {
                    if (isGeneratingPdf) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    else Icon(Icons.Default.Download, null, Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (isGeneratingPdf) "Gerando relatório..." else "Baixar relatório em PDF")
                }
            }
        }
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).testTag("reportContent"),
            contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            item {
                Icon(Icons.Default.PictureAsPdf, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(40.dp))
                Spacer(Modifier.height(12.dp))
                Text("Seu relatório de 30 dias", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("${today.minusDays(29).format(format)} — ${today.format(format)}")
            }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("$recordedDays de 30 dias com registros", style = MaterialTheme.typography.titleMedium)
                        Text("Pressão, glicemia, hidratação e atividade, conforme os dados disponíveis.")
                        Text("Leve o documento à sua próxima consulta.")
                    }
                }
            }
            item {
                TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "Menos informações" else "Sobre o relatório") }
                if (expanded) Text("O PDF reúne seus registros do período. Dias sem registro ficam identificados; o documento não substitui uma avaliação profissional.")
            }
        }
    }
}
