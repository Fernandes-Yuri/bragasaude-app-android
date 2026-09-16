package br.com.bragasaude.ui.legal

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.bragasaude.ui.theme.TealLight
import br.com.bragasaude.ui.theme.TealPrimary
import br.com.bragasaude.ui.theme.TealSurface

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TermsOfUseScreen(
    onBack: (() -> Unit)? = null,
    onAccept: (() -> Unit)? = null,
    isAcceptanceFlow: Boolean = false
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Termos de Uso", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        bottomBar = {
            if (isAcceptanceFlow && onAccept != null) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shadowElevation = 8.dp,
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Box(modifier = Modifier.padding(16.dp)) {
                        Button(
                            onClick = onAccept,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = TealPrimary)
                        ) {
                            Text("Concordo com os Termos de Uso", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Surface(
                    color = TealSurface,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Gavel,
                            contentDescription = null,
                            tint = TealPrimary,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                "Termos de Uso do Aplicativo",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "Versão 2026.1 • Última atualização: 01/09/2026",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            item {
                LegalClause(
                    number = "1",
                    title = "Natureza Informativa e Não Médica",
                    content = "O aplicativo é uma ferramenta de suporte pessoal ao autocuidado, organização de histórico e incentivo a hábitos saudáveis. As informações, gráficos, estimativas e análises NÃO constituem diagnóstico médico, recomendação clínica, conduta terapêutica ou prescrição farmacológica."
                )
            }

            item {
                LegalClause(
                    number = "2",
                    title = "Responsabilidade do Usuário",
                    content = "O usuário é responsável pela veracidade dos dados inseridos voluntariamente. Em caso de mal-estar, emergência ou alteração significativa dos sinais vitais, o usuário deve buscar atendimento médico presencial imediato nos serviços de saúde públicos ou privados."
                )
            }

            item {
                LegalClause(
                    number = "3",
                    title = "Transparência de Parâmetros e Referências",
                    content = "Os cálculos e faixas de normalidade exibidos pelo aplicativo baseiam-se em consensos internacionais públicos (AHA, ADA e OMS). Essas faixas representam médias populacionais e podem variar conforme orientação individual do seu médico assistente."
                )
            }

            item {
                LegalClause(
                    number = "4",
                    title = "Segurança de Dados e Acesso",
                    content = "Suas credenciais de login e dados de saúde são armazenados de forma criptografada. Você pode solicitar a exportação ou exclusão definitiva do seu histórico a qualquer momento através das Configurações do aplicativo."
                )
            }

            item {
                LegalClause(
                    number = "5",
                    title = "Atualizações dos Termos",
                    content = "Podemos atualizar estes termos periodicamente para refletir melhorias no serviço ou exigências regulatórias. Notificaremos você sobre mudanças substanciais diretamente pelo aplicativo."
                )
            }

            item {
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

@Composable
fun LegalClause(
    number: String,
    title: String,
    content: String
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = TealLight,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.size(26.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(number, fontWeight = FontWeight.Bold, color = TealPrimary, fontSize = 13.sp)
                    }
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                content,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 20.sp
            )
        }
    }
}
