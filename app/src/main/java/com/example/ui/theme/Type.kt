package com.example.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.R

// App-wide brand typeface: Plus Jakarta Sans (SIL Open Font License).
val ZevoraFont = FontFamily(
    Font(R.font.plus_jakarta_sans_regular, FontWeight.Normal),
    Font(R.font.plus_jakarta_sans_medium, FontWeight.Medium),
    Font(R.font.plus_jakarta_sans_semibold, FontWeight.SemiBold),
    Font(R.font.plus_jakarta_sans_bold, FontWeight.Bold),
    Font(R.font.plus_jakarta_sans_extrabold, FontWeight.ExtraBold)
)

// Set of Material typography styles to start with (brand font on every style).
val Typography = with(Typography()) {
    copy(
        displayLarge = displayLarge.copy(fontFamily = ZevoraFont),
        displayMedium = displayMedium.copy(fontFamily = ZevoraFont),
        displaySmall = displaySmall.copy(fontFamily = ZevoraFont),
        headlineLarge = headlineLarge.copy(fontFamily = ZevoraFont),
        headlineMedium = headlineMedium.copy(fontFamily = ZevoraFont),
        headlineSmall = headlineSmall.copy(fontFamily = ZevoraFont),
        titleLarge = titleLarge.copy(fontFamily = ZevoraFont),
        titleMedium = titleMedium.copy(fontFamily = ZevoraFont),
        titleSmall = titleSmall.copy(fontFamily = ZevoraFont),
        bodyLarge = bodyLarge.copy(fontFamily = ZevoraFont, lineHeight = 24.sp, letterSpacing = 0.5.sp),
        bodyMedium = bodyMedium.copy(fontFamily = ZevoraFont),
        bodySmall = bodySmall.copy(fontFamily = ZevoraFont),
        labelLarge = labelLarge.copy(fontFamily = ZevoraFont),
        labelMedium = labelMedium.copy(fontFamily = ZevoraFont),
        labelSmall = labelSmall.copy(fontFamily = ZevoraFont)
    )
}
