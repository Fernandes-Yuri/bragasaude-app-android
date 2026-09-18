package br.com.bragasaude.ui.family

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import br.com.bragasaude.data.local.FamilyBindingEntity
import br.com.bragasaude.ui.components.BragaBadgeType
import br.com.bragasaude.ui.components.BragaStatusBadge
import br.com.bragasaude.ui.components.EmeraldHeaderBanner
import br.com.bragasaude.ui.theme.BragaBackground
import br.com.bragasaude.ui.theme.BragaCardSurface
import br.com.bragasaude.ui.theme.BragaEmerald
import br.com.bragasaude.ui.theme.BragaMint
import br.com.bragasaude.ui.theme.BragaMintBorder
import br.com.bragasaude.ui.theme.BragaTextPrimary
import br.com.bragasaude.ui.theme.BragaTextSecondary
import br.com.bragasaude.util.QrCodeGenerator
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Tela de Conexão Familiar — 100% dedicada ao TITULAR / PACIENTE (idoso / adulto 40+/50+).
 *
 * Funcionalidades:
 * - Gera código de 8 caracteres alfanuméricos para compartilhar com familiares/cuidadores via WhatsApp
 * - Lista cuidadores vinculados com opção de revogar acesso
 * - Mostra mensagens recebidas da família
 * - ZERO menção a "Sou Cuidador" (limpeza completa de código legado)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FamilyConnectScreen(
    onClose: () -> Unit,
    onOpenDashboard: (() -> Unit)? = null,
    viewModel: PatientFamilyViewModel = hiltViewModel()
) {
    val connectionCode by viewModel.connectionCode.collectAsState()
    val activeCaregivers by viewModel.activeCaregivers.collectAsState()
    val unreadCount by viewModel.unreadMessageCount.collectAsState()
    val inviteHistory by viewModel.inviteHistory.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    var showShareDialog by remember { mutableStateOf(false) }
    var showRevokeConfirm by remember { mutableStateOf<Pair<String, String>?>(null) }
    var showRegenerateConfirm by remember { mutableStateOf(false) }
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        // Carregar código existente, se houver
        if (connectionCode == null) {
            viewModel.loadExistingCode()
        }
        viewModel.uiEvents.collect { event ->
            when (event) {
                is FamilyUiEvent.Error -> snackbarHostState.showSnackbar(event.text)
                is FamilyUiEvent.Notice -> snackbarHostState.showSnackbar(event.text)
            }
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = BragaBackground,
        topBar = {
            EmeraldHeaderBanner(
                title = "Círculo Familiar",
                subtitle = "Acompanhamento e Cuidados",
                onBack = onClose,
                trailingContent = {
                    if (unreadCount > 0) {
                        Box {
                            IconButton(onClick = { viewModel.markAllMessagesAsRead() }) {
                                Icon(Icons.Default.Forum, contentDescription = "Ver recados", tint = Color.White)
                            }
                            Badge(
                                modifier = Modifier
                                    .offset(x = (-4).dp, y = (-4).dp)
                                    .size(18.dp)
                            ) {
                                Text(
                                    text = if (unreadCount > 9) "9+" else unreadCount.toString(),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            Spacer(Modifier.height(16.dp))

            // ============================================
            // SEÇÃO 1: GERAR E COMPARTILHAR O CÓDIGO
            // ============================================
            SectionTitle("Seu código de conexão familiar")

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = BragaCardSurface
                ),
                border = BorderStroke(1.dp, BragaMintBorder),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = BragaMint,
                            modifier = Modifier.size(44.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.Group,
                                    contentDescription = null,
                                    tint = BragaEmerald,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Text(
                            "Compartilhe este código com seu filho ou familiar de confiança para que ele acompanhe sua saúde.",
                            style = MaterialTheme.typography.bodyMedium,
                            lineHeight = 20.sp,
                            color = BragaTextPrimary,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(Modifier.height(16.dp))

                    // Código exibido grande
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = BragaMint,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = connectionCode ?: "Gerando...",
                            fontSize = 32.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Black,
                            color = BragaEmerald,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(20.dp)
                        )
                    }

                    Spacer(Modifier.height(16.dp))

                    // QR Code para compartilhamento rápido
                    connectionCode?.let { code ->
                        val qrBitmap = remember(code) {
                            QrCodeGenerator.generateQrCode(code, size = 400)
                        }
                        
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = Color.White,
                            border = BorderStroke(1.dp, BragaMintBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    "Ou escaneie o QR Code",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                
                                Spacer(Modifier.height(12.dp))
                                
                                Image(
                                    bitmap = qrBitmap.asImageBitmap(),
                                    contentDescription = "QR Code de conexão: $code",
                                    modifier = Modifier.size(200.dp)
                                )
                                
                                Spacer(Modifier.height(8.dp))
                                
                                Text(
                                    QrCodeGenerator.formatExpiration(
                                        System.currentTimeMillis() + (7 * 24 * 60 * 60 * 1000)
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = {
                                connectionCode?.let {
                                    clipboardManager.setText(AnnotatedString(it))
                                }
                            },
                            modifier = Modifier.weight(1f).heightIn(min = 56.dp),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Copiar")
                        }

                        OutlinedButton(
                            onClick = { showShareDialog = true },
                            modifier = Modifier.weight(1f).heightIn(min = 56.dp),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("WhatsApp")
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    TextButton(
                        onClick = { showRegenerateConfirm = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Gerar Novo Código")
                    }
                }
            }

            // Diálogo de confirmação para gerar novo código (invalida o código atual)
            if (showRegenerateConfirm) {
                AlertDialog(
                    onDismissRequest = { showRegenerateConfirm = false },
                    title = { Text("Gerar novo código?", fontWeight = FontWeight.Bold) },
                    text = { Text("O código atual será invalidado e não poderá mais ser usado por familiares.") },
                    confirmButton = {
                        Button(onClick = {
                            showRegenerateConfirm = false
                            viewModel.regenerateInvite()
                        }) { Text("Gerar novo código") }
                    },
                    dismissButton = {
                        TextButton(onClick = { showRegenerateConfirm = false }) { Text("Cancelar") }
                    }
                )
            }

            // Diálogo de compartilhamento por WhatsApp
            if (showShareDialog && connectionCode != null) {
                ShareViaWhatsAppDialog(
                    code = connectionCode!!,
                    onDismiss = { showShareDialog = false }
                )
            }

            Spacer(Modifier.height(16.dp))

            // ============================================
            // SEÇÃO 2: CUIDADORES VINCULADOS
            // ============================================
            SectionTitle("Familiares que podem te acompanhar (${activeCaregivers.size})")

            if (activeCaregivers.isEmpty()) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.PersonAdd,
                            contentDescription = null,
                            modifier = Modifier.size(40.dp),
                            tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "Nenhum familiar vinculado ainda.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Compartilhe seu código acima para convidar alguém.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    activeCaregivers.forEach { binding ->
                        CaregiverListItem(
                            binding = binding,
                            onRevoke = {
                                showRevokeConfirm = Pair(binding.id, binding.caregiverName)
                            }
                        )
                    }
                }
            }

            // ============================================
            // SEÇÃO 3: HISTÓRICO DE CÓDIGOS GERADOS
            // ============================================
            SectionTitle("Histórico de códigos gerados")

            if (inviteHistory.isEmpty()) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.History,
                            contentDescription = null,
                            modifier = Modifier.size(40.dp),
                            tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "Nenhum código gerado ainda.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                val validCodes = inviteHistory.filter { it.status != "REVOKED" }
                if (validCodes.isEmpty()) {
                    Text(
                        "Nenhum código gerado ainda.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        validCodes.forEach { binding ->
                            InviteHistoryItem(binding = binding)
                        }
                    }
                }
            }

            // Confirmação de revogação
            showRevokeConfirm?.let { (bindingId, name) ->
                AlertDialog(
                    onDismissRequest = { showRevokeConfirm = null },
                    shape = RoundedCornerShape(16.dp),
                    icon = {
                        Icon(Icons.Default.Close, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    },
                    title = { Text("Remover $name?") },
                    text = {
                        Text(
                            "$name deixará de visualizar seus sinais vitais. Você poderá gerar um novo código sempre que quiser."
                        )
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                viewModel.revokeCaregiver(bindingId)
                                showRevokeConfirm = null
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Remover acesso")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showRevokeConfirm = null }) { Text("Cancelar") }
                    }
                )
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

// ========================== SUBCOMPONENTES ==========================

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = BragaTextPrimary,
        modifier = Modifier.padding(bottom = 4.dp, top = 8.dp)
    )
}

@Composable
private fun CaregiverListItem(
    binding: FamilyBindingEntity,
    onRevoke: () -> Unit
) {
    val displayName = binding.caregiverName.ifBlank { "Familiar" }
    val initials = displayName.split(" ")
        .filter { it.isNotBlank() }
        .take(2)
        .mapNotNull { it.firstOrNull()?.toString()?.uppercase() }
        .joinToString("")
        .ifEmpty { "F" }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = BragaCardSurface),
        border = BorderStroke(1.dp, BragaMintBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = BragaMint,
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = initials,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = BragaEmerald
                    )
                }
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    displayName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = BragaTextPrimary
                )
                Text(
                    binding.caregiverRelation.ifBlank { "Familiar" },
                    style = MaterialTheme.typography.bodySmall,
                    color = BragaTextSecondary
                )
            }

            Spacer(Modifier.width(8.dp))
            BragaStatusBadge(text = "Ativo", type = BragaBadgeType.CONFIRMED)

            OutlinedIconButton(
                onClick = onRevoke,
                modifier = Modifier.size(40.dp),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Default.LinkOff, contentDescription = "Revogar acesso", modifier = Modifier.size(18.dp), tint = BragaTextSecondary)
            }
        }
    }
}

