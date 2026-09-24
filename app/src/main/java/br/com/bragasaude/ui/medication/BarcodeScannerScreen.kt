package br.com.bragasaude.ui.medication

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material.icons.filled.NotificationsActive
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
 * Sanitiza valores de texto para blindagem anti-"null" nos inputs.
 */
private fun sanitizeInput(value: String?): String {
    if (value == null) return ""
    val trimmed = value.trim()
    return if (trimmed.equals("null", ignoreCase = true) ||
        trimmed.equals("none", ignoreCase = true) ||
        trimmed.equals("undefined", ignoreCase = true)
    ) "" else trimmed
}

/**
 * Calcula horários das doses com base no primeiro horário e no intervalo em horas.
 */
fun calculateScheduleTimes(firstTime: String, intervalHours: Int): List<String> {
    val parts = firstTime.split(":")
    val baseHour = parts.getOrNull(0)?.toIntOrNull()?.coerceIn(0, 23) ?: 8
    val baseMinute = parts.getOrNull(1)?.toIntOrNull()?.coerceIn(0, 59) ?: 0
    val dosesCount = when (intervalHours) {
        24 -> 1
        12 -> 2
        8 -> 3
        6 -> 4
        else -> (24 / intervalHours.coerceIn(1, 24)).coerceIn(1, 6)
    }
    return (0 until dosesCount).map { i ->
        val h = (baseHour + i * intervalHours) % 24
        "%02d:%02d".format(h, baseMinute)
    }
}

/**
 * Dispara alarmes no Despertador nativo do sistema Android (AlarmClock).
 */
