package br.com.bragasaude.ui.b2b

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.bragasaude.domain.b2b.ABUSE_REPORT_REASONS
import br.com.bragasaude.domain.b2b.B2BOnboardingValidator
import br.com.bragasaude.domain.b2b.B2BThemeEngine
import br.com.bragasaude.domain.b2b.EnrollmentAppointmentsFeedDto
import br.com.bragasaude.domain.b2b.EnrollmentRemindersFeedDto
import br.com.bragasaude.domain.b2b.PartnerApprovalStatus
import br.com.bragasaude.domain.b2b.PartnerProgramDto
import br.com.bragasaude.ui.auth.PartnerLoginPreview
import br.com.bragasaude.domain.b2b.PartnerReminderType
import br.com.bragasaude.domain.b2b.ProgramExperienceConfigDto
import br.com.bragasaude.domain.b2b.SharedCategoryOption
import br.com.bragasaude.domain.b2b.TemplateCatalogItemDto
import br.com.bragasaude.ui.components.BragaAlertDialog
import br.com.bragasaude.ui.components.BragaBottomSheet
import java.io.File

enum class PartnerOwnerTab(val title: String) {
    WALLET("Carteira"),
    STUDIO("Estúdio"),
    PREVIEW("Prévia")
}

private enum class PreviewSurfaceMode(val label: String) {
    LOGIN("Entrada"),
    HOME("Início do Cliente"),
    INVITE_CARD("Cartão de Entrada"),
    PDF("Resumo PDF")
}

@Composable
fun FirstAccessActivationChecklistCard(
    programInfo: PartnerProgramDto,
    brandPrimary: Color,
    brandAccent: Color,
    softSurface: Color,
    softBorder: BorderStroke,
    onOpenOnboarding: () -> Unit,
    onOpenStudio: () -> Unit,
    onOpenQr: () -> Unit
) {
    val approved = programInfo.parsedStatus == PartnerApprovalStatus.APPROVED
    val customized = programInfo.experienceConfig !=
        ProgramExperienceConfigDto.defaultForTemplate(programInfo.templateKey)
    val hasParticipants = programInfo.activeParticipants > 0

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = softSurface),
        border = softBorder
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Primeiros passos do seu espaço parceiro",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = brandAccent
            )
            ChecklistActionRow(
                done = approved,
                title = "1. Cadastro CNPJ e aprovação",
                subtitle = if (approved) "Empresa validada e convite liberado." else "Conclua os dados e termos do programa.",
                brandPrimary = brandPrimary,
                onClick = onOpenOnboarding
            )
            ChecklistActionRow(
                done = customized,
                title = "2. Personalizar módulos e boas-vindas no Estúdio",
                subtitle = "Escolha template, switches de rotina e Recepcionista IA.",
                brandPrimary = brandPrimary,
                onClick = onOpenStudio
            )
            ChecklistActionRow(
                done = hasParticipants,
                title = "3. Compartilhar convite, QR Code ou placa PDF",
                subtitle = if (hasParticipants) "${programInfo.activeParticipants} participante(s) ativos." else "Convide participantes sem exposição de documentos.",
                brandPrimary = brandPrimary,
                enabled = approved && !programInfo.inviteCode.isNullOrBlank(),
                onClick = onOpenQr
            )
        }
    }
}

