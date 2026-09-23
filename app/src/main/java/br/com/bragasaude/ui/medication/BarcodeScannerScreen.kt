package br.com.bragasaude.ui.medication

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import br.com.bragasaude.data.remote.model.BarcodeMedication
import br.com.bragasaude.ui.care.CareOsViewModel
import br.com.bragasaude.ui.theme.*
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions

/**
 * Scanner de Caixa de Remédio (Care OS — D62).
 *
 * TRAVA REGULATÓRIA (RDC 657/2022 & CDC Art. 14): o leitor lê EXCLUSIVAMENTE o
 * código de barras EAN-13 da caixa/embalagem para bater na ANVISA. O app NUNCA
 * identifica comprimido solto por foto — a câmera é do código de barras, ponto.
 * A foto do rótulo/receita é só referência visual humana (photo_reference_url).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BarcodeScannerScreen(
    onBack: () -> Unit,
    onSaved: () -> Unit,
    viewModel: CareOsViewModel = hiltViewModel()
) {
    val ui by viewModel.ui.collectAsState()

    var ean by remember { mutableStateOf("") }
    var found by remember { mutableStateOf<BarcodeMedication?>(null) }
    var name by remember { mutableStateOf("") }
    var totalUnits by remember { mutableStateOf("") }
    var scheduleTime by remember { mutableStateOf("08:00") }
    var confirmed by remember { mutableStateOf(false) }
    var lookupError by remember { mutableStateOf<String?>(null) }

    val scanLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        val code = result.contents?.trim()
        if (!code.isNullOrEmpty()) {
            ean = code
            lookupError = null
            viewModel.lookupBarcode(code) { med ->
                found = med
                if (med != null) {
                    name = med.name
                    if (totalUnits.isBlank()) totalUnits = "30"
                } else {
                    lookupError = "Não encontrei este código no catálogo da ANVISA. Preencha manualmente."
                }
            }
        }
    }

    fun launchScanner() {
        scanLauncher.launch(
            ScanOptions()
                .setDesiredBarcodeFormats(ScanOptions.EAN_13)
                .setPrompt("Aponte para o código de barras da caixa do remédio")
                .setBeepEnabled(true)
                .setOrientationLocked(false)
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Cadastrar Remédio", fontWeight = FontWeight.SemiBold) },
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
        containerColor = BragaBackground
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Aviso regulatório — clareza total para o usuário idoso.
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = BragaMintSurface),
                border = BorderStroke(1.dp, BragaMintBorder)
            ) {
                Row(modifier = Modifier.padding(14.dp)) {
                    Icon(
                        Icons.Filled.Medication, contentDescription = null,
                        tint = BragaEmeraldDark, modifier = Modifier.size(32.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "Aponte a câmera para o código de barras da CAIXA. " +
                            "Identificamos só a embalagem — nunca o comprimido solto.",
                        fontSize = 14.sp, color = BragaTextPrimary
                    )
                }
            }

            Button(
                onClick = ::launchScanner,
                colors = ButtonDefaults.buttonColors(containerColor = BragaEmerald),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
            ) {
                Icon(Icons.Filled.QrCodeScanner, contentDescription = null)
                Spacer(Modifier.width(10.dp))
                Text("Escanear código de barras", color = Color.White, fontSize = 17.sp)
            }

            OutlinedTextField(
                value = ean,
                onValueChange = {
                    ean = it.filter { c -> c.isDigit() }.take(13)
                    found = null
                },
                label = { Text("Código EAN-13 (13 dígitos)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            lookupError?.let {
                Text(it, color = BragaEmergencyOrange, fontSize = 14.sp)
            }

            found?.let { med ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = BragaMint),
                    border = BorderStroke(1.dp, BragaEmeraldLight)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("Encontrado na ANVISA:", fontSize = 13.sp, color = BragaTextSecondary)
                        Spacer(Modifier.height(4.dp))
                        Text(med.name, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = BragaTextPrimary)
                        med.activePrinciple?.takeIf { it.isNotBlank() }?.let {
                            Text("Princípio ativo: $it", fontSize = 14.sp)
                        }
                        med.concentration?.takeIf { it.isNotBlank() }?.let {
                            Text("Concentração: $it", fontSize = 14.sp)
                        }
                        med.manufacturer?.takeIf { it.isNotBlank() }?.let {
                            Text("Laboratório: $it", fontSize = 14.sp)
                        }
                        med.farmaciaPopularEligible?.let { eligible ->
                            Text(
                                if (eligible) "Possivelmente elegível no Farmácia Popular" else "Não elegível no Farmácia Popular",
                                fontSize = 13.sp, color = BragaTextSecondary
                            )
                        }
                    }
                }
            }

            HorizontalDivider()

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Nome do remédio") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = totalUnits,
                onValueChange = { totalUnits = it.filter { c -> c.isDigit() }.take(5) },
                label = { Text("Quantidade da caixa (ex: 30 comprimidos)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = scheduleTime,
                onValueChange = { scheduleTime = it },
                label = { Text("Horário da dose (HH:mm)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            // Checkbox OBRIGATÓRIO — sem ele, o cadastro não existe.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = confirmed,
                    onCheckedChange = { confirmed = it },
                    colors = CheckboxDefaults.colors(checkedColor = BragaEmerald)
                )
                Text("Conferido com a receita médica", fontSize = 16.sp, color = BragaTextPrimary)
            }

            val canSave = name.length >= 2 && totalUnits.toIntOrNull()?.let { it > 0 } == true &&
                confirmed && !ui.loading

            Button(
                onClick = {
                    viewModel.createMedication(
                        name = name.trim(),
                        totalUnits = totalUnits.toInt(),
                        scheduleTimes = listOf(scheduleTime.trim()),
                        confirmedWithPrescription = confirmed,
                        barcode = found
                    ) { ok -> if (ok) onSaved() }
                },
                enabled = canSave,
                colors = ButtonDefaults.buttonColors(containerColor = BragaEmerald),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
            ) {
                Icon(Icons.Filled.CheckCircle, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(
                    if (confirmed) "Cadastrar remédio" else "Marque a conferência da receita",
                    color = Color.White, fontSize = 17.sp
                )
            }

            if (ui.loading) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(color = BragaEmerald)
                }
            }
        }
    }
}
