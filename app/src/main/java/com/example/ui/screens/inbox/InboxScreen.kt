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
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.local.entities.NotificationEntity
import com.example.data.repository.TokPulseRepository
import com.example.ui.theme.AccentGold
import com.example.ui.theme.AccentGreen
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
fun InboxScreen(
    repository: TokPulseRepository,
    onNavigateToProfile: (String) -> Unit,
    onNavigateToChat: (String) -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    val currentUser by repository.currentUser.collectAsState()
    val notifications by repository.getNotifications(currentUser?.id ?: "user_me").collectAsState(initial = emptyList())
    val unreadCount by repository.getUnreadCount(currentUser?.id ?: "user_me").collectAsState(initial = 0)

    var mainTab by remember { mutableStateOf("Activity") } // "Activity" or "Messages"
    var selectedFilter by remember { mutableStateOf("All") } // "All", "Likes", "Comments", "Followers", "System"

    val filteredNotifications = remember(notifications, selectedFilter) {
        when (selectedFilter) {
            "Likes" -> notifications.filter { it.type == "like" }
            "Comments" -> notifications.filter { it.type == "comment" }
            "Followers" -> notifications.filter { it.type == "follow" }
            "System" -> notifications.filter { it.type == "system" }
            else -> notifications
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TokDarkBg)
            .statusBarsPadding()
            .testTag("inbox_screen")
    ) {
        // Top Header with Tabs: Activity | Direct Messages
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Activity",
                    color = if (mainTab == "Activity") TextPrimary else TextMuted,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable { mainTab = "Activity" }
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = "Messages (3)",
                    color = if (mainTab == "Messages") TokCyan else TextMuted,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable { mainTab = "Messages" }
                )
            }

            IconButton(
                onClick = {
                    currentUser?.id?.let { uid ->
                        scope.launch { repository.markAllNotificationsRead(uid) }
                    }
                }
            ) {
                Icon(
                    imageVector = Icons.Default.DoneAll,
                    contentDescription = "Mark all as read",
                    tint = TokCyan,
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        if (mainTab == "Messages") {
            // Direct messages list
            val dms = listOf(
                Triple("alex_skates", "Alex Rivera", "Let's do a duet next week for the #skatechallenge! 🛹"),
                Triple("chef_elena", "Chef Elena", "Loved the recipe feedback, trying your suggestion! 🍳"),
                Triple("maya_beats", "Maya Lin", "Hey, your video is trending on the sound page! ✨")
            )
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(dms) { (handle, name, lastMsg) ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onNavigateToChat(handle) },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = TokDarkSurface),
                        border = androidx.compose.foundation.BorderStroke(1.dp, TokBorder)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(CircleShape)
                                    .background(TokDarkElevated),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(name.take(1), color = TokCyan, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(name, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text(lastMsg, color = TextSecondary, fontSize = 12.sp, maxLines = 1)
                            }
                            Text("Active", color = TokCyan, fontSize = 10.sp)
                        }
                    }
                }
            }
        } else {
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
                        selectedContainerColor = TokRed,
                        selectedLabelColor = Color.White,
                        containerColor = TokDarkElevated,
                        labelColor = TextSecondary
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        borderColor = TokBorder,
                        selectedBorderColor = TokRed,
                        enabled = true,
                        selected = selectedFilter == cat
                    ),
                    shape = RoundedCornerShape(16.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Notifications List
        if (filteredNotifications.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 60.dp),
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
                        text = "When people like, comment or follow you, it'll appear here",
                        color = TextMuted,
                        fontSize = 12.sp
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 60.dp)
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
}

@Composable
private fun NotificationCard(
    notification: NotificationEntity,
    onActorClick: () -> Unit
) {
    val (typeIcon, iconColor) = when (notification.type) {
        "like" -> Pair(Icons.Default.Favorite, TokRed)
        "comment" -> Pair(Icons.Default.Comment, TokCyan)
        "follow" -> Pair(Icons.Default.PersonAdd, AccentGreen)
        else -> Pair(Icons.Default.Security, AccentGold)
    }

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (!notification.isRead) TokDarkElevated else TokDarkSurface
        ),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = if (!notification.isRead) 1.dp else 0.5.dp,
                color = if (!notification.isRead) TokCyan.copy(alpha = 0.5f) else TokBorder,
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
            // Actor Avatar with badge
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
                                .background(TokRed)
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
