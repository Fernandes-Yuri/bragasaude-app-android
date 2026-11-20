package br.com.bragasaude.ui.profile

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import br.com.bragasaude.data.backup.GoogleDriveAppDataClient
import br.com.bragasaude.domain.b2b.B2BThemeEngine
import br.com.bragasaude.ui.components.BragaBottomSheet
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class DrivePendingOperation {
    NONE,
    BACKUP,
    RESTORE
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatBackupBottomSheet(
    onDismiss: () -> Unit,
    viewModel: ChatBackupViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val scope = rememberCoroutineScope()

    val isWorking by viewModel.isWorking.collectAsState()
    val autoEnabled by viewModel.autoBackupEnabled.collectAsState()
    val lastBackupAtMs by viewModel.lastBackupAtMs.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

    var passphrase by rememberSaveable { mutableStateOf("") }
    var pendingDriveOp by rememberSaveable { mutableStateOf(DrivePendingOperation.NONE) }
    var pendingFileBytes by remember { mutableStateOf<ByteArray?>(null) }

    val createBackupDocLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        val bytes = pendingFileBytes
        pendingFileBytes = null
        if (uri == null || bytes == null) return@rememberLauncherForActivityResult
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(bytes)
                    } ?: error("Não foi possível gravar no destino selecionado.")
                }
            }.onFailure { err ->
                viewModel.setError(err.message ?: "Erro ao salvar arquivo .bragabkp.")
            }
        }
    }

    val openBackupDocLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        val picked = uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            val loaded = runCatching {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(picked)?.use { it.readBytes() }
                        ?: error("Não foi possível ler o arquivo selecionado.")
                }
            }
            loaded.fold(
                onSuccess = { bytes -> viewModel.restoreFromFileBytes(bytes, passphrase) },
                onFailure = { err -> viewModel.setError(err.message ?: "Erro ao abrir arquivo de backup.") }
            )
        }
    }

    val driveAuthLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        val op = pendingDriveOp
        pendingDriveOp = DrivePendingOperation.NONE
        val act = activity ?: return@rememberLauncherForActivityResult
        if (result.resultCode != Activity.RESULT_OK || result.data == null) {
            viewModel.setError("Autorização ao Google Drive cancelada.")
            return@rememberLauncherForActivityResult
        }
        runCatching {
            Identity.getAuthorizationClient(act).getAuthorizationResultFromIntent(result.data)
        }.fold(
            onSuccess = { authResult ->
                val token = authResult.accessToken
                if (token.isNullOrBlank()) {
                    viewModel.setError("Token do Google Drive indisponível.")
                } else {
                    when (op) {
                        DrivePendingOperation.BACKUP -> viewModel.backupToGoogleDrive(token, passphrase)
                        DrivePendingOperation.RESTORE -> viewModel.restoreFromGoogleDrive(token, passphrase)
                        DrivePendingOperation.NONE -> Unit
                    }
                }
            },
            onFailure = { err ->
                viewModel.setError(err.message ?: "Não foi possível concluir o acesso ao Google Drive.")
            }
        )
    }

    fun requestDriveAccess(op: DrivePendingOperation) {
        val act = activity
        if (act == null) {
            viewModel.setError("Tela indisponível para autorização Google Drive.")
            return
        }
        if (op == DrivePendingOperation.BACKUP && !viewModel.validatePassphraseForExport(passphrase)) {
            return
        }
        if (op == DrivePendingOperation.RESTORE && passphrase.isBlank()) {
            viewModel.setError("Informe a senha de recuperação antes de restaurar.")
            return
        }
        viewModel.consumeMessages()
        val authRequest = AuthorizationRequest.builder()
            .setRequestedScopes(listOf(Scope(GoogleDriveAppDataClient.DRIVE_APPDATA_SCOPE)))
            .build()
        Identity.getAuthorizationClient(act)
            .authorize(authRequest)
            .addOnSuccessListener { authResult ->
                if (authResult.hasResolution()) {
                    val intentSender = authResult.pendingIntent?.intentSender
                    if (intentSender != null) {
                        pendingDriveOp = op
                        driveAuthLauncher.launch(IntentSenderRequest.Builder(intentSender).build())
                    } else {
                        viewModel.setError("Não foi possível abrir a autorização do Google Drive.")
                    }
                } else {
                    val token = authResult.accessToken
                    if (token.isNullOrBlank()) {
                        viewModel.setError("Acesso ao Google Drive indisponível neste dispositivo.")
                    } else if (op == DrivePendingOperation.BACKUP) {
                        viewModel.backupToGoogleDrive(token, passphrase)
                    } else if (op == DrivePendingOperation.RESTORE) {
                        viewModel.restoreFromGoogleDrive(token, passphrase)
                    }
                }
            }
            .addOnFailureListener { err ->
                viewModel.setError(
                    err.message ?: "Google Drive indisponível neste aparelho. Use o arquivo criptografado (.bragabkp)."
                )
            }
    }

    BragaBottomSheet(onDismissRequest = { if (!isWorking) onDismiss() }) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Backup de Conversas",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
            ) {
                Text(
                    text = "${B2BThemeEngine.LOCAL_CHAT_DEVICE_ONLY_NOTICE} Ao criar um backup, suas conversas da família, assistente IA e programas parceiros são compactadas e cifradas de ponta a ponta apenas com a senha abaixo antes de sair do celular.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(12.dp)
                )
            }

            lastBackupAtMs?.let { ts ->
                val formatted = remember(ts) {
                    SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(ts))
                }
                Text(
                    text = "Último backup no Google Drive: $formatted",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
            }

            OutlinedTextField(
                value = passphrase,
                onValueChange = { passphrase = it },
                label = { Text("Senha de recuperação (mín. 8 caracteres)") },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                enabled = !isWorking,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            Text(
                text = "Importante: sem esta senha não é possível abrir o backup em outro aparelho.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            statusMessage?.let { msg ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                ) {
                    Text(
                        text = msg,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }

            errorMessage?.let { err ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f)
                ) {
                    Text(
                        text = err,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }

            Button(
                onClick = { requestDriveAccess(DrivePendingOperation.BACKUP) },
                enabled = !isWorking,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Salvar no Google Drive", fontWeight = FontWeight.Bold)
            }

            OutlinedButton(
                onClick = { requestDriveAccess(DrivePendingOperation.RESTORE) },
                enabled = !isWorking,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Restaurar do Google Drive")
            }

            HorizontalDivider()

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                    Text(
                        text = "Backup automático no Google Drive",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Semanal, somente conectado no Wi-Fi e carregando.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = autoEnabled,
                    enabled = !isWorking,
                    onCheckedChange = { checked ->
                        viewModel.toggleAutoBackup(checked, passphrase)
                    }
                )
            }

            HorizontalDivider()

            OutlinedButton(
                onClick = {
                    scope.launch {
                        val bytes = viewModel.prepareEncryptedBackupBytes(passphrase)
                        if (bytes != null) {
                            pendingFileBytes = bytes
                            val dayTag = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
                            createBackupDocLauncher.launch("braga-conversas-$dayTag.bragabkp")
                        }
                    }
                },
                enabled = !isWorking,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Exportar arquivo cifrado (.bragabkp)")
            }

            OutlinedButton(
                onClick = {
                    if (passphrase.isBlank()) {
                        viewModel.setError("Informe a senha de recuperação antes de escolher o arquivo.")
                    } else {
                        viewModel.consumeMessages()
                        openBackupDocLauncher.launch(arrayOf("*/*"))
                    }
                },
                enabled = !isWorking,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Restaurar de arquivo (.bragabkp)")
            }

            TextButton(
                onClick = onDismiss,
                enabled = !isWorking,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Text("Fechar")
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
