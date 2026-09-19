package br.com.bragasaude.ui.exams

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.bragasaude.data.remote.model.RemoteExam
import br.com.bragasaude.data.remote.model.RemoteExamItem
import br.com.bragasaude.data.util.HealthFormatter
import br.com.bragasaude.ui.components.EmeraldHeaderBanner
import br.com.bragasaude.ui.theme.*
import java.util.UUID

/**
 * Item editável para o fluxo de conferência humana.
 * Conforme Seção 3 do Caderno de Contratos (08_CADERNO_DE_CONTRATOS_EXAMES_E_DOSSIE.md).
 */
data class EditableExamItemState(
    val tempId: String = UUID.randomUUID().toString(),
    val itemKey: String,
    val itemName: String,
    val valueInput: String,
    val unit: String,
    val isNumericValid: Boolean,
    val isDeleted: Boolean = false,
    val isCustomAdded: Boolean = false
)

/**
 * Tela de Conferência Humana Obrigatória.
 * Substitui o antigo ValidationDialog por uma experiência completa, espaçosa e acessível para adultos e idosos.
 *
 * NENHUM laudo é salvo no prontuário definitivo sem a conferência e aprovação explícita do paciente.
 */
@Composable
fun ExamReviewScreen(
    exam: RemoteExam,
    initialItems: List<RemoteExamItem>,
    onBack: () -> Unit,
    onConfirm: (RemoteExam, List<RemoteExamItem>) -> Unit
) {
    var examTitle by remember { mutableStateOf(exam.title) }
    var examCategory by remember { mutableStateOf(exam.category ?: "Laboratorial") }
    var examDate by remember { mutableStateOf(exam.examDate) }

    // Lista de itens em edição
    val itemsState = remember {
        mutableStateListOf<EditableExamItemState>().apply {
            addAll(
                initialItems.map { remoteItem ->
                    val valStr = remoteItem.valueText ?: remoteItem.valueNumeric?.toString() ?: ""
                    val num = HealthFormatter.parseDouble(valStr)
                    EditableExamItemState(
                        tempId = remoteItem.id ?: UUID.randomUUID().toString(),
                        itemKey = remoteItem.itemKey,
                        itemName = remoteItem.itemName,
                        valueInput = valStr,
                        unit = remoteItem.unit ?: "mg/dL",
                        isNumericValid = num != null && num > 0
                    )
                }
            )
        }
    }

    var showAddItemDialog by remember { mutableStateOf(false) }

    // Regras de validação do contrato
    val activeItems = itemsState.filter { !it.isDeleted }
    val isConfirmEnabled = examTitle.isNotBlank() &&
            activeItems.isNotEmpty() &&
            activeItems.all { it.isNumericValid }

    Scaffold(
        containerColor = BragaBackground,
        topBar = {
            EmeraldHeaderBanner(
                title = "Conferência Obrigatória",
                subtitle = "Verifique os valores antes de salvar no prontuário",
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
                            if (isConfirmEnabled) {
                                val updatedExam = exam.copy(
                                    title = examTitle,
                                    category = examCategory,
                                    examDate = examDate,
                                    status = "confirmed"
                                )
                                val confirmedItems = activeItems.map { editable ->
                                    val parsedVal = HealthFormatter.parseDouble(editable.valueInput) ?: 0.0
                                    RemoteExamItem(
                                        id = editable.tempId,
                                        examId = exam.id ?: UUID.randomUUID().toString(),
                                        userId = exam.userId,
                                        itemKey = editable.itemKey,
                                        itemName = editable.itemName,
                                        valueNumeric = parsedVal,
                                        valueText = editable.valueInput.replace(".", ","),
                                        unit = editable.unit,
                                        status = "confirmed"
                                    )
                                }
                                onConfirm(updatedExam, confirmedItems)
                            }
                        },
                        enabled = isConfirmEnabled,
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
                        Icon(Icons.Default.Check, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Confirmar e Gravar no Meu Prontuário",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }

                    if (!isConfirmEnabled) {
                        Text(
                            text = if (activeItems.isEmpty()) "Adicione ao menos 1 parâmetro clínico para salvar."
                            else "Preencha valores numéricos maiores que zero em todos os exames listados.",
                            style = MaterialTheme.typography.labelSmall,
                            color = BragaEmergencyOrange,
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
            // Card Institucional Informativo
            item {
                Spacer(Modifier.height(4.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = BragaMint),
                    border = BorderStroke(1.dp, BragaMintBorder)
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = BragaEmerald,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.VerifiedUser,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Conferência Obrigatória do Usuário",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = BragaEmerald
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = "Nossa inteligência artificial transcreveu os dados. Confira cada número com o laudo em mãos para garantir fidelidade clínica absoluta.",
                                style = MaterialTheme.typography.bodySmall,
                                color = BragaTextPrimary,
                                lineHeight = 18.sp
                            )
                        }
                    }
                }
            }

            // Metadados do Exame (Título e Data)
            item {
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
                            text = "Dados do Documento",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = BragaTextPrimary
                        )

                        OutlinedTextField(
                            value = examTitle,
                            onValueChange = { examTitle = it },
                            label = { Text("Título do Exame") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedTextField(
                                value = examDate,
                                onValueChange = { examDate = it },
                                label = { Text("Data da Coleta (AAAA-MM-DD)") },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp)
                            )

                            OutlinedTextField(
                                value = examCategory,
                                onValueChange = { examCategory = it },
                                label = { Text("Categoria") },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp)
                            )
                        }
                    }
                }
            }

            // Cabeçalho da Lista de Parâmetros
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Parâmetros Clínicos Identificados",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = BragaTextPrimary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${activeItems.size} parâmetros ativos",
                            style = MaterialTheme.typography.bodySmall,
                            color = BragaTextSecondary
                        )
                    }

                    FilledTonalButton(
                        onClick = { showAddItemDialog = true },
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = BragaMint,
                            contentColor = BragaEmerald
                        )
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("+ Adicionar", fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Estado Vazio caso nenhum parâmetro tenha sido extraído
            if (activeItems.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = BragaCardSurface),
                        border = BorderStroke(1.dp, BragaMintBorder)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Default.SearchOff,
                                contentDescription = null,
                                tint = BragaTextSecondary,
                                modifier = Modifier.size(40.dp)
                            )
                            Text(
                                text = "Nenhum parâmetro extraído automaticamente",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = BragaTextPrimary
                            )
                            Text(
                                text = "Clique em '+ Adicionar' acima para incluir manualmente os parâmetros do seu laudo.",
                                style = MaterialTheme.typography.bodySmall,
                                color = BragaTextSecondary,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }

            // Lista de Cartões de Parâmetros
            itemsIndexed(itemsState) { index, item ->
                if (!item.isDeleted) {
                    ExamItemReviewCard(
                        item = item,
                        onValueChange = { newValue ->
                            val num = HealthFormatter.parseDouble(newValue)
                            itemsState[index] = item.copy(
                                valueInput = newValue,
                                isNumericValid = num != null && num > 0
                            )
                        },
                        onUnitChange = { newUnit ->
                            itemsState[index] = item.copy(unit = newUnit)
                        },
                        onDelete = {
                            itemsState[index] = item.copy(isDeleted = true)
                        }
                    )
                }
            }

            item {
                Spacer(Modifier.height(20.dp))
            }
        }
    }

    // Modal para Adicionar Novo Parâmetro não identificado
    if (showAddItemDialog) {
        AddNewParamDialog(
            onDismiss = { showAddItemDialog = false },
            onAdd = { name, value, unit ->
                val num = HealthFormatter.parseDouble(value)
                val key = name.lowercase().replace(" ", "_")
                itemsState.add(
                    EditableExamItemState(
                        itemKey = key,
                        itemName = name,
                        valueInput = value,
                        unit = unit,
                        isNumericValid = num != null && num > 0,
                        isCustomAdded = true
                    )
                )
                showAddItemDialog = false
            }
        )
    }
}

