package br.com.bragasaude.ui.care

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import br.com.bragasaude.ui.theme.*
import br.com.bragasaude.util.QrCodeGenerator
import androidx.compose.material3.*
import kotlinx.coroutines.flow.collectLatest

/**
 * Tela "Leva pro Doutor" — Modo Consulta (Care OS — D62).
 *
 * QR Code gerado localmente com o payload do MedicalAccessGrant (2 horas de
 * validade), compartilhamento do Magic Link por WhatsApp e abertura do PDF
 * executivo de 1 página (servidor, com fallback on-device).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DoctorModeScreen(
    onBack: () -> Unit,
    viewModel: CareOsViewModel = hiltViewModel()
) {
    val ui by viewModel.ui.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        if (ui.medicalAccess == null && !ui.isGeneratingAccess) {
            viewModel.generateMedicalAccess()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.ui.collectLatest { state ->
            state.message?.let {
                snackbarHostState.showSnackbar(it)
                viewModel.consumeMessage()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Leva pro Doutor", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = BragaEmerald,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = BragaBackground
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "Mostre este QR Code ao médico na consulta. " +
                    "Ele dá acesso ao resumo de saúde por 2 horas.",
                fontSize = 16.sp,
                color = BragaTextSecondary
            )

            val access = ui.medicalAccess
            if (ui.isGeneratingAccess || access == null) {
                Card(
                    modifier = Modifier.size(240.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = BragaMintSurface),
                    border = BorderStroke(1.dp, BragaMintBorder)
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        if (ui.isGeneratingAccess) {
                            CircularProgressIndicator(color = BragaEmerald)
                        } else {
                            Icon(
                                Icons.Filled.QrCode2, contentDescription = null,
                                tint = BragaTextSecondary, modifier = Modifier.size(96.dp)
                            )
                        }
                    }
                }
            } else {
                val bitmap = remember(access.qrCodePayload) {
                    QrCodeGenerator.generateQrCode(access.qrCodePayload, 512)
                }
                Card(
                    modifier = Modifier.size(264.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(2.dp, BragaEmeraldLight)
                ) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "QR Code de acesso médico",
                        modifier = Modifier
                            .padding(16.dp)
                            .fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                }

                AssistChipRow("Válido por 2 horas (até ${access.expiresAt.take(16).replace("T", " ")})")

                // Compartilhar Magic Link (WhatsApp ou qualquer app)
                Button(
                    onClick = {
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, access.magicLink)
                        }
                        try {
                            context.startActivity(Intent.createChooser(intent, "Compartilhar link do médico"))
                        } catch (e: Exception) {
                            Toast.makeText(context, "Nenhum app de compartilhamento disponível.", Toast.LENGTH_LONG).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BragaEmerald),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                ) {
                    Icon(Icons.Filled.Share, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Compartilhar link do médico", color = Color.White, fontSize = 17.sp)
                }
            }

            HorizontalDivider()

            // PDF executivo de 1 página
            Text(
                "Relatório de saúde para o médico",
                fontWeight = FontWeight.SemiBold,
                fontSize = 18.sp,
                color = BragaTextPrimary,
                modifier = Modifier.fillMaxWidth()
            )
            Button(
                onClick = { viewModel.generateDoctorReport() },
                enabled = !ui.isGeneratingReport,
                colors = ButtonDefaults.buttonColors(containerColor = BragaEmeraldDark),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
            ) {
                Icon(Icons.Filled.PictureAsPdf, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Abrir relatório em PDF", color = Color.White, fontSize = 17.sp)
            }

            // Ficha de emergência
            HorizontalDivider()
            EmergencyCard(viewModel)
        }
    }
}

@Composable
private fun AssistChipRow(text: String) {
    AssistChip(
        onClick = {},
        label = { Text(text, fontSize = 13.sp) },
        colors = AssistChipDefaults.assistChipColors(
            containerColor = BragaMint,
            labelColor = BragaEmeraldDark
        ),
        modifier = Modifier.heightIn(min = 40.dp)
    )
}

@Composable
private fun EmergencyCard(viewModel: CareOsViewModel) {
    var token by remember { mutableStateOf<br.com.bragasaude.data.remote.model.EmergencyTokenGrant?>(null) }
    val context = LocalContext.current

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = BragaEmergencyLight),
        border = BorderStroke(1.dp, BragaEmergencyOrange)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Bolt, contentDescription = null, tint = BragaEmergencyOrange)
                Spacer(Modifier.width(8.dp))
                Text(
                    "Ficha de Emergência",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 17.sp,
                    color = BragaEmergencyOrange
                )
            }
            Text(
                "Gera um link de resgate para a equipe de saúde acessar suas informações " +
                    "essenciais em uma emergência.",
                fontSize = 14.sp,
                color = BragaTextPrimary
            )
            OutlinedButton(
                onClick = {
                    viewModel.generateEmergencyAccess { grant -> token = grant }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp),
                border = BorderStroke(1.dp, BragaEmergencyOrange)
            ) { Text("Gerar ficha de emergência", fontSize = 16.sp) }
            token?.let {
                Text("Link de resgate: ${it.rescueLink}", fontSize = 13.sp, color = BragaTextSecondary)
            }
        }
    }
}
