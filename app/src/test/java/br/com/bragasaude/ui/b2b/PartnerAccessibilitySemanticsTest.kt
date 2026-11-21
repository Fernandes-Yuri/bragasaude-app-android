package br.com.bragasaude.ui.b2b

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import br.com.bragasaude.domain.b2b.PartnerApprovalStatus
import br.com.bragasaude.domain.b2b.PartnerWalletKpisDto
import android.app.Application
import br.com.bragasaude.ui.theme.BragasaudeTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Cobertura automatizada (JVM/Robolectric) das semânticas de acessibilidade que o
 * TalkBack consome nas telas B2B. Não substitui a execução manual do TalkBack/
 * Accessibility Scanner no aparelho, mas protege as descrições faladas no CI.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w360dp-h800dp-mdpi")
class PartnerAccessibilitySemanticsTest {

    @get:Rule
    val compose = createComposeRule()

    private fun content(block: @androidx.compose.runtime.Composable () -> Unit) {
        compose.setContent {
            BragasaudeTheme {
                PartnerBrandTheme("#00695C") { block() }
            }
        }
    }

    @Test
    fun `anel de participantes expoe descricao falada`() {
        content {
            PartnerRing(
                part = 2,
                total = 2,
                centerLabel = "2",
                contentDescription = "Participantes: 2 de 2 ativos, 100%"
            )
        }
        compose.onNodeWithContentDescription("Participantes: 2 de 2 ativos, 100%").assertExists()
    }

    @Test
    fun `chip de status anuncia estado do programa`() {
        content { PartnerStatusChip(status = PartnerApprovalStatus.APPROVED) }
        compose.onNodeWithContentDescription("Status do programa: Empresa aprovada").assertExists()
    }

    @Test
    fun `carteira anuncia lembretes lidos pendentes e mensagens nao lidas`() {
        content {
            PartnerWalletKpis(
                kpis = PartnerWalletKpisDto(
                    activeUsers = 2,
                    inactiveUsers = 0,
                    retentionRate = null,
                    remindersRead = 2,
                    remindersTotal = 2,
                    appointmentsConfirmed = 2,
                    pendingAppointmentsTotal = 1,
                    unreadMessagesTotal = 3,
                    dailyActiveUsers = listOf(0, 0, 2, 0, 0, 2, 0)
                )
            )
        }
        compose.onNodeWithText("2 de 2 lidos").assertExists()
        compose.onNodeWithText("1 pendente para você").assertExists()
        compose.onNodeWithText("3 mensagens não lidas").assertExists()
        compose.onNodeWithContentDescription("Ativos: 2").assertExists()
        compose.onNodeWithText("Carteira global")
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
    }

    @Test
    fun `sparkline fala a serie de atividade`() {
        content { PartnerSparkline(values = listOf(0, 0, 2, 0, 0, 2, 0), periodDays = 7) }
        compose.onNode(
            hasContentDescription("Participantes ativos por dia", substring = true)
        ).assertExists()
    }

    @Test
    fun `titulo da carteira tem semantica de heading`() {
        content {
            PartnerWalletKpis(kpis = PartnerWalletKpisDto(activeUsers = 1, dailyActiveUsers = listOf(1, 1)))
        }
        compose.onNodeWithText("Carteira global")
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
    }

    @Test
    fun `estado vazio da carteira nao anuncia grafico`() {
        content { PartnerWalletKpis(kpis = null) }
        compose.onNodeWithText("Ainda não há indicadores").assertExists()
        compose.onNode(
            hasContentDescription("Participantes ativos por dia", substring = true)
        ).assertDoesNotExist()
    }

    @Test
    fun `grafico de barras diarias anuncia valores e media`() {
        val values = listOf(250L, null, 500L, null, null, null, null)
        val average = 375.0
        val expected = dailyBarsDescription(values, average)
        content { PartnerDailyBars(values = values, average = average) }
        compose.onNodeWithContentDescription(expected).assertExists()
    }
}
