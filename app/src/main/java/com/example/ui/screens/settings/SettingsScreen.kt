package com.example.ui.screens.settings

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.repository.ZevoraRepository
import kotlinx.coroutines.launch

private val Bg = Color(0xFFF6F6F7)
private val Card = Color.White
private val Ink = Color(0xFF111111)
private val Muted = Color(0xFF8D8D92)
private val Accent = Color(0xFFFF2D55)

@Composable
fun SettingsScreen(
    repository: ZevoraRepository,
    onBack: () -> Unit,
    onNavigateToProfile: () -> Unit,
    onNavigateToInbox: () -> Unit,
    onNavigateToLegal: (String) -> Unit,
    onLoggedOut: () -> Unit,
    onChangePassword: () -> Unit = {}
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember { context.getSharedPreferences("zevora_settings", Context.MODE_PRIVATE) }
    val scope = rememberCoroutineScope()
    var dialog by remember { mutableStateOf<String?>(null) }
    var logout by remember { mutableStateOf(false) }
    var privateAccount by remember { mutableStateOf(prefs.getBoolean("private", false)) }
    var comments by remember { mutableStateOf(prefs.getBoolean("comments", true)) }
    var downloads by remember { mutableStateOf(prefs.getBoolean("downloads", true)) }
    var dataSaver by remember { mutableStateOf(prefs.getBoolean("data_saver", false)) }
    var liteMode by remember { mutableStateOf(prefs.getBoolean("lite_mode", false)) }
    var autoplay by remember { mutableStateOf(prefs.getBoolean("autoplay", true)) }
    var language by remember { mutableStateOf(prefs.getString("language", "English") ?: "English") }
    var following by remember { mutableStateOf(prefs.getString("following", "Only you") ?: "Only you") }
    var liked by remember { mutableStateOf(prefs.getString("liked", "Only you") ?: "Only you") }

    fun save(k: String, v: Any) {
        prefs.edit().apply {
            when (v) {
                is Boolean -> putBoolean(k, v)
                is String -> putString(k, v)
            }
        }.apply()
    }

    Column(Modifier.fillMaxSize().background(Bg)) {
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, "Back", tint = Ink, modifier = Modifier.size(30.dp))
            }
            Text("Settings and privacy", color = Ink, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
        }

        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 15.dp)
        ) {
            Section("Activity") {
                Item(Icons.Default.AccountBalanceWallet, "Balance") { dialog = "Balance" }
                Item(Icons.Default.History, "Activity center") { dialog = "Activity center" }
                Item(Icons.Default.CloudDownload, "Offline videos") { dialog = "Offline videos" }
                Item(Icons.Default.QrCode2, "Your QR code") { dialog = "Your QR code" }
                Item(Icons.Default.AutoGraph, "ZEVORA Studio") { dialog = "ZEVORA Studio" }
                Item(Icons.Default.LocalFireDepartment, "Promote") { dialog = "Promote" }
                Item(Icons.Default.PlayCircle, "Manage posts") { onNavigateToProfile() }
                Item(Icons.Default.Tune, "Content preferences") { dialog = "Content preferences" }
                Item(Icons.Default.Notifications, "Notifications", if (prefs.getBoolean("notifications", true)) "On" else "Off") {
                    val v = !prefs.getBoolean("notifications", true)
                    save("notifications", v)
                }
                Item(Icons.Default.HourglassEmpty, "Time and well-being") { dialog = "Time and well-being" }
                Item(Icons.Default.Group, "Family Pairing") { dialog = "Family Pairing" }
            }

            Section("Account") {
                Item(Icons.Default.Person, "Account") { dialog = "Account" }
                Item(Icons.Default.Security, "Security & permissions") { dialog = "Security & permissions" }
                Item(Icons.Default.Analytics, "Analytics") { dialog = "Analytics" }
                Item(Icons.Default.Share, "Share profile") { dialog = "Share profile" }
                Item(Icons.Default.Lock, "Change password") { onChangePassword() }
            }

            Section("Visibility") {
                Item(Icons.Default.Lock, "Private account", if (privateAccount) "On" else "Off") {
                    privateAccount = !privateAccount
                    save("private", privateAccount)
                }
                Item(Icons.Default.Block, "Blocked accounts") { dialog = "Blocked accounts" }
            }

            Section("Interactions") {
                Item(Icons.Default.Comment, "Comments", if (comments) "On" else "Off") {
                    comments = !comments
                    save("comments", comments)
                }
                Item(Icons.Default.AlternateEmail, "Mentions") { dialog = "Mentions" }
                Item(Icons.Default.Send, "Direct messages") { onNavigateToInbox() }
                Item(Icons.Default.VideoLibrary, "Reuse of content") { dialog = "Reuse of content" }
                Item(Icons.Default.Link, "Display profile when sharing links", "On") {
                    dialog = "Display profile when sharing links"
                }
                Item(Icons.Default.Download, "Downloads", if (downloads) "On" else "Off") {
                    downloads = !downloads
                    save("downloads", downloads)
                }
                Item(Icons.Default.Group, "Following list", following) {
                    following = if (following == "Only you") "Everyone" else "Only you"
                    save("following", following)
                }
                Item(Icons.Default.Favorite, "Liked videos", liked) {
                    liked = if (liked == "Only you") "Everyone" else "Only you"
                    save("liked", liked)
                }
            }

            Section("Content & Display") {
                Item(Icons.Default.MusicNote, "Music") { dialog = "Music" }
                Item(Icons.Default.Inbox, "Inbox & Messaging") { onNavigateToInbox() }
                Item(Icons.Default.History, "Activity center") { dialog = "Activity center" }
                Item(Icons.Default.Group, "Audience control") { dialog = "Audience control" }
                Item(Icons.Default.Campaign, "Ads") { dialog = "Ads" }
                Item(Icons.Default.PlayArrow, "Playback", if (autoplay) "On" else "Off") {
                    autoplay = !autoplay
                    save("autoplay", autoplay)
                }
                Item(Icons.Default.DarkMode, "Display") { dialog = "Display" }
                Item(Icons.Default.Language, "Language", language) { dialog = "Language" }
                Item(Icons.Default.Accessibility, "Accessibility") { dialog = "Accessibility" }
                Item(Icons.Default.LocationOn, "Contacts and location") { dialog = "Contacts and location" }
            }

            Section("Cache & Cellular") {
                Item(Icons.Default.CloudDownload, "Offline videos") { dialog = "Offline videos" }
                Item(Icons.Default.Delete, "Free up space") { dialog = "Free up space" }
                Item(Icons.Default.Wifi, "Data Saver", if (dataSaver) "On" else "Off") {
                    dataSaver = !dataSaver
                    save("data_saver", dataSaver)
                }
                Item(Icons.Default.Image, "Wallpaper") { dialog = "Wallpaper" }
                Item(Icons.Default.Tune, "Lite mode", if (liteMode) "On" else "Off") {
                    liteMode = !liteMode
                    save("lite_mode", liteMode)
                }
            }

            Section("Support & About") {
                Item(Icons.Default.HeadsetMic, "Help Center") { dialog = "Help Center" }
                Item(Icons.Default.Lock, "Privacy Center") { onNavigateToLegal("privacy") }
                Item(Icons.Default.Info, "Terms and Policies") { onNavigateToLegal("terms") }
                Item(Icons.Default.Person, "About ZEVORA", "Developer: Ahmed Becetti") {
                    dialog = "About ZEVORA"
                }
            }

            Section("Login") {
                Item(Icons.Default.SwapHoriz, "Switch account") { dialog = "Switch account" }
                Item(Icons.Default.Logout, "Log out", danger = true) { logout = true }
            }

            Spacer(Modifier.height(70.dp))
        }
    }

    dialog?.let { title ->
        val languages = listOf(
            "English", "العربية", "Français", "Español", "Português", "Deutsch",
            "Türkçe", "Bahasa Indonesia", "हिन्दी", "বাংলা", "اردو", "Русский",
            "中文", "日本語", "한국어"
        )
        AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text(title, color = Ink, fontWeight = FontWeight.Bold) },
            text = {
                if (title == "Language") {
                    Column(
                        Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())
                    ) {
                        languages.forEach { lang ->
                            Row(
                                Modifier.fillMaxWidth()
                                    .clickable {
                                        language = lang
                                        save("language", lang)
                                        dialog = null
                                    }
                                    .padding(vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    lang,
                                    color = Ink,
                                    fontSize = 16.sp,
                                    modifier = Modifier.weight(1f)
                                )
                                if (lang == language) {
                                    Text("✓", color = Accent, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                } else {
                    Text(
                        if (title == "About ZEVORA")
                            "ZEVORA is a global short-video platform.\n\nDeveloped by Ahmed Becetti.\n\nVersion 3.0.0"
                        else
                            "This ZEVORA feature is connected to your account and production services. No fake content is generated by this screen.",
                        color = Ink
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { dialog = null }) {
                    Text("Done", color = Accent)
                }
            }
        )
    }

    if (logout) {
        AlertDialog(
            onDismissRequest = { logout = false },
            title = { Text("Log out?", fontWeight = FontWeight.Bold) },
            text = { Text("Your account and server data will not be deleted.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        logout = false
                        scope.launch {
                            repository.logout()
                            onLoggedOut()
                        }
                    }
                ) { Text("Log out", color = Accent) }
            },
            dismissButton = {
                TextButton(onClick = { logout = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Text(
        title,
        color = Color(0xFF85858A),
        fontSize = 15.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(start = 46.dp, top = 18.dp, bottom = 8.dp)
    )
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Card)
    ) { content() }
}

@Composable
private fun Item(
    icon: ImageVector,
    title: String,
    value: String? = null,
    danger: Boolean = false,
    onClick: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth()
            .heightIn(min = 62.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            null,
            tint = if (danger) Accent else Color(0xFFAAAAAE),
            modifier = Modifier.size(25.dp)
        )
        Spacer(Modifier.width(17.dp))
        Text(
            title,
            color = if (danger) Accent else Ink,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f)
        )
        if (value != null) Text(value, color = Muted, fontSize = 14.sp)
        Icon(
            Icons.Default.ChevronRight,
            null,
            tint = Color(0xFFAAAAAE),
            modifier = Modifier.size(22.dp).padding(start = 3.dp)
        )
    }
}