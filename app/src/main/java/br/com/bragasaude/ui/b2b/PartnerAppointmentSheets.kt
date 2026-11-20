package br.com.bragasaude.ui.b2b

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
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

internal fun formatAppointmentIsoPtBr(iso: String?): String? {
    val clean = iso?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    return runCatching {
        Instant.parse(clean)
            .atZone(ZoneId.systemDefault())
            .format(ptBrDateTimeFormatter)
    }.getOrNull() ?: clean
}

@Composable
fun OwnerAppointmentsWalletCard(
    appointments: List<ProgramAppointmentDto>,
    policy: AppointmentsPolicyDto,
    updatingAppointmentId: String?,
    brandPrimary: Color,
    brandAccent: Color,
    softBorder: BorderStroke?,
    onManageAppointment: (ProgramAppointmentDto) -> Unit
) {
    var pendingOnlyFilter by rememberSaveable { mutableStateOf(true) }
    val pendingCount = appointments.count { it.status == "PENDING" }
    val visibleList = remember(appointments, pendingOnlyFilter) {
        val base = if (pendingOnlyFilter) {
            appointments.filter { it.status == "PENDING" || it.status == "RESCHEDULE_REQUESTED" }
        } else {
            appointments
        }
        base.take(8)
    }

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
                            color = brandAccent,
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
                        label = { Text("Aguardando") }
                    )
                    FilterChip(
                        selected = !pendingOnlyFilter,
                        onClick = { pendingOnlyFilter = false },
                        label = { Text("Todos (${appointments.size})") }
                    )
                }

                if (visibleList.isEmpty()) {
                    Text(
                        text = if (pendingOnlyFilter) {
                            "Nenhuma solicitação pendente de confirmação no momento."
                        } else {
                            "Ainda não há solicitações de agendamento recebidas."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    visibleList.forEach { appt ->
                        val isBusy = updatingAppointmentId == appt.appointmentId
                        val formattedDt = formatAppointmentIsoPtBr(appt.scheduledAt)
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Text(
                                        text = "${appt.patientName ?: "Participante"} · ${appt.parsedModality.labelPtBr}",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Preferência: ${appt.requestedSlotText}",
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = formattedDt?.let { "${appt.parsedStatus.labelPtBr} · $it" }
                                            ?: appt.parsedStatus.labelPtBr,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = brandAccent
                                    )
                                }
                                if (appt.parsedStatus != ProgramAppointmentStatus.CANCELLED &&
                                    appt.parsedStatus != ProgramAppointmentStatus.COMPLETED
                                ) {
                                    OutlinedButton(
                                        onClick = { onManageAppointment(appt) },
                                        enabled = !isBusy,
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Text("Gerenciar")
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

                if (appointments.isNotEmpty()) {
                    HorizontalDivider()
                    appointments.take(6).forEach { appt ->
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
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${appt.parsedModality.labelPtBr} · ${status.labelPtBr}",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = brandAccent
                                    )
                                    if (canCancel) {
                                        TextButton(
                                            onClick = { onCancelAppointment(appt.appointmentId) },
                                            enabled = !isBusy
                                        ) {
                                            Text(if (isBusy) "Cancelando..." else "Cancelar")
                                        }
                                    }
                                }
                                Text(
                                    text = "Solicitado: ${appt.requestedSlotText}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                scheduledPt?.let { dt ->
                                    Text(
                                        text = "Horário definido: $dt",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.SemiBold
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
                text = "Modalidade",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                modalities.forEach { modWire ->
                    val label = AppointmentModality.fromWire(modWire).labelPtBr
                    FilterChip(
                        selected = selectedModality == modWire,
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
                            selectedModality,
                            slotText.trim(),
                            patientNote.trim().takeIf { it.isNotEmpty() }
                        )
                    },
                    enabled = slotValid && !isSubmitting,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = brandPrimary,
                        contentColor = brandOnPrimary
                    )
                ) {
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

    BragaBottomSheet(onDismissRequest = { if (!isUpdating) onDismiss() }) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Decisão de agendamento",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "${appointment.patientName ?: "Participante"} · ${appointment.parsedModality.labelPtBr}\nPedido: ${appointment.requestedSlotText}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                validTargets.forEach { stWire ->
                    val label = ProgramAppointmentStatus.fromWire(stWire).labelPtBr
                    FilterChip(
                        selected = targetStatus == stWire,
                        onClick = { if (!isUpdating) targetStatus = stWire },
                        label = { Text(label) }
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

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    enabled = !isUpdating,
                    modifier = Modifier.weight(1f),
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
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = brandPrimary,
                        contentColor = brandOnPrimary
                    )
                ) {
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
