package br.com.bragasaude.data.remote.api

import br.com.bragasaude.data.remote.auth.AuthService
import br.com.bragasaude.data.remote.model.MedicationCreate
import br.com.bragasaude.data.remote.model.MedicationTakeRequest
import br.com.bragasaude.data.remote.model.MedicationRestockRequest
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class BragaApiClientCareOsTest {
    private lateinit var server: MockWebServer
    private lateinit var api: BragaApiClient

    @Before
    fun setup() {
        server = MockWebServer().also { it.start() }
        val authService = mockk<AuthService>()
        every { authService.getTokenBlocking(any(), any()) } returns "care-os-token"
        api = BragaApiClient(mockk(relaxed = true), server.url("/").toString().trimEnd('/'), authService)
    }

    @After
    fun cleanup() = server.shutdown()

    @Test
    fun `EAN-13 lookup maps canonical ANVISA response`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""
            {
              "ean_barcode":"7891234567890",
              "name":"Losartana Potássica",
              "active_principle":"losartana potássica",
              "manufacturer":"Laboratório Exemplo",
              "source_name":"ANVISA",
              "source_checked_at":"2026-09-23T00:00:00Z"
            }
        """.trimIndent()))

        val result = api.lookupBarcode("7891234567890")

        assertEquals("7891234567890", result?.eanBarcode)
        assertEquals("Losartana Potássica", result?.name)
        assertEquals("ANVISA", result?.sourceName)
        assertEquals("/api/anvisa/medications/barcode/7891234567890", server.takeRequest().path)
    }

    @Test
    fun `medication creation returns gateway id and canonical body`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(201).setBody("""{"id":"remote-med-1","patient_id":"p1"}"""))

        val id = api.createMedication(
            "p1",
            MedicationCreate(
                name = "Losartana 50mg",
                scheduleTimes = listOf("08:00"),
                totalUnits = 30,
                confirmedWithPrescription = true
            )
        )

        assertEquals("remote-med-1", id)
        val request = server.takeRequest()
        assertEquals("/api/family/patients/p1/medications", request.path)
        assertTrue(request.body.readUtf8().contains("\"confirmed_with_prescription\":true"))
        assertEquals("Bearer care-os-token", request.getHeader("Authorization"))
    }

    @Test
    fun `409 duplicate is graceful but insufficient stock is failure`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(409).setBody("""{"detail":"Esta dose já foi confirmada por outro cuidador."}"""))
        server.enqueue(MockResponse().setResponseCode(409).setBody("""{"detail":"Estoque insuficiente para registrar esta dose."}"""))
        val body = MedicationTakeRequest("2026-09-23T08:00:00-03:00", 1, "p1_med1_2026-09-23T08:00")

        assertTrue(api.takeMedication("med1", body) is BragaApiClient.TakeMedicationResult.AlreadyTaken)
        val failure = api.takeMedication("med1", body)
        assertTrue(failure is BragaApiClient.TakeMedicationResult.Failure)
        assertTrue((failure as BragaApiClient.TakeMedicationResult.Failure).message.contains("Estoque insuficiente"))
    }

    @Test
    fun `restock uses canonical idempotent contract`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"status":"recorded","current_units":60}"""))

        val units = api.restockMedication("med1", MedicationRestockRequest(60, "restock-key-123"))

        assertEquals(60, units)
        val request = server.takeRequest()
        assertEquals("/api/medications/med1/restock", request.path)
        val body = request.body.readUtf8()
        assertTrue(body.contains("\"new_total_units\":60"))
        assertTrue(body.contains("\"idempotency_key\":\"restock-key-123\""))
    }

    @Test
    fun `daily bulletin and clinical correlations are consumed`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""
            {"patient_id":"p1","day":"2026-09-23","medications_taken":2,
             "medications_expected":3,"latest_blood_pressure":"120/80","hydration_ml":1500}
        """.trimIndent()))
        server.enqueue(MockResponse().setResponseCode(200).setBody("""
            {"patient_id":"p1","correlations":[{"event_at":"2026-09-23T12:00:00Z",
             "event_type":"BLOOD_PRESSURE_SPIKE","observation":"Pico após noite ruim",
             "evidence":["sleep_quality=1"],"window_hours":24}],
             "disclaimer":"Associação temporal; não é diagnóstico."}
        """.trimIndent()))

        val bulletin = api.getDailyBulletin("p1")
        val correlations = api.getClinicalCorrelations("p1")

        assertEquals(2, bulletin?.medicationsTaken)
        assertEquals(1500, bulletin?.hydrationMl)
        assertEquals("BLOOD_PRESSURE_SPIKE", correlations?.correlations?.single()?.eventType)
        assertEquals("/api/family/patients/p1/daily-bulletin", server.takeRequest().path)
        assertEquals("/api/patients/p1/clinical-correlations", server.takeRequest().path)
    }

    @Test
    fun `medical access rejects incomplete response`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(201).setBody("""{"access_token":"","qr_code_payload":""}"""))
        assertNull(api.generateMedicalAccess("p1"))
    }
}
