package br.com.bragasaude.domain

import kotlin.math.abs

/**
 * Motor de Reconciliacao de Atividade - Braga Saude
 *
 * Cruza duas fontes independentes de distância (GPS e contador de passos) e produz
 * uma estimativa final acompanhada de um score de confiabilidade. Resolve a dupla
 * contagem estrutural que ocorria quando ambas as fontes eram somadas no mesmo
 * campo `distanceMeters` no MovementManager.
 *
 * Função pura, sem dependências Android — testável isoladamente no JVM.
 *
 * Pseudocódigo:
 * ```
 * IF duration <= 0 OR (gps == 0 AND steps == 0):
 *     → DISCARDED, reliability 0
 * speed_kmh = gps_meters / duration_sec * 3.6
 * IF speed_kmh > GLITCH:                      # teleporte de GPS (impossível)
 *     → usa passos se houver, senão descarta
 * IF steps > 0 AND (gps/steps) > STRIDE AND speed_kmh > VEHICLE_SPEED:
 *     → VEHICLE (passos espúrios de solavanco de carro)
 * IF steps == 0:
 *     IF gps < GPS_MIN_OUTDOOR: → drift, descarta
 *     ELSE: → movimento real sem passos (bicicleta/cadeira), confia no GPS
 * IF gps < GPS_MIN_OUTDOOR: → INDOOR (esteira/túnel), confia nos passos
 * erro% = |gps - steps| / gps * 100
 * IF erro% < QUALITY:  → OUTDOOR_QUALITY, final = gps, reliability 0.9
 * IF erro% < AVG:      → média das fontes, reliability 0.6
 * ELSE:                → discordância, final = min(gps, steps), reliability 0.4
 * ```
 */
object ActivityThresholds {
    // Distância mínima de GPS para considerar a sessão "outdoor" com ambas as fontes
    const val GPS_MIN_OUTDOOR_METERS = 50f

    // Stride efetivo (m/passo) acima do qual assumimos veículo: humano < 2.0, carro >> 5
    const val VEHICLE_STRIDE_METERS = 2.5f
    const val VEHICLE_SPEED_KMH = 8.0

    // Velocidade impossível = teleporte/glitch de GPS (acima de qualquer veículo real num app de saúde)
    const val GLITCH_SPEED_KMH = 150.0

    // Bandas de erro percentual entre GPS e passos
    const val ERROR_QUALITY_PERCENT = 15.0
    const val ERROR_AVG_PERCENT = 30.0

    // Scores de confiabilidade por cenário
    const val RELIABILITY_QUALITY = 0.9f
    const val RELIABILITY_AVG = 0.6f
    const val RELIABILITY_INDOOR = 0.4f
    const val RELIABILITY_VEHICLE = 0.5f
    const val RELIABILITY_GLITCH = 0.3f
    const val RELIABILITY_DISAGREE = 0.4f
    const val RELIABILITY_DRIFT = 0.1f
    const val RELIABILITY_NULL = 0.0f
}

enum class DistanceSource { GPS, STEPS, AVERAGE, DISCARDED }

enum class ActivityFlag {
    /** Sessão outdoor com GPS e passos concordando (< 15%). Fonte de calibração de passada. */
    OUTDOOR_QUALITY,
    /** Movimento indoor (esteira) ou GPS insuficiente. Distância vem dos passos. */
    INDOOR,
    /** GPS totalmente ausente (túnel/garagem). Distância vem dos passos. */
    GPS_LOST,
    /** Movimento não-pedestre (carro/bicicleta) — passos não devem render XP de caminhada. */
    VEHICLE,
    /** Glitch de GPS (teleporte/velocidade impossível). GPS descartado. */
    GPS_GLITCH,
    /** GPS e passos discordam (> 30%). Tomou o menor valor por segurança anti-inflação. */
    LOW_AGREEMENT,
    /** Sessão nula, sem dados suficientes. */
    NULL_SESSION
}

data class ReconciliationInput(
    /** Distância acumulada pelo GPS na sessão (metros). */
    val gpsMeters: Float,
    /** Distância estimada pelos passos (steps × passada) na sessão (metros). */
    val stepMeters: Float,
    /** Número de passos na sessão. */
    val steps: Int,
    /** Duração da sessão em segundos. */
    val durationSec: Long
)

data class ReconciliationResult(
    /** Distância final reconciliada (metros). É o valor a persistir/somar no total diário. */
    val finalMeters: Float,
    /** Score de confiabilidade 0..1. Abaixo de ~0.4 a sessão não deve render XP (Fase 5). */
    val reliability: Float,
    /** Fonte que originou o valor final. */
    val source: DistanceSource,
    /** Flags de classificação do cenário detectado (GPS x passos). */
    val flags: Set<ActivityFlag>
)

object ActivityReconciler {

