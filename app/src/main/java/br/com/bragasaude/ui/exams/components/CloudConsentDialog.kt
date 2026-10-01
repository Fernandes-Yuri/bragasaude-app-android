package br.com.bragasaude.ui.exams.components

import br.com.bragasaude.ui.components.BragaFormSheet


import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Diálogo de Escolha de Armazenamento e Consentimento LGPD.
 *
 * Cumpre os requisitos formais de software da Seção 5 de:
 * 08_CADERNO_DE_CONTRATOS_EXAMES_E_DOSSIE.md
 *
 * Apresenta a bifurcação de armazenamento e o termo explícito
 * de isenção de responsabilidade por ataques cibernéticos fortuitos
 * de terceiros (Art. 43, inciso III da LGPD).
 */
@Composable
fun CloudConsentDialog(
    onConfirm: (acceptedCloudStorage: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedOption by rememberSaveable { mutableStateOf<Boolean?>(true) } // true = Nuvem, false = Local

    BragaFormSheet(
        onDismissRequest = onDismiss,
        title = { Text("Onde guardar este exame?") },
        confirmButton = {
            Button(
                onClick = { selectedOption?.let(onConfirm) },
                enabled = selectedOption != null,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
            ) { Text("Confirmar escolha", style = MaterialTheme.typography.bodyMedium) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text("Cancelar", style = MaterialTheme.typography.bodyMedium)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Escolha entre sincronizar seus laudos ou manter o arquivo apenas neste aparelho.",
                    style = MaterialTheme.typography.bodyMedium)
                StorageOptionCard(
                    title = "Nuvem Braga Saúde",
                    subtitle = "Sincronização entre seus aparelhos e backup criptografado nos servidores dedicados.",
                    badge = "Recomendado",
                    icon = Icons.Default.CloudDone,
                    isSelected = selectedOption == true,
                    onClick = { selectedOption = true }
                )
                StorageOptionCard(
                    title = "Apenas neste aparelho",
                    subtitle = "O arquivo original permanece exclusivamente na memória deste celular e nunca é transmitido para os nossos servidores.",
                    badge = "Armazenamento local",
                    icon = Icons.Default.PhoneAndroid,
                    isSelected = selectedOption == false,
                    onClick = { selectedOption = false }
                )
                if (selectedOption == true) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text(
                            text = "Termo LGPD (Art. 43, III): A Braga Saúde emprega criptografia de ponta a ponta e rígidas salvaguardas técnicas, ficando isenta de responsabilidade civil por acessos não autorizados provocados por ataques cibernéticos fortuitos ou ações ilícitas de terceiros externos.",
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    )
}

@Composable
private fun StorageOptionCard(
    title: String,
    subtitle: String,
    badge: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val borderColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    val bgColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(bgColor)
            .border(if (isSelected) 2.dp else 1.dp, borderColor, RoundedCornerShape(16.dp))
            .selectable(selected = isSelected, role = Role.RadioButton, onClick = onClick)
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            RadioButton(
                selected = isSelected,
                onClick = null,
                colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Column(modifier = Modifier.weight(1f)) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 18.sp
                        ),
                    )
                    Surface(
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = badge,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            ),
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 16.sp,
                        lineHeight = 24.sp
                    )
                )
            }
        }
    }
}
