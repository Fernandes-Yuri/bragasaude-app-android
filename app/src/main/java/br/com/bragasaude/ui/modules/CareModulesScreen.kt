package br.com.bragasaude.ui.modules

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import br.com.bragasaude.R
import br.com.bragasaude.ui.theme.Success

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CareModulesScreen(
    viewModel: CareModulesViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val detected by viewModel.detectedConditions.collectAsState()
    val profile by viewModel.profile.collectAsState()
    val tasks by viewModel.tasks.collectAsState()

    val activeModules = remember(profile) {
        listOf(
            "Hipertensão" to (profile?.hasHypertension == true),
            "Diabetes" to (profile?.hasDiabetes == true),
            "Saúde Renal" to (profile?.hasRenalIssue == true)
        )
    }

    val activeModuleNames = activeModules.filter { it.second }.map { it.first }
    val relevantTasks = tasks.filter { activeModuleNames.contains(it.moduleName) }
    val completedCount = relevantTasks.count { it.isCompleted }
    val progressPercent = if (relevantTasks.isNotEmpty()) ((completedCount.toFloat() / relevantTasks.size) * 100).toInt() else 0

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text("Módulos de Autocuidado", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(
                context.getString(R.string.care_modules_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.Gray
            )
        }

        // DECISOES.md (D4): observações descrevem valor x faixa geral e SEMPRE
        // exibem o disclaimer — nunca nomeiam condição/doença.
        if (detected.isNotEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f))
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(context.getString(R.string.observations_section_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        detected.forEach { observation ->
                            Text(observation.conditionName, style = MaterialTheme.typography.bodyMedium)
                        }
                        Text(
                            context.getString(R.string.observations_referral),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            context.getString(R.string.disclaimer_not_medical),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                    }
                }
            }
        }

        // Progresso Geral das Tarefas do Paciente
        if (relevantTasks.isNotEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Adesão ao Plano de Cuidado", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text("$completedCount de ${relevantTasks.size} tarefas concluídas hoje", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                            }
                            Text("$progressPercent%", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
                        }
                        Spacer(Modifier.height(10.dp))
                        LinearProgressIndicator(
                            progress = { if (relevantTasks.isNotEmpty()) completedCount.toFloat() / relevantTasks.size else 0f },
                            modifier = Modifier.fillMaxWidth().height(8.dp),
                            color = if (progressPercent >= 100) Success else MaterialTheme.colorScheme.primary,
                            trackColor = Color.LightGray.copy(alpha = 0.3f)
                        )
                    }
                }
            }
        }

        // Seção de Tarefas Diárias do Tratamento
        if (relevantTasks.isNotEmpty()) {
            item {
                Text("Suas Metas e Tarefas de Hoje", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }

            items(relevantTasks) { task ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (task.isCompleted) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f) else MaterialTheme.colorScheme.surface
                    ),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(0.5.dp, Color.LightGray.copy(alpha = 0.3f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.toggleTask(task.id) }
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = task.isCompleted,
                            onCheckedChange = { viewModel.toggleTask(task.id) }
                        )
                        Spacer(Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                task.title,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = if (task.isCompleted) Color.Gray else MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                task.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Gray
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                        ) {
                            Text(
                                task.moduleName,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // Seção de Programas Ativos e Disponíveis
        // DECISOES.md (D4): estes temas são AUTO-DECLARADOS pelo usuário (o que ele
        // já trata/com seus médicos) — o app nunca afirma a condição.
        item {
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            Text("Programas de Acompanhamento", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                "Marque os temas sobre os quais você já conversa com seu médico.",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray
            )
        }

        items(activeModules) { (name, isActive) ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (isActive) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                ),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, if (isActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.3f) else Color.LightGray.copy(alpha = 0.2f))
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(
                            if (isActive) Icons.Default.CheckCircle else Icons.Default.HealthAndSafety,
                            contentDescription = null,
                            tint = if (isActive) MaterialTheme.colorScheme.primary else Color.Gray,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(
                                if (isActive) "Ativo • Gerando Metas" else "Marque se se aplica a você",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Gray
                            )
                        }
                    }

                    Switch(
                        checked = isActive,
                        onCheckedChange = { viewModel.toggleModule(name, it) }
                    )
                }
            }
        }

        item { Spacer(Modifier.height(40.dp)) }
    }
}
