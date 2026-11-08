package br.com.bragasaude.ui.home

import android.app.Application
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import br.com.bragasaude.data.remote.repository.NotificationCategoryFilter
import br.com.bragasaude.data.remote.repository.NotificationDisplayItem
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
        compose.onNodeWithText("Voltar aos avisos").performClick()
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

    @Test fun persisted_items_render_cards_filter_by_category_and_trigger_actions() {
        var selectedFilter: NotificationCategoryFilter = NotificationCategoryFilter.ALL
        var openedItem: NotificationDisplayItem? = null
        var archivedItem: NotificationDisplayItem? = null

        val familyItem = NotificationDisplayItem(
            id = "family-1",
            revision = 1L,
            kind = "FAMILY_MESSAGE",
            category = "FAMILY",
            categoryLabel = "Família",
            title = "Nova mensagem de José",
            body = "Conversa de cuidado de José. Toque para abrir.",
            personLabel = "José",
            occurredAt = 1_700_000_000_000L,
            count = 1,
            unreadCount = 1,
            isRead = false,
            patientId = "jose-uid",
            resourceType = "family_chat",
            resourceId = "jose-uid"
        )
        val agendaItem = NotificationDisplayItem(
            id = "agenda-1",
            revision = 2L,
            kind = "CONSULTATION_CREATED",
            category = "AGENDA",
            categoryLabel = "Agenda",
            title = "Nova consulta agendada",
            body = "Cardiologista · Toque para abrir.",
            personLabel = null,
            occurredAt = 1_700_000_000_000L,
            count = 1,
            unreadCount = 0,
            isRead = true,
            patientId = null,
            resourceType = "consultation",
            resourceId = "consult-1"
        )

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
                    onDismiss = {},
                    persistedItems = listOf(familyItem, agendaItem),
                    selectedCategory = selectedFilter,
                    onSelectCategory = { selectedFilter = it },
                    showCategoryFilters = true,
                    onOpenPersistedItem = { openedItem = it },
                    onArchivePersistedItem = { archivedItem = it }
                )
            }
        }

        compose.onNodeWithTag("notificationCategoryFilters").assertIsDisplayed()
        compose.onNodeWithTag("notificationFilterChip_ALL").assertIsDisplayed()
        compose.onNodeWithTag("notificationFilterChip_FAMILY").assertIsDisplayed()
        compose.onNodeWithTag("notificationFilterChip_AGENDA").assertIsDisplayed()
        compose.onNodeWithTag("notificationFilterChip_COMMUNITY").assertIsDisplayed()
        compose.onNodeWithTag("notificationFilterChip_CARE").assertIsDisplayed()

        compose.onNodeWithText("Nova mensagem de José").assertIsDisplayed()
        compose.onNodeWithText("Abrir conversa").assertIsDisplayed()
        compose.onNodeWithText("Nova consulta agendada").assertIsDisplayed()

        compose.onNodeWithText("Nova mensagem de José").performClick()
        compose.runOnIdle { assertEquals("family-1", openedItem?.id) }

        compose.onAllNodesWithContentDescription("Arquivar aviso")[0].performClick()
        compose.runOnIdle { assertEquals("family-1", archivedItem?.id) }

        compose.onNodeWithTag("notificationFilterChip_AGENDA").performClick()
        compose.runOnIdle { assertEquals(NotificationCategoryFilter.AGENDA, selectedFilter) }
    }

    @Test fun load_more_button_renders_when_has_more_pages_and_triggers_callback() {
        var loadMoreCalled = 0
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
                    onDismiss = {},
                    persistedItems = listOf(
                        NotificationDisplayItem(
                            id = "med-stock",
                            revision = 1L,
                            kind = "MEDICATION_LOW_STOCK",
                            category = "CARE",
                            categoryLabel = "Cuidado",
                            title = "Estoque baixo: Losartana",
                            body = "Restam 2 unidades.",
                            personLabel = null,
                            occurredAt = 1_700_000_000_000L,
                            count = 1,
                            unreadCount = 1,
                            isRead = false,
                            patientId = null,
                            resourceType = "medication",
                            resourceId = "med-1"
                        )
                    ),
                    hasMorePages = true,
                    isLoadingMore = false,
                    onLoadMore = { loadMoreCalled++ }
                )
            }
        }

        compose.onNodeWithTag("notificationLoadMoreButton").assertIsDisplayed()
        compose.onNodeWithText("Carregar mais avisos").performClick()
        compose.runOnIdle { assertEquals(1, loadMoreCalled) }
    }

    @Test fun unavailable_destination_sheet_renders_message_and_dismisses() {
        var dismissCalled = 0
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
                    onDismiss = {},
                    unavailableMessage = "O vínculo familiar foi encerrado. Os dados clínicos desta pessoa não estão mais disponíveis.",
                    onDismissUnavailableMessage = { dismissCalled++ }
                )
            }
        }

        compose.onNodeWithText("Conteúdo indisponível").assertIsDisplayed()
        compose.onNodeWithTag("notificationUnavailableMessage").assertIsDisplayed()
        compose.onNodeWithText("Entendi").performClick()
        compose.runOnIdle { assertEquals(1, dismissCalled) }
    }
}
