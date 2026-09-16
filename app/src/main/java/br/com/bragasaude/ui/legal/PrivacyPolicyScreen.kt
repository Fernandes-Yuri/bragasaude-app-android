package br.com.bragasaude.ui.legal

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.bragasaude.ui.theme.TealLight
import br.com.bragasaude.ui.theme.TealPrimary
import br.com.bragasaude.ui.theme.TealSurface

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyPolicyScreen(
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Política de Privacidade", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
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
                Text("Relógios e Health Connect", style = MaterialTheme.typography.titleMedium)
                Text("Com sua autorização, lemos passos, frequência cardíaca e saturação de oxigênio compartilhados com Health Connect. Batimentos e SpO₂ são armazenados neste celular, na sua conta, com horário e origem, e podem integrar o relatório que você escolher compartilhar. Passos integram suas métricas de atividade sincronizadas. Você pode revogar as permissões no Health Connect. A revogação não apaga o histórico já importado; a exclusão da conta remove os dados locais.")
            }
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
                            Icons.Default.Lock,
                            contentDescription = null,
                            tint = TealPrimary,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                "Proteção de Dados e LGPD",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "Lei Geral de Proteção de Dados (Lei nº 13.709/2018)",
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
                    title = "Dados Coletados e Finalidade",
                    content = "Coletamos apenas os dados estritamente necessários para o funcionamento do app: registros de sinais vitais informados por você, parâmetros de exames laboratoriais em PDF carregados voluntariamente, dados de movimentação/passos (quando autorizados) e contato de emergência opcional."
                )
            }

            item {
                LegalClause(
                    number = "2",
                    title = "Tratamento e Não Compartilhamento",
                    content = "Seus dados de saúde são sensíveis e pertencem exclusivamente a você. NÃO vendemos, NÃO alugamos e NÃO compartilhamos seus dados individuais com terceiros, seguradoras ou anunciantes."
                )
            }

            item {
                LegalClause(
                    number = "3",
                    title = "Armazenamento e Segurança",
                    content = "Os dados são mantidos localmente em banco de dados criptografado e sincronizados em nuvem segura (Google Cloud / Firebase) com conexões HTTPS/TLS e regras de isolamento por usuário autenticado."
                )
            }

            item {
                LegalClause(
                    number = "4",
                    title = "Seus Direitos como Titular",
                    content = "Em conformidade com a LGPD, você tem direito a: confirmar a existência de tratamento, acessar seus dados, corrigir dados incompletos ou desatualizados, solicitar a anonimização e requerer a eliminação completa da sua conta e de todo o seu histórico a qualquer momento."
                )
            }

            item {
                LegalClause(
                    number = "5",
                    title = "Encarregado de Dados (DPO)",
                    content = "Para esclarecer dúvidas sobre a privacidade dos seus dados ou exercer seus direitos de exclusão e portabilidade, você pode utilizar a aba de Suporte no aplicativo ou entrar em contato pelos canais oficiais."
                )
            }

            item {
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}
