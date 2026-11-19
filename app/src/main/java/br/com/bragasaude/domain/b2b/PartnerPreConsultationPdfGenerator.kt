package br.com.bragasaude.domain.b2b

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import br.com.bragasaude.data.local.DailyMetricsEntity
import br.com.bragasaude.data.local.MedicationEntity
import br.com.bragasaude.data.local.MedicationLogEntity
import br.com.bragasaude.data.local.VitalSignEntity
import br.com.bragasaude.domain.PdfBranding
import br.com.bragasaude.domain.PdfTextLayout
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale

data class PartnerPreConsultationPdfInput(
    val programName: String,
    val companyName: String,
    val primaryColorHex: String?,
    val participantName: String,
    val periodDays: Int,
    val selectedCategories: Set<String>,
    val dailyMetrics: List<DailyMetricsEntity>,
    val vitalSigns: List<VitalSignEntity>,
    val medications: List<MedicationEntity>,
    val medicationLogs: List<MedicationLogEntity>
)

/**
 * Gerador local de PDF personalizado do programa parceiro (B2B-12).
 *
 * Regras atendidas:
 * - Contraste WCAG AA automatico no cabecalho via [B2BThemeEngine.resolveAccessiblePalette].
 * - Filtra estritamente as categorias escolhidas pelo paciente e o periodo selecionado.
 * - Identifica origem e periodo de cada secao; nao inclui arquivos/PDFs do Organizador
 *   pessoal (Zero File Egress) e explicita aviso de nao-SaMD / nao-PEP.
 */
class PartnerPreConsultationPdfGenerator(private val context: Context) {

