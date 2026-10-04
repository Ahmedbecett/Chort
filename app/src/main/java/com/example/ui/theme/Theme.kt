package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val TokDarkColorScheme = darkColorScheme(
    primary = TokRed,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF500F1C),
    onPrimaryContainer = Color(0xFFFFDADE),
    secondary = TokCyan,
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFF004F4D),
    onSecondaryContainer = Color(0xFF6FF8F4),
    tertiary = AccentGold,
    background = TokDarkBg,
    onBackground = TextPrimary,
    surface = TokDarkSurface,
    onSurface = TextPrimary,
    surfaceVariant = TokDarkElevated,
    onSurfaceVariant = TextSecondary,
    outline = TokBorder,
    outlineVariant = Color(0xFF383C4D),
    error = StatusBanned,
    onError = Color.White
)

@Composable
fun TokPulseTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = TokDarkColorScheme,
        typography = Typography,
        content = content
    )
}
