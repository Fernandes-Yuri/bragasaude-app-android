package br.com.bragasaude.ui.onboarding

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.bragasaude.ui.components.ShieldEcgIcon
import br.com.bragasaude.ui.components.WhatsAppLinkButton
import br.com.bragasaude.ui.theme.TealLight
import br.com.bragasaude.ui.theme.TealPrimary
import br.com.bragasaude.ui.theme.TealSurface
import kotlinx.coroutines.launch

data class TourSlide(
    val title: String,
    val description: String,
    val icon: ImageVector? = null,
    val useShieldIcon: Boolean = false
)

// ----- Slides para TITULAR / PACIENTE (autocuidado) -----
val patientTourSlides = listOf(
    TourSlide(
        title = "Seu Guardião de Saúde Pessoal",
        description = "Acompanhe seus sinais vitais, pressão e glicose com faixas de referência científicas (AHA/ADA 2026) e total transparência metodológica.",
        useShieldIcon = true
    ),
    TourSlide(
        title = "Seus Laudos e Exames Organizados",
        description = "Carregue seus laudos em PDF e visualize a evolução dos seus parâmetros laboratoriais ao longo do tempo de forma leve e descomplicada.",
        icon = Icons.Default.Description
    ),
    TourSlide(
        title = "Autocuidado e Nutrição Consciente",
        description = "Receba orientações inteligentes de refeições e combinações de alimentos ajustadas para suas preferências e perfil de saúde.",
        icon = Icons.Default.Restaurant
    ),
    TourSlide(
        title = "Movimento, Água e Conquistas",
        description = "Mantenha sua meta de hidratação diária, registre sua caminhada e acompanhe sua disciplina com respeito absoluto à sua privacidade.",
        icon = Icons.Default.EmojiEvents
    ),
    TourSlide(
        title = "Sua Família por Perto, Mesmo de Longe",
        description = "Compartilhe um código seguro com filhos, netos ou cuidadores de confiança. Eles acompanham sua evolução e mandam mensagens de incentivo direto no app.",
        icon = Icons.Default.Favorite
    )
)

// ----- Slides para CUIDADOR (acompanhamento de familiar) -----
val caregiverTourSlides = listOf(
    TourSlide(
        title = "Anjo da Guarda do Seu Familiar",
        description = "Você foi convidado a acompanhar a saúde de alguém importante. Tudo o que ele registrar aparece aqui automaticamente.",
        useShieldIcon = true
    ),
    TourSlide(
        title = "Métricas em Tempo Real",
        description = "Veja pressão arterial, glicemia, hidratação e passos do seu familiar — sempre com a última atualização destacada.",
        icon = Icons.Default.BarChart
    ),
    TourSlide(
        title = "Mensagens de Afeto com 1 Toque",
        description = "Envie lembretes de remédio, incentivos de hidratação e recados personalizados. Tudo chega no celular dele com o seu nome.",
        icon = Icons.AutoMirrored.Filled.Message
    ),
    TourSlide(
        title = "Lista de Compras Colaborativa",
        description = "A lista semanal do seu familiar aparece aqui. Marque o que você já comprou e a despensa dele atualiza automaticamente em casa.",
        icon = Icons.Default.ShoppingCart
    ),
    TourSlide(
        title = "Alertas Silenciosos de Segurança",
        description = "Se algo fugir muito da faixa de referência (ex.: pressão fora do habitual), você recebe um aviso discreto para acolher e conversar. Nenhuma notificação alarmista é enviada.",
        icon = Icons.Default.NotificationsActive
    )
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingTourScreen(
    userRole: String? = "PATIENT",
    totpSecret: String? = null,
    whatsappPhone: String? = null,
    onFinishTour: () -> Unit
) {
    val context = LocalContext.current
    val tourSlides = if (userRole == "CAREGIVER") caregiverTourSlides else patientTourSlides
    val pagerState = rememberPagerState(pageCount = { tourSlides.size })
    val scope = rememberCoroutineScope()

    fun completeTour() {
        val prefs = context.getSharedPreferences("braga_prefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean("tutorial_seen", true)
            .putString("tutorial_seen_for_role", userRole)
            .apply()
        onFinishTour()
    }

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Page indicator minimalista no topo
                Text(
                    text = "${pagerState.currentPage + 1} de ${tourSlides.size}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                TextButton(onClick = { completeTour() }) {
                    Text(
                        "Pular Tutorial",
                        color = TealPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Indicadores de página (bolinhas)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    repeat(tourSlides.size) { index ->
                        val isSelected = pagerState.currentPage == index
                        Box(
                            modifier = Modifier
                                .height(8.dp)
                                .width(if (isSelected) 24.dp else 8.dp)
                                .clip(CircleShape)
                                .background(if (isSelected) TealPrimary else MaterialTheme.colorScheme.outlineVariant)
                        )
                    }
                }

                // Botão de Avanço / Conclusão
                val isLastPage = pagerState.currentPage == tourSlides.size - 1

                Button(
                    onClick = {
                        if (isLastPage) {
                            completeTour()
                        } else {
                            scope.launch {
                                pagerState.animateScrollToPage(pagerState.currentPage + 1)
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = TealPrimary)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = if (isLastPage) "Começar a Usar" else "Próximo",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Spacer(Modifier.width(8.dp))
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) { page ->
            val slide = tourSlides[page]
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Surface(
                    color = TealSurface,
                    shape = RoundedCornerShape(28.dp),
                    modifier = Modifier.size(140.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (slide.useShieldIcon) {
                            ShieldEcgIcon(sizeDp = 80.dp, shieldColor = TealPrimary)
                        } else if (slide.icon != null) {
                            Icon(
                                imageVector = slide.icon,
                                contentDescription = null,
                                tint = TealPrimary,
                                modifier = Modifier.size(64.dp)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(36.dp))

                Text(
                    text = slide.title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(Modifier.height(16.dp))

                Text(
                    text = slide.description,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 24.sp
                )

                // Na última página, convida ao vínculo do WhatsApp. O usuário pode
                // pular e fazer depois pela tela de Perfil.
                if (page == tourSlides.size - 1) {
                    Spacer(Modifier.height(28.dp))
                    WhatsAppLinkButton(
                        totpSecret = totpSecret,
                        alreadyLinked = !whatsappPhone.isNullOrBlank()
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Sem pressa: você também pode vincular depois, na tela de Perfil.",
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
