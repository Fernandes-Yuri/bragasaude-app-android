package br.com.bragasaude.ui.profile

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import br.com.bragasaude.ui.components.ShieldEcgIcon
import br.com.bragasaude.ui.theme.TealPrimary
import br.com.bragasaude.ui.theme.TealSurface

@Composable
fun ConsentScreen(
    onAccepted: () -> Unit,
    onNavigateToTerms: (() -> Unit)? = null,
    onNavigateToPrivacy: (() -> Unit)? = null,
    viewModel: ProfileViewModel = hiltViewModel()
) {
    val isLoading by viewModel.isLoading.collectAsState()

    Scaffold(
        modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing),
        bottomBar = {
            Box(Modifier.padding(24.dp)) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = TealPrimary)
                } else {
                    Button(
                        onClick = {
                            viewModel.saveConsent(onAccepted)
                        },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = TealPrimary)
                    ) {
                        Text("Eu aceito os termos e condições", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                Spacer(Modifier.height(24.dp))
                ShieldEcgIcon(sizeDp = 72.dp, shieldColor = TealPrimary)
                Spacer(Modifier.height(16.dp))
                Text(
                    "Termo de Consentimento e Privacidade",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Text(
                    "Seus dados protegidos de acordo com a LGPD.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }

            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = TealSurface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        ConsentItem(
                            "Localização & Movimento",
                            "Utilizamos sensores e GPS local para apoiar seu registro de passos e atividade física com total transparência."
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                        ConsentItem(
                            "Processamento de Laudos em PDF",
                            "A extração de parâmetros laboratoriais dos seus PDFs tem como único objetivo organizar seu histórico de saúde."
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                        ConsentItem(
                            "Autocuidado e Limites Clínicos",
                            "O aplicativo não substitui consultas, diagnósticos ou prescrições médicas. É uma ferramenta de apoio ao seu bem-estar."
                        )
                    }
                }
            }

            item {
                Text(
                    "Ao tocar em aceitar, você concorda com o processamento dos seus dados conforme nossos documentos legais oficiais:",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    TextButton(onClick = { onNavigateToTerms?.invoke() }) {
                        Text("1. Termos de Uso", fontSize = 13.sp, color = TealPrimary, fontWeight = FontWeight.Bold)
                    }
                    TextButton(onClick = { onNavigateToPrivacy?.invoke() }) {
                        Text("2. Política de Privacidade", fontSize = 13.sp, color = TealPrimary, fontWeight = FontWeight.Bold)
                    }
                }
            }
            
            item { Spacer(Modifier.height(32.dp)) }
        }
    }
}

@Composable
fun ConsentItem(title: String, description: String) {
    Column {
        Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
        Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
