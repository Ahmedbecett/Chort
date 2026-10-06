package com.example.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.R

// App-wide brand typeface: Plus Jakarta Sans (SIL Open Font License).
val ThileliFont = FontFamily(
    Font(R.font.plus_jakarta_sans_regular, FontWeight.Normal),
    Font(R.font.plus_jakarta_sans_medium, FontWeight.Medium),
    Font(R.font.plus_jakarta_sans_semibold, FontWeight.SemiBold),
    Font(R.font.plus_jakarta_sans_bold, FontWeight.Bold),
    Font(R.font.plus_jakarta_sans_extrabold, FontWeight.ExtraBold)
)

// Set of Material typography styles to start with (brand font on every style).
val Typography = with(Typography()) {
    copy(
        displayLarge = displayLarge.copy(fontFamily = ThileliFont),
        displayMedium = displayMedium.copy(fontFamily = ThileliFont),
        displaySmall = displaySmall.copy(fontFamily = ThileliFont),
        headlineLarge = headlineLarge.copy(fontFamily = ThileliFont),
        headlineMedium = headlineMedium.copy(fontFamily = ThileliFont),
        headlineSmall = headlineSmall.copy(fontFamily = ThileliFont),
        titleLarge = titleLarge.copy(fontFamily = ThileliFont),
        titleMedium = titleMedium.copy(fontFamily = ThileliFont),
        titleSmall = titleSmall.copy(fontFamily = ThileliFont),
        bodyLarge = bodyLarge.copy(fontFamily = ThileliFont, lineHeight = 24.sp, letterSpacing = 0.5.sp),
        bodyMedium = bodyMedium.copy(fontFamily = ThileliFont),
        bodySmall = bodySmall.copy(fontFamily = ThileliFont),
        labelLarge = labelLarge.copy(fontFamily = ThileliFont),
        labelMedium = labelMedium.copy(fontFamily = ThileliFont),
        labelSmall = labelSmall.copy(fontFamily = ThileliFont)
    )
}
