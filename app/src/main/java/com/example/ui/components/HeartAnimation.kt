package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.example.ui.theme.ZevoraRed

@Composable
fun BurstHeart(
    trigger: Long,
    onComplete: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (trigger == 0L) return

    val scale = remember(trigger) { Animatable(0.2f) }
    val alpha = remember(trigger) { Animatable(1f) }
    val rotation = remember(trigger) { (Math.random() * 30 - 15).toFloat() }

    LaunchedEffect(trigger) {
        scale.animateTo(
            targetValue = 1.3f,
            animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing)
        )
        scale.animateTo(
            targetValue = 1.1f,
            animationSpec = tween(durationMillis = 150)
        )
        alpha.animateTo(
            targetValue = 0f,
            animationSpec = tween(durationMillis = 250)
        )
        onComplete()
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.Favorite,
            contentDescription = "Liked",
            tint = ZevoraRed,
            modifier = Modifier
                .size(100.dp)
                .scale(scale.value)
                .alpha(alpha.value)
                .graphicsLayer(rotationZ = rotation)
        )
    }
}

