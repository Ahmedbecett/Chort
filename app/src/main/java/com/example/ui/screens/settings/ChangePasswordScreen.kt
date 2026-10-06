package com.example.ui.screens.settings

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardOptions
import com.example.data.repository.TokPulseRepository
import com.example.ui.theme.StatusBanned
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TokBorder
import com.example.ui.theme.TokDarkBg
import com.example.ui.theme.TokRed
import kotlinx.coroutines.launch

/** In-app password change: the server verifies the current password. */
@Composable
fun ChangePasswordScreen(
    repository: TokPulseRepository,
    onBack: () -> Unit,
    onChanged: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var current by remember { mutableStateOf("") }
    var next by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var showPasswords by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TokDarkBg)
            .statusBarsPadding()
            .imePadding()
            .padding(horizontal = 24.dp)
            .testTag("change_password_screen")
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
            Icon(imageVector = Icons.Default.Lock, contentDescription = null, tint = TokRed)
            Spacer(modifier = Modifier.weight(1f))
            Spacer(modifier = Modifier.padding(24.dp))
        }

        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Change password",
            color = TextPrimary,
            fontSize = 24.sp,
            fontWeight = FontWeight.ExtraBold
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Your current password is verified on the server. Other devices sign out after a change.",
            color = TextSecondary,
            fontSize = 13.sp
        )
        Spacer(modifier = Modifier.height(20.dp))

        PasswordField("Current password", current, { current = it; error = null }, showPasswords) {
            showPasswords = !showPasswords
        }
        Spacer(modifier = Modifier.height(12.dp))
        PasswordField("New password (6+ characters)", next, { next = it; error = null }, showPasswords) {
            showPasswords = !showPasswords
        }
        Spacer(modifier = Modifier.height(12.dp))
        PasswordField("Confirm new password", confirm, { confirm = it; error = null }, showPasswords) {
            showPasswords = !showPasswords
        }

        if (error != null) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(error!!, color = StatusBanned, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }

        Spacer(modifier = Modifier.height(20.dp))
        Button(
            onClick = {
                if (next.length < 6) {
                    error = "New password must be at least 6 characters."
                    return@Button
                }
                if (next != confirm) {
                    error = "New passwords do not match."
                    return@Button
                }
                isSaving = true
                error = null
                scope.launch {
                    val res = repository.changePassword(current, next)
                    isSaving = false
                    if (res.isSuccess) {
                        Toast.makeText(context, res.getOrNull() ?: "Password changed.", Toast.LENGTH_LONG).show()
                        onChanged()
                    } else {
                        error = res.exceptionOrNull()?.message ?: "Password change failed."
                    }
                }
            },
            enabled = !isSaving && current.isNotBlank() && next.isNotBlank(),
            colors = ButtonDefaults.buttonColors(containerColor = TokRed),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
        ) {
            if (isSaving) {
                CircularProgressIndicator(color = Color.White, strokeWidth = 2.5.dp)
            } else {
                Text("Save new password", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
        }

        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = "Signed in with Google, Facebook or phone only? Use phone recovery from the login screen to set a password.",
            color = TextMuted,
            fontSize = 11.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun PasswordField(
    label: String,
    value: String,
    onValue: (String) -> Unit,
    visible: Boolean,
    onToggleVisibility: () -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValue,
        label = { Text(label, color = TextMuted, fontSize = 13.sp) },
        singleLine = true,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        trailingIcon = {
            IconButton(onClick = onToggleVisibility) {
                Icon(
                    imageVector = if (visible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                    contentDescription = "Toggle visibility",
                    tint = TextMuted
                )
            }
        },
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = TextPrimary,
            unfocusedTextColor = TextPrimary,
            focusedBorderColor = TokRed,
            unfocusedBorderColor = TokBorder
        ),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    )
}
