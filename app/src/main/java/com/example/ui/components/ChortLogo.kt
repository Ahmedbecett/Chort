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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.ThileliFont
import com.example.ui.theme.TokCyan
import com.example.ui.theme.TokRed

/**
 * Original thileli dz brand mark: an eighth-note whose head is a play
 * triangle, drawn with a neon glow (soft cyan/red light layers behind a
 * crisp white core). Still by design - the glow does the talking.
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
                .padding(size * 0.03f)
                .clip(RoundedCornerShape(size * 0.25f))
                .background(Color(0xFF0B0D13)),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.size(size * 0.66f)) {
                // this.size = DrawScope canvas size in px (the Dp param shadows it by name).
                val s = this.size.minDimension
                val ox = (this.size.width - s) / 2f
                val oy = (this.size.height - s) / 2f
                // Neon glow: wide soft halos, then chromatic offsets, then core.
                drawNoteMark(TokRed.copy(alpha = 0.30f), ox + s * 0.035f, oy, s, s * 0.16f)
                drawNoteMark(TokCyan.copy(alpha = 0.30f), ox - s * 0.035f, oy, s, s * 0.16f)
                drawNoteMark(TokRed.copy(alpha = 0.65f), ox + s * 0.018f, oy, s, s * 0.115f)
                drawNoteMark(TokCyan.copy(alpha = 0.65f), ox - s * 0.018f, oy, s, s * 0.115f)
                drawNoteMark(Color.White, ox, oy, s, s * 0.10f)
            }
        }
        if (showWordmark) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "thileli dz",
                color = TextPrimary,
                fontSize = 30.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = ThileliFont,
                letterSpacing = 1.sp,
                textAlign = TextAlign.Center
            )
            if (tagline != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = tagline,
                    color = TextSecondary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = ThileliFont,
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

private fun DrawScope.drawNoteMark(color: Color, ox: Float, oy: Float, s: Float, stroke: Float) {
    // Stem of the note.
    drawRoundRect(
        color = color,
        topLeft = Offset(ox + s * 0.52f, oy + s * 0.16f),
        size = Size(s * 0.10f, s * 0.48f),
        cornerRadius = CornerRadius(s * 0.05f)
    )
    // Flag sweeping off the stem.
    drawPath(
        path = Path().apply {
            moveTo(ox + s * 0.57f, oy + s * 0.17f)
            cubicTo(
                ox + s * 0.78f, oy + s * 0.21f,
                ox + s * 0.82f, oy + s * 0.34f,
                ox + s * 0.76f, oy + s * 0.46f
            )
        },
        color = color,
        style = Stroke(width = stroke, cap = StrokeCap.Round)
    )
    // Head of the note: a play triangle.
    drawPath(
        path = Path().apply {
            moveTo(ox + s * 0.24f, oy + s * 0.50f)
            lineTo(ox + s * 0.24f, oy + s * 0.82f)
            lineTo(ox + s * 0.56f, oy + s * 0.66f)
            close()
        },
        color = color
    )
}
