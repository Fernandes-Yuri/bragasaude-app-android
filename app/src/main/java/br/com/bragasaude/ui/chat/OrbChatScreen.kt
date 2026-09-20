package br.com.bragasaude.ui.chat

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.bragasaude.BuildConfig
import br.com.bragasaude.data.remote.ai.OrbConnectionState
import br.com.bragasaude.ui.components.RiskNotificationDialog
import br.com.bragasaude.ui.util.Screen
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.sin
import kotlin.math.PI

@Composable
fun OrbChatScreen(onBack: () -> Unit, onNavigate: (Screen) -> Unit, viewModel: OrbChatViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val connection by viewModel.connection.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val clipboard = LocalClipboardManager.current
    var emergency by remember { mutableStateOf(false) }
    var listening by remember { mutableStateOf(false) }
    val recognizer = remember(context) {
        if (SpeechRecognizer.isRecognitionAvailable(context)) SpeechRecognizer.createSpeechRecognizer(context) else null
    }
    DisposableEffect(recognizer) {
        recognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { listening = true }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() { listening = false }
            override fun onError(error: Int) { listening = false; viewModel.showError("Não consegui ouvir. Você pode tentar novamente ou digitar.") }
            override fun onResults(results: Bundle?) {
                listening = false
                results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let(viewModel::updateInput)
            }
            override fun onPartialResults(partialResults: Bundle?) {
                partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let(viewModel::updateInput)
            }
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        onDispose { recognizer?.destroy() }
    }
    fun startVoice() {
        if (recognizer == null) { viewModel.showError("Reconhecimento de voz indisponível neste aparelho."); return }
        if (listening) { recognizer.stopListening(); listening = false; return }
        try {
            recognizer.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pt-BR")
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            })
        } catch (_: Exception) { viewModel.showError("Não foi possível iniciar o microfone.") }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startVoice() else viewModel.showError("Permita o microfone para ditar uma mensagem.")
    }
    DisposableEffect(lifecycle, viewModel) {
        viewModel.enterScreen()
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) viewModel.enterScreen()
            if (event == Lifecycle.Event.ON_STOP) { recognizer?.cancel(); listening = false; viewModel.leaveScreen() }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); viewModel.leaveScreen() }
    }
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is OrbChatEvent.Navigate -> onNavigate(event.screen)
                OrbChatEvent.Emergency -> emergency = true
            }
        }
    }
    OrbChatContent(state, connection, onBack, viewModel::updateInput, viewModel::sendMessage,
        viewModel::cancelGeneration, viewModel::clearError, viewModel::retryConnection,
        viewModel::newConversation, viewModel::showHistory,
        onOpenConversation = { id -> state.conversations.find { it.id == id }?.let(viewModel::openConversation) },
        onConfirm = viewModel::confirmAction, onIgnore = viewModel::ignoreAction,
        onAudio = viewModel::toggleAudio, onSelectTextScale = viewModel::setTextScale,
        onClearCurrent = viewModel::clearCurrentMessages,
        onDeleteAll = viewModel::deleteAllConversations,
        onDeleteConversation = viewModel::deleteConversation,
        onExport = { clipboard.setText(AnnotatedString(viewModel.exportText())) },
        listening = listening, onVoice = {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) startVoice()
            else permission.launch(Manifest.permission.RECORD_AUDIO)
        })
    if (emergency) RiskNotificationDialog(type = "EMERGENCIA",
        message = "Se precisar de socorro imediato, ligue 192. Escolha uma opção abaixo.", onDismiss = { emergency = false })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrbChatContent(
    state: OrbChatUiState,
    connection: OrbConnectionState = OrbConnectionState.CONNECTED,
    onBack: () -> Unit = {}, onInput: (String) -> Unit = {}, onSend: (String) -> Unit = {},
    onCancel: () -> Unit = {}, onClearError: () -> Unit = {}, onRetry: () -> Unit = {},
    onNew: () -> Unit = {}, onHistory: () -> Unit = {}, onOpenConversation: (String) -> Unit = {},
    onConfirm: (String) -> Unit = {}, onIgnore: (String) -> Unit = {}, onAudio: (ChatMessage) -> Unit = {},
    onSelectTextScale: (Float) -> Unit = {},
    onClearCurrent: () -> Unit = {},
    onDeleteAll: () -> Unit = {},
    onDeleteConversation: (String) -> Unit = {},
    onExport: () -> Unit = {}, listening: Boolean = false, onVoice: () -> Unit = {}
) {
    val scroll = rememberLazyListState()
    val snackbar = remember { SnackbarHostState() }
    var menu by remember { mutableStateOf(false) }
    var exported by remember { mutableStateOf(false) }
    var showTextSizeDialog by remember { mutableStateOf(false) }
    var showClearCurrentDialog by remember { mutableStateOf(false) }
    var showDeleteAllDialog by remember { mutableStateOf(false) }
    var conversationToDelete by remember { mutableStateOf<br.com.bragasaude.data.local.OrbConversation?>(null) }

    LaunchedEffect(state.error) { state.error?.let { snackbar.showSnackbar(it); onClearError() } }
    LaunchedEffect(exported) { if (exported) { snackbar.showSnackbar("Conversa copiada."); exported = false } }
    // AUD-AN04: antes era keyado em state.partialText, que muda a CADA TOKEN do
    // streaming — o efeito era cancelado e re-lançado a cada token, reiniciando
    // o scrollToItem e fazendo o auto-scroll engasgar. Keyar em isStreaming cobre
    // o início e o fim da resposta sem oscilar.
    LaunchedEffect(state.messages.size, state.isStreaming, state.showHistory) {
        if (!state.showHistory) scroll.scrollToItem(state.messages.size)
    }

    if (showTextSizeDialog) {
        ChatTextSizeDialog(
            currentScale = state.textScale,
            onSelect = { scale ->
                onSelectTextScale(scale)
                showTextSizeDialog = false
            },
            onDismiss = { showTextSizeDialog = false }
        )
    }

    if (showClearCurrentDialog) {
        AlertDialog(
            onDismissRequest = { showClearCurrentDialog = false },
            title = { Text("Limpar conversa atual?", fontWeight = FontWeight.Bold) },
            text = { Text("Todas as mensagens desta conversa serão apagadas.") },
            confirmButton = {
                Button(
                    onClick = {
                        onClearCurrent()
                        showClearCurrentDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                ) { Text("Limpar") }
            },
            dismissButton = {
                TextButton(onClick = { showClearCurrentDialog = false }) { Text("Cancelar") }
            }
        )
    }

    if (showDeleteAllDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteAllDialog = false },
            title = { Text("Excluir todo o histórico?", fontWeight = FontWeight.Bold) },
            text = { Text("Todas as conversas anteriores salvas no seu dispositivo serão removidas permanentemente.") },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteAll()
                        showDeleteAllDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                ) { Text("Excluir tudo") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAllDialog = false }) { Text("Cancelar") }
            }
        )
    }

    conversationToDelete?.let { conv ->
        AlertDialog(
            onDismissRequest = { conversationToDelete = null },
            title = { Text("Excluir esta conversa?", fontWeight = FontWeight.Bold) },
            text = { Text("Deseja apagar permanentemente a conversa \"${conv.title}\"?") },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteConversation(conv.id)
                        conversationToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                ) { Text("Excluir") }
            },
            dismissButton = {
                TextButton(onClick = { conversationToDelete = null }) { Text("Cancelar") }
            }
        )
    }

    Scaffold(
        modifier = Modifier.fillMaxSize().imePadding(),
        containerColor = Color(0xFFFAFCFB),
        snackbarHost = { SnackbarHost(snackbar, Modifier.testTag("chatError")) },
        topBar = {
            val infiniteTransition = rememberInfiniteTransition(label = "connectionPulse")
            val pulseAlpha = if (connection == OrbConnectionState.CONNECTED) {
                infiniteTransition.animateFloat(0.6f, 1.0f, infiniteRepeatable(tween(1500), RepeatMode.Reverse), label = "pulse").value
            } else if (connection == OrbConnectionState.RECONNECTING) {
                infiniteTransition.animateFloat(0.3f, 1.0f, infiniteRepeatable(tween(500), RepeatMode.Reverse), label = "pulse").value
            } else {
                1.0f
            }

            val dotColor = when (connection) {
                OrbConnectionState.CONNECTED -> Color(0xFF10B981)
                OrbConnectionState.RECONNECTING -> Color(0xFFF59E0B)
                else -> Color(0xFFEF4444)
            }

            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(dotColor.copy(alpha = pulseAlpha)))
                        Spacer(Modifier.width(8.dp))
                        Text("Braga Assistente", style = MaterialTheme.typography.titleMedium)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.White,
                    titleContentColor = Color(0xFF1E293B),
                    navigationIconContentColor = Color(0xFF1E293B),
                    actionIconContentColor = Color(0xFF64748B)
                ),
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar") } },
                actions = {
                    IconButton(onClick = onNew) { Icon(Icons.Default.Add, "Nova conversa") }
                    IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "Opções") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Conversas anteriores") }, onClick = { menu = false; onHistory() })
                        DropdownMenuItem(text = { Text("Ajustar tamanho do texto") }, onClick = { menu = false; showTextSizeDialog = true })
                        DropdownMenuItem(
                            text = { Text("Limpar conversa atual") },
                            enabled = state.messages.isNotEmpty(),
                            onClick = { menu = false; showClearCurrentDialog = true }
                        )
                        DropdownMenuItem(
                            text = { Text("Excluir todas as conversas") },
                            enabled = state.conversations.isNotEmpty() || state.messages.isNotEmpty(),
                            onClick = { menu = false; showDeleteAllDialog = true }
                        )
                        DropdownMenuItem(
                            text = { Text("Exportar para área de transferência") },
                            enabled = state.messages.isNotEmpty(),
                            onClick = { menu = false; onExport(); exported = true }
                        )
                    }
                }
            )
        },
        bottomBar = {
            if (!state.showHistory) {
                Surface(color = Color.Transparent, tonalElevation = 0.dp) {
                    Column {
                        ChatInput(state.input, onInput, onSend, state.isStreaming, onCancel, listening, onVoice)
                    }
                }
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (state.showHistory) {
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Suas conversas", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() }, color = Color(0xFF1E293B))
                            if (state.conversations.isNotEmpty()) {
                                TextButton(onClick = { showDeleteAllDialog = true }) {
                                    Text("Excluir todas", color = Color(0xFFEF4444))
                                }
                            }
                        }
                    }
                    item { Button(onClick = onNew, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00897B))) { Text("Nova conversa") } }
                    if (state.conversations.isEmpty()) item { Text("Bom dia! Comece uma conversa com o Braga sobre sua rotina de saúde.", color = Color(0xFF64748B)) }
                    items(state.conversations, key = { it.id }) { conversation ->
                        OutlinedCard(onClick = { onOpenConversation(conversation.id) }, modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(conversation.title, style = MaterialTheme.typography.titleMedium, color = Color(0xFF1E293B))
                                    Text(SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(Date(conversation.updatedAt)), style = MaterialTheme.typography.labelSmall, color = Color(0xFF64748B))
                                }
                                IconButton(onClick = { conversationToDelete = conversation }) {
                                    Icon(Icons.Default.DeleteOutline, contentDescription = "Excluir conversa", tint = Color(0xFFEF4444))
                                }
                            }
                        }
                    }
                }
            } else {
                LazyColumn(Modifier.fillMaxSize().testTag("chatMessages"), state = scroll,
                    contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(state.messages, key = { it.id }) { message ->
                        ChatBubble(message, state.textScale, state.speakingId == message.id,
                            onAudio = { onAudio(message) }, onConfirm = { onConfirm(message.id) }, onIgnore = { onIgnore(message.id) })
                    }
                    item(key = "stream") {
                        if (state.isStreaming) {
                            Column {
                                if (state.partialText.isNotBlank()) StreamingText(state.partialText, state.textScale)
                                TypingIndicator()
                            }
                        } else if (state.messages.isEmpty()) {
                            Box(Modifier.fillMaxWidth().padding(top = 40.dp), contentAlignment = Alignment.Center) {
                                var visible by remember { mutableStateOf(false) }
                                LaunchedEffect(Unit) { visible = true }
                                
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                                    val pulseAlpha by infiniteTransition.animateFloat(0.4f, 1f, infiniteRepeatable(tween(1000), RepeatMode.Reverse), label = "pulse")
                                    
                                    Box(Modifier.size(48.dp).clip(CircleShape).background(Color(0xFF00897B).copy(alpha = pulseAlpha)), contentAlignment = Alignment.Center) {
                                        Icon(Icons.Default.Add, contentDescription = "Saúde", tint = Color.White)
                                    }
                                    
                                    Spacer(Modifier.height(16.dp))
                                    
                                    AnimatedVisibility(
                                        visible = visible,
                                        enter = fadeIn(tween(800)) + slideInVertically(initialOffsetY = { it / 4 })
                                    ) {
                                        Text("Olá! Como posso ajudar com sua saúde hoje?", color = Color(0xFF1E293B))
                                    }
                                    
                                    Spacer(Modifier.height(24.dp))
                                    
                                    val suggestions = listOf("Como está minha pressão?", "O que posso almoçar?", "Lembrete de remédio", "Preciso de ajuda")
                                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        itemsIndexed(suggestions) { index, suggestion ->
                                            var chipVisible by remember { mutableStateOf(false) }
                                            LaunchedEffect(Unit) {
                                                delay(index * 150L)
                                                chipVisible = true
                                            }
                                            AnimatedVisibility(
                                                visible = chipVisible,
                                                enter = fadeIn(tween(300)) + slideInVertically(initialOffsetY = { it / 2 })
                                            ) {
                                                SuggestionChip(
                                                    onClick = { onSend(suggestion) },
                                                    label = { Text(suggestion) }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ChatBubble(message: ChatMessage, textScale: Float = 1f, speaking: Boolean = false,
               onAudio: () -> Unit = {}, onConfirm: () -> Unit = {}, onIgnore: () -> Unit = {}) {
    val user = message.role == "user"
    var visible by remember { mutableStateOf(false) }
    
    LaunchedEffect(Unit) {
        visible = true
    }

    AnimatedVisibility(
        visible = visible,
        enter = slideInHorizontally(initialOffsetX = { if (user) it / 4 else -it / 4 }) + fadeIn(tween(350))
    ) {
        BoxWithConstraints {
            val maxBubbleWidth = if (maxWidth < 600.dp) maxWidth * 0.85f else 480.dp

            Row(Modifier.fillMaxWidth(), horizontalArrangement = if (user) Arrangement.End else Arrangement.Start) {
                val shape = if (user) RoundedCornerShape(20.dp, 20.dp, 6.dp, 20.dp) else RoundedCornerShape(20.dp, 20.dp, 20.dp, 6.dp)
                
                Surface(
                    color = Color.Transparent,
                    contentColor = if (user) Color.White else Color(0xFF1E293B),
                    shape = shape,
                    modifier = Modifier.widthIn(max = maxBubbleWidth).semantics { isTraversalGroup = true }
                ) {
                    val bgModifier = if (user) {
                        Modifier.background(Brush.linearGradient(listOf(Color(0xFF00897B), Color(0xFF00695C))))
                    } else {
                        Modifier.background(Color(0xFFF4FBF9)).border(1.dp, Color(0xFFB2DFDB), shape)
                    }

                    Box(modifier = bgModifier) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (!user) {
                                    Box(Modifier.size(6.dp).clip(CircleShape).background(Color(0xFF00897B)))
                                    Spacer(Modifier.width(6.dp))
                                }
                                Text(if (user) "Você" else "Braga", style = MaterialTheme.typography.labelMedium, color = if (user) Color.White else Color(0xFF1E293B))
                            }
                            Text(linkedText(message.text), color = if (user) Color.White else Color(0xFF1E293B), fontSize = (17 * textScale).sp,
                                modifier = Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState()))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                val statusSuffix = when (message.status) {
                                    "cancelled" -> " · cancelada"
                                    "error" -> " · não enviada"
                                    else -> ""
                                }
                                Text(SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(message.timestamp)) + statusSuffix,
                                    style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f), color = if (user) Color.White.copy(alpha = 0.7f) else Color(0xFF64748B))
                                if (!user && message.status != "cancelled") IconButton(onClick = onAudio) {
                                    if (speaking) AudioWaves() else Icon(Icons.Default.VolumeUp, "Ouvir resposta", tint = Color(0xFF64748B))
                                }
                            }
                            if (message.action != null) {
                                HorizontalDivider(color = if (user) Color.White.copy(alpha = .3f) else Color(0xFFB2DFDB))
                                Text("Sugestão: ${actionLabel(message.action)}", color = if (user) Color.White else Color(0xFF1E293B))
                                val preview = remember(message.parameters) { actionPreview(message.parameters) }
                                if (preview.isNotBlank()) Text(preview, color = if (user) Color.White else Color(0xFF1E293B))
                                if (message.actionStatus == "pending") Row {
                                    TextButton(onClick = onConfirm) { Text("Confirmar", color = Color(0xFF00897B)) }
                                    TextButton(onClick = onIgnore) { Text("Cancelar", color = Color(0xFF64748B)) }
                                } else Text(when (message.actionStatus) { "confirmed" -> "Confirmada"; "running" -> "Executando…"; else -> "Ignorada" }, color = if (user) Color.White else Color(0xFF1E293B))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StreamingText(text: String, textScale: Float = 1f) {
    BoxWithConstraints {
        val maxBubbleWidth = if (maxWidth < 600.dp) maxWidth * 0.85f else 480.dp
        Surface(color = Color(0xFFF4FBF9), contentColor = Color(0xFF1E293B), shape = RoundedCornerShape(18.dp), border = BorderStroke(1.dp, Color(0xFFB2DFDB)), modifier = Modifier.widthIn(max = maxBubbleWidth)) {
            Text(text, fontSize = (17 * textScale).sp, modifier = Modifier.padding(14.dp).testTag("streamingText"))
        }
    }
}

@Composable
fun TypingIndicator() {
    val infiniteTransition = rememberInfiniteTransition(label = "typing")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing)),
        label = "phase"
    )

    Surface(
        color = Color.Transparent,
        shape = RoundedCornerShape(20.dp, 20.dp, 20.dp, 6.dp)
    ) {
        Box(
            Modifier
                .background(Color(0xFFF4FBF9))
                .border(1.dp, Color(0xFFB2DFDB), RoundedCornerShape(20.dp, 20.dp, 20.dp, 6.dp))
                .padding(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Braga ", style = MaterialTheme.typography.labelMedium, color = Color(0xFF1E293B))
                for (i in 0 until 3) {
                    val dotPhase = (phase + i * 0.33f) * 2 * PI.toFloat()
                    val fraction = ((sin(dotPhase) + 1f) / 2f).toFloat()
                    val animValue = 0.3f + 0.7f * fraction
                    Box(
                        Modifier
                            .size(8.dp)
                            .scale(animValue)
                            .clip(CircleShape)
                            .background(Color(0xFF00897B).copy(alpha = animValue))
                    )
                }
            }
        }
    }
}

@Composable
private fun AudioWaves() {
    val transition = rememberInfiniteTransition(label = "audio")
    val height by transition.animateFloat(.3f, 1f, infiniteRepeatable(tween(350), RepeatMode.Reverse), label = "waves")
    Row(Modifier.size(24.dp).semantics { contentDescription = "Parar áudio" }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        repeat(4) { i -> Box(Modifier.width(3.dp).height((24 * if (i % 2 == 0) height else 1.3f - height).dp).background(Color(0xFF00897B))) }
    }
}

@Composable
fun ChatInput(text: String, onTextChange: (String) -> Unit, onSend: (String) -> Unit,
              isStreaming: Boolean = false, onCancel: () -> Unit = {}, listening: Boolean = false, onVoice: () -> Unit = {}) {
    Surface(
        color = Color.White,
        modifier = Modifier
            .fillMaxWidth()
            .border(BorderStroke(1.dp, Color(0xFFE5EBE8)), RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
    ) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.Bottom) {
            Box(contentAlignment = Alignment.Center) {
                if (listening) {
                    val transition = rememberInfiniteTransition(label = "micPulse")
                    val pulse by transition.animateFloat(1f, 1.5f, infiniteRepeatable(tween(600), RepeatMode.Reverse), label = "pulse")
                    Box(Modifier.size(40.dp).scale(pulse).clip(CircleShape).border(1.dp, Color(0xFFFF5252).copy(alpha = 0.5f), CircleShape))
                }
                IconButton(onClick = onVoice, enabled = !isStreaming) {
                    Icon(
                        if (listening) Icons.Default.Stop else Icons.Default.Mic,
                        if (listening) "Parar ditado" else "Ditar mensagem",
                        tint = if (listening) Color(0xFFFF5252) else Color(0xFF00897B)
                    )
                }
            }
            
            OutlinedTextField(
                value = text,
                onValueChange = onTextChange,
                placeholder = { Text(if (listening) "Ouvindo…" else "Digite sua mensagem…") },
                modifier = Modifier.weight(1f).testTag("chatInput").onPreviewKeyEvent {
                    if (it.key == Key.Enter && !it.isShiftPressed) {
                        if (it.type == KeyEventType.KeyDown && !isStreaming && text.isNotBlank()) onSend(text)
                        true
                    } else false
                },
                maxLines = 5,
                enabled = !isStreaming,
                shape = RoundedCornerShape(24.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { if (!isStreaming && text.isNotBlank()) onSend(text) }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedTextColor = Color(0xFF1E293B),
                    unfocusedTextColor = Color(0xFF1E293B),
                    cursorColor = Color(0xFF00897B),
                    focusedBorderColor = Color(0xFF00897B),
                    unfocusedBorderColor = Color(0xFFE5EBE8),
                    focusedPlaceholderColor = Color(0xFF64748B),
                    unfocusedPlaceholderColor = Color(0xFF64748B)
                )
            )
            
            if (isStreaming) {
                IconButton(onClick = onCancel) { Icon(Icons.Default.StopCircle, "Cancelar geração", tint = Color(0xFF64748B)) }
            } else {
                IconButton(onClick = { onSend(text) }, enabled = text.isNotBlank()) {
                    Icon(Icons.AutoMirrored.Filled.Send, "Enviar mensagem", tint = Color(0xFF00897B))
                }
            }
        }
    }
}

private fun linkedText(text: String): AnnotatedString = buildAnnotatedString {
    append(text)
    val pattern = Regex("https?://[^\\s]+|www\\.[^\\s]+|(?:\\+?55[ .-]?)?\\(?[1-9][0-9]\\)?[ .-]?[0-9]{4,5}[ .-]?[0-9]{4}")
    pattern.findAll(text).forEach { match ->
        val raw = match.value.trimEnd('.', ',', ')', ';')
        val url = if (raw.startsWith("http")) raw else if (raw.startsWith("www.")) "https://$raw" else {
            val digits = raw.filter { it.isDigit() }
            "https://wa.me/" + if (digits.length in 10..11) "55$digits" else digits
        }
        addLink(LinkAnnotation.Url(url, TextLinkStyles(style = SpanStyle(color = Color(0xFF00897B), textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline))), match.range.first, match.range.first + raw.length)
    }
}

private fun actionPreview(parameters: String): String = try {
    val p = org.json.JSONObject(parameters)
    // Agente B1: prévia dos itens da lista.
    if (p.has("items") && !p.isNull("items")) {
        val arr = p.optJSONArray("items")
        if (arr != null && arr.length() > 0) {
            "Itens: " + List(arr.length()) { arr.optString(it) }.filter { it.isNotBlank() }.joinToString(", ")
        } else "Abrir lista de compras"
    } else {
        val labels = mapOf("sistolica" to "Sistólica", "diastolica" to "Diastólica", "glicemia" to "Glicemia",
            "quantidade_ml" to "Água (ml)", "batimentos" to "Batimentos", "saturacao" to "Saturação", "alimento" to "Alimento", "tipo_metrica" to "Métrica")
        labels.mapNotNull { (key, label) -> if (p.has(key) && !p.isNull(key)) "$label: ${p.get(key)}" else null }.joinToString(" · ")
    }
} catch (_: Exception) { "" }

@Composable
fun ChatTextSizeDialog(
    currentScale: Float,
    onSelect: (Float) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedScale by remember { mutableStateOf(currentScale) }
    val options = listOf(
        1.0f to "Padrão (100%)",
        1.15f to "Médio (115%)",
        1.30f to "Grande (130%)",
        1.50f to "Muito Grande (150%)"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Tune, contentDescription = null, tint = Color(0xFF00897B))
                Spacer(Modifier.width(8.dp))
                Text("Tamanho do Texto", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                Text(
                    "Escolha o tamanho ideal para ler as respostas do Braga com total conforto:",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF64748B)
                )

                // Prévia do Balão
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFFF4FBF9),
                    border = BorderStroke(1.dp, Color(0xFFB2DFDB)),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(6.dp).clip(CircleShape).background(Color(0xFF00897B)))
                            Spacer(Modifier.width(6.dp))
                            Text("Braga", style = MaterialTheme.typography.labelSmall, color = Color(0xFF00897B), fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Olá! Acompanho sua rotina e suas metas de saúde todos os dias.",
                            fontSize = (16 * selectedScale).sp,
                            color = Color(0xFF1E293B)
                        )
                    }
                }

                // Opções de Escala
                options.forEach { (scale, label) ->
                    val isSelected = Math.abs(selectedScale - scale) < 0.05f
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = isSelected,
                                onClick = { selectedScale = scale }
                            )
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = isSelected,
                            onClick = { selectedScale = scale },
                            colors = RadioButtonDefaults.colors(selectedColor = Color(0xFF00897B))
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            label,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) Color(0xFF00897B) else Color(0xFF1E293B)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSelect(selectedScale); onDismiss() },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00897B))
            ) {
                Text("Aplicar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar", color = Color(0xFF64748B))
            }
        }
    )
}
