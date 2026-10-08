package com.example

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.example.util.NetworkConnectivityMonitor
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entities.VideoEntity
import com.example.data.remote.FacebookAuth
import com.example.ui.components.ZevoraMark
import com.example.ui.screens.activity.ActivityCenterScreen
import com.example.ui.screens.admin.AdminDashboardScreen
import com.example.ui.screens.create.CameraCaptureScreen
import com.example.ui.screens.create.MediaPickerScreen
import com.example.ui.screens.auth.AuthScreen
import com.example.ui.screens.auth.OtpMode
import com.example.ui.screens.auth.OtpScreen
import com.example.ui.screens.auth.PhoneAuthScreen
import com.example.ui.screens.auth.RecoveryScreen
import com.example.ui.screens.auth.SplashScreen
import com.example.ui.screens.auth.WelcomeAuthScreen
import com.example.ui.screens.discover.DiscoverScreen
import com.example.ui.screens.feed.FeedScreen
import com.example.ui.screens.friends.FriendsScreen
import com.example.ui.screens.inbox.InboxScreen
import com.example.ui.screens.legal.LegalScreen
import com.example.ui.screens.live.LiveStreamScreen
import com.example.ui.screens.profile.ProfileScreen
import com.example.ui.screens.settings.ChangePasswordScreen
import com.example.ui.screens.settings.SettingsScreen
import com.example.ui.screens.sound.SoundDetailScreen
import com.example.ui.screens.tracking.ExternalTrackingCenterScreen
import com.example.ui.screens.offline.OfflineVideosScreen
import com.example.ui.screens.promote.PromoteScreen
import com.example.ui.screens.qr.ProfileQrScreen
import com.example.ui.screens.studio.CreatorStudioScreen
import com.example.ui.screens.upload.UploadScreen
import com.example.ui.screens.viewer.VideoViewerScreen
import com.example.ui.screens.wallet.WalletScreen
import com.example.util.AppPrefs
import com.example.ui.theme.ZevoraTheme
import com.example.ui.theme.ZevoraRed

class MainActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppPrefs.wrapLocale(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        com.example.util.CrashHandler.install(this)
        enableEdgeToEdge()
        setContent {
            ZevoraTheme {
                ZevoraApp()
            }
        }
    }

    @Deprecated("Forwarded to the Facebook SDK for Login results.")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        try {
            FacebookAuth.callbackManager.onActivityResult(requestCode, resultCode, data)
        } catch (_: Exception) {
        }
    }
}

