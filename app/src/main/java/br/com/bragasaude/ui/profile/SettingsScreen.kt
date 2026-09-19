package br.com.bragasaude.ui.profile

import br.com.bragasaude.data.util.HealthFormatter
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Feedback
import androidx.compose.material.icons.filled.HeadsetMic
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Whatsapp
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import br.com.bragasaude.ui.components.EmeraldHeaderBanner
import br.com.bragasaude.ui.theme.BragaMintBorder
import br.com.bragasaude.ui.theme.BragaBackground
import br.com.bragasaude.ui.theme.BragaEmerald
import br.com.bragasaude.ui.theme.BragaMint
import br.com.bragasaude.ui.theme.BragaTextPrimary
import br.com.bragasaude.ui.theme.BragaTextSecondary

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onLogout: () -> Unit,
    onNavigateToFeedback: () -> Unit = {},
    onNavigateToTerms: () -> Unit = {},
    onNavigateToPrivacy: () -> Unit = {},
    onNavigateToTutorial: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val profile by viewModel.profile.collectAsState()
    val isSaving by viewModel.isSaving.collectAsState()
    val voiceAssistantEnabled by viewModel.voiceAssistantEnabled.collectAsState()
    val voiceConfirmationEnabled by viewModel.voiceConfirmationEnabled.collectAsState()
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current

    // Quando o ViewModel monta o link do WhatsApp (TOTP atual), abre e consome.
    val waLink by viewModel.openWhatsAppLinkEvent.collectAsState()
    LaunchedEffect(waLink) {
        waLink?.let { url ->
            runCatching {
                context.startActivity(
                    android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
                        .apply { flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK }
                )
            }
            viewModel.consumeOpenWhatsAppLink()
        }
    }

    var showEmergencyDialog by remember { mutableStateOf(false) }
    var showStepGoalDialog by remember { mutableStateOf(false) }
    var showDeleteAccountDialog by remember { mutableStateOf(false) }
    var deleteConfirmationText by remember { mutableStateOf("") }

    val userName = profile?.fullName?.takeIf { it.isNotBlank() } ?: "Meu perfil"
    val initials = userName.split(" ")
        .filter { it.isNotBlank() }
        .take(2)
        .mapNotNull { it.firstOrNull()?.toString() }
        .joinToString("")
        .ifEmpty { "DM" }

    Scaffold(
        containerColor = BragaBackground
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = innerPadding.calculateBottomPadding()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                EmeraldHeaderBanner(
                    title = "Configurações",
                    subtitle = "Preferências e Conta • $userName",
                    avatarInitials = initials,
                    onBack = onBack
                )
            }

            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Metas de Saúde
                    Text(
                        "Metas de Saúde Personalizadas",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = BragaTextPrimary,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = BorderStroke(1.dp, BragaMintBorder.copy(alpha = 0.7f)),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        val currentGoal = profile?.stepGoal ?: 8000
                        SettingsItem(
                            title = "Meta Diária de Passos",
                            subtitle = "$currentGoal passos por dia (Toque para ajustar)",
                            icon = Icons.AutoMirrored.Filled.DirectionsWalk,
                            onClick = { showStepGoalDialog = true }
                        )
                    }

                    // WhatsApp e Emergência
                    Text(
                        "WhatsApp e Emergência",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = BragaTextPrimary,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = BorderStroke(1.dp, BragaMintBorder.copy(alpha = 0.7f)),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Column {
                            val linkedPhone = profile?.whatsappPhone
                            val hasWhatsapp = !linkedPhone.isNullOrBlank()
                            SettingsItem(
                                title = if (hasWhatsapp) "Meu WhatsApp" else "Vincular meu WhatsApp",
                                subtitle = if (hasWhatsapp) "$linkedPhone • Toque para vincular de novo" else "Abre o WhatsApp com o código pronto. É só enviar.",
                                icon = Icons.Default.Whatsapp,
                                onClick = { viewModel.openWhatsAppLink() }
                            )
                            HorizontalDivider(color = BragaMintBorder.copy(alpha = 0.3f), thickness = 0.8.dp)
                            SettingsItem(
                                title = "Parente Próximo de Confiança",
                                subtitle = profile?.emergencyContactName ?: "Não cadastrado",
                                icon = Icons.Default.Person,
                                onClick = { showEmergencyDialog = true }
                            )
                        }
                    }

                    // Preferências do Aplicativo
                    Text(
                        "Preferências do Aplicativo",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = BragaTextPrimary,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = BorderStroke(1.dp, BragaMintBorder.copy(alpha = 0.7f)),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Column {
                            SettingsSwitchItem(
                                title = "Notificações de Saúde",
                                subtitle = "Alertas sobre glicose, pressão e remédios",
                                icon = Icons.Default.Notifications,
                                checked = profile?.notificationsEnabled ?: true,
                                onCheckedChange = { viewModel.updateNotifications(it) }
                            )
                            HorizontalDivider(color = BragaMintBorder.copy(alpha = 0.3f), thickness = 0.8.dp)
                            SettingsSwitchItem(
                                title = "Localização e Passos",
                                subtitle = "Rastreamento em segundo plano para atividade física",
                                icon = Icons.Default.LocationOn,
                                checked = profile?.locationEnabled ?: true,
                                onCheckedChange = { viewModel.updateLocation(it) }
                            )
                        }
                    }

                    // Acessibilidade e Voz
                    Text(
                        "Acessibilidade e Voz",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = BragaTextPrimary,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = BorderStroke(1.dp, BragaMintBorder.copy(alpha = 0.7f)),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Column {
                            SettingsSwitchItem(
                                title = "Assistente por voz",
                                subtitle = "Registrar sinais vitais e rotina falando com o Braga",
                                icon = Icons.Default.Mic,
                                checked = voiceAssistantEnabled,
                                onCheckedChange = { viewModel.updateVoiceAssistant(it) }
                            )
                            HorizontalDivider(color = BragaMintBorder.copy(alpha = 0.3f), thickness = 0.8.dp)
                            SettingsSwitchItem(
                                title = "Confirmação por áudio",
                                subtitle = "Voz acolhedora do assistente vocalizando resultados",
                                icon = Icons.Default.RecordVoiceOver,
                                checked = voiceConfirmationEnabled,
                                onCheckedChange = { viewModel.updateVoiceConfirmation(it) }
                            )
                        }
                    }

                    // Ajuda e Tutorial
                    Text(
                        "Ajuda e Tutorial",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = BragaTextPrimary,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = BorderStroke(1.dp, BragaMintBorder.copy(alpha = 0.7f)),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Column {
                            SettingsItem(
                                title = "Ver Tutorial do Aplicativo",
                                subtitle = "Reveja o tour guiado e as principais áreas do app.",
                                icon = Icons.Default.Info,
                                onClick = onNavigateToTutorial
                            )
                            HorizontalDivider(color = BragaMintBorder.copy(alpha = 0.3f), thickness = 0.8.dp)
                            SettingsItem(
                                title = "Falar com os Desenvolvedores",
                                subtitle = "Envie sugestões de melhorias ou observações por texto ou voz.",
                                icon = Icons.Default.HeadsetMic,
                                onClick = onNavigateToFeedback
                            )
                        }
                    }

                    // Documentos Legais e Conta
                    Text(
                        "Documentos Legais e Conta",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = BragaTextPrimary,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = BorderStroke(1.dp, BragaMintBorder.copy(alpha = 0.7f)),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Column {
                            SettingsItem(
                                title = "Termos de Uso",
                                subtitle = "Consulte as condições e limites do aplicativo.",
                                icon = Icons.Default.Info,
                                onClick = onNavigateToTerms
                            )
                            HorizontalDivider(color = BragaMintBorder.copy(alpha = 0.3f), thickness = 0.8.dp)
                            SettingsItem(
                                title = "Política de Privacidade (LGPD)",
                                subtitle = "Consulte o tratamento e proteção de dados sensíveis.",
                                icon = Icons.Default.Info,
                                onClick = onNavigateToPrivacy
                            )
                            HorizontalDivider(color = BragaMintBorder.copy(alpha = 0.3f), thickness = 0.8.dp)
                            SettingsItem(
                                title = "Sair da Conta",
                                subtitle = "Encerrar sessão no dispositivo atual.",
                                icon = Icons.AutoMirrored.Filled.ExitToApp,
                                onClick = { viewModel.logout(onLogout) }
                            )
                            HorizontalDivider(color = BragaMintBorder.copy(alpha = 0.3f), thickness = 0.8.dp)
                            SettingsItem(
                                title = "Excluir Perfil e Conta",
                                subtitle = "Apagar permanentemente todos os dados e histórico.",
                                icon = Icons.Default.DeleteForever,
                                titleColor = MaterialTheme.colorScheme.error,
                                onClick = {
                                    deleteConfirmationText = ""
                                    showDeleteAccountDialog = true
                                }
                            )
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(16.dp), tint = BragaTextSecondary)
                        Spacer(Modifier.width(8.dp))
                        Text("Braga Saúde • Versão 1.2.0 (Build 42)", style = MaterialTheme.typography.labelSmall, color = BragaTextSecondary)
                    }
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }

    if (showDeleteAccountDialog) {
        val isConfirmed = deleteConfirmationText.trim().equals("deletar meu perfil", ignoreCase = true)
        AlertDialog(
            onDismissRequest = { showDeleteAccountDialog = false },
            icon = {
                Icon(
                    Icons.Default.DeleteForever,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(36.dp)
                )
            },
            title = {
                Text(
                    "Excluir Perfil e Conta?",
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "Esta ação apagará permanentemente todos os seus dados de saúde, exames, registros e metas deste dispositivo e da nuvem.\n\nSua conta será cancelada, mas você poderá criar uma nova conta com o mesmo e-mail Google a qualquer momento caso deseje retornar.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "Para confirmar, digite \"deletar meu perfil\" abaixo:",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error
                    )
                    OutlinedTextField(
                        value = deleteConfirmationText,
                        onValueChange = { deleteConfirmationText = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("deletar meu perfil") },
                        singleLine = true,
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteAccountDialog = false
                        viewModel.deleteAccount(onLogout)
                    },
                    enabled = isConfirmed,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        disabledContainerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.3f)
                    )
                ) {
                    Text("Excluir Definitivamente", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAccountDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    if (showStepGoalDialog) {
        StepGoalDialog(
            currentGoal = profile?.stepGoal ?: 8000,
            onDismiss = { showStepGoalDialog = false },
            onSave = { newGoal ->
                viewModel.updateStepGoal(newGoal)
                showStepGoalDialog = false
            }
        )
    }

    if (showEmergencyDialog) {
        EmergencyContactDialog(
            initialName = profile?.emergencyContactName ?: "",
            initialRelation = profile?.emergencyContactRelation ?: "",
            initialPhone = profile?.emergencyContactPhone ?: "",
            onDismiss = { showEmergencyDialog = false },
            onSave = { name, relation, phone ->
                viewModel.updateEmergencyContact(name, relation, phone)
                showEmergencyDialog = false
            }
        )
    }
}

@Composable
fun SettingsItem(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    titleColor: Color = BragaTextPrimary,
    onClick: () -> Unit
) {
    val isDestructive = titleColor == MaterialTheme.colorScheme.error
    val iconBg = if (isDestructive) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f) else BragaMint
    val iconTint = if (isDestructive) MaterialTheme.colorScheme.error else BragaEmerald

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(iconBg),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = titleColor)
            Spacer(Modifier.height(2.dp))
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = BragaTextSecondary)
        }
    }
}

