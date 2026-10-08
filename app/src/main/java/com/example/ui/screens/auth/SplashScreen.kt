package com.example.ui.screens.auth

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.ZevoraLogo
import com.example.ui.theme.TextMuted
import com.example.ui.theme.ZevoraCyan
import com.example.ui.theme.ZevoraDarkBg
import kotlinx.coroutines.delay

/**
 * Rivo launch splash: brand identity up top, soft scale+fade entrance,
 * then hands off to the app (feed when a session exists, welcome otherwise).
 */
@Composable
fun SplashScreen(onFinished: () -> Unit) {
    val alpha = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        alpha.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 500)
        )
        delay(1400)
        onFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ZevoraDarkBg)
            .statusBarsPadding()
            .testTag("splash_screen"),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier.alpha(alpha.value)
            ) {
                ZevoraLogo(
                    size = 104.dp,
                    tagline = "Short videos, made social"
                )
            }
            Spacer(modifier = Modifier.height(28.dp))
            CircularProgressIndicator(
                color = ZevoraCyan,
                strokeWidth = 3.dp,
                modifier = Modifier.alpha(alpha.value)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Loading your experience…",
                color = TextMuted,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.alpha(alpha.value)
            )
        }
        Text(
            text = "Rivo • v3.2.2",
            color = Color.White.copy(alpha = 0.25f),
            fontSize = 11.sp,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}
