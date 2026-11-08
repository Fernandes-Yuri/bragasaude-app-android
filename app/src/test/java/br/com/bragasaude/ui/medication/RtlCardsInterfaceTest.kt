package br.com.bragasaude.ui.medication

import android.app.Application
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import br.com.bragasaude.data.local.ConsultationEntity
import br.com.bragasaude.data.local.MedicationEntity
import br.com.bragasaude.data.medicationdoses.MedicationStockDose
import br.com.bragasaude.ui.care.*
import br.com.bragasaude.ui.family.ConsultationReminderStatus
import br.com.bragasaude.ui.family.ConsultationSummaryItem
import br.com.bragasaude.ui.theme.BragasaudeTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.Date

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w411dp-h1000dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RtlCardsInterfaceTest {
    @get:Rule val compose = createComposeRule()
    private val medication = MedicationEntity("med", "patient",
        "Cloridrato de metformina de liberação prolongada", dosageMg = 12.5, currentUnits = 28)

    @Test fun dosageAndOneThreeSixTimesFitAcrossCompactWidthsAndFontScales() {
        val allTimes = listOf("06:00", "09:00", "12:00", "15:00", "18:00", "21:00")
        val times = mutableStateOf(allTimes.take(1))
        val width = mutableStateOf(320)
        val fontScale = mutableStateOf(1f)
        val record = mutableStateOf(medication)
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale.value)) {
                BragasaudeTheme {
                    Column(Modifier.width(width.value.dp).padding(32.dp)) {
                        MedicationStockHeader(record.value, false)
                        Spacer(Modifier.height(12.dp))
                        MedicationScheduleDetails(times.value, "APOS_CAFE")
                    }
                }
            }
        }
        val scenarios = listOf(
            medication.copy(name = "Losartana", dosageMg = 50.0) to "50 mg",
            medication to "12,5 mg",
            medication.copy(dosage = "50/12,5 mg", currentUnits = 1234) to "50/12,5 mg",
            medication.copy(dosage = null, dosageMg = null) to null
        )
        for (size in listOf(320, 360, 411)) for (scale in listOf(1f, 1.3f, 2f)) {
            for ((item, doseLabel) in scenarios) {
                compose.runOnIdle { width.value = size; fontScale.value = scale; record.value = item }
                assertTrue(layout(item.name).lineCount <= 2)
                doseLabel?.let {
                    assertEquals(1, layout(it).lineCount)
                    assertFalse(layout(it).hasVisualOverflow)
                } ?: compose.onNodeWithText("12,5 mg").assertDoesNotExist()
                compose.onNodeWithText("${item.currentUnits} comprimidos restantes").assertIsDisplayed()
                for (count in listOf(1, 3, 6)) {
                    compose.runOnIdle { times.value = allTimes.take(count) }
                    for (time in allTimes.take(count)) {
                        val node = compose.onNodeWithText(time).assertIsDisplayed()
                        assertEquals(1, layout(time).lineCount)
                        assertFalse(layout(time).hasVisualOverflow)
                        assertTrue(node.fetchSemanticsNode().boundsInRoot.right <= size - 32f)
                    }
                    compose.onNodeWithText("Após o café da manhã").assertIsDisplayed()
                }
            }
        }
    }

    @Test fun viewerHasNoRestockButtonAndLosingPermissionClosesTheForm() {
        val canWrite = mutableStateOf(true)
        compose.setContent {
            BragasaudeTheme {
                Column(Modifier.width(320.dp).verticalScroll(rememberScrollState()).padding(16.dp)) {
                    MedicationStockCard(medication, 20.0, false, MedicationStockDose(), false,
                        onTake = {}, onRestock = {}, onDelete = {}, onEditSchedule = { _, _, _ -> },
                        canTake = false, canRestock = canWrite.value, canEdit = canWrite.value,
                        canDelete = canWrite.value, loading = false)
                }
            }
        }
        compose.onNodeWithText("Reabastecer").performScrollTo().performClick()
        compose.onNodeWithText("Confirmar reposição").assertExists()
        compose.runOnIdle { canWrite.value = false }
        compose.onNodeWithText("Reabastecer").assertDoesNotExist()
        compose.onNodeWithText("Confirmar reposição").assertDoesNotExist()
        compose.onNodeWithText("Editar").assertDoesNotExist()
        compose.onNodeWithText("Enviar Lembrete").assertDoesNotExist()
        compose.onNodeWithText("Você pode visualizar os medicamentos.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Agenda do aparelho").performScrollTo().performClick()
        compose.onNodeWithText("Adicionar à Agenda do Celular").assertIsDisplayed()
    }

    @Test fun patientMenuExplainsWhoseMedicationAndEffectiveAccess() {
        val self = CarePatientOption("self", "Ana", true, true, true, true, "PATIENT")
        val viewer = CarePatientOption("patient", "Maria", false, false, false, false, "CAREGIVER_VIEWER")
        compose.setContent {
            BragasaudeTheme {
                CarePatientSelector(CareOsUiState(patients = listOf(self, viewer), selectedPatientId = "patient",
                    userRole = "CAREGIVER", isCaregiver = true), {}, "De quem são os medicamentos?")
            }
        }
        compose.onNodeWithText("De quem são os medicamentos?").assertIsDisplayed()
        compose.onNodeWithText("Maria").performClick()
        compose.onNodeWithText("Minha saúde").assertIsDisplayed()
        compose.onNodeWithText("Pode visualizar").assertIsDisplayed()
        compose.onNodeWithText("CAREGIVER VIEWER").assertDoesNotExist()
    }

    @Test fun consultationBadgeHasItsOwnLineBelowCancelAtLargeFont() {
        var cancelled = false
        val status = mutableStateOf(ConsultationReminderStatus(setOf("24h", "1h"), loading = false))
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                BragasaudeTheme {
                    Box(Modifier.width(320.dp).padding(32.dp)) {
                        ConsultationSummaryItem(ConsultationEntity("c", "patient", "self", "Ana", "Filha",
                            "Cardiologia", scheduledDate = Date(1_900_000_000_000), status = "ACCEPTED"), status.value, { cancelled = true })
                    }
                }
            }
        }
        val badge = compose.onNodeWithContentDescription(status.value.accessibilityLabel).assertIsDisplayed()
        val cancel = compose.onNodeWithContentDescription("Cancelar Consulta")
        assertTrue(badge.fetchSemanticsNode().boundsInRoot.top >= cancel.fetchSemanticsNode().boundsInRoot.bottom)
        assertEquals(1, layout("24h e 1h antes").lineCount)
        assertFalse(layout("24h e 1h antes").hasVisualOverflow)
        cancel.performClick()
        assertTrue(cancelled)
        compose.runOnIdle { status.value = status.value.copy(deviceEnabled = false) }
        compose.onNodeWithText("Avisos bloqueados no aparelho").assertIsDisplayed()
        compose.onNodeWithText("24h e 1h antes", useUnmergedTree = true).assertDoesNotExist()
    }

    private fun layout(text: String): TextLayoutResult {
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(text, useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        return layouts.single()
    }
}