@Composable
fun ZevoraApp() {
    val context = LocalContext.current
    val repository = ZevoraApplication.instance.repository
    val currentUser by repository.currentUser.collectAsState()
    val unreadNotifications by repository.getUnreadCount(currentUser?.id ?: "user_me").collectAsState(initial = 0)

    val connectivityMonitor = remember { NetworkConnectivityMonitor(context) }
    val isOnline by connectivityMonitor.isOnline.collectAsState(initial = true)

    // Pending crash report from a previous run: surface it in-app (works even
    // on devices that block the isolated `:crash` reporter process).
    var pendingCrash by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        pendingCrash = com.example.util.CrashHandler.readLastCrash(context)
    }
    pendingCrash?.let { report ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { },
            title = { Text("Rivo stopped last time") },
            text = {
                Text(
                    text = report.take(4000),
                    modifier = Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState()),
                    fontSize = 11.sp
                )
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    try {
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        cm.setPrimaryClip(android.content.ClipData.newPlainText("Rivo crash", report))
                        android.widget.Toast.makeText(context, "Copied", android.widget.Toast.LENGTH_SHORT).show()
                    } catch (_: Exception) {
                    }
                }) { Text("Copy") }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = {
                    com.example.util.CrashHandler.clearLastCrash(context)
                    pendingCrash = null
                }) { Text("Dismiss") }
            }
        )
    }

    // Screens: "splash" | auth: "welcome","email","phone","otp","recover" |
    // main: "feed","friends","inbox","profile" | create: "create","camera","upload" |
    // sub: "discover","viewer","settings","admin","legal","live","sound","tracking",
    // "wallet","activity","offline","qr","studio","promote","change_password"
    var currentScreen by remember { mutableStateOf("splash") }
    var viewingProfileUserId by remember { mutableStateOf<String?>(null) }
    var selectedSoundTitle by remember { mutableStateOf("Original Sound") }
    var legalType by remember { mutableStateOf("terms") } // "terms" or "privacy"

    // Create-flow handoff: picker/camera/remix -> publish queue (+ optional sound).
    var pendingUploadUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var pendingAudioUri by remember { mutableStateOf<Uri?>(null) }
    var pendingAudioTitle by remember { mutableStateOf("") }
    var viewingVideo by remember { mutableStateOf<VideoEntity?>(null) }
    var viewerReturn by remember { mutableStateOf("feed") }
    var drawerReturn by remember { mutableStateOf("profile") }
    var settingsStartPage by remember { mutableStateOf<String?>(null) }

    // Screen-time tracking: flushed every minute plus the tail on exit.
    val fgSessionStart = remember { System.currentTimeMillis() }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(60_000)
            AppPrefs.addForegroundTime(60_000)
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            AppPrefs.addForegroundTime((System.currentTimeMillis() - fgSessionStart) % 60_000)
        }
    }

    fun resolveAudioTitle(uri: Uri): String {
        return try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0 && cursor.moveToFirst()) {
                    cursor.getString(nameIndex)?.substringBeforeLast(".") ?: ""
                } else ""
            } ?: ""
        } catch (_: Exception) {
            ""
        }
    }

    fun openUpload(uris: List<Uri>, audio: Uri?) {
        pendingUploadUris = uris.take(10)
        pendingAudioUri = audio
        pendingAudioTitle = audio?.let { resolveAudioTitle(it) } ?: ""
        currentScreen = "upload"
    }

    fun openDrawerScreen(screen: String, from: String) {
        drawerReturn = from
        currentScreen = screen
    }

    // OTP handoff state (phone flow)
    var otpPhone by remember { mutableStateOf("") }
    var otpCooldown by remember { mutableIntStateOf(60) }
    var otpExpiresIn by remember { mutableIntStateOf(600) }

    fun goHome() {
        viewingProfileUserId = null
        currentScreen = "feed"
    }

    fun goWelcome() {
        viewingProfileUserId = null
        currentScreen = "welcome"
    }

    // Back handling for sub screens (welcome/splash use the system back = exit)
    BackHandler(enabled = currentScreen != "feed" && currentScreen != "splash" && currentScreen != "welcome") {
        if (currentScreen == "profile" && viewingProfileUserId != null) {
            viewingProfileUserId = null
        } else if (currentScreen in listOf("live", "sound", "tracking", "discover", "create")) {
            currentScreen = "feed"
        } else if (currentScreen == "camera" || currentScreen == "upload") {
            currentScreen = "create"
        } else if (currentScreen == "viewer") {
            currentScreen = viewerReturn
        } else if (currentScreen == "change_password") {
            currentScreen = "settings"
        } else if (currentScreen == "settings" || currentScreen == "admin") {
            viewingProfileUserId = null
            currentScreen = "profile"
        } else if (currentScreen in listOf("wallet", "activity", "offline", "qr", "studio", "promote")) {
            currentScreen = drawerReturn
        } else if (currentScreen == "legal") {
            currentScreen = "profile"
        } else if (currentScreen in listOf("email", "phone", "otp", "recover")) {
            currentScreen = "welcome"
        } else {
            currentScreen = "feed"
        }
    }

    val showBottomNav = currentScreen in listOf("feed", "friends", "inbox", "profile") && (currentScreen != "profile" || viewingProfileUserId == null)
    val navBarBottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Color.Black,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            AnimatedVisibility(
                visible = !isOnline,
                enter = slideInVertically(initialOffsetY = { -it }),
                exit = slideOutVertically(targetOffsetY = { -it })
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(ZevoraRed.copy(alpha = 0.95f))
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudOff,
                        contentDescription = "Offline Mode",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "No Internet Connection • Running in Offline Mode",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            // Main content area - feed goes complete full-screen, others respect bottom nav height
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        bottom = if (showBottomNav && currentScreen != "feed") (58.dp + navBarBottomInset) else 0.dp
                    )
            ) {
                when (currentScreen) {
                    "splash" -> {
                        SplashScreen(
                            onFinished = {
                                if (currentUser != null) goHome() else goWelcome()
                            }
                        )
                    }

                    "welcome" -> {
                        WelcomeAuthScreen(
                            repository = repository,
                            onAuthSuccess = { goHome() },
                            onUseEmail = { currentScreen = "email" },
                            onUsePhone = { currentScreen = "phone" },
                            onRecoverAccount = { currentScreen = "recover" }
                        )
                    }

                    "email" -> {
                        AuthScreen(
                            repository = repository,
                            onAuthSuccess = { goHome() },
                            onBackToOptions = { goWelcome() }
                        )
                    }

                    "phone" -> {
                        PhoneAuthScreen(
                            repository = repository,
                            onCodeSent = { phone, cooldown, expires ->
                                otpPhone = phone
                                otpCooldown = cooldown
                                otpExpiresIn = expires
                                currentScreen = "otp"
                            },
                            onBack = { goWelcome() }
                        )
                    }

                    "otp" -> {
                        OtpScreen(
                            repository = repository,
                            mode = OtpMode.REGISTER,
                            phone = otpPhone,
                            cooldownSeconds = otpCooldown,
                            expiresInSeconds = otpExpiresIn,
                            onSuccess = { goHome() },
                            onBack = { currentScreen = "phone" }
                        )
                    }

                    "recover" -> {
                        RecoveryScreen(
                            repository = repository,
                            onRecovered = { goHome() },
                            onBack = { goWelcome() }
                        )
                    }

                    "feed" -> {
                        FeedScreen(
                            repository = repository,
                            onNavigateToSearch = { currentScreen = "discover" },
                            onNavigateToProfile = { creatorId ->
                                viewingProfileUserId = creatorId
                                currentScreen = "profile"
                            },
                            onNavigateToCreate = { currentScreen = "create" },
                            onNavigateToLive = { currentScreen = "live" },
                            onNavigateToSound = { title ->
                                selectedSoundTitle = title
                                currentScreen = "sound"
                            },
                            onNavigateToTracking = { currentScreen = "tracking" },
                            onRemixVideo = { uri -> openUpload(listOf(uri), null) }
                        )
                    }

                    "friends" -> {
                        FriendsScreen(
                            repository = repository,
                            onNavigateToSearch = { currentScreen = "discover" },
                            onNavigateToProfile = { creatorId ->
                                viewingProfileUserId = creatorId
                                currentScreen = "profile"
                            },
                            onNavigateToCreate = { currentScreen = "create" },
                            onNavigateToSound = { title ->
                                selectedSoundTitle = title
                                currentScreen = "sound"
                            },
                            onRemixVideo = { uri -> openUpload(listOf(uri), null) }
                        )
                    }

                    "discover" -> {
                        DiscoverScreen(
                            repository = repository,
                            onNavigateToProfile = { creatorId ->
                                viewingProfileUserId = creatorId
                                currentScreen = "profile"
                            },
                            onSelectVideo = { video ->
                                viewingVideo = video
                                viewerReturn = "discover"
                                currentScreen = "viewer"
                            },
                            onNavigateToSound = { title ->
                                selectedSoundTitle = title
                                currentScreen = "sound"
                            },
                            onBack = { currentScreen = "feed" }
                        )
                    }

                    "create" -> {
                        if (currentUser == null) {
                            LaunchedEffect(Unit) { currentScreen = "welcome" }
                        } else {
                            MediaPickerScreen(
                                repository = repository,
                                onMediaConfirmed = { uris, audio -> openUpload(uris, audio) },
                                onOpenCamera = { currentScreen = "camera" },
                                onGoLive = { currentScreen = "live" },
                                onClose = { currentScreen = "feed" }
                            )
                        }
                    }

                    "camera" -> {
                        if (currentUser == null) {
                            LaunchedEffect(Unit) { currentScreen = "welcome" }
                        } else {
                            CameraCaptureScreen(
                                repository = repository,
                                onVideoConfirmed = { uri, audio -> openUpload(listOf(uri), audio) },
                                onOpenPicker = { currentScreen = "create" },
                                onGoLive = { currentScreen = "live" },
                                onClose = { currentScreen = "create" }
                            )
                        }
                    }

                    "upload" -> {
                        if (currentUser == null) {
                            LaunchedEffect(Unit) { currentScreen = "welcome" }
                        } else {
                            UploadScreen(
                                repository = repository,
                                onUploadSuccess = {
                                    pendingUploadUris = emptyList()
                                    pendingAudioUri = null
                                    pendingAudioTitle = ""
                                    currentScreen = "feed"
                                },
                                initialUris = pendingUploadUris,
                                initialAudioUri = pendingAudioUri,
                                initialAudioTitle = pendingAudioTitle,
                                onBack = { currentScreen = "create" }
                            )
                        }
                    }

                    "viewer" -> {
                        val video = viewingVideo
                        if (video == null) {
                            LaunchedEffect(Unit) { currentScreen = viewerReturn }
                        } else {
                            VideoViewerScreen(
                                repository = repository,
                                video = video,
                                onViewProfile = { creatorId ->
                                    viewingProfileUserId = creatorId
                                    currentScreen = "profile"
                                },
                                onRemixVideo = { uri -> openUpload(listOf(uri), null) },
                                onClose = { currentScreen = viewerReturn }
                            )
                        }
                    }

                    "wallet" -> {
                        WalletScreen(
                            repository = repository,
                            onOpenPromote = { openDrawerScreen("promote", drawerReturn) },
                            onBack = { currentScreen = drawerReturn }
                        )
                    }

                    "activity" -> {
                        ActivityCenterScreen(
                            repository = repository,
                            onSelectVideo = { video ->
                                viewingVideo = video
                                viewerReturn = "activity"
                                currentScreen = "viewer"
                            },
                            onBack = { currentScreen = drawerReturn }
                        )
                    }

                    "offline" -> {
                        OfflineVideosScreen(
                            onBack = { currentScreen = drawerReturn }
                        )
                    }

                    "qr" -> {
                        if (currentUser == null) {
                            LaunchedEffect(Unit) { currentScreen = "welcome" }
                        } else {
                            ProfileQrScreen(
                                repository = repository,
                                onBack = { currentScreen = drawerReturn }
                            )
                        }
                    }

                    "studio" -> {
                        CreatorStudioScreen(
                            repository = repository,
                            onBack = { currentScreen = drawerReturn }
                        )
                    }

                    "promote" -> {
                        if (currentUser == null) {
                            LaunchedEffect(Unit) { currentScreen = "welcome" }
                        } else {
                            PromoteScreen(
                                repository = repository,
                                onBack = { currentScreen = drawerReturn }
                            )
                        }
                    }

                    "inbox" -> {
                        InboxScreen(
                            repository = repository,
                            onNavigateToProfile = { actorId ->
                                viewingProfileUserId = actorId
                                currentScreen = "profile"
                            },
                            onNavigateToSearch = { currentScreen = "discover" },
                            onNavigateToCreate = { currentScreen = "create" }
                        )
                    }

                    "live" -> {
                        LiveStreamScreen(
                            repository = repository,
                            onClose = { currentScreen = "feed" }
                        )
                    }

                    "sound" -> {
                        SoundDetailScreen(
                            soundTitle = selectedSoundTitle,
                            repository = repository,
                            onBack = { currentScreen = "feed" },
                            onUseSound = { currentScreen = "create" },
                            onSelectVideo = { _ -> currentScreen = "feed" }
                        )
                    }

                    "tracking" -> {
                        ExternalTrackingCenterScreen(
                            repository = repository,
                            onBack = { currentScreen = "feed" }
                        )
                    }

                    "profile" -> {
                        ProfileScreen(
                            repository = repository,
                            userIdToView = viewingProfileUserId,
                            onNavigateToAdmin = { currentScreen = "admin" },
                            onNavigateToLegal = { type ->
                                legalType = type
                                currentScreen = "legal"
                            },
                            onSelectVideo = { video ->
                                viewingVideo = video
                                viewerReturn = "profile"
                                currentScreen = "viewer"
                            },
                            onRequireLogin = {
                                goWelcome()
                            },
                            onNavigateToSettings = {
                                settingsStartPage = null
                                currentScreen = "settings"
                            },
                            onViewProfile = { userId ->
                                viewingProfileUserId = userId.ifBlank { null }
                            },
                            onOpenWallet = { openDrawerScreen("wallet", "profile") },
                            onOpenActivity = { openDrawerScreen("activity", "profile") },
                            onOpenOffline = { openDrawerScreen("offline", "profile") },
                            onOpenQr = { openDrawerScreen("qr", "profile") },
                            onOpenStudio = { openDrawerScreen("studio", "profile") },
                            onOpenPromote = { openDrawerScreen("promote", "profile") },
                            onEditProfile = {
                                settingsStartPage = "account"
                                currentScreen = "settings"
                            },
                            onOpenContacts = {
                                settingsStartPage = "contacts"
                                currentScreen = "settings"
                            },
                            onOpenInbox = { currentScreen = "inbox" },
                            onOpenCreate = { currentScreen = "create" }
                        )
                    }

                    "settings" -> {
                        SettingsScreen(
                            repository = repository,
                            onBack = {
                                viewingProfileUserId = null
                                settingsStartPage = null
                                currentScreen = "profile"
                            },
                            onNavigateToProfile = {
                                viewingProfileUserId = null
                                currentScreen = "profile"
                            },
                            onNavigateToInbox = { currentScreen = "inbox" },
                            onNavigateToLegal = { type ->
                                legalType = type
                                currentScreen = "legal"
                            },
                            onLoggedOut = { goWelcome() },
                            onChangePassword = { currentScreen = "change_password" },
                            onOpenWallet = { openDrawerScreen("wallet", "settings") },
                            onOpenActivity = { openDrawerScreen("activity", "settings") },
                            onOpenOffline = { openDrawerScreen("offline", "settings") },
                            onOpenQr = { openDrawerScreen("qr", "settings") },
                            onOpenStudio = { openDrawerScreen("studio", "settings") },
                            onOpenPromote = { openDrawerScreen("promote", "settings") },
                            startPage = settingsStartPage
                        )
                    }

                    "change_password" -> {
                        ChangePasswordScreen(
                            repository = repository,
                            onBack = { currentScreen = "settings" },
                            onChanged = { currentScreen = "settings" }
                        )
                    }

                    "admin" -> {
                        // Defense in depth: server also enforces ADMIN on every call.
                        if (currentUser?.role == "admin") {
                            AdminDashboardScreen(
                                repository = repository,
                                onBackToFeed = { currentScreen = "feed" }
                            )
                        } else {
                            LaunchedEffect(Unit) { currentScreen = "feed" }
                        }
                    }

                    "legal" -> {
                        LegalScreen(
                            type = legalType,
                            onBack = { currentScreen = "profile" }
                        )
                    }
                }
            }

            // Bottom Navigation overlay positioned at bottom center
            if (showBottomNav) {
                ZevoraBottomNavigation(
                    currentScreen = currentScreen,
                    isFeedScreen = (currentScreen == "feed"),
                    unreadBadgeCount = if (AppPrefs.isPushEnabled()) unreadNotifications else 0,
                    onNavigate = { screen ->
                        if (screen == "profile") {
                            viewingProfileUserId = null
                        }
                        // Guests browse feed/friends; account areas need a login.
                        if (currentUser == null && screen in listOf("create", "inbox", "profile")) {
                            currentScreen = "welcome"
                        } else {
                            currentScreen = screen
                        }
                    },
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }
        }
    }
}

