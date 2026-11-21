package br.com.bragasaude.ui.b2b

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Monitor
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import br.com.bragasaude.domain.b2b.AppointmentModality
import br.com.bragasaude.domain.b2b.AppointmentsPolicyDto
import br.com.bragasaude.domain.b2b.B2BOnboardingValidator
import br.com.bragasaude.domain.b2b.EnrollmentAppointmentsFeedDto
import br.com.bragasaude.domain.b2b.ProgramAppointmentDto
import br.com.bragasaude.domain.b2b.ProgramAppointmentStatus
import br.com.bragasaude.ui.components.BragaBottomSheet
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val ptBrDateTimeFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm", Locale("pt", "BR"))

private val ptBrDayChipFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("dd/MM (EEE)", Locale("pt", "BR"))

/** O contrato atual não envia duração: exibimos um rótulo honesto em vez de inventar minutos. */
private const val APPOINTMENT_DURATION_FALLBACK_LABEL = "Duração a combinar"

internal fun formatAppointmentIsoPtBr(iso: String?): String? {
    val clean = iso?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    val zoned = runCatching { java.time.OffsetDateTime.parse(clean) }.getOrNull()
        ?: runCatching { Instant.parse(clean).atOffset(java.time.ZoneOffset.UTC) }.getOrNull()
    return zoned
        ?.atZoneSameInstant(ZoneId.of("America/Sao_Paulo"))
        ?.format(ptBrDateTimeFormatter)
        ?: clean
}

/** Rótulo curto de status; sempre acompanhado do ícone (nunca só da cor). */
internal fun appointmentStatusVisualLabel(status: ProgramAppointmentStatus): String = when (status) {
    ProgramAppointmentStatus.PENDING -> "Pendente"
    ProgramAppointmentStatus.CONFIRMED -> "Confirmado"
    ProgramAppointmentStatus.RESCHEDULE_REQUESTED -> "Reagendar"
    ProgramAppointmentStatus.CANCELLED -> "Cancelado"
    ProgramAppointmentStatus.COMPLETED -> "Concluído"
}

/**
 * Linha principal do card: o horário confirmado nunca é confundido com a
 * preferência informada pelo participante.
 */
internal fun appointmentScheduleHeadline(
    status: ProgramAppointmentStatus,
    scheduledAtLabel: String?
): String = when {
    status == ProgramAppointmentStatus.CONFIRMED && scheduledAtLabel != null ->
        "Confirmado para $scheduledAtLabel"
    status == ProgramAppointmentStatus.RESCHEDULE_REQUESTED && scheduledAtLabel != null ->
        "Novo horário sugerido: $scheduledAtLabel"
    status == ProgramAppointmentStatus.COMPLETED && scheduledAtLabel != null ->
        "Concluído em $scheduledAtLabel"
    status == ProgramAppointmentStatus.COMPLETED -> "Atendimento concluído"
    status == ProgramAppointmentStatus.CANCELLED -> "Atendimento cancelado"
    scheduledAtLabel != null -> "Horário definido: $scheduledAtLabel"
    else -> "Aguardando confirmação do programa"
}

/**
 * `requestedPeriod` chega do gateway como token estável (ex.: PROXIMA_SEMANA_MANHA),
 * já em maiúsculas e sem espaços/hifens. Traduzimos apenas códigos conhecidos para
 * texto humano; token desconhecido não vaza cru para a UI.
 */
