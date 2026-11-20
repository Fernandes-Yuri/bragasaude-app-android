package br.com.bragasaude.ui.b2b

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.bragasaude.data.remote.api.BragaApiClient
import br.com.bragasaude.domain.b2b.B2BOnboardingValidator
import br.com.bragasaude.domain.b2b.B2BThemeEngine
import br.com.bragasaude.domain.b2b.PartnerApprovalStatus
import br.com.bragasaude.domain.b2b.PartnerProgramDto
import br.com.bragasaude.domain.b2b.UpsertPartnerProgramRequest
import br.com.bragasaude.ui.components.BragaBottomSheet
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PartnerProgramOnboardingSheet(
    apiClient: BragaApiClient,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var isLoading by remember { mutableStateOf(true) }
    var isSaving by remember { mutableStateOf(false) }
    var isUploadingLogo by remember { mutableStateOf(false) }
    var isUploadingProof by remember { mutableStateOf(false) }
    var existingProgram by remember { mutableStateOf<PartnerProgramDto?>(null) }

    var companyName by rememberSaveable { mutableStateOf("") }
    var programName by rememberSaveable { mutableStateOf("") }
    var cnpj by rememberSaveable { mutableStateOf("") }
    var primaryColor by rememberSaveable {
        mutableStateOf(B2BOnboardingValidator.DEFAULT_PRIMARY_COLOR)
    }
    var logoUrl by rememberSaveable { mutableStateOf("") }
    var showManualLogoUrl by rememberSaveable { mutableStateOf(false) }

    var templateKey by rememberSaveable { mutableStateOf("CLINICA_GERAL") }
    var responsibleName by rememberSaveable { mutableStateOf("") }
    var responsibleCpf by rememberSaveable { mutableStateOf("") }
    var responsibleRole by rememberSaveable { mutableStateOf("SOCIO_TITULAR") }
    var responsiblePhone by rememberSaveable { mutableStateOf("") }
    var city by rememberSaveable { mutableStateOf("") }
    var stateUf by rememberSaveable { mutableStateOf("") }
    var councilType by rememberSaveable { mutableStateOf("") }
    var councilNumber by rememberSaveable { mutableStateOf("") }
    var councilUf by rememberSaveable { mutableStateOf("") }
    var termsAccepted by rememberSaveable { mutableStateOf(false) }

    var selectedProofDocType by rememberSaveable { mutableStateOf("CARTAO_CNPJ_CCMEI") }
    var kybDecisionBanner by rememberSaveable { mutableStateOf<String?>(null) }
    var kybFlagsBanner by remember { mutableStateOf<List<String>>(emptyList()) }
    var kybErrorDetail by remember { mutableStateOf<String?>(null) }

    fun syncFromLoadedProgram(prog: PartnerProgramDto) {
        existingProgram = prog
        companyName = prog.companyName
        programName = prog.programName
        cnpj = prog.cnpj.orEmpty()
        primaryColor = B2BOnboardingValidator.normalizeHexColorOrDefault(prog.primaryColor)
        logoUrl = prog.logoUrl.orEmpty()
        templateKey = prog.templateKey.ifBlank { "CLINICA_GERAL" }
        responsibleName = prog.responsibleName.orEmpty()
        responsibleRole = prog.responsibleRole.orEmpty().ifBlank { "SOCIO_TITULAR" }
        responsiblePhone = prog.responsiblePhone.orEmpty()
        city = prog.city.orEmpty()
        stateUf = prog.state.orEmpty()
        councilType = prog.councilType.orEmpty()
        councilNumber = prog.councilNumber.orEmpty()
        councilUf = prog.councilUf.orEmpty()
        if (!prog.termsAcceptedAt.isNullOrBlank()) {
            termsAccepted = true
        }
        kybDecisionBanner = prog.autoKybStatus
        kybFlagsBanner = prog.autoKybFlags
    }

    fun buildUpsertRequest(): UpsertPartnerProgramRequest {
        val cleanCouncilType = councilType.trim().uppercase().takeIf { it.isNotBlank() }
        val cleanCouncilNum = councilNumber.trim().takeIf { it.isNotBlank() }
        val cleanCouncilUf = councilUf.trim().uppercase().takeIf { it.isNotBlank() }
        return UpsertPartnerProgramRequest(
            companyName = companyName,
            programName = programName,
            cnpj = B2BOnboardingValidator.formatCnpjOrNull(cnpj),
            primaryColor = B2BOnboardingValidator.normalizeHexColorOrDefault(primaryColor),
            logoUrl = logoUrl.trim().takeIf { it.isNotBlank() },
            templateKey = templateKey,
            responsibleName = responsibleName.trim().takeIf { it.isNotBlank() },
            responsibleCpf = B2BOnboardingValidator.formatCpfOrNull(responsibleCpf),
            responsibleRole = responsibleRole.trim().takeIf { it.isNotBlank() },
            responsiblePhone = responsiblePhone.trim().takeIf { it.isNotBlank() },
            councilType = cleanCouncilType,
            councilNumber = cleanCouncilNum,
            councilUf = cleanCouncilUf,
            city = city.trim().takeIf { it.isNotBlank() },
            state = stateUf.trim().uppercase().takeIf { it.isNotBlank() },
            termsAccepted = if (termsAccepted) true else null,
            termsVersion = if (termsAccepted) B2BOnboardingValidator.TERMS_VERSION_CURRENT else null
        )
    }

    suspend fun ensureProgramDraftExists(): PartnerProgramDto? {
        existingProgram?.let { return it }
        if (!B2BOnboardingValidator.canSaveDraft(
                companyName = companyName,
                programName = programName,
                primaryColor = primaryColor,
                cnpj = cnpj.takeIf { it.isNotBlank() },
                logoUrl = null
            )
        ) {
            Toast.makeText(
                context,
                "Informe primeiro o nome da empresa e do programa.",
                Toast.LENGTH_LONG
            ).show()
            return null
        }
        val createdRes = apiClient.saveMyPartnerProgramDetailed(
            buildUpsertRequest().copy(logoUrl = null)
        )
        if (!createdRes.isSuccess) {
            Toast.makeText(
                context,
                createdRes.errorDetail ?: "Salve o rascunho antes de anexar arquivos.",
                Toast.LENGTH_LONG
            ).show()
            return null
        }
        createdRes.program?.let { existingProgram = it }
        return createdRes.program
    }

    LaunchedEffect(Unit) {
        val loaded = apiClient.getMyPartnerProgram()
        if (loaded != null) {
            syncFromLoadedProgram(loaded)
        }
        isLoading = false
    }

    val logoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        val picked = uri ?: return@rememberLauncherForActivityResult
        if (isUploadingLogo || isUploadingProof || isSaving) return@rememberLauncherForActivityResult
        isUploadingLogo = true
        scope.launch {
            try {
                if (ensureProgramDraftExists() == null) return@launch
                val prepared = B2BLogoMediaHelper.prepareFromUri(context, picked)
                val uploaded = apiClient.uploadPartnerLogo(
                    fileName = prepared.fileName,
                    mimeType = prepared.mimeType,
                    bytes = prepared.bytes
                )
                if (uploaded.isSuccess) {
                    logoUrl = uploaded.logoUrl.orEmpty()
                    uploaded.program?.let { syncFromLoadedProgram(it) }
                    Toast.makeText(
                        context,
                        "Logo enviada e armazenada com segurança!",
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    Toast.makeText(
                        context,
                        uploaded.errorDetail ?: "Não foi possível enviar a logo agora.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Toast.makeText(
                    context,
                    e.message ?: "Não foi possível preparar a imagem selecionada.",
                    Toast.LENGTH_LONG
                ).show()
            } finally {
                isUploadingLogo = false
            }
        }
    }

    val proofPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        val picked = uri ?: return@rememberLauncherForActivityResult
        if (isUploadingProof || isUploadingLogo || isSaving) return@rememberLauncherForActivityResult
        isUploadingProof = true
        scope.launch {
            try {
                if (ensureProgramDraftExists() == null) return@launch
                val prepared = B2BLogoMediaHelper.prepareProofFromUri(context, picked)
                val uploaded = apiClient.uploadPartnerProof(
                    docType = selectedProofDocType,
                    fileName = prepared.fileName,
                    mimeType = prepared.mimeType,
                    bytes = prepared.bytes
                )
                if (uploaded.isSuccess && uploaded.program != null) {
                    syncFromLoadedProgram(uploaded.program)
                    kybErrorDetail = null
                    Toast.makeText(
                        context,
                        "Comprovante privado enviado para revisão.",
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    Toast.makeText(
                        context,
                        uploaded.errorDetail ?: "Não foi possível enviar o comprovante.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Toast.makeText(
                    context,
                    e.message ?: "Não foi possível ler o arquivo selecionado.",
                    Toast.LENGTH_LONG
                ).show()
            } finally {
                isUploadingProof = false
            }
        }
    }

    val tokens = remember(primaryColor) {
        B2BThemeEngine.resolveAccessiblePalette(primaryColor)
    }
    val parsedColor = remember(tokens.primaryHex) {
        Color(android.graphics.Color.parseColor(tokens.primaryHex))
    }
    val onParsedPrimary = remember(tokens.onPrimaryHex) {
        Color(android.graphics.Color.parseColor(tokens.onPrimaryHex))
    }

    val status = existingProgram?.parsedStatus ?: PartnerApprovalStatus.DRAFT
    val publicReviewNote = sanitizePublicReviewNote(existingProgram?.reviewNote)
    // Só exibe "Retorno da equipe Braga" em status que geram devolutiva pública. Em SUSPENDED
    // a nota já é mostrada no card vermelho próprio; em APPROVED/DRAFT não há retorno a exibir.
    val showsTeamReturn = status == PartnerApprovalStatus.PENDING_REVIEW ||
        status == PartnerApprovalStatus.CHANGES_REQUESTED ||
        status == PartnerApprovalStatus.REJECTED

    BragaBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "PROGRAMA PARCEIRO",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Sua empresa. A sua marca.",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = parsedColor.copy(alpha = 0.14f)
                ) {
                    Text(
                        text = status.labelPtBr,
                        color = parsedColor,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }

            Text(
                text = B2BOnboardingValidator.REFERENCE_SUBTITLE,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Prévia da marca do parceiro
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = parsedColor.copy(alpha = 0.10f)),
                border = BorderStroke(1.dp, parsedColor.copy(alpha = 0.45f))
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
                            .background(parsedColor),
                        contentAlignment = Alignment.Center
                    ) {
                        val initial = (companyName.trim().firstOrNull() ?: 'P').uppercaseChar()
                        Text(
                            text = initial.toString(),
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = programName.ifBlank { "Nome do seu programa" },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = parsedColor
                        )
                        Text(
                            text = "${companyName.ifBlank { "Sua empresa" }} · Tecnologia Braga Saúde",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (!kybErrorDetail.isNullOrBlank()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "Validação cadastral recusada",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Text(
                            text = kybErrorDetail.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }

            if (status == PartnerApprovalStatus.SUSPENDED) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.75f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "Programa suspenso temporariamente",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Text(
                            text = publicReviewNote
                                ?: "Os convites estão bloqueados até regularização pela equipe Braga.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }

            if (status == PartnerApprovalStatus.APPROVED && kybDecisionBanner == "GREEN") {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFFDCFCE7)
                    ),
                    border = BorderStroke(1.dp, Color(0xFF16A34A).copy(alpha = 0.45f))
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "Aprovação automática concluída",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF166534)
                        )
                        Text(
                            text = "CNPJ e responsável confirmados automaticamente na Receita Federal. Código de convite e placa de recepção liberados!",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF14532D)
                        )
                    }
                }
            }

            if (kybFlagsBanner.isNotEmpty() &&
                (status == PartnerApprovalStatus.PENDING_REVIEW || status == PartnerApprovalStatus.CHANGES_REQUESTED)
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFFFEF3C7)
                    ),
                    border = BorderStroke(1.dp, Color(0xFFD97706).copy(alpha = 0.5f))
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Em análise pela equipe Braga",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF92400E)
                        )
                        kybFlagsBanner.forEach { flag ->
                            Text(
                                text = "• ${B2BOnboardingValidator.formatKybFlagPtBr(flag)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF78350F)
                            )
                        }
                        Text(
                            text = "Dica: anexe até 2 comprovantes privados abaixo (Cartão CNPJ/CCMEI, CRM/CRN ou carta de autorização) para acelerar a revisão.",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF92400E)
                        )
                    }
                }
            }

            if (showsTeamReturn) {
                publicReviewNote?.let { note ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                        )
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = "Retorno da equipe Braga",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = note,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }

            if (status == PartnerApprovalStatus.APPROVED && existingProgram != null) {
                val prog = existingProgram!!
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                    ),
                    border = BorderStroke(1.dp, parsedColor.copy(alpha = 0.35f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Código de convite do programa",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = prog.inviteCode ?: "Aguardando código",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = parsedColor
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = parsedColor.copy(alpha = 0.14f)
                            ) {
                                Text(
                                    text = "${prog.activeParticipants} / ${prog.seatLimit} ${B2BTextFormatters.plural(prog.seatLimit, "vaga", "vagas")}",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = parsedColor,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }

                        Text(
                            text = if (prog.invitePaused) {
                                "Novas adesões estão pausadas. Participantes já vinculados continuam ativos."
                            } else {
                                "Convites ativos dentro do limite da franquia contratada."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    if (isSaving) return@OutlinedButton
                                    isSaving = true
                                    scope.launch {
                                        val updated = apiClient.setPartnerInvitePaused(!prog.invitePaused)
                                        isSaving = false
                                        if (updated != null) {
                                            existingProgram = updated
                                            val msg = if (updated.invitePaused) {
                                                "Novas adesões pausadas."
                                            } else {
                                                "Novas adesões retomadas."
                                            }
                                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                enabled = !isSaving,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(
                                    text = if (prog.invitePaused) "Retomar convites" else "Pausar convites",
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            OutlinedButton(
                                onClick = {
                                    if (isSaving) return@OutlinedButton
                                    isSaving = true
                                    scope.launch {
                                        val updated = apiClient.regeneratePartnerInviteCode()
                                        isSaving = false
                                        if (updated != null) {
                                            existingProgram = updated
                                            Toast.makeText(
                                                context,
                                                "Novo código de convite gerado.",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    }
                                },
                                enabled = !isSaving,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Novo código", fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }

                prog.inviteCode?.takeIf { it.isNotBlank() }?.let { activeCode ->
                    PartnerInviteQrCard(
                        programName = prog.programName,
                        companyName = prog.companyName,
                        inviteCode = activeCode,
                        primaryColorHex = prog.primaryColor,
                        accentColor = parsedColor,
                        onAccentColor = onParsedPrimary
                    )
                }
            }

            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            } else {
                Text(
                    text = "1. Perfil do programa",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(B2BOnboardingValidator.TEMPLATE_OPTIONS) { option ->
                        FilterChip(
                            selected = templateKey.equals(option.key, ignoreCase = true),
                            onClick = { templateKey = option.key },
                            label = { Text(option.labelPtBr) }
                        )
                    }
                }

                Text(
                    text = "2. Identificação da empresa",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )

                OutlinedTextField(
                    value = companyName,
                    onValueChange = { if (it.length <= 160) companyName = it },
                    label = { Text("Razão social ou nome da empresa *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                )

                OutlinedTextField(
                    value = programName,
                    onValueChange = { if (it.length <= 120) programName = it },
                    label = { Text("Nome do programa (ex.: Cuidado Cardio) *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                )

                val cnpjDigits = B2BOnboardingValidator.extractCnpjDigits(cnpj)
                val cnpjLooksValid = B2BOnboardingValidator.isValidCnpj(cnpj)
                OutlinedTextField(
                    value = cnpj,
                    onValueChange = { if (it.length <= 18) cnpj = it },
                    label = { Text("CNPJ *") },
                    singleLine = true,
                    isError = cnpj.isNotBlank() && !cnpjLooksValid,
                    supportingText = {
                        when {
                            cnpj.isBlank() -> Text("14 dígitos; CPF não é aceito. Validado na Receita Federal.")
                            cnpjDigits.length == 11 && !cnpjLooksValid -> Text("14 dígitos; CPF não é aceito. O CPF é informado apenas no responsável abaixo.")
                            cnpjLooksValid -> Text("CNPJ válido para verificação oficial.")
                            else -> Text("Confira os 14 dígitos e os dígitos verificadores do CNPJ.")
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = city,
                        onValueChange = { if (it.length <= 96) city = it },
                        label = { Text("Cidade *") },
                        singleLine = true,
                        modifier = Modifier.weight(0.68f),
                        shape = RoundedCornerShape(14.dp)
                    )
                    val cleanUf = stateUf.trim().uppercase()
                    OutlinedTextField(
                        value = stateUf,
                        onValueChange = { raw ->
                            val clean = raw.filter { it.isLetter() }.uppercase().take(2)
                            stateUf = clean
                        },
                        label = { Text("UF *") },
                        singleLine = true,
                        isError = cleanUf.isNotBlank() && cleanUf !in B2BOnboardingValidator.BR_UFS,
                        modifier = Modifier.weight(0.32f),
                        shape = RoundedCornerShape(14.dp),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters)
                    )
                }

                Text(
                    text = "3. Responsável pelo programa",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )

                OutlinedTextField(
                    value = responsibleName,
                    onValueChange = { if (it.length <= 160) responsibleName = it },
                    label = { Text("Nome completo do responsável *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                )

                val cpfLooksValid = B2BOnboardingValidator.isValidCpf(responsibleCpf)
                val savedMaskedCpf = existingProgram?.responsibleCpfMasked
                OutlinedTextField(
                    value = responsibleCpf,
                    onValueChange = { raw ->
                        if (raw.length <= 14) responsibleCpf = raw
                    },
                    label = { Text("CPF do responsável (somente conferência LGPD) *") },
                    singleLine = true,
                    isError = responsibleCpf.isNotBlank() && !cpfLooksValid,
                    supportingText = {
                        when {
                            responsibleCpf.isNotBlank() && !cpfLooksValid ->
                                Text("Informe um CPF com 11 dígitos válidos.")
                            responsibleCpf.isBlank() && !savedMaskedCpf.isNullOrBlank() ->
                                Text("CPF conferido salvo: $savedMaskedCpf (preencha só se quiser alterar).")
                            else ->
                                Text("Usado em memória apenas para conferir QSA/MEI na Receita Federal; nunca exibido a pacientes.")
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )

                Text(
                    text = "Vínculo / Cargo na empresa *",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(B2BOnboardingValidator.RESPONSIBLE_ROLE_OPTIONS) { roleOpt ->
                        FilterChip(
                            selected = responsibleRole.equals(roleOpt.key, ignoreCase = true),
                            onClick = { responsibleRole = roleOpt.key },
                            label = { Text(roleOpt.labelPtBr) }
                        )
                    }
                }

                OutlinedTextField(
                    value = responsiblePhone,
                    onValueChange = { if (it.length <= 20) responsiblePhone = it },
                    label = { Text("Telefone comercial / WhatsApp (com DDD) *") },
                    singleLine = true,
                    isError = responsiblePhone.isNotBlank() &&
                        !B2BOnboardingValidator.isValidResponsiblePhone(responsiblePhone),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
                )

                val isAssistencial = B2BOnboardingValidator.isAssistencialTemplate(templateKey)
                val councilLocked = status == PartnerApprovalStatus.APPROVED &&
                    !existingProgram?.councilType.isNullOrBlank()
                if (isAssistencial || councilType.isNotBlank() || councilNumber.isNotBlank()) {
                    Text(
                        text = if (isAssistencial) {
                            "4. Registro no Conselho Profissional (recomendado para aprovação imediata)"
                        } else {
                            "4. Registro no Conselho Profissional (opcional)"
                        },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        item {
                            FilterChip(
                                selected = councilType.isBlank(),
                                enabled = !councilLocked,
                                onClick = {
                                    councilType = ""
                                    councilNumber = ""
                                    councilUf = ""
                                },
                                label = { Text("Sem conselho") }
                            )
                        }
                        items(B2BOnboardingValidator.COUNCIL_TYPE_OPTIONS) { ct ->
                            FilterChip(
                                selected = councilType.equals(ct, ignoreCase = true),
                                enabled = !councilLocked,
                                onClick = { councilType = ct },
                                label = { Text(ct) }
                            )
                        }
                    }
                    if (councilType.isNotBlank()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedTextField(
                                value = councilNumber,
                                onValueChange = { if (it.length <= 24) councilNumber = it },
                                enabled = !councilLocked,
                                label = { Text("Número $councilType") },
                                singleLine = true,
                                modifier = Modifier.weight(0.68f),
                                shape = RoundedCornerShape(14.dp)
                            )
                            OutlinedTextField(
                                value = councilUf,
                                onValueChange = { raw ->
                                    councilUf = raw.filter { it.isLetter() }.uppercase().take(2)
                                },
                                enabled = !councilLocked,
                                label = { Text("UF") },
                                singleLine = true,
                                modifier = Modifier.weight(0.32f),
                                shape = RoundedCornerShape(14.dp),
                                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters)
                            )
                        }
                    }
                    if (councilLocked) {
                        Text(
                            text = "Registro profissional bloqueado após aprovação (contate o suporte para alterações).",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else if (isAssistencial && councilType.isBlank()) {
                        Text(
                            text = "Sem conselho informado neste perfil assistencial, a solicitação entrará em revisão manual (bandeira amarela).",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Text(
                    text = "5. Identidade visual",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = "Cor principal do programa",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(B2BOnboardingValidator.PRESET_COLORS) { preset ->
                        val swatchColor = runCatching {
                            Color(android.graphics.Color.parseColor(preset))
                        }.getOrDefault(Color(0xFF1A6B5C))
                        val isSelected = primaryColor.equals(preset, ignoreCase = true)
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(swatchColor)
                                .border(
                                    width = if (isSelected) 3.dp else 1.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                                    shape = CircleShape
                                )
                                .clickable { primaryColor = preset }
                        )
                    }
                }

                OutlinedTextField(
                    value = primaryColor,
                    onValueChange = { if (it.length <= 7) primaryColor = it },
                    label = { Text("Código hexadecimal (#RRGGBB)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                )

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            if (logoUrl.isNotBlank()) {
                                AsyncImage(
                                    model = logoUrl,
                                    contentDescription = "Prévia da logo do programa",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(52.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(MaterialTheme.colorScheme.surface)
                                )
                            } else {
                                val fallbackInitial = (companyName.trim().firstOrNull() ?: 'B').uppercaseChar()
                                Box(
                                    modifier = Modifier
                                        .size(52.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(parsedColor.copy(alpha = 0.16f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = fallbackInitial.toString(),
                                        color = parsedColor,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 18.sp
                                    )
                                }
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Logo do programa",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Envie PNG, JPEG ou WebP direto da galeria (até 2 MB, sem metadados).",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        OutlinedButton(
                            onClick = {
                                logoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            enabled = !isUploadingLogo && !isSaving,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            if (isUploadingLogo) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp
                                )
                                Spacer(Modifier.width(8.dp))
                                Text("Enviando logo...")
                            } else {
                                Text(
                                    text = if (logoUrl.isNotBlank()) "Trocar logo pela galeria" else "Escolher logo da galeria",
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        TextButton(
                            onClick = { showManualLogoUrl = !showManualLogoUrl },
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                        ) {
                            Text(
                                if (showManualLogoUrl) "Ocultar URL manual"
                                else "Ou informar URL HTTPS manualmente (opcional)"
                            )
                        }

                        if (showManualLogoUrl) {
                            OutlinedTextField(
                                value = logoUrl,
                                onValueChange = { if (it.length <= 512) logoUrl = it },
                                label = { Text("URL da logo (opcional — https://)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp)
                            )
                        }
                    }
                }

                val currentProofs = existingProgram?.proofDocuments.orEmpty()
                if (status != PartnerApprovalStatus.APPROVED) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "6. Comprovantes privados (opcional · até 2 PDF/imagem de 5 MB)",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Armazenados em cofre privado criptografado somente durante a análise e expurgados automaticamente após aprovação ou recusa.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                items(B2BOnboardingValidator.PROOF_DOC_TYPE_OPTIONS) { docOpt ->
                                    FilterChip(
                                        selected = selectedProofDocType == docOpt.key,
                                        onClick = { selectedProofDocType = docOpt.key },
                                        label = { Text(docOpt.labelPtBr) }
                                    )
                                }
                            }

                            OutlinedButton(
                                onClick = {
                                    proofPickerLauncher.launch(
                                        arrayOf("application/pdf", "image/png", "image/jpeg", "image/webp")
                                    )
                                },
                                enabled = currentProofs.size < 2 && !isUploadingProof && !isUploadingLogo && !isSaving,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                if (isUploadingProof) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text("Enviando comprovante seguro...")
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.UploadFile,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        if (currentProofs.size >= 2) "Limite de 2 comprovantes atingido"
                                        else "Anexar comprovante (PDF ou imagem)"
                                    )
                                }
                            }

                            currentProofs.forEach { doc ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(MaterialTheme.colorScheme.surface)
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = doc.fileName,
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = "${proofDocTypeLabel(doc.docType)} · ${(doc.sizeBytes / 1024L).coerceAtLeast(1L)} KB",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            if (isSaving || isUploadingProof) return@IconButton
                                            isSaving = true
                                            scope.launch {
                                                val res = apiClient.deletePartnerProof(doc.id)
                                                isSaving = false
                                                res.program?.let { syncFromLoadedProgram(it) }
                                                Toast.makeText(
                                                    context,
                                                    if (res.isSuccess) "Comprovante removido."
                                                    else (res.errorDetail ?: "Erro ao remover comprovante."),
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                        },
                                        enabled = !isSaving && !isUploadingProof
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Remover comprovante",
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { termsAccepted = !termsAccepted }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Checkbox(
                            checked = termsAccepted,
                            onCheckedChange = { termsAccepted = it }
                        )
                        Text(
                            text = "Declaro responsabilidade pelos dados acima e aceito os Termos B2B (${B2BOnboardingValidator.TERMS_VERSION_CURRENT}), o DPA LGPD e a Declaração Não-SaMD (bem-estar e acompanhamento sem diagnóstico clínico).",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 10.dp)
                        )
                    }
                }

                val hasSavedCpf = !existingProgram?.responsibleCpfMasked.isNullOrBlank()
                val canSave = B2BOnboardingValidator.canSaveDraft(
                    companyName = companyName,
                    programName = programName,
                    primaryColor = primaryColor,
                    cnpj = cnpj.takeIf { it.isNotBlank() },
                    logoUrl = logoUrl.takeIf { it.isNotBlank() }
                )
                val canSubmit = B2BOnboardingValidator.canSubmitKybForReview(
                    companyName = companyName,
                    programName = programName,
                    cnpj = cnpj,
                    primaryColor = primaryColor,
                    responsibleName = responsibleName,
                    responsibleCpf = responsibleCpf,
                    cpfAlreadySaved = hasSavedCpf,
                    responsibleRole = responsibleRole,
                    responsiblePhone = responsiblePhone,
                    city = city,
                    state = stateUf,
                    termsAccepted = termsAccepted,
                    councilType = councilType.takeIf { it.isNotBlank() },
                    councilNumber = councilNumber.takeIf { it.isNotBlank() },
                    councilUf = councilUf.takeIf { it.isNotBlank() },
                    logoUrl = logoUrl.takeIf { it.isNotBlank() }
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            if (!canSave || isSaving || isUploadingLogo || isUploadingProof) return@OutlinedButton
                            isSaving = true
                            kybErrorDetail = null
                            scope.launch {
                                val savedRes = apiClient.saveMyPartnerProgramDetailed(buildUpsertRequest())
                                isSaving = false
                                if (savedRes.isSuccess && savedRes.program != null) {
                                    syncFromLoadedProgram(savedRes.program)
                                    Toast.makeText(
                                        context,
                                        "Preparação salva com sucesso.",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                } else {
                                    kybErrorDetail = savedRes.errorDetail
                                    Toast.makeText(
                                        context,
                                        savedRes.errorDetail ?: "Não foi possível salvar agora.",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                        },
                        enabled = canSave && !isSaving && !isUploadingLogo && !isUploadingProof,
                        modifier = Modifier.weight(1f).height(52.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text("Salvar rascunho", fontWeight = FontWeight.SemiBold)
                    }

                    Button(
                        onClick = {
                            if (!canSubmit || isSaving || isUploadingLogo || isUploadingProof) return@Button
                            isSaving = true
                            kybErrorDetail = null
                            scope.launch {
                                val savedRes = apiClient.saveMyPartnerProgramDetailed(buildUpsertRequest())
                                if (!savedRes.isSuccess || savedRes.program == null) {
                                    isSaving = false
                                    kybErrorDetail = savedRes.errorDetail
                                    Toast.makeText(
                                        context,
                                        savedRes.errorDetail ?: "Verifique os dados informados.",
                                        Toast.LENGTH_LONG
                                    ).show()
                                    return@launch
                                }
                                syncFromLoadedProgram(savedRes.program)
                                val submitRes = apiClient.submitMyPartnerProgramKyb(
                                    termsAccepted = termsAccepted,
                                    termsVersion = B2BOnboardingValidator.TERMS_VERSION_CURRENT,
                                    responsibleCpf = B2BOnboardingValidator.formatCpfOrNull(responsibleCpf)
                                )
                                isSaving = false
                                if (submitRes.isSuccess && submitRes.program != null) {
                                    syncFromLoadedProgram(submitRes.program)
                                    kybDecisionBanner = submitRes.kybDecision
                                    kybFlagsBanner = submitRes.flags
                                    responsibleCpf = ""
                                    val msg = if (submitRes.kybDecision == "GREEN") {
                                        "Programa aprovado automaticamente!"
                                    } else {
                                        "Solicitação enviada para revisão da equipe Braga."
                                    }
                                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                } else {
                                    kybErrorDetail = submitRes.errorDetail
                                    kybDecisionBanner = submitRes.kybDecision
                                    kybFlagsBanner = submitRes.flags
                                    Toast.makeText(
                                        context,
                                        submitRes.errorDetail ?: "Verifique os dados antes de enviar.",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                        },
                        enabled = canSubmit && !isSaving && !isUploadingLogo && !isUploadingProof && status != PartnerApprovalStatus.APPROVED,
                        modifier = Modifier.weight(1f).height(52.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = parsedColor,
                            contentColor = onParsedPrimary
                        )
                    ) {
                        Text(
                            if (status == PartnerApprovalStatus.PENDING_REVIEW) "Revalidar dados"
                            else "Validar e enviar",
                            fontWeight = FontWeight.Bold
                        )
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

private val INTERNAL_REVIEW_MARKERS = listOf(
    "conta de teste",
    "aprovação operacional de demonstração"
)

private val INTERNAL_REVIEW_TICKET = Regex("""\bB2B-\d+\b""", RegexOption.IGNORE_CASE)

/**
 * A nota de revisão pode carregar metadados internos de homologação (contas de teste,
 * aprovações de demonstração, tickets B2B-31). Esses marcadores nunca vão para a UI
 * do parceiro: nesses casos o card de retorno simplesmente não é exibido.
 */
private fun sanitizePublicReviewNote(note: String?): String? {
    val clean = note?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    val hasInternalMarker = INTERNAL_REVIEW_MARKERS.any { clean.contains(it, ignoreCase = true) } ||
        INTERNAL_REVIEW_TICKET.containsMatchIn(clean)
    return clean.takeUnless { hasInternalMarker }
}

/** Rótulo humano do comprovante; nunca renderiza o wire key cru do backend. */
private fun proofDocTypeLabel(raw: String?): String =
    B2BOnboardingValidator.PROOF_DOC_TYPE_OPTIONS
        .firstOrNull { it.key.equals(raw?.trim(), ignoreCase = true) }
        ?.labelPtBr
        ?: "Comprovante anexado"