@Composable
private fun ShareViaWhatsAppDialog(code: String, onDismiss: () -> Unit) {
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(16.dp),
        title = { Text("Compartilhar via WhatsApp") },
        text = {
            Column {
                Text("Envie esta mensagem para seu filho ou familiar:")
                Spacer(Modifier.height(12.dp))
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = """Olá! Aqui é do Braga Saúde. Meu código de conexão familiar é: $code

Use este código no app Braga Saúde para se conectar comigo como meu cuidador.""",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val message = "Olá! Aqui é do Braga Saúde. Meu código de conexão familiar é: $code\n\nUse este código no app Braga Saúde para se conectar comigo como meu cuidador."
                    try {
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, message)
                            setPackage("com.whatsapp")
                        }
                        context.startActivity(intent)
                        onDismiss()
                    } catch (e: ActivityNotFoundException) {
                        val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                        clipboard?.setPrimaryClip(android.content.ClipData.newPlainText("BragaSaude", message))
                        onDismiss()
                    }
                },
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Abrir WhatsApp")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

// ========================== ITEM DE HISTÓRICO DE CONVITE ==========================

@Composable
private fun InviteHistoryItem(
    binding: FamilyBindingEntity
) {
    val statusColor = when (binding.status) {
        "ACTIVE" -> MaterialTheme.colorScheme.primary
        "PENDING" -> Color(0xFFFFA726) // Laranja
        "EXPIRED" -> Color(0xFFEF5350) // Vermelho suave
        "REVOKED" -> Color.Gray
        else -> Color.Gray
    }
    
    val statusText = when (binding.status) {
        "ACTIVE" -> "Ativo ✓"
        "PENDING" -> "Aguardando conexão"
        "EXPIRED" -> "Expirado"
        "REVOKED" -> "Revogado"
        else -> binding.status
    }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)),
        border = BorderStroke(1.dp, statusColor.copy(alpha = 0.4f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = statusColor.copy(alpha = 0.15f),
                modifier = Modifier.size(36.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = when (binding.status) {
                            "ACTIVE" -> "✓"
                            "PENDING" -> "⏳"
                            "EXPIRED" -> "✖"
                            "REVOKED" -> "🚫"
                            else -> "?"
                        },
                        fontSize = 18.sp
                    )
                }
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    binding.connectionCode,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                Text(
                    statusText,
                    style = MaterialTheme.typography.bodySmall,
                    color = statusColor
                )
            }

            Text(
                SimpleDateFormat("dd/MM", Locale.getDefault()).format(Date(binding.createdAt)),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
