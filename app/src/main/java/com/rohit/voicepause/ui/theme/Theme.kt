package com.rohit.voicepause.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable

private val DarkScheme = darkColorScheme(
    background = BackgroundDark,
    surface = SurfaceDark,
    primary = NeonBlue,
    secondary = NeonTeal,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    outline = OutlineSoft
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
