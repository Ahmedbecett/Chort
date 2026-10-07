package com.example.ui.screens.activity

import android.widget.Toast
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.local.entities.VideoEntity
import com.example.data.repository.ZevoraRepository
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.ZevoraDarkBg
import com.example.ui.theme.ZevoraDarkSurface
import com.example.ui.theme.ZevoraRed
import com.example.util.AppPrefs
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * ZEVORA 3.1.0 — Activity center: liked videos (Room + server sync) and the
 * real on-device watch history. Clearing history wipes it for real.
 */
@Composable
fun ActivityCenterScreen(
    repository: ZevoraRepository,
    onSelectVideo: (VideoEntity) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentUser by repository.currentUser.collectAsState()
    val meId = currentUser?.id ?: ""

    var tab by remember { mutableStateOf(0) } // 0 liked, 1 watched
    var syncing by remember { mutableStateOf(false) }
    var history by remember { mutableStateOf(AppPrefs.getWatchHistory()) }

    val likedIds by repository.getUserLikedVideoIds(meId).collectAsState(initial = emptyList())
    val allVideos by repository.getActiveVideos().collectAsState(initial = emptyList())
    val likedVideos = remember(likedIds, allVideos) {
        val byId = allVideos.associateBy { it.id }
        likedIds.mapNotNull { byId[it] }
    }

    fun syncNow() {
        if (meId.isBlank() || syncing) return
        syncing = true
        scope.launch {
            try {
                val liked = repository.fetchLikedVideos(meId)
                val saved = repository.fetchSavedVideos(meId)
                history = AppPrefs.getWatchHistory()
                Toast.makeText(
                    context,
                    if (liked.isSuccess || saved.isSuccess) "Activity synced" else "Sync failed — showing offline data",
                    Toast.LENGTH_SHORT
                ).show()
            } finally {
                syncing = false
            }
        }
    }

    LaunchedEffect(meId) {
        if (meId.isNotBlank()) syncNow()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ZevoraDarkBg)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 48.dp, start = 8.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
            }
            Text("Activity center", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            if (tab == 1) {
                IconButton(onClick = {
                    AppPrefs.clearWatchHistory()
                    history = emptyList()
                    Toast.makeText(context, "Watch history cleared", Toast.LENGTH_SHORT).show()
                }) {
                    Icon(Icons.Default.DeleteSweep, "Clear history", tint = Color.White)
                }
            }
            IconButton(onClick = { syncNow() }, enabled = !syncing) {
                if (syncing) {
                    CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                } else {
                    Icon(Icons.Default.Refresh, "Sync", tint = Color.White)
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(28.dp)
        ) {
            ActivityTab("Liked", tab == 0) { tab = 0 }
            ActivityTab("Watch history", tab == 1) { tab = 1 }
        }

        if (tab == 0) {
            if (likedVideos.isEmpty()) {
                EmptyPanel(
                    icon = Icons.Default.Favorite,
                    title = "No liked videos yet",
                    subtitle = "Double-tap any video to like it — it will appear here."
                )
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(2.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    items(likedVideos, key = { it.id }) { video ->
                        Box(
                            modifier = Modifier
                                .aspectRatio(0.75f)
                                .clip(RoundedCornerShape(4.dp))
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
                        }
                    }
                }
            }
        } else {
            if (history.isEmpty()) {
                EmptyPanel(
                    icon = Icons.Default.History,
                    title = "No watch history",
                    subtitle = "Videos you watch will be listed here with their creators."
                )
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(history, key = { it.videoId + it.timestamp }) { entry ->
                        val video = allVideos.firstOrNull { it.id == entry.videoId }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = video != null) { video?.let(onSelectVideo) }
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(64.dp)
                                    .aspectRatio(0.75f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(ZevoraDarkSurface),
                                contentAlignment = Alignment.Center
                            ) {
                                if (video != null) {
                                    AsyncImage(
                                        model = video.thumbnailUrl.ifBlank { video.videoUrl },
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Icon(Icons.Default.History, null, tint = TextMuted)
                                }
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    (video?.caption ?: entry.caption).ifBlank { "Untitled video" },
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    "@" + (video?.creatorUsername ?: entry.creator).ifBlank { "unknown" },
                                    color = TextSecondary,
                                    fontSize = 12.sp,
                                    maxLines = 1
                                )
                                Text(
                                    SimpleDateFormat("MMM d, HH:mm", Locale.US).format(Date(entry.timestamp)) +
                                        if (video == null) " • no longer available" else "",
                                    color = TextMuted,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ActivityTab(label: String, active: Boolean, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            label,
            color = if (active) Color.White else TextMuted,
            fontSize = 15.sp,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium
        )
        Spacer(Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .width(26.dp)
                .height(2.5.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(if (active) Color.White else Color.Transparent)
        )
    }
}

@Composable
private fun EmptyPanel(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(icon, null, tint = TextMuted, modifier = Modifier.size(52.dp))
        Spacer(Modifier.height(12.dp))
        Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Spacer(Modifier.height(6.dp))
        Text(subtitle, color = TextSecondary, fontSize = 13.sp)
    }
}

fun compactCount(n: Int): String = when {
    n >= 1_000_000 -> "%.1fM".format(n / 1_000_000f)
    n >= 1_000 -> "%.1fK".format(n / 1_000f)
    else -> "$n"
}
