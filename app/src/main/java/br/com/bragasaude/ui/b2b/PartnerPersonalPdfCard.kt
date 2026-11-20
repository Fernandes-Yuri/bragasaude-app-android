package br.com.bragasaude.ui.b2b

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import br.com.bragasaude.domain.b2b.B2BThemeEngine
import br.com.bragasaude.domain.b2b.ProgramExperienceConfigDto
import br.com.bragasaude.domain.b2b.SharedCategoryOption
import java.io.File

/**
 * PDF de pré-consulta na área pessoal do participante (sem marca extra, sem gestão).
 *
 * O resumo só inclui categorias simultaneamente habilitadas pelo programa
 * (`experienceConfig.enabledMetrics`) e autorizadas pelo participante
 * (`sharedCategories`). Arquivos e documentos clínicos nunca entram no PDF.
 */
@Composable
fun PartnerPersonalPdfCard(
    experienceConfig: ProgramExperienceConfigDto,
    sharedCategories: List<String>,
    isGenerating: Boolean,
    generatedPdf: File?,
    brandPrimary: Color,
    brandOnPrimary: Color,
    brandAccent: Color,
    onGenerate: (Int, Set<String>) -> Unit,
    modifier: Modifier = Modifier
) {
    val enabledOptions = remember(experienceConfig.enabledMetrics) {
        experienceConfig.enabledMetrics.mapNotNull { SharedCategoryOption.fromWire(it) }.distinct()
    }
    val sharedKeys = remember(sharedCategories) {
        sharedCategories.mapNotNull { SharedCategoryOption.fromWire(it)?.wireKey }.toSet()
    }
    val allowedOptions = remember(enabledOptions, sharedKeys) {
        enabledOptions.filter { it.wireKey in sharedKeys }
    }
    val hasAllowedCategories = allowedOptions.isNotEmpty()
    var selectedDays by rememberSaveable { mutableStateOf(30) }
    var selectedCategories by remember(allowedOptions) {
        mutableStateOf(allowedOptions.map { it.wireKey }.toSet())
    }
    val context = LocalContext.current

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = brandAccent)
                Text(
                    text = B2BThemeEngine.PDF_PRECONSULTATION_TITLE,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                text = B2BThemeEngine.PDF_NON_PEP_NOTICE,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Período do resumo",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(listOf(7, 14, 30)) { days ->
                    FilterChip(
                        selected = selectedDays == days,
                        onClick = { selectedDays = days },
                        label = { Text("$days dias") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = brandPrimary,
                            selectedLabelColor = brandOnPrimary
                        )
                    )
                }
            }
            Text(
                text = "O que incluir no PDF (arquivos pessoais nunca são incluídos)",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
            if (hasAllowedCategories) {
                allowedOptions.forEach { opt ->
                    val checked = selectedCategories.contains(opt.wireKey)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selectedCategories = if (checked) {
                                    selectedCategories - opt.wireKey
                                } else {
                                    selectedCategories + opt.wireKey
                                }
                            }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = checked,
                            onCheckedChange = { nowChecked ->
                                selectedCategories = if (nowChecked) {
                                    selectedCategories + opt.wireKey
                                } else {
                                    selectedCategories - opt.wireKey
                                }
                            },
                            colors = CheckboxDefaults.colors(checkedColor = brandPrimary)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(opt.titlePtBr, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            } else {
                Text(
                    text = "Nenhuma categoria de rotina está habilitada pelo programa e compartilhada por você ainda. Ajuste suas permissões em “Meu consentimento” para liberar o resumo.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Button(
                onClick = { onGenerate(selectedDays, selectedCategories) },
                enabled = hasAllowedCategories && !isGenerating,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = brandPrimary,
                    contentColor = brandOnPrimary
                )
            ) {
                Text(
                    text = if (isGenerating) "Gerando resumo em PDF..." else "Gerar PDF",
                    fontWeight = FontWeight.Bold
                )
            }

            if (isGenerating) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = brandPrimary
                )
            }

            generatedPdf?.takeIf { it.exists() }?.let { pdfFile ->
                HorizontalDivider()
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = brandAccent)
                    Text(
                        text = "PDF gerado: ${pdfFile.name}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            openPartnerPdfFile(
                                context = context,
                                file = pdfFile,
                                chooserTitle = B2BThemeEngine.PDF_PRECONSULTATION_TITLE
                            )
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Abrir PDF")
                    }
                    Button(
                        onClick = {
                            sharePartnerPdfFile(
                                context = context,
                                file = pdfFile,
                                subject = B2BThemeEngine.PDF_PRECONSULTATION_TITLE,
                                chooserTitle = B2BThemeEngine.PDF_SHARE_ACTION_LABEL
                            )
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = brandPrimary,
                            contentColor = brandOnPrimary
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("Compartilhar", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
