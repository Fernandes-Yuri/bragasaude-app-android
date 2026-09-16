package br.com.bragasaude.domain

import br.com.bragasaude.data.local.DailyMetricsEntity
import br.com.bragasaude.data.local.ExamItemEntity
import br.com.bragasaude.data.remote.model.RemoteProfile
import br.com.bragasaude.data.remote.model.RemoteVitalSign

/**
 * Representa o detalhamento da pontuação de saúde.
 */
data class ScoreBreakdown(
    val finalScore: Int,
    val positiveFactors: List<String> = emptyList(),
    val negativeFactors: List<String> = emptyList()
)

/**
 * Calculadora de Pontuação de Saúde (0-100) refinada com transparência de dados.
 */
object HealthScoreCalculator {

    fun calculate(
        profile: RemoteProfile?,
        vitals: List<RemoteVitalSign>,
        examItems: List<ExamItemEntity> = emptyList(),
        dailyMetrics: List<DailyMetricsEntity> = emptyList()
    ): ScoreBreakdown {
        val scores = mutableListOf<Int>()
        val positive = mutableListOf<String>()
        val negative = mutableListOf<String>()

        // 1. Glicose
        val labGlucose = examItems.firstOrNull { it.itemKey == "glucose" }?.valueNumeric
        val vitalGlucose = vitals.firstOrNull { it.glucoseLevel != null }
        val glucoseValue = labGlucose ?: vitalGlucose?.glucoseLevel?.toDouble()
        
        glucoseValue?.let { value ->
            val isFasting = (labGlucose != null) || (vitalGlucose?.glucoseType?.lowercase() == "jejum")
            val targetMin = if (isFasting) 80.0 else 70.0
            val targetMax = if (isFasting) 130.0 else 160.0
            
            when {
                value < 70 || value > 180 -> {
                    scores.add(20)
                    negative.add("Glicemia fora dos limites seguros ($value)")
                }
                value in targetMin..targetMax -> {
                    scores.add(100)
                    positive.add("Nível de glicose excelente")
                }
                else -> {
                    scores.add(60)
                    negative.add("Glicose levemente alterada")
                }
            }
        }

        // 2. Pressão Arterial
        vitals.firstOrNull { it.systolicPressure != null || it.diastolicPressure != null }?.let {
            val sysRaw = it.systolicPressure ?: 0
            val diaRaw = it.diastolicPressure ?: 0
            val sys = br.com.bragasaude.domain.util.BloodPressureParser.normalizePressure(sysRaw)
            val dia = br.com.bragasaude.domain.util.BloodPressureParser.normalizePressure(diaRaw)

            when {
                (sys > 0 && (sys < 90 || sys >= 180)) || (dia > 0 && (dia < 60 || dia >= 110)) -> {
                    scores.add(20)
                    negative.add("Pressão arterial em nível de risco ($sys/$dia)")
                }
                (sys in 110..130) && (dia == 0 || dia in 70..85) -> {
                    scores.add(100)
                    positive.add("Pressão arterial ideal")
                }
                else -> {
                    scores.add(60)
                    negative.add("Pressão arterial fora do alvo")
                }
            }
        }

        // 3. Colesterol
        examItems.firstOrNull { it.itemKey == "total_cholesterol" }?.valueNumeric?.let { value ->
            if (value < 200) {
                scores.add(100)
                positive.add("Colesterol total sob controle")
            } else {
                scores.add(60)
                negative.add("Colesterol total elevado ($value)")
            }
        }

        // 4. Composição Corporal (IMC Adaptado para Sênior / Adulto - SBGG / OPAS)
        profile?.let { p ->
            if (p.weight != null && p.weight > 0 && p.height != null && p.height > 0) {
                val heightInMeters = if (p.height > 3.0) p.height / 100.0 else p.height
                if (heightInMeters > 0.5) {
                    val imc = p.weight / (heightInMeters * heightInMeters)
                    val senior = isSenior(p.birthDate)
                    val (targetMin, targetMax) = if (senior) 22.0 to 27.0 else 18.5 to 24.9

                    when {
                        imc in targetMin..targetMax -> {
                            scores.add(100)
                            positive.add("Peso ideal para sua altura (IMC)")
                        }
                        imc >= 30.0 -> {
                            scores.add(40)
                            negative.add("IMC acima da faixa geral de referência")
                        }
                        imc < (if (senior) 22.0 else 18.5) -> {
                            scores.add(60)
                            negative.add("IMC abaixo da faixa recomendada")
                        }
                        else -> {
                            scores.add(75)
                            negative.add("Peso fora da faixa ideal")
                        }
                    }
                }
            }
        }

        // 5. Nível de Atividade Física (Passos Ponderados + Pontos de Cardio OMS - Diretriz 150 pts/sem)
        val stepTarget = profile?.stepGoal ?: 8000
        val recent7 = dailyMetrics.take(7)
        if (recent7.isNotEmpty()) {
            val totalWeightedSteps = recent7.sumOf { (it.steps * it.reliabilityScore.coerceIn(0.2f, 1.0f)).toDouble() }
            val totalWeight = recent7.sumOf { it.reliabilityScore.coerceIn(0.2f, 1.0f).toDouble() }
            val avgSteps = if (totalWeight > 0) (totalWeightedSteps / totalWeight).toInt() else recent7.sumOf { it.steps } / recent7.size
            val avgCardioMinutes = recent7.sumOf { it.activeMinutes } / recent7.size
            val weeklyCardioMinutes = recent7.sumOf { it.activeMinutes }

            when {
                avgSteps >= stepTarget && weeklyCardioMinutes >= 150 -> {
                    scores.add(100)
                    positive.add("Passos e Pontos de Cardio (OMS) exemplares")
                }
                avgSteps >= stepTarget || avgCardioMinutes >= 22 -> {
                    scores.add(90)
                    positive.add("Média de atividade física consistente")
                }
                avgSteps >= (stepTarget * 0.6f).toInt() || avgCardioMinutes >= 15 -> {
                    scores.add(75)
                    positive.add("Atividade física regular")
                }
                else -> {
                    scores.add(40)
                    negative.add("Baixa média de atividade física semanal")
                }
            }
        } else {
            profile?.activityLevel?.let { level ->
                when (level) {
                    "Muito Ativo", "Moderadamente Ativo" -> {
                        scores.add(100)
                        positive.add("Excelente nível de atividade física")
                    }
                    "Sedentário" -> {
                        scores.add(40)
                        negative.add("Estilo de vida sedentário")
                    }
                    else -> scores.add(70)
                }
            }
        }

        // 6. Hidratação (Agregação Diária Corrigida: soma todas as ingestões de hoje)
        val targetHydration = profile?.hydrationTargetMl?.toDouble() ?: 2000.0
        val todayStr = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
        val todayHydration = vitals
            .filter { it.hydrationMl != null && it.hydrationMl > 0 }
            .filter { vital ->
                val mDate = vital.measuredAt?.let { br.com.bragasaude.data.util.parseDate(it) }
                mDate != null && java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(mDate) == todayStr
            }
            .sumOf { it.hydrationMl ?: 0 }

        if (todayHydration > 0) {
            val percentage = (todayHydration.toDouble() / targetHydration) * 100
            if (percentage >= 80) positive.add("Hidratação diária exemplar")
            else if (percentage < 50) negative.add("Consumo de água abaixo do ideal")
            scores.add(percentage.toInt().coerceIn(0, 100))
        }

        val baseScore = if (scores.isEmpty()) 50 else scores.average().toInt()
        var finalScore = baseScore

        // 7. Penalidade Tabagismo
        if (profile?.isSmoker == true) {
            finalScore = (finalScore - 15).coerceAtLeast(0)
            negative.add("Penalidade por tabagismo (-15 pts)")
        }

        return ScoreBreakdown(finalScore, positive, negative)
    }

    private fun isSenior(birthDateStr: String?): Boolean {
        if (birthDateStr.isNullOrBlank()) return true // default sênior
        return try {
            val iso = br.com.bragasaude.data.util.HealthFormatter.toIsoDateString(birthDateStr) ?: birthDateStr
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
            val birthDate = sdf.parse(iso) ?: return true
            val dob = java.util.Calendar.getInstance().apply { time = birthDate }
            val today = java.util.Calendar.getInstance()
            var age = today.get(java.util.Calendar.YEAR) - dob.get(java.util.Calendar.YEAR)
            if (today.get(java.util.Calendar.DAY_OF_YEAR) < dob.get(java.util.Calendar.DAY_OF_YEAR)) {
                age--
            }
            age >= 60
        } catch (_: Exception) {
            true
        }
    }
}
