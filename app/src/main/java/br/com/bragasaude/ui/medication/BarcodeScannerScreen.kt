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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
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
import br.com.bragasaude.data.remote.model.MedicationBatchItemCreateDto
import br.com.bragasaude.ui.care.CareAuthenticationRequired
import br.com.bragasaude.ui.care.CareOsViewModel
import br.com.bragasaude.ui.care.CarePatientSelector
import br.com.bragasaude.ui.theme.*
import br.com.bragasaude.util.BragaConstants
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import java.util.UUID

/**
 * Item reativo para cadastro de lote/remessa de medicamentos.
 */
class BatchMedicationItem(
    val id: String = UUID.randomUUID().toString(),
    initialName: String = "",
    initialTotalUnits: String = "30",
    initialScheduleTime: String = "08:00",
    initialEan: String = "",
    initialFound: BarcodeMedication? = null,
    initialIsDivergent: Boolean = false,
    initialDivergenceReason: String? = null,
    initialSourcePages: List<Int> = emptyList()
) {
    var name by mutableStateOf(initialName)
    var totalUnits by mutableStateOf(initialTotalUnits)
    var scheduleTime by mutableStateOf(initialScheduleTime)
    var ean by mutableStateOf(initialEan)
    var found by mutableStateOf(initialFound)
    var isDivergent by mutableStateOf(initialIsDivergent)
    var divergenceReason by mutableStateOf(initialDivergenceReason)
    var sourcePages by mutableStateOf(initialSourcePages)
}