fun setSystemAlarms(context: Context, medName: String, times: List<String>) {
    times.forEach { timeStr ->
        val parts = timeStr.split(":")
        if (parts.size == 2) {
            val hour = parts[0].toIntOrNull() ?: 8
            val minute = parts[1].toIntOrNull() ?: 0
            val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                putExtra(AlarmClock.EXTRA_HOUR, hour)
                putExtra(AlarmClock.EXTRA_MINUTES, minute)
                putExtra(AlarmClock.EXTRA_MESSAGE, "Tomar $medName")
                putExtra(AlarmClock.EXTRA_DAYS, arrayListOf(1, 2, 3, 4, 5, 6, 7)) // Todos os dias
                putExtra(AlarmClock.EXTRA_SKIP_UI, false) // Abre para confirmação do usuário
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }
}

/**
 * Dispara evento diário no Calendário nativo do sistema Android (CalendarContract).
 */
fun setSystemCalendarEvent(context: Context, medName: String, firstTime: String) {
    val parts = firstTime.split(":")
    val hour = parts.getOrNull(0)?.toIntOrNull() ?: 8
    val minute = parts.getOrNull(1)?.toIntOrNull() ?: 0
    val calendar = java.util.Calendar.getInstance().apply {
        set(java.util.Calendar.HOUR_OF_DAY, hour)
        set(java.util.Calendar.MINUTE, minute)
        set(java.util.Calendar.SECOND, 0)
    }
    val intent = Intent(Intent.ACTION_INSERT).apply {
        data = CalendarContract.Events.CONTENT_URI
        putExtra(CalendarContract.Events.TITLE, "Remédio: $medName")
        putExtra(CalendarContract.Events.DESCRIPTION, "Dose programada pelo Braga Saúde")
        putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, calendar.timeInMillis)
        putExtra(CalendarContract.Events.RRULE, "FREQ=DAILY;INTERVAL=1")
        putExtra(CalendarContract.Events.HAS_ALARM, 1)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
}

/**
 * Item reativo para cadastro de lote/remessa de medicamentos com posologia inteligente.
 */
class BatchMedicationItem(
    val id: String = UUID.randomUUID().toString(),
    initialName: String = "",
    initialTotalUnits: String = "30",
    initialScheduleTimes: List<String> = listOf("08:00"),
    initialIntervalHours: Int = 12,
    initialEan: String = "",
    initialFound: BarcodeMedication? = null,
    initialIsDivergent: Boolean = false,
    initialDivergenceReason: String? = null,
    initialSourcePages: List<Int> = emptyList(),
    initialTreatmentDurationDays: Int? = null,
    initialAnvisaRegistrationNumber: String? = null
) {
    var name by mutableStateOf(sanitizeInput(initialName))
    var totalUnits by mutableStateOf(sanitizeInput(initialTotalUnits).ifBlank { "30" })
    var intervalHours by mutableStateOf(initialIntervalHours)
    val scheduleTimes = mutableStateListOf<String>().apply {
        if (initialScheduleTimes.isNotEmpty()) {
            addAll(initialScheduleTimes.map { sanitizeInput(it).ifBlank { "08:00" } })
        } else {
            add("08:00")
        }
    }
    var ean by mutableStateOf(sanitizeInput(initialEan))
    var found by mutableStateOf(initialFound)
    var isDivergent by mutableStateOf(initialIsDivergent)
    var divergenceReason by mutableStateOf(initialDivergenceReason)
    var sourcePages by mutableStateOf(initialSourcePages)
    var treatmentDurationDays by mutableStateOf(initialTreatmentDurationDays)
    var anvisaRegistrationNumber by mutableStateOf(sanitizeInput(initialAnvisaRegistrationNumber))

    fun recalculateTimes(newIntervalHours: Int? = null) {
        if (newIntervalHours != null) {
            intervalHours = newIntervalHours
        }
        val first = scheduleTimes.firstOrNull()?.takeIf { it.matches(Regex("^([01]?\\d|2[0-3]):[0-5]\\d$")) } ?: "08:00"
        val calculated = calculateScheduleTimes(first, intervalHours)
        scheduleTimes.clear()
        scheduleTimes.addAll(calculated)
    }

    fun updateFirstTimeAndRecalculate(newFirstTime: String) {
        val sanitized = sanitizeInput(newFirstTime)
        if (scheduleTimes.isEmpty()) {
            scheduleTimes.add(sanitized)
        } else {
            scheduleTimes[0] = sanitized
        }
        if (sanitized.matches(Regex("^([01]?\\d|2[0-3]):[0-5]\\d$"))) {
            val calculated = calculateScheduleTimes(sanitized, intervalHours)
            scheduleTimes.clear()
            scheduleTimes.addAll(calculated)
        }
    }
}

/**
 * Scanner e Cadastro de Medicamentos com Suporte a Remessa Multi-Medicamentos (Care OS — D62 / Fase B).
 *
 * TRAVA REGULATÓRIA (RDC 657/2022 & CDC Art. 14):
 * - O leitor lê exclusivamente o código de barras EAN-13 da caixa/embalagem (ANVISA).
 * - O app NUNCA identifica comprimido solto por foto.
 * - Desburocratizado: sem campos cartoriais desnecessários (CRM, validade, fotos soltas).
 * - Checkbox de conferência da receita mantido (requisito SaMD).
 * - Suporte a remessa com múltiplos medicamentos, deduplicação entre páginas de receita,
 *   posologia inteligente (chips 24h, 12h, 8h, 6h), integração nativa com AlarmClock e CalendarContract.
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
                            val interval = med.frequencyIntervalHours ?: when (med.suggestedTimes.size) {
                                1 -> 24
                                2 -> 12
                                3 -> 8
                                4 -> 6
                                else -> 12
                            }
                            val times = if (med.suggestedTimes.isNotEmpty() && !med.frequencyDivergent) {
                                med.suggestedTimes
                            } else {
                                calculateScheduleTimes("08:00", interval)
                            }
                            val divergenceReason = if (hasDivergence) {
                                med.divergenceReason ?: "Caligrafia médica incerta: por favor, digite o nome e a dosagem deste remédio."
                            } else null

                            val item = BatchMedicationItem(
                                initialName = sanitizeInput(resolvedName),
                                initialTotalUnits = "30",
                                initialScheduleTimes = times,
                                initialIntervalHours = interval,
                                initialEan = sanitizeInput(med.eanBarcode),
                                initialIsDivergent = hasDivergence,
                                initialDivergenceReason = divergenceReason,
                                initialSourcePages = med.sourcePages,
                                initialTreatmentDurationDays = med.treatmentDurationDays,
                                initialAnvisaRegistrationNumber = sanitizeInput(med.anvisaRegistrationNumber)
                            )

                            if (!med.eanBarcode.isNullOrBlank()) {
                                viewModel.lookupBarcode(med.eanBarcode) { anvisaMed ->
                                    item.found = anvisaMed
                                    if (!hasDivergence && item.name.isBlank() && anvisaMed != null) {
                                        item.name = sanitizeInput(anvisaMed.name)
                                    }
                                    if (item.anvisaRegistrationNumber.isBlank() && anvisaMed != null) {
                                        item.anvisaRegistrationNumber = sanitizeInput(anvisaMed.anvisaRegistrationNumber)
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
                targetItem.ean = sanitizeInput(code)
                targetItem.found = med
                if (med != null) {
                    targetItem.name = sanitizeInput(med.name)
                    if (targetItem.totalUnits.isBlank()) targetItem.totalUnits = "30"
                    if (targetItem.anvisaRegistrationNumber.isBlank()) {
                        targetItem.anvisaRegistrationNumber = sanitizeInput(med.anvisaRegistrationNumber)
                    }
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
                            onValueChange = { item.name = sanitizeInput(it) },
                            label = { Text("Nome do remédio *") },
                            placeholder = { Text("Ex: Losartana 50mg") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = item.totalUnits,
                            onValueChange = { item.totalUnits = it.filter { c -> c.isDigit() }.take(5) },
                            label = { Text("Quantidade da caixa") },
                            placeholder = { Text("30") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        // Posologia Inteligente: Seletor de Frequência Rápido
                        Text(
                            text = "Frequência e Posologia",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            color = BragaTextPrimary
                        )

                        val frequencyOptions = listOf(
                            24 to "1x ao dia (24h)",
                            12 to "2x ao dia (12h)",
                            8 to "3x ao dia (8h)",
                            6 to "4x ao dia (6h)"
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            frequencyOptions.forEach { (hours, label) ->
                                val selected = item.intervalHours == hours
                                FilterChip(
                                    selected = selected,
                                    onClick = { item.recalculateTimes(hours) },
                                    label = {
                                        Text(
                                            text = label,
                                            fontSize = 12.sp,
                                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                                        )
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = BragaEmerald,
                                        selectedLabelColor = Color.White,
                                        containerColor = BragaMintSurface,
                                        labelColor = BragaTextPrimary
                                    ),
                                    border = FilterChipDefaults.filterChipBorder(
                                        enabled = true,
                                        selected = selected,
                                        borderColor = if (selected) BragaEmerald else BragaCardBorder,
                                        selectedBorderColor = BragaEmerald
                                    )
                                )
                            }
                        }

                        // Campos de Horários Dinâmicos
                        if (item.scheduleTimes.size <= 1) {
                            OutlinedTextField(
                                value = item.scheduleTimes.getOrElse(0) { "08:00" },
                                onValueChange = { newTime ->
                                    item.updateFirstTimeAndRecalculate(newTime.take(5))
                                },
                                label = { Text("Horário da dose (HH:mm) *") },
                                placeholder = { Text("08:00") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else {
                            val chunkedTimes = item.scheduleTimes.chunked(2)
                            chunkedTimes.forEachIndexed { rowIndex, pair ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    pair.forEachIndexed { colIndex, _ ->
                                        val globalIndex = rowIndex * 2 + colIndex
                                        val timeValue = item.scheduleTimes.getOrElse(globalIndex) { "" }
                                        OutlinedTextField(
                                            value = timeValue,
                                            onValueChange = { newTime ->
                                                val trimmed = newTime.take(5)
                                                if (globalIndex == 0) {
                                                    item.updateFirstTimeAndRecalculate(trimmed)
                                                } else {
                                                    if (globalIndex < item.scheduleTimes.size) {
                                                        item.scheduleTimes[globalIndex] = trimmed
                                                    }
                                                }
                                            },
                                            label = { Text("Dose ${globalIndex + 1} (HH:mm) *") },
                                            placeholder = { Text("08:00") },
                                            singleLine = true,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                    if (pair.size == 1) {
                                        Spacer(Modifier.weight(1f))
                                    }
                                }
                            }
                        }

                        // Campos Opcionais / Blindados contra "null"
                        OutlinedTextField(
                            value = item.ean,
                            onValueChange = { newEan ->
                                item.ean = newEan.filter { c -> c.isDigit() }.take(13)
                                if (item.ean.length == 13) {
                                    viewModel.lookupBarcode(item.ean) { med ->
                                        item.found = med
                                        if (med != null) {
                                            if (item.name.isBlank()) {
                                                item.name = sanitizeInput(med.name)
                                            }
                                            if (item.anvisaRegistrationNumber.isBlank()) {
                                                item.anvisaRegistrationNumber = sanitizeInput(med.anvisaRegistrationNumber)
                                            }
                                        }
                                    }
                                }
                            },
                            label = { Text("Código EAN-13 (opcional)") },
                            placeholder = { Text("Opcional / Não catalogado") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = item.anvisaRegistrationNumber,
                            onValueChange = { item.anvisaRegistrationNumber = sanitizeInput(it) },
                            label = { Text("Registro ANVISA (opcional)") },
                            placeholder = { Text("Opcional / Não catalogado") },
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

                        // Botão de Agendamento Nativo (Despertador e Calendário)
                        OutlinedButton(
                            onClick = {
                                val medName = item.name.ifBlank { "Medicamento" }
                                val times = item.scheduleTimes.toList()
                                runCatching {
                                    setSystemAlarms(context, medName, times)
                                }
                                runCatching {
                                    setSystemCalendarEvent(context, medName, times.firstOrNull() ?: "08:00")
                                }
                                Toast.makeText(
                                    context,
                                    "Agendamento nativo disparado para $medName.",
                                    Toast.LENGTH_SHORT
                                ).show()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 46.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = BragaMintSurface,
                                contentColor = BragaEmeraldDark
                            ),
                            border = BorderStroke(1.dp, BragaEmeraldLight)
                        ) {
                            Icon(
                                Icons.Filled.NotificationsActive,
                                contentDescription = null,
                                tint = BragaEmerald,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "Agendar no Despertador e Calendário",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
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
            val allTimesValid = batchList.isNotEmpty() && batchList.all { item ->
                item.scheduleTimes.isNotEmpty() && item.scheduleTimes.all { t ->
                    t.trim().matches(Regex("^([01]?\\d|2[0-3]):[0-5]\\d$"))
                }
            }
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
                            scheduleTimes = item.scheduleTimes.map { it.trim() },
                            totalUnits = units,
                            eanBarcode = item.ean.takeIf { it.isNotBlank() },
                            confirmedWithPrescription = true,
                            frequencyIntervalHours = item.intervalHours,
                            treatmentDurationDays = item.treatmentDurationDays,
                            anvisaRegistrationNumber = item.anvisaRegistrationNumber.takeIf { it.isNotBlank() }
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
