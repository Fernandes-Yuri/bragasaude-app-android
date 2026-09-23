package br.com.bragasaude.ui.care

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import br.com.bragasaude.ui.theme.BragaCardBorder
import br.com.bragasaude.ui.theme.BragaCardSurface
import br.com.bragasaude.ui.theme.BragaTextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CarePatientSelector(state: CareOsUiState, onSelect: (String) -> Unit) {
    if (!state.isAuthenticated || state.patients.isEmpty()) return
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = state.selectedPatient.name,
            onValueChange = {},
            readOnly = true,
            label = { Text("Pessoa acompanhada") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            state.patients.forEach { patient ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(patient.name, fontWeight = FontWeight.SemiBold)
                            if (patient.role != "PATIENT") Text(patient.role.replace('_', ' '), color = BragaTextSecondary)
                        }
                    },
                    onClick = { onSelect(patient.id); expanded = false }
                )
            }
        }
    }
}

@Composable
fun CareAuthenticationRequired(modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = BragaCardSurface),
        border = BorderStroke(1.dp, BragaCardBorder)
    ) {
        Column(Modifier.padding(20.dp)) {
            Icon(Icons.Filled.Lock, contentDescription = null)
            Text("Entre na sua conta", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
            Text(
                "O Care OS sincroniza informações protegidas entre paciente e cuidadores e não está disponível no modo convidado.",
                color = BragaTextSecondary,
                modifier = Modifier.padding(top = 6.dp).heightIn(min = 48.dp)
            )
        }
    }
}
