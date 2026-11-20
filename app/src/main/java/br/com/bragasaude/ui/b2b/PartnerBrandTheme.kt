package br.com.bragasaude.ui.b2b

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.bragasaude.domain.b2b.B2BThemeEngine
import coil3.compose.AsyncImage

data class PartnerBrandColors(
    val primary: Color,
    val onPrimary: Color,
    val accent: Color,
    val softSurface: Color,
    val softBorder: Color
)

private val LocalPartnerBrandColors = staticCompositionLocalOf<PartnerBrandColors?> { null }

internal fun contrastRatio(first: Color, second: Color): Float {
    val lighter = maxOf(first.luminance(), second.luminance())
    val darker = minOf(first.luminance(), second.luminance())
    return (lighter + 0.05f) / (darker + 0.05f)
}

internal fun readableBrandAccent(primary: Color, background: Color): Color {
    val target = if (contrastRatio(Color.White, background) > contrastRatio(Color(0xFF0F172A), background)) {
        Color.White
    } else Color(0xFF0F172A)
    return (0..100).asSequence().map { lerp(primary, target, it / 100f) }
        .firstOrNull { contrastRatio(it, background) >= 4.5f } ?: target
}

/** Usa o tema escolhido no app, sem depender do modo claro/escuro do sistema. */
@Composable
fun PartnerBrandTheme(primaryColor: String?, content: @Composable () -> Unit) {
    val base = MaterialTheme.colorScheme
    val tokens = remember(primaryColor) { B2BThemeEngine.resolveAccessiblePalette(primaryColor) }
    val colors = remember(tokens, base) {
        val primary = Color(android.graphics.Color.parseColor(tokens.primaryHex))
        val dark = base.background.luminance() < 0.5f
        val soft = lerp(base.surface, primary, if (dark) 0.14f else 0.07f)
        PartnerBrandColors(
            primary = primary,
            onPrimary = Color(android.graphics.Color.parseColor(tokens.onPrimaryHex)),
            accent = readableBrandAccent(primary, base.surface),
            softSurface = soft,
            softBorder = lerp(base.surface, primary, if (dark) 0.45f else 0.25f)
        )
    }
    val onAccent = if (contrastRatio(Color.White, colors.accent) >= 4.5f) Color.White else Color(0xFF0F172A)
    val onSoft = readableBrandAccent(colors.primary, colors.softSurface)
    val scheme = base.copy(
        primary = colors.accent, onPrimary = onAccent,
        primaryContainer = colors.softSurface, onPrimaryContainer = onSoft,
        secondary = colors.accent, onSecondary = onAccent,
        secondaryContainer = colors.softSurface, onSecondaryContainer = onSoft,
        tertiary = colors.accent, onTertiary = onAccent,
        tertiaryContainer = colors.softSurface, onTertiaryContainer = onSoft,
        surfaceTint = Color.Transparent
    )
    CompositionLocalProvider(LocalPartnerBrandColors provides colors) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}

@Composable
fun partnerBrandColors(): PartnerBrandColors = LocalPartnerBrandColors.current ?: MaterialTheme.colorScheme.let {
    PartnerBrandColors(it.primary, it.onPrimary, it.primary, it.primaryContainer, it.outlineVariant)
}

/** Cor original no preenchimento; links/campos usam a variante legível do tema. */
@Composable
fun partnerButtonColors(): ButtonColors {
    val brand = partnerBrandColors()
    return ButtonDefaults.buttonColors(containerColor = brand.primary, contentColor = brand.onPrimary)
}

@Composable
fun PartnerTechnologyCredit(modifier: Modifier = Modifier) {
    Text(
        text = B2BThemeEngine.PARTNER_ATTRIBUTION,
        modifier = modifier.fillMaxWidth().padding(vertical = 6.dp).testTag("technology_credit"),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 10.sp,
        lineHeight = 14.sp,
        fontWeight = FontWeight.Normal,
        textAlign = TextAlign.Center
    )
}

/** Marca do parceiro em primeiro plano. O logo inteiro permanece visível, sem recorte. */
@Composable
fun PartnerIdentityLockup(
    companyName: String,
    programName: String,
    logoUrl: String?,
    modifier: Modifier = Modifier
) {
    val brand = partnerBrandColors()
    var logoFailed by remember(logoUrl) { mutableStateOf(false) }
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            modifier = Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(brand.softSurface),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = (companyName.trim().firstOrNull() ?: 'P').uppercaseChar().toString(),
                color = readableBrandAccent(brand.primary, brand.softSurface),
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold
            )
            if (!logoUrl.isNullOrBlank() && !logoFailed) {
                AsyncImage(
                    model = logoUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    onError = { logoFailed = true },
                    modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).padding(4.dp)
                )
            }
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(companyName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(programName, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}
