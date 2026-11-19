package br.com.bragasaude.domain.b2b

import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Paleta resolvida com contraste minimo WCAG 2.1 AA (4.5:1) em Kotlin puro
 * (pronto para KMP / iOS) para qualquer cor cadastrada pelo parceiro.
 */
data class PartnerVisualTokens(
    val primaryHex: String,
    val onPrimaryHex: String,
    val softSurfaceHex: String,
    val softBorderHex: String,
    val accentOnLightHex: String,
    val accentOnDarkHex: String,
    val onPrimaryContrastRatio: Double
)

object B2BThemeEngine {
    const val PARTNER_AREA_TITLE = "Rotina compartilhada"
    const val PARTNER_RECORDS_TITLE = "Registros autorizados"
    const val PARTNER_ATTRIBUTION = "Tecnologia Braga Saúde"
    const val PDF_PRECONSULTATION_TITLE = "Resumo de rotina para a consulta"
    const val PDF_NON_PEP_NOTICE =
        "Resumo de rotina para a consulta. Dados selecionados pelo paciente; não constitui laudo, diagnóstico nem prontuário oficial."
    const val PDF_SHARE_ACTION_LABEL = "Revisar e compartilhar PDF"
    const val DIRECT_CHAT_NON_URGENCY_NOTICE =
        "Canal assíncrono de rotina. Não utilize para emergências — em caso de urgência, procure atendimento imediato (SAMU 192)."
    const val DIRECT_CHAT_ZERO_ATTACHMENT_HINT =
        "Apenas mensagens de texto (envio de anexos, imagens ou PDFs não permitido neste canal)."
    const val WCAG_AA_NORMAL_TEXT_RATIO = 4.5

    private const val WHITE_HEX = "#FFFFFF"
    private const val DARK_INK_HEX = "#0F172A"
    private const val DARK_SURFACE_HEX = "#0F1A16"

    fun parseHexRgb(rawHex: String?): Triple<Int, Int, Int> {
        val clean = B2BOnboardingValidator.normalizeHexColorOrDefault(rawHex).removePrefix("#")
        val r = clean.substring(0, 2).toInt(16)
        val g = clean.substring(2, 4).toInt(16)
        val b = clean.substring(4, 6).toInt(16)
        return Triple(r, g, b)
    }

    fun formatHexRgb(r: Int, g: Int, b: Int): String {
        val rr = r.coerceIn(0, 255).toString(16).padStart(2, '0')
        val gg = g.coerceIn(0, 255).toString(16).padStart(2, '0')
        val bb = b.coerceIn(0, 255).toString(16).padStart(2, '0')
        return "#${rr}${gg}${bb}".uppercase()
    }

    private fun linearizeChannel(channel255: Int): Double {
        val srgb = channel255.coerceIn(0, 255) / 255.0
        return if (srgb <= 0.04045) srgb / 12.92 else ((srgb + 0.055) / 1.055).pow(2.4)
    }

    fun relativeLuminance(hex: String): Double {
        val (r, g, b) = parseHexRgb(hex)
        return 0.2126 * linearizeChannel(r) +
            0.7152 * linearizeChannel(g) +
            0.0722 * linearizeChannel(b)
    }

    fun contrastRatio(fgHex: String, bgHex: String): Double {
        val l1 = relativeLuminance(fgHex)
        val l2 = relativeLuminance(bgHex)
        val lighter = max(l1, l2)
        val darker = min(l1, l2)
        return (lighter + 0.05) / (darker + 0.05)
    }

    private fun mixHex(hexA: String, hexB: String, weightB: Double): String {
        val w = weightB.coerceIn(0.0, 1.0)
        val (r1, g1, b1) = parseHexRgb(hexA)
        val (r2, g2, b2) = parseHexRgb(hexB)
        return formatHexRgb(
            (r1 * (1.0 - w) + r2 * w).roundToInt(),
            (g1 * (1.0 - w) + g2 * w).roundToInt(),
            (b1 * (1.0 - w) + b2 * w).roundToInt()
        )
    }

    private fun ensureContrastAgainst(
        seedHex: String,
        targetBgHex: String,
        blendTowardHex: String
    ): String {
        if (contrastRatio(seedHex, targetBgHex) >= WCAG_AA_NORMAL_TEXT_RATIO) {
            return seedHex
        }
        for (step in 1..20) {
            val candidate = mixHex(seedHex, blendTowardHex, step / 20.0)
            if (contrastRatio(candidate, targetBgHex) >= WCAG_AA_NORMAL_TEXT_RATIO) {
                return candidate
            }
        }
        return blendTowardHex
    }

    fun resolveAccessiblePalette(rawPrimaryHex: String?): PartnerVisualTokens {
        val primary = B2BOnboardingValidator.normalizeHexColorOrDefault(rawPrimaryHex)
        val whiteRatio = contrastRatio(WHITE_HEX, primary)
        val darkRatio = contrastRatio(DARK_INK_HEX, primary)
        val onPrimary = if (whiteRatio >= WCAG_AA_NORMAL_TEXT_RATIO || whiteRatio >= darkRatio) {
            WHITE_HEX
        } else {
            DARK_INK_HEX
        }
        val effectiveRatio = contrastRatio(onPrimary, primary)
        val softSurface = mixHex(primary, WHITE_HEX, 0.90)
        val softBorder = mixHex(primary, WHITE_HEX, 0.65)
        val accentOnLight = ensureContrastAgainst(primary, WHITE_HEX, DARK_INK_HEX)
        val accentOnDark = ensureContrastAgainst(primary, DARK_SURFACE_HEX, WHITE_HEX)

        return PartnerVisualTokens(
            primaryHex = primary,
            onPrimaryHex = onPrimary,
            softSurfaceHex = softSurface,
            softBorderHex = softBorder,
            accentOnLightHex = accentOnLight,
            accentOnDarkHex = accentOnDark,
            onPrimaryContrastRatio = effectiveRatio
        )
    }
}