@Composable
private fun ChecklistActionRow(
    done: Boolean,
    title: String,
    subtitle: String,
    brandPrimary: Color,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = null,
            tint = if (done) brandPrimary else MaterialTheme.colorScheme.outlineVariant,
            modifier = Modifier.size(20.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun PartnerStudioTabContent(
    programInfo: PartnerProgramDto?,
    draftConfig: ProgramExperienceConfigDto,
    savedConfig: ProgramExperienceConfigDto,
    templates: List<TemplateCatalogItemDto>,
    isSaving: Boolean,
    brandPrimary: Color,
    brandOnPrimary: Color,
    brandAccent: Color,
    onDraftChange: (ProgramExperienceConfigDto) -> Unit,
    onOpenOnboarding: () -> Unit,
    onOpenSimulator: () -> Unit,
    onSaveChanges: () -> Unit
) {
    var pendingTemplateConfirm by remember { mutableStateOf<TemplateCatalogItemDto?>(null) }

    val catalog = remember(templates) {
        if (templates.isNotEmpty()) {
            templates
        } else {
            B2BOnboardingValidator.TEMPLATE_OPTIONS.map { opt ->
                TemplateCatalogItemDto(
                    templateKey = opt.key,
                    title = opt.labelPtBr,
                    subtitle = "Configuração sugerida do perfil",
                    sampleSafeReminder = "Lembrete de rotina e preparo pré-consulta",
                    defaultConfig = ProgramExperienceConfigDto.defaultForTemplate(opt.key)
                )
            }
        }
    }

    pendingTemplateConfirm?.let { tpl ->
        BragaAlertDialog(
            onDismissRequest = { pendingTemplateConfirm = null },
            title = { Text("Aplicar padrões de ${tpl.title}?") },
            text = {
                Text("Isso substituirá os textos e switches atuais do rascunho pelos padrões desse perfil. Você poderá ajustar cada item antes de salvar.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDraftChange(tpl.defaultConfig.copy(templateKey = tpl.templateKey))
                        pendingTemplateConfirm = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = brandPrimary,
                        contentColor = brandOnPrimary
                    )
                ) {
                    Text("Aplicar template")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingTemplateConfirm = null }) {
                    Text("Cancelar")
                }
            }
        )
    }

    val isDirty = draftConfig != savedConfig
    val isExternalWithoutValidUrl = draftConfig.features.appointmentsEnabled &&
        draftConfig.appointmentsPolicy.bookingMode == "EXTERNAL_LINK_ONLY" &&
        !draftConfig.appointmentsPolicy.externalBookingUrl.orEmpty().trim().startsWith("https://")
    val isFormValid = draftConfig.welcomeHeadline.trim().length in 2..140 &&
        draftConfig.welcomeMessage.trim().length in 2..500 &&
        draftConfig.enabledMetrics.isNotEmpty() &&
        !isExternalWithoutValidUrl

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "1. Modelo inicial por perfil (7 templates)",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(catalog, key = { it.templateKey }) { tpl ->
                        val selected = draftConfig.templateKey.equals(tpl.templateKey, ignoreCase = true)
                        Card(
                            modifier = Modifier
                                .width(220.dp)
                                .clickable {
                                    if (!selected) {
                                        if (draftConfig != ProgramExperienceConfigDto.defaultForTemplate(draftConfig.templateKey)) {
                                            pendingTemplateConfirm = tpl
                                        } else {
                                            onDraftChange(tpl.defaultConfig.copy(templateKey = tpl.templateKey))
                                        }
                                    }
                                },
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (selected) brandPrimary.copy(alpha = 0.12f)
                                else MaterialTheme.colorScheme.surface
                            ),
                            border = BorderStroke(
                                width = if (selected) 2.dp else 1.dp,
                                color = if (selected) brandPrimary else MaterialTheme.colorScheme.outlineVariant
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = tpl.title,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = if (selected) brandAccent else MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = tpl.subtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "2. Boas-vindas & Identidade visual",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = onOpenOnboarding) {
                            Text("Logo / Cor / CNPJ")
                        }
                    }
                    OutlinedTextField(
                        value = draftConfig.welcomeHeadline,
                        onValueChange = {
                            if (it.length <= 140) onDraftChange(draftConfig.copy(welcomeHeadline = it))
                        },
                        label = { Text("Título de boas-vindas (até 140 caracteres)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    OutlinedTextField(
                        value = draftConfig.welcomeMessage,
                        onValueChange = {
                            if (it.length <= 500) onDraftChange(draftConfig.copy(welcomeMessage = it))
                        },
                        label = { Text("Mensagem aos participantes (Não-SaMD)") },
                        minLines = 2,
                        maxLines = 4,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "3. Métricas acompanhadas no programa",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "O participante sempre mantém o controle de escolher quais dessas categorias compartilhar.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(SharedCategoryOption.entries) { opt ->
                            val active = draftConfig.enabledMetrics.contains(opt.wireKey)
                            FilterChip(
                                selected = active,
                                onClick = {
                                    val next = if (active && draftConfig.enabledMetrics.size > 1) {
                                        draftConfig.enabledMetrics - opt.wireKey
                                    } else if (!active) {
                                        draftConfig.enabledMetrics + opt.wireKey
                                    } else {
                                        draftConfig.enabledMetrics
                                    }
                                    onDraftChange(draftConfig.copy(enabledMetrics = next))
                                },
                                label = { Text(opt.titlePtBr) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = brandPrimary,
                                    selectedLabelColor = brandOnPrimary
                                )
                            )
                        }
                    }
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "4. Módulos No-Code do Programa",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )

                    StudioFeatureSwitchRow(
                        title = "Lembretes de rotina do parceiro",
                        subtitle = "Envio de orientações Não-SaMD e avisos com a marca do programa.",
                        checked = draftConfig.features.partnerRemindersEnabled,
                        onCheckedChange = { checked ->
                            onDraftChange(
                                draftConfig.copy(
                                    features = draftConfig.features.copy(partnerRemindersEnabled = checked)
                                )
                            )
                        }
                    )

                    StudioFeatureSwitchRow(
                        title = "Agendamento in-app",
                        subtitle = "Janelas de atendimento presencial/remoto ou link externo HTTPS.",
                        checked = draftConfig.features.appointmentsEnabled,
                        onCheckedChange = { checked ->
                            onDraftChange(
                                draftConfig.copy(
                                    features = draftConfig.features.copy(appointmentsEnabled = checked)
                                )
                            )
                        }
                    )

                    AnimatedVisibility(visible = draftConfig.features.appointmentsEnabled) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf("IN_PERSON" to "Presencial", "REMOTE" to "Remoto").forEach { (key, lbl) ->
                                    val selected = draftConfig.appointmentsPolicy.allowedModalities.contains(key)
                                    FilterChip(
                                        selected = selected,
                                        onClick = {
                                            val cur = draftConfig.appointmentsPolicy.allowedModalities
                                            val next = if (selected && cur.size > 1) cur - key else if (!selected) cur + key else cur
                                            onDraftChange(
                                                draftConfig.copy(
                                                    appointmentsPolicy = draftConfig.appointmentsPolicy.copy(allowedModalities = next)
                                                )
                                            )
                                        },
                                        label = { Text(lbl) }
                                    )
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(
                                    "REQUEST_CONFIRMATION" to "Solicitar no app",
                                    "EXTERNAL_LINK_ONLY" to "Link HTTPS externo"
                                ).forEach { (modeKey, modeLbl) ->
                                    FilterChip(
                                        selected = draftConfig.appointmentsPolicy.bookingMode == modeKey,
                                        onClick = {
                                            onDraftChange(
                                                draftConfig.copy(
                                                    appointmentsPolicy = draftConfig.appointmentsPolicy.copy(bookingMode = modeKey)
                                                )
                                            )
                                        },
                                        label = { Text(modeLbl) }
                                    )
                                }
                            }
                            OutlinedTextField(
                                value = draftConfig.appointmentsPolicy.availabilitiesSummary,
                                onValueChange = {
                                    if (it.length <= 160) {
                                        onDraftChange(
                                            draftConfig.copy(
                                                appointmentsPolicy = draftConfig.appointmentsPolicy.copy(availabilitiesSummary = it)
                                            )
                                        )
                                    }
                                },
                                label = { Text("Resumo de disponibilidade") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = draftConfig.appointmentsPolicy.externalBookingUrl.orEmpty(),
                                onValueChange = { raw ->
                                    if (raw.length <= 512) {
                                        onDraftChange(
                                            draftConfig.copy(
                                                appointmentsPolicy = draftConfig.appointmentsPolicy.copy(
                                                    externalBookingUrl = raw.takeIf { it.isNotBlank() }
                                                )
                                            )
                                        )
                                    }
                                },
                                isError = isExternalWithoutValidUrl,
                                label = { Text("URL externa HTTPS (opcional / obrigatória no modo link)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    StudioFeatureSwitchRow(
                        title = "Chat direto assíncrono com o responsável",
                        subtitle = "Canal delimitado por horário e SLA (separado do chat familiar).",
                        checked = draftConfig.features.directPartnerChatEnabled,
                        onCheckedChange = { checked ->
                            onDraftChange(
                                draftConfig.copy(
                                    features = draftConfig.features.copy(
                                        directPartnerChatEnabled = checked,
                                        aiReceptionistEnabled = if (checked) draftConfig.features.aiReceptionistEnabled else false
                                    )
                                )
                            )
                        }
                    )

                    AnimatedVisibility(visible = draftConfig.features.directPartnerChatEnabled) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedTextField(
                                value = draftConfig.directChatPolicy.serviceHoursLabel,
                                onValueChange = {
                                    if (it.length <= 80) {
                                        onDraftChange(
                                            draftConfig.copy(
                                                directChatPolicy = draftConfig.directChatPolicy.copy(serviceHoursLabel = it)
                                            )
                                        )
                                    }
                                },
                                label = { Text("Horário de atendimento") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = draftConfig.directChatPolicy.responseSlaLabel,
                                onValueChange = {
                                    if (it.length <= 80) {
                                        onDraftChange(
                                            draftConfig.copy(
                                                directChatPolicy = draftConfig.directChatPolicy.copy(responseSlaLabel = it)
                                            )
                                        )
                                    }
                                },
                                label = { Text("Prazo esperado de resposta") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            StudioFeatureSwitchRow(
                                title = "Recepcionista IA no Chat Direto",
                                subtitle = "Recebe dúvidas sobre horários/FAQ com guarda Não-SaMD.",
                                checked = draftConfig.features.aiReceptionistEnabled,
                                onCheckedChange = { checked ->
                                    onDraftChange(
                                        draftConfig.copy(
                                            features = draftConfig.features.copy(
                                                aiReceptionistEnabled = checked,
                                                directPartnerChatEnabled = if (checked) true else draftConfig.features.directPartnerChatEnabled
                                            )
                                        )
                                    )
                                }
                            )
                            if (draftConfig.features.aiReceptionistEnabled) {
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    items(
                                        listOf(
                                            "OUT_OF_HOURS_ONLY" to "Fora do expediente",
                                            "ALWAYS_FIRST" to "Sempre no 1º contato",
                                            "MANUAL_ONLY" to "Sob demanda"
                                        )
                                    ) { (mKey, mLbl) ->
                                        FilterChip(
                                            selected = draftConfig.aiReceptionistPolicy.activationMode == mKey,
                                            onClick = {
                                                onDraftChange(
                                                    draftConfig.copy(
                                                        aiReceptionistPolicy = draftConfig.aiReceptionistPolicy.copy(activationMode = mKey)
                                                    )
                                                )
                                            },
                                            label = { Text(mLbl) }
                                        )
                                    }
                                }
                                OutlinedTextField(
                                    value = draftConfig.aiReceptionistPolicy.personaName,
                                    onValueChange = {
                                        if (it.length <= 60) {
                                            onDraftChange(
                                                draftConfig.copy(
                                                    aiReceptionistPolicy = draftConfig.aiReceptionistPolicy.copy(personaName = it)
                                                )
                                            )
                                        }
                                    },
                                    label = { Text("Nome da Recepcionista Virtual") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                OutlinedTextField(
                                    value = draftConfig.aiReceptionistPolicy.greetingMessage,
                                    onValueChange = {
                                        if (it.length <= 400) {
                                            onDraftChange(
                                                draftConfig.copy(
                                                    aiReceptionistPolicy = draftConfig.aiReceptionistPolicy.copy(greetingMessage = it)
                                                )
                                            )
                                        }
                                    },
                                    label = { Text("Saudação inicial da recepcionista") },
                                    minLines = 2,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                OutlinedTextField(
                                    value = draftConfig.aiReceptionistPolicy.partnerFaqKnowledge,
                                    onValueChange = {
                                        if (it.length <= 1500) {
                                            onDraftChange(
                                                draftConfig.copy(
                                                    aiReceptionistPolicy = draftConfig.aiReceptionistPolicy.copy(partnerFaqKnowledge = it)
                                                )
                                            )
                                        }
                                    },
                                    label = { Text("Orientações e FAQ (até 1500 chars — sem posologia)") },
                                    minLines = 2,
                                    maxLines = 4,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }

                            OutlinedButton(
                                onClick = onOpenSimulator,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Testar minha recepcionista agora", fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }

                    StudioFeatureSwitchRow(
                        title = "Assistente educativo no programa",
                        subtitle = "Disponibiliza apoio educativo de IA para a rotina do participante.",
                        checked = draftConfig.features.assistantChatEnabled,
                        onCheckedChange = { checked ->
                            onDraftChange(
                                draftConfig.copy(
                                    features = draftConfig.features.copy(assistantChatEnabled = checked)
                                )
                            )
                        }
                    )

                    StudioFeatureSwitchRow(
                        title = "Apoio Familiar no programa (opt-in)",
                        subtitle = "Familiar autorizado acompanha lembretes sem expor conversas privadas.",
                        checked = draftConfig.features.familyLinkEnabled,
                        onCheckedChange = { checked ->
                            onDraftChange(
                                draftConfig.copy(
                                    features = draftConfig.features.copy(familyLinkEnabled = checked)
                                )
                            )
                        }
                    )

                    StudioFeatureSwitchRow(
                        title = "Resumo pré-consulta em PDF",
                        subtitle = "Permite que o paciente gere e revise PDF local da sua rotina.",
                        checked = draftConfig.features.preConsultationPdfEnabled,
                        onCheckedChange = { checked ->
                            onDraftChange(
                                draftConfig.copy(
                                    features = draftConfig.features.copy(preConsultationPdfEnabled = checked)
                                )
                            )
                        }
                    )
                }
            }
        }

        item {
            Button(
                onClick = onSaveChanges,
                enabled = isDirty && isFormValid && !isSaving && programInfo != null,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = brandPrimary,
                    contentColor = brandOnPrimary
                )
            ) {
                Text(
                    text = if (isSaving) "Aplicando mudanças..." else if (isDirty) "Aplicar mudanças no programa" else "Configuração salva",
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun StudioFeatureSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
fun PartnerPreviewTabContent(
    programName: String,
    companyName: String,
    logoUrl: String?,
    primaryColorHex: String?,
    draftConfig: ProgramExperienceConfigDto,
    brandPrimary: Color,
    brandOnPrimary: Color,
    brandAccent: Color,
    softSurface: Color,
    softBorder: BorderStroke,
    onOpenSimulator: () -> Unit
) {
    var surfaceMode by rememberSaveable { mutableStateOf(PreviewSurfaceMode.LOGIN) }
    var simulateDarkTheme by rememberSaveable { mutableStateOf(false) }
    var simulateLargeText by rememberSaveable { mutableStateOf(false) }

    val tokens = remember(primaryColorHex) {
        B2BThemeEngine.resolveAccessiblePalette(primaryColorHex)
    }
    val previewSurface = if (simulateDarkTheme) Color(0xFF0F1A16) else Color(0xFFF8FAFC)
    val previewCardBg = if (simulateDarkTheme) Color(0xFF172721) else softSurface
    val previewAccent = if (simulateDarkTheme) Color(android.graphics.Color.parseColor(tokens.accentOnDarkHex)) else brandAccent

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)
                )
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(Icons.Default.Visibility, contentDescription = null, tint = brandAccent)
                    Text(
                        text = "Prévia ao vivo do que seus participantes verão (usando dados demonstrativos e os toggles atuais do Estúdio).",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(PreviewSurfaceMode.entries) { mode ->
                        FilterChip(
                            selected = surfaceMode == mode,
                            onClick = { surfaceMode = mode },
                            label = { Text(mode.label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = brandPrimary,
                                selectedLabelColor = brandOnPrimary
                            )
                        )
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { simulateDarkTheme = !simulateDarkTheme }, modifier = Modifier.weight(1f)) {
                            Text(if (simulateDarkTheme) "Ver prévia em modo claro" else "Ver prévia em modo escuro")
                        }
                        FilterChip(selected = simulateLargeText, onClick = { simulateLargeText = !simulateLargeText },
                            label = { Text("Texto ampliado") })
                    }
                    OutlinedButton(onClick = onOpenSimulator, shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Testar Recepcionista IA")
                    }
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = previewSurface),
                border = BorderStroke(1.5.dp, brandPrimary.copy(alpha = 0.45f))
            ) {
                val previewScheme = if (simulateDarkTheme) {
                    darkColorScheme(background = previewSurface, surface = Color(0xFF182822))
                } else lightColorScheme(background = previewSurface, surface = Color.White)
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density,
                    density.fontScale * if (simulateLargeText) 1.3f else 1f)) {
                    MaterialTheme(colorScheme = previewScheme) {
                        PartnerBrandTheme(primaryColorHex) {
                            Box(modifier = Modifier.padding(16.dp)) {
                                when (surfaceMode) {
                                    PreviewSurfaceMode.LOGIN -> PartnerLoginPreview()
                                    PreviewSurfaceMode.HOME -> {
                                        ParticipantExperienceContent(
                                            programName = programName.ifBlank { "Seu Programa" },
                                            companyName = companyName.ifBlank { "Sua Empresa" },
                                            logoUrl = logoUrl,
                                            experienceConfig = draftConfig,
                                            sharedCategories = draftConfig.enabledMetrics,
                                            coParticipatedCount = 2,
                                            brandPrimary = brandPrimary,
                                            brandOnPrimary = brandOnPrimary,
                                            brandAccent = previewAccent,
                                            softSurface = previewCardBg,
                                            softBorder = softBorder,
                                            isGeneratingPdf = false,
                                            generatedPdf = null,
                                            isPreview = true,
                                            onOpenConsentSheet = {},
                                            onGeneratePdf = { _, _ -> },
                                            onPreviewPdf = {},
                                            onSharePdf = {},
                                            onOpenAiPreview = onOpenSimulator
                                        )
                                    }
                                    PreviewSurfaceMode.INVITE_CARD -> {
                                        Card(
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(16.dp),
                                            colors = CardDefaults.cardColors(containerColor = brandPrimary)
                                        ) {
                                            Column(
                                                modifier = Modifier.padding(18.dp),
                                                verticalArrangement = Arrangement.spacedBy(10.dp)
                                            ) {
                                                Text(
                                                    text = "CONVITE DO PROGRAMA",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = brandOnPrimary.copy(alpha = 0.8f),
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Text(
                                                    text = programName.ifBlank { "Seu Programa" },
                                                    style = MaterialTheme.typography.titleLarge,
                                                    color = brandOnPrimary,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Text(
                                                    text = draftConfig.welcomeHeadline,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = brandOnPrimary
                                                )
                                                Text(
                                                    text = "${companyName.ifBlank { "Sua Empresa" }} · Você decide passo a passo o que compartilhar.",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = brandOnPrimary.copy(alpha = 0.9f)
                                                )
                                            }
                                        }
                                    }
                                    PreviewSurfaceMode.PDF -> {
                                        Card(
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(16.dp),
                                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                                        ) {
                                            Column(
                                                modifier = Modifier.padding(16.dp),
                                                verticalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Text(
                                                    text = if (draftConfig.features.preConsultationPdfEnabled) B2BThemeEngine.PDF_PRECONSULTATION_TITLE
                                                    else "Exportação PDF desativada neste programa",
                                                    style = MaterialTheme.typography.titleSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = previewAccent
                                                )
                                                Text(
                                                    text = if (draftConfig.features.preConsultationPdfEnabled) {
                                                        "Cabeçalho com a marca ${programName.ifBlank { "do programa" }} e métricas: ${draftConfig.enabledMetrics.mapNotNull { SharedCategoryOption.fromWire(it)?.titlePtBr }.joinToString(", ")}. Documentos pessoais nunca são anexados."
                                                    } else {
                                                        "Habilite o switch 'Resumo pré-consulta em PDF' no Estúdio para liberar este card."
                                                    },
                                                    style = MaterialTheme.typography.bodySmall
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ParticipantExperienceContent(
    programName: String,
    companyName: String,
    logoUrl: String?,
    experienceConfig: ProgramExperienceConfigDto,
    sharedCategories: List<String>,
    coParticipatedCount: Int,
    brandPrimary: Color,
    brandOnPrimary: Color,
    brandAccent: Color,
    softSurface: Color,
    softBorder: BorderStroke?,
    isGeneratingPdf: Boolean,
    generatedPdf: File?,
    isPreview: Boolean = false,
    remindersFeed: EnrollmentRemindersFeedDto? = null,
    acknowledgingReminderId: String? = null,
    isUpdatingMute: Boolean = false,
    onAcknowledgeReminder: (String) -> Unit = {},
    onToggleMute: (Boolean) -> Unit = {},
    onOpenConsentSheet: () -> Unit,
    onGeneratePdf: (Int, Set<String>) -> Unit,
    onPreviewPdf: (File) -> Unit,
    onSharePdf: (File) -> Unit,
    onOpenAiPreview: () -> Unit = {},
    onOpenReportSheet: () -> Unit = {},
    appointmentsFeed: EnrollmentAppointmentsFeedDto? = null,
    updatingAppointmentId: String? = null,
    onOpenRequestAppointmentSheet: () -> Unit = {},
    onCancelAppointment: (String) -> Unit = {},
    onOpenDirectChatSheet: () -> Unit = {}
) {
    val missingConsent = remember(experienceConfig, sharedCategories) {
        B2BOnboardingValidator.computeMissingConsentCategories(experienceConfig, sharedCategories)
    }
    var selectedPdfDays by remember { mutableStateOf(30) }
    val enabledOptions = remember(experienceConfig.enabledMetrics) {
        experienceConfig.enabledMetrics.mapNotNull { SharedCategoryOption.fromWire(it) }
            .ifEmpty { SharedCategoryOption.entries }
    }
    var selectedPdfCategories by remember(sharedCategories, enabledOptions) {
        val allowedKeys = enabledOptions.map { it.wireKey }.toSet()
        val initial = sharedCategories.filter { it in allowedKeys }.toSet()
        mutableStateOf(initial.ifEmpty { allowedKeys })
    }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // Identificacao explicita multi-clinica (B2B-16)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = brandPrimary.copy(alpha = 0.10f)),
            border = BorderStroke(1.5.dp, brandPrimary.copy(alpha = 0.45f))
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PartnerIdentityLockup(companyName, programName, logoUrl, Modifier.fillMaxWidth())
                if (coParticipatedCount > 1) {
                    Text("$coParticipatedCount programas vinculados", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(
                    text = experienceConfig.welcomeHeadline,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = brandAccent
                )
                Text(
                    text = experienceConfig.welcomeMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (!isPreview && missingConsent.isNotEmpty()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpenConsentSheet),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Novos módulos disponíveis em $programName — revisar permissões",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Disponível para autorizar se desejar: ${missingConsent.joinToString(", ") { it.titlePtBr }}.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        if (experienceConfig.features.partnerRemindersEnabled) {
            if (isPreview) {
                ProgramModuleHighlightCard(
                    icon = Icons.Default.NotificationsActive,
                    title = "Lembretes e orientações de $programName",
                    body = "Avisos de rotina emitidos por $companyName, sempre separados dos seus alarmes pessoais de medicação.",
                    accent = brandAccent
                )
            } else {
                ProgramRemindersParticipantCard(
                    programName = programName,
                    companyName = companyName,
                    feed = remindersFeed,
                    acknowledgingReminderId = acknowledgingReminderId,
                    isUpdatingMute = isUpdatingMute,
                    brandPrimary = brandPrimary,
                    brandOnPrimary = brandOnPrimary,
                    brandAccent = brandAccent,
                    onAcknowledgeReminder = onAcknowledgeReminder,
                    onToggleMute = onToggleMute
                )
            }
        }

        if (experienceConfig.features.appointmentsEnabled) {
            if (isPreview) {
                val modalitiesPt = experienceConfig.appointmentsPolicy.allowedModalities.joinToString(" e ") {
                    if (it == "REMOTE") "Remoto" else "Presencial"
                }
                ProgramModuleHighlightCard(
                    icon = Icons.Default.CalendarMonth,
                    title = "Agendamento com $programName ($modalitiesPt)",
                    body = "Disponibilidade informada: ${experienceConfig.appointmentsPolicy.availabilitiesSummary}.",
                    accent = brandAccent
                )
            } else {
                ProgramAppointmentsParticipantCard(
                    programName = programName,
                    policy = experienceConfig.appointmentsPolicy,
                    feed = appointmentsFeed,
                    updatingAppointmentId = updatingAppointmentId,
                    brandPrimary = brandPrimary,
                    brandOnPrimary = brandOnPrimary,
                    brandAccent = brandAccent,
                    onOpenRequestSheet = onOpenRequestAppointmentSheet,
                    onCancelAppointment = onCancelAppointment
                )
            }
        }

        if (experienceConfig.features.directPartnerChatEnabled) {
            val badge = if (experienceConfig.features.aiReceptionistEnabled) {
                "Recepcionista Virtual ativa (${experienceConfig.aiReceptionistPolicy.personaName}). "
            } else {
                ""
            }
            ProgramModuleHighlightCard(
                icon = Icons.Default.SupportAgent,
                title = "Falar com $programName",
                body = "$badge${experienceConfig.directChatPolicy.serviceHoursLabel} · ${experienceConfig.directChatPolicy.responseSlaLabel}.",
                accent = brandAccent,
                actionLabel = when {
                    isPreview && experienceConfig.features.aiReceptionistEnabled -> "Simular recepcionista"
                    !isPreview -> "Abrir chat direto"
                    else -> null
                },
                onAction = {
                    if (isPreview) onOpenAiPreview() else onOpenDirectChatSheet()
                }
            )
        }

        if (experienceConfig.features.familyLinkEnabled) {
            ProgramModuleHighlightCard(
                icon = Icons.Default.VerifiedUser,
                title = "Apoio Familiar conectado ao programa",
                body = "Você pode permitir que seu familiar de apoio acompanhe avisos deste programa, mantendo o chat familiar privado isolado.",
                accent = brandAccent
            )
        }

        if (experienceConfig.features.assistantChatEnabled) {
            ProgramModuleHighlightCard(
                icon = Icons.Default.AutoAwesome,
                title = "Apoio para sua rotina",
                body = "Assistente educativo ativo para dúvidas gerais de uso e rotina neste espaço.",
                accent = brandAccent
            )
        }

        // Controle granular de compartilhamento (sempre visivel para revogacao imediata)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = softSurface),
            border = softBorder ?: BorderStroke(1.dp, brandAccent.copy(alpha = 0.3f))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = brandAccent)
                    Text(
                        text = "Compartilhamento com $programName",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = brandAccent
                    )
                }
                val labels = sharedCategories.mapNotNull { SharedCategoryOption.fromWire(it)?.titlePtBr }
                Text(
                    text = if (labels.isEmpty()) {
                        "Você ainda não autorizou categorias in-app para $companyName."
                    } else {
                        "Categorias visíveis a $companyName: ${labels.joinToString(", ")}."
                    },
                    style = MaterialTheme.typography.bodySmall
                )
                if (!isPreview) {
                    OutlinedButton(
                        onClick = onOpenConsentSheet,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Gerenciar permissões ou encerrar vínculo", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        if (experienceConfig.features.preConsultationPdfEnabled) {
            Card(
                modifier = Modifier.fillMaxWidth(),
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
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(7, 14, 30).forEach { days ->
                            FilterChip(
                                selected = selectedPdfDays == days,
                                onClick = { selectedPdfDays = days },
                                label = { Text("Últimos $days dias") },
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
                    enabledOptions.forEach { opt ->
                        val checked = selectedPdfCategories.contains(opt.wireKey)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedPdfCategories = if (checked) {
                                        selectedPdfCategories - opt.wireKey
                                    } else {
                                        selectedPdfCategories + opt.wireKey
                                    }
                                }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = checked,
                                onCheckedChange = { nowChecked ->
                                    selectedPdfCategories = if (nowChecked) {
                                        selectedPdfCategories + opt.wireKey
                                    } else {
                                        selectedPdfCategories - opt.wireKey
                                    }
                                },
                                colors = CheckboxDefaults.colors(checkedColor = brandPrimary)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(opt.titlePtBr, style = MaterialTheme.typography.bodyMedium)
                        }
                    }

                    Button(
                        onClick = { onGeneratePdf(selectedPdfDays, selectedPdfCategories) },
                        enabled = !isPreview && !isGeneratingPdf && selectedPdfCategories.isNotEmpty(),
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
                            text = if (isPreview) "Disponível no app do participante" else if (isGeneratingPdf) "Gerando resumo em PDF..." else "Gerar PDF para revisão",
                            fontWeight = FontWeight.Bold
                        )
                    }

                    if (!isPreview && generatedPdf != null) {
                        HorizontalDivider()
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = brandAccent)
                            Text(
                                text = "PDF gerado: ${generatedPdf.name}",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = { onPreviewPdf(generatedPdf) },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Visualizar PDF")
                            }
                            Button(
                                onClick = { onSharePdf(generatedPdf) },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = brandPrimary,
                                    contentColor = brandOnPrimary
                                )
                            ) {
                                Text("Compartilhar", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        if (!isPreview) {
            OutlinedButton(
                onClick = onOpenReportSheet,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Silenciar ou reportar programa")
            }
        }
        if (isPreview) PartnerTechnologyCredit()
    }
}

@Composable
internal fun ProgramRemindersParticipantCard(
    programName: String,
    companyName: String,
    feed: EnrollmentRemindersFeedDto?,
    acknowledgingReminderId: String?,
    isUpdatingMute: Boolean,
    brandPrimary: Color,
    brandOnPrimary: Color,
    brandAccent: Color,
    onAcknowledgeReminder: (String) -> Unit,
    onToggleMute: (Boolean) -> Unit
) {
    val isMuted = feed?.muted == true
    val reminders = feed?.reminders.orEmpty()

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.2.dp, brandPrimary.copy(alpha = 0.35f))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.NotificationsActive,
                        contentDescription = null,
                        tint = brandAccent
                    )
                    Column {
                        Text(
                            text = "Avisos e Lembretes · $programName",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = brandAccent
                        )
                        Text(
                            text = "$companyName · Separados dos alarmes pessoais de medicação",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                FilterChip(
                    selected = !isMuted,
                    onClick = { if (!isUpdatingMute) onToggleMute(!isMuted) },
                    label = { Text(if (isMuted) "Silenciado" else "Alertas ON") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = brandPrimary,
                        selectedLabelColor = brandOnPrimary
                    )
                )
            }

            if (reminders.isEmpty()) {
                Text(
                    text = "Nenhum lembrete de rotina recebido deste programa até o momento.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                reminders.forEach { rem ->
                    val typeLabel = PartnerReminderType.fromWire(rem.reminderType).labelPtBr
                    val isAcking = acknowledgingReminderId == rem.reminderId
                    val isAcked = !rem.acknowledgedAt.isNullOrBlank()
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "$typeLabel · $programName",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = brandAccent
                                )
                                Text(
                                    text = rem.createdAt.take(10),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                text = rem.title,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = rem.body,
                                style = MaterialTheme.typography.bodySmall
                            )
                            if (isAcked) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = brandAccent,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "Leitura confirmada",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = brandAccent
                                    )
                                }
                            } else {
                                OutlinedButton(
                                    onClick = { onAcknowledgeReminder(rem.reminderId) },
                                    enabled = !isAcking,
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text(
                                        text = if (isAcking) "Confirmando..." else "Confirmar leitura",
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProgramModuleHighlightCard(
    icon: ImageVector,
    title: String,
    body: String,
    accent: Color,
    actionLabel: String? = null,
    onAction: () -> Unit = {}
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
                Text(text = title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            }
            Text(
                text = body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (!actionLabel.isNullOrBlank()) {
                TextButton(onClick = onAction, contentPadding = PaddingValues(0.dp)) {
                    Text(actionLabel, color = accent, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiReceptionistSimulatorSheet(
    programName: String,
    config: ProgramExperienceConfigDto,
    brandPrimary: Color,
    brandOnPrimary: Color,
    onDismiss: () -> Unit
) {
    var question by rememberSaveable { mutableStateOf("") }
    var transcript by remember {
        mutableStateOf(
            listOf(
                config.aiReceptionistPolicy.personaName to config.aiReceptionistPolicy.greetingMessage
            )
        )
    }

    BragaBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "SIMULADOR RECEPCIONISTA IA (SANDBOX)",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = config.aiReceptionistPolicy.personaName,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Teste horários, FAQ e o bloqueio de segurança Não-SaMD antes de ativar para seus participantes.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(
                    listOf(
                        "Qual o horário de atendimento?",
                        "Como agendar retorno?",
                        "Qual a dose de um remédio?"
                    )
                ) { prompt ->
                    AssistChip(
                        onClick = {
                            val reply = B2BOnboardingValidator.simulateAiReceptionistReply(
                                config = config,
                                programName = programName,
                                question = prompt
                            )
                            transcript = transcript + ("Participante (teste)" to prompt) +
                                (config.aiReceptionistPolicy.personaName to reply)
                        },
                        label = { Text(prompt) }
                    )
                }
            }

            transcript.forEach { (speaker, msg) ->
                val isUser = speaker.startsWith("Participante")
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isUser) brandPrimary.copy(alpha = 0.12f)
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = speaker,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(text = msg, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            OutlinedTextField(
                value = question,
                onValueChange = { if (it.length <= 240) question = it },
                label = { Text("Simule uma pergunta de participante") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Fechar")
                }
                Button(
                    onClick = {
                        val q = question.trim()
                        if (q.isNotEmpty()) {
                            val reply = B2BOnboardingValidator.simulateAiReceptionistReply(
                                config = config,
                                programName = programName,
                                question = q
                            )
                            transcript = transcript + ("Participante (teste)" to q) +
                                (config.aiReceptionistPolicy.personaName to reply)
                            question = ""
                        }
                    },
                    enabled = question.isNotBlank(),
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = brandPrimary,
                        contentColor = brandOnPrimary
                    )
                ) {
                    Text("Enviar teste", fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportProgramBottomSheet(
    programName: String,
    isSubmitting: Boolean,
    brandPrimary: Color,
    brandOnPrimary: Color,
    onSubmit: (reason: String, details: String?, muteAlso: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedReason by rememberSaveable {
        mutableStateOf(ABUSE_REPORT_REASONS.first().first)
    }
    var details by rememberSaveable { mutableStateOf("") }
    var muteAlso by rememberSaveable { mutableStateOf(true) }

    BragaBottomSheet(onDismissRequest = { if (!isSubmitting) onDismiss() }) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Reportar programa parceiro",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Se $programName enviou orientações medicamentosas indevidas, spam ou conteúdo inadequado, nossa moderação analisará a denúncia.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            ABUSE_REPORT_REASONS.forEach { (reasonKey, reasonLabel) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(enabled = !isSubmitting) { selectedReason = reasonKey }
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    RadioButton(
                        selected = selectedReason == reasonKey,
                        onClick = { if (!isSubmitting) selectedReason = reasonKey }
                    )
                    Text(
                        text = reasonLabel,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            OutlinedTextField(
                value = details,
                onValueChange = { if (it.length <= 500) details = it },
                label = { Text("Detalhes opcionais (máx. 500 caracteres)") },
                enabled = !isSubmitting,
                minLines = 2,
                maxLines = 4,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Silenciar avisos deste programa agora",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = muteAlso,
                    onCheckedChange = { if (!isSubmitting) muteAlso = it }
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    enabled = !isSubmitting,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Cancelar")
                }
                Button(
                    onClick = {
                        onSubmit(
                            selectedReason,
                            details.trim().takeIf { it.isNotEmpty() },
                            muteAlso
                        )
                    },
                    enabled = !isSubmitting,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = brandPrimary,
                        contentColor = brandOnPrimary
                    )
                ) {
                    Text(
                        text = if (isSubmitting) "Enviando..." else "Enviar denúncia",
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}
