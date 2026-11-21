package br.com.bragasaude.ui.b2b

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.bragasaude.data.remote.api.BragaApiClient
import br.com.bragasaude.domain.b2b.AppContextMode
import br.com.bragasaude.domain.b2b.B2BContextDescriptor
import br.com.bragasaude.domain.b2b.B2BOnboardingValidator
import br.com.bragasaude.domain.b2b.PartnerInvitePreviewDto
import br.com.bragasaude.domain.b2b.SharedCategoryOption
import br.com.bragasaude.ui.components.BragaBottomSheet
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PartnerConsentSheet(
    apiClient: BragaApiClient,
    initialInviteCode: String = "",
    initialProgramId: String? = null,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var isLoading by remember { mutableStateOf(true) }
    var isWorking by remember { mutableStateOf(false) }
    var inviteCode by rememberSaveable { mutableStateOf(initialInviteCode.trim().uppercase()) }
    var invitePreview by remember { mutableStateOf<PartnerInvitePreviewDto?>(null) }
    var activeEnrollment by remember { mutableStateOf<B2BContextDescriptor?>(null) }
    var enrollments by remember { mutableStateOf<List<B2BContextDescriptor>>(emptyList()) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(initialInviteCode, initialProgramId) {
        val contexts = apiClient.getB2BContexts()
        enrollments = contexts.filter {
            it.parsedMode == AppContextMode.PARTNER_PARTICIPANT && !it.programId.isNullOrBlank()
        }
        val participant = enrollments.firstOrNull { it.programId == initialProgramId }
            ?: enrollments.firstOrNull()
        if (participant != null) {
            activeEnrollment = participant
        }
        val cleanInitial = initialInviteCode.trim().uppercase()
        if (cleanInitial.length >= 4 && B2BOnboardingValidator.isValidInviteCode(cleanInitial)) {
            inviteCode = cleanInitial
            invitePreview = apiClient.lookupPartnerInvitePreview(cleanInitial)
        }
        isLoading = false
    }

    // Programa alvo do consentimento: a prévia validada manda; sem código
    // informado, vale o vínculo ativo. Digitar/colar no campo de código não
    // troca o programa resolvido (a troca só acontece após validar a prévia).
    val resolvedProgramId = invitePreview?.programId ?: activeEnrollment?.programId
    // Código digitado/colado ainda sem prévia validada: o texto não pode trocar
    // o alvo do salvamento nem zerar a seleção atual.
    val pendingInviteEdit = inviteCode.isNotBlank() && invitePreview == null

    // A seleção sobrevive a recomposições, mudanças de configuração e morte de
    // processo. Ela só é recarregada quando o programa resolvido realmente muda
    // (abaixo), nunca porque o usuário digitou/colou no campo de convite.
    var selectedCategoryKeys by rememberSaveable(
        stateSaver = listSaver<List<String>, String>(
            save = { it },
            restore = { it }
        )
    ) { mutableStateOf(emptyList<String>()) }
    val selectedCategories = selectedCategoryKeys.toSet()
    var confirmedDetails by rememberSaveable { mutableStateOf(false) }
    var familyShareEnabled by rememberSaveable { mutableStateOf(false) }
    var seededProgramId by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(resolvedProgramId, isLoading, enrollments) {
        if (isLoading) return@LaunchedEffect
        val programId = resolvedProgramId ?: return@LaunchedEffect
        if (seededProgramId == programId) return@LaunchedEffect
        // Programa ainda sem vínculo (adesão nova): preserva o que o usuário
        // já marcou em vez de zerar categorias e checkbox.
        val enrollment = enrollments.firstOrNull { it.programId == programId }
            ?: activeEnrollment?.takeIf { it.programId == programId }
            ?: return@LaunchedEffect
        selectedCategoryKeys = enrollment.sharedCategories
        familyShareEnabled = enrollment.familyShareEnabled
        seededProgramId = programId
    }

    val clinicalSelection = SharedCategoryOption.entries.filter {
        it.wireKey in selectedCategories && it in setOf(SharedCategoryOption.MEDICATIONS, SharedCategoryOption.AUTHORIZED_RECORDS)
    }

    val effectiveProgramName =
        invitePreview?.programName ?: activeEnrollment?.programName ?: "o programa parceiro"
    val effectiveCompanyName =
        invitePreview?.companyName ?: activeEnrollment?.companyName ?: "Parceiro Braga Saúde"
    val effectiveColorHex =
        invitePreview?.primaryColor ?: activeEnrollment?.primaryColor
            ?: B2BOnboardingValidator.DEFAULT_PRIMARY_COLOR

    val brandColor = remember(effectiveColorHex) {
        runCatching {
            Color(
                android.graphics.Color.parseColor(
                    B2BOnboardingValidator.normalizeHexColorOrDefault(effectiveColorHex)
                )
            )
        }.getOrDefault(Color(0xFF1A6B5C))
    }

    BragaBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "PROGRAMA PARCEIRO",
                    style = MaterialTheme.typography.labelSmall,
                    color = brandColor,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (activeEnrollment != null && invitePreview == null) {
                        "Seu compartilhamento ativo"
                    } else {
                        "Participar por código"
                    },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = B2BOnboardingValidator.buildConsentHeading(effectiveProgramName),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = brandColor)
                }
            } else {
                if (enrollments.size > 1 && inviteCode.isBlank()) {
                    Text("Escolha o programa para ajustar o compartilhamento")
                    // FlowRow evita que nomes longos de programa sejam cortados
                    // na borda do bottom sheet em telas estreitas.
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        enrollments.forEach { enrollment ->
                            val selectedProgram = activeEnrollment?.programId == enrollment.programId
                            FilterChip(
                                selected = selectedProgram,
                                enabled = !isWorking,
                                onClick = {
                                    if (activeEnrollment?.programId != enrollment.programId) {
                                        // Autorização de detalhes clínicos é específica
                                        // do programa: não transportar o check entre vínculos.
                                        confirmedDetails = false
                                    }
                                    activeEnrollment = enrollment
                                    errorMessage = null
                                },
                                leadingIcon = if (selectedProgram) {
                                    { Icon(Icons.Default.Check, contentDescription = null) }
                                } else {
                                    null
                                },
                                label = { Text(enrollment.programName ?: "Programa parceiro") }
                            )
                        }
                    }
                }
                // Entrada ou busca de código de convite
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = inviteCode,
                        onValueChange = {
                            if (it.length <= 32) {
                                inviteCode = it.uppercase()
                                invitePreview = null
                                errorMessage = null
                            }
                        },
                        label = { Text("Código do programa (ex.: CARD-4F9A)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp),
                        enabled = !isWorking
                    )
                    OutlinedButton(
                        onClick = {
                            val code = inviteCode.trim().uppercase()
                            if (!B2BOnboardingValidator.isValidInviteCode(code)) {
                                errorMessage = "Informe um código válido."
                                return@OutlinedButton
                            }
                            isWorking = true
                            errorMessage = null
                            scope.launch {
                                val found = apiClient.lookupPartnerInvitePreview(code)
                                isWorking = false
                                if (found != null) {
                                    // Troca real de programa (prévia validada): a
                                    // autorização clínica é específica do vínculo.
                                    if (found.programId != activeEnrollment?.programId) {
                                        confirmedDetails = false
                                    }
                                    invitePreview = found
                                } else {
                                    invitePreview = null
                                    errorMessage =
                                        "Programa não encontrado ou ainda não disponível para adesão."
                                }
                            }
                        },
                        enabled = !isWorking && inviteCode.trim().length >= 3,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.height(54.dp)
                    ) {
                        Text("Validar", fontWeight = FontWeight.SemiBold)
                    }
                }

                // Cartão de identificação do programa (prévia ou vínculo ativo)
                if (invitePreview != null || activeEnrollment != null) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = brandColor.copy(alpha = 0.10f)
                        ),
                        border = BorderStroke(1.dp, brandColor.copy(alpha = 0.45f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(brandColor),
                                contentAlignment = Alignment.Center
                            ) {
                                val initial =
                                    (effectiveCompanyName.trim().firstOrNull() ?: 'P').uppercaseChar()
                                Text(
                                    text = initial.toString(),
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = effectiveProgramName,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = brandColor
                                )
                                Text(
                                    text = "$effectiveCompanyName · Tecnologia Braga Saúde",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                invitePreview?.let { prev ->
                                    if (prev.invitePaused) {
                                        Text(
                                            text = "Novas adesões pausadas no momento.",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.error,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    } else if (!prev.hasAvailableSeats) {
                                        Text(
                                            text = "Limite de participantes atingido no momento.",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.error,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Lista de categorias granulares de consentimento
                Text(
                    text = "Categorias de registros compartilhados",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )

                SharedCategoryOption.entries.forEach { option ->
                    val isChecked = selectedCategories.contains(option.wireKey)
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .toggleable(value = isChecked, enabled = !isWorking, role = Role.Checkbox) { checked ->
                                selectedCategoryKeys = if (checked) {
                                    (selectedCategoryKeys + option.wireKey).distinct()
                                } else {
                                    selectedCategoryKeys - option.wireKey
                                }
                                errorMessage = null
                            },
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isChecked) {
                                brandColor.copy(alpha = 0.08f)
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                            }
                        ),
                        border = BorderStroke(
                            width = 1.dp,
                            color = if (isChecked) brandColor.copy(alpha = 0.45f) else Color.Transparent
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = isChecked,
                                onCheckedChange = null,
                                enabled = !isWorking,
                                colors = CheckboxDefaults.colors(checkedColor = brandColor)
                            )
                            Spacer(Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = option.titlePtBr,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = option.descriptionPtBr,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                Text(
                    text = "Você pode alterar essas categorias ou encerrar o vínculo a qualquer momento. Seus registros pessoais continuam salvos na sua conta Braga Saúde.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                val familyShareToggleEnabled = !isWorking && resolvedProgramId != null
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .toggleable(
                            value = familyShareEnabled,
                            enabled = familyShareToggleEnabled,
                            role = Role.Checkbox
                        ) { familyShareEnabled = it },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = familyShareEnabled,
                        onCheckedChange = null,
                        enabled = familyShareToggleEnabled
                    )
                    Text(
                        "Permitir que meu familiar conectado veja lembretes e agendamentos deste programa",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                }
                Text(
                    "Opcional. O acesso depende de um vínculo familiar ativo e de o programa permitir apoio familiar. Você pode retirar esta autorização a qualquer momento. O chat com o parceiro permanece privado.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                errorMessage?.let { msg ->
                    Text(
                        text = msg,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                if (clinicalSelection.isNotEmpty()) {
                    val categories = clinicalSelection.joinToString { it.titlePtBr }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier
                        .fillMaxWidth().toggleable(value = confirmedDetails, enabled = !isWorking,
                            role = Role.Checkbox, onValueChange = { confirmedDetails = it })) {
                        Checkbox(checked = confirmedDetails, onCheckedChange = null)
                        Text("Autorizo o gestor de $effectiveProgramName a consultar os detalhes de $categories para acompanhar minha rotina.",
                            modifier = Modifier.padding(8.dp))
                    }
                }
                val hasValidCategories =
                    B2BOnboardingValidator.isValidConsentSelection(selectedCategories) &&
                        (clinicalSelection.isEmpty() || confirmedDetails)
                // Motivo explícito quando o botão principal fica desabilitado — o
                // usuário nunca deve ficar sem saber o que falta (P2).
                val blockedReason = when {
                    !B2BOnboardingValidator.isValidConsentSelection(selectedCategories) ->
                        "Selecione ao menos uma categoria de registros para continuar."
                    clinicalSelection.isNotEmpty() && !confirmedDetails ->
                        "Autorize a consulta aos detalhes clínicos para continuar."
                    else -> null
                }
                val currentActive = activeEnrollment
                val isUpdatingSameProgram = currentActive?.programId != null &&
                    resolvedProgramId == currentActive?.programId

                if (isUpdatingSameProgram && currentActive?.programId != null) {
                    val progId = currentActive.programId
                    val saveBlockedReason = if (pendingInviteEdit) {
                        "Valide o código digitado ou limpe o campo para salvar as preferências deste programa."
                    } else {
                        blockedReason
                    }
                    saveBlockedReason?.let { reason ->
                        Text(
                            text = reason,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                        )
                    }
                    Button(
                        onClick = {
                            if (!hasValidCategories || isWorking || pendingInviteEdit) return@Button
                            isWorking = true
                            errorMessage = null
                            scope.launch {
                                val res = apiClient.updatePartnerConsent(progId, selectedCategories, familyShareEnabled, confirmedDetails = confirmedDetails)
                                isWorking = false
                                if (res.isSuccess && res.activeContext != null) {
                                    activeEnrollment = res.activeContext
                                    enrollments = enrollments.map { if (it.programId == progId) res.activeContext else it }
                                    familyShareEnabled = res.activeContext.familyShareEnabled
                                    Toast.makeText(
                                        context,
                                        "Preferências de compartilhamento atualizadas.",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                } else {
                                    errorMessage = res.errorDetail
                                }
                            }
                        },
                        enabled = hasValidCategories && !isWorking && !pendingInviteEdit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 52.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = brandColor)
                    ) {
                        Text("Salvar preferências de compartilhamento", fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = {
                            if (isWorking) return@OutlinedButton
                            isWorking = true
                            errorMessage = null
                            scope.launch {
                                val res = apiClient.leavePartnerProgram(progId)
                                isWorking = false
                                if (res.isSuccess) {
                                    activeEnrollment = null
                                    invitePreview = null
                                    enrollments = enrollments.filterNot { it.programId == progId }
                                    familyShareEnabled = false
                                    confirmedDetails = false
                                    Toast.makeText(
                                        context,
                                        "Compartilhamento com o programa encerrado.",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                } else {
                                    errorMessage = res.errorDetail
                                }
                            }
                        },
                        enabled = !isWorking,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("Encerrar participação no programa", fontWeight = FontWeight.SemiBold)
                    }
                } else {
                    val canJoin = B2BOnboardingValidator.isValidInviteCode(inviteCode) &&
                        hasValidCategories &&
                        invitePreview != null &&
                        invitePreview?.invitePaused != true &&
                        invitePreview?.hasAvailableSeats != false
                    val joinBlockedReason = blockedReason ?: when {
                        invitePreview == null -> "Valide o código do programa para confirmar a participação."
                        invitePreview?.invitePaused == true -> "As novas adesões estão pausadas neste programa."
                        invitePreview?.hasAvailableSeats == false -> "O programa está sem vagas disponíveis no momento."
                        else -> null
                    }
                    joinBlockedReason?.let { reason ->
                        Text(
                            text = reason,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                        )
                    }

                    Button(
                        onClick = {
                            if (!canJoin || isWorking) return@Button
                            isWorking = true
                            errorMessage = null
                            scope.launch {
                                val res = apiClient.joinPartnerProgram(inviteCode, selectedCategories, familyShareEnabled, confirmedDetails = confirmedDetails)
                                isWorking = false
                                if (res.isSuccess && res.activeContext != null) {
                                    activeEnrollment = res.activeContext
                                    enrollments = enrollments.filterNot { it.programId == res.activeContext.programId } + res.activeContext
                                    familyShareEnabled = res.activeContext.familyShareEnabled
                                    // O estado atual já reflete o que foi enviado; evita
                                    // recarregar do vínculo e zerar a seleção recém-salva.
                                    seededProgramId = res.activeContext.programId
                                    inviteCode = ""
                                    invitePreview = null
                                    Toast.makeText(
                                        context,
                                        "Participação confirmada com sucesso!",
                                        Toast.LENGTH_LONG
                                    ).show()
                                } else {
                                    errorMessage = res.errorDetail
                                }
                            }
                        },
                        enabled = canJoin && !isWorking,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 52.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = brandColor)
                    ) {
                        Text("Confirmar e compartilhar selecionados", fontWeight = FontWeight.Bold)
                    }
                }
            }

            TextButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Text("Fechar")
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
