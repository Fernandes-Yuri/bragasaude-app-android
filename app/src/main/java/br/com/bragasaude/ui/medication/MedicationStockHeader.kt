package br.com.bragasaude.ui.medication

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.bragasaude.data.local.MedicationEntity
import br.com.bragasaude.ui.theme.*
import java.math.BigDecimal

/** Preserva a dose cadastrada; o campo numérico é apenas uma alternativa quando ela está vazia. */
internal fun medicationDosageLabel(dosage: String?, dosageMg: Double?): String? {
    dosage?.trim()?.takeIf { it.isNotEmpty() }?.let { registered ->
        return Regex("(\\d+(?:[.,]\\d+)*)\\s*(mcg|µg|μg|mg|g|ml|UI|%)(?![\\p{L}])", RegexOption.IGNORE_CASE)
            .replace(registered) { match ->
                val number = match.groupValues[1]
                // Não interpreta como decimal um possível separador de milhar já cadastrado.
                val localized = if (',' in number || Regex("[1-9]\\d{0,2}(?:\\.\\d{3})+").matches(number)) number
                    else number.replace('.', ',')
                "$localized ${match.groupValues[2]}"
            }
    }
    return dosageMg?.takeIf { it.isFinite() && it > 0 }?.let {
        "${BigDecimal.valueOf(it).stripTrailingZeros().toPlainString().replace('.', ',')} mg"
    }
}

@Composable
internal fun MedicationStockHeader(medication: MedicationEntity, isCritical: Boolean) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Filled.Medication, contentDescription = null, tint = BragaEmeraldDark,
                modifier = Modifier.size(32.dp))
            Text(medication.name, fontWeight = FontWeight.Bold, fontSize = 18.sp,
                color = BragaTextPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f))
        }
        medicationDosageLabel(medication.dosage, medication.dosageMg)?.let { label ->
            Surface(shape = RoundedCornerShape(6.dp), color = BragaMintSurface,
                border = BorderStroke(1.dp, BragaMintBorder)) {
                Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                    color = BragaEmeraldDark, softWrap = false, maxLines = 1,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
            }
        }
        medication.activePrinciple?.takeIf { it.isNotBlank() }?.let {
            Text(it, fontSize = 14.sp, color = BragaTextSecondary)
        }
        medication.manufacturer?.takeIf { it.isNotBlank() }?.let {
            Text(it, fontSize = 13.sp, color = BragaTextSecondary)
        }
        Text("${medication.currentUnits} comprimidos restantes", fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp, color = if (isCritical) BragaEmergencyOrange else BragaEmeraldDark)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MedicationScheduleDetails(times: List<String>, mealContext: String?) {
    if (times.isEmpty() && mealContext.isNullOrBlank()) return
    FlowRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (times.isNotEmpty()) {
            Text("Horários:", fontSize = 12.sp, fontWeight = FontWeight.Medium,
                color = BragaTextSecondary, modifier = Modifier.padding(vertical = 4.dp))
        }
        times.forEach { time ->
            Surface(shape = RoundedCornerShape(6.dp), color = BragaMintSurface,
                border = BorderStroke(1.dp, BragaMintBorder)) {
                Text(time, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = BragaEmeraldDark,
                    softWrap = false, maxLines = 1,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp))
            }
        }
        mealContext?.takeIf { it.isNotBlank() }?.let { notes ->
            Surface(shape = RoundedCornerShape(6.dp), color = BragaCardSurface,
                border = BorderStroke(1.dp, BragaCardBorder)) {
                Text(medicationInstructionLabel(notes), fontSize = 12.sp, color = BragaTextSecondary,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp))
            }
        }
    }
}