    fun reconcile(input: ReconciliationInput): ReconciliationResult {
        val gps = input.gpsMeters
        val steps = input.steps
        val stepM = input.stepMeters
        val duration = input.durationSec

        // [1] Sessão nula: sem duração ou sem nenhum dado de entrada
        if (duration <= 0L || (gps <= 0f && steps <= 0)) {
            return ReconciliationResult(
                finalMeters = 0f,
                reliability = ActivityThresholds.RELIABILITY_NULL,
                source = DistanceSource.DISCARDED,
                flags = setOf(ActivityFlag.NULL_SESSION)
            )
        }

        val speedKmh = if (duration > 0L) gps.toDouble() / duration.toDouble() * 3.6 else 0.0

        // [2] Glitch de GPS: velocidade impossível (teleporte) — descarta o GPS
        if (speedKmh > ActivityThresholds.GLITCH_SPEED_KMH) {
            return if (steps > 0) {
                ReconciliationResult(
                    finalMeters = stepM,
                    reliability = ActivityThresholds.RELIABILITY_GLITCH,
                    source = DistanceSource.STEPS,
                    flags = setOf(ActivityFlag.GPS_GLITCH)
                )
            } else {
                ReconciliationResult(
                    finalMeters = 0f,
                    reliability = ActivityThresholds.RELIABILITY_GLITCH,
                    source = DistanceSource.DISCARDED,
                    flags = setOf(ActivityFlag.GPS_GLITCH)
                )
            }
        }

        // [3] Veículo: stride efetivo alto + velocidade alta = passos espúrios (solavanco de carro)
        if (steps > 0 && gps > 0f) {
            val effectiveStride = gps / steps.toFloat()
            if (effectiveStride > ActivityThresholds.VEHICLE_STRIDE_METERS &&
                speedKmh > ActivityThresholds.VEHICLE_SPEED_KMH
            ) {
                return ReconciliationResult(
                    finalMeters = gps,
                    reliability = ActivityThresholds.RELIABILITY_VEHICLE,
                    source = DistanceSource.GPS,
                    flags = setOf(ActivityFlag.VEHICLE)
                )
            }
        }

        // [4] Sem passos: só temos GPS
        if (steps <= 0) {
            return if (gps < ActivityThresholds.GPS_MIN_OUTDOOR_METERS) {
                // GPS baixo e lento = drift de aparelho parado
                ReconciliationResult(
                    finalMeters = 0f,
                    reliability = ActivityThresholds.RELIABILITY_DRIFT,
                    source = DistanceSource.DISCARDED,
                    flags = setOf(ActivityFlag.GPS_GLITCH)
                )
            } else {
                // Movimento real sem passos (bicicleta, cadeira de rodas, patins): confia no GPS
                ReconciliationResult(
                    finalMeters = gps,
                    reliability = ActivityThresholds.RELIABILITY_VEHICLE,
                    source = DistanceSource.GPS,
                    flags = setOf(ActivityFlag.VEHICLE)
                )
            }
        }

        // [5] A partir daqui: steps > 0.
        // Indoor / GPS insuficiente (esteira, ginásio, túnel)
        if (gps < ActivityThresholds.GPS_MIN_OUTDOOR_METERS) {
            val flag = if (gps <= 0f) ActivityFlag.GPS_LOST else ActivityFlag.INDOOR
            return ReconciliationResult(
                finalMeters = stepM,
                reliability = ActivityThresholds.RELIABILITY_INDOOR,
                source = DistanceSource.STEPS,
                flags = setOf(flag)
            )
        }

        // [6] Outdoor com ambas as fontes — compara e decide
        val erroPercent = abs(gps - stepM) / gps * 100.0
        return when {
            erroPercent < ActivityThresholds.ERROR_QUALITY_PERCENT ->
                ReconciliationResult(
                    finalMeters = gps,
                    reliability = ActivityThresholds.RELIABILITY_QUALITY,
                    source = DistanceSource.GPS,
                    flags = setOf(ActivityFlag.OUTDOOR_QUALITY)
                )

            erroPercent < ActivityThresholds.ERROR_AVG_PERCENT ->
                ReconciliationResult(
                    finalMeters = (gps + stepM) / 2f,
                    reliability = ActivityThresholds.RELIABILITY_AVG,
                    source = DistanceSource.AVERAGE,
                    flags = setOf(ActivityFlag.OUTDOOR_QUALITY)
                )

            else -> {
                // Discordância forte: toma o menor (anti-inflação) e marca baixa concordância
                ReconciliationResult(
                    finalMeters = minOf(gps, stepM),
                    reliability = ActivityThresholds.RELIABILITY_DISAGREE,
                    source = DistanceSource.AVERAGE,
                    flags = setOf(ActivityFlag.LOW_AGREEMENT)
                )
            }
        }
    }
}
