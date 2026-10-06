package com.example.ui.screens.settings

import android.app.Activity
import android.content.Context
import android.content.Intent
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Comment
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import com.example.data.remote.ApiSession
import com.example.data.remote.LinkedProvider
import com.example.data.repository.TokPulseRepository
import com.example.ui.theme.StatusBanned
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TokBorder
import com.example.ui.theme.TokCyan
import com.example.ui.theme.TokDarkBg
import com.example.ui.theme.TokDarkSurface
import com.example.ui.theme.TokRed
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

/**
 * Settings and privacy, in grouped cards: every toggle is persisted on
 * device, and account/security rows are backed by the live thileli dz API
 * (linked providers, active sessions, sign-out).
 */
@Composable
fun SettingsScreen(
    repository: TokPulseRepository,
    onBack: () -> Unit,
    onNavigateToProfile: () -> Unit,
    onNavigateToInbox: () -> Unit,
    onNavigateToLegal: (String) -> Unit,
    onLoggedOut: () -> Unit,
    onChangePassword: () -> Unit = {}
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val scope = rememberCoroutineScope()
    val currentUser by repository.currentUser.collectAsState()
    val prefs = remember { context.getSharedPreferences("chort_settings", Context.MODE_PRIVATE) }

    var autoplay by remember { mutableStateOf(prefs.getBoolean("autoplay", true)) }
    var dataSaver by remember { mutableStateOf(prefs.getBoolean("data_saver", false)) }
    var pushEnabled by remember { mutableStateOf(prefs.getBoolean("push_enabled", true)) }
    var wellbeingReminder by remember { mutableStateOf(prefs.getBoolean("wellbeing_reminder", false)) }
    var privateAccount by remember { mutableStateOf(prefs.getBoolean("private_account", false)) }
    var followingVisibility by remember { mutableStateOf(prefs.getString("following_visibility", "Only you") ?: "Only you") }
    var likedVisibility by remember { mutableStateOf(prefs.getString("liked_visibility", "Only you") ?: "Only you") }
    var commentsAllowed by remember { mutableStateOf(prefs.getBoolean("comments_allowed", true)) }
    var downloadsAllowed by remember { mutableStateOf(prefs.getBoolean("downloads_allowed", true)) }
    var viewersOn by remember { mutableStateOf(prefs.getBoolean("viewers_on", true)) }

    var showAccountCard by remember { mutableStateOf(false) }
    var showSecurityCard by remember { mutableStateOf(false) }
    var showLogoutConfirm by remember { mutableStateOf(false) }
    var isLoggingOut by remember { mutableStateOf(false) }

    fun saveBoolean(key: String, value: Boolean) {
        prefs.edit().putBoolean(key, value).apply()
    }
    fun saveString(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }
    fun cycleVisibility(current: String): String {
        return if (current == "Everyone") "Only you" else "Everyone"
    }

    if (showLogoutConfirm) {
        AlertDialog(
            onDismissRequest = { showLogoutConfirm = false },
            title = { Text("Log out?", color = TextPrimary, fontWeight = FontWeight.Bold) },
            text = { Text("You'll be signed out on this device.", color = TextSecondary) },
            confirmButton = {
                Button(
                    onClick = {
                        isLoggingOut = true
                        scope.launch {
                            repository.logout()
                            isLoggingOut = false
                            showLogoutConfirm = false
                            onLoggedOut()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = TokRed),
                    enabled = !isLoggingOut
                ) {
                    Text(if (isLoggingOut) "Logging out…" else "Log out", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutConfirm = false }) {
                    Text("Cancel", color = TokCyan)
                }
            },
            containerColor = TokDarkSurface
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TokDarkBg)
            .statusBarsPadding()
            .testTag("settings_screen")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = TextPrimary
                )
            }
            Text(
                text = "Settings and privacy",
                color = TextPrimary,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(48.dp))
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp)
        ) {
            SettingsCard {
                SettingRow(
                    icon = Icons.Default.Description,
                    title = "Manage posts",
                    onClick = onNavigateToProfile
                )
                SettingToggleRow(
                    icon = Icons.Default.Tune,
                    title = "Autoplay videos",
                    subtitle = "Play videos automatically in feeds",
                    checked = autoplay,
                    onCheckedChange = { autoplay = it; saveBoolean("autoplay", it) }
                )
                SettingToggleRow(
                    icon = Icons.Default.Download,
                    title = "Reduce mobile data",
                    subtitle = "Lower playback quality on mobile data",
                    checked = dataSaver,
                    onCheckedChange = { dataSaver = it; saveBoolean("data_saver", it) }
                )
                SettingToggleRow(
                    icon = Icons.Default.Notifications,
                    title = "Push notifications",
                    subtitle = "Likes, comments and new followers",
                    checked = pushEnabled,
                    onCheckedChange = { pushEnabled = it; saveBoolean("push_enabled", it) }
                )
                SettingToggleRow(
                    icon = Icons.Default.HourglassEmpty,
                    title = "Daily watch reminder",
                    subtitle = "A gentle nudge after long sessions",
                    checked = wellbeingReminder,
                    onCheckedChange = { wellbeingReminder = it; saveBoolean("wellbeing_reminder", it) }
                )
            }

            SectionLabel("Account")
            SettingsCard {
                SettingRow(
                    icon = Icons.Default.Person,
                    title = "Account",
                    subtitle = "@${currentUser?.username ?: "guest"}",
                    expanded = showAccountCard,
                    onClick = { showAccountCard = !showAccountCard }
                )
                AnimatedVisibility(visible = showAccountCard) {
                    LinkedAccountsSection(repository = repository, activity = activity)
                }
                SettingRow(
                    icon = Icons.Default.Security,
                    title = "Security & permissions",
                    subtitle = "Active sessions on your account",
                    expanded = showSecurityCard,
                    onClick = { showSecurityCard = !showSecurityCard }
                )
                AnimatedVisibility(visible = showSecurityCard) {
                    SessionsSection(repository = repository)
                }
                SettingRow(
                    icon = Icons.Default.Lock,
                    title = "Change password",
                    subtitle = "Update your account password",
                    onClick = onChangePassword
                )
                SettingRow(
                    icon = Icons.Default.Share,
                    title = "Share profile",
                    onClick = {
                        val handle = currentUser?.username ?: "chort"
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, "Follow me on thileli dz: @$handle")
                        }
                        context.startActivity(Intent.createChooser(send, "Share profile"))
                    }
                )
                SettingRow(
                    icon = Icons.Default.Logout,
                    title = "Log out",
                    danger = true,
                    onClick = { showLogoutConfirm = true }
                )
            }

            SectionLabel("Visibility")
            SettingsCard {
                SettingToggleRow(
                    icon = Icons.Default.Lock,
                    title = "Private account",
                    subtitle = "Only approved followers see your videos",
                    checked = privateAccount,
                    onCheckedChange = { privateAccount = it; saveBoolean("private_account", it) }
                )
                SettingRow(
                    icon = Icons.Default.Group,
                    title = "Following list",
                    value = followingVisibility,
                    onClick = {
                        followingVisibility = cycleVisibility(followingVisibility)
                        saveString("following_visibility", followingVisibility)
                    }
                )
                SettingRow(
                    icon = Icons.Default.Favorite,
                    title = "Liked videos",
                    value = likedVisibility,
                    onClick = {
                        likedVisibility = cycleVisibility(likedVisibility)
                        saveString("liked_visibility", likedVisibility)
                    }
                )
            }

            SectionLabel("Interactions")
            SettingsCard {
                SettingToggleRow(
                    icon = Icons.Default.Comment,
                    title = "Comments",
                    subtitle = "Allow comments on your videos",
                    checked = commentsAllowed,
                    onCheckedChange = { commentsAllowed = it; saveBoolean("comments_allowed", it) }
                )
                SettingRow(
                    icon = Icons.AutoMirrored.Filled.Message,
                    title = "Direct messages",
                    subtitle = "Activity and mentions land in your inbox",
                    onClick = onNavigateToInbox
                )
                SettingToggleRow(
                    icon = Icons.Default.Download,
                    title = "Downloads",
                    subtitle = "Let others save your videos",
                    checked = downloadsAllowed,
                    onCheckedChange = { downloadsAllowed = it; saveBoolean("downloads_allowed", it) }
                )
                SettingToggleRow(
                    icon = Icons.Default.Visibility,
                    title = "Viewers",
                    subtitle = "See who watched your videos",
                    checked = viewersOn,
                    onCheckedChange = { viewersOn = it; saveBoolean("viewers_on", it) }
                )
            }

            SectionLabel("Content & Display")
            SettingsCard {
                SettingToggleRow(
                    icon = Icons.Default.PlayArrow,
                    title = "Playback",
                    subtitle = "Autoplay next video in feed",
                    checked = autoplay,
                    onCheckedChange = { autoplay = it; saveBoolean("autoplay", it) }
                )
                SettingRow(
                    icon = Icons.Default.DarkMode,
                    title = "Display",
                    value = "Dark",
                    onClick = {}
                )
            }

            SectionLabel("About")
            SettingsCard {
                SettingRow(
                    icon = Icons.Default.Info,
                    title = "About thileli dz",
                    subtitle = "Version 2.4.3",
                    onClick = { onNavigateToLegal("terms") }
                )
                SettingRow(
                    icon = Icons.Default.Policy,
                    title = "Terms & Privacy",
                    onClick = { onNavigateToLegal("privacy") }
                )
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        color = TextSecondary,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 4.dp, top = 18.dp, bottom = 8.dp)
    )
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(TokDarkSurface)
            .border(1.dp, TokBorder, RoundedCornerShape(16.dp))
            .padding(vertical = 4.dp)
    ) {
        content()
    }
}

