package br.com.bragasaude.ui.exams.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.bragasaude.ui.theme.BragaBackground
import br.com.bragasaude.ui.theme.BragaCardBorder
import br.com.bragasaude.ui.theme.BragaCardSurface
import br.com.bragasaude.ui.theme.BragaEmerald
import br.com.bragasaude.ui.theme.BragaMint
import br.com.bragasaude.ui.theme.BragaMintBorder
import br.com.bragasaude.ui.theme.BragaTextPrimary
import br.com.bragasaude.ui.theme.BragaTextSecondary

/**
 * Modal Bottom Sheet de Entrada em 3 Vias do Ecossistema de Exames.
 * Conforme Fase 2 do Plano Mestre (07_PLANO_MESTRE_OCR_EXAMES_E_RELATORIO_MEDICO_PDF.md) e Decisão D49.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddExamBottomSheet(
    onDismiss: () -> Unit,
    onTakePhoto: () -> Unit,
    onAttachFile: () -> Unit,
    onManualEntry: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = BragaBackground,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        dragHandle = {
            BottomSheetDefaults.DragHandle(
                color = BragaEmerald.copy(alpha = 0.4f),
                width = 48.dp,
                height = 4.dp
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Cabeçalho da Folha
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Adicionar Novo Exame",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = BragaTextPrimary
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Escolha como deseja registrar seu laudo no prontuário:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = BragaTextSecondary
                    )
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Fechar",
                        tint = BragaTextSecondary
                    )
                }
            }

            Spacer(Modifier.height(4.dp))

            // 1. Fotografar Laudo (Multipage + Gatekeeper)
            ExamEntryOptionCard(
                icon = Icons.Default.PhotoCamera,
                title = "Fotografar Laudo",
                subtitle = "Fotografe de 1 a 5 páginas do seu laudo físico com trava de foco e leitura óptica automática.",
                tag = "Multipage & Nitidez",
                tagColor = BragaEmerald,
                onClick = {
                    onDismiss()
                    onTakePhoto()
                }
            )

            // 2. Anexar Arquivo (PDF ou Imagem da Galeria)
            ExamEntryOptionCard(
                icon = Icons.Default.UploadFile,
                title = "Anexar Arquivo",
                subtitle = "Selecione o arquivo PDF original fornecido pelo laboratório ou imagem salva na galeria.",
                tag = "PDF ou Imagem",
                tagColor = Color(0xFF0284C7), // Azul moderno
                onClick = {
                    onDismiss()
                    onAttachFile()
                }
            )

            // 3. Digitar Manualmente
            ExamEntryOptionCard(
                icon = Icons.Default.EditNote,
                title = "Digitar Manualmente",
                subtitle = "Insira diretamente os valores em formulários estruturados (Glicemia, Lipídios, Hemograma, etc.).",
                tag = "Direto no Prontuário",
                tagColor = Color(0xFF7C3AED), // Roxo suave
                onClick = {
                    onDismiss()
                    onManualEntry()
                }
            )
        }
    }
}

@Composable
private fun ExamEntryOptionCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    tag: String,
    tagColor: Color,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = BragaCardSurface),
        border = BorderStroke(1.dp, BragaMintBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = BragaMint,
                modifier = Modifier.size(52.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = BragaEmerald,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            Spacer(Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = BragaTextPrimary,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = tagColor.copy(alpha = 0.12f)
                    ) {
                        Text(
                            text = tag,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = tagColor,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(Modifier.height(4.dp))

                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = BragaTextSecondary,
                    lineHeight = 18.sp
                )
            }

            Spacer(Modifier.width(8.dp))

            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                contentDescription = null,
                tint = BragaTextSecondary.copy(alpha = 0.6f),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}
