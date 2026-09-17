package br.com.bragasaude.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import br.com.bragasaude.util.BiometricAuthHelper

@Composable
fun SensitiveScreenGuard(
    screenTitle: String,
    onBack: () -> Unit,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    var isAuthenticated by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    fun requestAuth() {
        if (activity != null) {
            BiometricAuthHelper.promptBiometric(
                activity = activity,
                title = "Acesso a $screenTitle",
                subtitle = "Dados confidenciais protegidos por biometria",
                onSuccess = { isAuthenticated = true },
                onError = { err -> errorMessage = err }
            )
        } else {
            isAuthenticated = true
        }
    }

    LaunchedEffect(Unit) {
        requestAuth()
    }

    if (isAuthenticated) {
        content()
    } else {
        Scaffold { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "Bloqueado",
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Acesso Bloqueado",
                    style = MaterialTheme.typography.headlineSmall
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = errorMessage ?: "Esta área contém informações médicas protegidas pela LGPD.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(onClick = { requestAuth() }) {
                    Text("Desbloquear com Biometria")
                }
                TextButton(onClick = onBack) {
                    Text("Voltar")
                }
            }
        }
    }
}
