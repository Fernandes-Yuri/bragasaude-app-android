package br.com.bragasaude.domain

import br.com.bragasaude.data.remote.repository.MedicationRepository
import com.google.firebase.auth.FirebaseAuth
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceHealthExecutorMedicationTest {
    @Test fun `hydration evaluates achievement after saving the voice record`() = runTest {
        val vitals = mockk<br.com.bragasaude.data.remote.repository.VitalsRepository>(relaxed = true)
        val engine = mockk<HealthEngine>(relaxed = true)
        val auth = mockk<FirebaseAuth>(relaxed = true)
        every { auth.currentUser?.uid } returns "patient"
        val executor = VoiceHealthExecutor(vitals, mockk(), mockk(), mockk(), mockk(), mockk(), auth, engine)
        executor.execute(VoiceHealthIntent.Hydration(250, "um copo"))
        coVerifyOrder {
            vitals.saveVitalSigns(match { it.single().hydrationMl == 250 })
            engine.analyzeVitals("patient", match { it.single().hydrationMl == 250 }, false)
        }
    }

    @Test fun `executor legado nao cria medicamento nem marca dose por nome parcial`() = runTest {
        val medications = mockk<MedicationRepository>()
        val auth = mockk<FirebaseAuth>(relaxed = true)
        val executor = VoiceHealthExecutor(mockk(), mockk(), medications, mockk(), mockk(), mockk(), auth, mockk())
        for (query in listOf("manhã", "pressão", "losartana", "remédio desconhecido")) {
            val reply = executor.execute(VoiceHealthIntent.Medication(query, "08:00"))
            assertTrue(reply.contains("Medicamentos por horário"))
            assertTrue(reply.contains("Nenhum medicamento foi criado"))
        }
        verify { medications wasNot Called }
    }
}
