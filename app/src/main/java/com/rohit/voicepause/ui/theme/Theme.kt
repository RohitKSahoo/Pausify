package com.rohit.voicepause.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkScheme = darkColorScheme(
    primary = PausifyRed,
    onPrimary = Color.White,
    primaryContainer = PausifyRedDark,
    onPrimaryContainer = Color.White,
    secondary = PausifyRed,
    onSecondary = Color.Black,
    background = BackgroundDark,
    onBackground = TextPrimary,
    surface = SurfaceDark,
    onSurface = TextPrimary,
    surfaceVariant = CardBackground,
    onSurfaceVariant = TextSecondary,
    outline = OutlineSoft,
    outlineVariant = DividerColor
)

@Composable
fun VoicePauseTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkScheme,
        typography = PausifyTypography,
        shapes = PausifyShapes,
        content = content
    )
}
