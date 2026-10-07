package com.example.ui.screens.inbox

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Comment
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.local.entities.NotificationEntity
import com.example.data.repository.ZevoraRepository
import com.example.util.AppPrefs
import com.example.ui.components.StoriesRow
import com.example.ui.theme.AccentGold
import com.example.ui.theme.AccentGreen
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.ZevoraBorder
import com.example.ui.theme.ZevoraCyan
import com.example.ui.theme.ZevoraDarkBg
import com.example.ui.theme.ZevoraDarkElevated
import com.example.ui.theme.ZevoraDarkSurface
import com.example.ui.theme.ZevoraRed
import kotlinx.coroutines.launch

/**
 * Inbox: stories on top, live activity (likes, comments, follows)
 * synced from the Rivo server below. No placeholder content.
 */
@Composable
fun InboxScreen(
    repository: ZevoraRepository,
    onNavigateToProfile: (String) -> Unit,
    onNavigateToSearch: () -> Unit = {},
    onNavigateToCreate: () -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    val currentUser by repository.currentUser.collectAsState()
    val userId = currentUser?.id ?: "user_me"
    val notifications by repository.getNotifications(userId).collectAsState(initial = emptyList())
    val unreadCount by repository.getUnreadCount(userId).collectAsState(initial = 0)
    val followingIds by repository.getFollowingIds(userId).collectAsState(initial = emptyList())

    var selectedFilter by remember { mutableStateOf("All") }
    var isSyncing by remember { mutableStateOf(false) }
    var syncError by remember { mutableStateOf<String?>(null) }

    fun sync() {
        if (isSyncing) return
        isSyncing = true
        syncError = null
        scope.launch {
            val result = repository.syncRemoteNotifications()
            isSyncing = false
            if (result.isFailure) {
                syncError = result.exceptionOrNull()?.message
            }
        }
    }

    LaunchedEffect(userId) {
        // Push master switch: off = local inbox only, no background sync.
        if (AppPrefs.isPushEnabled()) sync()
    }

    val filteredNotifications = remember(notifications, selectedFilter) {
        val base = when (selectedFilter) {
            "Likes" -> notifications.filter { it.type == "like" }
            "Comments" -> notifications.filter { it.type == "comment" }
            "Followers" -> notifications.filter { it.type == "follow" }
            "System" -> notifications.filter { it.type == "system" }
            else -> notifications
        }
        // Real enforcement of Settings → Notifications + Mentions choices.
        base.filter { n ->
            when (n.type) {
                "like" -> AppPrefs.isNotifTypeEnabled("like")
                "comment" -> AppPrefs.isNotifTypeEnabled("comment")
                "follow" -> AppPrefs.isNotifTypeEnabled("follow")
                "mention" -> {
                    if (!AppPrefs.isNotifTypeEnabled("mention")) false
                    else when (AppPrefs.getMentionMode()) {
                        "followers" -> n.actorId in followingIds
                        else -> true
                    }
                }
                else -> AppPrefs.isNotifTypeEnabled("system")
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ZevoraDarkBg)
            .statusBarsPadding()
            .testTag("inbox_screen")
    ) {
        // Header: centered title + search + mark-read
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = {
                    scope.launch { repository.markRemoteNotificationsRead() }
                }
            ) {
                Icon(
                    imageVector = Icons.Default.DoneAll,
                    contentDescription = "Mark all as read",
                    tint = ZevoraCyan,
                    modifier = Modifier.size(22.dp)
                )
            }
            Text(
                text = "Inbox",
                color = TextPrimary,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onNavigateToSearch) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search",
                    tint = TextPrimary,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        StoriesRow(
            repository = repository,
            onCreateStory = onNavigateToCreate,
            onStoryClick = { user -> onNavigateToProfile(user.id) },
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        // Activity header row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = if (unreadCount > 0) "Activity ($unreadCount new)" else "Activity",
                color = TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
            if (isSyncing) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        color = ZevoraCyan,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = "Syncing…", color = TextMuted, fontSize = 11.sp)
                }
            } else if (syncError != null && notifications.isEmpty()) {
                Text(
                    text = "Tap to retry",
                    color = ZevoraCyan,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable { sync() }
                )
            }
        }

        // Filter Categories
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val categories = listOf("All", "Likes", "Comments", "Followers", "System")
            items(categories) { cat ->
                FilterChip(
                    selected = (selectedFilter == cat),
                    onClick = { selectedFilter = cat },
                    label = { Text(cat, fontSize = 12.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = ZevoraRed,
                        selectedLabelColor = Color.White,
                        containerColor = ZevoraDarkElevated,
                        labelColor = TextSecondary
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        borderColor = ZevoraBorder,
                        selectedBorderColor = ZevoraRed,
                        enabled = true,
                        selected = selectedFilter == cat
                    ),
                    shape = RoundedCornerShape(16.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (filteredNotifications.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 72.dp)
                    .clickable { sync() },
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Notifications,
                        contentDescription = "Empty",
                        tint = TextMuted,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "No notifications yet",
                        color = TextSecondary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = if (syncError != null) {
                            (syncError ?: "Couldn't reach the server.") + " Tap to retry."
                        } else {
                            "When people like, comment or follow you, it'll appear here"
                        },
                        color = TextMuted,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 32.dp)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 72.dp)
            ) {
                items(filteredNotifications, key = { it.id }) { notif ->
                    NotificationCard(
                        notification = notif,
                        onActorClick = { onNavigateToProfile(notif.actorId) }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun NotificationCard(
    notification: NotificationEntity,
    onActorClick: () -> Unit
) {
    val (typeIcon, iconColor) = when (notification.type) {
        "like" -> Pair(Icons.Default.Favorite, ZevoraRed)
        "comment" -> Pair(Icons.Default.Comment, ZevoraCyan)
        "follow" -> Pair(Icons.Default.PersonAdd, AccentGreen)
        else -> Pair(Icons.Default.Security, AccentGold)
    }

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (!notification.isRead) ZevoraDarkElevated else ZevoraDarkSurface
        ),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = if (!notification.isRead) 1.dp else 0.5.dp,
                color = if (!notification.isRead) ZevoraCyan.copy(alpha = 0.5f) else ZevoraBorder,
                shape = RoundedCornerShape(14.dp)
            )
            .clickable { onActorClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(contentAlignment = Alignment.BottomEnd) {
                AsyncImage(
                    model = notification.actorAvatar,
                    contentDescription = notification.actorUsername,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                )

                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(iconColor),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = typeIcon,
                        contentDescription = notification.type,
                        tint = Color.White,
                        modifier = Modifier.size(11.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "@${notification.actorUsername}",
                        color = TextPrimary,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                    if (!notification.isRead) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(ZevoraRed)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = notification.message,
                    color = TextSecondary,
                    fontSize = 12.5.sp,
                    lineHeight = 16.sp
                )
            }
        }
    }
}
