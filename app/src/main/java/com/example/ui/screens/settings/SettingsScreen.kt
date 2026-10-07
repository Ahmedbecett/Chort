package com.example.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoGraph
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Comment
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.HeadsetMic
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.repository.ZevoraRepository
import com.example.util.AppPrefs
import kotlinx.coroutines.launch

private val Bg = Color(0xFFF6F6F7)
private val Card = Color.White
private val Ink = Color(0xFF111111)
private val Muted = Color(0xFF8D8D92)
private val Accent = Color(0xFFFF2D55)

/**
 * Rivo 3.1.0 — Settings and privacy in the reference structure.
 * EVERY row is wired: sub-pages for device/account features, full screens
 * for Balance / Activity / Offline / QR / Studio / Promote. No dead dialogs.
 */
@Composable
fun SettingsScreen(
    repository: ZevoraRepository,
    onBack: () -> Unit,
    onNavigateToProfile: () -> Unit,
    onNavigateToInbox: () -> Unit,
    onNavigateToLegal: (String) -> Unit,
    onLoggedOut: () -> Unit,
    onChangePassword: () -> Unit = {},
    onOpenWallet: () -> Unit = {},
    onOpenActivity: () -> Unit = {},
    onOpenOffline: () -> Unit = {},
    onOpenQr: () -> Unit = {},
    onOpenStudio: () -> Unit = {},
    onOpenPromote: () -> Unit = {},
    startPage: String? = null
) {
    var page by remember(startPage) { mutableStateOf(startPage) }

    when (page) {
        "posts" -> ManagePostsPage(repository, onBack = { page = null })
        "content" -> ContentPrefsPage(onBack = { page = null })
        "notifications" -> NotificationsPage(repository, onBack = { page = null })
        "screen_time" -> ScreenTimePage(onBack = { page = null })
        "family" -> FamilyPage(onBack = { page = null })
        "account" -> AccountPage(repository, onBack = { page = null }, onDeleted = onLoggedOut)
        "security" -> SecurityPage(repository, onBack = { page = null }, onChangePassword = onChangePassword)
        "analytics" -> AnalyticsPage(repository, onBack = { page = null }, onOpenStudio = onOpenStudio)
        "share" -> ShareProfilePage(repository, onBack = { page = null }, onOpenQr = onOpenQr)
        "blocked" -> BlockedPage(repository, onBack = { page = null })
        "comments" -> CommentsChoicePage(onBack = { page = null })
        "mentions" -> MentionsChoicePage(onBack = { page = null })
        "dms" -> DmChoicePage(onBack = { page = null })
        "reuse" -> ReusePage(onBack = { page = null })
        "downloads" -> DownloadsChoicePage(onBack = { page = null })
        "following_visibility" -> FollowingVisibilityPage(onBack = { page = null })
        "liked_visibility" -> LikedVisibilityPage(onBack = { page = null })
        "music" -> MusicPage(onBack = { page = null })
        "audience" -> AudiencePage(repository, onBack = { page = null })
        "ads" -> AdsPage(onBack = { page = null })
        "playback" -> PlaybackPage(onBack = { page = null })
        "display" -> DisplayPage(onBack = { page = null })
        "language" -> LanguagePage(onBack = { page = null })
        "captions" -> CaptionSizePage(onBack = { page = null })
        "contacts" -> ContactsPage(repository, onBack = { page = null })
        "storage" -> StoragePage(onBack = { page = null }, onOpenOffline = onOpenOffline)
        "datasaver" -> DataSaverInfoPage(onBack = { page = null })
        "lite" -> LiteModePage(onBack = { page = null })
        "help" -> HelpPage(onBack = { page = null })
        "about" -> AboutPage(repository, onBack = { page = null })
        "switch" -> SwitchAccountPage(repository, onBack = { page = null }, onLoggedOut = onLoggedOut)
        else -> SettingsMainList(
            repository = repository,
            onBack = onBack,
            onOpenPage = { page = it },
            onNavigateToProfile = onNavigateToProfile,
            onNavigateToInbox = onNavigateToInbox,
            onNavigateToLegal = onNavigateToLegal,
            onLoggedOut = onLoggedOut,
            onChangePassword = onChangePassword,
            onOpenWallet = onOpenWallet,
            onOpenActivity = onOpenActivity,
            onOpenOffline = onOpenOffline,
            onOpenQr = onOpenQr,
            onOpenStudio = onOpenStudio,
            onOpenPromote = onOpenPromote
        )
    }
}

