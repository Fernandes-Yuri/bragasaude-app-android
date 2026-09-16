package br.com.bragasaude.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// ESQUEMA CLARO MODERNO (Base Teal com Neutros Limpos)
private val LightColorScheme = lightColorScheme(
    primary = TealPrimary,
    onPrimary = OnPrimary,
    primaryContainer = TealLight,
    onPrimaryContainer = TealPrimary,
    secondary = TealSecondary,
    onSecondary = Color.White,
    secondaryContainer = TealSurface,
    onSecondaryContainer = TealPrimary,
    background = Background,
    onBackground = OnBackground,
    surface = Surface,
    onSurface = OnSurface,
    surfaceVariant = SurfaceVariant,
    onSurfaceVariant = OnSurfaceVariant,
    error = Error,
    errorContainer = AccentRedLight,
    onErrorContainer = AccentRed
)

// ESQUEMA ESCURO HARMONIOSO E ELEGANTE (Verde-ardósia escuro, sem cortes bruscos)
private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF26A69A),
    onPrimary = Color(0xFF042F2E),
    primaryContainer = Color(0xFF164E44),
    onPrimaryContainer = Color(0xFFCCFBF1),
    secondary = Color(0xFF80CBC4),
    onSecondary = Color(0xFF003831),
    secondaryContainer = Color(0xFF1F463D),
    onSecondaryContainer = Color(0xFFE0F2F1),
    background = Color(0xFF0F1A16),          // Fundo escuro suave com sutil matiz esmeralda
    onBackground = Color(0xFFF1F8F5),        // Alto contraste e leitura confortável
    surface = Color(0xFF182822),             // Superfície elevada elegante
    onSurface = Color(0xFFF1F8F5),
    surfaceVariant = Color(0xFF22362F),      // Cards de apoio e seleções sutis
    onSurfaceVariant = Color(0xFFB0C8BF),
    error = Error,
    errorContainer = Color(0xFF7F1D1D),
    onErrorContainer = Color(0xFFFECACA)
)

@Composable
fun BragasaudeTheme(
    forceThemeMode: String? = null,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val systemInDark = isSystemInDarkTheme()
    val savedMode = forceThemeMode ?: br.com.bragasaude.util.AppPreferences.getThemeMode(context)
    val isDark = when (savedMode) {
        br.com.bragasaude.util.AppPreferences.THEME_MODE_LIGHT -> false
        br.com.bragasaude.util.AppPreferences.THEME_MODE_DARK -> true
        else -> systemInDark
    }

    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        isDark -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