internal fun appointmentPeriodHumanLabel(requestedPeriod: String?): String? {
    val raw = requestedPeriod?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    val token = raw
        .uppercase(Locale.ROOT)
        .replace(' ', '_')
        .replace('-', '_')
        .replace(Regex("_+"), "_")
        .let(::stripPeriodAccents)
    return when (token) {
        "ESTA_SEMANA" -> "Esta semana"
        "ESTA_SEMANA_MANHA" -> "Esta semana · manhã"
        "ESTA_SEMANA_TARDE" -> "Esta semana · tarde"
        "ESTA_SEMANA_NOITE" -> "Esta semana · noite"
        "PROXIMA_SEMANA" -> "Próxima semana"
        "PROXIMA_SEMANA_MANHA" -> "Próxima semana · manhã"
        "PROXIMA_SEMANA_TARDE" -> "Próxima semana · tarde"
        "PROXIMA_SEMANA_NOITE" -> "Próxima semana · noite"
        "MANHA" -> "Manhã"
        "TARDE" -> "Tarde"
        "NOITE" -> "Noite"
        "QUALQUER_HORARIO", "QUALQUER_DIA", "INDIFERENTE" -> "Qualquer horário"
        else -> null
    }
}

/** Remove acentos para casar tanto PRÓXIMA_SEMANA_MANHÃ quanto PROXIMA_SEMANA_MANHA. */
private fun stripPeriodAccents(value: String): String = buildString(value.length) {
    value.forEach { ch ->
        append(
            when (ch) {
                'Á', 'À', 'Â', 'Ã', 'Ä' -> 'A'
                'É', 'È', 'Ê', 'Ë' -> 'E'
                'Í', 'Ì', 'Î', 'Ï' -> 'I'
                'Ó', 'Ò', 'Ô', 'Õ', 'Ö' -> 'O'
                'Ú', 'Ù', 'Û', 'Ü' -> 'U'
                'Ç' -> 'C'
                else -> ch
            }
        )
    }
}

/**
 * "Preferência informada" exibe o período humano quando o token é conhecido e
 * cai no texto livre legado nos demais casos. O parâmetro `requestedPeriod` já
 * está pronto para a integração do DTO; como o contrato do app ainda não expõe o
 * campo, a ponte [preferenceLine] passa `null` para todos os cards da agenda.
 */
internal fun appointmentPreferenceLine(
    requestedSlotText: String?,
    requestedPeriod: String? = null
): String? {
    val value = appointmentPeriodHumanLabel(requestedPeriod)
        ?: requestedSlotText?.trim()?.takeIf { it.isNotEmpty() }
        ?: return null
    return "Preferência informada: $value"
}

/**
 * Ponte única UI <-> contrato. Quando `ProgramAppointmentDto` ganhar
 * `requestedPeriod: String?` (gateway a7395c1), basta trocar o `null` abaixo pelo
 * campo: os três cards da agenda já passam por aqui.
 */
private fun ProgramAppointmentDto.preferenceLine(): String? =
    appointmentPreferenceLine(
        requestedSlotText = requestedSlotText,
        requestedPeriod = requestedPeriod
    )

internal fun appointmentDurationLabel(): String = APPOINTMENT_DURATION_FALLBACK_LABEL

private fun appointmentStatusIcon(status: ProgramAppointmentStatus): ImageVector = when (status) {
    ProgramAppointmentStatus.PENDING -> Icons.Default.Schedule
    ProgramAppointmentStatus.CONFIRMED -> Icons.Default.CheckCircle
    ProgramAppointmentStatus.RESCHEDULE_REQUESTED -> Icons.Default.Refresh
    ProgramAppointmentStatus.CANCELLED -> Icons.Default.Block
    ProgramAppointmentStatus.COMPLETED -> Icons.Default.Check
}

