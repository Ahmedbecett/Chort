package com.example.ui.screens.auth

import androidx.compose.foundation.background
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.repository.TokPulseRepository
import com.example.ui.components.ChortMark
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

/**
 * Account recovery, step by step:
 * 1) verified phone number -> SMS code
 * 2) choose a new password (optional - skip to keep the old one)
 * 3) enter the code -> account recovered + signed in
 */
@Composable
fun RecoveryScreen(
    repository: TokPulseRepository,
    onRecovered: () -> Unit,
    onBack: () -> Unit
) {
    var step by remember { mutableStateOf(0) } // 0 = phone, 1 = new password, 2 = code
    var phone by remember { mutableStateOf("") }
    var cooldown by remember { mutableStateOf(60) }
    var expiresIn by remember { mutableStateOf(600) }
    var newPassword by remember { mutableStateOf("") }

    when (step) {
        0 -> PhoneAuthScreen(
            repository = repository,
            mode = "recovery",
            title = "Recover your account",
            subtitle = "Enter the phone number linked to your account. We'll text you a code.",
            onCodeSent = { sentPhone, sentCooldown, sentExpires, sentDevOtp ->
                phone = sentPhone
                cooldown = sentCooldown
                expiresIn = sentExpires
                step = 1
            },
            onBack = onBack
        )

        1 -> NewPasswordStep(
            onContinue = { password ->
                newPassword = password
                step = 2
            },
            onBack = { step = 0 }
        )

        else -> OtpScreen(
            repository = repository,
            mode = OtpMode.RECOVERY,
            phone = phone,
            cooldownSeconds = cooldown,
            expiresInSeconds = expiresIn,
            newPassword = newPassword.ifBlank { null },
            onSuccess = onRecovered,
            onBack = { step = 1 }
        )
    }
}

@Composable
private fun NewPasswordStep(
    onContinue: (String) -> Unit,
    onBack: () -> Unit
) {
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TokDarkBg)
            .statusBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
            .testTag("recovery_password_step"),
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
            text = "Choose a new password",
            color = TextPrimary,
            fontSize = 24.sp,
            fontWeight = FontWeight.ExtraBold,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Optional - leave empty to keep your current password.",
            color = TextSecondary,
            fontSize = 14.sp,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))

        if (error != null) {
            Text(text = error ?: "", color = StatusBanned, fontSize = 13.sp)
            Spacer(modifier = Modifier.height(12.dp))
        }

        OutlinedTextField(
            value = password,
            onValueChange = { password = it.take(100); error = null },
            label = { Text("New password", color = TextMuted) },
            leadingIcon = { Icon(imageVector = Icons.Default.Lock, contentDescription = null, tint = TextMuted) },
            trailingIcon = {
                IconButton(onClick = { showPassword = !showPassword }) {
                    Icon(
                        imageVector = if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = "Toggle visibility",
                        tint = TextMuted
                    )
                }
            },
            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = TokDarkSurface,
                unfocusedContainerColor = TokDarkSurface,
                focusedBorderColor = TokCyan,
                unfocusedBorderColor = TokBorder,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary,
                cursorColor = TokCyan
            ),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = confirm,
            onValueChange = { confirm = it.take(100); error = null },
            label = { Text("Confirm new password", color = TextMuted) },
            leadingIcon = { Icon(imageVector = Icons.Default.Lock, contentDescription = null, tint = TextMuted) },
            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = TokDarkSurface,
                unfocusedContainerColor = TokDarkSurface,
                focusedBorderColor = TokCyan,
                unfocusedBorderColor = TokBorder,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary,
                cursorColor = TokCyan
            ),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(28.dp))
        Button(
            onClick = {
                if (password.isNotBlank() || confirm.isNotBlank()) {
                    if (password.length < 6) {
                        error = "Password must be at least 6 characters."
                        return@Button
                    }
                    if (password != confirm) {
                        error = "Passwords don't match."
                        return@Button
                    }
                }
                onContinue(password)
            },
            colors = ButtonDefaults.buttonColors(
                containerColor = TokRed,
                disabledContainerColor = TokDarkElevated
            ),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
        ) {
            Text(
                if (password.isBlank() && confirm.isBlank()) "Skip - keep my password" else "Continue",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}
