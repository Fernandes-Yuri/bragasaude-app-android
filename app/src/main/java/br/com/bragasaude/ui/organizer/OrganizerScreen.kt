package br.com.bragasaude.ui.organizer

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import br.com.bragasaude.data.local.organizer.*
import br.com.bragasaude.ui.components.BragaAlertDialog
import br.com.bragasaude.ui.components.BragaBadgeType
import br.com.bragasaude.ui.components.BragaBottomSheet
import br.com.bragasaude.ui.components.BragaStatusBadge
import br.com.bragasaude.ui.components.EmeraldHeaderBanner
import br.com.bragasaude.ui.theme.*
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrganizerScreen(onBack: () -> Unit, viewModel: OrganizerViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current
    var reviewing by remember { mutableStateOf<OrganizerDocument?>(null) }
    var appendTo by rememberSaveable { mutableStateOf<String?>(null) }
    var ending by remember { mutableStateOf(false) }
    var originalToSave by rememberSaveable { mutableStateOf<String?>(null) }
    var resultReview by remember { mutableStateOf(false) }
    var sharing by remember { mutableStateOf(false) }
    var showInfoSheet by remember { mutableStateOf(false) }
    var captureUriString by rememberSaveable { mutableStateOf<String?>(null) }
    var cameraError by remember { mutableStateOf<String?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) viewModel.import(uris, appendTo)
        appendTo = null
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        captureUriString?.let { value ->
            val uri = Uri.parse(value)
            if (ok) viewModel.import(listOf(uri), appendTo)
            else context.contentResolver.delete(uri, null, null)
        }
        captureUriString = null
        appendTo = null
    }
    fun capturePage() {
        val session = state.session ?: return
        val file = File(context.cacheDir, "organizer-capture/${session.id}/capture.jpg").apply { parentFile?.mkdirs() }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        captureUriString = uri.toString()
        try { camera.launch(uri) }
        catch (_: Exception) { file.delete(); captureUriString = null; cameraError = "Não foi possível abrir a câmera. Selecione uma foto ou um PDF existente." }
    }
    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) capturePage() else { cameraError = "Permita o acesso à câmera para fotografar ou selecione arquivos já existentes."; appendTo = null }
    }
    fun requestCamera(documentId: String?) {
        appendTo = documentId; cameraError = null
        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.CAMERA) == android.content.pm.PackageManager.PERMISSION_GRANTED) capturePage()
        else cameraPermission.launch(android.Manifest.permission.CAMERA)
    }
    val saveOriginal = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        originalToSave?.let { if (uri != null) viewModel.saveOriginal(it, uri) }
        originalToSave = null
    }
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        if (uri != null) viewModel.save(uri)
    }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) viewModel.refresh() }
        lifecycle.lifecycle.addObserver(observer)
        onDispose { lifecycle.lifecycle.removeObserver(observer) }
    }
    // Proteção de segurança: impede capturas de tela e gravação de prévias clínicas
    val activity = context as? android.app.Activity
    DisposableEffect(activity) {
        val alreadySecure = activity?.window?.attributes?.flags?.and(WindowManager.LayoutParams.FLAG_SECURE) != 0
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { if (!alreadySecure) activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }
    BackHandler { onBack() }

    Scaffold(
        containerColor = BragaBackground,
        topBar = {
            EmeraldHeaderBanner(
                title = "Organizar exames",
                subtitle = "Reúna e prepare seus exames para a consulta",
                onBack = onBack,
                trailingContent = {
                    IconButton(onClick = { showInfoSheet = true }) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Informações sobre o organizador",
                            tint = Color.White
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            cameraError?.let {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        Text(it, color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            state.notice?.let {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = BragaMintSurface,
                    border = BorderStroke(1.dp, BragaMintBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = BragaEmerald)
                        Text(it, color = BragaTextPrimary, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            state.error?.let {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        Text(it, color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            if (state.busy) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = BragaEmerald
                )
            }

            val session = state.session
            if (session == null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = BragaCardSurface),
                    border = BorderStroke(1.dp, BragaCardBorder)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .background(BragaMint),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Assignment,
                                contentDescription = null,
                                tint = BragaEmerald,
                                modifier = Modifier.size(36.dp)
                            )
                        }

                        Text(
                            text = "Prepare seus exames para a consulta",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = BragaTextPrimary,
                            textAlign = TextAlign.Center
                        )

                        Text(
                            text = "Reúna PDFs e fotos de exames em um documento único, organizado com índice cronológico e divisórias para levar ao médico.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = BragaTextSecondary,
                            textAlign = TextAlign.Center,
                            lineHeight = 22.sp
                        )

                        Surface(
                            onClick = { showInfoSheet = true },
                            shape = RoundedCornerShape(12.dp),
                            color = BragaMintSurface,
                            border = BorderStroke(1.dp, BragaMintBorder)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = null,
                                    tint = BragaEmerald,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "Processamento 100% no celular · Expira em 24h",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = BragaEmeraldDark,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        Button(
                            onClick = viewModel::start,
                            enabled = !state.busy,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = BragaEmerald)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Começar organização", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                    }
                }
            } else {
                // Barra de Ações Rápidas
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = BragaCardSurface),
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
                            Text(
                                text = "Adicionar Documentos",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = BragaTextPrimary
                            )
                            IconButton(onClick = { showInfoSheet = true }, modifier = Modifier.size(28.dp)) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = "Limites e regras",
                                    tint = BragaTextSecondary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = { appendTo = null; picker.launch(arrayOf("application/pdf", "image/*")) },
                                enabled = !state.busy,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = BragaEmerald)
                            ) {
                                Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("PDF / Fotos", fontWeight = FontWeight.SemiBold)
                            }

                            OutlinedButton(
                                onClick = { requestCamera(null) },
                                enabled = !state.busy,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = BragaEmeraldDark),
                                border = BorderStroke(1.dp, BragaEmerald)
                            ) {
                                Icon(Icons.Default.PhotoCamera, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Fotografar", fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }

                // Lista de Documentos
                if (session.documents.isNotEmpty()) {
                    Text(
                        text = "Documentos adicionados (${session.documents.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = BragaTextPrimary
                    )

                    OrganizerMetadata.ordered(session.documents, state.newest).forEach { doc ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = BragaCardSurface),
                            border = BorderStroke(1.dp, if (doc.confirmed) BragaCardBorder else Warning.copy(alpha = 0.6f))
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        modifier = Modifier.weight(1f, fill = false)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(38.dp)
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(if (doc.confirmed) BragaMint else Color(0xFFFEF3C7)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Description,
                                                contentDescription = null,
                                                tint = if (doc.confirmed) BragaEmerald else Warning,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                        Column {
                                            Text(
                                                text = doc.title,
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = BragaTextPrimary,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = "${doc.date.ifBlank { "Sem data" }} · ${doc.type} · ${doc.pages} pág.",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = BragaTextSecondary
                                            )
                                        }
                                    }
                                    BragaStatusBadge(
                                        text = if (doc.confirmed) "Conferido" else "Revisão pendente",
                                        type = if (doc.confirmed) BragaBadgeType.CONFIRMED else BragaBadgeType.REVIEW
                                    )
                                }

                                if (doc.possibleDuplicate) {
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = Color(0xFFFEF3C7),
                                        border = BorderStroke(1.dp, Color(0xFFFDE68A))
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 12.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Icon(Icons.Default.Warning, contentDescription = null, tint = Warning, modifier = Modifier.size(16.dp))
                                            Text("Possível documento repetido. Confira antes de manter.", style = MaterialTheme.typography.bodySmall, color = Color(0xFF92400E))
                                        }
                                    }
                                }

                                Button(
                                    onClick = { reviewing = doc },
                                    enabled = !state.busy,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(44.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (doc.confirmed) BragaMint else BragaEmerald,
                                        contentColor = if (doc.confirmed) BragaEmeraldDark else Color.White
                                    )
                                ) {
                                    Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(if (doc.confirmed) "Ver documento" else "Conferir e validar documento", fontWeight = FontWeight.SemiBold)
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        TextButton(
                                            onClick = { appendTo = doc.id; picker.launch(arrayOf("application/pdf", "image/*")) },
                                            enabled = !state.busy
                                        ) {
                                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text("+ Páginas", style = MaterialTheme.typography.bodySmall)
                                        }
                                        TextButton(
                                            onClick = { requestCamera(doc.id) },
                                            enabled = !state.busy
                                        ) {
                                            Icon(Icons.Default.AddAPhoto, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text("+ Foto", style = MaterialTheme.typography.bodySmall)
                                        }
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        IconButton(
                                            onClick = { originalToSave = doc.id; saveOriginal.launch("documento-de-exame.pdf") },
                                            enabled = !state.busy
                                        ) {
                                            Icon(Icons.Default.FileDownload, contentDescription = "Salvar cópia", tint = BragaTextSecondary)
                                        }
                                        IconButton(
                                            onClick = { viewModel.delete(doc.id) },
                                            enabled = !state.busy
                                        ) {
                                            Icon(Icons.Default.DeleteOutline, contentDescription = "Remover", tint = MaterialTheme.colorScheme.error)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Ação de Gerar PDF
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = BragaCardSurface),
                        border = BorderStroke(1.dp, BragaCardBorder)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = state.newest,
                                    onCheckedChange = { viewModel.order(it) },
                                    enabled = !state.busy
                                )
                                Spacer(Modifier.width(6.dp))
                                Text("Mais recentes primeiro no índice", style = MaterialTheme.typography.bodyMedium, color = BragaTextPrimary)
                            }

                            val allConfirmed = session.documents.all { it.confirmed }
                            Button(
                                onClick = viewModel::generate,
                                enabled = !state.busy && allConfirmed,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = BragaEmerald)
                            ) {
                                Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Gerar PDF com índice", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            }

                            if (!allConfirmed) {
                                Text(
                                    text = "Confira todos os documentos acima para habilitar a geração do PDF.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = BragaTextSecondary,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }

                // Bloco de PDF Pronto
                if (state.ready) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = BragaMintSurface),
                        border = BorderStroke(1.dp, BragaMintBorder)
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = BragaEmerald, modifier = Modifier.size(26.dp))
                                Column {
                                    Text("PDF pronto para a consulta", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = BragaTextPrimary)
                                    Text("Contém índice, divisórias e todas as páginas.", style = MaterialTheme.typography.bodySmall, color = BragaTextSecondary)
                                }
                            }

                            Button(
                                onClick = { save.launch("exames-organizados.pdf") },
                                enabled = !state.busy,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = BragaEmerald)
                            ) {
                                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Salvar fora do aplicativo", fontWeight = FontWeight.Bold)
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { resultReview = true },
                                    enabled = !state.busy,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(46.dp),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Conferir")
                                }

                                OutlinedButton(
                                    onClick = { sharing = true },
                                    enabled = !state.busy,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(46.dp),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Compartilhar")
                                }
                            }
                        }
                    }
                }

                if (state.saved) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = BragaMintSurface,
                        border = BorderStroke(1.dp, BragaMintBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = BragaEmerald)
                            Text("PDF salvo no destino escolhido. Você pode encerrar a organização.", style = MaterialTheme.typography.bodySmall, color = BragaTextPrimary)
                        }
                    }
                }

                TextButton(
                    onClick = { ending = true },
                    enabled = !state.busy,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Icon(Icons.Default.DeleteSweep, contentDescription = null, tint = BragaTextSecondary, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Encerrar e apagar cópias temporárias", color = BragaTextSecondary)
                }
            }
        }
    }

    // Modal de Informações Detalhadas (i)
    if (showInfoSheet) {
        BragaBottomSheet(onDismissRequest = { showInfoSheet = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(BragaMint),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = BragaEmerald,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Sobre o Organizador de Exames",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = BragaTextPrimary
                        )
                        Text(
                            text = "Privacidade e funcionamento da área",
                            style = MaterialTheme.typography.bodySmall,
                            color = BragaTextSecondary
                        )
                    }
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = BragaMintSurface),
                    border = BorderStroke(1.dp, BragaMintBorder)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        OrganizerInfoItem(
                            icon = Icons.Default.Lock,
                            title = "Processamento 100% Local",
                            description = "Seus exames são processados apenas neste celular. Nada é enviado para a nuvem nem para os servidores do Braga Saúde."
                        )
                        OrganizerInfoItem(
                            icon = Icons.Default.Timer,
                            title = "Sessão Temporária de 24 Horas",
                            description = "Esta área não é armazenamento definitivo. As cópias temporárias expiram em 24h. Guarde sempre os documentos físicos originais."
                        )
                        OrganizerInfoItem(
                            icon = Icons.Default.FilePresent,
                            title = "Limites da Sessão",
                            description = "Até 20 documentos ou 100 páginas (máximo de 35 MB por arquivo e 150 MB por sessão). Aceita PDFs e fotos."
                        )
                        OrganizerInfoItem(
                            icon = Icons.Default.FactCheck,
                            title = "Reconhecimento Assistivo",
                            description = "O leitor local apenas sugere título, data e tipo do exame para montar o índice. Não interpretamos laudos nem diagnosticamos condições."
                        )
                    }
                }

                Button(
                    onClick = { showInfoSheet = false },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = BragaEmerald)
                ) {
                    Text("Entendido", fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }
    }

    reviewing?.let { doc ->
        ReviewDocument(doc, state, viewModel, onClose = { reviewing = null; viewModel.clearPreview() })
    }

    if (resultReview) {
        var page by remember { mutableIntStateOf(0) }
        LaunchedEffect(page) { viewModel.previewResult(page) }
        BragaBottomSheet(onDismissRequest = { if (!state.busy) { resultReview = false; viewModel.clearPreview() } }) {
            Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("PDF organizado", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                PagePreview(state.preview.takeIf { state.previewPage == page }, page)
                if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth(), color = BragaEmerald)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = { page-- }, enabled = page > 0 && !state.busy) { Text("Anterior") }
                    Text("${page + 1}/${state.resultPages}", fontWeight = FontWeight.SemiBold)
                    TextButton(onClick = { page++ }, enabled = page + 1 < state.resultPages && !state.busy) { Text("Próxima") }
                }
                Button(
                    onClick = { resultReview = false; viewModel.clearPreview() },
                    enabled = !state.busy,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = BragaEmerald)
                ) {
                    Text("Fechar prévia", fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    if (ending) BragaAlertDialog(
        onDismissRequest = { ending = false },
        title = { Text("Encerrar organização?") },
        text = {
            Text(if (state.saved) "As cópias temporárias serão apagadas. Seus arquivos de origem e o PDF salvo fora do aplicativo permanecem."
            else "Não há confirmação de um PDF salvo. Compartilhar não confirma o salvamento. As fotos feitas aqui também serão apagadas. Salve antes se precisar delas.")
        },
        confirmButton = { TextButton(onClick = { ending = false; viewModel.end() }) { Text("Apagar cópias e encerrar") } },
        dismissButton = { TextButton(onClick = { ending = false }) { Text("Continuar organizando") } }
    )

    if (sharing) BragaAlertDialog(
        onDismissRequest = { sharing = false },
        title = { Text("Compartilhar dados de saúde?") },
        text = { Text("O aplicativo escolhido receberá o PDF e ficará responsável por essa cópia. Confira o destinatário. Compartilhar não confirma que o PDF foi salvo.") },
        confirmButton = {
            TextButton(onClick = {
                sharing = false
                viewModel.share { uri ->
                    val intent = Intent(Intent.ACTION_SEND).setType("application/pdf").putExtra(Intent.EXTRA_STREAM, uri)
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    intent.clipData = ClipData.newRawUri("Exames organizados", uri)
                    context.startActivity(Intent.createChooser(intent, "Compartilhar PDF"))
                }
            }) { Text("Escolher destinatário") }
        },
        dismissButton = { TextButton(onClick = { sharing = false }) { Text("Cancelar") } }
    )
}

@Composable
private fun OrganizerInfoItem(
    icon: ImageVector,
    title: String,
    description: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(BragaMint),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = BragaEmerald,
                modifier = Modifier.size(18.dp)
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = BragaTextPrimary
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = BragaTextSecondary,
                lineHeight = 18.sp
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReviewDocument(doc: OrganizerDocument, state: OrganizerUiState, vm: OrganizerViewModel, onClose: () -> Unit) {
    var title by remember(doc.id) { mutableStateOf(doc.title) }
    var date by remember(doc.id) { mutableStateOf(doc.date) }
    var type by remember(doc.id) { mutableStateOf(doc.type) }
    var page by remember(doc.id) { mutableIntStateOf(0) }
    val seen = remember(doc.id) { mutableStateListOf<Int>() }
    var confirmed by remember(doc.id) { mutableStateOf(false) }

    LaunchedEffect(doc.id, page) { vm.preview(doc.id, page) }
    LaunchedEffect(state.preview, page) { if (state.preview != null && state.previewPage == page && page !in seen) seen.add(page) }

    BragaBottomSheet(onDismissRequest = { if (!state.busy) onClose() }) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Confira o documento original", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = BragaTextPrimary)
            PagePreview(state.preview.takeIf { state.previewPage == page }, page)
            if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth(), color = BragaEmerald)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = { page-- }, enabled = page > 0 && !state.busy) { Text("Anterior") }
                Text("${page + 1}/${doc.pages}", fontWeight = FontWeight.SemiBold)
                TextButton(onClick = { page++ }, enabled = page + 1 < doc.pages && !state.busy) { Text("Próxima") }
            }

            if (doc.photoOnly) {
                TextButton(onClick = { vm.editPhotoPage(doc.id, page); onClose() }, enabled = !state.busy) {
                    Icon(Icons.Default.RotateRight, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Girar esta foto e conferir novamente")
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    TextButton(onClick = { vm.editPhotoPage(doc.id, page, page - 1); onClose() }, enabled = page > 0 && !state.busy) {
                        Text("Mover página para antes")
                    }
                    TextButton(onClick = { vm.editPhotoPage(doc.id, page, page + 1); onClose() }, enabled = page + 1 < doc.pages && !state.busy) {
                        Text("Mover página para depois")
                    }
                }
            }

            OutlinedTextField(
                value = title,
                onValueChange = { title = it.take(80); confirmed = false },
                label = { Text("Título do exame") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            OutlinedTextField(
                value = date,
                onValueChange = { date = it.take(10); confirmed = false },
                label = { Text("Data dd/mm/aaaa (opcional)") },
                isError = !OrganizerMetadata.validDate(date),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            Text("Tipo de exame", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = BragaTextPrimary)
            OrganizerMetadata.types.forEach { option ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = type == option, onClick = { type = option; confirmed = false })
                    Spacer(Modifier.width(6.dp))
                    Text(option, style = MaterialTheme.typography.bodyMedium)
                }
            }

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = BragaMintSurface,
                border = BorderStroke(1.dp, BragaMintBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = confirmed,
                        onCheckedChange = { confirmed = it },
                        enabled = seen.size == doc.pages && !state.busy
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Vi todas as páginas e conferi título, data e tipo.",
                        style = MaterialTheme.typography.bodySmall,
                        color = BragaTextPrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Button(
                onClick = {
                    vm.update(doc.copy(title = title.trim(), date = date.trim(), type = type, confirmed = true))
                    onClose()
                },
                enabled = confirmed && seen.size == doc.pages && title.isNotBlank() && OrganizerMetadata.validDate(date) && !state.busy,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = BragaEmerald)
            ) {
                Text("Confirmar revisão", fontWeight = FontWeight.Bold)
            }

            TextButton(onClick = onClose, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
                Text("Voltar sem confirmar", color = BragaTextSecondary)
            }
        }
    }
}

@Composable
private fun PagePreview(bitmap: android.graphics.Bitmap?, page: Int) {
    var scale by remember(page, bitmap) { mutableFloatStateOf(1f) }
    var offset by remember(page, bitmap) { mutableStateOf(Offset.Zero) }
    val transform = rememberTransformableState { zoom, pan, _ ->
        scale = (scale * zoom).coerceIn(1f, 5f)
        offset = if (scale == 1f) Offset.Zero else offset + pan
    }
    Box(
        Modifier
            .fillMaxWidth()
            .height(350.dp)
            .clipToBounds()
            .background(Color(0xFFF1F5F9), RoundedCornerShape(12.dp))
            .transformable(transform)
    ) {
        bitmap?.let {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = "Página ${page + 1}; use dois dedos para ampliar e mover",
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(scaleX = scale, scaleY = scale, translationX = offset.x, translationY = offset.y)
            )
        }
    }
    Text("Use dois dedos para ampliar e mover a página.", style = MaterialTheme.typography.bodySmall, color = BragaTextSecondary)
}
