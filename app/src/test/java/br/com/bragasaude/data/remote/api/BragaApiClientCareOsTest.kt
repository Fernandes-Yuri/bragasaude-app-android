package br.com.bragasaude.data.remote.api

import br.com.bragasaude.data.remote.auth.AuthService
import br.com.bragasaude.data.remote.model.MedicationCreate
import br.com.bragasaude.data.remote.model.MedicationTakeRequest
import br.com.bragasaude.data.remote.model.MedicationRestockRequest
import br.com.bragasaude.data.remote.model.MedicationBatchItemCreateDto
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.json.JSONObject
import br.com.bragasaude.util.safeString
import br.com.bragasaude.util.safeNullableString
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

    @Test
    fun `get symptoms diary parses remote entries and falls back on error`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""
            [
              {
                "id": "sym-1",
                "patient_id": "p1",
                "reported_by": "p1",
                "reported_at": "2026-09-23T08:00:00Z",
                "symptoms_text": "Dormi bem, sem dores",
                "sleep_quality": 4,
                "disposition": 5,
                "input_method": "VOICE"
              }
            ]
        """.trimIndent()))

        val diary = api.getSymptomsDiary("p1")
        assertEquals(1, diary.size)
        assertEquals("sym-1", diary.first().id)
        assertEquals("p1", diary.first().patientId)
        assertEquals(4, diary.first().sleepQuality)
        assertEquals(5, diary.first().disposition)
        assertEquals("Dormi bem, sem dores", diary.first().symptomsText)
        assertEquals("/api/patients/p1/symptoms-diary?limit=30", server.takeRequest().path)

        // Test fallback on 500 error
        server.enqueue(MockResponse().setResponseCode(500).setBody("""{"error":"internal"}"""))
        val emptyOnErr = api.getSymptomsDiary("p1")
        assertTrue(emptyOnErr.isEmpty())
    }

    @Test
    fun `analyze prescription maps dual check response with divergence flags`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""
            {
              "status": "success",
              "prescription_image_url": "https://api.bragasaude.online/api/files/prescriptions/uuid_foto.jpg",
              "total_pages_analyzed": 2,
              "duplicates_merged_count": 1,
              "medications": [
                {
                  "name": "Losartana Potássica",
                  "name_divergent": false,
                  "dosage": "50mg",
                  "dosage_divergent": false,
                  "dosage_mg": 50.0,
                  "frequency": "1x ao dia",
                  "frequency_divergent": false,
                  "suggested_times": ["08:00"],
                  "ean_barcode": "7896004715506",
                  "active_principle": "Losartana Potássica",
                  "confidence_score": 0.98,
                  "requires_human_fill": false,
                  "divergence_reason": null,
                  "source_pages": [1, 2],
                  "frequency_interval_hours": 24,
                  "daily_doses_count": 1,
                  "treatment_duration_days": 30,
                  "anvisa_registration_number": "1023501230012"
                },
                {
                  "name": "",
                  "name_divergent": true,
                  "dosage": "",
                  "dosage_divergent": true,
                  "dosage_mg": null,
                  "frequency": "",
                  "frequency_divergent": true,
                  "suggested_times": [],
                  "ean_barcode": null,
                  "active_principle": null,
                  "confidence_score": 0.42,
                  "requires_human_fill": true,
                  "divergence_reason": "Caligrafia médica duvidosa no item 2",
                  "source_pages": [1],
                  "frequency_interval_hours": null,
                  "daily_doses_count": 1,
                  "treatment_duration_days": null,
                  "anvisa_registration_number": null
                }
              ],
              "raw_ocr_snippet": "1. Losartana 50mg 1x dia 08:00\n2. ???"
            }
        """.trimIndent()))

        val dummyBytes = "fake prescription image bytes".toByteArray()
        val result = api.analyzePrescription("p1", "receita.jpg", "image/jpeg", dummyBytes)

        assertEquals("success", result?.status)
        assertEquals("https://api.bragasaude.online/api/files/prescriptions/uuid_foto.jpg", result?.prescriptionImageUrl)
        assertEquals(2, result?.totalPagesAnalyzed)
        assertEquals(1, result?.duplicatesMergedCount)
        assertEquals(2, result?.medications?.size)

        val med1 = result?.medications?.get(0)
        assertEquals("Losartana Potássica", med1?.name)
        assertEquals(false, med1?.nameDivergent)
        assertEquals("50mg", med1?.dosage)
        assertEquals(false, med1?.dosageDivergent)
        assertEquals(50.0, med1?.dosageMg ?: 0.0, 0.001)
        assertEquals(listOf("08:00"), med1?.suggestedTimes)
        assertEquals("7896004715506", med1?.eanBarcode)
        assertEquals(0.98, med1?.confidenceScore ?: 0.0, 0.001)
        assertEquals(false, med1?.requiresHumanFill)
        assertNull(med1?.divergenceReason)
        assertEquals(listOf(1, 2), med1?.sourcePages)
        assertEquals(24, med1?.frequencyIntervalHours)
        assertEquals(1, med1?.dailyDosesCount)
        assertEquals(30, med1?.treatmentDurationDays)
        assertEquals("1023501230012", med1?.anvisaRegistrationNumber)

        val med2 = result?.medications?.get(1)
        assertEquals("", med2?.name)
        assertEquals(true, med2?.nameDivergent)
        assertEquals(true, med2?.dosageDivergent)
        assertEquals(true, med2?.requiresHumanFill)
        assertEquals("Caligrafia médica duvidosa no item 2", med2?.divergenceReason)
        assertEquals(listOf(1), med2?.sourcePages)
        assertNull(med2?.frequencyIntervalHours)
        assertEquals(1, med2?.dailyDosesCount)
        assertNull(med2?.treatmentDurationDays)
        assertNull(med2?.anvisaRegistrationNumber)

        val request = server.takeRequest()
        assertEquals("/api/family/patients/p1/prescriptions/analyze", request.path)
        assertEquals("POST", request.method)
        assertEquals("Bearer care-os-token", request.getHeader("Authorization"))
        assertTrue(request.getHeader("Content-Type")?.contains("multipart/form-data") == true)
    }

    @Test
    fun `createMedicationsBatch sends batch request and returns success`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(201).setBody("""
            {
              "status": "success",
              "created_count": 2,
              "ids": ["med-1", "med-2"]
            }
        """.trimIndent()))

        val items = listOf(
            MedicationBatchItemCreateDto(
                name = "Losartana Potássica 50mg",
                dosageMg = 50.0,
                scheduleTimes = listOf("08:00"),
                totalUnits = 30,
                eanBarcode = "7896004715506",
                confirmedWithPrescription = true,
                frequencyIntervalHours = 24,
                treatmentDurationDays = 30,
                anvisaRegistrationNumber = "1023501230012"
            ),
            MedicationBatchItemCreateDto(
                name = "Metformina 850mg",
                dosageMg = 850.0,
                scheduleTimes = listOf("12:00", "20:00"),
                totalUnits = 60,
                eanBarcode = "7896004715507",
                confirmedWithPrescription = true,
                frequencyIntervalHours = 12,
                treatmentDurationDays = 60,
                anvisaRegistrationNumber = null
            )
        )

        val result = api.createMedicationsBatch("p1", items)
        assertTrue(result)

        val request = server.takeRequest()
        assertEquals("/api/family/patients/p1/medications/batch", request.path)
        assertEquals("POST", request.method)
        assertEquals("Bearer care-os-token", request.getHeader("Authorization"))
        assertEquals("application/json", request.getHeader("Content-Type"))

        val body = request.body.readUtf8()
        assertTrue(body.contains(""""name":"Losartana Potássica 50mg""""))
        assertTrue(body.contains(""""dosage_mg":50"""))
        assertTrue(body.contains(""""total_units":30"""))
        assertTrue(body.contains(""""frequency_interval_hours":24"""))
        assertTrue(body.contains(""""treatment_duration_days":30"""))
        assertTrue(body.contains(""""anvisa_registration_number":"1023501230012""""))
        assertTrue(body.contains(""""name":"Metformina 850mg""""))
        assertTrue(body.contains(""""total_units":60"""))
        assertTrue(body.contains(""""confirmed_with_prescription":true"""))
        assertTrue(body.contains(""""schedule_times":["12:00","20:00"]"""))

        // Error case: 500 response
        server.enqueue(MockResponse().setResponseCode(500).setBody("""{"error":"internal"}"""))
        val failResult = api.createMedicationsBatch("p1", items)
        assertFalse(failResult)
    }

    @Test
    fun `safeString and safeNullableString properly filter null none and undefined`() {
        val json = JSONObject("""
            {
              "literal_null": "null",
              "literal_none": "none",
              "literal_undefined": "undefined",
              "actual_null": null,
              "blank_str": "   ",
              "valid_str": "Losartana 50mg"
            }
        """.trimIndent())

        assertEquals("", json.safeString("literal_null"))
        assertEquals("", json.safeString("literal_none"))
        assertEquals("", json.safeString("literal_undefined"))
        assertEquals("", json.safeString("actual_null"))
        assertEquals("", json.safeString("missing_key"))
        assertEquals("Losartana 50mg", json.safeString("valid_str"))

        assertNull(json.safeNullableString("literal_null"))
        assertNull(json.safeNullableString("literal_none"))
        assertNull(json.safeNullableString("literal_undefined"))
        assertNull(json.safeNullableString("actual_null"))
        assertNull(json.safeNullableString("blank_str"))
        assertNull(json.safeNullableString("missing_key"))
        assertEquals("Losartana 50mg", json.safeNullableString("valid_str"))
    }
}