@Composable
private fun SettingRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    value: String? = null,
    danger: Boolean = false,
    expanded: Boolean = false,
    onClick: () -> Unit
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (danger) StatusBanned else TextSecondary,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = if (danger) StatusBanned else TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium
                )
                if (subtitle != null) {
                    Text(text = subtitle, color = TextMuted, fontSize = 12.sp)
                }
            }
            if (value != null) {
                Text(text = value, color = TextSecondary, fontSize = 13.sp)
                Spacer(modifier = Modifier.width(4.dp))
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = TextMuted,
                modifier = Modifier.size(20.dp)
            )
        }
        if (!expanded) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 52.dp)
                    .height(0.5.dp)
                    .background(TokBorder)
            )
        }
    }
}

@Composable
private fun SettingToggleRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onCheckedChange(!checked) }
                .padding(horizontal = 16.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = TextSecondary,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                if (subtitle != null) {
                    Text(text = subtitle, color = TextMuted, fontSize = 12.sp)
                }
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = TokCyan,
                    uncheckedThumbColor = TextSecondary,
                    uncheckedTrackColor = TokBorder
                )
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 52.dp)
                .height(0.5.dp)
                .background(TokBorder)
        )
    }
}

// ---------------------------------------------------------------------------
// Live account section: linked sign-in methods (real API)
// ---------------------------------------------------------------------------

