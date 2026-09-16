package br.com.bragasaude.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.bragasaude.ui.theme.TealLight
import br.com.bragasaude.ui.theme.TealPrimary
import br.com.bragasaude.ui.theme.TealSurface

/**
 * Catálogo de informações de métricas para transparência metodológica.
 * Baseado estritamente nas diretrizes da SBC (Sociedade Brasileira de Cardiologia),
 * SBD (Sociedade Brasileira de Diabetes) e OMS.
 */
data class MetricInfo(
    val name: String,
    val howCalculated: String,
    val referenceInstitution: String,
    val whyThisReference: String,
    val unit: String,
    val officialUrl: String? = null,
    val tableRows: List<Pair<String, String>> = emptyList()
)

object MetricInfoCatalog {
    
    val bloodPressure = MetricInfo(
        name = "Pressão Arterial (Sistólica e Diastólica)",
        howCalculated = "Medida em mmHg através de monitor oscilométrico validado. Avalia tanto a pressão máxima durante a contração (Sistólica) quanto a pressão mínima no relaxamento (Diastólica). Quando Sistólica e Diastólica estão em faixas diferentes, a mais alta define a classificação, indicando eventuais transições/oscilações.",
        referenceInstitution = "SBC - Sociedade Brasileira de Cardiologia / Diretrizes Brasileiras de Hipertensão Arterial & OMS",
        whyThisReference = "Diretriz médica oficial e padrão de referência nacional e internacional para saúde cardiovascular e acompanhamento preventivo no Brasil.",
        unit = "mmHg",
        officialUrl = "https://www.portal.cardiol.br",
        tableRows = listOf(
            "Pressão Ótima" to "< 120 mmHg e < 80 mmHg",
            "Pressão Normal" to "120–129 mmHg e/ou 80–84 mmHg",
            "Pré-Hipertensão" to "130–139 mmHg e/ou 85–89 mmHg",
            "Hipertensão Estágio 1" to "140–159 mmHg e/ou 90–99 mmHg",
            "Hipertensão Estágio 2" to "160–179 mmHg e/ou 100–109 mmHg",
            "Hipertensão Estágio 3" to "≥ 180 mmHg e/ou ≥ 110 mmHg",
            "Hipotensão (Baixa)" to "< 90 mmHg ou < 60 mmHg"
        )
    )

    val systolicPressure = bloodPressure
    val diastolicPressure = bloodPressure
    
    val fastingGlucose = MetricInfo(
        name = "Glicemia em Jejum",
        howCalculated = "Medida em mg/dL após período mínimo de 8 a 12 horas sem consumo calórico. Reflete a homeostase glicêmica basal.",
        referenceInstitution = "SBD - Sociedade Brasileira de Diabetes & ADA 2026",
        whyThisReference = "Consenso oficial brasileiro e internacional para faixas de referência e rastreamento glicêmico.",
        unit = "mg/dL",
        officialUrl = "https://diabetes.org.br",
        tableRows = listOf(
            "Hipoglicemia" to "< 70 mg/dL (Alerta Severo: < 54 mg/dL)",
            "Faixa de Referência (Normal)" to "70 a 99 mg/dL",
            "Glicemia de Jejum Alterada" to "100 a 125 mg/dL",
            "Faixa Elevada" to "≥ 126 mg/dL (Requer confirmação médica)",
            "Atenção Imediata" to "≥ 300 mg/dL"
        )
    )
    
    val postprandialGlucose = MetricInfo(
        name = "Glicose Pós-Prandial",
        howCalculated = "Medida em mg/dL realizada 1 a 2 horas após o início da refeição. Avalia a resposta do organismo à absorção de carboidratos.",
        referenceInstitution = "SBD - Sociedade Brasileira de Diabetes & ADA 2026",
        whyThisReference = "Referência oficial para avaliação da tolerância glicêmica alimentar pós-refeição.",
        unit = "mg/dL",
        officialUrl = "https://diabetes.org.br",
        tableRows = listOf(
            "Normal / Alvo" to "< 140 mg/dL",
            "Tolerância Diminuída" to "140 a 199 mg/dL",
            "Faixa Elevada" to "≥ 200 mg/dL"
        )
    )

