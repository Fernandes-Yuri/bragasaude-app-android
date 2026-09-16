package br.com.bragasaude.domain

import br.com.bragasaude.data.local.DailyMetricsEntity
import br.com.bragasaude.data.remote.model.RemoteProfile
import br.com.bragasaude.data.remote.model.RemoteVitalSign
import br.com.bragasaude.data.util.parseDate
import java.text.SimpleDateFormat
import java.util.*

data class MonthlyCycleReport(
    val userId: String,
    val startDate: String,
    val endDate: String,
    val pressureStats: BloodPressureCycleStats,
    val glucoseStats: GlucoseCycleStats,
    val hydrationStats: HydrationCycleStats,
    val activityStats: ActivityCycleStats
)

data class BloodPressureCycleStats(
    val averageSystolic: Double,
    val averageDiastolic: Double,
    val maxSystolic: Int,
    val minSystolic: Int,
    val maxDiastolic: Int,
    val minDiastolic: Int,
    val normalReadingsCount: Int,
    val totalReadings: Int,
    val systolicSeries: List<Double>,
    val diastolicSeries: List<Double>
)

data class GlucoseCycleStats(
    val averageFasting: Double,
    val averagePostPrandial: Double,
    val averageOverall: Double,
    val maxLevel: Int,
    val minLevel: Int,
    val normalReadingsCount: Int,
    val totalReadings: Int,
    val series: List<Double>
)

data class HydrationCycleStats(
    val dailyTotals: Map<String, Int>, // yyyy-MM-dd -> totalMl
    val dailySeries: List<Double>,
    val averageDailyMl: Double,
    val targetMl: Int,
    val daysGoalMet: Int,
    val goalAttainmentPercentage: Double
)

data class ActivityCycleStats(
    val totalSteps: Int,
    val averageStepsPerDay: Int,
    val totalCaloriesBurned: Double,
    val averageCaloriesPerDay: Double,
    val totalActiveMinutes: Int,
    val averageActiveMinutesPerDay: Int,
    val daysGoalMet: Int,
    val totalDaysTracked: Int,
    val stepsSeries: List<Double>,
    val caloriesSeries: List<Double>,
    val distanceSeries: List<Double>,
    val activeMinutesSeries: List<Double>
)