/**
 * Scanner e Cadastro de Medicamentos com Suporte a Remessa Multi-Medicamentos (Care OS — D62 / Fase B).
 *
 * TRAVA REGULATÓRIA (RDC 657/2022 & CDC Art. 14):
 * - O leitor lê exclusivamente o código de barras EAN-13 da caixa/embalagem (ANVISA).
 * - O app NUNCA identifica comprimido solto por foto.
 * - Desburocratizado: sem campos cartoriais desnecessários (CRM, validade, fotos soltas).
 * - Checkbox de conferência da receita mantido (requisito SaMD).
 * - Suporte a remessa com múltiplos medicamentos, deduplicação entre páginas de receita e botão [+ Adicionar outro remédio].
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

    val batchList = remember {
        mutableStateListOf<BatchMedicationItem>().apply {
            add(BatchMedicationItem())
        }
    }
    var confirmed by remember { mutableStateOf(false) }
    var prescriptionDocumentUri by remember { mutableStateOf<Uri?>(null) }
    var prescriptionFileName by remember { mutableStateOf<String?>(null) }
    var isAnalyzingPrescription by remember { mutableStateOf(false) }
    var batchInfoNotice by remember { mutableStateOf<String?>(null) }

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
            batchInfoNotice = null
            val patientId = ui.selectedPatientId ?: ui.selectedPatient.id.takeIf { it.isNotBlank() } ?: BragaConstants.GUEST_UID
            viewModel.analyzePrescription(patientId, uri) { response ->
                isAnalyzingPrescription = false
                if (response != null) {
                    if (response.duplicatesMergedCount > 0) {
                        batchInfoNotice = "Foram mesclados ${response.duplicatesMergedCount} medicamentos duplicados entre as páginas da receita."
                    }
                    if (response.medications.isNotEmpty()) {
                        batchList.clear()
                        response.medications.forEach { med ->
                            val hasDivergence = med.nameDivergent || med.dosageDivergent
                            var resolvedName = ""
                            if (!hasDivergence) {
                                val n = if (!med.nameDivergent && med.name.isNotBlank()) med.name else ""
                                val d = if (!med.dosageDivergent && med.dosage.isNotBlank()) med.dosage else ""
                                resolvedName = if (n.isNotBlank() && d.isNotBlank()) {
                                    "$n $d".trim()
                                } else {
                                    (n.ifBlank { d }).trim()
                                }
                            }
                            val schedule = if (med.suggestedTimes.isNotEmpty() && !med.frequencyDivergent) {
                                med.suggestedTimes.first()
                            } else {
                                "08:00"
                            }
                            val divergenceReason = if (hasDivergence) {
                                med.divergenceReason ?: "Caligrafia médica incerta: por favor, digite o nome e a dosagem deste remédio."
                            } else null

                            val item = BatchMedicationItem(
                                initialName = resolvedName,
                                initialTotalUnits = "30",
                                initialScheduleTime = schedule,
                                initialEan = med.eanBarcode ?: "",
                                initialIsDivergent = hasDivergence,
                                initialDivergenceReason = divergenceReason,
                                initialSourcePages = med.sourcePages
                            )

                            if (!med.eanBarcode.isNullOrBlank()) {
                                viewModel.lookupBarcode(med.eanBarcode) { anvisaMed ->
                                    item.found = anvisaMed
                                    if (!hasDivergence && item.name.isBlank() && anvisaMed != null) {
                                        item.name = anvisaMed.name
                                    }
                                }
                            }
                            batchList.add(item)
                        }
                    } else {
                        batchInfoNotice = "Não foi possível identificar medicamentos legíveis na receita. Você pode preencher manualmente."
                    }
                } else {
                    batchInfoNotice = "Não foi possível analisar a receita no momento. Você pode preencher manualmente."
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
            viewModel.lookupBarcode(code) { med ->
                val targetItem = if (batchList.size == 1 && batchList[0].name.isBlank() && batchList[0].ean.isBlank()) {
                    batchList[0]
                } else {
                    BatchMedicationItem().also { batchList.add(it) }
                }
                targetItem.ean = code
                targetItem.found = med
                if (med != null) {
                    targetItem.name = med.name
                    if (targetItem.totalUnits.isBlank()) targetItem.totalUnits = "30"
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
                title = { Text("Cadastrar Remédios", fontWeight = FontWeight.SemiBold) },
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
                        "Aponte a câmera para o código de barras da CAIXA ou anexe a receita médica. " +
                            "Identificamos só a embalagem e o documento — nunca o comprimido solto.",
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
                Text("Escanear código de barras da caixa", color = Color.White, fontSize = 17.sp)
            }

            // Campo para anexar receita (PDF ou Imagem)
            if (prescriptionDocumentUri == null) {
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
                                batchInfoNotice = null
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.Filled.Close, contentDescription = "Remover receita", tint = BragaTextSecondary)
                        }
                    }
                }
            }

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

            // Notificação informativa do lote / deduplicação (se houver)
            batchInfoNotice?.let { notice ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = BragaMintSurface),
                    border = BorderStroke(1.dp, BragaMintBorder)
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.Info,
                            contentDescription = null,
                            tint = BragaEmerald,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = notice,
                            fontSize = 14.sp,
                            color = BragaTextPrimary
                        )
                    }
                }
            }

            HorizontalDivider()

            // Lista de cartões de medicamentos da remessa
            batchList.forEachIndexed { index, item ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, BragaCardBorder)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Filled.Medication,
                                    contentDescription = null,
                                    tint = BragaEmerald,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = "Medicamento ${index + 1}",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = BragaTextPrimary
                                )
                            }
                            if (batchList.size > 1) {
                                IconButton(
                                    onClick = { batchList.removeAt(index) },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        Icons.Filled.Delete,
                                        contentDescription = "Remover medicamento",
                                        tint = BragaEmergencyOrange
                                    )
                                }
                            }
                        }

                        if (item.sourcePages.isNotEmpty()) {
                            Text(
                                text = "Identificado na(s) página(s): ${item.sourcePages.joinToString(", ")}",
                                fontSize = 12.sp,
                                color = BragaTextSecondary
                            )
                        }

                        if (item.isDivergent) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFBEB)),
                                border = BorderStroke(1.dp, Color(0xFFF59E0B))
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Icon(
                                        Icons.Filled.Warning,
                                        contentDescription = "Alerta de divergência",
                                        tint = Color(0xFFD97706),
                                        modifier = Modifier.size(22.dp)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            "Caligrafia médica incerta:",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = Color(0xFFB45309)
                                        )
                                        Spacer(Modifier.height(2.dp))
                                        Text(
                                            item.divergenceReason ?: "Por segurança, digite o nome e a dosagem deste remédio.",
                                            fontSize = 12.sp,
                                            color = Color(0xFF92400E)
                                        )
                                    }
                                }
                            }
                        }

                        OutlinedTextField(
                            value = item.name,
                            onValueChange = { item.name = it },
                            label = { Text("Nome do remédio *") },
                            placeholder = { Text("Ex: Losartana 50mg") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedTextField(
                                value = item.totalUnits,
                                onValueChange = { item.totalUnits = it.filter { c -> c.isDigit() }.take(5) },
                                label = { Text("Quantidade") },
                                placeholder = { Text("30") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = item.scheduleTime,
                                onValueChange = { item.scheduleTime = it.take(5) },
                                label = { Text("Horário (HH:mm) *") },
                                placeholder = { Text("08:00") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        OutlinedTextField(
                            value = item.ean,
                            onValueChange = { newEan ->
                                item.ean = newEan.filter { c -> c.isDigit() }.take(13)
                                if (item.ean.length == 13) {
                                    viewModel.lookupBarcode(item.ean) { med ->
                                        item.found = med
                                        if (med != null && item.name.isBlank()) {
                                            item.name = med.name
                                        }
                                    }
                                }
                            },
                            label = { Text("Código EAN-13 (opcional)") },
                            placeholder = { Text("13 dígitos da caixa") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        item.found?.let { med ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                                colors = CardDefaults.cardColors(containerColor = BragaMint),
                                border = BorderStroke(1.dp, BragaEmeraldLight)
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text("Encontrado na ANVISA:", fontSize = 12.sp, color = BragaTextSecondary)
                                    Text(med.name, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = BragaTextPrimary)
                                    med.activePrinciple?.takeIf { it.isNotBlank() }?.let {
                                        Text("Princípio ativo: $it", fontSize = 13.sp)
                                    }
                                    med.concentration?.takeIf { it.isNotBlank() }?.let {
                                        Text("Concentração: $it", fontSize = 13.sp)
                                    }
                                    med.manufacturer?.takeIf { it.isNotBlank() }?.let {
                                        Text("Laboratório: $it", fontSize = 13.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Botão [+ Adicionar outro medicamento à remessa]
            OutlinedButton(
                onClick = { batchList.add(BatchMedicationItem()) },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = BragaEmeraldDark),
                border = BorderStroke(1.dp, BragaEmerald)
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, tint = BragaEmerald)
                Spacer(Modifier.width(8.dp))
                Text("Adicionar outro medicamento à remessa", fontSize = 15.sp, fontWeight = FontWeight.Medium)
            }

            // Validação Reativa e SaMD (RDC 657/2022)
            val allNamesValid = batchList.isNotEmpty() && batchList.all { it.name.trim().length >= 2 }
            val allTimesValid = batchList.isNotEmpty() && batchList.all { it.scheduleTime.trim().matches(Regex("^([01]?\\d|2[0-3]):[0-5]\\d$")) }
            val canConfirm = allNamesValid && allTimesValid && !isAnalyzingPrescription
            val isConfirmed = confirmed && canConfirm
            val canSave = allNamesValid && allTimesValid && isConfirmed && ui.selectedPatient.canWriteMedication && !ui.loading && !isAnalyzingPrescription

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
                if (!allNamesValid) add("o nome de todos os remédios")
                if (!allTimesValid) add("o horário válido (HH:mm) de todos os remédios")
                if (!isConfirmed) add("a conferência com a receita médica")
            }
            val helperText = if (missingList.isNotEmpty()) {
                "Para cadastrar: preencha ${missingList.joinToString(" e ")}."
            } else null

            val submitButtonText = if (batchList.size > 1) {
                "Cadastrar remessa (${batchList.size} remédios)"
            } else {
                "Cadastrar remédio"
            }

            Button(
                onClick = {
                    val patientId = ui.selectedPatientId ?: ui.selectedPatient.id.takeIf { it.isNotBlank() } ?: BragaConstants.GUEST_UID
                    val dtos = batchList.map { item ->
                        val units = item.totalUnits.toIntOrNull()?.takeIf { it > 0 } ?: 30
                        MedicationBatchItemCreateDto(
                            name = item.name.trim(),
                            scheduleTimes = listOf(item.scheduleTime.trim()),
                            totalUnits = units,
                            eanBarcode = item.ean.takeIf { it.isNotBlank() },
                            confirmedWithPrescription = true
                        )
                    }
                    viewModel.createMedicationsBatch(
                        patientId = patientId,
                        items = dtos,
                        onSuccess = { onSaved() },
                        onError = { /* exibido via feedback */ }
                    )
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
                Text(submitButtonText, fontSize = 17.sp, fontWeight = FontWeight.Bold)
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
