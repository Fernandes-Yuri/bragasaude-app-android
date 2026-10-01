package br.com.bragasaude.ui.exams.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import br.com.bragasaude.ui.components.BragaFormSheet

@Composable
fun CloudConsentDialog(onConfirm: (Boolean) -> Unit, onDismiss: () -> Unit) {
    var validExam by rememberSaveable { mutableStateOf(false) }
    var acceptedPurpose by rememberSaveable { mutableStateOf(false) }
    BragaFormSheet(
        onDismissRequest = onDismiss,
        title = { Text("Termo da Nuvem de Exames") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Antes de cada envio", style = MaterialTheme.typography.titleSmall)
                Text("Você está enviando um exame e seus dados de saúde para armazenamento na Nuvem de Exames da Braga Saúde. Os arquivos e os valores que você conferir ficam associados à sua conta para organização, exportação e compartilhamento por sua iniciativa com seu médico.")
                Text("Não fazemos diagnóstico, interpretação de exames, recomendações de tratamento ou prescrição. A organização e a transcrição dos dados não validam o conteúdo do exame e não substituem a avaliação de um profissional.")
                Text("Envie apenas exames válidos que sejam seus ou que você tenha autorização para armazenar. Não envie arquivos aleatórios ou documentos sem relação com exames. Confira os dados transcritos com o documento original antes de salvar.")
                Text("Você pode cancelar agora, sem enviar. Para remover um exame armazenado, use a opção de excluir na lista de exames. Esta autorização vale apenas para este envio.")
                ConsentCheck("Declaro que estou enviando um exame válido e que tenho autorização para armazená-lo.", validExam) { validExam = it }
                ConsentCheck("Li e aceito este termo. Autorizo o armazenamento deste exame e de seus dados de saúde para a finalidade descrita, e entendo que o app não faz diagnóstico.", acceptedPurpose) { acceptedPurpose = it }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(true) }, enabled = validExam && acceptedPurpose,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                Text("Aceitar e continuar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text("Cancelar envio")
            }
        }
    )
}

@Composable
private fun ConsentCheck(text: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().toggleable(value = checked, role = Role.Checkbox, onValueChange = onChange)
        .padding(vertical = 8.dp), verticalAlignment = Alignment.Top) {
        Checkbox(checked = checked, onCheckedChange = null)
        Spacer(Modifier.width(12.dp))
        Text(text, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
    }
}
