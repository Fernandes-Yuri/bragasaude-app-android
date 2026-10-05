package br.com.bragasaude.ui.exams

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.compose.runtime.saveable.rememberSaveable
import br.com.bragasaude.data.util.ExamPhotoDecoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.bragasaude.data.util.ImageQualityGatekeeper
import br.com.bragasaude.data.util.QualityEvaluationResult
import br.com.bragasaude.data.util.RejectionReason
import br.com.bragasaude.ui.components.EmeraldHeaderBanner
import br.com.bragasaude.ui.theme.*
import java.util.Date

/**
 * Assistente de Captura Multipage com Trava de Nitidez (Gatekeeper On-Device).
 * Conforme Fase 2 do Plano Mestre e Seção 1 do Caderno de Contratos (08_CADERNO_DE_CONTRATOS_EXAMES_E_DOSSIE.md).
 *
 * Suporta capturar de 1 a 5 páginas consecutivas.
 * Rejeita matematicamente fotos borradas (< 85.0 de variância) impedindo envio incorreto.
 */
@Composable
fun MultipageCaptureScreen(
    onBack: () -> Unit,
    onDocumentCompleted: (title: String, category: String, date: Date, pages: List<Bitmap>) -> Unit
) {
    val context = LocalContext.current
    var examTitle by remember { mutableStateOf("Exame Laboratorial") }
    var examCategory by remember { mutableStateOf("Laboratorial") }
    val capturedPages = remember { mutableStateListOf<Bitmap>() }

    // Estado da última foto capturada/avaliada
    var lastEvaluationResult by remember { mutableStateOf<QualityEvaluationResult?>(null) }
    var selectedPageIndex by remember { mutableStateOf<Int?>(null) }

    val scope = rememberCoroutineScope()
    var pendingPhotoPath by rememberSaveable { mutableStateOf<String?>(null) }
    var processingPhoto by remember { mutableStateOf(false) }

    fun readPhoto(uri: Uri, temporaryFile: File? = null) {
        processingPhoto = true
        scope.launch {
            try {
                val (bitmap, evaluation) = withContext(Dispatchers.IO) {
                    val bitmap = ExamPhotoDecoder.decode(context.contentResolver, uri)
                    bitmap to ImageQualityGatekeeper.evaluateImageQuality(bitmap)
                }
                lastEvaluationResult = evaluation
                if (evaluation is QualityEvaluationResult.Approved && capturedPages.size < 5) {
                    capturedPages.add(bitmap)
                    selectedPageIndex = capturedPages.lastIndex
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) {
                Toast.makeText(context, "Não foi possível ler a foto. Tente novamente ou use a galeria.", Toast.LENGTH_LONG).show()
            } finally {
                temporaryFile?.delete()
                processingPhoto = false
            }
        }
    }

    // A câmera grava a foto completa em um arquivo privado temporário.
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val file = pendingPhotoPath?.let(::File)
        pendingPhotoPath = null
        if (success && file != null) {
            readPhoto(Uri.fromFile(file), file)
        } else {
            file?.delete()
        }
    }

    fun launchCamera() {
        try {
            val directory = File(context.cacheDir, "exam_photos").apply { mkdirs() }
            val file = File.createTempFile("exam_", ".jpg", directory)
            pendingPhotoPath = file.absolutePath
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            cameraLauncher.launch(uri)
        } catch (_: SecurityException) {
            pendingPhotoPath?.let { File(it).delete() }
            pendingPhotoPath = null
            Toast.makeText(context, "Permita o acesso à câmera nas configurações do app ou use a galeria.", Toast.LENGTH_LONG).show()
        } catch (_: ActivityNotFoundException) {
            pendingPhotoPath?.let { File(it).delete() }
            pendingPhotoPath = null
            Toast.makeText(context, "Câmera indisponível. Escolha uma foto da galeria.", Toast.LENGTH_LONG).show()
        } catch (_: Exception) {
            pendingPhotoPath?.let { File(it).delete() }
            pendingPhotoPath = null
            Toast.makeText(context, "Não foi possível abrir a câmera. Tente novamente ou use a galeria.", Toast.LENGTH_LONG).show()
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) launchCamera()
        else Toast.makeText(context, "Para fotografar, permita a câmera nas configurações do app. Você também pode usar a galeria.", Toast.LENGTH_LONG).show()
    }

    fun requestCamera() {
        if (processingPhoto || pendingPhotoPath != null) return
        lastEvaluationResult = null
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            launchCamera()
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { readPhoto(it) }
    }

    Scaffold(
        containerColor = BragaBackground,
        topBar = {
            EmeraldHeaderBanner(
                title = "Captura de Laudo",
                subtitle = "Até 5 páginas por exame",
                onBack = onBack
            )
        },
        bottomBar = {
            Surface(
                color = BragaCardSurface,
                border = BorderStroke(1.dp, BragaMintBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Botão primário para concluir documento
                    val isRejectedActive = lastEvaluationResult is QualityEvaluationResult.Rejected
                    val canConclude = capturedPages.isNotEmpty() && !isRejectedActive && !processingPhoto && pendingPhotoPath == null

                    Button(
                        onClick = {
                            if (canConclude) {
                                onDocumentCompleted(
                                    examTitle.ifBlank { "Exame Laboratorial" },
                                    examCategory,
                                    Date(),
                                    capturedPages.toList()
                                )
                            }
                        },
                        enabled = canConclude,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = BragaEmerald,
                            contentColor = Color.White,
                            disabledContainerColor = BragaEmerald.copy(alpha = 0.35f),
                            disabledContentColor = Color.White.copy(alpha = 0.6f)
                        )
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = if (capturedPages.isEmpty()) "Adicione ao menos 1 página"
                            else "Concluir Documento (${capturedPages.size} ${if (capturedPages.size == 1) "página" else "páginas"})",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }

                    // Se a última foto foi reprovada pelo Gatekeeper, botão de envio fica DESABILITADO
                    // e oferecemos o botão de ação corretiva
                    if (isRejectedActive) {
                        OutlinedButton(
                            onClick = {
                                requestCamera()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = BragaEmergencyOrange
                            ),
                            border = BorderStroke(1.5.dp, BragaEmergencyOrange)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Tirar Outra Foto (Substituir)", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (processingPhoto) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())

            // Metadados do Exame (Título e Categoria)
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = BragaCardSurface),
                border = BorderStroke(1.dp, BragaMintBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Identificação do Exame",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = BragaTextPrimary
                    )

                    OutlinedTextField(
                        value = examTitle,
                        onValueChange = { examTitle = it },
                        label = { Text("Nome do Exame") },
                        placeholder = { Text("Ex: Hemograma, Perfil Lipídico") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }

            // Exibição de Alerta do Gatekeeper (se Foto for Rejeitada)
            lastEvaluationResult?.let { result ->
                when (result) {
                    is QualityEvaluationResult.Rejected -> {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = BragaEmergencyLight),
                            border = BorderStroke(1.5.dp, BragaEmergencyOrange)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Surface(
                                        shape = CircleShape,
                                        color = BragaEmergencyOrange.copy(alpha = 0.2f),
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                Icons.Default.Warning,
                                                contentDescription = null,
                                                tint = BragaEmergencyOrange,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                    Spacer(Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = "Aviso de Nitidez e Qualidade",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = BragaEmergencyOrange
                                        )
                                        Text(
                                            text = when (result.reason) {
                                                RejectionReason.BLUR_DETECTED -> "Foto fora de foco ou tremida"
                                                RejectionReason.LOW_RESOLUTION -> "Resolução muito baixa"
                                                RejectionReason.EXTREME_EXPOSURE -> "Iluminação muito clara ou escura"
                                            },
                                            style = MaterialTheme.typography.labelSmall,
                                            color = BragaTextSecondary
                                        )
                                    }
                                }

                                Text(
                                    text = result.userMessage,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = BragaTextPrimary,
                                    lineHeight = 20.sp
                                )

                                Text(
                                    text = "Tire outra foto para continuar.",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = BragaEmergencyOrange
                                )
                            }
                        }
                    }
                    is QualityEvaluationResult.Approved -> {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = BragaMint),
                            border = BorderStroke(1.dp, BragaEmerald.copy(alpha = 0.4f))
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = BragaEmerald,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "Foto adicionada",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = BragaEmerald
                                    )
                                    Text(
                                        text = "Confira os valores após a leitura.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = BragaTextPrimary
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Área de Ações de Captura
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = BragaCardSurface),
                border = BorderStroke(1.dp, BragaMintBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "Páginas do Documento (${capturedPages.size}/5)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = BragaTextPrimary
                    )

                    Text(
                        text = "Você pode anexar laudos com frente e verso ou até 5 páginas do mesmo exame.",
                        style = MaterialTheme.typography.bodySmall,
                        color = BragaTextSecondary
                    )

                    // Carrossel horizontal de miniaturas das páginas capturadas
                    if (capturedPages.isNotEmpty()) {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            itemsIndexed(capturedPages) { index, pageBitmap ->
                                PageThumbnailCard(
                                    pageNumber = index + 1,
                                    bitmap = pageBitmap,
                                    isSelected = selectedPageIndex == index,
                                    onSelect = { selectedPageIndex = index },
                                    onDelete = {
                                        capturedPages.removeAt(index)
                                        if (selectedPageIndex == index) {
                                            selectedPageIndex = if (capturedPages.isNotEmpty()) 0 else null
                                        }
                                    }
                                )
                            }
                        }
                    }

                    // Botões de Ação de Captura
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = {
                                requestCamera()
                            },
                            enabled = capturedPages.size < 5 && !processingPhoto && pendingPhotoPath == null,
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = BragaEmerald,
                                contentColor = Color.White
                            )
                        ) {
                            Icon(Icons.Default.PhotoCamera, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = if (capturedPages.isEmpty()) "Tirar 1ª Foto" else "+ Próxima Página",
                                fontWeight = FontWeight.Bold
                            )
                        }

                        OutlinedButton(
                            onClick = {
                                lastEvaluationResult = null
                                galleryLauncher.launch("image/*")
                            },
                            enabled = capturedPages.size < 5 && !processingPhoto && pendingPhotoPath == null,
                            modifier = Modifier
                                .weight(0.9f)
                                .height(50.dp),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, BragaMintBorder)
                        ) {
                            Icon(Icons.Default.Image, contentDescription = null, tint = BragaEmerald)
                            Spacer(Modifier.width(6.dp))
                            Text("Galeria", color = BragaEmerald, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            // Visualização ampliada da página selecionada
            selectedPageIndex?.let { idx ->
                if (idx in capturedPages.indices) {
                    val bitmap = capturedPages[idx]
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = BragaCardSurface),
                        border = BorderStroke(1.dp, BragaMintBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Visualização da Página ${idx + 1}",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = BragaTextPrimary
                                )

                                TextButton(
                                    onClick = {
                                        capturedPages.removeAt(idx)
                                        selectedPageIndex = if (capturedPages.isNotEmpty()) 0 else null
                                    }
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = null, tint = BragaEmergencyOrange, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Excluir", color = BragaEmergencyOrange, fontSize = 13.sp)
                                }
                            }

                            Spacer(Modifier.height(8.dp))

                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = "Página ${idx + 1}",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(240.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color.Black.copy(alpha = 0.05f)),
                                contentScale = ContentScale.Fit
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PageThumbnailCard(
    pageNumber: Int,
    bitmap: Bitmap,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onDelete: () -> Unit
) {
    val borderColor = if (isSelected) BragaEmerald else BragaMintBorder
    val borderWidth = if (isSelected) 2.dp else 1.dp

    Box(
        modifier = Modifier
            .width(100.dp)
            .height(130.dp)
            .clip(RoundedCornerShape(12.dp))
            .border(borderWidth, borderColor, RoundedCornerShape(12.dp))
            .background(BragaCardSurface)
            .clickable { onSelect() }
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Página $pageNumber",
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)),
                contentScale = ContentScale.Crop
            )

            Surface(
                color = if (isSelected) BragaMint else BragaCardSurface,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Pág. $pageNumber",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (isSelected) BragaEmerald else BragaTextPrimary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }
        }

        // Botão de deletar no canto superior direito
        Surface(
            shape = CircleShape,
            color = Color.Black.copy(alpha = 0.6f),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(4.dp)
                .size(22.dp)
                .clickable { onDelete() }
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Remover página",
                    tint = Color.White,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}