private fun shareAppointmentsAvailability(context: Context, policy: AppointmentsPolicyDto) {
    val summary = policy.availabilitiesSummary.trim()
    if (summary.isEmpty()) {
        Toast.makeText(
            context,
            "Configure a disponibilidade no Estúdio antes de compartilhar.",
            Toast.LENGTH_LONG
        ).show()
        return
    }
    runCatching {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Disponibilidade de agendamento")
            putExtra(Intent.EXTRA_TEXT, "Agenda de atendimentos no Braga Saúde: $summary")
        }
        context.startActivity(Intent.createChooser(intent, "Compartilhar agenda"))
    }.onFailure {
        Toast.makeText(
            context,
            "Não foi possível abrir o compartilhamento agora.",
            Toast.LENGTH_LONG
        ).show()
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OwnerAppointmentsWalletCard(
    appointments: List<ProgramAppointmentDto>,
    policy: AppointmentsPolicyDto,
    updatingAppointmentId: String?,
    brandPrimary: Color,
    brandAccent: Color,
    softBorder: BorderStroke?,
    onManageAppointment: (ProgramAppointmentDto) -> Unit,
    onOpenStudio: (() -> Unit)? = null
) {
    var pendingOnlyFilter by rememberSaveable { mutableStateOf(true) }
    var showAll by rememberSaveable { mutableStateOf(false) }
    val pendingCount = appointments.count { it.status == "PENDING" }
    val awaitingCount = appointments.count {
        it.status == "PENDING" || it.status == "RESCHEDULE_REQUESTED"
    }
    val filteredList = remember(appointments, pendingOnlyFilter) {
        if (pendingOnlyFilter) {
            appointments.filter {
                it.status == "PENDING" || it.status == "RESCHEDULE_REQUESTED"
            }
        } else {
            appointments
        }
    }
    val visibleList = remember(filteredList, showAll) {
        if (showAll) filteredList else filteredList.take(5)
    }
    val context = LocalContext.current

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = softBorder
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = brandAccent)
                    Column {
                        Text(
                            text = "Agenda do programa",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = brandAccent
                        )
                        Text(
                            text = if (policy.bookingMode == "EXTERNAL_LINK_ONLY") {
                                "Modo link externo ativo (${policy.availabilitiesSummary})."
                            } else {
                                policy.availabilitiesSummary
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                if (pendingCount > 0) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = brandPrimary.copy(alpha = 0.14f)
                    ) {
                        Text(
                            text = B2BTextFormatters.countLabel(pendingCount, "pendente", "pendentes"),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            if (policy.bookingMode != "EXTERNAL_LINK_ONLY") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = pendingOnlyFilter,
                        onClick = { pendingOnlyFilter = true },
                        label = { Text("Aguardando ($awaitingCount)") }
                    )
                    FilterChip(
                        selected = !pendingOnlyFilter,
                        onClick = { pendingOnlyFilter = false },
                        label = { Text("Todos (${appointments.size})") }
                    )
                }

                if (visibleList.isEmpty()) {
                    AppointmentsEmptyState(
                        title = if (pendingOnlyFilter) {
                            "Nenhum pedido pendente"
                        } else {
                            "Nenhum agendamento ainda"
                        },
                        supporting = if (pendingOnlyFilter) {
                            "Quando um participante solicitar um horário, o pedido aparece aqui para você confirmar."
                        } else {
                            "Compartilhe a disponibilidade do programa para começar a receber solicitações."
                        },
                        brandAccent = brandAccent,
                        action = {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(
                                    onClick = { shareAppointmentsAvailability(context, policy) },
                                    modifier = Modifier.heightIn(min = 48.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Share,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text("Compartilhar disponibilidade")
                                }
                                if (onOpenStudio != null) {
                                    OutlinedButton(
                                        onClick = onOpenStudio,
                                        modifier = Modifier.heightIn(min = 48.dp)
                                    ) {
                                        Text("Abrir Estúdio")
                                    }
                                }
                            }
                        }
                    )
                } else {
                    visibleList.forEach { appt ->
                        val isBusy = updatingAppointmentId == appt.appointmentId
                        val status = appt.parsedStatus
                        val formattedDt = formatAppointmentIsoPtBr(appt.scheduledAt)
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
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = appt.patientName ?: "Participante",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    AppointmentStatusChip(status = status, brandPrimary = brandPrimary)
                                }
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = appointmentStatusIcon(status),
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = appointmentScheduleHeadline(status, formattedDt),
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                if (status != ProgramAppointmentStatus.COMPLETED &&
                                    status != ProgramAppointmentStatus.CANCELLED
                                ) {
                                    appt.preferenceLine()?.let { preference ->
                                        Text(
                                            text = preference,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    AppointmentModalityChip(appt.parsedModality)
                                    AppointmentDurationChip()
                                }
                                if (status != ProgramAppointmentStatus.CANCELLED &&
                                    status != ProgramAppointmentStatus.COMPLETED
                                ) {
                                    OutlinedButton(
                                        onClick = { onManageAppointment(appt) },
                                        enabled = !isBusy,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(min = 48.dp),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        if (isBusy) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(16.dp),
                                                strokeWidth = 2.dp
                                            )
                                            Spacer(Modifier.width(8.dp))
                                        }
                                        Text(if (isBusy) "Abrindo..." else "Gerenciar pedido")
                                    }
                                }
                            }
                        }
                    }

                    if (filteredList.size > 5) {
                        TextButton(
                            onClick = { showAll = !showAll },
                            modifier = Modifier.heightIn(min = 48.dp)
                        ) {
                            Icon(
                                imageVector = if (showAll) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                if (showAll) {
                                    "Mostrar menos"
                                } else {
                                    "Ver todos (${filteredList.size})"
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AppointmentStatusChip(
    status: ProgramAppointmentStatus,
    brandPrimary: Color
) {
    val container = when (status) {
        ProgramAppointmentStatus.CONFIRMED -> brandPrimary.copy(alpha = 0.14f)
        ProgramAppointmentStatus.RESCHEDULE_REQUESTED ->
            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.85f)
        ProgramAppointmentStatus.CANCELLED ->
            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.75f)
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f)
    }
    val content = when (status) {
        ProgramAppointmentStatus.RESCHEDULE_REQUESTED -> MaterialTheme.colorScheme.onSecondaryContainer
        ProgramAppointmentStatus.CANCELLED -> MaterialTheme.colorScheme.onErrorContainer
        else -> MaterialTheme.colorScheme.onSurface
    }
    Surface(shape = RoundedCornerShape(50), color = container) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = appointmentStatusIcon(status),
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = content
            )
            Text(
                text = appointmentStatusVisualLabel(status),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = content
            )
        }
    }
}

