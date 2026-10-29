package br.com.bragasaude.ui.home

import android.app.Application
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import br.com.bragasaude.ui.theme.BragasaudeTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w360dp-h800dp-mdpi")
class NotificationsInterfaceTest {
    @get:Rule val compose = createComposeRule()

    @Test fun opening_keeps_unread_notifications_until_explicit_action() {
        var reads = 0
        var opened: String? = null
        compose.setContent {
            BragasaudeTheme(forceThemeMode = "LIGHT") {
                NotificationsContent(listOf("Beba água", "Caminhada anterior"), setOf("Caminhada anterior"), emptyMap(),
                    true, null, {}, {}, { reads++ }, { opened = it }, {})
            }
        }
        compose.onNodeWithText("Novas (1)").assertIsDisplayed()
        compose.onNodeWithText("Lidas (1)").assertExists()
        compose.runOnIdle { assertEquals(0, reads); assertEquals(null, opened) }
        compose.onNodeWithText("Beba água").performClick()
        compose.runOnIdle { assertEquals("Beba água", opened) }
        compose.onNodeWithText("Marcar todas como lidas").performClick()
        compose.runOnIdle { assertEquals(1, reads) }
    }

    @Test fun read_only_list_is_history_and_battery_guidance_is_optional() {
        var settings = 0
        compose.setContent {
            BragasaudeTheme(forceThemeMode = "LIGHT") {
                NotificationsContent(listOf("Beba água"), setOf("Beba água"), emptyMap(),
                    false, null, {}, { settings++ }, {}, {}, {})
            }
        }
        compose.onNodeWithText("Histórico de avisos").assertIsDisplayed()
        compose.onNodeWithText("Marcar todas como lidas").assertDoesNotExist()
        compose.onNodeWithText("Tudo tranquilo por aqui").assertDoesNotExist()
        compose.onNodeWithText("Configurar bateria").assertDoesNotExist()
        compose.onNodeWithContentDescription("Ver orientação de bateria").performClick()
        compose.onNodeWithText("Configurar bateria").performClick()
        compose.runOnIdle { assertEquals(1, settings) }
        compose.onNodeWithContentDescription("Dispensar orientação de bateria").performClick()
        compose.onNodeWithText("Avisos em segundo plano").assertDoesNotExist()
        compose.onNodeWithText("Beba água").assertIsDisplayed()
    }

    @Test fun empty_alerts_displays_reassuring_empty_state() {
        compose.setContent {
            BragasaudeTheme(forceThemeMode = "LIGHT") {
                NotificationsContent(
                    alerts = emptyList(),
                    readAlerts = emptySet(),
                    identifiedAt = emptyMap(),
                    batteryAllowed = true,
                    batteryGuidance = null,
                    onBack = {},
                    onBatterySettings = {},
                    onMarkAllRead = {},
                    onOpen = {},
                    onDismiss = {}
                )
            }
        }
        compose.onNodeWithText("Tudo tranquilo por aqui").assertIsDisplayed()
        compose.onNodeWithText("Nenhum aviso no momento.").assertIsDisplayed()
    }

    @Test fun permission_alerts_render_under_settings_category_without_raw_prefix() {
        val permissionAlert = "PERMISSION_NOTIFICATIONS: Ative as notificações para receber alertas de saúde importantes."
        compose.setContent {
            BragasaudeTheme(forceThemeMode = "LIGHT") {
                NotificationsContent(
                    alerts = listOf(permissionAlert),
                    readAlerts = emptySet(),
                    identifiedAt = emptyMap(),
                    batteryAllowed = true,
                    batteryGuidance = null,
                    onBack = {},
                    onBatterySettings = {},
                    onMarkAllRead = {},
                    onOpen = {},
                    onDismiss = {}
                )
            }
        }
        compose.onNodeWithText("Configurações").assertIsDisplayed()
        compose.onNodeWithText("Ative as notificações para receber alertas de saúde importantes.").assertIsDisplayed()
    }
}
