package br.com.bragasaude.ui.exams.components


import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
    var selectedOption by remember { mutableStateOf<Boolean?>(true) } // true = Nuvem, false = Local

    BragaContentSheet(
        onDismissRequest = onDismiss
    ) {

        Column(
            modifier = Modifier
                .padding(24.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Cabeçalho
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Armazenamento & Privacidade",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 22.sp
                    ),
                    modifier = Modifier.weight(1f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Você tem total soberania sobre onde deseja guardar seus laudos médicos. Escolha como prefere armazenar este exame:",
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 16.sp,
                    lineHeight = 24.sp
                )
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Opção 1: Nuvem Segura Braga Saúde
            StorageOptionCard(
                title = "Nuvem Segura Braga Saúde",
                subtitle = "Sincronização entre seus aparelhos e backup criptografado nos servidores dedicados.",
                badge = "Recomendado",
                icon = Icons.Default.CloudDone,
                isSelected = selectedOption == true,
                onClick = { selectedOption = true }
            )

            if (selectedOption == true) {
                Spacer(modifier = Modifier.height(10.dp))
                // Termo Legal LGPD com Isenção Art. 43, III
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                        .padding(12.dp)
                ) {
                    Text(
                        text = "Termo LGPD (Art. 43, III): A Braga Saúde emprega criptografia de ponta a ponta e rígidas salvaguardas técnicas, ficando isenta de responsabilidade civil por acessos não autorizados provocados por ataques cibernéticos fortuitos ou ações ilícitas de terceiros externos.",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp,
                            lineHeight = 16.sp
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Opção 2: Apenas Neste Aparelho
            StorageOptionCard(
                title = "Apenas Neste Aparelho (Local)",
                subtitle = "O arquivo original permanece exclusivamente na memória deste celular e nunca é transmitido para os nossos servidores.",
                badge = "Soberania Total",
                icon = Icons.Default.PhoneAndroid,
                isSelected = selectedOption == false,
                onClick = { selectedOption = false }
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Ações
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant)
                ) {
                    Text("Cancelar", fontWeight = FontWeight.SemiBold)
                }

                Button(
                    onClick = {
                        selectedOption?.let { onConfirm(it) }
                    },
                    enabled = selectedOption != null,
                    modifier = Modifier
                        .weight(1.3f)
                        .height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = Color.White
                    )
                ) {
                    Text("Confirmar", fontWeight = FontWeight.Bold)
                }
            }
        }

    }
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
    val borderColor = if (isSelected) MaterialTheme.colorScheme.primary else Color(0xFFE2E8F0)
    val bgColor = if (isSelected) Color(0xFFE0F2F1).copy(alpha = 0.4f) else Color.White

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(bgColor)
            .border(if (isSelected) 2.dp else 1.dp, borderColor, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            RadioButton(
                selected = isSelected,
                onClick = onClick,
                colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 14.5.sp
                        ),
                        modifier = Modifier.weight(1f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
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
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )
                )
            }
        }
    }
}
