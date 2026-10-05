package com.example

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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import com.example.ui.screens.admin.AdminDashboardScreen
import com.example.ui.screens.auth.AuthScreen
import com.example.ui.screens.chat.DirectMessageScreen
import com.example.ui.screens.discover.DiscoverScreen
import com.example.ui.screens.feed.FeedScreen
import com.example.ui.screens.inbox.InboxScreen
import com.example.ui.screens.legal.LegalScreen
import com.example.ui.screens.live.LiveStreamScreen
import com.example.ui.screens.profile.ProfileScreen
import com.example.ui.screens.sound.SoundDetailScreen
import com.example.ui.screens.tracking.ExternalTrackingCenterScreen
import com.example.ui.screens.upload.UploadScreen
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TokBorder
import com.example.ui.theme.TokCyan
import com.example.ui.theme.TokDarkBg
import com.example.ui.theme.TokPulseTheme
import com.example.ui.theme.TokRed

import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.border

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
}

@Composable
fun TokPulseApp() {
    val context = LocalContext.current
    val repository = TokPulseApplication.instance.repository
    val currentUser by repository.currentUser.collectAsState()
    val unreadNotifications by repository.getUnreadCount(currentUser?.id ?: "user_me").collectAsState(initial = 0)

    val connectivityMonitor = remember { NetworkConnectivityMonitor(context) }
    val isOnline by connectivityMonitor.isOnline.collectAsState(initial = true)

    var currentScreen by remember { mutableStateOf("feed") } // "feed", "discover", "upload", "inbox", "profile", "admin", "auth", "legal", "live", "sound", "chat", "tracking"
    var viewingProfileUserId by remember { mutableStateOf<String?>(null) }
    var selectedSoundTitle by remember { mutableStateOf("Original Sound - Chort Creator") }
    var legalType by remember { mutableStateOf("terms") } // "terms" or "privacy"

    // Back handling for sub screens
    BackHandler(enabled = currentScreen != "feed") {
        if (currentScreen == "profile" && viewingProfileUserId != null) {
            viewingProfileUserId = null
        } else if (currentScreen in listOf("live", "sound", "chat", "tracking")) {
            currentScreen = "feed"
        } else {
            currentScreen = "feed"
        }
    }

    val showBottomNav = currentScreen in listOf("feed", "discover", "upload", "inbox", "profile") && (currentScreen != "profile" || viewingProfileUserId == null)
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
                        bottom = if (showBottomNav && currentScreen != "feed") (48.dp + navBarBottomInset) else 0.dp
                    )
            ) {
                when (currentScreen) {
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
                            }
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
                            onNavigateToChat = {
                                currentScreen = "chat"
                            }
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
                                currentScreen = "auth"
                            }
                        )
                    }

                    "admin" -> {
                        AdminDashboardScreen(
                            repository = repository,
                            onBackToFeed = { currentScreen = "feed" }
                        )
                    }

                    "auth" -> {
                        AuthScreen(
                            repository = repository,
                            onAuthSuccess = { currentScreen = "feed" }
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
                .height(48.dp)
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

            // Discover
            BottomNavItem(
                icon = if (currentScreen == "discover") Icons.Default.Search else Icons.Outlined.Search,
                label = "Discover",
                isSelected = (currentScreen == "discover"),
                onClick = { onNavigate("discover") },
                testTag = "nav_discover"
            )

            // Distinctive Chort Center Create '+' Button
            ChortCenterCreateButton(
                onClick = { onNavigate("upload") }
            )

            // Notifications
            BottomNavItem(
                icon = if (currentScreen == "inbox") Icons.Default.Notifications else Icons.Outlined.Notifications,
                label = "Notifications",
                isSelected = (currentScreen == "inbox"),
                badgeCount = unreadBadgeCount,
                onClick = { onNavigate("inbox") },
                testTag = "nav_notifications"
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
                modifier = Modifier.size(22.dp)
            )
            if (badgeCount > 0) {
                Box(
                    modifier = Modifier
                        .offset(x = 5.dp, y = (-2).dp)
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(TokRed)
                )
            }
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            color = if (isSelected) Color.White else Color.White.copy(alpha = 0.55f),
            fontSize = 10.sp,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
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
        // Sleek Chort Squircle with integrated Cyan-Red gradient border and crisp white center
        Box(
            modifier = Modifier
                .size(width = 42.dp, height = 28.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(
                    androidx.compose.ui.graphics.Brush.horizontalGradient(
                        colors = listOf(TokCyan, TokRed)
                    )
                )
                .padding(1.5.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.White),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Create Video",
                tint = Color.Black,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
