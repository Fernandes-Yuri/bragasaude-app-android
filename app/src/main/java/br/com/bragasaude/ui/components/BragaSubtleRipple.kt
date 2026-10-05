package br.com.bragasaude.ui.components

import androidx.compose.material.ripple.RippleAlpha
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.RippleConfiguration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color

/** Feedback discreto para os atalhos e ações do cabeçalho. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BragaSubtleRipple(color: Color, content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalRippleConfiguration provides RippleConfiguration(
            color = color,
            rippleAlpha = RippleAlpha(draggedAlpha = 0.04f, focusedAlpha = 0.04f, hoveredAlpha = 0.02f, pressedAlpha = 0.04f)
        ),
        content = content
    )
}
