package br.com.bragasaude.domain

import org.junit.Assert.*
import org.junit.Test

/**
 * Suíte de testes do motor de reconciliação de atividade.
 *
 * Cada teste é um cenário sintético tipado (uma sessão de caminhada/corrida/carro/etc.)
 * com entrada e resultado esperado. Servem como especificação executável: se um
 * limiar mudar, o teste correspondente deve mudar junto.
 */
class ActivityReconcilerTest {

    // ------------------------------------------------------------------
    // [1] OUTDOOR DE QUALIDADE — GPS e passos concordam, caminhada real
    // ------------------------------------------------------------------
    @Test
    fun `reconcile outdoor quality walk returns GPS distance with high reliability`() {
        // 10 min de caminhada, 800m no GPS, 1050 passos × 0.70m = 735m estimados
        val input = ReconciliationInput(
            gpsMeters = 800f,
            stepMeters = 735f,
            steps = 1050,
            durationSec = 600L // 4.8 km/h
        )
        val result = ActivityReconciler.reconcile(input)

        assertEquals(DistanceSource.GPS, result.source)
        assertEquals(800f, result.finalMeters, 0.01f)
        assertEquals(ActivityThresholds.RELIABILITY_QUALITY, result.reliability, 0.001f)
        assertTrue(ActivityFlag.OUTDOOR_QUALITY in result.flags)
    }

    // ------------------------------------------------------------------
    // [2] MÉDIA CONFIÁVEL — fontes discordam moderadamente (15–30%)
    // ------------------------------------------------------------------
    @Test
    fun `reconcile moderate disagreement averages both sources`() {
        // 12 min, GPS 1000m, 1100 passos × 0.70m = 770m -> erro 23%
        val input = ReconciliationInput(
            gpsMeters = 1000f,
            stepMeters = 770f,
            steps = 1100,
            durationSec = 720L
        )
        val result = ActivityReconciler.reconcile(input)

        assertEquals(DistanceSource.AVERAGE, result.source)
        assertEquals(885f, result.finalMeters, 0.01f) // (1000+770)/2
        assertEquals(ActivityThresholds.RELIABILITY_AVG, result.reliability, 0.001f)
        assertTrue(ActivityFlag.OUTDOOR_QUALITY in result.flags)
    }

    // ------------------------------------------------------------------
    // [3] INDOOR / ESTEIRA — GPS irrelevante, confia nos passos
    // ------------------------------------------------------------------
    @Test
    fun `reconcile treadmill indoor relies on steps with low reliability`() {
        // 30 min de esteira, GPS 12m (drift mínimo), 4000 passos × 0.70m = 2800m
        val input = ReconciliationInput(
            gpsMeters = 12f,
            stepMeters = 2800f,
            steps = 4000,
            durationSec = 1800L
        )
        val result = ActivityReconciler.reconcile(input)

        assertEquals(DistanceSource.STEPS, result.source)
        assertEquals(2800f, result.finalMeters, 0.01f)
        assertEquals(ActivityThresholds.RELIABILITY_INDOOR, result.reliability, 0.001f)
        assertTrue(ActivityFlag.INDOOR in result.flags)
    }

    // ------------------------------------------------------------------
    // [4] VEÍCULO — passos espúrios por solavanco de carro
    // ------------------------------------------------------------------
    @Test
    fun `reconcile car movement detects vehicle and trusts GPS`() {
        // 20 min dirigindo, 18km no GPS, 200 "passos" de vibração -> stride 90m/passo
        val input = ReconciliationInput(
            gpsMeters = 18000f,
            stepMeters = 140f, // 200 × 0.70
            steps = 200,
            durationSec = 1200L // 54 km/h
        )
        val result = ActivityReconciler.reconcile(input)

        assertEquals(DistanceSource.GPS, result.source)
        assertEquals(18000f, result.finalMeters, 0.01f)
        assertEquals(ActivityThresholds.RELIABILITY_VEHICLE, result.reliability, 0.001f)
        assertTrue(ActivityFlag.VEHICLE in result.flags)
    }

    // ------------------------------------------------------------------
    // [5] GLITCH DE GPS — teleporte, velocidade impossível
    // ------------------------------------------------------------------
    @Test
    fun `reconcile gps teleport glitch discards GPS`() {
        // GPS saltou 2km em 2 segundos (3600 km/h), sem passos
        val input = ReconciliationInput(
            gpsMeters = 2000f,
            stepMeters = 0f,
            steps = 0,
            durationSec = 2L
        )
        val result = ActivityReconciler.reconcile(input)

        assertEquals(DistanceSource.DISCARDED, result.source)
        assertEquals(0f, result.finalMeters, 0.01f)
        assertEquals(ActivityThresholds.RELIABILITY_GLITCH, result.reliability, 0.001f)
        assertTrue(ActivityFlag.GPS_GLITCH in result.flags)
    }

