package br.com.bragasaude.ui.components

import br.com.bragasaude.data.util.HealthFormatter
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@Composable
fun PhoneLinkDialog(
    onDismiss: () -> Unit,
    onSendOtp: (String) -> Unit,
    onVerifyOtp: (String, String) -> Unit,
    // Plano B OTP: código só existe após sucesso real; waLink abre a conversa.
    codeSent: Boolean = false,
    waLink: String? = null,
    onOpenWhatsApp: (String) -> Unit = {},
    initialPhone: String = "",
    isLoading: Boolean = false,
    errorMessage: String? = null,
    onResetStep: (() -> Unit)? = null
) {
    var phoneNumber by remember(initialPhone) { mutableStateOf(initialPhone) }
    var otpCode by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = { if (!isLoading) onDismiss() },
        title = {
            Text(
                "Vincular seu WhatsApp",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (!codeSent) {
                    Text(
                        "Cadastre ou atualize seu WhatsApp para receber lembretes de medicamentos, consultas e avisos importantes de saúde.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = phoneNumber,
                        onValueChange = { phoneNumber = HealthFormatter.formatPhoneInput(it) },
                        label = { Text("Número do WhatsApp com DDD") },
                        placeholder = { Text("(11) 99999-8888") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        enabled = !isLoading
                    )
                } else {
                    if (waLink != null) {
                        Text(
                            "Toque abaixo para abrir a conversa no WhatsApp e enviar a mensagem — o código chega lá em segundos:",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(
                            onClick = { onOpenWhatsApp(waLink) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            enabled = !isLoading
                        ) {
                            Text("Abrir conversa no WhatsApp")
                        }
                    }
                    Text(
                        "Enviamos um código de 6 dígitos no seu WhatsApp ($phoneNumber). Digite-o abaixo para confirmar:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = otpCode,
                        onValueChange = { if (it.length <= 6) otpCode = it.filter { c -> c.isDigit() } },
                        label = { Text("Código de 6 dígitos") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        enabled = !isLoading
                    )

                    if (onResetStep != null) {
                        TextButton(
                            onClick = onResetStep,
                            modifier = Modifier.align(Alignment.End),
                            enabled = !isLoading
                        ) {
                            Text("Corrigir número", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }

                if (!errorMessage.isNullOrBlank()) {
                    Text(
                        text = errorMessage,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            if (!codeSent) {
                Button(
                    onClick = {
                        if (phoneNumber.trim().length >= 10 && !isLoading) {
                            onSendOtp(phoneNumber.trim())
                        }
                    },
                    enabled = phoneNumber.trim().length >= 10 && !isLoading,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Text("Receber Código")
                    }
                }
            } else {
                Button(
                    onClick = {
                        if (otpCode.length == 6 && !isLoading) {
                            onVerifyOtp(phoneNumber.trim(), otpCode)
                        }
                    },
                    enabled = otpCode.length == 6 && !isLoading,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Text("Validar e Concluir")
                    }
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isLoading) {
                Text("Agora não")
            }
        }
    )
}
