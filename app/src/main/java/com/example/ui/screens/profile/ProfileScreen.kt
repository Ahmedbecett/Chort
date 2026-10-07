package com.example.ui.screens.profile

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.AutoGraph
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.local.entities.UserEntity
import com.example.data.local.entities.VideoEntity
import com.example.data.repository.ZevoraRepository
import com.example.ui.components.ReportDialog
import com.example.ui.screens.activity.compactCount
import com.example.ui.theme.AccentGold
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.ZevoraCyan
import com.example.ui.theme.ZevoraDarkBg
import com.example.ui.theme.ZevoraDarkElevated
import com.example.ui.theme.ZevoraDarkSurface
import com.example.ui.theme.ZevoraRed
import com.example.util.AppPrefs
import kotlinx.coroutines.launch

/**
 * ZEVORA 3.1.0 — profile in the reference layout: avatar + stats header,
 * action buttons, Videos/Liked/Saved tabs, 3-column grid, follow system with
 * live server state, private-account locks, followers sheets, and the full
 * navigation drawer (Balance … Settings).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    repository: ZevoraRepository,
    userIdToView: String? = null,
    onNavigateToAdmin: () -> Unit,
    onNavigateToLegal: (String) -> Unit,
    onSelectVideo: (VideoEntity) -> Unit,
    onRequireLogin: () -> Unit,
    onNavigateToSettings: () -> Unit = {},
    onViewProfile: (String) -> Unit = {},
    onOpenWallet: () -> Unit = {},
    onOpenActivity: () -> Unit = {},
    onOpenOffline: () -> Unit = {},
    onOpenQr: () -> Unit = {},
    onOpenStudio: () -> Unit = {},
    onOpenPromote: () -> Unit = {},
    onEditProfile: () -> Unit = {},
    onOpenContacts: () -> Unit = {},
    onOpenInbox: () -> Unit = {},
    onOpenCreate: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val loggedInUser by repository.currentUser.collectAsState()

    val targetUserId = userIdToView ?: loggedInUser?.id ?: "user_me"
    val isMyProfile = targetUserId == loggedInUser?.id

    val profileUserFromDb by remember(targetUserId) {
        repository.getUserById(targetUserId)
    }.collectAsState(initial = null)
    val profileUser = profileUserFromDb ?: (if (isMyProfile) loggedInUser else null)

    val userVideos by repository.getVideosByCreator(targetUserId).collectAsState(initial = emptyList())
    val allVideos by repository.getActiveVideos().collectAsState(initial = emptyList())
    val localFollowers by repository.getFollowersCount(targetUserId).collectAsState(initial = 0)
    val localFollowing by repository.getFollowingCount(targetUserId).collectAsState(initial = 0)
    val likedIdsMine by repository.getUserLikedVideoIds(loggedInUser?.id ?: "").collectAsState(initial = emptyList())
    val savedIds by repository.savedVideoIds.collectAsState(initial = emptySet())

    var serverFollowers by remember { mutableStateOf<Int?>(null) }
    var serverFollowing by remember { mutableStateOf<Int?>(null) }
    var following by remember { mutableStateOf(false) }
    var followBusy by remember { mutableStateOf(false) }
    var syncing by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableStateOf(0) } // 0 videos, 1 liked, 2 saved
    var otherLiked by remember { mutableStateOf<List<VideoEntity>?>(null) }
    var otherLikedPrivate by remember { mutableStateOf(false) }
    var showFollowSheet by remember { mutableStateOf<String?>(null) } // "followers" | "following"
    var showMoreMenu by remember { mutableStateOf(false) }
    var showReport by remember { mutableStateOf(false) }
    var blockedNow by remember { mutableStateOf(AppPrefs.isBlocked(targetUserId)) }

    val drawerState = rememberDrawerState(DrawerValue.Closed)

    // ---- server sync ----
    LaunchedEffect(targetUserId) {
        selectedTab = 0
        otherLiked = null
        otherLikedPrivate = false
        serverFollowers = null
        serverFollowing = null
        blockedNow = AppPrefs.isBlocked(targetUserId)
        syncing = true
        try {
            repository.syncProfileFromServer(targetUserId)
            if (isMyProfile) {
                repository.fetchLikedVideos(targetUserId)
                repository.fetchSavedVideos(targetUserId)
            } else {
                val state = repository.fetchFollowState(targetUserId).getOrNull()
                if (state != null) {
                    following = state.following
                    serverFollowers = state.followersCount
                    serverFollowing = state.followingCount
                } else {
                    following = repository.isFollowing(targetUserId)
                }
            }
        } finally {
            syncing = false
        }
    }

    // Other users' liked videos (server-gated for private accounts).
    LaunchedEffect(selectedTab, targetUserId) {
        if (!isMyProfile && selectedTab == 1 && otherLiked == null && !otherLikedPrivate) {
            val result = repository.fetchLikedVideos(targetUserId)
            if (result.isSuccess) {
                otherLiked = result.getOrNull() ?: emptyList()
            } else if ((result.exceptionOrNull()?.message ?: "").contains("private", ignoreCase = true) ||
                (result.exceptionOrNull()?.message ?: "").contains("403")
            ) {
                otherLikedPrivate = true
            } else {
                otherLiked = emptyList()
            }
        }
    }

    val followersCount = serverFollowers ?: profileUser?.followersCount?.takeIf { it > 0 } ?: localFollowers
    val followingCount = serverFollowing ?: profileUser?.followingCount?.takeIf { it > 0 } ?: localFollowing
    val targetPrivate = !isMyProfile && AppPrefs.isUserPrivate(targetUserId)
    val gridLocked = targetPrivate && !following

    val boostedIds = remember(userVideos) { AppPrefs.getBoostedIds() }
    val orderedVideos = remember(userVideos, boostedIds, isMyProfile) {
        if (!isMyProfile) userVideos.sortedByDescending { it.createdAt }
        else userVideos.sortedWith(compareBy({ it.id !in boostedIds }, { -it.createdAt }))
    }
    val myLikedVideos = remember(likedIdsMine, allVideos) {
        val byId = allVideos.associateBy { it.id }
        likedIdsMine.mapNotNull { byId[it] }
    }
    val mySavedVideos = remember(savedIds, allVideos) {
        val byId = allVideos.associateBy { it.id }
        savedIds.mapNotNull { byId[it] }
    }

    fun shareProfile() {
        try {
            val profile = profileUser
            val text = if (AppPrefs.showProfileOnShare()) {
                "Follow @${profile?.username ?: "zevora"} on ZEVORA! zevora:user:$targetUserId:${profile?.username ?: ""}"
            } else {
                "Find me on ZEVORA!"
            }
            context.startActivity(
                Intent.createChooser(
                    Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, text)
                    },
                    "Share profile"
                )
            )
        } catch (e: Exception) {
            Toast.makeText(context, "Share failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    fun toggleFollow() {
        if (loggedInUser == null) {
            onRequireLogin()
            return
        }
        if (followBusy) return
        followBusy = true
        scope.launch {
            try {
                following = repository.toggleFollow(targetUserId)
                val state = repository.fetchFollowState(targetUserId).getOrNull()
                if (state != null) {
                    serverFollowers = state.followersCount
                    serverFollowing = state.followingCount
                }
            } finally {
                followBusy = false
            }
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = isMyProfile,
        drawerContent = {
            if (isMyProfile) {
                ModalDrawerSheet(
                    drawerContainerColor = ZevoraDarkSurface,
                    drawerContentColor = Color.White
                ) {
                    ProfileDrawerContent(
                        user = loggedInUser,
                        videosCount = userVideos.size,
                        followersCount = followersCount,
                        followingCount = followingCount,
                        onOpenWallet = { scope.launch { drawerState.close() }; onOpenWallet() },
                        onOpenActivity = { scope.launch { drawerState.close() }; onOpenActivity() },
                        onOpenOffline = { scope.launch { drawerState.close() }; onOpenOffline() },
                        onOpenQr = { scope.launch { drawerState.close() }; onOpenQr() },
                        onOpenStudio = { scope.launch { drawerState.close() }; onOpenStudio() },
                        onOpenPromote = { scope.launch { drawerState.close() }; onOpenPromote() },
                        onOpenSettings = { scope.launch { drawerState.close() }; onNavigateToSettings() },
                        onOpenAdmin = { scope.launch { drawerState.close() }; onNavigateToAdmin() }
                    )
                }
            }
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(ZevoraDarkBg)
        ) {
            // ---- top bar ----
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!isMyProfile) {
                    IconButton(onClick = { onViewProfile("") }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
                    }
                } else {
                    Spacer(Modifier.width(48.dp))
                }
                Spacer(Modifier.weight(1f))
                if (targetPrivate) {
                    Icon(Icons.Default.Lock, null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    profileUser?.username ?: "profile",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.weight(1f))
                if (isMyProfile) {
                    IconButton(onClick = { scope.launch { drawerState.open() } }) {
                        Icon(Icons.Default.Menu, "Menu", tint = Color.White)
                    }
                } else {
                    Box {
                        IconButton(onClick = { showMoreMenu = true }) {
                            Icon(Icons.Default.MoreVert, "More", tint = Color.White)
                        }
                        DropdownMenu(
                            expanded = showMoreMenu,
                            onDismissRequest = { showMoreMenu = false },
                            modifier = Modifier.background(ZevoraDarkElevated)
                        ) {
                            DropdownMenuItem(
                                text = { Text("Share profile", color = Color.White) },
                                leadingIcon = { Icon(Icons.Default.Share, null, tint = TextSecondary) },
                                onClick = { showMoreMenu = false; shareProfile() }
                            )
                            DropdownMenuItem(
                                text = { Text("Copy link", color = Color.White) },
                                leadingIcon = { Icon(Icons.Default.Link, null, tint = TextSecondary) },
                                onClick = {
                                    showMoreMenu = false
                                    try {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        clipboard.setPrimaryClip(
                                            ClipData.newPlainText(
                                                "ZEVORA profile",
                                                "zevora:user:$targetUserId:${profileUser?.username ?: ""}"
                                            )
                                        )
                                        Toast.makeText(context, "Profile link copied", Toast.LENGTH_SHORT).show()
                                    } catch (_: Exception) {
                                    }
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Report", color = Color.White) },
                                leadingIcon = { Icon(Icons.Default.Flag, null, tint = TextSecondary) },
                                onClick = { showMoreMenu = false; showReport = true }
                            )
                            DropdownMenuItem(
                                text = { Text(if (blockedNow) "Unblock" else "Block", color = ZevoraRed) },
                                leadingIcon = { Icon(Icons.Default.Block, null, tint = ZevoraRed) },
                                onClick = {
                                    showMoreMenu = false
                                    blockedNow = AppPrefs.toggleBlocked(targetUserId)
                                    Toast.makeText(
                                        context,
                                        if (blockedNow) "Blocked — their videos are hidden from your feeds"
                                        else "Unblocked",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            )
                        }
                    }
                }
            }

            if (profileUser == null && !syncing) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Profile unavailable offline.", color = TextSecondary)
                }
                return@Column
            }

            // ---- header: avatar + stats ----
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .clip(CircleShape)
                        .background(ZevoraDarkElevated)
                        .border(1.dp, Color.White.copy(alpha = 0.15f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    val avatar = profileUser?.avatarUrl ?: ""
                    if (avatar.isNotBlank()) {
                        AsyncImage(
                            model = avatar,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(Icons.Default.Person, null, tint = TextMuted, modifier = Modifier.size(48.dp))
                    }
                }
                Spacer(Modifier.width(22.dp))
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.SpaceEvenly) {
                    StatColumn("${orderedVideos.size}", "Posts", onClick = null)
                    StatColumn(compactCount(followersCount), "Followers") { showFollowSheet = "followers" }
                    StatColumn(compactCount(followingCount), "Following") { showFollowSheet = "following" }
                }
            }

            // ---- name / bio / city ----
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                Text(
                    profileUser?.displayName ?: "",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
                if (profileUser?.bio?.isNotBlank() == true) {
                    Text(profileUser!!.bio, color = Color.White, fontSize = 14.sp)
                }
                if (AppPrefs.showProfileCity() && AppPrefs.getProfileCity().isNotBlank() && isMyProfile) {
                    Text("📍 ${AppPrefs.getProfileCity()}", color = TextSecondary, fontSize = 13.sp)
                }
            }
            Spacer(Modifier.height(12.dp))

            // ---- action buttons ----
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (isMyProfile) {
                    ProfileButton("Edit profile", Modifier.weight(1f), primary = false) { onEditProfile() }
                    ProfileButton("Share profile", Modifier.weight(1f), primary = false) { shareProfile() }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(ZevoraDarkElevated)
                            .clickable(onClick = onOpenContacts)
                            .padding(horizontal = 14.dp, vertical = 11.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.PersonAdd, "Find friends", tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                } else {
                    Button(
                        onClick = { toggleFollow() },
                        enabled = !followBusy,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (following) ZevoraDarkElevated else ZevoraRed
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                    ) {
                        Text(
                            if (following) "Following" else "Follow",
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                    Button(
                        onClick = {
                            if (loggedInUser == null) onRequireLogin() else onOpenInbox()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ZevoraDarkElevated),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                    ) {
                        Text("Message", fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
            Spacer(Modifier.height(10.dp))

            // ---- tabs ----
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                ProfileTab(Icons.Default.GridView, "Videos", selectedTab == 0) { selectedTab = 0 }
                if (isMyProfile || !targetPrivate || following) {
                    ProfileTab(
                        if (selectedTab == 1) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        "Liked",
                        selectedTab == 1
                    ) { selectedTab = 1 }
                }
                if (isMyProfile) {
                    ProfileTab(
                        if (selectedTab == 2) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                        "Saved",
                        selectedTab == 2
                    ) { selectedTab = 2 }
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(0.5.dp)
                    .background(Color.White.copy(alpha = 0.12f))
            )

            // ---- content ----
            if (gridLocked) {
                PrivateLockPanel(username = profileUser?.username ?: "user")
            } else if (syncing && orderedVideos.isEmpty() && selectedTab == 0) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = ZevoraRed)
                }
            } else {
                val gridVideos = when (selectedTab) {
                    0 -> orderedVideos
                    1 -> if (isMyProfile) myLikedVideos else (otherLiked ?: emptyList())
                    else -> mySavedVideos
                }
                if (!isMyProfile && selectedTab == 1 && otherLikedPrivate) {
                    PrivateLockPanel(username = profileUser?.username ?: "user")
                } else if (gridVideos.isEmpty()) {
                    EmptyGridPanel(
                        isMyProfile = isMyProfile,
                        tab = selectedTab,
                        onOpenCreate = onOpenCreate
                    )
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(1.dp),
                        horizontalArrangement = Arrangement.spacedBy(1.dp),
                        verticalArrangement = Arrangement.spacedBy(1.dp)
                    ) {
                        items(gridVideos, key = { it.id }) { video ->
                            Box(
                                modifier = Modifier
                                    .aspectRatio(0.75f)
                                    .background(ZevoraDarkSurface)
                                    .clickable { onSelectVideo(video) }
                            ) {
                                AsyncImage(
                                    model = video.thumbnailUrl.ifBlank { video.videoUrl },
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                                Row(
                                    modifier = Modifier.align(Alignment.BottomStart).padding(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.PlayArrow, null, tint = Color.White, modifier = Modifier.size(14.dp))
                                    Text(
                                        compactCount(video.viewsCount),
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                if (video.id in boostedIds) {
                                    Text(
                                        "BOOSTED",
                                        color = Color.Black,
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        modifier = Modifier
                                            .align(Alignment.TopStart)
                                            .padding(4.dp)
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(AccentGold)
                                            .padding(horizontal = 5.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // ---- followers / following sheet ----
    showFollowSheet?.let { mode ->
        FollowListSheet(
            repository = repository,
            targetUserId = targetUserId,
            mode = mode,
            onViewProfile = { id ->
                showFollowSheet = null
                onViewProfile(id)
            },
            onDismiss = { showFollowSheet = null }
        )
    }

    if (showReport) {
        ReportDialog(
            targetType = "user",
            targetSnippet = "@${profileUser?.username ?: "user"}",
            onDismiss = { showReport = false },
            onSubmitReport = { reason, desc ->
                scope.launch {
                    repository.submitReport(
                        targetType = "user",
                        targetId = targetUserId,
                        targetOwnerUsername = profileUser?.username ?: "",
                        targetSnippet = "@${profileUser?.username ?: "user"}",
                        reason = reason,
                        description = desc
                    )
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        Toast.makeText(context, "Report submitted — thank you", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )
    }
}

@Composable
private fun StatColumn(value: String, label: String, onClick: (() -> Unit)? = null) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = if (onClick != null) {
            Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick).padding(4.dp)
        } else {
            Modifier.padding(4.dp)
        }
    ) {
        Text(value, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
        Text(label, color = TextSecondary, fontSize = 12.5.sp)
    }
}

@Composable
private fun ProfileButton(label: String, modifier: Modifier, primary: Boolean, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (primary) ZevoraRed else ZevoraDarkElevated)
            .clickable(onClick = onClick)
            .padding(vertical = 11.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ProfileTab(icon: ImageVector, label: String, active: Boolean, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 26.dp, vertical = 8.dp)
    ) {
        Icon(
            icon,
            label,
            tint = if (active) Color.White else TextMuted,
            modifier = Modifier.size(24.dp)
        )
        Spacer(Modifier.height(3.dp))
        Box(
            modifier = Modifier
                .width(30.dp)
                .height(2.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(if (active) Color.White else Color.Transparent)
        )
    }
}

@Composable
private fun PrivateLockPanel(username: String) {
    Column(
        modifier = Modifier.fillMaxSize().padding(48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.Lock, null, tint = TextMuted, modifier = Modifier.size(48.dp))
        Spacer(Modifier.height(12.dp))
        Text("This account is private", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Spacer(Modifier.height(6.dp))
        Text(
            "Follow @$username to see their videos and liked posts.",
            color = TextSecondary,
            fontSize = 13.sp
        )
    }
}

@Composable
private fun EmptyGridPanel(isMyProfile: Boolean, tab: Int, onOpenCreate: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        val (title, subtitle) = when {
            isMyProfile && tab == 0 -> "No videos yet" to "Publish your first video and it will appear here."
            isMyProfile && tab == 1 -> "No liked videos" to "Double-tap any video to like it."
            isMyProfile -> "No saved videos" to "Bookmark videos to watch them later."
            tab == 1 -> "No liked videos" to "This user hasn't liked anything public yet."
            else -> "No videos" to "This user hasn't published anything yet."
        }
        Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Spacer(Modifier.height(6.dp))
        Text(subtitle, color = TextSecondary, fontSize = 13.sp)
        if (isMyProfile && tab == 0) {
            Spacer(Modifier.height(14.dp))
            Button(
                onClick = onOpenCreate,
                colors = ButtonDefaults.buttonColors(containerColor = ZevoraRed),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Create video", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FollowListSheet(
    repository: ZevoraRepository,
    targetUserId: String,
    mode: String,
    onViewProfile: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var users by remember { mutableStateOf<List<UserEntity>?>(null) }
    var isPrivateList by remember { mutableStateOf(false) }

    LaunchedEffect(mode, targetUserId) {
        scope.launch {
            val result = if (mode == "followers") {
                repository.fetchFollowers(targetUserId)
            } else {
                repository.fetchFollowing(targetUserId)
            }
            if (result.isSuccess) {
                users = result.getOrNull() ?: emptyList()
            } else if ((result.exceptionOrNull()?.message ?: "").contains("private", ignoreCase = true) ||
                (result.exceptionOrNull()?.message ?: "").contains("403")
            ) {
                isPrivateList = true
                users = emptyList()
            } else {
                users = emptyList()
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(),
        containerColor = ZevoraDarkSurface
    ) {
        Text(
            if (mode == "followers") "Followers" else "Following",
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
        )
        val list = users
        when {
            list == null -> {
                Box(
                    Modifier.fillMaxWidth().height(140.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = ZevoraRed)
                }
            }
            isPrivateList -> {
                Row(
                    Modifier.fillMaxWidth().padding(24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(Icons.Default.Lock, null, tint = TextMuted)
                    Spacer(Modifier.width(8.dp))
                    Text("This list is private.", color = TextSecondary)
                }
            }
            list.isEmpty() -> {
                Text(
                    "Nobody here yet.",
                    color = TextMuted,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 20.dp)
                )
            }
            else -> {
                LazyColumn(modifier = Modifier.padding(bottom = 28.dp)) {
                    items(list, key = { it.id }) { user ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onViewProfile(user.id) }
                                .padding(horizontal = 20.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(ZevoraDarkElevated),
                                contentAlignment = Alignment.Center
                            ) {
                                if (user.avatarUrl.isNotBlank()) {
                                    AsyncImage(
                                        model = user.avatarUrl,
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Icon(Icons.Default.Person, null, tint = TextMuted)
                                }
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    user.displayName.ifBlank { user.username },
                                    color = Color.White,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text("@${user.username}", color = TextMuted, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileDrawerContent(
    user: com.example.data.local.entities.UserEntity?,
    videosCount: Int,
    followersCount: Int,
    followingCount: Int,
    onOpenWallet: () -> Unit,
    onOpenActivity: () -> Unit,
    onOpenOffline: () -> Unit,
    onOpenQr: () -> Unit,
    onOpenStudio: () -> Unit,
    onOpenPromote: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAdmin: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().padding(top = 48.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(ZevoraDarkElevated),
                contentAlignment = Alignment.Center
            ) {
                val avatar = user?.avatarUrl ?: ""
                if (avatar.isNotBlank()) {
                    AsyncImage(
                        model = avatar,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(Icons.Default.Person, null, tint = TextMuted)
                }
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    user?.displayName ?: "Creator",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text("@${user?.username ?: "user"}", color = TextSecondary, fontSize = 13.sp)
            }
        }
        Text(
            "$videosCount posts • $followersCount followers • $followingCount following",
            color = TextMuted,
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
        )
        Spacer(Modifier.height(10.dp))
        DrawerItem(Icons.Default.AccountBalanceWallet, "Balance") { onOpenWallet() }
        DrawerItem(Icons.Default.History, "Activity center") { onOpenActivity() }
        DrawerItem(Icons.Default.CloudDownload, "Offline videos") { onOpenOffline() }
        DrawerItem(Icons.Default.QrCode2, "Your QR code") { onOpenQr() }
        DrawerItem(Icons.Default.AutoGraph, "ZEVORA Studio") { onOpenStudio() }
        DrawerItem(Icons.Default.LocalFireDepartment, "Promote") { onOpenPromote() }
        DrawerItem(Icons.Default.Settings, "Settings and privacy") { onOpenSettings() }
        if (user?.role == "admin") {
            DrawerItem(Icons.Default.AdminPanelSettings, "Admin console") { onOpenAdmin() }
        }
    }
}

@Composable
private fun DrawerItem(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(16.dp))
        Text(label, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium)
    }
}