    // ------------------------------------------------------------------
    // [6] GPS PERDIDO — túnel/garagem, só passos
    // ------------------------------------------------------------------
    @Test
    fun `reconcile tunnel no GPS fix uses steps`() {
        // 15 min caminhando em túnel, GPS zero, 2000 passos × 0.70m = 1400m
        val input = ReconciliationInput(
            gpsMeters = 0f,
            stepMeters = 1400f,
            steps = 2000,
            durationSec = 900L
        )
        val result = ActivityReconciler.reconcile(input)

        assertEquals(DistanceSource.STEPS, result.source)
        assertEquals(1400f, result.finalMeters, 0.01f)
        assertEquals(ActivityThresholds.RELIABILITY_INDOOR, result.reliability, 0.001f)
        assertTrue(ActivityFlag.GPS_LOST in result.flags)
    }

    // ------------------------------------------------------------------
    // [7] DISCORDÂNCIA FORTE — GPS e passos diferem > 30%
    // ------------------------------------------------------------------
    @Test
    fun `reconcile strong disagreement takes minimum to avoid inflation`() {
        // GPS 1000m, passos 500 × 0.70m = 350m -> erro 65% (GPS provavelmente com drift)
        val input = ReconciliationInput(
            gpsMeters = 1000f,
            stepMeters = 350f,
            steps = 500,
            durationSec = 600L // 6 km/h
        )
        val result = ActivityReconciler.reconcile(input)

        assertEquals(DistanceSource.AVERAGE, result.source)
        assertEquals(350f, result.finalMeters, 0.01f) // min(1000, 350)
        assertEquals(ActivityThresholds.RELIABILITY_DISAGREE, result.reliability, 0.001f)
        assertTrue(ActivityFlag.LOW_AGREEMENT in result.flags)
    }

    // ------------------------------------------------------------------
    // [8] CORREDOR — não deve ser classificado como veículo
    // ------------------------------------------------------------------
    @Test
    fun `reconcile runner with calibrated stride is not vehicle`() {
        // 15 min correndo a 12 km/h, 3000m no GPS, 2200 passos × 1.36m = ~2990m
        val input = ReconciliationInput(
            gpsMeters = 3000f,
            stepMeters = 2990f,
            steps = 2200,
            durationSec = 900L // 12 km/h, stride efetivo 1.36m (< 2.5 -> não é veículo)
        )
        val result = ActivityReconciler.reconcile(input)

        // Demonstra que, com passada calibrada, o corredor cai em alta confiabilidade
        assertEquals(DistanceSource.GPS, result.source)
        assertEquals(3000f, result.finalMeters, 0.01f)
        assertEquals(ActivityThresholds.RELIABILITY_QUALITY, result.reliability, 0.001f)
        assertTrue(ActivityFlag.OUTDOOR_QUALITY in result.flags)
        assertFalse(ActivityFlag.VEHICLE in result.flags)
    }

    // ------------------------------------------------------------------
    // [9] BICICLETA — movimento real sem passos, confia no GPS
    // ------------------------------------------------------------------
    @Test
    fun `reconcile cycling without steps trusts GPS`() {
        // 15 min de bike, 5km no GPS, zero passos (não há solavanco de carro)
        val input = ReconciliationInput(
            gpsMeters = 5000f,
            stepMeters = 0f,
            steps = 0,
            durationSec = 900L // 20 km/h
        )
        val result = ActivityReconciler.reconcile(input)

        assertEquals(DistanceSource.GPS, result.source)
        assertEquals(5000f, result.finalMeters, 0.01f)
        assertEquals(ActivityThresholds.RELIABILITY_VEHICLE, result.reliability, 0.001f)
        assertTrue(ActivityFlag.VEHICLE in result.flags) // movimento não-pedestre
    }

    // ------------------------------------------------------------------
    // [10] SESSÃO NULA — sem dados suficientes
    // ------------------------------------------------------------------
    @Test
    fun `reconcile null session discards everything`() {
        val input = ReconciliationInput(
            gpsMeters = 0f,
            stepMeters = 0f,
            steps = 0,
            durationSec = 0L
        )
        val result = ActivityReconciler.reconcile(input)

        assertEquals(DistanceSource.DISCARDED, result.source)
        assertEquals(0f, result.finalMeters, 0.01f)
        assertEquals(ActivityThresholds.RELIABILITY_NULL, result.reliability, 0.001f)
        assertTrue(ActivityFlag.NULL_SESSION in result.flags)
    }

    // ------------------------------------------------------------------
    // [11] DRIFT DE GPS PARADO — pouco movimento, sem passos
    // ------------------------------------------------------------------
    @Test
    fun `reconcile stationary GPS drift is discarded`() {
        // 1h parado, GPS acumula 30m de jitter, zero passos
        val input = ReconciliationInput(
            gpsMeters = 30f,
            stepMeters = 0f,
            steps = 0,
            durationSec = 3600L // ~0.03 km/h
        )
        val result = ActivityReconciler.reconcile(input)

        assertEquals(DistanceSource.DISCARDED, result.source)
        assertEquals(0f, result.finalMeters, 0.01f)
        assertEquals(ActivityThresholds.RELIABILITY_DRIFT, result.reliability, 0.001f)
        assertTrue(ActivityFlag.GPS_GLITCH in result.flags)
    }
}
