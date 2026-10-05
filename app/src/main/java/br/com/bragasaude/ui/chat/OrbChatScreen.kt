package br.com.bragasaude.ui.chat

import br.com.bragasaude.ui.components.BragaAlertDialog
import br.com.bragasaude.ui.components.BragaFormSheet

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
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
import androidx.compose.foundation.interaction.collectIsDraggedAsState
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
import androidx.compose.ui.text.style.TextAlign
import br.com.bragasaude.BuildConfig
import br.com.bragasaude.ai.BragaRoutingLogger
import br.com.bragasaude.ai.RoutingLogEntry
import br.com.bragasaude.data.remote.ai.OrbConnectionState
import br.com.bragasaude.ui.components.RiskNotificationDialog
import br.com.bragasaude.ui.util.Screen
import br.com.bragasaude.ui.util.OnDeviceSpeechRecognition
import kotlinx.coroutines.delay
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collectLatest
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
    var recognitionStarting by remember { mutableStateOf(false) }
    var recognitionActive by remember { mutableStateOf(false) }
    var recognitionRevision by remember { mutableIntStateOf(0) }
    var screenActive by remember { mutableStateOf(true) }
    var recognitionJob by remember { mutableStateOf<Job?>(null) }
    val voiceScope = rememberCoroutineScope()
    val recognition = remember(context) { runCatching { OnDeviceSpeechRecognition.create(context) } }
    val recognizer = recognition.getOrNull()
    fun stopVoice() {
        recognitionRevision++
        recognitionJob?.cancel()
        recognitionJob = null
        recognitionStarting = false
        recognitionActive = false
        listening = false
        recognizer?.cancel()
    }
    DisposableEffect(recognizer) {
        recognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { if (recognitionActive) listening = true }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() { listening = false }
            override fun onError(error: Int) {
                if (!recognitionActive || !screenActive) return
                recognitionActive = false
                listening = false
                viewModel.showError(OnDeviceSpeechRecognition.errorMessage(error))
            }
            override fun onResults(results: Bundle?) {
                if (!recognitionActive || !screenActive) return
                recognitionActive = false
                listening = false
                results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let(viewModel::updateVoiceInput)
            }
            override fun onPartialResults(partialResults: Bundle?) {
                if (!recognitionActive || !screenActive) return
                partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let(viewModel::updateVoiceInput)
            }
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        onDispose { stopVoice(); recognizer?.destroy() }
    }
    fun startVoice() {
        if (!screenActive) return
        if (recognizer == null) {
            viewModel.showError(recognition.exceptionOrNull()?.message
                ?: "Reconhecimento de voz no dispositivo indisponível. Você pode digitar no chat.")
            return
        }
        if (recognitionStarting || recognitionActive) { stopVoice(); return }
        val token = ++recognitionRevision
        recognitionStarting = true
        recognitionJob = voiceScope.launch {
            try {
                val intent = OnDeviceSpeechRecognition.intent()
                OnDeviceSpeechRecognition.checkPortugueseSupport(context, recognizer, intent)
                if (token != recognitionRevision || !screenActive) return@launch
                recognitionActive = true
                listening = true
                recognizer.startListening(intent)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                if (token == recognitionRevision && screenActive) {
                    recognitionActive = false
                    listening = false
                    viewModel.showError(failure.message
                        ?: "Não foi possível iniciar a voz no dispositivo. Você pode digitar no chat.")
                }
            } finally {
                if (token == recognitionRevision) recognitionStarting = false
            }
        }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startVoice() else viewModel.showError("Permita o microfone para ditar uma mensagem.")
    }
    DisposableEffect(lifecycle, viewModel) {
        screenActive = lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        viewModel.enterScreen()
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) { screenActive = true; viewModel.enterScreen() }
            if (event == Lifecycle.Event.ON_STOP) { screenActive = false; stopVoice(); viewModel.leaveScreen() }
        }
        lifecycle.addObserver(observer)
        onDispose { screenActive = false; stopVoice(); lifecycle.removeObserver(observer); viewModel.leaveScreen() }
    }
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is OrbChatEvent.Navigate -> onNavigate(event.screen)
                OrbChatEvent.Emergency -> emergency = true
            }
        }
    }
    OrbChatContent(state, connection, onBack, viewModel::updateInput, viewModel::sendInput,
        viewModel::cancelGeneration, viewModel::clearError, viewModel::retryConnection,
        viewModel::newConversation, viewModel::showHistory,
        onOpenConversation = { id -> state.conversations.find { it.id == id }?.let(viewModel::openConversation) },
        onConfirm = viewModel::confirmAction, onIgnore = viewModel::ignoreAction,
        onAudio = viewModel::toggleAudio, onSelectTextScale = viewModel::setTextScale,
        onClearCurrent = viewModel::clearCurrentMessages,
        onDeleteAll = viewModel::deleteAllConversations,
        onDeleteConversation = viewModel::deleteConversation,
        onExport = { clipboard.setText(AnnotatedString(viewModel.exportText())) },
        listening = listening || recognitionStarting, onVoice = {
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
    val dragging by scroll.interactionSource.collectIsDraggedAsState()
    val currentState by rememberUpdatedState(state)
    val scrollMessages = remember(state.messages) { state.messages.map { ChatScrollMessage(it.id, it.role == "user") } }
    val currentScrollMessages by rememberUpdatedState(scrollMessages)
    val scrollPolicy = remember { ChatScrollPolicy() }
    var scrollNavigation by remember { mutableStateOf(ChatScrollNavigation()) }
    var returnToLatestRequest by remember { mutableIntStateOf(0) }
    val snackbar = remember { SnackbarHostState() }
    var menu by remember { mutableStateOf(false) }
    var exported by remember { mutableStateOf(false) }
    var showTextSizeDialog by remember { mutableStateOf(false) }
    var showRoutingLogsDialog by remember { mutableStateOf(false) }
    var showClearCurrentDialog by remember { mutableStateOf(false) }
    var showDeleteAllDialog by remember { mutableStateOf(false) }
    var conversationToDelete by remember { mutableStateOf<br.com.bragasaude.data.local.OrbConversation?>(null) }

    LaunchedEffect(state.error) { state.error?.let { snackbar.showSnackbar(it); onClearError() } }
    LaunchedEffect(exported) { if (exported) { snackbar.showSnackbar("Conversa copiada."); exported = false } }
    // Um coletor conflacionado acompanha o crescimento do streaming e do viewport.
    // O marcador final permite chegar ao fim até de uma resposta maior que a tela.
    LaunchedEffect(scroll) {
        snapshotFlow {
            ChatScrollSnapshot(
                firstMessage = currentState.messages.firstOrNull()?.id,
                latestUser = currentState.messages.lastOrNull { it.role == "user" }?.id,
                messageCount = currentState.messages.size,
                partialLength = currentState.partialText.length,
                streaming = currentState.isStreaming,
                history = currentState.showHistory,
                dragging = dragging,
                atBottom = !scroll.canScrollForward,
                itemCount = scroll.layoutInfo.totalItemsCount,
                viewportEnd = scroll.layoutInfo.viewportEndOffset,
                messages = currentScrollMessages,
                returnRequest = returnToLatestRequest
            )
        }.collectLatest { snapshot ->
            scrollNavigation = scrollPolicy.update(snapshot)
            if (scrollNavigation.shouldFollow && !snapshot.atBottom && snapshot.itemCount == snapshot.messageCount + 2) {
                scroll.scrollToItem(snapshot.itemCount - 1)
            }
        }
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

    if (showRoutingLogsDialog) {
        RoutingLogsDialog(onDismiss = { showRoutingLogsDialog = false })
    }

    if (showClearCurrentDialog) {
        BragaAlertDialog(
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
        BragaAlertDialog(
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
        BragaAlertDialog(
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

            // Bolinha verde elegante sempre ativa e ligada (sem oscilar para vermelho)
            val dotColor = Color(0xFF10B981)

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
                        if (BuildConfig.DEBUG) {
                            DropdownMenuItem(text = { Text("Diagnóstico de Roteamento (Logs)") }, onClick = { menu = false; showRoutingLogsDialog = true })
                        }
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
                        if (scrollNavigation.showReturnToLatest) {
                            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.Center) {
                                ChatReturnToLatest(scrollNavigation.unreadMessages) { returnToLatestRequest++ }
                            }
                        }
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
                    item(key = "chatBottom") { Spacer(Modifier.fillMaxWidth().height(1.dp).testTag("chatBottom")) }
                }
            }
        }
    }
}

@Composable
private fun ChatReturnToLatest(unreadMessages: Int, onClick: () -> Unit) {
    val label = when (unreadMessages) {
        0 -> "Voltar ao fim"
        1 -> "1 nova mensagem · Voltar ao fim"
        else -> "$unreadMessages novas mensagens · Voltar ao fim"
    }
    FilledTonalButton(
        onClick = onClick,
        modifier = Modifier.heightIn(min = 48.dp).testTag("chatReturnToLatest")
            .semantics { contentDescription = label },
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = Color(0xFFE0F2EC), contentColor = Color(0xFF00695C)
        )
    ) {
        Icon(Icons.Default.ArrowDownward, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text(label)
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
        modifier = Modifier.testTag("typingIndicator"),
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

    BragaFormSheet(
        scrollContent = false,
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



@Composable
fun RoutingLogsDialog(onDismiss: () -> Unit) {
    val logs by br.com.bragasaude.ai.BragaRoutingLogger.logs.collectAsStateWithLifecycle()
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }

    val total = logs.size
    val localCount = logs.count { it.decision == br.com.bragasaude.ai.RoutingLogEntry.RoutingDecision.LOCAL_NLU }
    val cloudCount = logs.count { it.decision == br.com.bragasaude.ai.RoutingLogEntry.RoutingDecision.CLOUD_LLM }
    val localPercentage = if (total > 0) (localCount * 100 / total) else 0

    BragaAlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(Color(0xFF10B981)))
                    Spacer(Modifier.width(8.dp))
                    Text("Diagnóstico de Roteamento", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "Total: $total | NLU Local: $localCount ($localPercentage%) | Nuvem: $cloudCount",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF64748B)
                )
            }
        },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Button(
                        onClick = {
                            clipboard.setText(AnnotatedString(br.com.bragasaude.ai.BragaRoutingLogger.exportReport()))
                            copied = true
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00897B)),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(if (copied) "Copiado!" else "Copiar Todos os Logs", fontSize = 12.sp)
                    }

                    OutlinedButton(
                        onClick = { br.com.bragasaude.ai.BragaRoutingLogger.clear() },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text("Limpar", fontSize = 12.sp, color = Color(0xFFEF4444))
                    }
                }

                if (logs.isEmpty()) {
                    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        Text("Nenhum teste registrado ainda.\nEnvie mensagens de texto ou voz para ver o roteamento aqui.", textAlign = TextAlign.Center, color = Color(0xFF94A3B8), style = MaterialTheme.typography.bodySmall)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(logs, key = { it.id }) { item ->
                            val isLocal = item.decision == br.com.bragasaude.ai.RoutingLogEntry.RoutingDecision.LOCAL_NLU
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isLocal) Color(0xFFF0FDF4) else Color(0xFFF5F3FF),
                                border = BorderStroke(1.dp, if (isLocal) Color(0xFF86EFAC) else Color(0xFFDDD6FE)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(Modifier.padding(8.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = if (isLocal) Color(0xFF10B981) else Color(0xFF7C3AED)
                                        ) {
                                            Text(
                                                text = if (isLocal) "LOCAL NLU" else "NUVEM GROQ",
                                                color = Color.White,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                        Text(
                                            "${item.formattedTime} • ${item.channel.name} • ${item.durationMs}ms",
                                            fontSize = 10.sp,
                                            color = Color(0xFF64748B)
                                        )
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        "\"${item.input}\"",
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 12.sp,
                                        color = Color(0xFF1E293B)
                                    )
                                    Text(
                                        "Intent: ${item.intent}",
                                        fontSize = 11.sp,
                                        color = Color(0xFF0F766E),
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        "Motivo: ${item.reason}",
                                        fontSize = 10.sp,
                                        color = Color(0xFF64748B)
                                    )
                                    if (item.previewResponse.isNotBlank()) {
                                        Text(
                                            "Resp: ${item.previewResponse.take(90)}...",
                                            fontSize = 10.sp,
                                            color = Color(0xFF475569)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00897B))
            ) {
                Text("Fechar")
            }
        }
    )
}