@Composable
private fun LinkedAccountsSection(
    repository: TokPulseRepository,
    activity: Activity?
) {
    val scope = rememberCoroutineScope()
    var providers by remember { mutableStateOf<List<LinkedProvider>?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var busyLink by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }

    fun reload() {
        isLoading = true
        message = null
        scope.launch {
            val result = repository.getLinkedProviders()
            isLoading = false
            if (result.isSuccess) {
                providers = result.getOrThrow()
            } else {
                message = result.exceptionOrNull()?.message
            }
        }
    }

    LaunchedEffect(Unit) { reload() }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 12.dp)
    ) {
        if (isLoading) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(color = TokCyan, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Loading linked accounts…", color = TextMuted, fontSize = 13.sp)
            }
        } else if (message != null && providers == null) {
            Text(
                text = message ?: "Unavailable",
                color = StatusBanned,
                fontSize = 13.sp,
                modifier = Modifier.clickable { reload() }
            )
        } else {
            (providers ?: emptyList()).forEach { p ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = TokCyan,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = p.provider.replaceFirstChar { it.uppercase() } +
                            (if (!p.email.isNullOrBlank()) " • ${p.email}" else ""),
                        color = TextPrimary,
                        fontSize = 13.5.sp,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            if ((providers ?: emptyList()).isEmpty()) {
                Text("Email sign-in only.", color = TextSecondary, fontSize = 13.sp)
            }
            if (message != null) {
                Text(text = message ?: "", color = StatusBanned, fontSize = 12.sp)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                LinkChip(
                    label = "Link Google",
                    isBusy = busyLink == "google",
                    onClick = {
                        val act = activity ?: run {
                            message = "Needs an Activity context."
                            return@LinkChip
                        }
                        busyLink = "google"
                        message = null
                        scope.launch {
                            val result = repository.linkGoogleToMyAccount(act)
                            busyLink = null
                            if (result.isSuccess) reload()
                            else message = result.exceptionOrNull()?.message
                        }
                    }
                )
                LinkChip(
                    label = "Link Facebook",
                    isBusy = busyLink == "facebook",
                    onClick = {
                        val act = activity ?: run {
                            message = "Needs an Activity context."
                            return@LinkChip
                        }
                        busyLink = "facebook"
                        message = null
                        scope.launch {
                            val result = repository.linkFacebookToMyAccount(act)
                            busyLink = null
                            if (result.isSuccess) reload()
                            else message = result.exceptionOrNull()?.message
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun LinkChip(
    label: String,
    isBusy: Boolean,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .border(1.dp, TokCyan, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        if (isBusy) {
            CircularProgressIndicator(color = TokCyan, strokeWidth = 2.dp, modifier = Modifier.size(14.dp))
            Spacer(modifier = Modifier.width(6.dp))
        } else {
            Icon(imageVector = Icons.Default.Link, contentDescription = null, tint = TokCyan, modifier = Modifier.size(14.dp))
            Spacer(modifier = Modifier.width(6.dp))
        }
        Text(text = label, color = TokCyan, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

// ---------------------------------------------------------------------------
// Live security section: active sessions (real API)
// ---------------------------------------------------------------------------

@Composable
private fun SessionsSection(repository: TokPulseRepository) {
    val scope = rememberCoroutineScope()
    var sessions by remember { mutableStateOf<List<ApiSession>?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var revokingId by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    val dateFormat = remember { SimpleDateFormat("d MMM yyyy", Locale.getDefault()) }

    fun reload() {
        isLoading = true
        message = null
        scope.launch {
            val result = repository.listMySessions()
            isLoading = false
            if (result.isSuccess) {
                sessions = result.getOrThrow()
            } else {
                message = result.exceptionOrNull()?.message
            }
        }
    }

    LaunchedEffect(Unit) { reload() }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 12.dp)
    ) {
        if (isLoading) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(color = TokCyan, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Loading sessions…", color = TextMuted, fontSize = 13.sp)
            }
        } else if (message != null && sessions == null) {
            Text(
                text = (message ?: "Unavailable") + " Tap to retry.",
                color = StatusBanned,
                fontSize = 13.sp,
                modifier = Modifier.clickable { reload() }
            )
        } else {
            (sessions ?: emptyList()).forEach { s ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 7.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Smartphone,
                        contentDescription = null,
                        tint = if (s.current) TokCyan else TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = shortAgent(s.userAgent) + if (s.current) " • This device" else "",
                            color = TextPrimary,
                            fontSize = 13.5.sp,
                            fontWeight = if (s.current) FontWeight.Bold else FontWeight.Normal
                        )
                        Text(
                            text = listOfNotNull(
                                s.ipAddress,
                                if (s.createdAt > 0) "since ${dateFormat.format(Date(s.createdAt))}" else null
                            ).joinToString(" • "),
                            color = TextMuted,
                            fontSize = 12.sp
                        )
                    }
                    if (!s.current) {
                        if (revokingId == s.id) {
                            CircularProgressIndicator(color = StatusBanned, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                        } else {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Revoke session",
                                tint = StatusBanned,
                                modifier = Modifier
                                    .size(22.dp)
                                    .clickable {
                                        revokingId = s.id
                                        scope.launch {
                                            val result = repository.revokeMySession(s.id)
                                            revokingId = null
                                            if (result.isSuccess) reload()
                                            else message = result.exceptionOrNull()?.message
                                        }
                                    }
                            )
                        }
                    }
                }
            }
            if ((sessions ?: emptyList()).isEmpty()) {
                Text("No active sessions found.", color = TextSecondary, fontSize = 13.sp)
            }
            if (message != null) {
                Text(text = message ?: "", color = StatusBanned, fontSize = 12.sp)
            }
        }
    }
}

private fun shortAgent(userAgent: String?): String {
    if (userAgent.isNullOrBlank()) return "Unknown device"
    val ua = userAgent.lowercase()
    return when {
        ua.contains("android") -> "Android app"
        ua.contains("iphone") || ua.contains("ipad") || ua.contains("ios") -> "iOS app"
        ua.contains("windows") -> "Windows browser"
        ua.contains("mac") -> "Mac browser"
        ua.contains("linux") -> "Linux browser"
        else -> userAgent.take(28)
    }
}
