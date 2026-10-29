package br.com.bragasaude.ui.family

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class FamilySyncWarningTest {
    @get:Rule val compose = createComposeRule()

    @Test fun erroDeConexaoOfereceNovaTentativa() {
        var retries = 0
        compose.setContent { MaterialTheme { FamilySyncWarning(onRetry = { retries++ }) } }
        compose.onNodeWithText("Não foi possível atualizar os vínculos. Os dados salvos continuam disponíveis.").assertIsDisplayed()
        compose.onNodeWithText("Tentar novamente").performClick()
        assertEquals(1, retries)
    }
}
