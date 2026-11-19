package br.com.bragasaude.ui.b2b

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
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

@OptIn(ExperimentalMaterial3Api::class)
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

    var selectedCategories by remember {
        mutableStateOf(
            setOf(
                SharedCategoryOption.STEPS.wireKey,
                SharedCategoryOption.HYDRATION.wireKey
            )
        )
    }

    LaunchedEffect(initialInviteCode, initialProgramId) {
        val contexts = apiClient.getB2BContexts()
        enrollments = contexts.filter {
            it.parsedMode == AppContextMode.PARTNER_PARTICIPANT && !it.programId.isNullOrBlank()
        }
        val participant = enrollments.firstOrNull { it.programId == initialProgramId }
            ?: enrollments.firstOrNull()
        if (participant != null) {
            activeEnrollment = participant
            if (participant.sharedCategories.isNotEmpty()) {
                selectedCategories = participant.sharedCategories.toSet()
            }
        }
        val cleanInitial = initialInviteCode.trim().uppercase()
        if (cleanInitial.length >= 4 && B2BOnboardingValidator.isValidInviteCode(cleanInitial)) {
            inviteCode = cleanInitial
            invitePreview = apiClient.lookupPartnerInvitePreview(cleanInitial)
        }
        isLoading = false
    }

    val consentProgramId = invitePreview?.programId
        ?: activeEnrollment?.programId?.takeIf { inviteCode.isBlank() }
    var familyShareEnabled by remember(consentProgramId) {
        mutableStateOf(enrollments.firstOrNull { it.programId == consentProgramId }?.familyShareEnabled ?: false)
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
                    enrollments.forEach { enrollment ->
                        FilterChip(
                            selected = activeEnrollment?.programId == enrollment.programId,
                            enabled = !isWorking,
                            onClick = {
                                activeEnrollment = enrollment
                                selectedCategories = enrollment.sharedCategories.toSet()
                                errorMessage = null
                            },
                            label = { Text(enrollment.programName ?: "Programa parceiro") }
                        )
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
                            .clickable(enabled = !isWorking) {
                                selectedCategories = if (isChecked) {
                                    selectedCategories - option.wireKey
                                } else {
                                    selectedCategories + option.wireKey
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
                                onCheckedChange = { checked ->
                                    selectedCategories = if (checked) {
                                        selectedCategories + option.wireKey
                                    } else {
                                        selectedCategories - option.wireKey
                                    }
                                    errorMessage = null
                                },
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

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = familyShareEnabled,
                        onCheckedChange = { familyShareEnabled = it },
                        enabled = !isWorking && consentProgramId != null
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

                val hasValidCategories =
                    B2BOnboardingValidator.isValidConsentSelection(selectedCategories)
                val currentActive = activeEnrollment
                val isUpdatingSameProgram = currentActive != null &&
                    (inviteCode.isBlank() || invitePreview?.programId == currentActive.programId)

                if (isUpdatingSameProgram && currentActive?.programId != null) {
                    val progId = currentActive.programId
                    Button(
                        onClick = {
                            if (!hasValidCategories || isWorking) return@Button
                            isWorking = true
                            errorMessage = null
                            scope.launch {
                                val res = apiClient.updatePartnerConsent(progId, selectedCategories, familyShareEnabled)
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
                        enabled = hasValidCategories && !isWorking,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
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

                    Button(
                        onClick = {
                            if (!canJoin || isWorking) return@Button
                            isWorking = true
                            errorMessage = null
                            scope.launch {
                                val res = apiClient.joinPartnerProgram(inviteCode, selectedCategories, familyShareEnabled)
                                isWorking = false
                                if (res.isSuccess && res.activeContext != null) {
                                    activeEnrollment = res.activeContext
                                    enrollments = enrollments.filterNot { it.programId == res.activeContext.programId } + res.activeContext
                                    familyShareEnabled = res.activeContext.familyShareEnabled
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
                            .height(52.dp),
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
