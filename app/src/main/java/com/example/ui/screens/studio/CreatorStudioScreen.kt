package com.example.ui.screens.studio

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.repository.ZevoraRepository
import com.example.ui.screens.activity.compactCount
import com.example.ui.theme.AccentGold
import com.example.ui.theme.AccentGreen
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.ZevoraCyan
import com.example.ui.theme.ZevoraDarkBg
import com.example.ui.theme.ZevoraDarkSurface
import com.example.ui.theme.ZevoraRed
import com.example.util.AppPrefs

/**
 * Rivo 3.1.0 — Creator Studio: genuine analytics computed from the local
 * database (synced with the server): totals, engagement rate, top video and
 * a per-video breakdown. Zeros are real zeros, never placeholders.
 */
@Composable
fun CreatorStudioScreen(
    repository: ZevoraRepository,
    onBack: () -> Unit
) {
    val currentUser by repository.currentUser.collectAsState()
    val meId = currentUser?.id ?: ""
    val myVideos by repository.getVideosByCreator(meId).collectAsState(initial = emptyList())
    val followers by repository.getFollowersCount(meId).collectAsState(initial = 0)
    val following by repository.getFollowingCount(meId).collectAsState(initial = 0)
    val coins by AppPrefs.coins.collectAsState()

    val totalViews = remember(myVideos) { myVideos.sumOf { it.viewsCount } }
    val totalLikes = remember(myVideos) { myVideos.sumOf { it.likesCount } }
    val totalComments = remember(myVideos) { myVideos.sumOf { it.commentsCount } }
    val totalShares = remember(myVideos) { myVideos.sumOf { it.sharesCount } }
    val engagement = remember(totalViews, totalLikes, totalComments, totalShares) {
        if (totalViews <= 0) 0f
        else ((totalLikes + totalComments + totalShares).toFloat() / totalViews * 100)
    }
    val topVideo = remember(myVideos) { myVideos.maxByOrNull { it.viewsCount } }
    val ranked = remember(myVideos) { myVideos.sortedByDescending { it.viewsCount } }

    Column(modifier = Modifier.fillMaxSize().background(ZevoraDarkBg)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 48.dp, start = 8.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
            }
            Text("Rivo Studio", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    StatCard(Icons.Default.PlayArrow, "Views", compactCount(totalViews), ZevoraCyan, Modifier.weight(1f))
                    StatCard(Icons.Default.Favorite, "Likes", compactCount(totalLikes), ZevoraRed, Modifier.weight(1f))
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    StatCard(Icons.Default.Groups, "Followers", compactCount(followers), AccentGold, Modifier.weight(1f))
                    StatCard(Icons.Default.VideoLibrary, "Videos", "${myVideos.size}", AccentGreen, Modifier.weight(1f))
                }
                // engagement
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(ZevoraDarkSurface)
                        .padding(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Analytics, null, tint = ZevoraCyan)
                        Spacer(Modifier.width(8.dp))
                        Text("Engagement rate", color = Color.White, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.weight(1f))
                        Text(
                            "%.1f%%".format(engagement),
                            color = ZevoraCyan,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 18.sp
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    LinearProgressIndicator(
                        progress = { (engagement / 100f).coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = ZevoraCyan,
                        trackColor = Color.White.copy(alpha = 0.12f)
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "$totalComments comments • $totalShares shares • $totalViews views",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                }
                if (topVideo != null) {
                    TopVideoCard(
                        thumbnail = topVideo.thumbnailUrl.ifBlank { topVideo.videoUrl },
                        caption = topVideo.caption,
                        views = topVideo.viewsCount,
                        likes = topVideo.likesCount
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(ZevoraDarkSurface)
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.RocketLaunch, null, tint = AccentGold)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Creator balance", color = Color.White, fontWeight = FontWeight.Bold)
                        Text("$coins coins available for Boosts", color = TextSecondary, fontSize = 12.sp)
                    }
                }
                Text(
                    "All videos (${ranked.size})",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    modifier = Modifier.padding(start = 20.dp, top = 8.dp, bottom = 4.dp)
                )
            }
            if (ranked.isEmpty()) {
                item {
                    Text(
                        "Publish your first video to unlock analytics.",
                        color = TextMuted,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)
                    )
                }
            }
            items(ranked, key = { it.id }) { video ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(ZevoraDarkSurface)
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AsyncImage(
                        model = video.thumbnailUrl.ifBlank { video.videoUrl },
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .width(56.dp)
                            .aspectRatio(0.75f)
                            .clip(RoundedCornerShape(8.dp))
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            video.caption.ifBlank { "Untitled" },
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "${compactCount(video.viewsCount)} views • ${compactCount(video.likesCount)} likes • ${video.commentsCount} comments",
                            color = TextSecondary,
                            fontSize = 11.sp,
                            maxLines = 1
                        )
                    }
                    if (AppPrefs.isBoosted(video.id)) {
                        Text(
                            "BOOSTED",
                            color = Color.Black,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(AccentGold)
                                .padding(horizontal = 6.dp, vertical = 3.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    tint: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(ZevoraDarkSurface)
            .padding(14.dp)
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.padding(bottom = 6.dp))
        Text(value, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
        Text(label, color = TextSecondary, fontSize = 12.sp)
    }
}

@Composable
private fun TopVideoCard(thumbnail: String, caption: String, views: Int, likes: Int) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(ZevoraDarkSurface)
            .padding(16.dp)
    ) {
        Text("Top performing video", color = AccentGold, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = thumbnail,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .width(64.dp)
                    .aspectRatio(0.75f)
                    .clip(RoundedCornerShape(10.dp))
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    caption.ifBlank { "Untitled" },
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    maxLines = 2
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "$views views • $likes likes",
                    color = TextSecondary,
                    fontSize = 12.sp
                )
            }
        }
    }
}
