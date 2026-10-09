package br.com.bragasaude.ui.organizer

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.core.content.ContextCompat
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import br.com.bragasaude.data.local.organizer.*
import br.com.bragasaude.ui.components.BragaAlertDialog
import br.com.bragasaude.ui.components.BragaBottomSheet
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrganizerScreen(onBack: () -> Unit, viewModel: OrganizerViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current
    var reviewing by remember { mutableStateOf<OrganizerDocument?>(null) }
    var appendTo by remember { mutableStateOf<String?>(null) }
    var ending by remember { mutableStateOf(false) }
    var resultReview by remember { mutableStateOf(false) }
    var sharing by remember { mutableStateOf(false) }
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
        camera.launch(uri)
    }
    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) capturePage() else { cameraError = "Permita o acesso à câmera para fotografar ou selecione arquivos já existentes."; appendTo = null }
    }
    fun requestCamera(documentId: String?) {
        appendTo = documentId; cameraError = null
        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.CAMERA) == android.content.pm.PackageManager.PERMISSION_GRANTED) capturePage()
        else cameraPermission.launch(android.Manifest.permission.CAMERA)
    }
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        if (uri != null) viewModel.save(uri)
    }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) viewModel.refresh() }
        lifecycle.lifecycle.addObserver(observer)
        onDispose { lifecycle.lifecycle.removeObserver(observer) }
    }
    // Hide clinical previews in screenshots and the recent-app thumbnail.
    val activity = context as? android.app.Activity
    DisposableEffect(activity) {
        val alreadySecure = activity?.window?.attributes?.flags?.and(WindowManager.LayoutParams.FLAG_SECURE) != 0
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { if (!alreadySecure) activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }
    BackHandler { onBack() }
    Scaffold(topBar = { TopAppBar(title = { Text("Organizar exames") }, navigationIcon = {
        TextButton(onClick = onBack) { Text("Voltar") }
    }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Prepare seus exames para a consulta", style = MaterialTheme.typography.headlineSmall)
            Text("Esta área organiza documentos temporariamente. Não é um local de armazenamento. Nada é enviado ao Braga Saúde. Salve o PDF fora do aplicativo e guarde seus originais.")
            Text("As cópias expiram em 24 horas. Depois desse prazo não poderão ser abertas; a limpeza ocorre quando o sistema permitir ou ao abrir esta área.")
            cameraError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            val session = state.session
            if (session == null) {
                Button(onClick = viewModel::start, enabled = !state.busy) { Text("Começar organização") }
            } else {
                Text("Até 20 documentos, 100 páginas, 35 MB por arquivo e 150 MB por sessão. PDF e fotos. Uma foto pode ser uma página do mesmo exame.")
                Button(onClick = { appendTo = null; picker.launch(arrayOf("application/pdf", "image/*")) }, enabled = !state.busy) { Text("Selecionar arquivos") }
                OutlinedButton(onClick = { requestCamera(null) }, enabled = !state.busy) { Text("Fotografar exame") }
                Text("O reconhecimento ocorre no dispositivo e apenas sugere título, data e tipo. Confira cada página: não interpretamos resultados nem diagnosticamos condições.")
                OrganizerMetadata.ordered(session.documents, state.newest).forEach { doc ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(doc.title, style = MaterialTheme.typography.titleMedium)
                            Text("${doc.date.ifBlank { "Data não informada" }} · ${doc.type} · ${doc.pages} página(s)")
                            Text(if (doc.confirmed) "Conferido" else "Revisão obrigatória")
                            TextButton(onClick = { reviewing = doc }, enabled = !state.busy) { Text("Ver e conferir documento") }
                            TextButton(onClick = { appendTo = doc.id; picker.launch(arrayOf("application/pdf", "image/*")) }, enabled = !state.busy) { Text("Adicionar páginas a este exame") }
                            TextButton(onClick = { requestCamera(doc.id) }, enabled = !state.busy) { Text("Fotografar outra página deste exame") }
                            TextButton(onClick = { viewModel.delete(doc.id) }, enabled = !state.busy) { Text("Remover da organização") }
                        }
                    }
                }
                if (session.documents.isNotEmpty()) {
                    Row { Checkbox(state.newest, { viewModel.order(it) }, enabled = !state.busy); Text("Mais recentes primeiro") }
                    Button(onClick = viewModel::generate, enabled = !state.busy && session.documents.all { it.confirmed }) { Text("Gerar PDF com índice") }
                }
                if (state.ready) {
                    Text("PDF pronto. Contém índice, divisórias e todas as páginas originais. A montagem não preserva assinaturas digitais; guarde os arquivos originais.")
                    OutlinedButton(onClick = { resultReview = true }, enabled = !state.busy) { Text("Conferir PDF gerado") }
                    Button(onClick = { save.launch("exames-organizados.pdf") }, enabled = !state.busy) { Text("Salvar fora do aplicativo") }
                    OutlinedButton(onClick = { sharing = true }, enabled = !state.busy) { Text("Compartilhar PDF") }
                }
                if (state.saved) Text("PDF salvo no destino escolhido. Você pode encerrar a organização.")
                TextButton(onClick = { ending = true }, enabled = !state.busy) { Text("Encerrar e apagar cópias temporárias") }
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
                Text("PDF organizado", style = MaterialTheme.typography.titleLarge)
                PagePreview(state.preview.takeIf { state.previewPage == page }, page)
                if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                Row {
                    TextButton(onClick = { page-- }, enabled = page > 0 && !state.busy) { Text("Anterior") }
                    Text("${page + 1}/${state.resultPages}")
                    TextButton(onClick = { page++ }, enabled = page + 1 < state.resultPages && !state.busy) { Text("Próxima") }
                }
                TextButton(onClick = { resultReview = false; viewModel.clearPreview() }, enabled = !state.busy) { Text("Fechar prévia") }
            }
        }
    }
    if (ending) BragaAlertDialog(onDismissRequest = { ending = false }, title = { Text("Encerrar organização?") },
        text = { Text(if (state.saved) "As cópias temporárias serão apagadas. Seus arquivos de origem e o PDF salvo fora do aplicativo permanecem."
            else "Não há confirmação de um PDF salvo. Compartilhar não confirma o salvamento. As fotos feitas aqui também serão apagadas. Salve antes se precisar delas.") },
        confirmButton = { TextButton(onClick = { ending = false; viewModel.end() }) { Text("Apagar cópias e encerrar") } },
        dismissButton = { TextButton(onClick = { ending = false }) { Text("Continuar organizando") } })
    if (sharing) BragaAlertDialog(onDismissRequest = { sharing = false }, title = { Text("Compartilhar dados de saúde?") },
        text = { Text("O aplicativo escolhido receberá o PDF e ficará responsável por essa cópia. Confira o destinatário. Compartilhar não confirma que o PDF foi salvo.") },
        confirmButton = { TextButton(onClick = {
            sharing = false
            viewModel.share { uri ->
                val intent = Intent(Intent.ACTION_SEND).setType("application/pdf").putExtra(Intent.EXTRA_STREAM, uri)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                intent.clipData = ClipData.newRawUri("Exames organizados", uri)
                context.startActivity(Intent.createChooser(intent, "Compartilhar PDF"))
            }
        }) { Text("Escolher destinatário") } }, dismissButton = { TextButton(onClick = { sharing = false }) { Text("Cancelar") } })
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
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Confira o documento original", style = MaterialTheme.typography.titleLarge)
            PagePreview(state.preview.takeIf { state.previewPage == page }, page)
            if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { page-- }, enabled = page > 0 && !state.busy) { Text("Anterior") }
                Text("${page + 1}/${doc.pages}")
                TextButton(onClick = { page++ }, enabled = page + 1 < doc.pages && !state.busy) { Text("Próxima") }
            }
            OutlinedTextField(title, { title = it.take(80); confirmed = false }, label = { Text("Título") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(date, { date = it.take(10); confirmed = false }, label = { Text("Data dd/mm/aaaa (opcional)") },
                isError = !OrganizerMetadata.validDate(date), modifier = Modifier.fillMaxWidth())
            OrganizerMetadata.types.forEach { option ->
                Row { RadioButton(type == option, { type = option; confirmed = false }); Text(option) }
            }
            Row { Checkbox(confirmed, { confirmed = it }, enabled = seen.size == doc.pages && !state.busy)
                Text("Vi todas as páginas e conferi título, data e tipo.") }
            Text("Se uma página estiver cortada ou ilegível, substitua o documento. Uma sugestão vazia deve ser preenchida por você.")
            Button(onClick = { vm.update(doc.copy(title = title.trim(), date = date.trim(), type = type, confirmed = true)); onClose() },
                enabled = confirmed && seen.size == doc.pages && title.isNotBlank() && OrganizerMetadata.validDate(date) && !state.busy) { Text("Confirmar revisão") }
            TextButton(onClick = onClose, enabled = !state.busy) { Text("Voltar sem confirmar") }
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
    Box(Modifier.fillMaxWidth().height(350.dp).clipToBounds().transformable(transform)) {
        bitmap?.let { Image(it.asImageBitmap(), "Página ${page + 1}; use dois dedos para ampliar e mover",
            Modifier.fillMaxSize().graphicsLayer(scaleX = scale, scaleY = scale, translationX = offset.x, translationY = offset.y)) }
    }
    Text("Use dois dedos para ampliar e mover a página.", style = MaterialTheme.typography.bodySmall)
}