@Composable
private fun SettingsMainList(
    repository: ZevoraRepository,
    onBack: () -> Unit,
    onOpenPage: (String) -> Unit,
    onNavigateToProfile: () -> Unit,
    onNavigateToInbox: () -> Unit,
    onNavigateToLegal: (String) -> Unit,
    onLoggedOut: () -> Unit,
    onChangePassword: () -> Unit,
    onOpenWallet: () -> Unit,
    onOpenActivity: () -> Unit,
    onOpenOffline: () -> Unit,
    onOpenQr: () -> Unit,
    onOpenStudio: () -> Unit,
    onOpenPromote: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var logout by remember { mutableStateOf(false) }
    val privateAccount by AppPrefs.privateAccount.collectAsState()
    val coins by AppPrefs.coins.collectAsState()
    val downloadsOn by AppPrefs.allowDownloads.collectAsState()
    val commentsMode by AppPrefs.allowComments.collectAsState()
    val autoplay by AppPrefs.autoplay.collectAsState()
    val dataSaver by AppPrefs.dataSaver.collectAsState()
    val liteMode = remember { AppPrefs.isLiteMode() }
    val language by AppPrefs.language.collectAsState()

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
                Item(Icons.Default.AccountBalanceWallet, "Balance", "$coins coins") { onOpenWallet() }
                Item(Icons.Default.History, "Activity center") { onOpenActivity() }
                Item(Icons.Default.CloudDownload, "Offline videos") { onOpenOffline() }
                Item(Icons.Default.QrCode2, "Your QR code") { onOpenQr() }
                Item(Icons.Default.AutoGraph, "Rivo Studio") { onOpenStudio() }
                Item(Icons.Default.LocalFireDepartment, "Promote") { onOpenPromote() }
                Item(Icons.Default.PlayCircle, "Manage posts") { onOpenPage("posts") }
                Item(Icons.Default.Tune, "Content preferences") { onOpenPage("content") }
                Item(
                    Icons.Default.Notifications,
                    "Notifications",
                    if (AppPrefs.isPushEnabled()) "On" else "Off"
                ) { onOpenPage("notifications") }
                Item(Icons.Default.HourglassEmpty, "Time and well-being") { onOpenPage("screen_time") }
                Item(
                    Icons.Default.Group,
                    "Family Pairing",
                    if (AppPrefs.isFamilyLocked()) "On" else "Off"
                ) { onOpenPage("family") }
            }

            Section("Account") {
                Item(Icons.Default.Person, "Account") { onOpenPage("account") }
                Item(Icons.Default.Security, "Security & permissions") { onOpenPage("security") }
                Item(Icons.Default.Analytics, "Analytics") { onOpenPage("analytics") }
                Item(Icons.Default.Share, "Share profile") { onOpenPage("share") }
                Item(Icons.Default.Lock, "Change password") { onChangePassword() }
            }

            Section("Visibility") {
                Item(
                    Icons.Default.Lock,
                    "Private account",
                    if (privateAccount) "On" else "Off"
                ) {
                    syncPrivateAccount(repository, scope, context, !privateAccount) { }
                }
                Item(
                    Icons.Default.Block,
                    "Blocked accounts",
                    "${AppPrefs.getBlockedIds().size}"
                ) { onOpenPage("blocked") }
            }

            Section("Interactions") {
                Item(Icons.Default.Comment, "Comments", commentsMode.replaceFirstChar { it.uppercase() }) {
                    onOpenPage("comments")
                }
                Item(Icons.Default.AlternateEmail, "Mentions", AppPrefs.getMentionMode().replaceFirstChar { it.uppercase() }) {
                    onOpenPage("mentions")
                }
                Item(Icons.Default.Send, "Direct messages", AppPrefs.getDmMode()) { onOpenPage("dms") }
                Item(
                    Icons.Default.VideoLibrary,
                    "Reuse of content",
                    if (AppPrefs.allowReuse.value) "Allowed" else "Off"
                ) { onOpenPage("reuse") }
                Item(
                    Icons.Default.Link,
                    "Display profile when sharing links",
                    if (AppPrefs.showProfileOnShare()) "On" else "Off"
                ) {
                    AppPrefs.setShowProfileOnShare(!AppPrefs.showProfileOnShare())
                }
                Item(Icons.Default.Download, "Downloads", if (downloadsOn) "On" else "Off") {
                    onOpenPage("downloads")
                }
                Item(Icons.Default.Group, "Following list", AppPrefs.getFollowingVisibility()) {
                    onOpenPage("following_visibility")
                }
                Item(Icons.Default.Favorite, "Liked videos", AppPrefs.getLikedVisibility()) {
                    onOpenPage("liked_visibility")
                }
            }

            Section("Content & Display") {
                Item(Icons.Default.MusicNote, "Music") { onOpenPage("music") }
                Item(Icons.Default.Inbox, "Inbox & Messaging") { onNavigateToInbox() }
                Item(Icons.Default.History, "Activity center") { onOpenActivity() }
                Item(Icons.Default.Group, "Audience control") { onOpenPage("audience") }
                Item(Icons.Default.Campaign, "Ads") { onOpenPage("ads") }
                Item(Icons.Default.PlayArrow, "Playback", if (autoplay) "On" else "Off") {
                    onOpenPage("playback")
                }
                Item(
                    Icons.Default.DarkMode,
                    "Display",
                    if (AppPrefs.themeMode.value == "black") "Pure black" else "Dark"
                ) { onOpenPage("display") }
                Item(Icons.Default.Language, "Language", languageLabel(language)) {
                    onOpenPage("language")
                }
                Item(Icons.Default.Accessibility, "Accessibility", "Captions ${AppPrefs.getCaptionSize()}") {
                    onOpenPage("captions")
                }
                Item(Icons.Default.LocationOn, "Contacts and location") { onOpenPage("contacts") }
            }

            Section("Cache & Cellular") {
                Item(Icons.Default.CloudDownload, "Offline videos") { onOpenOffline() }
                Item(Icons.Default.Delete, "Free up space") { onOpenPage("storage") }
                Item(Icons.Default.Wifi, "Data Saver", if (dataSaver) "On" else "Off") {
                    AppPrefs.setDataSaver(!dataSaver)
                }
                Item(Icons.Default.Tune, "Lite mode", if (liteMode) "On" else "Off") {
                    onOpenPage("lite")
                }
            }

            Section("Support & About") {
                Item(Icons.Default.HeadsetMic, "Help Center") { onOpenPage("help") }
                Item(Icons.Default.Lock, "Privacy Center") { onNavigateToLegal("privacy") }
                Item(Icons.Default.Info, "Terms and Policies") { onNavigateToLegal("terms") }
                Item(Icons.Default.Person, "About Rivo", "Ahmed Becetti") { onOpenPage("about") }
            }

            Section("Login") {
                Item(Icons.Default.SwapHoriz, "Switch account") { onOpenPage("switch") }
                Item(Icons.Default.Logout, "Log out", danger = true) { logout = true }
            }

            Spacer(Modifier.height(70.dp))
        }
    }

    if (logout) {
        AlertDialog(
            onDismissRequest = { logout = false },
            title = { Text("Log out?") },
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

private fun languageLabel(tag: String): String = when (tag) {
    "ar" -> "العربية"
    "en" -> "English"
    "fr" -> "Français"
    else -> "System"
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
