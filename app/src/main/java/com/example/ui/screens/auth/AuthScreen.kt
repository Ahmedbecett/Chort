package com.example.ui.screens.auth

import android.widget.Toast
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.repository.TokPulseRepository
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

@Composable
fun AuthScreen(
    repository: TokPulseRepository,
    onAuthSuccess: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedTab by remember { mutableIntStateOf(0) } // 0: Sign In, 1: Create Account
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }

    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var successNotice by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TokDarkBg)
            .statusBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // TokPulse Header Icon
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(TokDarkElevated)
                .border(2.dp, TokRed, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Security,
                contentDescription = "TokPulse",
                tint = TokCyan,
                modifier = Modifier.size(38.dp)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Chort",
            color = TextPrimary,
            fontSize = 30.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 1.sp
        )

        Text(
            text = "Cloud Firestore & Object Storage Connected",
            color = TokCyan,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "Sign in or create a creator profile to share short videos, like, comment, and engage with millions.",
            color = TextSecondary,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(0.92f)
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Error Banner
        AnimatedVisibility(visible = errorMessage != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(StatusBanned.copy(alpha = 0.2f))
                    .border(1.dp, StatusBanned, RoundedCornerShape(12.dp))
                    .padding(12.dp)
            ) {
                Text(
                    text = errorMessage ?: "",
                    color = Color.White,
                    fontSize = 13.sp
                )
            }
        }

        // Success Banner
        AnimatedVisibility(visible = successNotice != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(TokCyan.copy(alpha = 0.2f))
                    .border(1.dp, TokCyan, RoundedCornerShape(12.dp))
                    .padding(12.dp)
            ) {
                Text(
                    text = successNotice ?: "",
                    color = Color.White,
                    fontSize = 13.sp
                )
            }
        }

        if (errorMessage != null || successNotice != null) {
            Spacer(modifier = Modifier.height(14.dp))
        }

        // Authentication Card with Tabs
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = TokDarkSurface),
            border = androidx.compose.foundation.BorderStroke(1.dp, TokBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                // Tab Row for Sign In / Register
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = TokDarkSurface,
                    contentColor = TextPrimary,
                    indicator = { tabPositions ->
                        TabRowDefaults.SecondaryIndicator(
                            Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                            color = if (selectedTab == 0) TokCyan else TokRed
                        )
                    }
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = {
                            selectedTab = 0
                            errorMessage = null
                        },
                        text = {
                            Text(
                                text = "Sign In",
                                color = if (selectedTab == 0) TextPrimary else TextMuted,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = {
                            selectedTab = 1
                            errorMessage = null
                        },
                        text = {
                            Text(
                                text = "Create Account",
                                color = if (selectedTab == 1) TextPrimary else TextMuted,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Registration specific inputs
                if (selectedTab == 1) {
                    Text("Display Name", color = TextMuted, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = displayName,
                        onValueChange = { displayName = it },
                        placeholder = { Text("e.g. Ahmed Becetti", color = TextMuted) },
                        leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = TokCyan) },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedBorderColor = TokCyan,
                            unfocusedBorderColor = TokBorder
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("auth_display_name_input")
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text("Username (@handle)", color = TextMuted, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        placeholder = { Text("e.g. ahmed_creator", color = TextMuted) },
                        leadingIcon = { Icon(Icons.Default.AccountCircle, contentDescription = null, tint = TokCyan) },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedBorderColor = TokCyan,
                            unfocusedBorderColor = TokBorder
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("auth_username_input")
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                }

                // Email
                Text("Email Address", color = TextMuted, fontSize = 12.sp)
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    placeholder = { Text("creator@tokpulse.com", color = TextMuted) },
                    leadingIcon = { Icon(Icons.Default.Email, contentDescription = null, tint = TokCyan) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = TokCyan,
                        unfocusedBorderColor = TokBorder
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("auth_email_input")
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Password
                Text("Password", color = TextMuted, fontSize = 12.sp)
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    placeholder = { Text("At least 6 characters", color = TextMuted) },
                    leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = TokCyan) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = TokCyan,
                        unfocusedBorderColor = TokBorder
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("auth_password_input")
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Submit Button
                Button(
                    onClick = {
                        if (email.isBlank() || password.isBlank()) {
                            errorMessage = "Please enter both email and password."
                            return@Button
                        }
                        if (password.length < 6) {
                            errorMessage = "Password must be at least 6 characters."
                            return@Button
                        }

                        isLoading = true
                        errorMessage = null
                        scope.launch {
                            if (selectedTab == 1) {
                                // Register & Create User Snapshot
                                val regResult = repository.registerWithEmail(
                                    email = email.trim(),
                                    password = password.trim(),
                                    username = username.trim(),
                                    displayName = displayName.trim()
                                )
                                isLoading = false
                                regResult.fold(
                                    onSuccess = { user ->
                                        Toast.makeText(context, "User snapshot created for @${user.username}!", Toast.LENGTH_SHORT).show()
                                        onAuthSuccess()
                                    },
                                    onFailure = { err ->
                                        errorMessage = err.message ?: "Failed to create user snapshot."
                                    }
                                )
                            } else {
                                // Sign In & Load User Snapshot
                                val loginResult = repository.signInWithEmail(email.trim(), password.trim())
                                isLoading = false
                                loginResult.fold(
                                    onSuccess = { user ->
                                        Toast.makeText(context, "Welcome back, ${user.displayName}!", Toast.LENGTH_SHORT).show()
                                        onAuthSuccess()
                                    },
                                    onFailure = { err ->
                                        errorMessage = err.message ?: "Failed to sign in."
                                    }
                                )
                            }
                        }
                    },
                    enabled = !isLoading,
                    colors = ButtonDefaults.buttonColors(containerColor = if (selectedTab == 1) TokRed else TokCyan),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("auth_submit_button")
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            color = if (selectedTab == 1) Color.White else Color.Black,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(20.dp)
                        )
                    } else {
                        Text(
                            text = if (selectedTab == 1) "Create Account & Snapshot" else "Log In",
                            color = if (selectedTab == 1) Color.White else Color.Black,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    HorizontalDivider(modifier = Modifier.weight(1f), color = TokBorder)
                    Text("  OR  ", color = TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    HorizontalDivider(modifier = Modifier.weight(1f), color = TokBorder)
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Google Sign In Button
                Button(
                    onClick = {
                        isLoading = true
                        errorMessage = null
                        scope.launch {
                            val result = repository.signInWithGoogle()
                            isLoading = false
                            result.fold(
                                onSuccess = { user ->
                                    Toast.makeText(context, "Welcome, ${user.displayName}!", Toast.LENGTH_SHORT).show()
                                    onAuthSuccess()
                                },
                                onFailure = { err ->
                                    errorMessage = err.message ?: "Google Sign-In failed."
                                }
                            )
                        }
                    },
                    enabled = !isLoading,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("google_sign_in_button")
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.AccountCircle,
                            contentDescription = "Google",
                            tint = Color(0xFF4285F4),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Continue with Google",
                            color = Color(0xFF1F1F1F),
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Quick Admin Login Card
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = TokDarkElevated),
            border = androidx.compose.foundation.BorderStroke(1.dp, TokRed.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.AdminPanelSettings,
                        contentDescription = "Admin Access",
                        tint = TokRed,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Direct Admin Access (Ahmed Becetti)",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "Grants immediate Administrator privileges: view user snapshots, moderation dashboard, violation logs, and policy controls.",
                    color = TextMuted,
                    fontSize = 11.5.sp,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = {
                        scope.launch {
                            val admin = repository.devSwitchToAdmin()
                            Toast.makeText(context, "Logged in as Admin (${admin.displayName})", Toast.LENGTH_SHORT).show()
                            onAuthSuccess()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = TokRed),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .testTag("admin_login_button")
                ) {
                    Text(
                        text = "Enter as Platform Admin",
                        color = Color.White,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "Chort Platform • Production Infrastructure Active",
            color = TextMuted,
            fontSize = 11.sp
        )
    }
}
