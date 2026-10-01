package br.com.bragasaude.domain

import android.content.Context
import android.graphics.*
import android.graphics.pdf.PdfDocument
import br.com.bragasaude.data.local.DailyMetricsEntity
import br.com.bragasaude.data.remote.model.RemoteProfile
import br.com.bragasaude.data.remote.model.RemoteVitalSign
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

class PdfReportGenerator(private val context: Context) {

    fun generateReport(
        profile: RemoteProfile,
        vitals: List<RemoteVitalSign>,
        dailyMetrics: List<DailyMetricsEntity> = emptyList(),
        wearableReadings: List<br.com.bragasaude.data.local.WearableReading> = emptyList()
    ): File? {
        val pdfDocument = PdfDocument()

        val textPaint = Paint().apply {
            textSize = 11.5f
            color = Color.rgb(26, 26, 46)
            isAntiAlias = true
        }
        val subTextPaint = Paint().apply {
            textSize = 9.5f
            color = Color.rgb(100, 116, 139)
            isAntiAlias = true
        }
        val sectionTitlePaint = Paint().apply {
            textSize = 13f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            color = Color.rgb(0, 137, 123)
            isAntiAlias = true
        }

        val disclaimerText = "Registros informados pelo usuário para organização pessoal e compartilhamento. A precisão depende dos dados registrados."
        val deviceDisclaimerText = "O Braga Saúde não realiza diagnóstico, interpretação de exames ou prescrição. Este relatório não substitui uma avaliação profissional."
        
        val disclaimerPaint = Paint().apply {
            textSize = 6.2f
            color = Color.rgb(100, 116, 139)
            textAlign = Paint.Align.LEFT
            isAntiAlias = true
        }

        val cycleReport = ClinicalReportAggregator.aggregate30DayCycle(profile, vitals, dailyMetrics)
        
        val period = PersonalReportPeriod.current()
        val recentVitals = vitals.filter { vital ->
            vital.measuredAt?.let { br.com.bragasaude.data.util.parseDate(it) }?.time?.let(period::contains) == true
        }.sortedByDescending { it.measuredAt }
        val historyLines = recentVitals.flatMap { vital ->
            val values = buildList {
                if (vital.systolicPressure != null || vital.diastolicPressure != null)
                    add("Pressão arterial: ${vital.systolicPressure ?: "--"}/${vital.diastolicPressure ?: "--"} mmHg")
                vital.glucoseLevel?.let { add("Glicose: $it mg/dL (${vital.glucoseType ?: "avulso"})") }
                vital.heartRate?.let { add("Frequência cardíaca: $it bpm") }
                vital.hydrationMl?.takeIf { it > 0 }?.let { add("Hidratação: $it ml") }
            }
            values.flatMap { value ->
                PdfTextLayout.wrap("${vital.measuredAt?.take(16) ?: "--"} • $value", 471f, textPaint::measureText)
            }
        }.ifEmpty { listOf("Nenhum registro informado neste período.") }
        val historyPages = historyLines.chunked(38)
        val wearablePagesData = summarizeWearableReadings(wearableReadings.filter { period.contains(it.measuredAt) }).chunked(10)
        val totalPages = 2 + historyPages.size + wearablePagesData.size

        // ==========================================
        // PÁGINA 1
        // ==========================================
        val pageInfo1 = PdfDocument.PageInfo.Builder(595, 842, 1).create()
        val page1 = pdfDocument.startPage(pageInfo1)
        val canvas1: Canvas = page1.canvas

        drawPageHeader(canvas1, "Relatório dos últimos 30 dias")

        var yPos = 85f

        val currentDate = SimpleDateFormat("dd/MM/yyyy 'às' HH:mm", Locale.getDefault()).format(Date())
        canvas1.drawText("Emitido em: $currentDate  |  Ciclo de 30 Dias: ${cycleReport.startDate} a ${cycleReport.endDate}", 50f, yPos, subTextPaint)
        yPos += 28f

        // Card da Pessoa Acompanhada
        val cardBgPaint = Paint().apply {
            color = Color.rgb(240, 250, 248)
            style = Paint.Style.FILL
        }
        val cardBorderPaint = Paint().apply {
            color = Color.rgb(178, 223, 219)
            style = Paint.Style.STROKE
            strokeWidth = 1f
        }
        canvas1.drawRoundRect(50f, yPos, 545f, yPos + 65f, 10f, 10f, cardBgPaint)
        canvas1.drawRoundRect(50f, yPos, 545f, yPos + 65f, 10f, 10f, cardBorderPaint)
        
        val iconPaint = Paint().apply {
            color = Color.rgb(0, 137, 123)
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        canvas1.drawCircle(68f, yPos + 30f, 14f, iconPaint)
        
        val iconTextPaint = Paint().apply {
            color = Color.WHITE
            textSize = 14f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }
        canvas1.drawText("P", 68f, yPos + 35f, iconTextPaint)

        val patientTitle = Paint().apply {
            textSize = 12f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            color = Color.BLACK
            isAntiAlias = true
        }
        PdfTextLayout.wrap("Titular: ${profile.fullName ?: "Não informado"}", 440f, patientTitle::measureText)
            .take(2).forEachIndexed { index, line -> canvas1.drawText(line, 90f, yPos + 19f + index * 14f, patientTitle) }
        
        val infoLine = "Idade/Nasc: ${profile.birthDate ?: "--"}  |  Gênero: ${profile.gender ?: "--"}  |  Peso: ${profile.weight ?: "--"}kg  |  Altura: ${profile.height ?: "--"}m"
        PdfTextLayout.wrap(infoLine, 440f, subTextPaint::measureText)
            .take(2).forEachIndexed { index, line -> canvas1.drawText(line, 90f, yPos + 48f + index * 12f, subTextPaint) }
        yPos += 65f + 28f

        canvas1.drawText("Registros de autocuidado dos últimos 30 dias", 50f, yPos, sectionTitlePaint)
        yPos += 18f

        // Gráficos
        val pressureDataSys = cycleReport.pressureStats.systolicSeries
        val pressureDataDia = cycleReport.pressureStats.diastolicSeries
        drawChartBox(
            canvas = canvas1,
            x = 50f,
            y = yPos,
            width = 495f,
            height = 140f,
            title = "Evolução da Pressão Arterial (Sistólica / Diastólica)",
            series = listOf(
                ChartSeries("Sistólica (mmHg)", pressureDataSys, Color.rgb(0, 137, 123)),
                ChartSeries("Diastólica (mmHg)", pressureDataDia, Color.rgb(0, 229, 255))
            ),
            target = null
        )
        yPos += 160f

        val glucoseData = cycleReport.glucoseStats.series
        drawChartBox(
            canvas = canvas1,
            x = 50f,
            y = yPos,
            width = 495f,
            height = 140f,
            title = "Evolução da Glicemia (mg/dL)",
            series = listOf(
                ChartSeries("Glicose", glucoseData, Color.rgb(234, 88, 12))
            ),
            target = null
        )
        yPos += 160f

        val hydrationData = cycleReport.hydrationStats.dailySeries
        drawChartBox(
            canvas = canvas1,
            x = 50f,
            y = yPos,
            width = 495f,
            height = 140f,
            title = "Consumo Hídrico Diário Consolidado (ml/dia)",
            series = listOf(
                ChartSeries("Água (ml)", hydrationData, Color.rgb(14, 165, 233))
            ),
            target = (profile.hydrationTargetMl ?: 2000).toDouble()
        )

        drawLegalDisclaimers(canvas1, disclaimerText, deviceDisclaimerText, disclaimerPaint)
        drawPageFooter(canvas1, 1, totalPages)
        
        pdfDocument.finishPage(page1)

        // ==========================================
        // PÁGINA 2
        // ==========================================
        val pageInfo2 = PdfDocument.PageInfo.Builder(595, 842, 2).create()
        val page2 = pdfDocument.startPage(pageInfo2)
        val canvas2: Canvas = page2.canvas

        drawPageHeader(canvas2, "Atividade Física & Estimativa de Gasto Calórico")

        var yPos2 = 85f
        canvas2.drawText("Atividade Física e Estimativa de Gasto Calórico (Ciclo 30 Dias)", 50f, yPos2, sectionTitlePaint)
        yPos2 += 18f

        val stepsData = cycleReport.activityStats.stepsSeries
        val caloriesData = cycleReport.activityStats.caloriesSeries
        val distanceData = cycleReport.activityStats.distanceSeries.map { it / 1000.0 }
        val activeMinutesData = cycleReport.activityStats.activeMinutesSeries

        drawChartBox(
            canvas = canvas2,
            x = 50f,
            y = yPos2,
            width = 495f,
            height = 160f,
            title = "Evolução de Passos e Distância Percorrida (km)",
            series = listOf(
                ChartSeries("Passos", stepsData, Color.rgb(0, 137, 123)),
                ChartSeries("Distância (km x1000)", distanceData.map { it * 1000 }, Color.rgb(16, 185, 129))
            ),
            target = (profile.stepGoal ?: 8000).toDouble()
        )
        yPos2 += 185f

        drawChartBox(
            canvas = canvas2,
            x = 50f,
            y = yPos2,
            width = 495f,
            height = 160f,
            title = "Gasto Calórico Estimado (kcal) & Minutos em Movimento",
            series = listOf(
                ChartSeries("Calorias (kcal)", caloriesData, Color.rgb(249, 115, 22)),
                ChartSeries("Minutos Ativos (x10)", activeMinutesData.map { it * 10 }, Color.rgb(124, 77, 255))
            )
        )
        yPos2 += 185f

        canvas2.drawRoundRect(50f, yPos2, 545f, yPos2 + 65f, 10f, 10f, cardBgPaint)
        canvas2.drawRoundRect(50f, yPos2, 545f, yPos2 + 65f, 10f, 10f, cardBorderPaint)
        
        val avgSteps = cycleReport.activityStats.averageStepsPerDay
        val avgCalories = cycleReport.activityStats.averageCaloriesPerDay.toInt()
        val avgMinutes = cycleReport.activityStats.averageActiveMinutesPerDay
        val daysMet = cycleReport.activityStats.daysGoalMet
        
        canvas2.drawText("Resumo de Condicionamento e Atividade (30 Dias):", 65f, yPos2 + 22f, patientTitle)
        val line1 = "Média: $avgSteps passos/dia  •  Gasto Estimado: $avgCalories kcal/dia"
        val line2 = "Tempo Ativo Médio: $avgMinutes min/dia  •  Metas Cumpridas: $daysMet dias"
        canvas2.drawText(line1, 65f, yPos2 + 38f, textPaint)
        canvas2.drawText(line2, 65f, yPos2 + 52f, textPaint)

        drawLegalDisclaimers(canvas2, disclaimerText, deviceDisclaimerText, disclaimerPaint)
        drawPageFooter(canvas2, 2, totalPages)
        
        pdfDocument.finishPage(page2)

        historyPages.forEachIndexed { index, lines ->
            val pageNum = 3 + index
            val historyPage = pdfDocument.startPage(PdfDocument.PageInfo.Builder(595, 842, pageNum).create())
            val canvas = historyPage.canvas
            drawPageHeader(canvas, "Registros dos últimos 30 dias • Histórico")
            var y = 95f
            lines.forEachIndexed { rowIndex, line ->
                val rowPaint = Paint().apply { color = if (rowIndex % 2 == 0) Color.rgb(240, 250, 248) else Color.TRANSPARENT }
                canvas.drawRect(50f, y - 13f, 545f, y + 5f, rowPaint)
                canvas.drawText(line, 62f, y, textPaint)
                y += 17f
            }
            drawLegalDisclaimers(canvas, disclaimerText, deviceDisclaimerText, disclaimerPaint)
            drawPageFooter(canvas, pageNum, totalPages)
            pdfDocument.finishPage(historyPage)
        }

        // ==========================================
        // WEARABLE PAGES
        // ==========================================
        wearablePagesData.forEachIndexed { index, rows ->
            val pageNum = index + 3 + historyPages.size
            val page = pdfDocument.startPage(PdfDocument.PageInfo.Builder(595, 842, pageNum).create())
            val canvas = page.canvas
            
            drawPageHeader(canvas, "Batimentos e Oxigenação")
            
            var y = 85f
            canvas.drawText("BATIMENTOS E OXIGENAÇÃO • ÚLTIMOS 30 DIAS", 50f, y, sectionTitlePaint)
            y += 18f
            canvas.drawText("Resumo por dia e aplicativo de origem. Horários conforme o fuso do celular.", 50f, y, subTextPaint)
            y += 28f
            
            rows.forEach { row ->
                val metric = if (row.metric == "HEART_RATE") "Frequência cardíaca (bpm)" else "SpO2 (%)"
                canvas.drawText("${row.date} • $metric • ${row.count} amostras", 50f, y, textPaint)
                canvas.drawText(String.format(Locale.getDefault(), "Mínima: %.1f   Média: %.1f   Máxima: %.1f", row.minimum, row.average, row.maximum), 50f, y + 18f, textPaint)
                val source = "Origem: " + if (row.source.startsWith("braga.")) HealthReadingInput.origin(row.source) else row.source
                canvas.drawText(source.take(subTextPaint.breakText(source, true, 495f, null)), 50f, y + 36f, subTextPaint)
                y += 54f
            }
            
            val wearableDisclaimer = "¹ Registros integrados de dispositivos e medições manuais/voz. Dados referenciais de autocuidado, sem finalidade diagnóstica. Apresente este histórico ao seu médico na consulta de rotina."
            drawLegalDisclaimers(canvas, wearableDisclaimer, null, disclaimerPaint)
            
            drawPageFooter(canvas, pageNum, totalPages)
            pdfDocument.finishPage(page)
        }

        // Salva o arquivo no armazenamento
        return try {
            // AUD-AN36: antes escrevia na RAIZ do cacheDir — só compartilhável
            // porque o FileProvider expunha "cache_root" path="." (TODO o cache,
            // incluindo áudio TTS derivado de texto de saúde). Agora em
            // subpasta própria, e o cache_root foi removido do file_paths.xml.
            val outDir = File(context.cacheDir, "relatorios").apply { if (!exists()) mkdirs() }
            val file = File(outDir, "relatorio_ultimos_30_dias_${System.currentTimeMillis()}.pdf")
            val outputStream = FileOutputStream(file)
            pdfDocument.writeTo(outputStream)
            pdfDocument.close()
            outputStream.close()
            file
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun drawPageHeader(canvas: Canvas, pageTitle: String) {
        val path = Path().apply {
            moveTo(0f, 0f)
            lineTo(595f, 0f)
            lineTo(595f, 55f)
            quadTo(595f, 65f, 585f, 65f)
            lineTo(10f, 65f)
            quadTo(0f, 65f, 0f, 55f)
            close()
        }
        val gradientPaint = Paint().apply {
            shader = LinearGradient(
                0f, 0f, 595f, 0f,
                intArrayOf(Color.rgb(0, 137, 123), Color.rgb(0, 168, 132), Color.rgb(0, 105, 92)),
                null,
                Shader.TileMode.CLAMP
            )
            isAntiAlias = true
        }
        canvas.drawPath(path, gradientPaint)

        val brandPaint = Paint().apply {
            textSize = 18f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            color = Color.WHITE
            isAntiAlias = true
        }
        canvas.drawText("BRAGA SAÚDE", 55f, 28f, brandPaint)

        val subtitlePaint = Paint().apply {
            textSize = 10f
            color = Color.WHITE
            alpha = 200
            isAntiAlias = true
        }
        canvas.drawText(pageTitle, 55f, 48f, subtitlePaint)

        val linePaint = Paint().apply {
            color = Color.rgb(0, 168, 132)
            strokeWidth = 1.5f
            alpha = 100
            isAntiAlias = true
        }
        canvas.drawLine(50f, 68f, 545f, 68f, linePaint)
    }

    private fun drawPageFooter(canvas: Canvas, pageNumber: Int, totalPages: Int) {
        PdfBranding.watermark(context, canvas)
        val linePaint = Paint().apply {
            color = Color.rgb(203, 213, 225)
            strokeWidth = 0.5f
            isAntiAlias = true
        }
        canvas.drawLine(50f, 808f, 545f, 808f, linePaint)

        val footerLeftPaint = Paint().apply {
            textSize = 6.8f
            color = Color.rgb(71, 85, 105)
            textAlign = Paint.Align.LEFT
            isAntiAlias = true
        }
        canvas.drawText("Braga Saúde • Plataforma de Autocuidado", 50f, 820f, footerLeftPaint)

        val footerRightPaint = Paint().apply {
            textSize = 6.8f
            color = Color.rgb(100, 116, 139)
            textAlign = Paint.Align.RIGHT
            isAntiAlias = true
        }
        canvas.drawText("Página $pageNumber de $totalPages", 545f, 820f, footerRightPaint)
    }

    private fun drawWrappedText(
        canvas: Canvas,
        text: String,
        x: Float,
        startY: Float,
        maxWidth: Float,
        paint: Paint,
        lineSpacing: Float = paint.textSize * 1.3f
    ): Float {
        val prevAlign = paint.textAlign
        paint.textAlign = Paint.Align.LEFT
        var currentY = startY
        PdfTextLayout.wrap(text, maxWidth, paint::measureText).forEach { line ->
            canvas.drawText(line, x, currentY, paint)
            currentY += lineSpacing
        }
        paint.textAlign = prevAlign
        return currentY
    }

    private fun drawLegalDisclaimers(
        canvas: Canvas,
        disclaimerText: String,
        deviceDisclaimerText: String? = null,
        disclaimerPaint: Paint
    ) {
        // ABNT NBR 14724: Filete horizontal de 5 cm (aprox. 142 pt) alinhado à margem esquerda
        val abntDividerPaint = Paint().apply {
            color = Color.rgb(148, 163, 184)
            strokeWidth = 0.6f
            isAntiAlias = true
        }
        canvas.drawLine(50f, 755f, 192f, 755f, abntDividerPaint)

        var y = 765f
        y = drawWrappedText(canvas, disclaimerText, 50f, y, 495f, disclaimerPaint, 7.8f)
        if (!deviceDisclaimerText.isNullOrBlank()) {
            drawWrappedText(canvas, deviceDisclaimerText, 50f, y + 2.5f, 495f, disclaimerPaint, 7.8f)
        }
    }

    private data class ChartSeries(
        val name: String,
        val points: List<Double>,
        val color: Int
    )

    private fun drawChartBox(
        canvas: Canvas,
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        title: String,
        series: List<ChartSeries>,
        target: Double? = null
    ) {
        val bgPaint = Paint().apply {
            color = Color.rgb(248, 250, 252)
            style = Paint.Style.FILL
        }
        val borderPaint = Paint().apply {
            color = Color.rgb(229, 235, 232)
            style = Paint.Style.STROKE
            strokeWidth = 1f
        }
        canvas.drawRoundRect(x, y, x + width, y + height, 8f, 8f, bgPaint)
        canvas.drawRoundRect(x, y, x + width, y + height, 8f, 8f, borderPaint)

        val titlePaint = Paint().apply {
            textSize = 10.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            color = Color.rgb(30, 30, 30)
            isAntiAlias = true
        }
        canvas.drawText(title, x + 12f, y + 18f, titlePaint)

        // Legenda
        var legendX = x + width - 12f
        val legendTextPaint = Paint().apply { textSize = 8.5f; isAntiAlias = true }
        series.reversed().forEach { s ->
            legendTextPaint.color = s.color
            val textWidth = legendTextPaint.measureText(s.name)
            legendX -= textWidth
            canvas.drawText(s.name, legendX, y + 34f, legendTextPaint)
            legendX -= 16f
            canvas.drawCircle(legendX + 6f, y + 31f, 3.5f, Paint().apply { color = s.color; isAntiAlias = true })
            legendX -= 12f
        }

        val chartLeft = x + 30f
        val chartRight = x + width - 20f
        val chartTop = y + 48f
        val chartBottom = y + height - 20f

        val gridPaint = Paint().apply {
            color = Color.rgb(235, 238, 242)
            strokeWidth = 1f
        }
        for (i in 0..3) {
            val gridY = chartTop + (chartBottom - chartTop) * (i / 3f)
            canvas.drawLine(chartLeft, gridY, chartRight, gridY, gridPaint)
        }

        val allPoints = series.flatMap { it.points }
        if (allPoints.isEmpty()) {
            val emptyPaint = Paint().apply { textSize = 10f; color = Color.GRAY; isAntiAlias = true }
            canvas.drawText("Sem dados registrados para este indicador.", chartLeft + 20f, (chartTop + chartBottom) / 2, emptyPaint)
            return
        }

        val minVal = (allPoints.minOrNull() ?: 0.0) * 0.9
        val maxVal = ((allPoints.maxOrNull() ?: 100.0) * 1.1).coerceAtLeast(minVal + 1.0)

        // Linha de Meta
        if (target != null && target in minVal..maxVal) {
            val targetY = chartBottom - ((target - minVal) / (maxVal - minVal) * (chartBottom - chartTop)).toFloat()
            val targetPaint = Paint().apply {
                color = Color.rgb(34, 197, 94)
                strokeWidth = 1f
                pathEffect = DashPathEffect(floatArrayOf(5f, 5f), 0f)
                isAntiAlias = true
            }
            canvas.drawLine(chartLeft, targetY, chartRight, targetY, targetPaint)
            val targetTextPaint = Paint().apply { textSize = 7.5f; color = Color.rgb(34, 197, 94); isAntiAlias = true }
            canvas.drawText("Meta: ${target.toInt()}", chartRight - 45f, targetY - 3f, targetTextPaint)
        }

        // Desenho das Curvas
        series.forEach { s ->
            if (s.points.isEmpty()) return@forEach

            val linePaint = Paint().apply {
                color = s.color
                strokeWidth = 2.2f
                style = Paint.Style.STROKE
                isAntiAlias = true
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
            }
            val dotPaint = Paint().apply {
                color = s.color
                style = Paint.Style.FILL
                isAntiAlias = true
            }

            val path = Path()
            val pointSpacing = if (s.points.size > 1) (chartRight - chartLeft) / (s.points.size - 1) else 0f

            s.points.forEachIndexed { index, value ->
                val px = chartLeft + index * pointSpacing
                val py = chartBottom - ((value - minVal) / (maxVal - minVal) * (chartBottom - chartTop)).toFloat()

                if (index == 0) {
                    path.moveTo(px, py)
                } else {
                    path.lineTo(px, py)
                }
                canvas.drawCircle(px, py, 3f, dotPaint)
            }
            canvas.drawPath(path, linePaint)
        }
    }
}
