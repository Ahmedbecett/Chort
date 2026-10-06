package com.example

import android.content.Intent
import android.os.Bundle
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
import com.example.data.remote.FacebookAuth
import com.example.ui.components.ChortMark
import com.example.ui.screens.admin.AdminDashboardScreen
import com.example.ui.screens.auth.AuthScreen
import com.example.ui.screens.auth.OtpMode
import com.example.ui.screens.auth.OtpScreen
import com.example.ui.screens.auth.PhoneAuthScreen
import com.example.ui.screens.auth.RecoveryScreen
import com.example.ui.screens.auth.SplashScreen
import com.example.ui.screens.auth.WelcomeAuthScreen
import com.example.ui.screens.chat.DirectMessageScreen
import com.example.ui.screens.discover.DiscoverScreen
import com.example.ui.screens.feed.FeedScreen
import com.example.ui.screens.friends.FriendsScreen
import com.example.ui.screens.inbox.InboxScreen
import com.example.ui.screens.legal.LegalScreen
import com.example.ui.screens.live.LiveStreamScreen
import com.example.ui.screens.profile.ProfileScreen
import com.example.ui.screens.settings.SettingsScreen
import com.example.ui.screens.sound.SoundDetailScreen
import com.example.ui.screens.tracking.ExternalTrackingCenterScreen
import com.example.ui.screens.upload.UploadScreen
import com.example.ui.theme.TokPulseTheme
import com.example.ui.theme.TokRed

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TokPulseTheme {
                TokPulseApp()
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
fun TokPulseApp() {
    val context = LocalContext.current
    val repository = TokPulseApplication.instance.repository
    val currentUser by repository.currentUser.collectAsState()
    val unreadNotifications by repository.getUnreadCount(currentUser?.id ?: "user_me").collectAsState(initial = 0)

    val connectivityMonitor = remember { NetworkConnectivityMonitor(context) }
    val isOnline by connectivityMonitor.isOnline.collectAsState(initial = true)

    // Screens: "splash" | auth: "welcome","email","phone","otp","recover" |
    // main: "feed","friends","upload","inbox","profile" | sub: "discover","settings",
    // "admin","legal","live","sound","chat","tracking"
    var currentScreen by remember { mutableStateOf("splash") }
    var viewingProfileUserId by remember { mutableStateOf<String?>(null) }
    var selectedSoundTitle by remember { mutableStateOf("Original Sound - thileli dz Creator") }
    var legalType by remember { mutableStateOf("terms") } // "terms" or "privacy"

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
        } else if (currentScreen in listOf("live", "sound", "chat", "tracking", "discover")) {
            currentScreen = "feed"
        } else if (currentScreen == "settings" || currentScreen == "admin") {
            viewingProfileUserId = null
            currentScreen = "profile"
        } else if (currentScreen == "legal") {
            currentScreen = "profile"
        } else if (currentScreen in listOf("email", "phone", "otp", "recover")) {
            currentScreen = "welcome"
        } else {
            currentScreen = "feed"
        }
    }

    val showBottomNav = currentScreen in listOf("feed", "friends", "upload", "inbox", "profile") && (currentScreen != "profile" || viewingProfileUserId == null)
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
                        .background(TokRed.copy(alpha = 0.95f))
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
                            onNavigateToCreate = { currentScreen = "upload" },
                            onNavigateToLive = { currentScreen = "live" },
                            onNavigateToSound = { title ->
                                selectedSoundTitle = title
                                currentScreen = "sound"
                            },
                            onNavigateToTracking = { currentScreen = "tracking" }
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
                            onNavigateToCreate = { currentScreen = "upload" },
                            onNavigateToSound = { title ->
                                selectedSoundTitle = title
                                currentScreen = "sound"
                            }
                        )
                    }

                    "discover" -> {
                        DiscoverScreen(
                            repository = repository,
                            onNavigateToProfile = { creatorId ->
                                viewingProfileUserId = creatorId
                                currentScreen = "profile"
                            },
                            onSelectVideo = { _ ->
                                currentScreen = "feed"
                            },
                            onNavigateToSound = { title ->
                                selectedSoundTitle = title
                                currentScreen = "sound"
                            },
                            onBack = { currentScreen = "feed" }
                        )
                    }

                    "upload" -> {
                        UploadScreen(
                            repository = repository,
                            onUploadSuccess = {
                                currentScreen = "feed"
                            }
                        )
                    }

                    "inbox" -> {
                        InboxScreen(
                            repository = repository,
                            onNavigateToProfile = { actorId ->
                                viewingProfileUserId = actorId
                                currentScreen = "profile"
                            },
                            onNavigateToSearch = { currentScreen = "discover" },
                            onNavigateToCreate = { currentScreen = "upload" }
                        )
                    }

                    "live" -> {
                        LiveStreamScreen(
                            onClose = { currentScreen = "feed" }
                        )
                    }

                    "sound" -> {
                        SoundDetailScreen(
                            soundTitle = selectedSoundTitle,
                            repository = repository,
                            onBack = { currentScreen = "feed" },
                            onUseSound = { currentScreen = "upload" },
                            onSelectVideo = { _ -> currentScreen = "feed" }
                        )
                    }

                    "chat" -> {
                        DirectMessageScreen(
                            onBack = { currentScreen = "inbox" }
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
                            onSelectVideo = { _ ->
                                currentScreen = "feed"
                            },
                            onRequireLogin = {
                                goWelcome()
                            },
                            onNavigateToSettings = { currentScreen = "settings" }
                        )
                    }

                    "settings" -> {
                        SettingsScreen(
                            repository = repository,
                            onBack = {
                                viewingProfileUserId = null
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
                            onLoggedOut = { goWelcome() }
                        )
                    }

                    "admin" -> {
                        AdminDashboardScreen(
                            repository = repository,
                            onBackToFeed = { currentScreen = "feed" }
                        )
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
                TokPulseBottomNavigation(
                    currentScreen = currentScreen,
                    isFeedScreen = (currentScreen == "feed"),
                    unreadBadgeCount = unreadNotifications,
                    onNavigate = { screen ->
                        if (screen == "profile") {
                            viewingProfileUserId = null
                        }
                        currentScreen = screen
                    },
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }
        }
    }
}

@Composable
fun TokPulseBottomNavigation(
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

            // Distinctive thileli dz Center Create '+' Button
            ChortCenterCreateButton(
                onClick = { onNavigate("upload") }
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
                        .background(TokRed)
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
private fun ChortCenterCreateButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clickable(onClick = onClick)
            .testTag("nav_upload_center_button")
            .padding(horizontal = 6.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        // thileli dz mark as the center action, framed by the brand gradient
        ChortMark(size = 44.dp)
    }
}
