package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import com.example.util.AppPrefs

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

private val ZevoraBlackColorScheme = darkColorScheme(
    primary = ZevoraRed,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF3A0A14),
    onPrimaryContainer = Color(0xFFFFDADE),
    secondary = ZevoraCyan,
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFF00302E),
    onSecondaryContainer = Color(0xFF6FF8F4),
    tertiary = AccentGold,
    background = Color.Black,
    onBackground = TextPrimary,
    surface = Color(0xFF0A0A0C),
    onSurface = TextPrimary,
    surfaceVariant = Color(0xFF131316),
    onSurfaceVariant = TextSecondary,
    outline = Color(0xFF232329),
    outlineVariant = Color(0xFF2E2E36),
    error = StatusBanned,
    onError = Color.White
)

@Composable
fun ZevoraTheme(
    content: @Composable () -> Unit
) {
    val themeMode by AppPrefs.themeMode.collectAsState()
    MaterialTheme(
        colorScheme = if (themeMode == "black") ZevoraBlackColorScheme else ZevoraDarkColorScheme,
        typography = Typography,
        content = content
    )
}
