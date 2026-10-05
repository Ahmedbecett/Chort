package com.example.ui.components

import android.net.Uri
import android.util.Log
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.platform.testTag
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import com.example.BuildConfig
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.example.ui.theme.TokCyan
import com.example.ui.theme.TokRed

import com.example.data.remote.TokPulseApiClient

private const val TAG = "VideoPlayerView"

@OptIn(UnstableApi::class)
@Composable
fun VideoPlayerView(
    videoUrl: String,
    thumbnailUrl: String,
    isCurrentPage: Boolean,
    videoId: String? = null,
    isMuted: Boolean = false,
    onToggleMute: (() -> Unit)? = null,
    onRetry: (() -> Unit)? = null,
    onSkip: (() -> Unit)? = null,
    onDoubleTap: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    var isPlaying by remember { mutableStateOf(true) }
    var isBuffering by remember { mutableStateOf(true) }
    var isPrepared by remember { mutableStateOf(false) }
    var hasError by remember { mutableStateOf(false) }
    var lastTechnicalError by remember { mutableStateOf<String?>(null) }
    var retryTrigger by remember { mutableIntStateOf(0) }
    var refreshAttempted by remember(videoUrl, retryTrigger) { mutableStateOf(false) }

    // Resolve videoId from prop or URL
    val effectiveVideoId = remember(videoId, videoUrl) {
        if (!videoId.isNullOrBlank()) videoId
        else {
            val match = Regex("vid_[a-zA-Z0-9]+").find(videoUrl)
            match?.value
        }
    }

    // Media3 ExoPlayer instance with AudioFocus, high-quality audio decoding and fast buffering
    val exoPlayer = remember(context, retryTrigger) {
        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
            .setUsage(C.USAGE_MEDIA)
            .build()

        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs = */ 1500,
                /* maxBufferMs = */ 10000,
                /* bufferForPlaybackMs = */ 500,
                /* bufferForPlaybackAfterRebufferMs = */ 1000
            )
            .build()

        val httpDataSourceFactory = androidx.media3.datasource.DefaultHttpDataSource.Factory()
            .setConnectTimeoutMs(15000)
            .setReadTimeoutMs(25000)
            .setAllowCrossProtocolRedirects(true)
            .setKeepPostFor302Redirects(true)
            .setUserAgent("Chort-Android/${BuildConfig.VERSION_NAME} (Linux; Android)")

        val dataSourceFactory = androidx.media3.datasource.DefaultDataSource.Factory(context, httpDataSourceFactory)
        val mediaSourceFactory = androidx.media3.exoplayer.source.DefaultMediaSourceFactory(dataSourceFactory)

        ExoPlayer.Builder(context)
            .setAudioAttributes(audioAttributes, /* handleAudioFocus = */ true)
            .setHandleAudioBecomingNoisy(true)
            .setLoadControl(loadControl)
            .setMediaSourceFactory(mediaSourceFactory)
            .build().apply {
                repeatMode = Player.REPEAT_MODE_ONE
                videoScalingMode = C.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING
                volume = if (isMuted) 0f else 1.0f

                addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        when (playbackState) {
                            Player.STATE_BUFFERING -> {
                                isBuffering = true
                            }
                            Player.STATE_READY -> {
                                isBuffering = false
                                isPrepared = true
                                hasError = false
                                lastTechnicalError = null
                            }
                            Player.STATE_ENDED -> {
                                isBuffering = false
                            }
                            Player.STATE_IDLE -> {
                                // idle
                            }
                        }
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        val detail = "Playback Error (${error.errorCodeName} / ${error.errorCode}): ${error.message ?: "Failed to decode/stream media"}"
                        Log.e(TAG, detail, error)
                        lastTechnicalError = detail

                        // If signed URL expired (403, 404, 416 or HTTP status error), auto-request refreshed signed stream URL
                        val isHttpStatusOrExpired = error.errorCode == PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS
                            || error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED
                            || (error.message?.contains("403") == true)
                            || (error.message?.contains("404") == true)
                            || (error.message?.contains("416") == true)

                        if (isHttpStatusOrExpired && effectiveVideoId != null && !refreshAttempted) {
                            refreshAttempted = true
                            isBuffering = true
                            hasError = false
                            val refreshedStreamUrl = TokPulseApiClient.getCanonicalStreamUrl(effectiveVideoId)
                            Log.i(TAG, "Refreshing stream URL via $refreshedStreamUrl")
                            try {
                                val mediaItem = MediaItem.Builder()
                                    .setUri(Uri.parse(refreshedStreamUrl))
                                    .setMimeType(androidx.media3.common.MimeTypes.APPLICATION_MP4)
                                    .build()
                                setMediaItem(mediaItem)
                                prepare()
                                play()
                                return
                            } catch (e: Exception) {
                                Log.w(TAG, "Failed to refresh stream URL: ${e.message}")
                            }
                        }

                        isBuffering = false
                        hasError = true
                        isPrepared = false
                    }
                })

                val trimmedUrl = videoUrl.trim()
                val isInvalidDummyHost = trimmedUrl.contains("test.com") || trimmedUrl.contains("example.com")
                if (trimmedUrl.isNotBlank() && !isInvalidDummyHost && (trimmedUrl.startsWith("http://") || trimmedUrl.startsWith("https://"))) {
                    try {
                        val isHls = trimmedUrl.contains(".m3u8") || trimmedUrl.contains("hls")
                        val mediaItem = MediaItem.Builder()
                            .setUri(Uri.parse(trimmedUrl))
                            .apply {
                                if (isHls) {
                                    setMimeType(androidx.media3.common.MimeTypes.APPLICATION_M3U8)
                                } else {
                                    setMimeType(androidx.media3.common.MimeTypes.APPLICATION_MP4)
                                }
                            }
                            .build()
                        setMediaItem(mediaItem)
                        prepare()
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to setMediaItem: ${e.message}", e)
                        lastTechnicalError = "Setup error: ${e.message}"
                        hasError = true
                        isBuffering = false
                    }
                } else if (effectiveVideoId != null) {
                    val canonicalStream = TokPulseApiClient.getCanonicalStreamUrl(effectiveVideoId)
                    val mediaItem = MediaItem.Builder()
                        .setUri(Uri.parse(canonicalStream))
                        .setMimeType(androidx.media3.common.MimeTypes.APPLICATION_MP4)
                        .build()
                    setMediaItem(mediaItem)
                    prepare()
                } else {
                    lastTechnicalError = "Invalid or empty video URL"
                    hasError = true
                    isBuffering = false
                }
            }
    }

    // Dynamic Volume & Mute control
    LaunchedEffect(isMuted, exoPlayer) {
        exoPlayer.volume = if (isMuted) 0f else 1.0f
    }

    // Playback state synchronization: play only when visible and user hasn't paused
    LaunchedEffect(isCurrentPage, isPlaying, exoPlayer) {
        if (isCurrentPage && isPlaying) {
            exoPlayer.playWhenReady = true
        } else {
            exoPlayer.playWhenReady = false
            if (!isCurrentPage) {
                exoPlayer.pause()
            }
        }
    }

    DisposableEffect(exoPlayer) {
        onDispose {
            try {
                exoPlayer.stop()
                exoPlayer.release()
            } catch (e: Exception) {
                Log.w(TAG, "Error disposing ExoPlayer: ${e.message}")
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(isPrepared, hasError) {
                detectTapGestures(
                    onDoubleTap = {
                        onDoubleTap()
                    },
                    onTap = {
                        if (!isPrepared || hasError) return@detectTapGestures
                        isPlaying = !isPlaying
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        // Thumbnail preview backdrop for immediate visual feedback
        if (thumbnailUrl.isNotBlank()) {
            AsyncImage(
                model = thumbnailUrl,
                contentDescription = "Video Backdrop",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }

        // Media3 PlayerView with full-screen zoom aspect ratio
        key(retryTrigger) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        useController = false
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                        player = exoPlayer
                    }
                },
                update = { playerView ->
                    if (playerView.player != exoPlayer) {
                        playerView.player = exoPlayer
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        // Buffering loader
        if (isBuffering && isCurrentPage && !hasError) {
            CircularProgressIndicator(
                color = TokCyan,
                strokeWidth = 3.dp,
                modifier = Modifier.size(44.dp)
            )
        }

        // Error with Real Technical Diagnostics and Real Retry / Skip
        if (hasError && isCurrentPage) {
            Column(
                modifier = Modifier
                    .padding(horizontal = 28.dp)
                    .background(Color.Black.copy(alpha = 0.85f), RoundedCornerShape(16.dp))
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "Playback Error",
                    tint = TokRed,
                    modifier = Modifier.size(36.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "تعذر تشغيل الفيديو",
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = lastTechnicalError ?: "Playback could not connect to video stream",
                    color = Color.White.copy(alpha = 0.75f),
                    fontSize = 11.5.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    maxLines = 3,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(14.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = {
                            hasError = false
                            isBuffering = true
                            refreshAttempted = false
                            onRetry?.invoke()
                            retryTrigger++
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = TokCyan),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.testTag("player_retry_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Retry",
                            tint = Color.Black,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.size(6.dp))
                        Text(text = "Retry", color = Color.Black, fontWeight = FontWeight.Bold)
                    }

                    if (onSkip != null) {
                        androidx.compose.material3.OutlinedButton(
                            onClick = {
                                hasError = false
                                onSkip.invoke()
                            },
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier.testTag("player_skip_button")
                        ) {
                            Text(text = "Skip", color = Color.White, fontSize = 13.sp)
                        }
                    }
                }
            }
        }

        // Pause indicator overlay
        AnimatedVisibility(
            visible = !isPlaying && !isBuffering && !hasError,
            enter = fadeIn() + scaleIn(initialScale = 0.7f),
            exit = fadeOut() + scaleOut(targetScale = 0.7f)
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .background(Color.Black.copy(alpha = 0.55f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Paused",
                    tint = Color.White.copy(alpha = 0.9f),
                    modifier = Modifier.size(48.dp)
                )
            }
        }
    }
}
