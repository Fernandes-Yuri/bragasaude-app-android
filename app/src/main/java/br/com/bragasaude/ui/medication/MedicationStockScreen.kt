package br.com.bragasaude.ui.medication

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import br.com.bragasaude.data.local.MedicationEntity
import br.com.bragasaude.ui.care.CareOsViewModel
import br.com.bragasaude.ui.care.MorningCheckInSheet
import br.com.bragasaude.ui.care.CareAuthenticationRequired
import br.com.bragasaude.ui.care.CarePatientSelector
import br.com.bragasaude.ui.theme.*
import kotlinx.coroutines.flow.collectLatest

/**
 * Gestão Visual de Estoque (Care OS — D62).
 *
 * Card de medicamento com pílulas restantes e tag de alerta quando
 * currentUnits <= doses_por_dia * alertThresholdDays.
 *
 * Regras de acessibilidade 60+: alvos de toque de no mínimo 48dp, contraste
 * elevado, tipografia generosa e ZERO emojis (Material Symbols).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MedicationStockScreen(
    onBack: () -> Unit,
    onScanNew: () -> Unit,
    viewModel: CareOsViewModel = hiltViewModel()
) {
    val ui by viewModel.ui.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showCheckIn by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.ui.collectLatest { state ->
            state.message?.let {
                snackbarHostState.showSnackbar(it)
                viewModel.consumeMessage()
            }
        }
    }

    if (showCheckIn) {
        MorningCheckInSheet(onDismiss = { showCheckIn = false })
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Meus Remédios", fontWeight = FontWeight.SemiBold) },
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
        floatingActionButton = {
            if (ui.isAuthenticated && ui.selectedPatient.canWriteMedication) {
                ExtendedFloatingActionButton(
                    onClick = onScanNew,
                    icon = { Icon(Icons.Filled.QrCodeScanner, contentDescription = null) },
                    text = { Text("Escanear caixa") },
                    containerColor = BragaEmerald,
                    contentColor = Color.White,
                    modifier = Modifier.heightIn(min = 56.dp)
                )
            }
        },
        containerColor = BragaBackground
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (!ui.isAuthenticated) {
                CareAuthenticationRequired()
                return@Column
            }

            CarePatientSelector(ui, viewModel::selectPatient)

            // Ação de check-in matinal (Cena C37)
            CheckInCard(ui.checkInDoneToday) { showCheckIn = true }

            if (ui.medications.isEmpty()) {
                EmptyStockCard(ui.selectedPatient.canWriteMedication, onScanNew)
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 96.dp)
                ) {
                    items(ui.medications, key = { it.id }) { med ->
                        MedicationStockCard(
                            medication = med,
                            daysRemaining = viewModel.daysRemaining(med),
                            isCritical = viewModel.isStockCritical(med),
                            onTake = { viewModel.takeDose(med.id) },
                            onRestock = { units -> viewModel.restock(med.id, units) },
                            canTake = ui.selectedPatient.canTakeMedication,
                            canRestock = ui.selectedPatient.canWriteMedication,
                            loading = ui.loading
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CheckInCard(doneToday: Boolean, onOpenCheckIn: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = BragaMintSurface),
        border = BorderStroke(1.dp, BragaMintBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (doneToday) Icons.Filled.CheckCircle else Icons.Filled.Medication,
                contentDescription = null,
                tint = BragaEmeraldDark,
                modifier = Modifier.size(40.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    if (doneToday) "Check-in de hoje concluído" else "Check-in do Dia",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 17.sp,
                    color = BragaTextPrimary
                )
                Text(
                    if (doneToday) "Você já compartilhou como está hoje."
                    else "Como foi sua noite e como você está se sentindo?",
                    fontSize = 14.sp,
                    color = BragaTextSecondary
                )
            }
            if (!doneToday) {
                Button(
                    onClick = onOpenCheckIn,
                    colors = ButtonDefaults.buttonColors(containerColor = BragaEmerald),
                    modifier = Modifier.heightIn(min = 48.dp)
                ) { Text("Falar", color = Color.White) }
            }
        }
    }
}

@Composable
private fun EmptyStockCard(canWrite: Boolean, onScanNew: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = BragaCardSurface),
        border = BorderStroke(1.dp, BragaCardBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                Icons.Filled.Medication,
                contentDescription = null,
                tint = BragaEmerald,
                modifier = Modifier.size(48.dp)
            )
            Text(
                "Nenhum remédio cadastrado",
                fontWeight = FontWeight.SemiBold,
                fontSize = 18.sp,
                color = BragaTextPrimary
            )
            Text(
                "Escanee o código de barras da caixa para cadastrar com a receita em mãos.",
                fontSize = 15.sp,
                color = BragaTextSecondary
            )
            if (canWrite) {
                Button(
                    onClick = onScanNew,
                    colors = ButtonDefaults.buttonColors(containerColor = BragaEmerald),
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Icon(Icons.Filled.QrCodeScanner, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Escanear primeira caixa", color = Color.White)
                }
            } else {
                Text("Seu vínculo permite consultar o estoque, sem alterar medicamentos.", color = BragaTextSecondary)
            }
        }
    }
}

@Composable
private fun MedicationStockCard(
    medication: MedicationEntity,
    daysRemaining: Double,
    isCritical: Boolean,
    onTake: () -> Unit,
    onRestock: (Int) -> Unit,
    canTake: Boolean,
    canRestock: Boolean,
    loading: Boolean
) {
    var showRestock by remember { mutableStateOf(false) }
    var restockQty by remember { mutableStateOf(medication.totalUnits.toString()) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isCritical) BragaEmergencyLight else BragaCardSurface
        ),
        border = BorderStroke(
            1.dp,
            if (isCritical) BragaEmergencyOrange else BragaCardBorder
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.Top) {
                Icon(
                    Icons.Filled.Medication,
                    contentDescription = null,
                    tint = BragaEmeraldDark,
                    modifier = Modifier.size(32.dp)
                )
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        medication.name,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = BragaTextPrimary
                    )
                    medication.activePrinciple?.takeIf { it.isNotBlank() }?.let {
                        Text(it, fontSize = 14.sp, color = BragaTextSecondary)
                    }
                    medication.manufacturer?.takeIf { it.isNotBlank() }?.let {
                        Text(it, fontSize = 13.sp, color = BragaTextSecondary)
                    }
                }
                // Pílulas restantes — destaque visual grande
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        "${medication.currentUnits}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 28.sp,
                        color = if (isCritical) BragaEmergencyOrange else BragaEmeraldDark
                    )
                    Text(
                        "comprimidos restantes",
                        fontSize = 12.sp,
                        color = BragaTextSecondary
                    )
                }
            }

            // Tag de alerta de reposição
            if (isCritical) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.Warning,
                        contentDescription = null,
                        tint = BragaEmergencyOrange,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    val days = if (daysRemaining.isInfinite()) "—" else String.format("%.0f", daysRemaining)
                    Text(
                        "Restam $days dias de uso — repor estoque",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                        color = BragaEmergencyOrange
                    )
                }
            } else if (!daysRemaining.isInfinite()) {
                Text(
                    "Aproximadamente ${String.format("%.0f", daysRemaining)} dias de uso restantes",
                    fontSize = 14.sp,
                    color = BragaTextSecondary
                )
            }

            if (medication.confirmedWithPrescription) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = Success,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "Conferido com a receita médica",
                        fontSize = 13.sp,
                        color = Success
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onTake,
                    enabled = !loading && medication.currentUnits > 0 && canTake,
                    colors = ButtonDefaults.buttonColors(containerColor = BragaEmerald),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 52.dp)
                ) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Tomar dose", color = Color.White, fontSize = 16.sp)
                }
                OutlinedButton(
                    onClick = { showRestock = !showRestock },
                    enabled = canRestock,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 52.dp)
                ) { Text("Reabastecer", fontSize = 16.sp) }
            }

            if (showRestock) {
                OutlinedTextField(
                    value = restockQty,
                    onValueChange = { restockQty = it.filter { c -> c.isDigit() } },
                    label = { Text("Quantidade da nova caixa") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Button(
                    onClick = {
                        restockQty.toIntOrNull()?.let {
                            if (it > 0) {
                                onRestock(it)
                                showRestock = false
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BragaEmeraldDark),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                ) { Text("Confirmar reposição", color = Color.White) }
            }
        }
    }
}
