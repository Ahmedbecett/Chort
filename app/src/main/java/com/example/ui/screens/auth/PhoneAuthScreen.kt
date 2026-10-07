package com.example.ui.screens.auth

import android.app.Activity
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.repository.ZevoraRepository
import com.example.ui.components.ZevoraMark
import com.example.ui.theme.StatusBanned
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.ZevoraBorder
import com.example.ui.theme.ZevoraCyan
import com.example.ui.theme.ZevoraDarkBg
import com.example.ui.theme.ZevoraDarkElevated
import com.example.ui.theme.ZevoraDarkSurface
import com.example.ui.theme.ZevoraRed
import kotlinx.coroutines.launch

data class CountryCode(val name: String, val dial: String, val flag: String)

val COMMON_COUNTRY_CODES = listOf(
    CountryCode("Algeria", "+213", "🇩🇿"),
    CountryCode("Morocco", "+212", "🇲🇦"),
    CountryCode("Tunisia", "+216", "🇹🇳"),
    CountryCode("Egypt", "+20", "🇪🇬"),
    CountryCode("France", "+33", "🇫🇷"),
    CountryCode("Spain", "+34", "🇪🇸"),
    CountryCode("Italy", "+39", "🇮🇹"),
    CountryCode("United States", "+1", "🇺🇸"),
    CountryCode("United Kingdom", "+44", "🇬🇧"),
    CountryCode("Canada", "+1", "🇨🇦"),
    CountryCode("UAE", "+971", "🇦🇪"),
    CountryCode("Saudi Arabia", "+966", "🇸🇦"),
    CountryCode("Qatar", "+974", "🇶🇦"),
    CountryCode("Turkey", "+90", "🇹🇷"),
    CountryCode("Germany", "+49", "🇩🇪"),
    CountryCode("Netherlands", "+31", "🇳🇱"),
    CountryCode("Belgium", "+32", "🇧🇪")
)

/**
 * Phone-number entry with a country picker. Sends a one-time SMS code,
 * then hands the verified E.164 number to the OTP screen.
 *
 * Registration codes are sent through Firebase Phone Auth and the issued
 * [verificationId] is forwarded via [onCodeSent]; when Firebase verifies
 * instantly (no SMS needed) [onAutoVerified] fires instead. Recovery codes
 * come from the production backend (verificationId is null there).
 */
@Composable
fun PhoneAuthScreen(
    repository: ZevoraRepository,
    mode: String = "register",
    title: String = "Enter your phone number",
    subtitle: String = "We'll text you a 6-digit code to confirm it's really you.",
    onCodeSent: (phone: String, cooldownSeconds: Int, expiresInSeconds: Int, verificationId: String?) -> Unit,
    onBack: () -> Unit,
    onAutoVerified: () -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    var country by remember { mutableStateOf(COMMON_COUNTRY_CODES.first()) }
    var showPicker by remember { mutableStateOf(false) }
    var localNumber by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val fullPhone = remember(country, localNumber) {
        country.dial + localNumber.trim().trimStart('0').filter { it.isDigit() }
    }
    val looksValid = remember(fullPhone) {
        fullPhone.matches(Regex("^\\+[1-9]\\d{7,14}$"))
    }

    if (showPicker) {
        CountryPickerDialog(
            selected = country,
            onSelect = { country = it; showPicker = false },
            onDismiss = { showPicker = false }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ZevoraDarkBg)
            .statusBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
            .testTag("phone_auth_screen"),
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
            ZevoraMark(size = 36.dp)
            Spacer(modifier = Modifier.weight(1f))
            Spacer(modifier = Modifier.width(48.dp))
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = title,
            color = TextPrimary,
            fontSize = 24.sp,
            fontWeight = FontWeight.ExtraBold,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = subtitle,
            color = TextSecondary,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(0.92f)
        )

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

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(ZevoraDarkSurface)
                    .border(1.dp, ZevoraBorder, RoundedCornerShape(14.dp))
                    .clickable { showPicker = true }
                    .padding(horizontal = 12.dp, vertical = 15.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = country.flag, fontSize = 18.sp)
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = country.dial, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = "Choose country",
                    tint = TextSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }
            OutlinedTextField(
                value = localNumber,
                onValueChange = { localNumber = it.filter { c -> c.isDigit() }.take(14) },
                placeholder = { Text("661 23 45 67", color = TextMuted) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = ZevoraDarkSurface,
                    unfocusedContainerColor = ZevoraDarkSurface,
                    focusedBorderColor = ZevoraCyan,
                    unfocusedBorderColor = ZevoraBorder,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    cursorColor = ZevoraCyan
                ),
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = if (localNumber.isBlank()) "${country.name} • ${country.dial}…" else fullPhone,
            color = TextMuted,
            fontSize = 12.sp
        )

        val context = LocalContext.current
        val activity = context as? Activity

        Spacer(modifier = Modifier.height(28.dp))

        Button(
            onClick = {
                if (!looksValid) {
                    errorMessage = "That number doesn't look complete. Check it and try again."
                    return@Button
                }
                isLoading = true
                errorMessage = null
                if (mode == "recovery") {
                    scope.launch {
                        val result = repository.requestRecoveryOtp(fullPhone)
                        isLoading = false
                        if (result.isSuccess) {
                            val otp = result.getOrThrow()
                            onCodeSent(fullPhone, otp.resendCooldownSeconds, otp.expiresInSeconds, null)
                        } else {
                            errorMessage = result.exceptionOrNull()?.message
                        }
                    }
                } else if (activity != null) {
                    repository.sendFirebasePhoneOtp(
                        activity = activity,
                        phone = fullPhone,
                        onCodeSent = { verificationId ->
                            isLoading = false
                            onCodeSent(fullPhone, 60, 600, verificationId)
                        },
                        onAutoVerified = {
                            isLoading = false
                            onAutoVerified()
                        },
                        onError = { fbErr ->
                            isLoading = false
                            errorMessage = fbErr.ifBlank { "Could not send the code. Please try again." }
                        }
                    )
                } else {
                    isLoading = false
                    errorMessage = "Phone verification is unavailable right now. Please try again."
                }
            },
            enabled = !isLoading,
            colors = ButtonDefaults.buttonColors(
                containerColor = ZevoraRed,
                disabledContainerColor = ZevoraDarkElevated
            ),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
        ) {
            if (isLoading) {
                CircularProgressIndicator(color = Color.White, strokeWidth = 2.5.dp, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(10.dp))
                Text("Sending code…", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            } else {
                Text("Send code", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Standard SMS rates may apply. Codes expire quickly and can only be used once.",
            color = TextMuted,
            fontSize = 11.sp,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun CountryPickerDialog(
    selected: CountryCode,
    onSelect: (CountryCode) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(ZevoraDarkSurface)
                .border(1.dp, ZevoraBorder, RoundedCornerShape(20.dp))
                .padding(vertical = 12.dp)
        ) {
            Text(
                text = "Choose country",
                color = TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp)
            )
            LazyColumn(modifier = Modifier.height(340.dp)) {
                items(COMMON_COUNTRY_CODES) { c ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(c) }
                            .background(if (c == selected) ZevoraCyan.copy(alpha = 0.08f) else Color.Transparent)
                            .padding(horizontal = 18.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = c.flag, fontSize = 20.sp)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = c.name,
                            color = TextPrimary,
                            fontSize = 15.sp,
                            modifier = Modifier.weight(1f)
                        )
                        Text(text = c.dial, color = TextSecondary, fontSize = 14.sp)
                        if (c == selected) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(imageVector = Icons.Default.Check, contentDescription = null, tint = ZevoraCyan, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
    }
}
