package com.example.ui.screens.profile

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.example.data.local.entities.UserEntity
import com.example.data.local.entities.VideoEntity
import com.example.data.repository.TokPulseRepository
import com.example.ui.theme.AccentGold
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
fun ProfileScreen(
    repository: TokPulseRepository,
    userIdToView: String? = null,
    onNavigateToAdmin: () -> Unit,
    onNavigateToLegal: (String) -> Unit,
    onSelectVideo: (VideoEntity) -> Unit,
    onRequireLogin: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val loggedInUser by repository.currentUser.collectAsState()

    val targetUserId = userIdToView ?: loggedInUser?.id ?: "user_me"
    val isMyProfile = (targetUserId == loggedInUser?.id)

    val targetUserFlow = remember(targetUserId) { repository.getUserById(targetUserId) }
    val profileUserFromDb by targetUserFlow.collectAsState(initial = null)
    val profileUser = profileUserFromDb ?: (if (isMyProfile) loggedInUser else null)

    val userVideos by repository.getVideosByCreator(targetUserId).collectAsState(initial = emptyList())
    val likedVideoIds by repository.getUserLikedVideoIds(targetUserId).collectAsState(initial = emptyList())
    val allVideos by repository.getActiveVideos().collectAsState(initial = emptyList())
    val likedVideos = remember(likedVideoIds, allVideos) {
        allVideos.filter { it.id in likedVideoIds }
    }

    val followingIds by repository.getFollowingIds(loggedInUser?.id ?: "").collectAsState(initial = emptyList())
    val isFollowing = remember(followingIds, targetUserId) { targetUserId in followingIds }

    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Videos, 1 = Liked, 2 = Private
    var showMenu by remember { mutableStateOf(false) }
    var showEditProfileDialog by remember { mutableStateOf(false) }
    var showDeleteAccountDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TokDarkBg)
            .statusBarsPadding()
            .testTag("profile_screen")
    ) {
        // Top Action Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "@${profileUser?.username ?: "user"}",
                color = TextPrimary,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                // If current user is Admin, show quick access to Admin Moderation Dashboard
                if (loggedInUser?.role == "admin" || isMyProfile) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(TokRed.copy(alpha = 0.2f))
                            .border(1.dp, TokRed, RoundedCornerShape(12.dp))
                            .clickable { onNavigateToAdmin() }
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                            .testTag("admin_dashboard_shortcut")
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.AdminPanelSettings,
                                contentDescription = "Admin Dashboard",
                                tint = TokRed,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Admin Portal",
                                color = TokRed,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                }

                // Settings Menu
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Options",
                            tint = TextPrimary
                        )
                    }

                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                        modifier = Modifier.background(TokDarkSurface)
                    ) {
                        DropdownMenuItem(
                            text = { Text("Terms of Service", color = TextPrimary) },
                            leadingIcon = { Icon(Icons.Default.Policy, contentDescription = null, tint = TokCyan) },
                            onClick = {
                                showMenu = false
                                onNavigateToLegal("terms")
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Privacy Policy", color = TextPrimary) },
                            leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = TokCyan) },
                            onClick = {
                                showMenu = false
                                onNavigateToLegal("privacy")
                            }
                        )
                        if (isMyProfile) {
                            DropdownMenuItem(
                                text = { Text("Request Account Deletion", color = StatusBanned) },
                                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = StatusBanned) },
                                onClick = {
                                    showMenu = false
                                    showDeleteAccountDialog = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Sign Out", color = TextSecondary) },
                                leadingIcon = { Icon(Icons.Default.Logout, contentDescription = null, tint = TextSecondary) },
                                onClick = {
                                    showMenu = false
                                    scope.launch {
                                        repository.logout()
                                        onRequireLogin()
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }

        // Profile Body in LazyColumn
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 60.dp)
        ) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Profile Avatar
                    Box(contentAlignment = Alignment.BottomEnd) {
                        AsyncImage(
                            model = profileUser?.avatarUrl ?: "https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?w=300",
                            contentDescription = "Profile Picture",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(90.dp)
                                .clip(CircleShape)
                                .border(2.dp, TokCyan, CircleShape)
                        )
                        if (profileUser?.role == "admin") {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(TokRed),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AdminPanelSettings,
                                    contentDescription = "Admin",
                                    tint = Color.White,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Display Name & Verified
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = profileUser?.displayName ?: "Creator",
                            color = TextPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Default.Verified,
                            contentDescription = "Verified",
                            tint = TokCyan,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Counters (Following, Followers, Likes)
                    Row(
                        modifier = Modifier.fillMaxWidth(0.85f),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        ProfileStat(label = "Following", count = profileUser?.followingCount ?: 0)
                        ProfileStat(label = "Followers", count = profileUser?.followersCount ?: 0)
                        ProfileStat(label = "Likes", count = profileUser?.totalLikes ?: 0)
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Action Buttons (Edit Profile / Follow)
                    Row(
                        modifier = Modifier.fillMaxWidth(0.85f),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (isMyProfile) {
                            Button(
                                onClick = { showEditProfileDialog = true },
                                colors = ButtonDefaults.buttonColors(containerColor = TokDarkElevated),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(42.dp)
                                    .border(1.dp, TokBorder, RoundedCornerShape(12.dp))
                                    .testTag("edit_profile_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Edit",
                                    tint = TextPrimary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Edit Profile", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            }
                        } else {
                            Button(
                                onClick = {
                                    scope.launch {
                                        repository.toggleFollow(targetUserId)
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isFollowing) TokDarkElevated else TokRed
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(42.dp)
                            ) {
                                Icon(
                                    imageVector = if (isFollowing) Icons.Default.PersonRemove else Icons.Default.PersonAdd,
                                    contentDescription = "Follow Action",
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (isFollowing) "Following" else "Follow",
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Button(
                            onClick = {
                                Toast.makeText(context, "Profile link copied!", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = TokDarkElevated),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .size(42.dp)
                                .border(1.dp, TokBorder, RoundedCornerShape(12.dp))
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "Share",
                                tint = TextPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Biography
                    Text(
                        text = profileUser?.bio ?: "No bio yet.",
                        color = TextSecondary,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 18.sp,
                        modifier = Modifier.fillMaxWidth(0.9f)
                    )

                    Spacer(modifier = Modifier.height(18.dp))
                }
            }

            // Tabs for Videos, Liked, Bookmarks
            item {
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = TokDarkBg,
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
                        onClick = { selectedTab = 0 },
                        icon = { Icon(Icons.Default.GridOn, contentDescription = "My Videos", modifier = Modifier.size(20.dp)) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        icon = { Icon(Icons.Default.Favorite, contentDescription = "Liked Videos", modifier = Modifier.size(20.dp)) }
                    )
                    Tab(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        icon = { Icon(Icons.Default.Lock, contentDescription = "Private Videos", modifier = Modifier.size(20.dp)) }
                    )
                }
            }

            // Videos List Grid
            val videosToShow = when (selectedTab) {
                0 -> userVideos
                1 -> likedVideos
                else -> emptyList()
            }

            if (videosToShow.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (selectedTab == 0) "No videos uploaded yet" else if (selectedTab == 1) "No liked videos" else "Private video vault is empty",
                            color = TextMuted,
                            fontSize = 13.5.sp
                        )
                    }
                }
            } else {
                val chunked = videosToShow.chunked(3)
                items(chunked.size) { index ->
                    val rowItems = chunked[index]
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        rowItems.forEach { video ->
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(0.8f)
                                    .background(TokDarkElevated)
                                    .clickable { onSelectVideo(video) }
                            ) {
                                AsyncImage(
                                    model = video.thumbnailUrl,
                                    contentDescription = video.caption,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(
                                            Brush.verticalGradient(
                                                listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))
                                            )
                                        )
                                )
                                Row(
                                    modifier = Modifier
                                        .align(Alignment.BottomStart)
                                        .padding(4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = "Plays",
                                        tint = Color.White,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Text(
                                        text = "${video.viewsCount}",
                                        color = Color.White,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                        if (rowItems.size < 3) {
                            for (i in 0 until (3 - rowItems.size)) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }

    // Edit Profile Dialog
    if (showEditProfileDialog) {
        var editDisplayName by remember { mutableStateOf(profileUser?.displayName ?: "") }
        var editBio by remember { mutableStateOf(profileUser?.bio ?: "") }
        var editAvatarUrl by remember { mutableStateOf(profileUser?.avatarUrl ?: "") }

        Dialog(onDismissRequest = { showEditProfileDialog = false }) {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = TokDarkSurface),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, TokBorder, RoundedCornerShape(20.dp))
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "Edit Profile",
                        color = TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(14.dp))

                    Text("Display Name", color = TextMuted, fontSize = 12.sp)
                    OutlinedTextField(
                        value = editDisplayName,
                        onValueChange = { editDisplayName = it },
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedBorderColor = TokCyan,
                            unfocusedBorderColor = TokBorder
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(10.dp))
                    Text("Bio", color = TextMuted, fontSize = 12.sp)
                    OutlinedTextField(
                        value = editBio,
                        onValueChange = { editBio = it },
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedBorderColor = TokCyan,
                            unfocusedBorderColor = TokBorder
                        ),
                        minLines = 2,
                        maxLines = 4,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(10.dp))
                    Text("Avatar Image URL", color = TextMuted, fontSize = 12.sp)
                    OutlinedTextField(
                        value = editAvatarUrl,
                        onValueChange = { editAvatarUrl = it },
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedBorderColor = TokCyan,
                            unfocusedBorderColor = TokBorder
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(18.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Button(
                            onClick = { showEditProfileDialog = false },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent)
                        ) {
                            Text("Cancel", color = TextSecondary)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                scope.launch {
                                    repository.updateProfile(editDisplayName, editBio, editAvatarUrl)
                                    showEditProfileDialog = false
                                    Toast.makeText(context, "Profile updated!", Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = TokCyan),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Save", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    // Account Deletion Dialog
    if (showDeleteAccountDialog) {
        var deletionReason by remember { mutableStateOf("") }
        Dialog(onDismissRequest = { showDeleteAccountDialog = false }) {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = TokDarkSurface),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, TokBorder, RoundedCornerShape(20.dp))
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "Request Account Deletion",
                        color = StatusBanned,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Under GDPR & CCPA privacy regulations, submitting this request schedules your personal information, videos, and interactions for permanent deletion. Our data compliance officer will process your request.",
                        color = TextSecondary,
                        fontSize = 12.5.sp,
                        lineHeight = 17.sp
                    )

                    Spacer(modifier = Modifier.height(14.dp))
                    OutlinedTextField(
                        value = deletionReason,
                        onValueChange = { deletionReason = it },
                        placeholder = { Text("Reason for deletion (optional)...", color = TextMuted, fontSize = 12.sp) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedBorderColor = StatusBanned,
                            unfocusedBorderColor = TokBorder
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(18.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Button(
                            onClick = { showDeleteAccountDialog = false },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent)
                        ) {
                            Text("Cancel", color = TextSecondary)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                scope.launch {
                                    repository.requestAccountDeletion(deletionReason)
                                    showDeleteAccountDialog = false
                                    Toast.makeText(context, "Account deletion request submitted for moderation review.", Toast.LENGTH_LONG).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = StatusBanned),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Confirm Request", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileStat(label: String, count: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = formatCount(count),
            color = TextPrimary,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            color = TextMuted,
            fontSize = 11.5.sp
        )
    }
}

private fun formatCount(count: Int): String {
    return when {
        count >= 1_000_000 -> String.format("%.1fM", count / 1_000_000.0)
        count >= 1_000 -> String.format("%.1fK", count / 1_000.0)
        else -> count.toString()
    }
}
