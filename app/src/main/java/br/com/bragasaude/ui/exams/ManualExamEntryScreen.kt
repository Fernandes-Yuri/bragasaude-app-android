package br.com.bragasaude.ui.exams

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.bragasaude.data.remote.model.RemoteExamItem
import br.com.bragasaude.data.util.HealthFormatter
import br.com.bragasaude.ui.components.EmeraldHeaderBanner
import br.com.bragasaude.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

/**
 * Definições dos Grupos de Exames Manuais Estruturados.
 * Conforme Seção 4 do Caderno de Contratos (08_CADERNO_DE_CONTRATOS_EXAMES_E_DOSSIE.md).
 */
data class ManualExamFieldDef(
    val key: String,
    val label: String,
    val defaultUnit: String,
    val placeholder: String,
    val minRealisticValue: Double,
    val maxRealisticValue: Double
)

data class ManualExamGroupDef(
    val groupId: String,
    val groupName: String,
    val icon: ImageVector,
    val fields: List<ManualExamFieldDef>
)

val MANUAL_EXAM_CATALOG = listOf(
    ManualExamGroupDef(
        groupId = "glycemia",
        groupName = "Glicemia & Metabolismo",
        icon = Icons.Default.Bloodtype,
        fields = listOf(
            ManualExamFieldDef("glucose", "Glicose de Jejum", "mg/dL", "ex: 92", 20.0, 600.0),
            ManualExamFieldDef("hba1c", "Hemoglobina Glicada (HbA1c)", "%", "ex: 5.4", 3.0, 20.0)
        )
    ),
    ManualExamGroupDef(
        groupId = "lipids",
        groupName = "Perfil Lipídico",
        icon = Icons.Default.Favorite,
        fields = listOf(
            ManualExamFieldDef("total_cholesterol", "Colesterol Total", "mg/dL", "ex: 180", 50.0, 500.0),
            ManualExamFieldDef("hdl", "Colesterol HDL", "mg/dL", "ex: 55", 10.0, 150.0),
            ManualExamFieldDef("ldl", "Colesterol LDL", "mg/dL", "ex: 105", 20.0, 400.0),
            ManualExamFieldDef("triglycerides", "Triglicerídeos", "mg/dL", "ex: 120", 20.0, 1000.0)
        )
    ),
    ManualExamGroupDef(
        groupId = "renal_hepatic",
        groupName = "Função Renal & Hepática",
        icon = Icons.Default.Healing,
        fields = listOf(
            ManualExamFieldDef("creatinine", "Creatinina Sérica", "mg/dL", "ex: 0.9", 0.2, 15.0),
            ManualExamFieldDef("urea", "Ureia", "mg/dL", "ex: 28", 5.0, 250.0),
            ManualExamFieldDef("ast_tgo", "TGO / AST", "U/L", "ex: 22", 2.0, 1000.0),
            ManualExamFieldDef("alt_tgp", "TGP / ALT", "U/L", "ex: 24", 2.0, 1000.0)
        )
    ),
    ManualExamGroupDef(
        groupId = "hemogram",
        groupName = "Hemograma Completo",
        icon = Icons.Default.MonitorHeart,
        fields = listOf(
            ManualExamFieldDef("hemoglobin", "Hemoglobina", "g/dL", "ex: 14.5", 4.0, 22.0),
            ManualExamFieldDef("hematocrit", "Hematócrito", "%", "ex: 42.0", 15.0, 65.0),
            ManualExamFieldDef("leukocytes", "Leucócitos Totais", "/mm³", "ex: 6500", 500.0, 50000.0),
            ManualExamFieldDef("platelets", "Plaquetas", "/mm³", "ex: 240000", 10000.0, 1000000.0)
        )
    ),
    ManualExamGroupDef(
        groupId = "hormones_vitamins",
        groupName = "Hormônios & Vitaminas",
        icon = Icons.Default.Science,
        fields = listOf(
            ManualExamFieldDef("tsh", "TSH Ultra Sensível", "µUI/mL", "ex: 2.1", 0.01, 100.0),
            ManualExamFieldDef("t4_free", "T4 Livre", "ng/dL", "ex: 1.2", 0.1, 10.0),
            ManualExamFieldDef("vitamin_d", "Vitamina D (25-OH)", "ng/mL", "ex: 32", 3.0, 150.0),
            ManualExamFieldDef("vitamin_b12", "Vitamina B12", "pg/mL", "ex: 450", 50.0, 2000.0)
        )
    )
)

/**
 * Tela de Entrada Manual Estruturada de Exames.
 * Permite inserção organizada por categorias normativas, com validação instantânea de formato
 * e gravação direta no prontuário como 'confirmed'.
 */
