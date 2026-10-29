package br.com.bragasaude.ui.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PermissionAlertFilterTest {

    private fun filterAlerts(
        alerts: List<String>,
        hasNotifications: Boolean,
        hasLocation: Boolean,
        hasActivity: Boolean
    ): List<String> {
        return alerts.filterNot { alert ->
            when {
                alert.startsWith("PERMISSION_NOTIFICATIONS:") -> hasNotifications
                alert.startsWith("PERMISSION_REQUIRED:") -> (hasLocation && hasActivity)
                else -> false
            }
        }
    }

    @Test
    fun `quando notificacoes sao concedidas, remove o alerta de notificacao preservando clinicos`() {
        val initial = listOf(
            "PERMISSION_NOTIFICATIONS: Ative as notificações para receber alertas de saúde importantes.",
            "Beba água: meta diária pendente",
            "Tomar remédio das 14h"
        )
        val result = filterAlerts(initial, hasNotifications = true, hasLocation = false, hasActivity = false)
        assertEquals(2, result.size)
        assertFalse(result.any { it.startsWith("PERMISSION_NOTIFICATIONS:") })
        assertTrue(result.contains("Beba água: meta diária pendente"))
        assertTrue(result.contains("Tomar remédio das 14h"))
    }

    @Test
    fun `quando rastreamento e passos sao concedidos, remove o alerta de monitoramento`() {
        val initial = listOf(
            "PERMISSION_REQUIRED: Ative o monitoramento de passos para melhorar seu Score.",
            "Pressão registrada hoje"
        )
        val result = filterAlerts(initial, hasNotifications = false, hasLocation = true, hasActivity = true)
        assertEquals(1, result.size)
        assertEquals("Pressão registrada hoje", result[0])
    }

    @Test
    fun `quando apenas uma das permissoes de passos e concedida, mantem o alerta`() {
        val initial = listOf(
            "PERMISSION_REQUIRED: Ative o monitoramento de passos para melhorar seu Score."
        )
        val result1 = filterAlerts(initial, hasNotifications = false, hasLocation = true, hasActivity = false)
        assertEquals(1, result1.size)

        val result2 = filterAlerts(initial, hasNotifications = false, hasLocation = false, hasActivity = true)
        assertEquals(1, result2.size)
    }

    @Test
    fun `quando todas as permissoes sao concedidas e nao ha outros avisos, lista fica vazia`() {
        val initial = listOf(
            "PERMISSION_NOTIFICATIONS: Ative as notificações para receber alertas de saúde importantes.",
            "PERMISSION_REQUIRED: Ative o monitoramento de passos para melhorar seu Score."
        )
        val result = filterAlerts(initial, hasNotifications = true, hasLocation = true, hasActivity = true)
        assertTrue(result.isEmpty())
    }
}