@Composable
fun ZevoraBottomNavigation(
    currentScreen: String,
    isFeedScreen: Boolean,
    unreadBadgeCount: Int,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                if (isFeedScreen) {
                    androidx.compose.ui.graphics.Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.45f),
                            Color.Black.copy(alpha = 0.85f)
                        )
                    )
                } else {
                    androidx.compose.ui.graphics.SolidColor(Color.Black.copy(alpha = 0.96f))
                }
            )
            .windowInsetsPadding(WindowInsets.navigationBars)
    ) {
        // Delicate subtle top hairline only when not on feed
        if (!isFeedScreen) {
            HorizontalDivider(
                color = Color.White.copy(alpha = 0.08f),
                thickness = 0.5.dp,
                modifier = Modifier.align(Alignment.TopCenter)
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            // Home / Feed
            BottomNavItem(
                icon = if (currentScreen == "feed") Icons.Default.Home else Icons.Outlined.Home,
                label = "Home",
                isSelected = (currentScreen == "feed"),
                onClick = { onNavigate("feed") },
                testTag = "nav_home"
            )

            // Friends
            BottomNavItem(
                icon = if (currentScreen == "friends") Icons.Default.Groups else Icons.Outlined.Groups,
                label = "Friends",
                isSelected = (currentScreen == "friends"),
                onClick = { onNavigate("friends") },
                testTag = "nav_friends"
            )

            // Distinctive Rivo Center Create '+' Button
            ZevoraCenterCreateButton(
                onClick = { onNavigate("create") }
            )

            // Inbox
            BottomNavItem(
                icon = if (currentScreen == "inbox") Icons.AutoMirrored.Filled.Chat else Icons.AutoMirrored.Outlined.Chat,
                label = "Inbox",
                isSelected = (currentScreen == "inbox"),
                badgeCount = unreadBadgeCount,
                onClick = { onNavigate("inbox") },
                testTag = "nav_inbox"
            )

            // Profile
            BottomNavItem(
                icon = if (currentScreen == "profile") Icons.Default.Person else Icons.Outlined.Person,
                label = "Profile",
                isSelected = (currentScreen == "profile"),
                onClick = { onNavigate("profile") },
                testTag = "nav_profile"
            )
        }
    }
}

@Composable
private fun BottomNavItem(
    icon: ImageVector,
    label: String,
    isSelected: Boolean,
    badgeCount: Int = 0,
    onClick: () -> Unit,
    testTag: String
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .testTag(testTag)
    ) {
        Box(contentAlignment = Alignment.TopEnd) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (isSelected) Color.White else Color.White.copy(alpha = 0.55f),
                modifier = Modifier.size(26.dp)
            )
            if (badgeCount > 0) {
                Box(
                    modifier = Modifier
                        .offset(x = 8.dp, y = (-4).dp)
                        .clip(CircleShape)
                        .background(ZevoraRed)
                        .padding(horizontal = 4.dp, vertical = 1.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (badgeCount > 99) "99+" else "$badgeCount",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            color = if (isSelected) Color.White else Color.White.copy(alpha = 0.55f),
            fontSize = 12.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold
        )
    }
}

@Composable
private fun ZevoraCenterCreateButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clickable(onClick = onClick)
            .testTag("nav_upload_center_button")
            .padding(horizontal = 6.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        // Rivo mark as the center action, framed by the brand gradient
        ZevoraMark(size = 44.dp)
    }
}