    val cgmGlucose = MetricInfo(
        name = "Monitoramento Contínuo de Glicose (CGM)",
        howCalculated = "Medição contínua através de sensor intersticial expressa em Tempo no Alvo (Time in Range - TIR).",
        referenceInstitution = "SBD & Consenso Internacional de CGM 2026",
        whyThisReference = "Padrão ouro para avaliação da variabilidade glicêmica dinâmica.",
        unit = "mg/dL",
        officialUrl = "https://diabetes.org.br",
        tableRows = listOf(
            "Tempo no Alvo (TIR)" to "70 a 180 mg/dL (Alvo: > 70% do tempo)",
            "Abaixo do Alvo" to "< 70 mg/dL (Alvo: < 4% do tempo)",
            "Acima do Alvo" to "> 180 mg/dL (Alvo: < 25% do tempo)"
        )
    )
    
    val imc = MetricInfo(
        name = "Índice de Massa Corporal (IMC)",
        howCalculated = "Fórmula antropométrica: Peso (kg) ÷ Altura² (m²).",
        referenceInstitution = "OMS - Organização Mundial da Saúde",
        whyThisReference = "Parâmetro padrão global para estratificação populacional de massa corporal.",
        unit = "kg/m²",
        officialUrl = "https://www.who.int",
        tableRows = listOf(
            "Abaixo do peso" to "< 18.5 kg/m²",
            "Peso normal" to "18.5 – 24.9 kg/m²",
            "Sobrepeso" to "25.0 – 29.9 kg/m²",
            "Obesidade Grau I" to "30.0 – 34.9 kg/m²",
            "Obesidade Grau II" to "35.0 – 39.9 kg/m²",
            "Obesidade Grau III" to "≥ 40.0 kg/m²"
        )
    )

    val dailySteps = MetricInfo(
        name = "Passos Diários",
        howCalculated = "Detecção contínua por acelerômetro e sensor de passos do dispositivo Android.",
        referenceInstitution = "OMS / OPAS - Diretrizes de Atividade Física",
        whyThisReference = "Recomendações globais para saúde cardiovascular e redução de riscos metabólicos.",
        unit = "passos",
        officialUrl = "https://www.who.int/news-room/fact-sheets/detail/physical-activity"
    )

    val heartPoints = MetricInfo(
        name = "Pontos de Cardio (Diretriz OMS)",
        howCalculated = "Sistema oficial de pontuação baseado nas diretrizes de atividade física da Organização Mundial da Saúde (OMS): você acumula 1 Ponto de Cardio por cada minuto de atividade moderada (caminhada rápida) e 2 Pontos de Cardio por cada minuto de atividade intensa (corrida). A meta estabelecida pela OMS é de 150 Pontos de Cardio por semana. O algoritmo identifica automaticamente os locais onde você costuma ficar parado. Até ele reconhecer esses pontos (primeiros dias), todos os minutos de atividade contam. Após o reconhecimento, só são computados os Pontos de Cardio ganhos fora dessas zonas — minutos de atividade dentro dos locais onde você costuma ficar não geram pontos.",
        referenceInstitution = "OMS (Organização Mundial da Saúde)",
        whyThisReference = "Diretriz global de saúde cardiovascular que comprova redução de mortalidade, melhor controle glicêmico e proteção cardíaca ao atingir 150 pontos semanais.",
        unit = "pontos/semana",
        officialUrl = "https://www.who.int/news-room/fact-sheets/detail/physical-activity",
        tableRows = listOf(
            "Meta Semanal Oficial (OMS)" to "150 Pontos de Cardio",
            "Meta Diária Sugerida" to "~22 Pontos / dia",
            "Atividade Moderada (Caminhada Rápida)" to "1 Ponto / minuto",
            "Atividade Intensa (Corrida/Treino)" to "2 Pontos / minuto",
            "Benefício Comprovado" to "Redução de risco cardíaco e longevidade",
            "Regra de Zona" to "Só conta fora dos locais onde você costuma ficar parado"
        )
    )

