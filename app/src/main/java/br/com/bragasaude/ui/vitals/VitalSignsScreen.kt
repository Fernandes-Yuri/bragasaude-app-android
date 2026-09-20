package br.com.bragasaude.ui.vitals

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import br.com.bragasaude.data.remote.model.RemoteVitalSign
import br.com.bragasaude.domain.util.BloodPressureParser
import br.com.bragasaude.domain.util.GlucoseClassifier
import br.com.bragasaude.domain.model.GlucoseContext
import br.com.bragasaude.ui.components.MetricInfoIcon
import br.com.bragasaude.ui.components.MetricInfoCatalog
import br.com.bragasaude.ui.components.EmeraldHeaderBanner
import br.com.bragasaude.ui.theme.BragaBackground
import br.com.bragasaude.ui.theme.BragaEmerald
import br.com.bragasaude.ui.theme.BragaMint
import br.com.bragasaude.ui.theme.BragaMintBorder
import br.com.bragasaude.ui.theme.BragaTextPrimary
import br.com.bragasaude.ui.theme.BragaTextSecondary
import br.com.bragasaude.ui.theme.Success
import br.com.bragasaude.ui.theme.TealPrimary
import br.com.bragasaude.ui.theme.Warning

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VitalSignsScreen(
    viewModel: VitalSignsViewModel = hiltViewModel(),
    initialType: String? = null, // "PRESSURE", "GLUCOSE", "HYDRATION"
    initialValue: String? = null, // ex: "120/80" ou "105"
    onBack: () -> Unit
) {
    var bloodPressure by remember(initialValue) { 
        mutableStateOf(if (initialType != "GLUCOSE" && initialValue != null) initialValue else "") 
    } // Formato "120/80"
    var glucose by remember(initialValue) { 
        mutableStateOf(if (initialType == "GLUCOSE" && initialValue != null) initialValue else "") 
    }
    var hydration by remember { mutableStateOf("") }
    var abbreviatedPressure by remember { mutableStateOf(false) }
    var showConfirmation by remember { mutableStateOf(false) }
    var sosRecommendation by remember { mutableStateOf<br.com.bragasaude.domain.HealthEngine.HealthRecommendation?>(null) }
    var milestoneMessage by remember { mutableStateOf<String?>(null) }
    // AUD-AN09: alerta SOS in-app — antes o coletor tinha corpo vazio e os
    // eventos de emergência eram descartados silenciosamente.
    var sosMessage by remember { mutableStateOf<String?>(null) }
    
    var selectedTab by remember { 
        mutableIntStateOf(
            when (initialType) {
                "GLUCOSE" -> 1
                else -> 0
            }
        ) 
    }

    val allVitals by viewModel.allVitals.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()

    val currentType = initialType ?: when(selectedTab) {
        0 -> "PRESSURE"
        else -> "GLUCOSE"
    }

    val pressureHistory = remember(allVitals) {
        allVitals.mapNotNull { it.systolicPressure?.toDouble() }.reversed()
    }
    val glucoseHistory = remember(allVitals) {
        allVitals.mapNotNull { it.glucoseLevel?.toDouble() }.reversed()
    }
    val currentVitalsList = remember(allVitals, currentType) {
        when (currentType) {
            "PRESSURE" -> allVitals.filter { it.systolicPressure != null }
            "GLUCOSE" -> allVitals.filter { it.glucoseLevel != null }
            else -> allVitals.filter { it.systolicPressure != null || it.glucoseLevel != null }
        }
    }

    // Helper para extrair sistólica e diastólica
    val bpPair = remember(bloodPressure) {
        val numbers = "\\d+".toRegex().findAll(bloodPressure).map { it.value }.toList()
        if (numbers.size >= 2) Pair(numbers[0], numbers[1]) else if (numbers.size == 1) Pair(numbers[0], "") else Pair("", "")
    }

    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(viewModel.sosAlert, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.sosAlert.collect { message ->
                // AUD-AN09: antes este coletor era vazio — o canal de SOS
                // estava morto das duas pontas. Agora surfamos a emergência.
                sosMessage = message
            }
        }
    }

    // Coleta do StateFlow respeitando estritamente o ciclo de vida Android
    val latestRecommendation by viewModel.latestRecommendation.collectAsStateWithLifecycle(initialValue = null)
    LaunchedEffect(latestRecommendation) {
        latestRecommendation?.let { sosRecommendation = it }
    }

    LaunchedEffect(viewModel.milestoneAlert, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.milestoneAlert.collect { message ->
                milestoneMessage = message
            }
        }
    }

    Scaffold(
        containerColor = BragaBackground,
        topBar = {
            Column {
                val bannerTitle = when(initialType) {
                    "PRESSURE" -> "Registrar Pressão"
                    "GLUCOSE" -> "Registrar Glicose"
                    else -> "Registrar Sinais Vitais"
                }
                EmeraldHeaderBanner(
                    title = bannerTitle,
                    subtitle = "Diretrizes Clínicas SBC / OMS",
                    onBack = onBack
                )
                if (initialType == null) {
                    SecondaryTabRow(
                        selectedTabIndex = selectedTab,
                        containerColor = BragaMint.copy(alpha = 0.6f)
                    ) {
                        Tab(
                            selected = selectedTab == 0,
                            onClick = { selectedTab = 0 },
                            text = { Text("Pressão", fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal, color = if (selectedTab == 0) BragaEmerald else BragaTextSecondary) }
                        )
                        Tab(
                            selected = selectedTab == 1,
                            onClick = { selectedTab = 1 },
                            text = { Text("Glicose", fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal, color = if (selectedTab == 1) BragaEmerald else BragaTextSecondary) }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = { viewModel.refresh() },
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
            item { Spacer(Modifier.height(8.dp)) }
            
            item {
                Text(
                    "Insira o valor abaixo ou utilize o botão de voz.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Gray,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            if (selectedTab == 0) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Diretriz SBC / OMS (mmHg)",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        MetricInfoIcon(metricInfo = MetricInfoCatalog.bloodPressure)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedTextField(
                            value = bpPair.first,
                            onValueChange = { newSystolic -> 
                                bloodPressure = "$newSystolic/${bpPair.second}"
                            },
                            label = { Text("Sistólica") },
                            placeholder = { Text("120") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(16.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = BragaEmerald,
                                focusedLabelColor = BragaEmerald,
                                cursorColor = BragaEmerald
                            )
                        )
                        OutlinedTextField(
                            value = bpPair.second,
                            onValueChange = { newDiastolic -> 
                                bloodPressure = "${bpPair.first}/$newDiastolic"
                            },
                            label = { Text("Diastólica") },
                            placeholder = { Text("80") },
                            suffix = { Text("mmHg", style = MaterialTheme.typography.bodySmall, color = BragaTextSecondary) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(16.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = BragaEmerald,
                                focusedLabelColor = BragaEmerald,
                                cursorColor = BragaEmerald
                            )
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = abbreviatedPressure, onCheckedChange = { abbreviatedPressure = it })
                        Text("Usei forma abreviada (12/8 equivale a 120/80 mmHg)", style = MaterialTheme.typography.bodySmall)
                    }
                    val sys = bpPair.first.toIntOrNull()
                    val dia = bpPair.second.toIntOrNull()
                    if (sys != null && dia != null && sys > 0 && dia > 0) {
                        Spacer(Modifier.height(10.dp))
                        BloodPressureRiskBadge(if (abbreviatedPressure) sys * 10 else sys, if (abbreviatedPressure) dia * 10 else dia)
                    }
                }
            } else if (selectedTab == 1) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Diretriz SBD / ADA (mg/dL)",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        MetricInfoIcon(metricInfo = MetricInfoCatalog.fastingGlucose)
                    }

                    VitalInputRow(
                        label = "Glicose",
                        value = glucose,
                        onValueChange = { glucose = it },
                        unit = "mg/dL",
                        shape = RoundedCornerShape(16.dp)
                    )

                    val glu = glucose.toIntOrNull()
                    if (glu != null && glu > 0) {
                        Spacer(Modifier.height(10.dp))
                        GlucoseRiskBadge(glu)
                    }
                }
            }

            item { Spacer(Modifier.height(16.dp)) }

            item {
                val canSave = if (currentType == "PRESSURE") {
                    bpPair.first.isNotEmpty() && bpPair.second.isNotEmpty()
                } else {
                    glucose.isNotEmpty()
                }

                Button(
                    onClick = { showConfirmation = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    enabled = !isLoading && canSave,
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = BragaEmerald)
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("Salvar", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Gráficos de Evolução (7D, 15D, 30D)
            item {
                when (currentType) {
                    "PRESSURE" -> {
                        if (pressureHistory.isNotEmpty()) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    "Evolução da Pressão Arterial",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
                                )
                                br.com.bragasaude.ui.components.SimpleTrendChart(
                                    data = pressureHistory,
                                    label = "Sistólica",
                                    color = MaterialTheme.colorScheme.primary,
                                    targetValue = 120.0,
                                    unit = "mmHg",
                                    showPeriodSelector = true
                                )
                            }
                        }
                    }
                    "GLUCOSE" -> {
                        if (glucoseHistory.isNotEmpty()) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    "Evolução da Glicemia",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
                                )
                                br.com.bragasaude.ui.components.SimpleTrendChart(
                                    data = glucoseHistory,
                                    label = "Glicemia",
                                    color = Color(0xFFEA580C),
                                    targetValue = 99.0,
                                    unit = "mg/dL",
                                    showPeriodSelector = true
                                )
                            }
                        }
                    }
                }
            }

            if (currentVitalsList.isNotEmpty()) {
                item {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
                    Box(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            "Histórico de ${if (currentType == "PRESSURE") "Pressão Arterial" else "Glicemia"}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.align(Alignment.CenterStart)
                        )
                    }
                }

                items(currentVitalsList.take(15)) { vital ->
                    VitalHistoryCard(vital = vital, metricType = currentType)
                }
            }
            
            item { Spacer(Modifier.height(32.dp)) }
        }
        }
    }

    // Dialogs
    if (sosRecommendation != null) {
        val rec = sosRecommendation!!
        AlertDialog(
            onDismissRequest = { sosRecommendation = null },
            title = { 
                Text(
                    if (rec.isEmergency) "Atenção Redobrada" else "Orientação de Saúde", 
                    color = if (rec.isEmergency) MaterialTheme.colorScheme.error else Color(0xFFFF9800)
                ) 
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.verticalScroll(rememberScrollState())
                ) {
                    Text(rec.message, fontWeight = FontWeight.Bold)
                    Text(rec.action)
                    if (rec.recheckMinutes != null) {
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = MaterialTheme.shapes.small
                         ) {
                            Text(
                                "Recomendação: Medir novamente em ${rec.recheckMinutes} minutos.",
                                modifier = Modifier.padding(8.dp),
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { sosRecommendation = null },
                    colors = if (rec.isEmergency) ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error) else ButtonDefaults.buttonColors()
                ) { 
                    Text("Entendido") 
                }
            }
        )
    }

    if (milestoneMessage != null) {
        AlertDialog(
            onDismissRequest = { milestoneMessage = null },
            title = { Text("Conquista", color = MaterialTheme.colorScheme.primary) },
            text = { Text(milestoneMessage!!) },
            confirmButton = {
                Button(onClick = { milestoneMessage = null }) { Text("Parabéns!") }
            }
        )
    }

    // AUD-AN09: segunda camada de aviso — a notificação do sistema pode ser
    // perdida; o alerta in-app garante que o idoso veja a emergência.
    if (sosMessage != null) {
        AlertDialog(
            onDismissRequest = { sosMessage = null },
            title = { Text("Atenção imediata", color = MaterialTheme.colorScheme.error) },
            text = { Text(sosMessage!!) },
            confirmButton = {
                Button(onClick = { sosMessage = null }) { Text("Entendido") }
            }
        )
    }

    if (showConfirmation) {
        val sysRaw = bpPair.first.toIntOrNull() ?: 0
        val diaRaw = bpPair.second.toIntOrNull() ?: 0
        val sysNorm = if (abbreviatedPressure) sysRaw * 10 else sysRaw
        val diaNorm = if (abbreviatedPressure) diaRaw * 10 else diaRaw

        AlertDialog(
            onDismissRequest = { showConfirmation = false },
            title = { Text("Confirmar ${if (currentType == "PRESSURE") "Pressão Arterial" else "Glicemia"}") },
            text = {
                Column {
                    if (currentType == "PRESSURE" && bpPair.first.isNotEmpty() && bpPair.second.isNotEmpty()) {
                        val displayNote = if (sysRaw != sysNorm || diaRaw != diaNorm) " (${bpPair.first}/${bpPair.second})" else ""
                        Text("Pressão: $sysNorm/$diaNorm mmHg$displayNote", fontWeight = FontWeight.Bold)
                    } else if (currentType == "GLUCOSE" && glucose.isNotEmpty()) {
                        Text("Glicose: $glucose mg/dL", fontWeight = FontWeight.Bold)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (currentType == "PRESSURE") {
                        viewModel.saveVitalSigns(
                            sysNorm,
                            diaNorm,
                            0,
                            0
                        )
                        bloodPressure = ""
                    } else {
                        viewModel.saveVitalSigns(
                            0,
                            0,
                            glucose.toIntOrNull() ?: 0,
                            0
                        )
                        glucose = ""
                    }
                    showConfirmation = false
                }) {
                    Text("Confirmar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmation = false }) { Text("Corrigir") }
            }
        )
    }
}

@Composable
fun VitalInputRow(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    unit: String,
    placeholder: String? = null,
    shape: Shape = MaterialTheme.shapes.medium
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it) } },
        suffix = { Text(unit, style = MaterialTheme.typography.bodySmall, color = BragaTextSecondary) },
        keyboardOptions = KeyboardOptions(keyboardType = if (placeholder == null) KeyboardType.Number else KeyboardType.Text),
        modifier = Modifier.fillMaxWidth(),
        shape = shape,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = BragaEmerald,
            focusedLabelColor = BragaEmerald,
            cursorColor = BragaEmerald
        )
    )
}

