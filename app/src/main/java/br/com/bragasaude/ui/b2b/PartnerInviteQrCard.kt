package br.com.bragasaude.ui.b2b

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import br.com.bragasaude.domain.b2b.B2BOnboardingValidator
import br.com.bragasaude.domain.b2b.B2BThemeEngine
import br.com.bragasaude.domain.b2b.PartnerReceptionSignPdfGenerator
import br.com.bragasaude.util.QrCodeGenerator
import kotlinx.coroutines.launch
import java.io.File

/**
 * B2B-16B: Cartao reutilizavel de QR Code nativo (`/convite/<CODE>`) e geracao da Placa PDF
 * da recepcao, compartilhado entre o onboarding e o painel de gestao do parceiro.
 */
@Composable
fun PartnerInviteQrCard(
    programName: String,
    companyName: String,
    inviteCode: String,
    primaryColorHex: String?,
    accentColor: Color,
    onAccentColor: Color = Color.White,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val deepLink = remember(inviteCode) { B2BOnboardingValidator.buildInviteDeepLink(inviteCode) }
        ?: return
    val qrBitmap = remember(deepLink) {
        runCatching { QrCodeGenerator.generateQrCode(deepLink, size = 420) }.getOrNull()
    }
    var isGeneratingSign by remember { mutableStateOf(false) }
    var generatedSignPdf by remember(inviteCode) { mutableStateOf<File?>(null) }
    var showPdfActionsMenu by remember { mutableStateOf(false) }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        ),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.30f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.QrCode2,
                    contentDescription = null,
                    tint = accentColor
                )
                Text(
                    text = "QR Code e Placa da Recepção",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = accentColor
                )
            }

            if (qrBitmap != null) {
                Box(
                    modifier = Modifier
                        .size(180.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.White)
                        .padding(10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        bitmap = qrBitmap.asImageBitmap(),
                        contentDescription = "QR Code de convite $inviteCode",
                        modifier = Modifier.fillMaxSize(),
                        filterQuality = FilterQuality.None
                    )
                }
            }

            Text(
                text = deepLink,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            val activePdf = generatedSignPdf?.takeIf { it.exists() }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        if (isGeneratingSign) return@Button
                        isGeneratingSign = true
                        scope.launch {
                            val result = runCatching {
                                PartnerReceptionSignPdfGenerator(context.applicationContext).generateSignPdf(
                                    programName = programName,
                                    companyName = companyName,
                                    inviteCode = inviteCode,
                                    primaryColorHex = primaryColorHex
                                )
                            }
                            isGeneratingSign = false
                            result.onSuccess { pdf ->
                                generatedSignPdf = pdf
                                Toast.makeText(
                                    context,
                                    "Placa PDF da recepção gerada com sucesso.",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }.onFailure {
                                Toast.makeText(
                                    context,
                                    "Não foi possível gerar a placa PDF agora.",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    },
                    enabled = !isGeneratingSign,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = accentColor,
                        contentColor = onAccentColor
                    )
                ) {
                    if (isGeneratingSign) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = onAccentColor
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Gerando placa PDF...")
                    } else {
                        Icon(
                            imageVector = Icons.Default.PictureAsPdf,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Gerar placa PDF", fontWeight = FontWeight.SemiBold)
                    }
                }

                // Ações secundárias só existem depois que o arquivo foi gerado.
                if (activePdf != null) {
                    Box {
                        IconButton(
                            onClick = { showPdfActionsMenu = true },
                            enabled = !isGeneratingSign
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "Mais ações da placa PDF",
                                tint = accentColor
                            )
                        }
                        DropdownMenu(
                            expanded = showPdfActionsMenu,
                            onDismissRequest = { showPdfActionsMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Abrir PDF") },
                                onClick = {
                                    showPdfActionsMenu = false
                                    if (activePdf.exists()) {
                                        openPartnerPdfFile(
                                            context,
                                            activePdf,
                                            "Placa da Recepção — $programName"
                                        )
                                    }
                                },
                                enabled = activePdf.exists(),
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.PictureAsPdf,
                                        contentDescription = null
                                    )
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Compartilhar") },
                                onClick = {
                                    showPdfActionsMenu = false
                                    if (activePdf.exists()) {
                                        sharePartnerPdfFile(
                                            context = context,
                                            file = activePdf,
                                            subject = "Placa PDF de Recepção — $programName",
                                            chooserTitle = "Compartilhar Placa PDF"
                                        )
                                    }
                                },
                                enabled = activePdf.exists(),
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Share,
                                        contentDescription = null
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

internal fun openPartnerPdfFile(context: Context, file: File, chooserTitle: String) {
    runCatching {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/pdf")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, chooserTitle))
    }.onFailure {
        Toast.makeText(
            context,
            "Instale ou selecione um leitor de PDF para revisar o documento.",
            Toast.LENGTH_LONG
        ).show()
    }
}

internal fun sharePartnerPdfFile(
    context: Context,
    file: File,
    subject: String,
    chooserTitle: String = B2BThemeEngine.PDF_SHARE_ACTION_LABEL
) {
    runCatching {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, subject)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, chooserTitle))
    }.onFailure {
        Toast.makeText(
            context,
            "Não foi possível abrir o seletor de compartilhamento agora.",
            Toast.LENGTH_LONG
        ).show()
    }
}
