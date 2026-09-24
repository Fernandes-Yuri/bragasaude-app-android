package br.com.bragasaude.ui.medication

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import br.com.bragasaude.data.remote.model.BarcodeMedication
import br.com.bragasaude.ui.care.CareAuthenticationRequired
import br.com.bragasaude.ui.care.CareOsViewModel
import br.com.bragasaude.ui.care.CarePatientSelector
import br.com.bragasaude.ui.theme.*
import br.com.bragasaude.util.BragaConstants
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions

/**
 * Scanner e Cadastro Limpo de Medicamento (Care OS — D62 / Fase B).
 *
 * TRAVA REGULATÓRIA (RDC 657/2022 & CDC Art. 14):
 * - O leitor lê exclusivamente o código de barras EAN-13 da caixa/embalagem (ANVISA).
 * - O app NUNCA identifica comprimido solto por foto.
 * - Desburocratizado: sem campos cartoriais desnecessários (CRM, validade, fotos soltas).
 * - Checkbox de conferência da receita mantido (requisito SaMD).
 * - Suporte opcional moderno para anexar receita (PDF ou imagem) com dupla checagem de IA.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BarcodeScannerScreen(
    onBack: () -> Unit,
    onSaved: () -> Unit,
    viewModel: CareOsViewModel = hiltViewModel()
) {
    val ui by viewModel.ui.collectAsState()
    val context = LocalContext.current

    var ean by remember { mutableStateOf("") }
    var found by remember { mutableStateOf<BarcodeMedication?>(null) }
    var name by remember { mutableStateOf("") }
    var totalUnits by remember { mutableStateOf("30") }
    var scheduleTime by remember { mutableStateOf("08:00") }
    var confirmed by remember { mutableStateOf(false) }
    var lookupError by remember { mutableStateOf<String?>(null) }
    var prescriptionDocumentUri by remember { mutableStateOf<Uri?>(null) }
    var prescriptionFileName by remember { mutableStateOf<String?>(null) }
    var isAnalyzingPrescription by remember { mutableStateOf(false) }
    var prescriptionDivergenceReason by remember { mutableStateOf<String?>(null) }

    val documentPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        prescriptionDocumentUri = uri
        prescriptionFileName = uri?.let {
            var fileName: String? = null
            runCatching {
                context.contentResolver.query(it, null, null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (nameIndex >= 0 && cursor.moveToFirst()) {
                        fileName = cursor.getString(nameIndex)
                    }
                }
            }
            fileName ?: "Receita anexada"
        }
        if (uri != null) {
            isAnalyzingPrescription = true
            prescriptionDivergenceReason = null
            val patientId = ui.selectedPatientId ?: BragaConstants.GUEST_UID
            viewModel.analyzePrescription(patientId, uri) { response ->
                isAnalyzingPrescription = false
                val med = response?.medications?.firstOrNull()
                if (med != null) {
                    val hasDivergence = med.nameDivergent || med.dosageDivergent

                    if (!hasDivergence) {
                        var resolvedName = ""
                        if (!med.nameDivergent && med.name.isNotBlank()) {
                            resolvedName = med.name
                        }
                        if (!med.dosageDivergent && med.dosage.isNotBlank()) {
                            resolvedName = if (resolvedName.isNotBlank()) {
                                "$resolvedName ${med.dosage}".trim()
                            } else {
                                med.dosage.trim()
                            }
                        }
                        if (resolvedName.isNotBlank()) {
                            name = resolvedName
                        }
                    } else {
                        // SaMD: Divergência detectada — campo de medicamento permanece estritamente VAZIO
                        name = ""
                        prescriptionDivergenceReason = med.divergenceReason
                            ?: "Por segurança, digite os dados da sua receita médica."
                    }

                    if (med.suggestedTimes.isNotEmpty() && !med.frequencyDivergent) {
                        scheduleTime = med.suggestedTimes.first()
                    }

                    if (!med.eanBarcode.isNullOrBlank()) {
                        ean = med.eanBarcode
                        viewModel.lookupBarcode(med.eanBarcode) { anvisaMed ->
                            found = anvisaMed
                            if (!hasDivergence && name.isBlank() && anvisaMed != null) {
                                name = anvisaMed.name
                            }
                        }
                    }
                } else if (response != null && response.medications.isEmpty()) {
                    prescriptionDivergenceReason = "Não foi possível identificar medicamentos legíveis na receita. Por favor, digite os dados manualmente."
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        if (viewModel.consumePrescriptionPickerRequest()) {
            documentPicker.launch(arrayOf("image/*", "application/pdf"))
        }
    }

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
            if (!ui.isAuthenticated) {
                CareAuthenticationRequired()
                return@Column
            }

            // Exibir seletor apenas quando o usuário logado for Cuidador gerenciando dependentes
            if (ui.isCaregiver && ui.patients.size > 1) {
                CarePatientSelector(ui, viewModel::selectPatient)
            }

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

            OutlinedButton(
                onClick = { documentPicker.launch(arrayOf("image/*", "application/pdf")) },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = BragaEmeraldDark),
                border = BorderStroke(1.dp, BragaEmeraldLight)
            ) {
                Icon(Icons.Filled.UploadFile, contentDescription = null, tint = BragaEmerald)
                Spacer(Modifier.width(8.dp))
                Text("Escanear ou Anexar Receita (Dupla Checagem com IA)", fontSize = 15.sp, fontWeight = FontWeight.Medium)
            }

            OutlinedTextField(
                value = ean,
                onValueChange = {
                    ean = it.filter { c -> c.isDigit() }.take(13)
                    found = null
                },
                label = { Text("Código EAN-13 (opcional)") },
                placeholder = { Text("13 dígitos da caixa") },
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
                label = { Text("Nome do remédio *") },
                placeholder = { Text("Ex: Losartana 50mg") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = totalUnits,
                onValueChange = { totalUnits = it.filter { c -> c.isDigit() }.take(5) },
                label = { Text("Quantidade da caixa (ex: 30 comprimidos)") },
                placeholder = { Text("30") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = scheduleTime,
                onValueChange = { scheduleTime = it.take(5) },
                label = { Text("Horário da dose (HH:mm) *") },
                placeholder = { Text("08:00") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            // Indicador de progresso acolhedor da análise da receita
            if (isAnalyzingPrescription) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = BragaMintSurface),
                    border = BorderStroke(1.dp, BragaEmeraldLight)
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = BragaEmerald,
                            strokeWidth = 3.dp
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            "Analisando receita com dupla checagem de segurança...",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = BragaTextPrimary
                        )
                    }
                }
            }

            // Tratamento Obrigatório de Divergências (Alerta Âmbar — AUD-AN02 compliance via ícone)
            if (prescriptionDivergenceReason != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFBEB)),
                    border = BorderStroke(1.dp, Color(0xFFF59E0B))
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            Icons.Filled.Warning,
                            contentDescription = "Alerta de divergência",
                            tint = Color(0xFFD97706),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(
                                "Divergência na caligrafia da receita:",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = Color(0xFFB45309)
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                prescriptionDivergenceReason ?: "Por segurança, digite os dados da sua receita médica.",
                                fontSize = 13.sp,
                                color = Color(0xFF92400E)
                            )
                        }
                    }
                }
            }

            // Campo opcional moderno: Anexar Receita (PDF ou Imagem)
            if (prescriptionDocumentUri == null) {
                OutlinedButton(
                    onClick = { documentPicker.launch(arrayOf("image/*", "application/pdf")) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = BragaEmeraldDark)
                ) {
                    Icon(Icons.Filled.UploadFile, contentDescription = null, tint = BragaEmerald)
                    Spacer(Modifier.width(8.dp))
                    Text("Anexar Receita (PDF ou Imagem)", fontSize = 15.sp, fontWeight = FontWeight.Medium)
                }
            } else {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = BragaMintSurface),
                    border = BorderStroke(1.dp, BragaMintBorder)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = BragaEmerald, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Receita anexada", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = BragaTextPrimary)
                            Text(prescriptionFileName ?: "Documento selecionado", fontSize = 12.sp, color = BragaTextSecondary, maxLines = 1)
                        }
                        IconButton(
                            onClick = {
                                prescriptionDocumentUri = null
                                prescriptionFileName = null
                                prescriptionDivergenceReason = null
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.Filled.Close, contentDescription = "Remover receita", tint = BragaTextSecondary)
                        }
                    }
                }
            }

            // Validação Reativa e SaMD (RDC 657/2022)
            val isNameValid = name.trim().length >= 2
            val isTimeValid = scheduleTime.trim().matches(Regex("^([01]?\\d|2[0-3]):[0-5]\\d$"))
            val canConfirm = isNameValid && isTimeValid && !isAnalyzingPrescription
            val isConfirmed = confirmed && canConfirm
            val canSave = isNameValid && isTimeValid && isConfirmed && ui.selectedPatient.canWriteMedication && !ui.loading && !isAnalyzingPrescription

            // Checkbox OBRIGATÓRIO (SaMD RDC 657/2022 mantido)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = isConfirmed,
                    onCheckedChange = { if (canConfirm) confirmed = it },
                    enabled = canConfirm,
                    colors = CheckboxDefaults.colors(
                        checkedColor = BragaEmerald,
                        disabledCheckedColor = BragaCardBorder,
                        disabledUncheckedColor = BragaCardBorder
                    )
                )
                Text(
                    "Conferido com a receita médica",
                    fontSize = 16.sp,
                    color = if (canConfirm) BragaTextPrimary else BragaTextSecondary
                )
            }

            val missingList = buildList {
                if (!isNameValid) add("o nome do remédio")
                if (!isTimeValid) add("o horário da dose (HH:mm)")
                if (!isConfirmed) add("a conferência com a receita médica")
            }
            val helperText = if (missingList.isNotEmpty()) {
                "Para cadastrar: preencha ${missingList.joinToString(" e ")}."
            } else null

            Button(
                onClick = {
                    val units = totalUnits.toIntOrNull()?.takeIf { it > 0 } ?: 30
                    viewModel.createMedication(
                        name = name.trim(),
                        totalUnits = units,
                        scheduleTimes = listOf(scheduleTime.trim()),
                        confirmedWithPrescription = confirmed,
                        barcode = found,
                        prescriptionImageUri = prescriptionDocumentUri
                    ) { ok -> if (ok) onSaved() }
                },
                enabled = canSave,
                colors = ButtonDefaults.buttonColors(
                    containerColor = BragaEmerald,
                    disabledContainerColor = BragaCardBorder,
                    disabledContentColor = BragaTextSecondary
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
            ) {
                Icon(Icons.Filled.CheckCircle, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Cadastrar remédio", fontSize = 17.sp, fontWeight = FontWeight.Bold)
            }

            helperText?.let {
                Text(
                    text = it,
                    color = BragaTextSecondary,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
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
