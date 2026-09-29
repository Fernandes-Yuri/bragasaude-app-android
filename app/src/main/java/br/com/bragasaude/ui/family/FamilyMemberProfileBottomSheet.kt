package br.com.bragasaude.ui.family

import br.com.bragasaude.ui.components.BragaAlertDialog
import br.com.bragasaude.ui.components.BragaBottomSheet

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VolunteerActivism
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.bragasaude.ui.theme.BragaCardBorder
import br.com.bragasaude.ui.theme.BragaCardSurface
import br.com.bragasaude.ui.theme.BragaEmerald
import br.com.bragasaude.ui.theme.BragaMint
import br.com.bragasaude.ui.theme.BragaTextPrimary
import br.com.bragasaude.ui.theme.BragaTextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * BottomSheet de perfil e detalhes do contato familiar.
 * 
 * Permite visualizar:
 * - Nome e grau de parentesco
 * - Papel no círculo de cuidado (cuidador ou pessoa acompanhada)
 * - Atalho para o Painel do Cuidador (quando aplicável)
 * - Aviso de retenção efêmera de 24 horas (D47)
 * - Gerenciamento de vínculos do círculo familiar
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FamilyMemberProfileBottomSheet(
    conversation: FamilyConversationUi,
    onDismissRequest: () -> Unit,
    onNavigateToConnect: () -> Unit,
    onOpenCaregiverDashboard: ((String) -> Unit)? = null,
    onRevokeBinding: (() -> Unit)? = null
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showRevokeConfirm by remember { mutableStateOf(false) }

    BragaBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Avatar
            Surface(
                shape = CircleShape,
                color = BragaMint,
                modifier = Modifier.size(72.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = conversation.avatarLetter,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = BragaEmerald
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // Nome
            Text(
                text = conversation.title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = BragaTextPrimary,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(4.dp))

            // Badge de Parentesco / Relação
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = BragaMint.copy(alpha = 0.5f)
            ) {
                Text(
                    text = conversation.relationLabel,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = BragaEmerald
                )
            }

            Spacer(Modifier.height(20.dp))

            // Card de Papel de Cuidado
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = BragaCardSurface),
                border = BorderStroke(1.dp, BragaCardBorder)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = BragaMint,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = if (conversation.isCaredByMe) Icons.Default.VolunteerActivism else Icons.Default.Shield,
                                    contentDescription = null,
                                    tint = BragaEmerald,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                text = if (conversation.isCaredByMe) "Você é cuidador(a)" else "Acompanhante familiar",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = BragaTextPrimary
                            )
                            Text(
                                text = conversation.subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = BragaTextSecondary
                            )
                        }
                    }

                    Spacer(Modifier.height(10.dp))

                    Text(
                        text = if (conversation.isCaredByMe) {
                            "Você tem permissão para acompanhar os sinais vitais, medicamentos, hidratação e metas de saúde deste familiar."
                        } else {
                            "Este familiar tem permissão para receber alertas de rotina e acompanhar seu bem-estar diário."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = BragaTextSecondary,
                        lineHeight = 18.sp
                    )

                    if (conversation.isCaredByMe && onOpenCaregiverDashboard != null) {
                        Spacer(Modifier.height(14.dp))
                        Button(
                            onClick = {
                                onDismissRequest()
                                onOpenCaregiverDashboard(conversation.patientId)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 44.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = BragaEmerald),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Icon(
                                Icons.Default.Dashboard,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Abrir Painel do Cuidador",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // Card de Retenção Efêmera 24h (D47)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = BragaCardSurface),
                border = BorderStroke(1.dp, BragaCardBorder)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Surface(
                        shape = CircleShape,
                        color = BragaMint,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.AccessTime,
                                contentDescription = null,
                                tint = BragaEmerald,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Privacidade e Retenção de 24h",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = BragaTextPrimary
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Todas as mensagens desta conversa expiram e são purgadas automaticamente após 24 horas, tanto no seu aparelho quanto nos servidores, conforme a LGPD.",
                            style = MaterialTheme.typography.bodySmall,
                            color = BragaTextSecondary,
                            lineHeight = 18.sp
                        )
                    }
                }
            }

            // Data de vínculo se disponível
            val earliestBinding = conversation.activeBindings.minByOrNull { it.createdAt }
            if (earliestBinding != null && earliestBinding.createdAt > 0L) {
                Spacer(Modifier.height(12.dp))
                val formattedDate = SimpleDateFormat("dd 'de' MMMM 'de' yyyy", Locale.forLanguageTag("pt-BR"))
                    .format(Date(earliestBinding.createdAt))
                Text(
                    text = "Vínculo ativo desde $formattedDate",
                    style = MaterialTheme.typography.labelSmall,
                    color = BragaTextSecondary,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(Modifier.height(20.dp))

            // Ações do rodapé
            OutlinedButton(
                onClick = {
                    onDismissRequest()
                    onNavigateToConnect()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 46.dp),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, BragaEmerald)
            ) {
                Icon(
                    Icons.Default.Groups,
                    contentDescription = null,
                    tint = BragaEmerald,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Gerenciar Círculo Familiar",
                    color = BragaEmerald,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp
                )
            }

            if (onRevokeBinding != null) {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { showRevokeConfirm = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 46.dp),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error)
                ) {
                    Icon(
                        Icons.Default.DeleteOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Excluir Vínculo Familiar",
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            TextButton(
                onClick = onDismissRequest,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Fechar",
                    color = BragaTextSecondary,
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp
                )
            }

            Spacer(Modifier.height(16.dp))
        }
    }

    if (showRevokeConfirm) {
        BragaAlertDialog(
            onDismissRequest = { showRevokeConfirm = false },
            title = {
                Text(
                    text = "Excluir vínculo familiar?",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "Esta ação desconectará ${conversation.title}. O acesso compartilhado de saúde e as mensagens do chat serão encerrados para ambas as partes.",
                    fontSize = 15.sp,
                    lineHeight = 20.sp
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showRevokeConfirm = false
                        onDismissRequest()
                        onRevokeBinding?.invoke()
                    }
                ) {
                    Text(
                        text = "Excluir vínculo",
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showRevokeConfirm = false }) {
                    Text("Cancelar", fontSize = 16.sp)
                }
            }
        )
    }
}
