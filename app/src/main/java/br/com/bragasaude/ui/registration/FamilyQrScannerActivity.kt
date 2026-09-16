package br.com.bragasaude.ui.registration

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import br.com.bragasaude.domain.FamilyConnectionCode
import br.com.bragasaude.ui.components.EmeraldHeaderBanner
import br.com.bragasaude.ui.theme.*
import com.google.zxing.BarcodeFormat
import com.google.zxing.ResultPoint
import com.journeyapps.barcodescanner.*

class FamilyQrScannerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { BragasaudeTheme { FamilyQrScanner(onCancel = { finish() }, onUse = { code ->
            setResult(RESULT_OK, Intent().putExtra("SCAN_RESULT", code))
            finish()
        }) } }
    }
}

@Composable
private fun FamilyQrScanner(onCancel: () -> Unit, onUse: (String) -> Unit) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var granted by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    var code by rememberSaveable { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var torch by rememberSaveable { mutableStateOf(false) }
    val hasFlash = remember { context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed ->
        granted = allowed
        message = if (allowed) null else "A câmera não foi autorizada. Você pode liberar o acesso nas configurações ou digitar o código."
    }
    val scanner = remember {
        BarcodeView(context).apply {
            decoderFactory = DefaultDecoderFactory(listOf(BarcodeFormat.QR_CODE))
            decodeContinuous(object : BarcodeCallback {
                override fun barcodeResult(result: BarcodeResult) {
                    val valid = FamilyConnectionCode.parse(result.text)
                    if (valid == null) message = "Este QR não é um código de conexão do Braga Saúde. Aponte para o QR exibido pelo familiar."
                    else { code = valid; message = null; pause() }
                }
                override fun possibleResultPoints(points: MutableList<ResultPoint>?) {}
            })
            addStateListener(object : CameraPreview.StateListener {
                override fun previewSized() {}
                override fun previewStarted() {}
                override fun previewStopped() {}
                override fun cameraClosed() {}
                override fun cameraError(error: Exception) { message = "Não foi possível abrir a câmera. Tente novamente ou digite o código." }
            })
        }
    }
    DisposableEffect(lifecycle, granted, code) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                if (granted && code == null) { message = null; scanner.resume() }
            } else if (event == Lifecycle.Event.ON_PAUSE) scanner.pause()
        }
        lifecycle.addObserver(observer)
        if (granted && code == null && lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) scanner.resume()
        else scanner.pause()
        onDispose { lifecycle.removeObserver(observer); scanner.pause() }
    }
    LaunchedEffect(torch) { scanner.setTorch(torch) }
    Scaffold(containerColor = BragaBackground, topBar = { EmeraldHeaderBanner(title = "Ler QR Code", subtitle = "Conecte-se ao seu familiar", onBack = onCancel) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (code == null) {
                Text("Enquadre o QR do familiar", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("Peça para ele abrir Círculo Familiar e mostrar o código de conexão.", color = BragaTextSecondary)
                if (granted) {
                    Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(24.dp)).background(Color.Black), contentAlignment = Alignment.Center) {
                        AndroidView(factory = { scanner }, modifier = Modifier.fillMaxSize())
                        Box(Modifier.fillMaxSize(0.76f).border(3.dp, Color.White, RoundedCornerShape(20.dp)))
                    }
                    if (hasFlash) OutlinedButton(onClick = { torch = !torch }, modifier = Modifier.heightIn(min = 48.dp)) {
                        Icon(if (torch) Icons.Default.FlashlightOff else Icons.Default.FlashlightOn, null)
                        Spacer(Modifier.width(8.dp)); Text(if (torch) "Apagar lanterna" else "Acender lanterna")
                    }
                } else {
                    Surface(color = BragaMint, shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Icon(Icons.Default.QrCodeScanner, null, tint = BragaEmerald, modifier = Modifier.size(64.dp))
                            Text("Permita o uso da câmera para ler o código.", style = MaterialTheme.typography.titleMedium)
                            Button(onClick = { permission.launch(Manifest.permission.CAMERA) }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Permitir câmera") }
                        }
                    }
                }
            } else {
                Surface(color = BragaMint, shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Icon(Icons.Default.CheckCircle, null, tint = BragaEmerald, modifier = Modifier.size(56.dp))
                        Text("Código identificado", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(code.orEmpty(), style = MaterialTheme.typography.headlineLarge, fontFamily = FontFamily.Monospace)
                        Text("Você ainda vai confirmar a conexão na próxima tela.", color = BragaTextSecondary)
                    }
                }
                Button(onClick = { code?.let(onUse) }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Usar este código") }
                TextButton(onClick = { code = null; torch = false }) { Text("Ler outro QR Code") }
            }
            message?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
                if (!granted) TextButton(onClick = {
                    runCatching { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + context.packageName))) }
                }) { Text("Abrir configurações") }
                else TextButton(onClick = { message = null; scanner.pause(); scanner.resume() }) { Text("Tentar novamente") }
            }
            OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                Icon(Icons.Default.Keyboard, null); Spacer(Modifier.width(8.dp)); Text("Prefiro digitar o código")
            }
        }
    }
}
