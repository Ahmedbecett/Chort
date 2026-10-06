package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TokCyan
import com.example.ui.theme.TokRed

/**
 * Original Chort brand mark: a bold "C" arc opening into a play triangle,
 * set on a dark tile with a cyan-to-red gradient frame. Drawn in code -
 * no third-party artwork.
 */
@Composable
fun ChortLogo(
    size: Dp = 88.dp,
    showWordmark: Boolean = true,
    tagline: String? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(size)
                .clip(RoundedCornerShape(size * 0.28f))
                .background(
                    Brush.horizontalGradient(listOf(TokCyan, TokRed))
                )
                .padding(size * 0.035f)
                .clip(RoundedCornerShape(size * 0.25f))
                .background(Color(0xFF10131B)),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.size(size * 0.62f)) {
                val w = this.size.width
                val h = this.size.height
                val stroke = w * 0.14f
                // "C" arc: gap faces right where the play triangle sits.
                drawArc(
                    color = Color.White,
                    startAngle = 52f,
                    sweepAngle = 256f,
                    useCenter = false,
                    topLeft = Offset(stroke / 2f, stroke / 2f),
                    size = Size(w - stroke, h - stroke),
                    style = Stroke(width = stroke, cap = StrokeCap.Round)
                )
                // Play triangle in the opening of the C.
                val tri = Path().apply {
                    moveTo(w * 0.44f, h * 0.36f)
                    lineTo(w * 0.66f, h * 0.50f)
                    lineTo(w * 0.44f, h * 0.64f)
                    close()
                }
                drawPath(
                    path = tri,
                    brush = Brush.linearGradient(
                        colors = listOf(TokCyan, TokRed),
                        start = Offset(w * 0.44f, h * 0.36f),
                        end = Offset(w * 0.66f, h * 0.64f)
                    )
                )
            }
        }
        if (showWordmark) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Chort",
                color = TextPrimary,
                fontSize = 30.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 2.sp,
                textAlign = TextAlign.Center
            )
            if (tagline != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = tagline,
                    color = TextSecondary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

/** Compact mark for headers and buttons (tile only, no wordmark). */
@Composable
fun ChortMark(
    size: Dp = 40.dp,
    modifier: Modifier = Modifier
) {
    ChortLogo(size = size, showWordmark = false, modifier = modifier)
}
