package com.example.ui.screens.sound

import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VideoCameraBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import coil.compose.AsyncImage
import com.example.data.repository.TokPulseRepository
import com.example.ui.theme.AccentGold
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TokBorder
import com.example.ui.theme.TokCyan
import com.example.ui.theme.TokDarkBg
import com.example.ui.theme.TokDarkElevated
import com.example.ui.theme.TokRed

@Composable
fun SoundDetailScreen(
    soundTitle: String,
    repository: TokPulseRepository,
    onBack: () -> Unit,
    onUseSound: () -> Unit,
    onSelectVideo: (String) -> Unit
) {
    val context = LocalContext.current
    val allVideos by repository.getActiveVideos().collectAsState(initial = emptyList())
    val matchingVideos = remember(allVideos, soundTitle) {
        val filtered = allVideos.filter {
            it.musicTitle.contains(soundTitle, ignoreCase = true) ||
            soundTitle.contains(it.musicTitle, ignoreCase = true)
        }
        if (filtered.isNotEmpty()) filtered else allVideos
    }

    var isPlaying by remember { mutableStateOf(true) }
    var isBookmarked by remember { mutableStateOf(false) }

    // Real audio player with Media3 ExoPlayer for sound preview
    val audioPlayer = remember(context, matchingVideos) {
        val streamUrl = matchingVideos.firstOrNull {
            val url = it.videoUrl.trim()
            url.isNotBlank() &&
                !url.contains("test.com") &&
                !url.contains("example.com") &&
                (url.startsWith("http://") || url.startsWith("https://"))
        }?.videoUrl

        if (!streamUrl.isNullOrBlank()) {
            val audioAttributes = AudioAttributes.Builder()
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .setUsage(C.USAGE_MEDIA)
                .build()

            try {
                ExoPlayer.Builder(context)
                    .setAudioAttributes(audioAttributes, true)
                    .build().apply {
                        addListener(object : Player.Listener {
                            override fun onPlayerError(error: PlaybackException) {
                                isPlaying = false
                            }
                        })
                        val item = MediaItem.fromUri(Uri.parse(streamUrl))
                        setMediaItem(item)
                        repeatMode = Player.REPEAT_MODE_ONE
                        volume = 1.0f
                        prepare()
                    }
            } catch (e: Exception) {
                null
            }
        } else {
            null
        }
    }

    LaunchedEffect(isPlaying, audioPlayer) {
        if (isPlaying) {
            audioPlayer?.playWhenReady = true
        } else {
            audioPlayer?.playWhenReady = false
            audioPlayer?.pause()
        }
    }

    DisposableEffect(audioPlayer) {
        onDispose {
            try {
                audioPlayer?.stop()
                audioPlayer?.release()
            } catch (_: Exception) {}
        }
    }

    // Vinyl spinning rotation
    val infiniteTransition = rememberInfiniteTransition(label = "vinyl_rotation")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation_anim"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TokDarkBg)
            .statusBarsPadding()
    ) {
        // TOP BAR
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = TextPrimary
                )
            }
            Text(
                text = "Audio Track",
                color = TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
            IconButton(onClick = { Toast.makeText(context, "Sound link copied!", Toast.LENGTH_SHORT).show() }) {
                Icon(
                    imageVector = Icons.Default.Share,
                    contentDescription = "Share",
                    tint = TextPrimary
                )
            }
        }

        // VINYL & SOUND METADATA
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Spinning Vinyl Disc
            Box(
                modifier = Modifier
                    .size(90.dp)
                    .clip(CircleShape)
                    .background(Color.Black)
                    .border(3.dp, TokDarkElevated, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .rotate(if (isPlaying) rotation else 0f)
                        .clip(CircleShape)
                        .background(Color(0xFF1E1E1E)),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(TokRed),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.MusicNote,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // Play / Pause overlay
                IconButton(
                    onClick = { isPlaying = !isPlaying },
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.5f))
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "Play/Pause",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = soundTitle,
                    color = TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "thileli dz Verified Audio • ${if (isPlaying) "Playing now" else "Tap play to listen"}",
                    color = TokCyan,
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.GraphicEq,
                        contentDescription = null,
                        tint = AccentGold,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "${matchingVideos.size} videos created",
                        color = TextMuted,
                        fontSize = 11.5.sp
                    )
                }
            }
        }

        // ACTION BUTTONS
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = onUseSound,
                colors = ButtonDefaults.buttonColors(containerColor = TokRed),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.VideoCameraBack,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Use this sound", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
            }

            OutlinedButton(
                onClick = {
                    isBookmarked = !isBookmarked
                    Toast.makeText(
                        context,
                        if (isBookmarked) "Added to Sound Favorites!" else "Removed from Favorites",
                        Toast.LENGTH_SHORT
                    ).show()
                },
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = if (isBookmarked) AccentGold else TextPrimary
                ),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (isBookmarked) AccentGold else TokBorder
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.height(44.dp)
            ) {
                Icon(
                    imageVector = if (isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                    contentDescription = "Save sound",
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(if (isBookmarked) "Saved" else "Save", fontSize = 13.sp)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // VIDEOS USING THIS SOUND
        Text(
            text = "Videos with this Audio (${matchingVideos.size})",
            color = TextPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
        )

        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            items(matchingVideos) { video ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                        .background(TokDarkElevated)
                        .clickable { onSelectVideo(video.id) }
                ) {
                    if (video.thumbnailUrl.isNotBlank()) {
                        AsyncImage(
                            model = video.thumbnailUrl,
                            contentDescription = video.caption,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    // Views tag
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(4.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color.Black.copy(alpha = 0.6f))
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(10.dp)
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                text = "${video.viewsCount}",
                                color = Color.White,
                                fontSize = 9.sp
                            )
                        }
                    }
                }
            }
        }
    }
}
