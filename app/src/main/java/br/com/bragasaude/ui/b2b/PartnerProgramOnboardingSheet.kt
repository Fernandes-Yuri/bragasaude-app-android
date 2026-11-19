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
    var existingProgram by remember { mutableStateOf<PartnerProgramDto?>(null) }

    var companyName by rememberSaveable { mutableStateOf("") }
    var programName by rememberSaveable { mutableStateOf("") }
    var cnpj by rememberSaveable { mutableStateOf("") }
    var primaryColor by rememberSaveable {
        mutableStateOf(B2BOnboardingValidator.DEFAULT_PRIMARY_COLOR)
    }
    var logoUrl by rememberSaveable { mutableStateOf("") }
    var showManualLogoUrl by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val loaded = apiClient.getMyPartnerProgram()
        if (loaded != null) {
            existingProgram = loaded
            companyName = loaded.companyName
            programName = loaded.programName
            cnpj = loaded.cnpj.orEmpty()
            primaryColor = B2BOnboardingValidator.normalizeHexColorOrDefault(loaded.primaryColor)
            logoUrl = loaded.logoUrl.orEmpty()
        }
        isLoading = false
    }

    val logoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        val picked = uri ?: return@rememberLauncherForActivityResult
        if (isUploadingLogo || isSaving) return@rememberLauncherForActivityResult
        isUploadingLogo = true
        scope.launch {
            try {
                if (existingProgram == null) {
                    val draftReady = B2BOnboardingValidator.canSaveDraft(
                        companyName = companyName,
                        programName = programName,
                        primaryColor = primaryColor,
                        cnpj = cnpj.takeIf { it.isNotBlank() },
                        logoUrl = null
                    )
                    if (!draftReady) {
                        Toast.makeText(
                            context,
                            "Informe primeiro o nome da empresa e do programa para enviar a logo.",
                            Toast.LENGTH_LONG
                        ).show()
                        return@launch
                    }
                    val created = apiClient.saveMyPartnerProgram(
                        UpsertPartnerProgramRequest(
                            companyName = companyName,
                            programName = programName,
                            cnpj = B2BOnboardingValidator.formatCnpjOrNull(cnpj),
                            primaryColor = B2BOnboardingValidator.normalizeHexColorOrDefault(primaryColor),
                            logoUrl = null
                        )
                    )
                    if (created == null) {
                        Toast.makeText(
                            context,
                            "Salve o rascunho antes de enviar a logo.",
                            Toast.LENGTH_LONG
                        ).show()
                        return@launch
                    }
                    existingProgram = created
                }

                val prepared = B2BLogoMediaHelper.prepareFromUri(context, picked)
                val uploaded = apiClient.uploadPartnerLogo(
                    fileName = prepared.fileName,
                    mimeType = prepared.mimeType,
                    bytes = prepared.bytes
                )
                if (uploaded.isSuccess) {
                    logoUrl = uploaded.logoUrl.orEmpty()
                    uploaded.program?.let { existingProgram = it }
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

            if (!existingProgram?.reviewNote.isNullOrBlank()) {
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
                            text = existingProgram?.reviewNote.orEmpty(),
                            style = MaterialTheme.typography.bodySmall
                        )
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
                                    text = "${prog.activeParticipants} / ${prog.seatLimit} vagas",
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
                OutlinedTextField(
                    value = companyName,
                    onValueChange = { if (it.length <= 160) companyName = it },
                    label = { Text("Nome da empresa ou clínica") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                )

                OutlinedTextField(
                    value = programName,
                    onValueChange = { if (it.length <= 120) programName = it },
                    label = { Text("Nome do programa (ex.: Cuidado Cardio)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                )

                OutlinedTextField(
                    value = cnpj,
                    onValueChange = { if (it.length <= 18) cnpj = it },
                    label = { Text("CNPJ (14 dígitos — obrigatório para aprovação)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
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

                Text(
                    text = "Confirmar o e-mail não aprova a empresa. Os convites e a divulgação são liberados somente após análise da equipe Braga.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                val canSave = B2BOnboardingValidator.canSaveDraft(
                    companyName = companyName,
                    programName = programName,
                    primaryColor = primaryColor,
                    cnpj = cnpj.takeIf { it.isNotBlank() },
                    logoUrl = logoUrl.takeIf { it.isNotBlank() }
                )
                val canSubmit = B2BOnboardingValidator.canSubmitForReview(
                    companyName = companyName,
                    programName = programName,
                    cnpj = cnpj,
                    primaryColor = primaryColor,
                    logoUrl = logoUrl.takeIf { it.isNotBlank() }
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            if (!canSave || isSaving || isUploadingLogo) return@OutlinedButton
                            isSaving = true
                            scope.launch {
                                val saved = apiClient.saveMyPartnerProgram(
                                    UpsertPartnerProgramRequest(
                                        companyName = companyName,
                                        programName = programName,
                                        cnpj = B2BOnboardingValidator.formatCnpjOrNull(cnpj),
                                        primaryColor = B2BOnboardingValidator.normalizeHexColorOrDefault(primaryColor),
                                        logoUrl = logoUrl.trim().takeIf { it.isNotBlank() }
                                    )
                                )
                                isSaving = false
                                if (saved != null) {
                                    existingProgram = saved
                                    Toast.makeText(
                                        context,
                                        "Preparação salva com sucesso.",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                } else {
                                    Toast.makeText(
                                        context,
                                        "Não foi possível salvar agora. Verifique os dados e tente novamente.",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                        },
                        enabled = canSave && !isSaving && !isUploadingLogo,
                        modifier = Modifier.weight(1f).height(52.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text("Salvar rascunho", fontWeight = FontWeight.SemiBold)
                    }

                    Button(
                        onClick = {
                            if (!canSubmit || isSaving || isUploadingLogo) return@Button
                            isSaving = true
                            scope.launch {
                                val saved = apiClient.saveMyPartnerProgram(
                                    UpsertPartnerProgramRequest(
                                        companyName = companyName,
                                        programName = programName,
                                        cnpj = B2BOnboardingValidator.formatCnpjOrNull(cnpj),
                                        primaryColor = B2BOnboardingValidator.normalizeHexColorOrDefault(primaryColor),
                                        logoUrl = logoUrl.trim().takeIf { it.isNotBlank() }
                                    )
                                )
                                val submitted = if (saved != null) {
                                    apiClient.submitMyPartnerProgram()
                                } else null
                                isSaving = false
                                if (submitted != null) {
                                    existingProgram = submitted
                                    Toast.makeText(
                                        context,
                                        "Solicitação enviada para análise da equipe Braga!",
                                        Toast.LENGTH_LONG
                                    ).show()
                                } else {
                                    Toast.makeText(
                                        context,
                                        "Verifique o CNPJ e os dados obrigatórios antes de enviar.",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                        },
                        enabled = canSubmit && !isSaving && !isUploadingLogo && status != PartnerApprovalStatus.APPROVED,
                        modifier = Modifier.weight(1f).height(52.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = parsedColor,
                            contentColor = onParsedPrimary
                        )
                    ) {
                        Text(
                            if (status == PartnerApprovalStatus.PENDING_REVIEW) "Reenviar análise"
                            else "Enviar para aprovação",
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