    fun generatePdf(input: PartnerPreConsultationPdfInput): File? {
        val tokens = B2BThemeEngine.resolveAccessiblePalette(input.primaryColorHex)
        val primaryInt = Color.parseColor(tokens.primaryHex)
        val onPrimaryInt = Color.parseColor(tokens.onPrimaryHex)
        val softSurfaceInt = Color.parseColor(tokens.softSurfaceHex)
        val softBorderInt = Color.parseColor(tokens.softBorderHex)
        val accentLightInt = Color.parseColor(tokens.accentOnLightHex)

        val clampedDays = input.periodDays.coerceIn(1, 90)
        val today = LocalDate.now()
        val startDate = today.minusDays((clampedDays - 1).toLong())
        val cutoffMillis = startDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val dateBr = DateTimeFormatter.ofPattern("dd/MM/yyyy")

        val categories = B2BOnboardingValidator.normalizeSharedCategories(input.selectedCategories)
        val lines = buildReportLines(input, categories, clampedDays, startDate, cutoffMillis)

        val pdfDocument = PdfDocument()
        val pageWidth = 595
        val pageHeight = 842
        val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 10.5f
            color = Color.rgb(30, 41, 59)
        }
        val headingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 12.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            color = accentLightInt
        }
        val subtlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 8.8f
            color = Color.rgb(71, 85, 105)
        }

        val maxLinesPerPage = 32
        val wrappedRows = lines.flatMap { row ->
            if (row.isSectionTitle) {
                listOf(row)
            } else {
                PdfTextLayout.wrap(row.text, 475f, bodyPaint::measureText).map { wrapped ->
                    PdfLine(wrapped, isSectionTitle = false)
                }
            }
        }
        val pages = wrappedRows.chunked(maxLinesPerPage).ifEmpty {
            listOf(listOf(PdfLine("Nenhuma categoria selecionada para este resumo.", false)))
        }

        val totalPages = pages.size
        pages.forEachIndexed { pageIndex, pageLines ->
            val pageNumber = pageIndex + 1
            val page = pdfDocument.startPage(
                PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
            )
            val canvas = page.canvas

            drawPartnerHeader(
                canvas = canvas,
                programName = input.programName.ifBlank { "Programa Parceiro" },
                companyName = input.companyName.ifBlank { "Parceiro Braga Saúde" },
                primaryColor = primaryInt,
                onPrimaryColor = onPrimaryInt
            )

            var y = 114f
            if (pageNumber == 1) {
                drawParticipantCard(
                    canvas = canvas,
                    y = y,
                    participantName = input.participantName.ifBlank { "Participante" },
                    periodText = "${startDate.format(dateBr)} a ${today.format(dateBr)} (últimos $clampedDays dias)",
                    softSurfaceColor = softSurfaceInt,
                    softBorderColor = softBorderInt,
                    titlePaint = bodyPaint,
                    subtlePaint = subtlePaint
                )
                y += 74f
            }

            pageLines.forEach { entry ->
                if (entry.isSectionTitle) {
                    y += 8f
                    canvas.drawText(entry.text, 50f, y, headingPaint)
                    y += 17f
                } else {
                    canvas.drawText(entry.text, 54f, y, bodyPaint)
                    y += 15f
                }
            }

            drawPartnerFooter(canvas, pageNumber, totalPages, subtlePaint)
            pdfDocument.finishPage(page)
        }

        return try {
            val outDir = File(context.cacheDir, "relatorios").apply { if (!exists()) mkdirs() }
            val safeSlug = input.programName
                .lowercase(Locale.ROOT)
                .replace(Regex("[^a-z0-9]+"), "_")
                .trim('_')
                .ifBlank { "programa" }
                .take(24)
            val outFile = File(outDir, "preconsulta_${safeSlug}_${System.currentTimeMillis()}.pdf")
            FileOutputStream(outFile).use { stream ->
                pdfDocument.writeTo(stream)
            }
            pdfDocument.close()
            outFile
        } catch (_: Exception) {
            runCatching { pdfDocument.close() }
            null
        }
    }

    private data class PdfLine(val text: String, val isSectionTitle: Boolean)

    private fun buildReportLines(
        input: PartnerPreConsultationPdfInput,
        categories: List<String>,
        clampedDays: Int,
        startDate: LocalDate,
        cutoffMillis: Long
    ): List<PdfLine> {
        val result = mutableListOf<PdfLine>()
        val dateStampFmt = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        val startIso = startDate.toString()

        if (categories.contains(SharedCategoryOption.STEPS.wireKey)) {
            result += PdfLine("1. PASSOS E ATIVIDADE (Origem: Braga Saúde • Passos)", true)
            val metrics = input.dailyMetrics
                .filter { it.date >= startIso && it.steps > 0 }
                .sortedByDescending { it.date }
                .take(clampedDays)
            if (metrics.isEmpty()) {
                result += PdfLine("Nenhum dia com passos registrados no período selecionado.", false)
            } else {
                val avg = metrics.map { it.steps.toLong() }.average().toLong()
                result += PdfLine("Média: $avg passos/dia (${metrics.size} dias com registro).", false)
                metrics.take(10).forEach { m ->
                    val km = String.format(Locale("pt", "BR"), "%.1f km", m.distanceFinalMeters / 1000f)
                    result += PdfLine("${m.date}: ${m.steps} passos • ${m.activeMinutes} min ativos • $km", false)
                }
            }
        }

        if (categories.contains(SharedCategoryOption.HYDRATION.wireKey)) {
            result += PdfLine("2. GESTÃO HÍDRICA (Origem: Braga Saúde • Autoinformado)", true)
            val hydByDay = input.vitalSigns
                .filter { it.deletedAt == null && it.measuredAt.time >= cutoffMillis && (it.hydrationMl ?: 0) > 0 }
                .groupBy {
                    Instant.ofEpochMilli(it.measuredAt.time)
                        .atZone(ZoneId.systemDefault())
                        .toLocalDate()
                        .toString()
                }
                .mapValues { (_, entries) -> entries.sumOf { it.hydrationMl ?: 0 } }
                .toSortedMap(reverseOrder())
            if (hydByDay.isEmpty()) {
                result += PdfLine("Nenhum registro de hidratação no período selecionado.", false)
            } else {
                val avgMl = hydByDay.values.average().toLong()
                result += PdfLine("Média diária registrada: $avgMl ml (${hydByDay.size} dias).", false)
                hydByDay.entries.take(10).forEach { (day, ml) ->
                    result += PdfLine("$day: $ml ml", false)
                }
            }
        }

        if (categories.contains(SharedCategoryOption.MEDICATIONS.wireKey)) {
            result += PdfLine("3. ADESÃO A LEMBRETES DE MEDICAÇÃO (Origem: Braga Saúde • Lembretes)", true)
            val logsCount = input.medicationLogs.count { it.takenAt.time >= cutoffMillis }
            result += PdfLine(
                "Confirmações de rotina registradas pelo usuário: $logsCount no período (não comprova tomada).",
                false
            )
            if (input.medications.isEmpty()) {
                result += PdfLine("Nenhum lembrete cadastrado na rotina.", false)
            } else {
                input.medications.take(15).forEach { med ->
                    val times = med.scheduleTimes?.takeIf { it.isNotBlank() } ?: med.scheduleTime.orEmpty()
                    val doseText = med.dosage?.takeIf { it.isNotBlank() }
                        ?: med.dosageMg?.let { "${it.toInt()} mg" }.orEmpty()
                    val detail = listOfNotNull(
                        doseText.takeIf { it.isNotBlank() },
                        times.takeIf { it.isNotBlank() }?.let { "Horários: $it" }
                    ).joinToString(" • ")
                    result += PdfLine("${med.name}${if (detail.isNotBlank()) " — $detail" else ""}", false)
                }
            }
        }

        if (categories.contains(SharedCategoryOption.AUTHORIZED_RECORDS.wireKey)) {
            result += PdfLine("4. REGISTROS AUTORIZADOS DE ROTINA (Origem: Autoinformado / App)", true)
            val vitals = input.vitalSigns
                .filter {
                    it.deletedAt == null &&
                        it.measuredAt.time >= cutoffMillis &&
                        (it.systolicPressure != null || it.diastolicPressure != null ||
                            it.glucoseLevel != null || it.heartRate != null || it.oxygenSaturation != null)
                }
                .sortedByDescending { it.measuredAt }
                .take(20)
            if (vitals.isEmpty()) {
                result += PdfLine("Nenhum registro de medição autorizado no período.", false)
            } else {
                vitals.forEach { v ->
                    val origin = when (v.confirmedVia?.lowercase(Locale.ROOT)) {
                        "braga.voice" -> "Braga Voz"
                        "braga.manual" -> "Manual"
                        else -> "App"
                    }
                    val metricsText = buildList {
                        if (v.systolicPressure != null || v.diastolicPressure != null) {
                            add("PA ${v.systolicPressure ?: "--"}/${v.diastolicPressure ?: "--"} mmHg")
                        }
                        v.glucoseLevel?.let { add("Glicose $it mg/dL") }
                        v.heartRate?.let { add("FC $it bpm") }
                        v.oxygenSaturation?.let { add("SpO2 ${"%.0f".format(it)}%") }
                    }.joinToString(" • ")
                    result += PdfLine("${dateStampFmt.format(v.measuredAt)} ($origin): $metricsText", false)
                }
            }
        }

        return result
    }

    private fun drawPartnerHeader(
        canvas: Canvas,
        programName: String,
        companyName: String,
        primaryColor: Int,
        onPrimaryColor: Int
    ) {
        PdfBranding.watermark(context, canvas)
        val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = primaryColor }
        canvas.drawRoundRect(40f, 24f, 555f, 96f, 14f, 14f, headerPaint)

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = onPrimaryColor
            textSize = 16.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val subtitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = onPrimaryColor
            alpha = 230
            textSize = 9.8f
        }
        canvas.drawText(programName.take(44), 56f, 50f, titlePaint)
        canvas.drawText(
            "$companyName · ${B2BThemeEngine.PDF_PRECONSULTATION_TITLE}",
            56f,
            68f,
            subtitlePaint
        )
        canvas.drawText(B2BThemeEngine.PARTNER_ATTRIBUTION, 56f, 84f, subtitlePaint)
    }

    private fun drawParticipantCard(
        canvas: Canvas,
        y: Float,
        participantName: String,
        periodText: String,
        softSurfaceColor: Int,
        softBorderColor: Int,
        titlePaint: Paint,
        subtlePaint: Paint
    ) {
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = softSurfaceColor
            style = Paint.Style.FILL
        }
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = softBorderColor
            style = Paint.Style.STROKE
            strokeWidth = 1f
        }
        canvas.drawRoundRect(46f, y, 549f, y + 54f, 10f, 10f, fillPaint)
        canvas.drawRoundRect(46f, y, 549f, y + 54f, 10f, 10f, borderPaint)
        val boldPaint = Paint(titlePaint).apply {
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val issuedAt = SimpleDateFormat("dd/MM/yyyy 'às' HH:mm", Locale.getDefault()).format(Date())
        canvas.drawText("Participante: ${participantName.take(50)}", 58f, y + 20f, boldPaint)
        canvas.drawText("Período: $periodText • Emitido em $issuedAt", 58f, y + 39f, subtlePaint)
    }

    private fun drawPartnerFooter(
        canvas: Canvas,
        pageNumber: Int,
        totalPages: Int,
        subtlePaint: Paint
    ) {
        val divider = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(203, 213, 225)
            strokeWidth = 0.8f
        }
        canvas.drawLine(46f, 770f, 549f, 770f, divider)
        var noticeY = 784f
        PdfTextLayout.wrap(B2BThemeEngine.PDF_NON_PEP_NOTICE, 495f, subtlePaint::measureText)
            .take(2)
            .forEach { line ->
                canvas.drawText(line, 46f, noticeY, subtlePaint)
                noticeY += 11f
            }
        canvas.drawText(
            "Arquivos pessoais do organizador não incluídos • Página $pageNumber de $totalPages",
            46f,
            814f,
            subtlePaint
        )
    }
}
