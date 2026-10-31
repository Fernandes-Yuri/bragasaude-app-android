package br.com.bragasaude.ui.home

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.inspector.WindowInspector
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import br.com.bragasaude.domain.HealthEngine
import br.com.bragasaude.ui.theme.BragasaudeTheme
import br.com.bragasaude.ui.util.Screen
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w360dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NotificationsScreenTest {
    @get:Rule val compose = createComposeRule()

    private var opened: String? = null
    private var dismissed: String? = null
    private var destination: Screen? = null
    private var notificationRequests = 0
    private var monitoringRequests = 0

    private fun show(alert: String, fontScale: Float = 1f) {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                BragasaudeTheme(forceThemeMode = "LIGHT") {
                    NotificationsContent(
                        alerts = listOf(alert), readAlerts = emptySet(), identifiedAt = emptyMap(),
                        batteryAllowed = true, batteryGuidance = null,
                        onBack = {}, onBatterySettings = {}, onMarkAllRead = {},
                        onOpen = { opened = it }, onDismiss = { dismissed = it },
                        onNotificationPermission = { notificationRequests++ },
                        onMonitoringPermission = { monitoringRequests++ },
                        onNavigateToScreen = { destination = it }
                    )
                }
            }
        }
        compose.onNodeWithText("Ler aviso").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(alert, opened) }
    }

    @Test fun readMovementAlertShowsTitleAndCompleteGuidanceBeforeTheActions() {
        val action = "Você está em repouso há algum tempo. Que tal uma breve caminhada ou alongamento?"
        val alert = HealthEngine.ActivityRecommendation("Momento de Movimento", action).alertText
        show(alert)

        compose.onNodeWithTag("notificationAlertTitle").assertTextEquals("Momento de Movimento").assertIsDisplayed()
        val body = compose.onNodeWithTag("notificationAlertBody").assertTextEquals(action).assertIsDisplayed()
            .fetchSemanticsNode().boundsInRoot
        val button = compose.onNodeWithText("Ver passos").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertTrue("A orientação deve aparecer antes das ações", body.bottom <= button.top)
        assertTrue("O corpo não deve deixar uma área vazia antes das ações", button.top - body.bottom < 80f)
        screenshot("spec-15-movimento")
        compose.onNodeWithText("Ver passos").performClick()
        compose.runOnIdle {
            assertEquals(Screen.Steps, destination)
            assertNull(dismissed)
        }
        compose.onNodeWithTag("notificationAlertBody").assertDoesNotExist()
    }

    @Test fun achievementKeepsItsPersonalGoalAndReturnDoesNotDismissIt() {
        val alert = HealthEngine.ActivityRecommendation(
            "Meta de Passos Alcançada!", "Parabéns! Você atingiu sua meta de 6000 passos hoje.", isAchievement = true
        ).alertText
        show(alert)

        compose.onNodeWithTag("notificationAlertBody")
            .assertTextEquals("Parabéns! Você atingiu sua meta de 6000 passos hoje.").assertIsDisplayed()
        compose.onNodeWithText("Voltar aos avisos").performClick()
        compose.onNodeWithTag("notificationAlertBody").assertDoesNotExist()
        compose.runOnIdle { assertNull(dismissed); assertNull(destination) }
        compose.onNodeWithText("Ler aviso").performClick()
        compose.onNodeWithText("Dispensar aviso").performClick()
        compose.runOnIdle { assertEquals(alert, dismissed) }
        compose.onNodeWithTag("notificationAlertBody").assertDoesNotExist()
    }

    @Test fun notificationPermissionExplainsWhyAndRequestsOnlyNotifications() {
        show("PERMISSION_NOTIFICATIONS: Ative as notificações para receber alertas de saúde importantes.")

        compose.onNodeWithTag("notificationAlertTitle")
            .assertTextEquals("Ative as notificações para receber alertas de saúde importantes.")
        compose.onNodeWithTag("notificationAlertBody").assertTextContains("mesmo com o aplicativo fechado", substring = true)
        compose.onNodeWithText("Configurar permissões").assertIsDisplayed().performClick()
        compose.runOnIdle {
            assertEquals(1, notificationRequests)
            assertEquals(0, monitoringRequests)
            assertNull(destination)
            assertNull(dismissed)
        }
        compose.onNodeWithTag("notificationAlertBody").assertDoesNotExist()
    }

    @Test fun monitoringPermissionExplainsBothAccessesAndRequestsMonitoring() {
        show("PERMISSION_REQUIRED: Ative o monitoramento de passos para melhorar seu Score.")

        compose.onNodeWithTag("notificationAlertTitle")
            .assertTextEquals("Ative o monitoramento de passos para melhorar seu Score.")
        compose.onNodeWithTag("notificationAlertBody").assertTextContains("atividade física e à localização", substring = true)
        compose.onNodeWithText("Ver passos").assertDoesNotExist()
        compose.onNodeWithText("Configurar permissões").assertIsDisplayed().performClick()
        compose.runOnIdle {
            assertEquals(0, notificationRequests)
            assertEquals(1, monitoringRequests)
            assertNull(destination)
            assertNull(dismissed)
        }
    }

    @Test fun hydrationGuidanceKeepsAllParagraphsWithLargeTextAndAnAccessibleAction() {
        val action = (1..8).joinToString("\n\n") { "Orientação $it: acompanhe os registros de água ao longo do dia." }
        show("Lembrete de hidratação\n\n$action", fontScale = 1.5f)

        compose.onNodeWithTag("notificationAlertBody").assertTextEquals(action)
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithTag("notificationAlertBody")
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertFalse("A orientação completa não pode ser truncada", layouts.single().hasVisualOverflow)
        compose.onNode(hasScrollAction() and hasAnyDescendant(hasTestTag("notificationAlertBody")))
            .performTouchInput { swipeUp() }
        compose.onNodeWithText("Registrar água").assertIsDisplayed()
        compose.onNodeWithText("Dispensar aviso").assertIsDisplayed()
        screenshot("spec-15-orientacao-longa")
        compose.onNodeWithText("Registrar água").performClick()
        compose.runOnIdle { assertEquals(Screen.Hydration(autoOpenDialog = true), destination) }
    }

    @Test fun medicationTitleControlsItsActionEvenWhenTheBodyMentionsWater() {
        show("Lembrete de medicamento\n\nConfira seu plano e tome com água conforme a orientação recebida.")

        compose.onNodeWithTag("notificationAlertBody")
            .assertTextEquals("Confira seu plano e tome com água conforme a orientação recebida.")
        compose.onNodeWithText("Registrar água").assertDoesNotExist()
        compose.onNodeWithText("Ver medicamentos").performClick()
        compose.runOnIdle { assertEquals(Screen.Reminders, destination) }
    }

    @Test fun legacySingleLineAlertIsShownInFullIncludingColonsAndTimes() {
        val alert = "Registro de saúde às 14:30: confira os valores e siga a orientação recebida."
        show(alert)

        compose.onNodeWithTag("notificationAlertBody").assertTextEquals(alert).assertIsDisplayed()
        compose.onNodeWithText("Voltar aos avisos").performClick()
        compose.runOnIdle { assertNull(destination); assertNull(dismissed) }
    }

    private fun screenshot(name: String) = compose.runOnIdle {
        val view = WindowInspector.getGlobalWindowViews().last()
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        val file = File("build/outputs/ui-review/$name.png")
        file.parentFile.mkdirs()
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}
