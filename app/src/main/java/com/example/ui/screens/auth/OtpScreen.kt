package com.example.ui.screens.auth

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.repository.TokPulseRepository
import com.example.ui.components.ChortMark
import com.example.ui.theme.AccentGold
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Which backend flow this OTP screen drives. */
enum class OtpMode { REGISTER, RECOVERY }

/**
 * 6-digit code entry with live expiry countdown, resend cooldown,
 * and clear server-driven error states (wrong / expired / locked).
 */
@Composable
fun OtpScreen(
    repository: TokPulseRepository,
    mode: OtpMode,
    phone: String,
    cooldownSeconds: Int,
    expiresInSeconds: Int,
    displayName: String? = null,
    newPassword: String? = null,
    devOtp: String? = null,
    onSuccess: () -> Unit,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var code by remember { mutableStateOf("") }
    var isVerifying by remember { mutableStateOf(false) }
    var isResending by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var successMessage by remember { mutableStateOf<String?>(null) }
    var secondsLeft by remember { mutableIntStateOf(expiresInSeconds) }
    var cooldownLeft by remember { mutableIntStateOf(cooldownSeconds) }
    var setupCode by remember(devOtp) { mutableStateOf(devOtp) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    LaunchedEffect(secondsLeft) {
        if (secondsLeft > 0) {
            delay(1000)
            secondsLeft--
        }
    }
    LaunchedEffect(cooldownLeft) {
        if (cooldownLeft > 0) {
            delay(1000)
            cooldownLeft--
        }
    }

    fun verify() {
        if (code.length != 6 || isVerifying) return
        isVerifying = true
        errorMessage = null
        successMessage = null
        scope.launch {
            try {
                val result = if (mode == OtpMode.RECOVERY) {
                    repository.confirmRecovery(phone, code, newPassword)
                } else {
                    repository.verifyPhoneOtp(phone, code, displayName)
                }
                if (result.isSuccess) {
                    successMessage = if (mode == OtpMode.RECOVERY) "Account recovered. Welcome back!" else "Phone verified. Welcome to thileli dz!"
                    delay(700)
                    onSuccess()
                } else {
                    errorMessage = result.exceptionOrNull()?.message ?: "Verification failed. Please check the code."
                }
            } catch (e: Exception) {
                errorMessage = e.message ?: "Verification error. Please try again."
            } finally {
                isVerifying = false
            }
        }
    }

    fun resend() {
        if (cooldownLeft > 0 || isResending) return
        isResending = true
        errorMessage = null
        scope.launch {
            val result = if (mode == OtpMode.RECOVERY) {
                repository.requestRecoveryOtp(phone)
            } else {
                repository.requestPhoneOtp(phone)
            }
            isResending = false
            if (result.isSuccess) {
                val otp = result.getOrThrow()
                cooldownLeft = otp.resendCooldownSeconds
                secondsLeft = otp.expiresInSeconds
                setupCode = otp.devOtp
                code = ""
                successMessage = "A fresh code is on its way."
            } else {
                errorMessage = result.exceptionOrNull()?.message
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TokDarkBg)
            .statusBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
            .testTag("otp_screen"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = TextPrimary
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            ChortMark(size = 36.dp)
            Spacer(modifier = Modifier.weight(1f))
            Spacer(modifier = Modifier.width(48.dp))
        }

        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = "Enter the 6-digit code",
            color = TextPrimary,
            fontSize = 24.sp,
            fontWeight = FontWeight.ExtraBold,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Sent to $phone",
            color = TextSecondary,
            fontSize = 14.sp,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = if (secondsLeft > 0) {
                "Code expires in ${secondsLeft / 60}:${String.format("%02d", secondsLeft % 60)}"
            } else {
                "This code has expired - request a new one below."
            },
            color = if (secondsLeft > 0) TokCyan else StatusBanned,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
        )
        // Setup mode (no SMS provider yet): the server returns the code itself.
        if (!setupCode.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "Setup mode — your code is ${setupCode!!.trim()}",
                color = AccentGold,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Digit boxes over a hidden input for reliable keyboards + paste.
        Box(contentAlignment = Alignment.Center) {
            BasicTextField(
                value = code,
                onValueChange = { next ->
                    val digits = next.filter { it.isDigit() }.take(6)
                    code = digits
                    errorMessage = null
                    successMessage = null
                    if (digits.length == 6) verify()
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                textStyle = TextStyle(color = Color.Transparent, fontSize = 1.sp),
                cursorBrush = SolidColor(Color.Transparent),
                modifier = Modifier
                    .size(1.dp)
                    .focusRequester(focusRequester),
                decorationBox = { }
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.clickable { focusRequester.requestFocus() }
            ) {
                for (i in 0 until 6) {
                    val digit = code.getOrNull(i)?.toString() ?: ""
                    val filled = digit.isNotEmpty()
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(TokDarkSurface)
                            .border(
                                1.5.dp,
                                when {
                                    errorMessage != null -> StatusBanned
                                    filled -> TokCyan
                                    i == code.length -> TextSecondary
                                    else -> TokBorder
                                },
                                RoundedCornerShape(14.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = digit,
                            color = TextPrimary,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        AnimatedVisibility(visible = errorMessage != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(StatusBanned.copy(alpha = 0.18f))
                    .border(1.dp, StatusBanned, RoundedCornerShape(12.dp))
                    .padding(12.dp)
            ) {
                Text(text = errorMessage ?: "", color = Color.White, fontSize = 13.sp, textAlign = TextAlign.Center)
            }
        }
        AnimatedVisibility(visible = successMessage != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(TokCyan.copy(alpha = 0.14f))
                    .border(1.dp, TokCyan, RoundedCornerShape(12.dp))
                    .padding(12.dp)
            ) {
                Text(text = successMessage ?: "", color = Color.White, fontSize = 13.sp, textAlign = TextAlign.Center)
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Button(
            onClick = { verify() },
            enabled = code.length == 6 && !isVerifying,
            colors = ButtonDefaults.buttonColors(
                containerColor = TokRed,
                disabledContainerColor = TokDarkElevated
            ),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
        ) {
            if (isVerifying) {
                CircularProgressIndicator(color = Color.White, strokeWidth = 2.5.dp, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(10.dp))
                Text("Verifying…", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            } else {
                Text(
                    if (mode == OtpMode.RECOVERY) "Recover account" else "Verify & continue",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (isResending) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(color = TokCyan, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Sending a new code…", color = TextSecondary, fontSize = 13.sp)
            }
        } else if (cooldownLeft > 0) {
            Text(
                text = "Resend code in ${cooldownLeft}s",
                color = TextMuted,
                fontSize = 13.sp
            )
        } else {
            Text(
                text = "Didn't get it? Resend code",
                color = TokCyan,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable { resend() }
            )
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}