@Composable
fun VitalHistoryCard(vital: RemoteVitalSign, metricType: String = "PRESSURE") {
    var statusText = "Normal"
    var statusColor = Success

    if (metricType == "PRESSURE") {
        val sys = vital.systolicPressure
        val dia = vital.diastolicPressure
        if (sys != null && dia != null) {
            if (sys !in 90..140 || dia !in 60..90) {
                statusText = "Atenção"
                statusColor = Warning
            }
        }
    } else {
        val glu = vital.glucoseLevel
        if (glu != null) {
            if (glu !in 70..140) {
                statusText = "Atenção"
                statusColor = Warning
            }
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = BorderStroke(1.dp, BragaMintBorder)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = vital.measuredAt?.take(10) ?: "",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Gray
                    )
                    Text(
                        text = vital.measuredAt?.substringAfter("T")?.take(5) ?: "",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Gray
                    )
                }
                
                Surface(
                    shape = RoundedCornerShape(percent = 50),
                    color = statusColor.copy(alpha = 0.1f),
                    contentColor = statusColor
                ) {
                    Text(
                        text = statusText,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            
            Spacer(Modifier.height(8.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (metricType == "PRESSURE" && vital.systolicPressure != null) {
                    VitalValueBadge("Pressão Arterial", "${vital.systolicPressure}/${vital.diastolicPressure}", "mmHg")
                } else if (metricType == "GLUCOSE" && vital.glucoseLevel != null) {
                    VitalValueBadge("Glicemia", "${vital.glucoseLevel}", "mg/dL")
                }
            }
        }
    }
}

@Composable
fun VitalValueBadge(label: String, value: String, unit: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
        Text("$value $unit", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun BloodPressureRiskBadge(sys: Int, dia: Int) {
    val normSys = sys
    val normDia = dia
    val result = remember(normSys, normDia) {
        BloodPressureParser.classify(normSys, normDia)
    }
    val badgeColor = Color(result.category.colorHex)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = badgeColor.copy(alpha = 0.08f)),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, badgeColor.copy(alpha = 0.35f))
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Surface(
                        shape = CircleShape,
                        color = badgeColor,
                        modifier = Modifier.size(10.dp)
                    ) {}
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = result.category.displayName,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleSmall,
                        color = badgeColor
                    )
                }
                MetricInfoIcon(metricInfo = MetricInfoCatalog.bloodPressure)
            }

            Surface(
                color = badgeColor.copy(alpha = 0.15f),
                shape = RoundedCornerShape(6.dp)
            ) {
                Text(
                    text = "Diretriz: ${result.institution}",
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = badgeColor
                )
            }

            Text(
                text = result.explanatoryDetail,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = result.category.recommendation,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 17.sp
            )
        }
    }
}

@Composable
fun GlucoseRiskBadge(glucose: Int) {
    val result = remember(glucose) {
        GlucoseClassifier.classifyFasting(glucose)
    }
    val badgeColor = Color(result.category.colorHex)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = badgeColor.copy(alpha = 0.08f)),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, badgeColor.copy(alpha = 0.35f))
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Surface(
                        shape = CircleShape,
                        color = badgeColor,
                        modifier = Modifier.size(10.dp)
                    ) {}
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = result.category.displayName,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleSmall,
                        color = badgeColor
                    )
                }
                MetricInfoIcon(metricInfo = MetricInfoCatalog.fastingGlucose)
            }

            Surface(
                color = badgeColor.copy(alpha = 0.15f),
                shape = RoundedCornerShape(6.dp)
            ) {
                Text(
                    text = "Diretriz: ${result.institution}",
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = badgeColor
                )
            }

            Text(
                text = result.explanatoryDetail,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = result.category.recommendation,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 17.sp
            )
        }
    }
}
