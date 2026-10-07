package com.example.ui.screens.offline

import android.net.Uri
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.example.ui.components.VideoPlayerView
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.ZevoraCyan
import com.example.ui.theme.ZevoraDarkBg
import com.example.ui.theme.ZevoraDarkSurface
import com.example.ui.theme.ZevoraRed

/**
 * ZEVORA 3.1.0 — offline vault: genuinely downloaded videos with real sizes,
 * in-app playback, per-video delete and clear-all.
 */
@Composable
fun OfflineVideosScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var videos by remember { mutableStateOf<List<OfflineVideo>>(emptyList()) }
    var usedBytes by remember { mutableStateOf(0L) }
    var playing by remember { mutableStateOf<OfflineVideo?>(null) }
    var confirmClear by remember { mutableStateOf(false) }

    fun refresh() {
        videos = OfflineStore.list(context)
        usedBytes = OfflineStore.totalBytes(context)
    }

    LaunchedEffect(Unit) { refresh() }

    Box(modifier = Modifier.fillMaxSize().background(ZevoraDarkBg)) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 48.dp, start = 8.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
                }
                Text("Offline videos", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                if (videos.isNotEmpty()) {
                    IconButton(onClick = { confirmClear = true }) {
                        Icon(Icons.Default.DeleteSweep, "Clear all", tint = Color.White)
                    }
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.CloudOff, null, tint = ZevoraCyan, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    "${videos.size} videos • ${formatBytes(usedBytes)} on this device",
                    color = TextSecondary,
                    fontSize = 13.sp
                )
            }
            if (videos.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(40.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(Icons.Default.CloudOff, null, tint = TextMuted, modifier = Modifier.size(52.dp))
                    Spacer(Modifier.height(12.dp))
                    Text("No offline videos", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Use “Save offline” on any video to watch it without internet.",
                        color = TextSecondary,
                        fontSize = 13.sp
                    )
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(videos, key = { it.id }) { video ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { playing = video }
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(72.dp)
                                    .aspectRatio(0.75f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(ZevoraDarkSurface),
                                contentAlignment = Alignment.Center
                            ) {
                                AsyncImage(
                                    model = Uri.fromFile(video.file),
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                                Box(
                                    modifier = Modifier
                                        .size(30.dp)
                                        .clip(androidx.compose.foundation.shape.CircleShape)
                                        .background(Color.Black.copy(alpha = 0.55f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.PlayArrow, null, tint = Color.White, modifier = Modifier.size(18.dp))
                                }
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    video.caption.ifBlank { "Untitled video" },
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    "@${video.creator.ifBlank { "unknown" }} • ${formatBytes(video.sizeBytes)}",
                                    color = TextMuted,
                                    fontSize = 12.sp,
                                    maxLines = 1
                                )
                            }
                            IconButton(onClick = {
                                if (OfflineStore.delete(context, video.id)) {
                                    refresh()
                                    Toast.makeText(context, "Deleted from device", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "Delete failed", Toast.LENGTH_SHORT).show()
                                }
                            }) {
                                Icon(Icons.Default.Delete, "Delete", tint = ZevoraRed)
                            }
                        }
                    }
                }
            }
        }

        playing?.let { video ->
            Box(
                modifier = Modifier.fillMaxSize().background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                VideoPlayerView(
                    videoUrl = Uri.fromFile(video.file).toString(),
                    thumbnailUrl = "",
                    isCurrentPage = true,
                    videoId = null,
                    modifier = Modifier.fillMaxSize()
                )
                IconButton(
                    onClick = { playing = null },
                    modifier = Modifier.align(Alignment.TopStart).padding(top = 44.dp, start = 8.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Close", tint = Color.White)
                }
            }
        }

        if (confirmClear) {
            AlertDialog(
                onDismissRequest = { confirmClear = false },
                title = { Text("Delete all offline videos?") },
                text = { Text("This frees ${formatBytes(usedBytes)} on this device. The online videos stay untouched.") },
                confirmButton = {
                    TextButton(onClick = {
                        val n = OfflineStore.clearAll(context)
                        confirmClear = false
                        refresh()
                        Toast.makeText(context, "Deleted $n videos", Toast.LENGTH_SHORT).show()
                    }) {
                        Text("Delete all", color = ZevoraRed, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { confirmClear = false }) { Text("Cancel") }
                }
            )
        }
    }
}
