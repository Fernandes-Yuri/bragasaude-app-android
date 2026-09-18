package br.com.bragasaude.ui.feedback

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.speech.RecognizerIntent
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import br.com.bragasaude.ui.util.rememberVoiceInputLauncher
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedbackScreen(
    onBack: () -> Unit,
    viewModel: FeedbackViewModel = hiltViewModel()
) {
    val category by viewModel.category.collectAsState()
    val title by viewModel.title.collectAsState()
    val message by viewModel.message.collectAsState()
    val screenshotBase64 by viewModel.screenshotBase64.collectAsState()
    val isVoiceTranscribed by viewModel.isVoiceTranscribed.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isSuccess by viewModel.isSuccess.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

    val context = LocalContext.current
    var selectedBitmap by remember { mutableStateOf<Bitmap?>(null) }

    // Launcher para reconhecimento de voz via microfone
    val voiceLauncher = rememberVoiceInputLauncher { transcription ->
        viewModel.onVoiceTranscriptionReceived(transcription)
    }

    // Launcher para seleção de print de tela (Imagem)
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val bitmap = loadAndCompressBitmap(context, it)
            if (bitmap != null) {
                selectedBitmap = bitmap
                val base64 = bitmapToBase64(bitmap)
                viewModel.setScreenshot(base64)
            }
        }
    }

    val launchVoiceInput = {
        val promptText = when (category) {
            "bug" -> "Descreva o problema ou erro que aconteceu"
            "passos" -> "Descreva sua dúvida ou dificuldade com a contagem de passos"
            "exames" -> "Descreva o que aconteceu no envio ou leitura de exames"
            else -> "Fale sua sugestão de melhoria para o Braga Saúde"
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale("pt", "BR"))
            putExtra(RecognizerIntent.EXTRA_PROMPT, promptText)
        }
        try {
            voiceLauncher.launch(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Falar com os Desenvolvedores", fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            // 1. Banner Informativo e Acolhedor
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(44.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                Icon(
                                    Icons.Default.SupportAgent,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                        Spacer(Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Canal direto com o time técnico",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                "Você pode digitar ou tocar no microfone para falar. Seus dados de diagnóstico são anexados automaticamente para agilizar o suporte.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // 2. Tags de Categorização Rápida (1 Toque)
            item {
                Text(
                    "Escolha o tipo de relato:",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FeedbackCategoryChip(
                        label = "💡 Ideia / Sugestão",
                        selected = category == "sugestao",
                        onClick = { viewModel.setCategory("sugestao") },
                        modifier = Modifier.weight(1f)
                    )
                    FeedbackCategoryChip(
                        label = "🐛 Problema / Bug",
                        selected = category == "bug",
                        onClick = { viewModel.setCategory("bug") },
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FeedbackCategoryChip(
                        label = "⏱️ Passos / Sensores",
                        selected = category == "passos",
                        onClick = { viewModel.setCategory("passos") },
                        modifier = Modifier.weight(1f)
                    )
                    FeedbackCategoryChip(
                        label = "Exames / Laudos",
                        selected = category == "exames",
                        onClick = { viewModel.setCategory("exames") },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // 3. Título / Assunto
            item {
                OutlinedTextField(
                    value = title,
                    onValueChange = { viewModel.setTitle(it) },
                    label = { Text("Assunto (opcional)") },
                    placeholder = { Text("Ex: Meta de passos, áudio de remédios, erro na leitura do PDF...") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )
            }

            // 4. Área de Texto Principal com Botão de Microfone
            item {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "Mensagem / Relato:",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(6.dp))

                    OutlinedTextField(
                        value = message,
                        onValueChange = { viewModel.setMessage(it) },
                        placeholder = {
                            Text(
                                "Escreva aqui sua mensagem detalhada, ou clique no botão do microfone abaixo para falar...",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 130.dp, max = 200.dp),
                        shape = RoundedCornerShape(14.dp)
                    )

                    Spacer(Modifier.height(8.dp))

                    // Botão do Microfone para Gravação e Transcrição por Voz
                    Button(
                        onClick = { launchVoiceInput() },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    ) {
                        Icon(Icons.Default.Mic, contentDescription = "Microfone", modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Ditar",
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            // 5. Card de Conferência e Validação da Transcrição
            if (isVoiceTranscribed) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9)),
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFA5D6A7))
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = Color(0xFF2E7D32),
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Áudio Transcrito com Sucesso!",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = Color(0xFF1B5E20)
                                )
                                Text(
                                    "Por favor, confira o texto na caixa acima e ajuste qualquer palavra se necessário antes de enviar.",
                                    fontSize = 12.sp,
                                    color = Color(0xFF2E7D32)
                                )
                            }
                        }
                    }
                }
            }

            // 6. Anexo Opcional de Captura de Tela (Print do Problema)
            item {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "Anexo de Imagem (Opcional):",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(6.dp))

                    if (selectedBitmap != null) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    Image(
                                        bitmap = selectedBitmap!!.asImageBitmap(),
                                        contentDescription = "Print anexado",
                                        modifier = Modifier
                                            .size(56.dp)
                                            .clip(RoundedCornerShape(8.dp)),
                                        contentScale = ContentScale.Crop
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Column {
                                        Text("Print anexado com sucesso", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                        Text("Pronto para envio", fontSize = 11.sp, color = Color.Gray)
                                    }
                                }

                                IconButton(
                                    onClick = {
                                        selectedBitmap = null
                                        viewModel.setScreenshot(null)
                                    }
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "Remover print", tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    } else {
                        OutlinedButton(
                            onClick = { imagePickerLauncher.launch("image/*") },
                            modifier = Modifier.fillMaxWidth().height(46.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Anexar print da tela com o problema", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }

            // 7. Aviso de Proteção LGPD e Telemetria
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Shield, contentDescription = null, tint = Color(0xFF2E7D32), modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Privacidade LGPD Ativa", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFF1B5E20))
                        }
                        Text(
                            "CPFs e telefones ditados são mascarados automaticamente antes do salvamento. Modelo do aparelho, versão do Android e status de passos são anexados para diagnóstico.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Mensagem de Erro se houver
            if (errorMessage != null) {
                item {
                    Text(
                        text = errorMessage ?: "",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // 8. Botão de Envio para a Equipe de Desenvolvimento
            item {
                Spacer(Modifier.height(4.dp))
                Button(
                    onClick = { viewModel.submitFeedback { /* handled via state */ } },
                    enabled = !isLoading && message.isNotBlank(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = Color.White
                    )
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            color = Color.White,
                            strokeWidth = 2.5.dp
                        )
                        Spacer(Modifier.width(10.dp))
                        Text("Enviando mensagem...")
                    } else {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(10.dp))
                        Text("Enviar", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    // Diálogo de Confirmação de Sucesso
    if (isSuccess) {
        AlertDialog(
            onDismissRequest = {
                viewModel.dismissSuccess()
                onBack()
            },
            icon = {
                Icon(
                    Icons.Default.VolunteerActivism,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(36.dp)
                )
            },
            title = {
                Text(
                    "Obrigado pelo seu Relato!",
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            },
            text = {
                Text(
                    "Sua mensagem e os dados de diagnóstico foram enviados com sucesso para a equipe técnica do Braga Saúde. Assim que uma melhoria for implementada, você verá um aviso no seu perfil!",
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.dismissSuccess()
                        onBack()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Concluir")
                }
            }
        )
    }
}

private fun loadAndCompressBitmap(context: Context, uri: Uri): Bitmap? {
    return try {
        val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
        val original = BitmapFactory.decodeStream(inputStream)
        inputStream?.close()

        if (original == null) return null

        // Redimensiona proporcionalmente para no máximo 800px para economizar banda e memória
        val maxDimension = 800
        val ratio = Math.min(maxDimension.toFloat() / original.width, maxDimension.toFloat() / original.height)
        if (ratio < 1.0f) {
            val width = (original.width * ratio).toInt()
            val height = (original.height * ratio).toInt()
            Bitmap.createScaledBitmap(original, width, height, true)
        } else {
            original
        }
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

private fun bitmapToBase64(bitmap: Bitmap): String {
    val outputStream = ByteArrayOutputStream()
    bitmap.compress(Bitmap.CompressFormat.JPEG, 75, outputStream)
    val byteArray = outputStream.toByteArray()
    return Base64.encodeToString(byteArray, Base64.NO_WRAP)
}

@Composable
private fun FeedbackCategoryChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Text(
                label,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
            )
        },
        modifier = modifier
    )
}

