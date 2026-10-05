package br.com.bragasaude.ui.exams

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import br.com.bragasaude.data.remote.model.RemoteExam
import br.com.bragasaude.data.remote.model.RemoteExamItem
import br.com.bragasaude.domain.ExamCloudState
import br.com.bragasaude.ui.components.BragaAlertDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ExamDetailScreen(exam: RemoteExam, items: List<RemoteExamItem>, busy: Boolean,
    onBack: () -> Unit, onOpen: () -> Unit, onEdit: () -> Unit,
    onPdf: () -> Unit, onShare: () -> Unit, onCloud: () -> Unit, onPause: () -> Unit) {
    Scaffold(topBar = {
        TopAppBar(title = { Text("Detalhe do exame") }, navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar") }
        })
    }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(exam.title, style = MaterialTheme.typography.headlineSmall)
            Text("${exam.examDate} · ${exam.category.orEmpty()}")
            Text(ExamCloudState.label(exam.cloudState), style = MaterialTheme.typography.titleSmall)
            Text(if (exam.localFilePath != null) "Original salvo neste aparelho" else if (exam.fileUrl != null) "Original disponível na nuvem; a abertura requer conexão" else "Exame digitado, sem arquivo anexado")
            Text("Resultados conferidos pelo usuário", style = MaterialTheme.typography.titleMedium)
            if (items.isEmpty()) Text("Nenhum valor transcrito. Consulte o documento original quando disponível.")
            items.forEach { item ->
                Text("${item.itemName}: ${item.valueText ?: item.valueNumeric?.toString() ?: "Não informado"} ${item.unit.orEmpty()}")
            }
            if (exam.localFilePath != null || exam.fileUrl != null) OutlinedButton(onClick = onOpen, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Abrir original") }
            OutlinedButton(onClick = onEdit, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Conferir ou editar resultados") }
            Button(onClick = onPdf, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Gerar PDF deste exame") }
            OutlinedButton(onClick = onShare, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Compartilhar PDF") }
            if (exam.cloudState != ExamCloudState.SYNCED) OutlinedButton(onClick = onCloud,
                enabled = !busy && exam.cloudState != ExamCloudState.PENDING, modifier = Modifier.fillMaxWidth()) {
                Text(if (exam.cloudState == ExamCloudState.ERROR) "Tentar envio à nuvem novamente" else "Salvar também na nuvem")
            }
            if (exam.cloudState in setOf(ExamCloudState.PENDING, ExamCloudState.ERROR, ExamCloudState.SYNCED)) {
                TextButton(onClick = onPause, enabled = !busy) { Text("Parar novos envios à nuvem") }
            }
            Text("Compartilhar PDF não ativa acompanhamento automático. Exames locais podem ser perdidos ao apagar os dados do app ou perder acesso ao aparelho.", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
internal fun ShareExamsDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    BragaAlertDialog(onDismissRequest = onDismiss,
        title = { Text("Compartilhar PDF dos exames") },
        text = { Text("O PDF contém informações dos exames escolhidos. O aplicativo de envio e o destinatário poderão guardar uma cópia. O envio não ativa sincronização com nossa nuvem nem acompanhamento do cuidador.") },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Gerar PDF e escolher destinatário") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } })
}
