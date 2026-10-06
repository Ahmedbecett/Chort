package com.example.ui.screens.auth

import android.app.Activity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.remote.FacebookAuth
import com.example.data.remote.GoogleAuth
import com.example.data.repository.ChortRepository
import com.example.ui.components.ChortLogo
import com.example.ui.theme.StatusBanned
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TokBorder
import com.example.ui.theme.TokCyan
import com.example.ui.theme.TokDarkBg
import com.example.ui.theme.TokDarkElevated
import com.example.ui.theme.TokDarkSurface
import com.example.ui.theme.TokRed
import kotlinx.coroutines.launch

/**
 * Sign-in method picker: Google, Facebook, phone number, or email.
 * One account per human - the backend links methods, never duplicates.
 */
@Composable
fun WelcomeAuthScreen(
    repository: ChortRepository,
    onAuthSuccess: () -> Unit,
    onUseEmail: () -> Unit,
    onUsePhone: () -> Unit,
    onRecoverAccount: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val scope = rememberCoroutineScope()

    var busyMethod by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var entered by remember { mutableStateOf(false) }

    androidx.compose.runtime.LaunchedEffect(Unit) { entered = true }

    fun fail(message: String?) {
        if (message == GoogleAuth.CANCELLED || message == FacebookAuth.CANCELLED ||
            message == "cancelled"
        ) {
            return // user dismissed the account picker - not an error
        }
        errorMessage = message ?: "Sign-in failed. Please try again."
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TokDarkBg)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 20.dp)
            .testTag("welcome_auth_screen"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(24.dp))

        AnimatedVisibility(
            visible = entered,
            enter = slideInVertically(initialOffsetY = { -24 }) + fadeIn()
        ) {
            ChortLogo(
                size = 92.dp,
                tagline = "Watch. Create. Belong."
            )
        }

        Spacer(modifier = Modifier.height(28.dp))

        AnimatedVisibility(visible = errorMessage != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(StatusBanned.copy(alpha = 0.18f))
                    .border(1.dp, StatusBanned, RoundedCornerShape(12.dp))
                    .padding(12.dp)
            ) {
                Text(text = errorMessage ?: "", color = Color.White, fontSize = 13.sp)
            }
        }
        if (errorMessage != null) Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Continue with",
            color = TextSecondary,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(14.dp))

        // Google
        AuthMethodButton(
            label = "Continue with Google",
            brandLetter = "G",
            brandColor = Color.White,
            letterColor = Color(0xFF1A1A1A),
            isLoading = busyMethod == "google",
            enabled = busyMethod == null,
            onClick = {
                val act = activity ?: run {
                    fail("Google sign-in needs an Activity context.")
                    return@AuthMethodButton
                }
                busyMethod = "google"
                errorMessage = null
                scope.launch {
                    val result = repository.signInWithGoogleBackend(act)
                    busyMethod = null
                    if (result.isSuccess) onAuthSuccess() else fail(result.exceptionOrNull()?.message)
                }
            }
        )
        Spacer(modifier = Modifier.height(12.dp))

        // Facebook
        AuthMethodButton(
            label = "Continue with Facebook",
            brandLetter = "f",
            brandColor = Color(0xFF1877F2),
            letterColor = Color.White,
            isLoading = busyMethod == "facebook",
            enabled = busyMethod == null,
            onClick = {
                val act = activity ?: run {
                    fail("Facebook sign-in needs an Activity context.")
                    return@AuthMethodButton
                }
                busyMethod = "facebook"
                errorMessage = null
                scope.launch {
                    val result = repository.signInWithFacebookBackend(act)
                    busyMethod = null
                    if (result.isSuccess) onAuthSuccess() else fail(result.exceptionOrNull()?.message)
                }
            }
        )
        Spacer(modifier = Modifier.height(12.dp))

        // Phone
        AuthMethodButton(
            label = "Continue with phone number",
            icon = Icons.Default.PhoneAndroid,
            brandColor = TokCyan,
            letterColor = Color.Black,
            isLoading = false,
            enabled = busyMethod == null,
            onClick = onUsePhone
        )
        Spacer(modifier = Modifier.height(12.dp))

        // Email
        AuthMethodButton(
            label = "Continue with email",
            icon = Icons.Default.Email,
            brandColor = TokRed,
            letterColor = Color.White,
            isLoading = false,
            enabled = busyMethod == null,
            onClick = onUseEmail
        )

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "Trouble signing in? Recover your account",
            color = TokCyan,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.clickable(onClick = onRecoverAccount)
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "By continuing you agree to the thileli dz Terms of Service and Privacy Policy.",
            color = TextMuted,
            fontSize = 11.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(0.9f)
        )
        Spacer(modifier = Modifier.height(12.dp))
    }
}

@Composable
private fun AuthMethodButton(
    label: String,
    brandLetter: String? = null,
    icon: ImageVector? = null,
    brandColor: Color,
    letterColor: Color,
    isLoading: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (enabled) TokDarkSurface else TokDarkElevated)
            .border(1.dp, TokBorder, RoundedCornerShape(16.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(brandColor),
            contentAlignment = Alignment.Center
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    color = letterColor,
                    strokeWidth = 2.5.dp,
                    modifier = Modifier.size(18.dp)
                )
            } else if (brandLetter != null) {
                Text(
                    text = brandLetter,
                    color = letterColor,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            } else if (icon != null) {
                androidx.compose.material3.Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = letterColor,
                    modifier = Modifier.size(19.dp)
                )
            }
        }
        Spacer(modifier = Modifier.width(14.dp))
        Text(
            text = if (isLoading) "Connecting…" else label,
            color = if (enabled) TextPrimary else TextMuted,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}
