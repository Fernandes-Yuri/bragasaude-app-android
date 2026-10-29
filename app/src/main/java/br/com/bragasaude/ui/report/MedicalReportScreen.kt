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

    MedicalReportContent(isGeneratingPdf, snackbarHostState, onBack, viewModel::generatePdfReport)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MedicalReportContent(
    isGeneratingPdf: Boolean,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onExport: () -> Unit
) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(title = { Text("Dados e Relatórios", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                } })
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        LazyColumn(Modifier.fillMaxSize().padding(innerPadding).testTag("reportContent"),
            contentPadding = PaddingValues(start = 20.dp, top = 20.dp, end = 20.dp,
                bottom = br.com.bragasaude.ui.components.AssistantOrbScrollClearance), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Default.PictureAsPdf, contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(40.dp))
                    Text("Seus registros contam uma história", style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold)
                    Text("O PDF reúne o que você registrou nos últimos 30 dias. Assim, fica mais fácil levar essas informações para uma conversa sobre sua saúde.",
                        style = MaterialTheme.typography.bodyLarge)
                }
            }
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), shape = RoundedCornerShape(20.dp)) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                        ReportBenefit("Leve para a consulta", "Mostre seus registros à equipe de saúde sem depender só da memória.")
                        ReportBenefit("Reveja o período com calma", "Consulte datas, gráficos e anotações reunidos no mesmo documento.")
                        ReportBenefit("Tenha seu histórico à mão", "Abra uma cópia organizada para acompanhar suas próximas conversas e consultas.")
                    }
                }
            }
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), shape = RoundedCornerShape(20.dp)) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Seu relatório de 30 dias", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("Inclui os registros disponíveis de pressão, glicemia, hidratação e atividade física. Quanto mais você registrar, mais completo ficará o relatório.",
                            style = MaterialTheme.typography.bodyMedium)
                        Button(onClick = onExport, enabled = !isGeneratingPdf, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                            if (isGeneratingPdf) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary)
                            else Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(if (isGeneratingPdf) "Gerando relatório..." else "Baixar relatório em PDF")
                        }
                        Text("O relatório organiza seus registros; ele não realiza diagnóstico nem substitui uma avaliação profissional.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun ReportBenefit(title: String, description: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