@Composable
fun ManualExamEntryScreen(
    onBack: () -> Unit,
    onSave: (title: String, category: String, examDate: String, items: List<RemoteExamItem>) -> Unit
) {
    var examTitle by remember { mutableStateOf("Exame Laboratorial") }
    val defaultDate = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()) }
    var examDate by remember { mutableStateOf(defaultDate) }

    // Mapa de valores inseridos: chave do campo -> valor em texto
    val valuesMap = remember { mutableStateMapOf<String, String>() }

    // Estado de expansão dos accordions (o primeiro começa expandido)
    val expandedGroups = remember { mutableStateMapOf("glycemia" to true) }

    // Conta quantos itens válidos foram preenchidos
    val validEnteredItems = remember(valuesMap) {
        val result = mutableListOf<RemoteExamItem>()
        val examId = UUID.randomUUID().toString()

        MANUAL_EXAM_CATALOG.forEach { group ->
            group.fields.forEach { field ->
                val textVal = valuesMap[field.key]?.trim() ?: ""
                val numVal = HealthFormatter.parseDouble(textVal)
                if (numVal != null && numVal > 0) {
                    result.add(
                        RemoteExamItem(
                            id = UUID.randomUUID().toString(),
                            examId = examId,
                            userId = "", // Será associado no ViewModel
                            itemKey = field.key,
                            itemName = field.label,
                            valueNumeric = numVal,
                            valueText = textVal.replace(".", ","),
                            unit = field.defaultUnit,
                            status = "confirmed"
                        )
                    )
                }
            }
        }
        result
    }

    val canSave = examTitle.isNotBlank() && validEnteredItems.isNotEmpty()

    Scaffold(
        containerColor = BragaBackground,
        topBar = {
            EmeraldHeaderBanner(
                title = "Digitação Manual",
                subtitle = "Preencha seus exames por categoria",
                onBack = onBack
            )
        },
        bottomBar = {
            Surface(
                color = BragaCardSurface,
                border = BorderStroke(1.dp, BragaMintBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            if (canSave) {
                                onSave(examTitle, "Laboratorial", examDate, validEnteredItems)
                            }
                        },
                        enabled = canSave,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = BragaEmerald,
                            contentColor = Color.White,
                            disabledContainerColor = BragaEmerald.copy(alpha = 0.35f),
                            disabledContentColor = Color.White.copy(alpha = 0.6f)
                        )
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = if (validEnteredItems.isEmpty()) "Preencha ao menos 1 parâmetro"
                            else "Gravar no Meu Prontuário (${validEnteredItems.size} ${if (validEnteredItems.size == 1) "exame" else "exames"})",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }

                    if (!canSave) {
                        Text(
                            text = "Abra uma das categorias abaixo e digite o valor de ao menos um exame.",
                            style = MaterialTheme.typography.labelSmall,
                            color = BragaTextSecondary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Metadados do Exame (Título e Data)
            item {
                Spacer(Modifier.height(4.dp))
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = BragaCardSurface),
                    border = BorderStroke(1.dp, BragaMintBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Identificação do Exame",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = BragaTextPrimary
                        )

                        OutlinedTextField(
                            value = examTitle,
                            onValueChange = { examTitle = it },
                            label = { Text("Título do Exame") },
                            placeholder = { Text("Ex: Exame de Sangue Rotina") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp)
                        )

                        OutlinedTextField(
                            value = examDate,
                            onValueChange = { examDate = it },
                            label = { Text("Data da Coleta (AAAA-MM-DD)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                }
            }

            item {
                Text(
                    text = "Categorias de Exames Padronizados",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = BragaTextPrimary
                )
            }

            // Accordions por Grupo Padronizado
            items(MANUAL_EXAM_CATALOG) { group ->
                val isExpanded = expandedGroups[group.groupId] == true
                val groupEnteredCount = group.fields.count { field ->
                    val num = HealthFormatter.parseDouble(valuesMap[field.key])
                    num != null && num > 0
                }

                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = BragaCardSurface),
                    border = BorderStroke(1.dp, if (isExpanded) BragaEmerald.copy(alpha = 0.5f) else BragaMintBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        // Linha do Cabeçalho do Accordion (clicável)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    expandedGroups[group.groupId] = !isExpanded
                                }
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    shape = CircleShape,
                                    color = if (groupEnteredCount > 0) BragaEmerald else BragaMint,
                                    modifier = Modifier.size(38.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = group.icon,
                                            contentDescription = null,
                                            tint = if (groupEnteredCount > 0) Color.White else BragaEmerald,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = group.groupName,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = BragaTextPrimary
                                    )
                                    Text(
                                        text = if (groupEnteredCount > 0) "$groupEnteredCount preenchido(s)"
                                        else "${group.fields.size} parâmetros disponíveis",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (groupEnteredCount > 0) BragaEmerald else BragaTextSecondary
                                    )
                                }
                            }

                            val rotation by animateFloatAsState(targetValue = if (isExpanded) 180f else 0f, label = "arrow")
                            Icon(
                                imageVector = Icons.Default.ExpandMore,
                                contentDescription = null,
                                tint = BragaTextSecondary,
                                modifier = Modifier.rotate(rotation)
                            )
                        }

                        // Conteúdo expansível com os campos
                        AnimatedVisibility(visible = isExpanded) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(BragaBackground.copy(alpha = 0.5f))
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                group.fields.forEach { field ->
                                    val currentValue = valuesMap[field.key] ?: ""
                                    val parsedVal = HealthFormatter.parseDouble(currentValue)
                                    val isInvalid = currentValue.isNotBlank() && (parsedVal == null || parsedVal <= 0)

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1.4f)) {
                                            Text(
                                                text = field.label,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                color = BragaTextPrimary
                                            )
                                            Text(
                                                text = "Unidade: ${field.defaultUnit}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = BragaTextSecondary
                                            )
                                        }

                                        OutlinedTextField(
                                            value = currentValue,
                                            onValueChange = { valuesMap[field.key] = it },
                                            placeholder = { Text(field.placeholder, fontSize = 13.sp) },
                                            modifier = Modifier.weight(1f),
                                            singleLine = true,
                                            isError = isInvalid,
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                            shape = RoundedCornerShape(12.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item {
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}