@Composable
fun SettingsSwitchItem(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(BragaMint),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = BragaEmerald, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = BragaTextPrimary)
            Spacer(Modifier.height(2.dp))
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = BragaTextSecondary)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = BragaEmerald,
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = BragaMintBorder
            )
        )
    }
}

@Composable
fun EmergencyContactDialog(
    initialName: String,
    initialRelation: String,
    initialPhone: String,
    onDismiss: () -> Unit,
    onSave: (String, String, String) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var relation by remember { mutableStateOf(initialRelation) }
    var phone by remember { mutableStateOf(initialPhone) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Contato de Emergência") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Nome Completo") })
                OutlinedTextField(value = relation, onValueChange = { relation = it }, label = { Text("Parentesco/Relação") })
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = HealthFormatter.formatPhoneInput(it) },
                    label = { Text("Telefone/WhatsApp") },
                    placeholder = { Text("(11) 99999-8888") }
                )
            }
        },
        confirmButton = {
            Button(onClick = { onSave(name, relation, phone) }) {
                Text("Salvar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}

@Composable
fun StepGoalDialog(
    currentGoal: Int,
    onDismiss: () -> Unit,
    onSave: (Int) -> Unit
) {
    var goalText by remember { mutableStateOf(currentGoal.toString()) }
    val currentInt = goalText.toIntOrNull() ?: currentGoal

    val presets = listOf(3000, 5000, 8000, 10000, 12000)

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                Icons.AutoMirrored.Filled.DirectionsWalk,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
        },
        title = {
            Text("Meta Diária de Passos", fontWeight = FontWeight.Bold)
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "Escolha a meta que melhor se adapta à sua rotina e condição física:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Atalhos rápidos
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    presets.forEach { preset ->
                        FilterChip(
                            selected = currentInt == preset,
                            onClick = { goalText = preset.toString() },
                            label = {
                                Text(
                                    if (preset >= 1000) "${preset / 1000}k" else "$preset",
                                    fontSize = 12.sp,
                                    fontWeight = if (currentInt == preset) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        )
                    }
                }

                // Ajuste Fino (+ / - 500) e Campo de Digitação
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    FilledTonalIconButton(
                        onClick = {
                            val next = (currentInt - 500).coerceAtLeast(500)
                            goalText = next.toString()
                        }
                    ) {
                        Text("-500", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }

                    OutlinedTextField(
                        value = goalText,
                        onValueChange = { input ->
                            if (input.all { it.isDigit() } && input.length <= 6) {
                                goalText = input
                            }
                        },
                        label = { Text("Passos/dia") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        textStyle = androidx.compose.ui.text.TextStyle(
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                    )

                    FilledTonalIconButton(
                        onClick = {
                            val next = (currentInt + 500).coerceAtMost(50000)
                            goalText = next.toString()
                        }
                    ) {
                        Text("+500", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val finalGoal = goalText.toIntOrNull() ?: currentGoal
                    onSave(finalGoal)
                }
            ) {
                Text("Salvar Meta")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}

