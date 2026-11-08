package br.com.bragasaude.ui.medication

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.inspector.WindowInspector
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
import java.io.File
import java.util.Date

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w320dp-h740dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RtlCardsInterfaceTest {
    @get:Rule val compose = createComposeRule()
    private val medication = MedicationEntity("med", "patient",
        "Cloridrato de metformina de liberação prolongada", dosageMg = 12.5, currentUnits = 28)

    @Test fun dosageAndOneThreeSixTimesFitAtLargeFont() {
        val allTimes = listOf("06:00", "09:00", "12:00", "15:00", "18:00", "21:00")
        val times = mutableStateOf(allTimes.take(1))
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.5f)) {
                BragasaudeTheme {
                    Column(Modifier.width(320.dp).padding(16.dp)) {
                        MedicationStockHeader(medication, false)
                        Spacer(Modifier.height(12.dp))
                        MedicationScheduleDetails(times.value, "APOS_CAFE")
                    }
                }
            }
        }
        val name = layout(medication.name)
        assertTrue(name.lineCount <= 2)
        assertEquals(1, layout("12,5 mg").lineCount)
        assertFalse(layout("12,5 mg").hasVisualOverflow)
        compose.onNodeWithText("28 comprimidos restantes").assertIsDisplayed()
        for (count in listOf(1, 3, 6)) {
            compose.runOnIdle { times.value = allTimes.take(count) }
            for (time in allTimes.take(count)) {
                val node = compose.onNodeWithText(time).assertIsDisplayed()
                assertEquals(1, layout(time).lineCount)
                assertFalse(layout(time).hasVisualOverflow)
                assertTrue(node.fetchSemanticsNode().boundsInRoot.right <= 304f)
            }
            compose.onNodeWithText("Após o café da manhã").assertIsDisplayed()
            screenshot("rtl-medicamento-$count-horarios-fonte-ampliada")
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
        compose.onNodeWithText("Enviar Lembrete").performScrollTo().assertIsDisplayed()
        screenshot("rtl-medicamento-visualizacao")
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
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.5f)) {
                BragasaudeTheme {
                    Box(Modifier.width(320.dp).padding(16.dp)) {
                        ConsultationSummaryItem(ConsultationEntity("c", "patient", "self", "Ana", "Filha",
                            "Cardiologia", scheduledDate = Date(1_900_000_000_000)), status.value, { cancelled = true })
                    }
                }
            }
        }
        val badge = compose.onNodeWithText("Avisos: 24h e 1h antes").assertIsDisplayed()
        val cancel = compose.onNodeWithContentDescription("Cancelar Consulta")
        assertTrue(badge.fetchSemanticsNode().boundsInRoot.top >= cancel.fetchSemanticsNode().boundsInRoot.bottom)
        assertEquals(1, layout("Avisos: 24h e 1h antes").lineCount)
        assertFalse(layout("Avisos: 24h e 1h antes").hasVisualOverflow)
        cancel.performClick()
        assertTrue(cancelled)
        screenshot("rtl-consulta-avisos-fonte-ampliada")
        compose.runOnIdle { status.value = status.value.copy(deviceEnabled = false) }
        compose.onNodeWithText("Avisos bloqueados no aparelho").assertIsDisplayed()
        compose.onNodeWithText("Avisos: 24h e 1h antes").assertDoesNotExist()
    }

    private fun layout(text: String): TextLayoutResult {
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(text, useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        return layouts.single()
    }

    private fun screenshot(name: String) = compose.runOnIdle {
        val view = WindowInspector.getGlobalWindowViews().first()
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        val file = File("build/outputs/ui-review/$name.png")
        file.parentFile.mkdirs()
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}
