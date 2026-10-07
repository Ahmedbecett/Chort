package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val ZevoraDarkColorScheme = darkColorScheme(
    primary = ZevoraRed,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF500F1C),
    onPrimaryContainer = Color(0xFFFFDADE),
    secondary = ZevoraCyan,
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFF004F4D),
    onSecondaryContainer = Color(0xFF6FF8F4),
    tertiary = AccentGold,
    background = ZevoraDarkBg,
    onBackground = TextPrimary,
    surface = ZevoraDarkSurface,
    onSurface = TextPrimary,
    surfaceVariant = ZevoraDarkElevated,
    onSurfaceVariant = TextSecondary,
    outline = ZevoraBorder,
    outlineVariant = Color(0xFF383C4D),
    error = StatusBanned,
    onError = Color.White
)

@Composable
fun ZevoraTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = ZevoraDarkColorScheme,
        typography = Typography,
        content = content
    )
}