    val hydration = MetricInfo(
        name = "Meta Diária de Hidratação (35 ml/kg)",
        howCalculated = "A meta de hidratação é calculada pela fórmula científica recomendada: 35 ml para cada 1 kg de peso corporal ao dia (ex: 70 kg × 35 ml = 2.450 ml/dia). A ingestão de água auxilia na circulação, controle térmico, filtração renal e equilíbrio pressórico.",
        referenceInstitution = "EFSA (European Food Safety Authority) / OMS & Ministério da Saúde do Brasil",
        whyThisReference = "Recomendação oficial que equilibra a homeostase hídrica e a taxa de filtração glomerular fisiológica.",
        unit = "ml/dia",
        officialUrl = "https://www.who.int/news-room/fact-sheets/detail/water-sanitation-and-health",
        tableRows = listOf(
            "Fórmula de Cálculo" to "35 ml × Peso corporal (kg)",
            "Pessoa de 50 kg" to "1.750 ml / dia",
            "Pessoa de 60 kg" to "2.100 ml / dia",
            "Pessoa de 70 kg" to "2.450 ml / dia",
            "Pessoa de 80 kg" to "2.800 ml / dia",
            "Pessoa de 90 kg" to "3.150 ml / dia",
            "Pessoa de 100 kg" to "3.500 ml / dia",
            "Dias de Calor / Exercício" to "+ 500 a 1.000 ml adicionais"
        )
    )
}

/**
 * Ícone de informação (ⓘ) clicável para exibir detalhes de uma métrica.
 */
@Composable
fun MetricInfoIcon(
    metricInfo: MetricInfo,
    modifier: Modifier = Modifier
) {
    var showSheet by remember { mutableStateOf(false) }
    
    IconButton(
        onClick = { showSheet = true },
        modifier = modifier.size(24.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Info,
            contentDescription = "Informações sobre ${metricInfo.name}",
            tint = TealPrimary,
            modifier = Modifier.size(20.dp)
        )
    }
    
    if (showSheet) {
        MetricInfoBottomSheet(
            metricInfo = metricInfo,
            onDismiss = { showSheet = false }
        )
    }
}

/**
 * Bottom Sheet Modal de Transparência Metodológica e Referências Oficiais.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MetricInfoBottomSheet(
    metricInfo: MetricInfo,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val uriHandler = LocalUriHandler.current
    
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header com destaque Teal
            Surface(
                color = TealSurface,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(16.dp)
                ) {
                    Surface(
                        color = TealLight,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.size(44.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = TealPrimary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column {
                        Text(
                            text = metricInfo.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Unidade: ${metricInfo.unit}",
                            style = MaterialTheme.typography.labelMedium,
                            color = TealPrimary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
            
            // 1. Como é calculada
            InfoSection(
                title = "Como é calculada?",
                content = metricInfo.howCalculated
            )
            
            // 2. Referência/Instituição
            InfoSection(
                title = "Diretriz e Instituição de Referência",
                content = metricInfo.referenceInstitution
            )

            // Tabela de Faixas Oficiais (se houver)
            if (metricInfo.tableRows.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Tabela Oficial de Referência",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = TealPrimary
                    )
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            metricInfo.tableRows.forEach { (cat, range) ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = cat,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = range,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
                            }
                        }
                    }
                }
            }
            
            // 3. Por que usamos esta referência
            InfoSection(
                title = "Por que usamos esta referência?",
                content = metricInfo.whyThisReference
            )

            // 4. Link Oficial Clicável para o Portal da Instituição
            metricInfo.officialUrl?.let { url ->
                Button(
                    onClick = { uriHandler.openUri(url) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = TealPrimary),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Acessar portal oficial da diretriz", fontWeight = FontWeight.Bold)
                }
            }
            
            Spacer(modifier = Modifier.height(4.dp))
            
            // Disclaimer Obrigatório de Não-Diagnóstico
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    horizontalAlignment = Alignment.Start
                ) {
                    Text(
                        text = "⚖️ Transparência e Segurança",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "As faixas e referências apresentadas seguem as diretrizes oficiais da Sociedade Brasileira de Cardiologia (SBC) e Sociedade Brasileira de Diabetes (SBD), tendo finalidade exclusivamente informativa de autocuidado. Elas não constituem diagnóstico médico nem prescrição. Em caso de dúvidas ou valores alterados, consulte sempre um médico.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 18.sp
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
private fun InfoSection(
    title: String,
    content: String
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = TealPrimary
        )
        Text(
            text = content,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            lineHeight = 20.sp
        )
    }
}
