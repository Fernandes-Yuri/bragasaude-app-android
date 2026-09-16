package br.com.bragasaude.ui.registration

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import br.com.bragasaude.domain.FamilyConnectionCode
import br.com.bragasaude.ui.auth.AuthViewModel
import br.com.bragasaude.ui.components.EmeraldHeaderBanner
import br.com.bragasaude.ui.family.CaregiverDashboardViewModel
import br.com.bragasaude.ui.profile.ProfileViewModel
import br.com.bragasaude.ui.theme.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private val RELATION_OPTIONS = listOf("Filho", "Filha", "Neto", "Neta", "Esposo", "Esposa", "Cuidador", "Cuidadora", "Outro familiar")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CaregiverRegistrationScreen(
    onBack: () -> Unit,
    onRegistrationComplete: () -> Unit,
    authViewModel: AuthViewModel = hiltViewModel(),
    caregiverViewModel: CaregiverDashboardViewModel = hiltViewModel(),
    profileViewModel: ProfileViewModel = hiltViewModel()
) {
    BackHandler { onBack() }
    val existingProfile by profileViewModel.profile.collectAsState()
    var caregiverName by rememberSaveable { mutableStateOf("") }
    var selectedRelation by rememberSaveable { mutableStateOf("") }
    var connectionCode by rememberSaveable { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }
    var errorMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var relationExpanded by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    LaunchedEffect(existingProfile?.id) { if (caregiverName.isBlank()) caregiverName = existingProfile?.fullName.orEmpty() }
    val scanner = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val code = FamilyConnectionCode.parse(result.data?.getStringExtra("SCAN_RESULT"))
            if (code != null) { connectionCode = code; errorMessage = null }
            else errorMessage = "QR inválido. Leia o código de conexão mostrado pelo familiar."
        }
    }
    val validCode = FamilyConnectionCode.parse(connectionCode)
    Scaffold(
        modifier = Modifier.imePadding(),
        containerColor = BragaBackground,
        topBar = { EmeraldHeaderBanner(title = "Conectar familiar", subtitle = "Cuidar de quem importa", onBack = onBack) },
        bottomBar = {
            Surface(color = BragaCardSurface, tonalElevation = 2.dp) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
                    Button(
                        onClick = {
                            isSubmitting = true; errorMessage = null
                            scope.launch {
                                try {
                                    val result = caregiverViewModel.connectWithCode(validCode ?: return@launch, caregiverName.trim(), selectedRelation)
                                    if (result.isSuccess) {
                                        profileViewModel.saveCaregiverProfile(caregiverName.trim(), authViewModel.caregiverMode.value ?: "VIEWER_ONLY") { saved ->
                                            isSubmitting = false
                                            if (saved) { authViewModel.setProfileComplete(); onRegistrationComplete() }
                                            else errorMessage = "O vínculo foi aceito, mas o perfil não foi salvo. Tente concluir novamente."
                                        }
                                    } else { isSubmitting = false; errorMessage = result.exceptionOrNull()?.message ?: "Não foi possível conectar. Confira o código e tente novamente." }
                                } catch (e: CancellationException) { throw e } catch (_: Exception) {
                                    isSubmitting = false; errorMessage = "A conexão não foi concluída. Tente novamente."
                                }
                            }
                        },
                        enabled = !isSubmitting && validCode != null && caregiverName.isNotBlank() && selectedRelation.isNotBlank(),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        if (isSubmitting) { CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary); Spacer(Modifier.width(8.dp)) }
                        Text(if (isSubmitting) "Conectando…" else "Confirmar conexão")
                    }
                }
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("O familiar escolhe compartilhar os dados com você. Sua própria saúde continua na mesma conta.", style = MaterialTheme.typography.bodyLarge, color = BragaTextSecondary)
            Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = BragaCardSurface), border = BorderStroke(1.dp, BragaCardBorder)) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("1. Código do familiar", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("Peça para ele abrir Círculo Familiar no Braga Saúde.", color = BragaTextSecondary)
                    OutlinedButton(enabled = !isSubmitting, onClick = {
                        try { scanner.launch(Intent(context, FamilyQrScannerActivity::class.java)) }
                        catch (_: Exception) { errorMessage = "Não foi possível abrir a câmera. Digite o código abaixo." }
                    }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                        Icon(Icons.Default.QrCodeScanner, null); Spacer(Modifier.width(8.dp)); Text("Ler QR Code")
                    }
                    OutlinedTextField(value = connectionCode, onValueChange = { connectionCode = it.uppercase().take(32); errorMessage = null },
                        label = { Text("Ou digite o código") }, placeholder = { Text("A1B2C3D4") }, enabled = !isSubmitting,
                        supportingText = { Text(if (validCode != null) "Código preenchido. Confirme seus dados abaixo." else "8 letras e números, sem espaços.") },
                        isError = connectionCode.length >= 8 && validCode == null, singleLine = true,
                        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters))
                }
            }
            Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = BragaCardSurface), border = BorderStroke(1.dp, BragaCardBorder)) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("2. Como o familiar verá você", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    OutlinedTextField(value = caregiverName, onValueChange = { caregiverName = it }, label = { Text("Seu nome") }, enabled = !isSubmitting,
                        singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words))
                    ExposedDropdownMenuBox(expanded = relationExpanded, onExpandedChange = { if (!isSubmitting) relationExpanded = it }) {
                        OutlinedTextField(value = selectedRelation, onValueChange = {}, readOnly = true, enabled = !isSubmitting, label = { Text("Sua relação com o familiar") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = relationExpanded) }, modifier = Modifier.menuAnchor().fillMaxWidth(), shape = RoundedCornerShape(12.dp))
                        ExposedDropdownMenu(expanded = relationExpanded, onDismissRequest = { relationExpanded = false }) {
                            RELATION_OPTIONS.forEach { relation -> DropdownMenuItem(text = { Text(relation) }, onClick = { selectedRelation = relation; relationExpanded = false }) }
                        }
                    }
                }
            }
            Text("A leitura do QR apenas preenche o código. O acesso só é solicitado ao tocar em Confirmar conexão.", style = MaterialTheme.typography.bodySmall, color = BragaTextSecondary)
        }
    }
}