@Composable
private fun ExamItemReviewCard(
    item: EditableExamItemState,
    onValueChange: (String) -> Unit,
    onUnitChange: (String) -> Unit,
    onDelete: () -> Unit
) {
    val borderColor = if (!item.isNumericValid) BragaEmergencyOrange else BragaMintBorder

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = BragaCardSurface),
        border = BorderStroke(1.dp, borderColor),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Linha superior: Nome do Exame + Botão Lixeira
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = BragaMint,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.Science,
                                contentDescription = null,
                                tint = BragaEmerald,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = item.itemName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = BragaTextPrimary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (item.isCustomAdded) {
                            Text(
                                text = "Adicionado manualmente",
                                style = MaterialTheme.typography.labelSmall,
                                color = BragaEmerald,
                                fontSize = 12.sp
                            )
                        }
                    }
                }

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Excluir item",
                        tint = BragaEmergencyOrange
                    )
                }
            }

            // Linha de Edição: Valor com Teclado Decimal + Unidade
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = item.valueInput,
                    onValueChange = onValueChange,
                    label = { Text("Valor Medido") },
                    placeholder = { Text("ex: 95") },
                    modifier = Modifier.weight(1.3f),
                    singleLine = true,
                    isError = !item.isNumericValid,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    shape = RoundedCornerShape(12.dp)
                )

                OutlinedTextField(
                    value = item.unit,
                    onValueChange = onUnitChange,
                    label = { Text("Unidade") },
                    modifier = Modifier.weight(0.9f),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )
            }

            if (!item.isNumericValid) {
                Text(
                    text = "Insira um número válido maior que zero (ex: 98 ou 12,5)",
                    style = MaterialTheme.typography.labelSmall,
                    color = BragaEmergencyOrange
                )
            }
        }
    }
}

@Composable
private fun AddNewParamDialog(
    onDismiss: () -> Unit,
    onAdd: (name: String, value: String, unit: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var value by remember { mutableStateOf("") }
    var unit by remember { mutableStateOf("mg/dL") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Adicionar Parâmetro Clínico", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Informe o exame que não foi detectado automaticamente:",
                    style = MaterialTheme.typography.bodySmall,
                    color = BragaTextSecondary
                )

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nome do Exame (ex: Vitamina B12)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = value,
                        onValueChange = { value = it },
                        label = { Text("Valor") },
                        modifier = Modifier.weight(1.2f),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    )

                    OutlinedTextField(
                        value = unit,
                        onValueChange = { unit = it },
                        label = { Text("Unidade") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onAdd(name.trim(), value.trim(), unit.trim()) },
                enabled = name.isNotBlank() && (HealthFormatter.parseDouble(value) ?: 0.0) > 0,
                colors = ButtonDefaults.buttonColors(containerColor = BragaEmerald)
            ) {
                Text("Adicionar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}
