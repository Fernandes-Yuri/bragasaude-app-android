package br.com.bragasaude.domain.b2b

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import br.com.bragasaude.domain.PdfBranding
import br.com.bragasaude.domain.PdfTextLayout
import br.com.bragasaude.util.QrCodeGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * B2B-16B: Gerador local de placa A4 em PDF para recepcao de clinicas/parceiros
 * com QR Code nativo apontando para `https://bragasaude.online/convite/<CODE>`.
 */
class PartnerReceptionSignPdfGenerator(private val context: Context) {

    suspend fun generateSignPdf(
        programName: String,
        companyName: String,
        inviteCode: String,
        primaryColorHex: String?
    ): File = withContext(Dispatchers.IO) {
        val cleanCode = inviteCode.trim().uppercase()
        val deepLink = B2BOnboardingValidator.buildInviteDeepLink(cleanCode)
            ?: error("Código de convite inválido para geração da placa.")

        val tokens = B2BThemeEngine.resolveAccessiblePalette(primaryColorHex)
        val primaryColor = Color.parseColor(tokens.primaryHex)
        val onPrimaryColor = Color.parseColor(tokens.onPrimaryHex)
        val accentTextColor = Color.parseColor(tokens.accentOnLightHex)
        val softSurfaceColor = Color.parseColor(tokens.softSurfaceHex)
        val softBorderColor = Color.parseColor(tokens.softBorderHex)

        val qrBitmap = QrCodeGenerator.generateQrCode(deepLink, size = 512)

        val doc = PdfDocument()
        try {
            val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
            val page = doc.startPage(pageInfo)
            val canvas = page.canvas

            PdfBranding.watermark(context, canvas)

            val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = primaryColor }
            canvas.drawRoundRect(40f, 32f, 555f, 132f, 18f, 18f, headerPaint)

            val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = onPrimaryColor
                alpha = 230
                textSize = 10.5f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = onPrimaryColor
                textSize = 21f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = onPrimaryColor
                alpha = 235
                textSize = 11.5f
            }
            canvas.drawText("ESPAÇO PARCEIRO · ${B2BThemeEngine.PARTNER_ATTRIBUTION.uppercase()}", 62f, 60f, labelPaint)
            canvas.drawText(programName.take(36), 62f, 88f, titlePaint)
            canvas.drawText(companyName.take(48), 62f, 112f, subPaint)

            val leadPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(15, 23, 42)
                textSize = 16f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText("Acompanhe sua rotina pelo app Braga Saúde", 297.5f, 174f, leadPaint)

            val leadSubPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(71, 85, 105)
                textSize = 11.5f
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText(
                "Aponte a câmera do celular para o QR Code abaixo ou informe o código no app",
                297.5f,
                196f,
                leadSubPaint
            )

            // Moldura do QR Code em alta nitidez
            val cardFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = softSurfaceColor }
            val cardBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = softBorderColor
                style = Paint.Style.STROKE
                strokeWidth = 1.6f
            }
            canvas.drawRoundRect(112f, 222f, 483f, 594f, 22f, 22f, cardFill)
            canvas.drawRoundRect(112f, 222f, 483f, 594f, 22f, 22f, cardBorder)

            val whiteBox = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
            canvas.drawRoundRect(148f, 244f, 447f, 543f, 16f, 16f, whiteBox)

            val qrPaint = Paint().apply { isFilterBitmap = false }
            val qrDest = Rect(164, 260, 431, 527)
            canvas.drawBitmap(qrBitmap, null, qrDest, qrPaint)

            val codeLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = accentTextColor
                textSize = 20f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText("CÓDIGO DE CONVITE: $cleanCode", 297.5f, 574f, codeLabelPaint)

            // Passo a passo na recepcao
            val stepTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = accentTextColor
                textSize = 12f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val stepBodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(30, 41, 59)
                textSize = 10.8f
            }
            canvas.drawText("Como vincular em menos de 1 minuto:", 62f, 636f, stepTitlePaint)
            canvas.drawText("1. Baixe ou abra o aplicativo Braga Saúde no seu celular.", 62f, 658f, stepBodyPaint)
            canvas.drawText("2. Escaneie este QR Code ou acesse Programas Parceiros com o código $cleanCode.", 62f, 678f, stepBodyPaint)
            canvas.drawText("3. Escolha com total autonomia quais categorias de rotina deseja compartilhar.", 62f, 698f, stepBodyPaint)

            val urlPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(71, 85, 105)
                textSize = 9.5f
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText("Link direto do convite: $deepLink", 297.5f, 734f, urlPaint)

            // Rodape com garantia LGPD / Nao-PEP
            val divider = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(203, 213, 225)
                strokeWidth = 0.8f
            }
            canvas.drawLine(46f, 764f, 549f, 764f, divider)
            val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(71, 85, 105)
                textSize = 8.7f
            }
            var fy = 782f
            PdfTextLayout.wrap(
                "Sua conta pessoal e seus arquivos privados permanecem sob seu controle exclusivo. Este espaço organiza o acompanhamento da rotina com base no consentimento do participante e não substitui avaliação profissional ou prontuário oficial.",
                495f,
                footerPaint::measureText
            ).take(3).forEach { line ->
                canvas.drawText(line, 46f, fy, footerPaint)
                fy += 11.5f
            }

            doc.finishPage(page)

            val dir = File(context.cacheDir, "relatorios").apply { mkdirs() }
            val safeSlug = cleanCode.replace(Regex("[^A-Z0-9_-]"), "")
            val outFile = File(dir, "placa_recepcao_${safeSlug}_${System.currentTimeMillis()}.pdf")
            FileOutputStream(outFile).use { stream ->
                doc.writeTo(stream)
            }
            outFile
        } finally {
            if (!qrBitmap.isRecycled) qrBitmap.recycle()
            doc.close()
        }
    }
}