@Composable
private fun AppointmentModalityChip(modality: AppointmentModality) {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (modality == AppointmentModality.REMOTE) {
                    Icons.Default.Monitor
                } else {
                    Icons.Default.LocationOn
                },
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = modality.labelPtBr,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun AppointmentDurationChip() {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Timer,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = appointmentDurationLabel(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun AppointmentsEmptyState(
    title: String,
    supporting: String,
    brandAccent: Color,
    action: (@Composable () -> Unit)? = null
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.Top
            ) {
                Icon(
                    imageVector = Icons.Default.CalendarMonth,
                    contentDescription = null,
                    tint = brandAccent
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = supporting,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            action?.invoke()
        }
    }
}

@Composable
fun ProgramAppointmentsParticipantCard(
    programName: String,
    policy: AppointmentsPolicyDto,
    feed: EnrollmentAppointmentsFeedDto?,
    updatingAppointmentId: String?,
    brandPrimary: Color,
    brandOnPrimary: Color,
    brandAccent: Color,
    onOpenRequestSheet: () -> Unit,
    onCancelAppointment: (String) -> Unit
) {
    val uriHandler = LocalUriHandler.current
    val activePolicy = feed?.appointmentsPolicy ?: policy
    val appointments = feed?.appointments.orEmpty()
    val pendingCount = feed?.pendingCount ?: appointments.count { it.status == "PENDING" }
    val modalitiesPt = activePolicy.allowedModalities.joinToString(" e ") {
        AppointmentModality.fromWire(it).labelPtBr
    }
    val canRequest = B2BOnboardingValidator.canRequestAppointment(pendingCount)
    var showAllAppointments by rememberSaveable { mutableStateOf(false) }
    val visibleAppointments = remember(appointments, showAllAppointments) {
        if (showAllAppointments) appointments else appointments.take(6)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = brandAccent)
                    Column {
                        Text(
                            text = "Agendamento com $programName",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Disponibilidade ($modalitiesPt): ${activePolicy.availabilitiesSummary}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (activePolicy.bookingMode == "EXTERNAL_LINK_ONLY") {
                val safeUrl = activePolicy.externalBookingUrl?.trim()?.takeIf { it.startsWith("https://") }
                if (safeUrl != null) {
                    Button(
                        onClick = { runCatching { uriHandler.openUri(safeUrl) } },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = brandPrimary,
                            contentColor = brandOnPrimary
                        )
                    ) {
                        Text("Agendar pelo link externo", fontWeight = FontWeight.Bold)
                    }
                } else {
                    Text(
                        text = "O parceiro ainda não informou um link HTTPS externo para agendamento.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Button(
                    onClick = onOpenRequestSheet,
                    enabled = canRequest,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = brandPrimary,
                        contentColor = brandOnPrimary
                    )
                ) {
                    Text(
                        text = if (canRequest) {
                            "Solicitar horário"
                        } else {
                            "Limite de 3 solicitações pendentes atingido"
                        },
                        fontWeight = FontWeight.Bold
                    )
                }

                if (appointments.isEmpty()) {
                    AppointmentsEmptyState(
                        title = "Nenhum pedido pendente",
                        supporting = "Use \"Solicitar horário\" acima para pedir seu próximo atendimento. O programa confirma a data final.",
                        brandAccent = brandAccent
                    )
                } else {
                    HorizontalDivider()
                    Text(
                        text = "Solicitações (${appointments.size})",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                    visibleAppointments.forEach { appt ->
                        val status = appt.parsedStatus
                        val isBusy = updatingAppointmentId == appt.appointmentId
                        val scheduledPt = formatAppointmentIsoPtBr(appt.scheduledAt)
                        val canCancel = status == ProgramAppointmentStatus.PENDING ||
                            status == ProgramAppointmentStatus.RESCHEDULE_REQUESTED ||
                            status == ProgramAppointmentStatus.CONFIRMED

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
                                    AppointmentStatusChip(status = status, brandPrimary = brandPrimary)
                                    if (canCancel) {
                                        TextButton(
                                            onClick = { onCancelAppointment(appt.appointmentId) },
                                            enabled = !isBusy,
                                            modifier = Modifier.heightIn(min = 48.dp)
                                        ) {
                                            if (isBusy) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(16.dp),
                                                    strokeWidth = 2.dp
                                                )
                                                Spacer(Modifier.width(6.dp))
                                            }
                                            Text(if (isBusy) "Cancelando..." else "Cancelar")
                                        }
                                    }
                                }
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = appointmentStatusIcon(status),
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = appointmentScheduleHeadline(status, scheduledPt),
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                if (status != ProgramAppointmentStatus.COMPLETED &&
                                    status != ProgramAppointmentStatus.CANCELLED
                                ) {
                                    appt.preferenceLine()?.let { preference ->
                                        Text(
                                            text = preference,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    AppointmentModalityChip(appt.parsedModality)
                                    AppointmentDurationChip()
                                }
                                appt.patientNote?.takeIf { it.isNotBlank() }?.let { note ->
                                    Text(
                                        text = "Sua observação: $note",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                appt.partnerNote?.takeIf { it.isNotBlank() }?.let { note ->
                                    Text(
                                        text = "Observação do programa: $note",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                    if (appointments.size > 6) {
                        TextButton(
                            onClick = { showAllAppointments = !showAllAppointments },
                            modifier = Modifier.heightIn(min = 48.dp)
                        ) {
                            Icon(
                                imageVector = if (showAllAppointments) {
                                    Icons.Default.ExpandLess
                                } else {
                                    Icons.Default.ExpandMore
                                },
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                if (showAllAppointments) {
                                    "Mostrar menos"
                                } else {
                                    "Ver todos (${appointments.size})"
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RequestAppointmentBottomSheet(
    programName: String,
    policy: AppointmentsPolicyDto,
    isSubmitting: Boolean,
    brandPrimary: Color,
    brandOnPrimary: Color,
    onSubmit: (modality: String, requestedSlotText: String, patientNote: String?) -> Unit,
    onDismiss: () -> Unit
) {
    val modalities = remember(policy.allowedModalities) {
        policy.allowedModalities.ifEmpty { listOf("IN_PERSON", "REMOTE") }
    }
    var selectedModality by rememberSaveable(modalities) { mutableStateOf(modalities.first()) }
    var slotText by rememberSaveable { mutableStateOf("") }
    var patientNote by rememberSaveable { mutableStateOf("") }
    val slotValid = slotText.trim().length in 2..160

    BragaBottomSheet(onDismissRequest = { if (!isSubmitting) onDismiss() }) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Solicitar horário em $programName",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Disponibilidade informada: ${policy.availabilitiesSummary}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Você informa uma preferência; o programa confirma o horário final.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text(
                text = "Modalidade",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                modalities.forEach { modWire ->
                    val label = AppointmentModality.fromWire(modWire).labelPtBr
                    FilterChip(
                        selected = selectedModality == modWire,
                        enabled = !isSubmitting,
                        onClick = { if (!isSubmitting) selectedModality = modWire },
                        label = { Text(label) }
                    )
                }
            }

            OutlinedTextField(
                value = slotText,
                onValueChange = { if (it.length <= 160) slotText = it },
                label = { Text("Dia e turno desejados") },
                supportingText = {
                    Text("Ex.: quinta pela manhã · ${slotText.length}/160 caracteres")
                },
                enabled = !isSubmitting,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            OutlinedTextField(
                value = patientNote,
                onValueChange = { if (it.length <= 400) patientNote = it },
                label = { Text("Observação") },
                supportingText = {
                    Text(
                        "Opcional · ex.: preferência por videoconferência · " +
                            "${patientNote.length}/400 caracteres"
                    )
                },
                enabled = !isSubmitting,
                minLines = 2,
                maxLines = 4,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            if (isSubmitting) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { liveRegion = LiveRegionMode.Polite },
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp,
                        color = brandPrimary
                    )
                    Text(
                        text = "Enviando solicitação…",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    enabled = !isSubmitting,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Cancelar")
                }
                Button(
                    onClick = {
                        onSubmit(
                            selectedModality,
                            slotText.trim(),
                            patientNote.trim().takeIf { it.isNotEmpty() }
                        )
                    },
                    enabled = slotValid && !isSubmitting,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = brandPrimary,
                        contentColor = brandOnPrimary
                    )
                ) {
                    if (isSubmitting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = brandOnPrimary
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(
                        text = if (isSubmitting) "Enviando..." else "Enviar solicitação",
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun OwnerAppointmentDecisionBottomSheet(
    appointment: ProgramAppointmentDto,
    isUpdating: Boolean,
    brandPrimary: Color,
    brandOnPrimary: Color,
    onConfirmDecision: (status: String, scheduledAtIso: String?, partnerNote: String?, allowOverlap: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val validTargets = remember(appointment.status) {
        when (appointment.status) {
            "CONFIRMED" -> listOf("COMPLETED", "RESCHEDULE_REQUESTED", "CANCELLED")
            else -> listOf("CONFIRMED", "RESCHEDULE_REQUESTED", "CANCELLED")
        }
    }
    var targetStatus by rememberSaveable(appointment.appointmentId) {
        mutableStateOf(validTargets.first())
    }
    var dayOffset by rememberSaveable(appointment.appointmentId) { mutableStateOf(1) }
    var selectedHour by rememberSaveable(appointment.appointmentId) { mutableStateOf(14) }
    var selectedMinute by rememberSaveable(appointment.appointmentId) { mutableStateOf(0) }
    var partnerNote by rememberSaveable(appointment.appointmentId) {
        mutableStateOf(appointment.partnerNote.orEmpty())
    }
    var allowOverlap by rememberSaveable(appointment.appointmentId) { mutableStateOf(false) }

    val selectedZdt = remember(dayOffset, selectedHour, selectedMinute) {
        val targetDate = LocalDate.now().plusDays(dayOffset.toLong())
        val targetTime = LocalTime.of(selectedHour, selectedMinute)
        ZonedDateTime.of(targetDate, targetTime, ZoneId.systemDefault())
    }
    val currentScheduledLabel = formatAppointmentIsoPtBr(appointment.scheduledAt)

    BragaBottomSheet(onDismissRequest = { if (!isUpdating) onDismiss() }) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Decisão de agendamento",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = appointment.patientName ?: "Participante",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    appointment.preferenceLine()?.let { preference ->
                        Text(
                            text = preference,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    currentScheduledLabel?.let { schedule ->
                        Text(
                            text = "Horário atual: $schedule",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                AppointmentStatusChip(status = appointment.parsedStatus, brandPrimary = brandPrimary)
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AppointmentModalityChip(appointment.parsedModality)
                AppointmentDurationChip()
            }

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                validTargets.forEach { stWire ->
                    val status = ProgramAppointmentStatus.fromWire(stWire)
                    FilterChip(
                        selected = targetStatus == stWire,
                        enabled = !isUpdating,
                        onClick = { if (!isUpdating) targetStatus = stWire },
                        leadingIcon = {
                            Icon(
                                imageVector = appointmentStatusIcon(status),
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        label = { Text(status.labelPtBr) }
                    )
                }
            }

            if (targetStatus == "CONFIRMED" || targetStatus == "RESCHEDULE_REQUESTED") {
                Text(
                    text = "Data e horário (${selectedZdt.format(ptBrDateTimeFormatter)})",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(1, 2, 3, 5, 7, 14).forEach { offset ->
                        val chipDate = LocalDate.now().plusDays(offset.toLong())
                        FilterChip(
                            selected = dayOffset == offset,
                            enabled = !isUpdating,
                            onClick = { if (!isUpdating) dayOffset = offset },
                            label = { Text(chipDate.format(ptBrDayChipFormatter)) }
                        )
                    }
                }
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(8 to 0, 9 to 0, 10 to 30, 14 to 0, 15 to 30, 17 to 0, 18 to 0).forEach { (h, m) ->
                        val lbl = String.format(Locale.US, "%02d:%02d", h, m)
                        FilterChip(
                            selected = selectedHour == h && selectedMinute == m,
                            enabled = !isUpdating,
                            onClick = {
                                if (!isUpdating) {
                                    selectedHour = h
                                    selectedMinute = m
                                }
                            },
                            label = { Text(lbl) }
                        )
                    }
                }
            }

            if (targetStatus == "CONFIRMED") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Permitir encaixe paralelo se houver outro horário dentro de 30 min",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = allowOverlap,
                        enabled = !isUpdating,
                        onCheckedChange = { if (!isUpdating) allowOverlap = it }
                    )
                }
            }

            OutlinedTextField(
                value = partnerNote,
                onValueChange = { if (it.length <= 400) partnerNote = it },
                label = { Text("Mensagem para o participante") },
                supportingText = {
                    Text("Opcional · ${partnerNote.length}/400 caracteres")
                },
                enabled = !isUpdating,
                minLines = 2,
                maxLines = 4,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            if (isUpdating) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { liveRegion = LiveRegionMode.Polite },
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp,
                        color = brandPrimary
                    )
                    Text(
                        text = "Salvando decisão…",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    enabled = !isUpdating,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Fechar")
                }
                Button(
                    onClick = {
                        val isoOrNull = if (targetStatus == "CONFIRMED" || targetStatus == "RESCHEDULE_REQUESTED") {
                            selectedZdt.toInstant().toString()
                        } else {
                            null
                        }
                        onConfirmDecision(
                            targetStatus,
                            isoOrNull,
                            partnerNote.trim().takeIf { it.isNotEmpty() },
                            allowOverlap
                        )
                    },
                    enabled = !isUpdating,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = brandPrimary,
                        contentColor = brandOnPrimary
                    )
                ) {
                    if (isUpdating) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = brandOnPrimary
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(
                        text = if (isUpdating) "Salvando..." else "Salvar decisão",
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}