object ClinicalReportAggregator {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    fun aggregate30DayCycle(
        profile: RemoteProfile?,
        vitals: List<RemoteVitalSign>,
        dailyMetrics: List<DailyMetricsEntity>
    ): MonthlyCycleReport {
        val cal = Calendar.getInstance()
        val endDateStr = dateFormat.format(cal.time)
        val cutoffCal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -30) }
        val startDateStr = dateFormat.format(cutoffCal.time)
        val cutoffMillis = cutoffCal.timeInMillis

        // 1. Filtragem da janela de 30 dias para sinais vitais
        val recentVitals = vitals.filter { v ->
            val measuredDate = v.measuredAt?.let { parseDate(it) }
            measuredDate != null && measuredDate.time >= cutoffMillis
        }.sortedBy { it.measuredAt?.let { d -> parseDate(d)?.time } ?: 0L }

        // --- 2. PRESSÃO ARTERIAL (Isolada de registros hídricos) ---
        val bpReadings = recentVitals.filter { it.systolicPressure != null || it.diastolicPressure != null }
        val sysList = bpReadings.mapNotNull { it.systolicPressure }
        val diaList = bpReadings.mapNotNull { it.diastolicPressure }

        val avgSys = if (sysList.isNotEmpty()) sysList.average() else 0.0
        val avgDia = if (diaList.isNotEmpty()) diaList.average() else 0.0
        val maxSys = sysList.maxOrNull() ?: 0
        val minSys = sysList.minOrNull() ?: 0
        val maxDia = diaList.maxOrNull() ?: 0
        val minDia = diaList.minOrNull() ?: 0

        val normalBpCount = bpReadings.count {
            val sys = it.systolicPressure ?: 0
            val dia = it.diastolicPressure ?: 0
            sys in 90..130 && dia in 60..85
        }

        val bpStats = BloodPressureCycleStats(
            averageSystolic = avgSys,
            averageDiastolic = avgDia,
            maxSystolic = maxSys,
            minSystolic = minSys,
            maxDiastolic = maxDia,
            minDiastolic = minDia,
            normalReadingsCount = normalBpCount,
            totalReadings = bpReadings.size,
            systolicSeries = sysList.map { it.toDouble() },
            diastolicSeries = diaList.map { it.toDouble() }
        )

        // --- 3. GLICOSE (Isolada de registros hídricos) ---
        val glucoseReadings = recentVitals.filter { it.glucoseLevel != null }
        val allGlucose = glucoseReadings.mapNotNull { it.glucoseLevel }
        val fastingReadings = glucoseReadings.filter { it.glucoseType?.lowercase() == "jejum" }.mapNotNull { it.glucoseLevel }
        val postPrandialReadings = glucoseReadings.filter { it.glucoseType?.lowercase() != "jejum" }.mapNotNull { it.glucoseLevel }

        val avgOverall = if (allGlucose.isNotEmpty()) allGlucose.average() else 0.0
        val avgFasting = if (fastingReadings.isNotEmpty()) fastingReadings.average() else avgOverall
        val avgPostPrandial = if (postPrandialReadings.isNotEmpty()) postPrandialReadings.average() else avgOverall

        val normalGlucoseCount = glucoseReadings.count {
            val level = it.glucoseLevel ?: 0
            val isFasting = it.glucoseType?.lowercase() == "jejum"
            if (isFasting) level in 70..100 else level in 70..140
        }

        val glucoseStats = GlucoseCycleStats(
            averageFasting = avgFasting,
            averagePostPrandial = avgPostPrandial,
            averageOverall = avgOverall,
            maxLevel = allGlucose.maxOrNull() ?: 0,
            minLevel = allGlucose.minOrNull() ?: 0,
            normalReadingsCount = normalGlucoseCount,
            totalReadings = glucoseReadings.size,
            series = allGlucose.map { it.toDouble() }
        )

        // --- 4. HIDRATAÇÃO (Agrupada por dia, somando múltiplas ingestões diárias) ---
        val hydrationReadings = recentVitals.filter { it.hydrationMl != null && it.hydrationMl > 0 }
        val dailyHydrationMap = mutableMapOf<String, Int>()

        hydrationReadings.forEach { vital ->
            val measuredDate = vital.measuredAt?.let { parseDate(it) }
            if (measuredDate != null) {
                val dayStr = dateFormat.format(measuredDate)
                dailyHydrationMap[dayStr] = dailyHydrationMap.getOrDefault(dayStr, 0) + (vital.hydrationMl ?: 0)
            }
        }

        // Gera a série cronológica completa dos últimos 30 dias
        val hydrationDaysList = (0..29).map { i ->
            val dCal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -i) }
            dateFormat.format(dCal.time)
        }.reversed()

        val hydrationDailySeries = hydrationDaysList.map { day ->
            (dailyHydrationMap[day] ?: 0).toDouble()
        }

        val activeHydrationDays = hydrationDailySeries.filter { it > 0 }
        val avgDailyHydration = if (activeHydrationDays.isNotEmpty()) activeHydrationDays.average() else 0.0
        val hydrationTarget = profile?.hydrationTargetMl ?: 2000
        val daysHydrationTargetMet = hydrationDailySeries.count { it >= hydrationTarget }
        val hydrationAttainment = if (hydrationDailySeries.isNotEmpty()) (daysHydrationTargetMet.toDouble() / 30.0) * 100.0 else 0.0

        val hydrationStats = HydrationCycleStats(
            dailyTotals = dailyHydrationMap,
            dailySeries = hydrationDailySeries,
            averageDailyMl = avgDailyHydration,
            targetMl = hydrationTarget,
            daysGoalMet = daysHydrationTargetMet,
            goalAttainmentPercentage = hydrationAttainment
        )

        // --- 5. ATIVIDADE FÍSICA (30 Dias) ---
        val recentMetrics = dailyMetrics
            .filter { it.date >= startDateStr }
            .sortedBy { it.date }

        val stepGoal = profile?.stepGoal ?: 8000
        val totalSteps = recentMetrics.sumOf { it.steps }
        val totalCals = recentMetrics.sumOf { it.caloriesBurned.toDouble() }
        val totalMins = recentMetrics.sumOf { it.activeMinutes }
        val trackedDaysCount = if (recentMetrics.isNotEmpty()) recentMetrics.size else 1

        val avgSteps = totalSteps / trackedDaysCount
        val avgCals = totalCals / trackedDaysCount
        val avgMins = totalMins / trackedDaysCount
        val daysStepGoalMet = recentMetrics.count { it.steps >= stepGoal }

        val activityStats = ActivityCycleStats(
            totalSteps = totalSteps,
            averageStepsPerDay = avgSteps,
            totalCaloriesBurned = totalCals,
            averageCaloriesPerDay = avgCals,
            totalActiveMinutes = totalMins,
            averageActiveMinutesPerDay = avgMins,
            daysGoalMet = daysStepGoalMet,
            totalDaysTracked = recentMetrics.size,
            stepsSeries = recentMetrics.map { it.steps.toDouble() },
            caloriesSeries = recentMetrics.map { it.caloriesBurned.toDouble() },
            distanceSeries = recentMetrics.map { it.distanceMeters.toDouble() },
            activeMinutesSeries = recentMetrics.map { it.activeMinutes.toDouble() }
        )

        return MonthlyCycleReport(
            userId = profile?.id ?: "",
            startDate = startDateStr,
            endDate = endDateStr,
            pressureStats = bpStats,
            glucoseStats = glucoseStats,
            hydrationStats = hydrationStats,
            activityStats = activityStats
        )
    }
}