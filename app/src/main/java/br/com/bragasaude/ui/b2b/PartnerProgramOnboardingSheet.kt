package br.com.bragasaude.ui.b2b

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PartnerProgramOnboardingSheet(
    apiClient: BragaApiClient,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current

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
    // Estado visual do indicador de etapas: o modelo padrão já vem selecionado, então só
    // marcamos a etapa 1 como concluída quando o parceiro confirma um perfil ou traz dados.
    var templateConfirmed by rememberSaveable { mutableStateOf(false) }
    var councilDecisionMade by rememberSaveable { mutableStateOf(false) }
    var inviteCopyFeedback by remember { mutableStateOf<String?>(null) }
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
        templateConfirmed = true
        responsibleName = prog.responsibleName.orEmpty()
        responsibleRole = prog.responsibleRole.orEmpty().ifBlank { "SOCIO_TITULAR" }
        responsiblePhone = prog.responsiblePhone.orEmpty()
        city = prog.city.orEmpty()
        stateUf = prog.state.orEmpty()
        councilType = prog.councilType.orEmpty()
        councilNumber = prog.councilNumber.orEmpty()
        councilUf = prog.councilUf.orEmpty()
        // Programa já salvo: a escolha (com ou sem conselho) já foi feita em algum momento.
        councilDecisionMade = true
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

    // Feedback do "Copiar código" some sozinho, mas fica no liveRegion enquanto visível.
    LaunchedEffect(inviteCopyFeedback) {
        if (inviteCopyFeedback != null) {
            delay(4000)
            inviteCopyFeedback = null
        }
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

    // --- Indicador visual de etapas (puramente de leitura: nenhuma validação nova) ---
    val hasSavedCpf = !existingProgram?.responsibleCpfMasked.isNullOrBlank()
    val cpfStepOk = if (responsibleCpf.isBlank()) hasSavedCpf else B2BOnboardingValidator.isValidCpf(responsibleCpf)
    val ufStepOk = stateUf.trim().uppercase() in B2BOnboardingValidator.BR_UFS
    val assistencialStep = B2BOnboardingValidator.isAssistencialTemplate(templateKey)
    val onboardingStepsDone = listOf(
        existingProgram != null || templateConfirmed || companyName.isNotBlank() || programName.isNotBlank(),
        companyName.trim().length in 2..160 &&
            programName.trim().length in 2..120 &&
            B2BOnboardingValidator.isValidCnpj(cnpj) &&
            city.trim().length in 2..96 &&
            ufStepOk,
        responsibleName.trim().length in 3..160 &&
            cpfStepOk &&
            B2BOnboardingValidator.RESPONSIBLE_ROLE_OPTIONS.any { it.key == responsibleRole.trim().uppercase() } &&
            B2BOnboardingValidator.isValidResponsiblePhone(responsiblePhone),
        !assistencialStep || councilDecisionMade || councilType.isNotBlank(),
        B2BOnboardingValidator.isValidHexColor(primaryColor),
        termsAccepted
    )
    val completedOnboardingSteps = onboardingStepsDone.count { it }
    val currentOnboardingStep = onboardingStepsDone.indexOfFirst { !it }
        .let { if (it < 0) ONBOARDING_STEP_TITLES.lastIndex else it }
    val allOnboardingStepsDone = completedOnboardingSteps == ONBOARDING_STEP_TITLES.size
    val onboardingStepSummary = if (allOnboardingStepsDone) {
        "6 de 6 etapas preenchidas · dados completos"
    } else {
        "Etapa ${currentOnboardingStep + 1} de 6 · ${ONBOARDING_STEP_TITLES[currentOnboardingStep]}"
    }

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
                Text(
                    text = "PROGRAMA PARCEIRO",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                // A folha é longa: sem um fechar no topo o usuário só o encontraria após rolar tudo.
                TextButton(
                    onClick = onDismiss,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("Fechar", fontWeight = FontWeight.SemiBold)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Sua empresa. A sua marca.",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                PartnerStatusChip(status = status)
            }

            Text(
                text = B2BOnboardingValidator.REFERENCE_SUBTITLE,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Indicador de etapas: orienta onde o parceiro está antes de rolar a folha inteira.
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics(mergeDescendants = true) {
                        contentDescription = "$onboardingStepSummary $completedOnboardingSteps de 6 etapas concluídas."
                    },
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (allOnboardingStepsDone) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Text(
                            text = onboardingStepSummary,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "$completedOnboardingSteps/6",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        onboardingStepsDone.forEachIndexed { index, done ->
                            val isCurrent = index == currentOnboardingStep && !done
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(if (isCurrent) 10.dp else 6.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(
                                        when {
                                            done -> MaterialTheme.colorScheme.primary
                                            isCurrent -> MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                                            else -> MaterialTheme.colorScheme.outlineVariant
                                        }
                                    )
                            )
                        }
                    }
                    Text(
                        text = if (allOnboardingStepsDone) {
                            when (status) {
                                PartnerApprovalStatus.APPROVED ->
                                    "Empresa aprovada: as edições ficam salvas no programa."
                                PartnerApprovalStatus.PENDING_REVIEW ->
                                    "Solicitação enviada. Use \"Revalidar dados\" se alterar algo."
                                else ->
                                    "Tudo preenchido: confira os comprovantes e envie para a verificação KYB."
                            }
                        } else {
                            ONBOARDING_STEP_HINTS[currentOnboardingStep]
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

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
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { liveRegion = LiveRegionMode.Polite },
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { liveRegion = LiveRegionMode.Polite },
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

            // Empresa aprovada mas KYB ainda em conferência: sem esta linha o selo "Aprovado"
            // parecia contradizer "Documentos: 0 · KYB aguardando" na aba Empresa.
            if (status == PartnerApprovalStatus.APPROVED && kybDecisionBanner != "GREEN") {
                val sentProofs = existingProgram?.proofDocuments?.size ?: 0
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.VerifiedUser,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Empresa aprovada · verificação KYB em andamento",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                        Text(
                            text = "Documentos enviados: $sentProofs de 2 opcionais. A conferência corre em paralelo e não bloqueia o código de convite, o QR Code nem a placa PDF.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
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
                            Text("Código de convite do programa", style = MaterialTheme.typography.labelMedium)
                            // Estado por texto + ícone, não só pela cor do selo.
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = if (prog.invitePaused) {
                                        Icons.Default.PauseCircle
                                    } else {
                                        Icons.Default.CheckCircle
                                    },
                                    contentDescription = null,
                                    tint = if (prog.invitePaused) {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    } else {
                                        parsedColor
                                    },
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = if (prog.invitePaused) "Pausado" else "Ativo",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (prog.invitePaused) {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    } else {
                                        parsedColor
                                    }
                                )
                            }
                        }
                        Text(
                            text = prog.inviteCode ?: "Aguardando código",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Button(
                            onClick = {
                                val code = prog.inviteCode?.trim().orEmpty()
                                if (code.isBlank()) return@Button
                                clipboardManager.setText(AnnotatedString(code))
                                inviteCopyFeedback = "Código $code copiado para a área de transferência."
                                Toast.makeText(context, "Código copiado.", Toast.LENGTH_SHORT).show()
                            },
                            enabled = !prog.inviteCode.isNullOrBlank() && !isSaving,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = parsedColor,
                                contentColor = onParsedPrimary
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Copiar código", fontWeight = FontWeight.SemiBold)
                        }
                        AnimatedVisibility(visible = inviteCopyFeedback != null) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .semantics { liveRegion = LiveRegionMode.Polite },
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = parsedColor,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = inviteCopyFeedback.orEmpty(),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Text(
                            "${prog.activeParticipants} de ${B2BTextFormatters.countLabel(prog.seatLimit, "vaga", "vagas")} em uso"
                        )
                        if (prog.seatLimit > 0) PartnerProgress(prog.activeParticipants.toFloat() / prog.seatLimit)

                        Text(
                            text = if (prog.invitePaused) {
                                "Convites pausados: novas adesões ficam bloqueadas; participantes já vinculados seguem ativos."
                            } else {
                                "Convites ativos. Sem data de expiração: o código vale enquanto o programa estiver aprovado e houver vagas."
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
                OnboardingSectionHeader(
                    number = 1,
                    title = "Perfil do programa",
                    description = "Escolha o modelo que combina com a sua operação.",
                    icon = Icons.Default.AutoAwesome
                )

                // Wrap em vez de rolagem horizontal: em telas estreitas e fonte ampliada os
                // últimos perfis ("Nutrição e Bem-Estar", "Personalizado…") ficavam fora da borda.
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    B2BOnboardingValidator.TEMPLATE_OPTIONS.forEach { option ->
                        FilterChip(
                            selected = templateKey.equals(option.key, ignoreCase = true),
                            onClick = {
                                templateKey = option.key
                                templateConfirmed = true
                            },
                            label = { Text(option.labelPtBr) }
                        )
                    }
                }

                OnboardingSectionHeader(
                    number = 2,
                    title = "Identificação da empresa",
                    description = "CNPJ, nome do programa e cidade/UF.",
                    icon = Icons.Default.Description
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
                    label = { Text("Nome do programa *") },
                    supportingText = { Text("Ex.: Cuidado Cardio") },
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

                OnboardingSectionHeader(
                    number = 3,
                    title = "Responsável pelo programa",
                    description = "Quem responde legalmente e mantém o contato.",
                    icon = Icons.Default.Person
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
                    label = { Text("CPF do responsável *") },
                    singleLine = true,
                    isError = responsibleCpf.isNotBlank() && !cpfLooksValid,
                    supportingText = {
                        when {
                            responsibleCpf.isNotBlank() && !cpfLooksValid ->
                                Text("Informe um CPF com 11 dígitos válidos.")
                            responsibleCpf.isBlank() && !savedMaskedCpf.isNullOrBlank() ->
                                Text("CPF conferido salvo: $savedMaskedCpf (preencha só se quiser alterar).")
                            else ->
                                Text("Somente conferência LGPD (QSA/MEI na Receita Federal); nunca exibido a pacientes.")
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
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    B2BOnboardingValidator.RESPONSIBLE_ROLE_OPTIONS.forEach { roleOpt ->
                        FilterChip(
                            selected = responsibleRole.equals(roleOpt.key, ignoreCase = true),
                            onClick = { responsibleRole = roleOpt.key },
                            label = { Text(roleOpt.labelPtBr) }
                        )
                    }
                }

                val phoneLooksValid =
                    B2BOnboardingValidator.isValidResponsiblePhone(responsiblePhone)
                OutlinedTextField(
                    value = responsiblePhone,
                    onValueChange = { if (it.length <= 20) responsiblePhone = it },
                    label = { Text("Telefone comercial / WhatsApp *") },
                    supportingText = {
                        Text(
                            if (responsiblePhone.isNotBlank() && !phoneLooksValid) {
                                "Informe DDD + número válido."
                            } else {
                                "Com DDD; usado apenas para contato do programa."
                            }
                        )
                    },
                    singleLine = true,
                    isError = responsiblePhone.isNotBlank() && !phoneLooksValid,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
                )

                val isAssistencial = B2BOnboardingValidator.isAssistencialTemplate(templateKey)
                val councilLocked = status == PartnerApprovalStatus.APPROVED &&
                    !existingProgram?.councilType.isNullOrBlank()
                if (isAssistencial || councilType.isNotBlank() || councilNumber.isNotBlank()) {
                    OnboardingSectionHeader(
                        number = 4,
                        title = "Registro no Conselho Profissional",
                        description = if (isAssistencial) {
                            "Recomendado para aprovação imediata neste perfil."
                        } else {
                            "Opcional neste perfil de programa."
                        },
                        icon = Icons.Default.VerifiedUser
                    )
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = councilType.isBlank(),
                            enabled = !councilLocked,
                            onClick = {
                                councilType = ""
                                councilNumber = ""
                                councilUf = ""
                                councilDecisionMade = true
                            },
                            label = { Text("Sem conselho") }
                        )
                        B2BOnboardingValidator.COUNCIL_TYPE_OPTIONS.forEach { ct ->
                            FilterChip(
                                selected = councilType.equals(ct, ignoreCase = true),
                                enabled = !councilLocked,
                                onClick = {
                                    councilType = ct
                                    councilDecisionMade = true
                                },
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

                OnboardingSectionHeader(
                    number = 5,
                    title = "Identidade visual",
                    description = "Cor principal e logo que aparecem no app do participante.",
                    icon = Icons.Default.Palette
                )

                Text(
                    text = "Cor principal do programa",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )

                // Anel + check no selecionado (antes o estado dependia só do anel de 3dp) e
                // semântica de RadioButton para o leitor de tela anunciar cor e seleção.
                FlowRow(
                    modifier = Modifier.fillMaxWidth().selectableGroup(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    B2BOnboardingValidator.PRESET_COLORS.forEach { preset ->
                        val swatchColor = runCatching {
                            Color(android.graphics.Color.parseColor(preset))
                        }.getOrDefault(Color(0xFF1A6B5C))
                        val isSelected = primaryColor.equals(preset, ignoreCase = true)
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(swatchColor)
                                .border(
                                    width = if (isSelected) 3.dp else 1.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                                    shape = CircleShape
                                )
                                .selectable(
                                    selected = isSelected,
                                    role = Role.RadioButton,
                                    onClick = { primaryColor = preset }
                                )
                                .semantics {
                                    contentDescription = if (isSelected) {
                                        "Cor principal selecionada: $preset"
                                    } else {
                                        "Selecionar cor principal $preset"
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = if (swatchColor.luminance() > 0.55f) Color.Black else Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = primaryColor,
                    onValueChange = { if (it.length <= 7) primaryColor = it },
                    label = { Text("Código hexadecimal") },
                    supportingText = { Text("#RRGGBB · ex.: #1A6B5C") },
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
                                label = { Text("URL da logo") },
                                supportingText = { Text("Opcional · apenas links https://") },
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
                            OnboardingSectionHeader(
                                number = 6,
                                title = "Comprovantes privados",
                                description = "${currentProofs.size} de 2 enviados · PDF ou imagem de até 5 MB.",
                                icon = Icons.Default.Lock
                            )
                            Text(
                                text = "Armazenados em cofre privado criptografado somente durante a análise e expurgados automaticamente após aprovação ou recusa.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                B2BOnboardingValidator.PROOF_DOC_TYPE_OPTIONS.forEach { docOpt ->
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

private val ONBOARDING_STEP_TITLES = listOf(
    "Perfil do programa",
    "Identificação da empresa",
    "Responsável",
    "Conselho profissional",
    "Identidade visual",
    "Comprovantes e termos"
)

private val ONBOARDING_STEP_HINTS = listOf(
    "Toque em um modelo para confirmar o perfil do programa.",
    "Informe razão social, nome do programa, CNPJ válido e cidade/UF.",
    "Preencha o responsável, o vínculo e o telefone com DDD.",
    "Selecione o conselho ou marque \"Sem conselho\" para seguir.",
    "Confirme a cor principal e, se quiser, envie a logo.",
    "Anexe até 2 comprovantes e aceite os termos para enviar."
)

/**
 * Cabeçalho único das seis seções do onboarding: mesmo ícone, número, cor e descrição
 * curta em todas elas, em vez dos títulos soltos que competiam com os campos.
 */
@Composable
private fun OnboardingSectionHeader(
    number: Int,
    title: String,
    description: String,
    icon: ImageVector
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = "Etapa $number de 6. $title. $description"
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = "$number. $title",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
