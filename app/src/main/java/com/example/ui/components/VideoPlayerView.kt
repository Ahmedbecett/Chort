package com.example.ui.components

import android.graphics.SurfaceTexture
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.util.Log
import android.view.Surface
import android.view.TextureView
import android.widget.FrameLayout
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import com.example.ui.theme.TokCyan
import com.example.ui.theme.TokRed

@Composable
fun VideoPlayerView(
    videoUrl: String,
    thumbnailUrl: String,
    isCurrentPage: Boolean,
    onDoubleTap: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val TAG = "VideoPlayerView"

    var isPlaying by remember { mutableStateOf(true) }
    var isBuffering by remember { mutableStateOf(true) }
    var isPrepared by remember { mutableStateOf(false) }
    var hasError by remember { mutableStateOf(false) }
    var retryTrigger by remember { mutableIntStateOf(0) }

    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var surfaceRef by remember { mutableStateOf<Surface?>(null) }

    // Playback state synchronization: ONLY call start/pause when MediaPlayer is in Prepared state
    LaunchedEffect(isCurrentPage, isPlaying, isPrepared) {
        if (!isPrepared) return@LaunchedEffect
        mediaPlayer?.let { mp ->
            try {
                if (isCurrentPage && isPlaying) {
                    if (!mp.isPlaying) {
                        mp.start()
                    }
                } else {
                    if (mp.isPlaying) {
                        mp.pause()
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Safe playback transition handled: ${e.message}")
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            isPrepared = false
            try {
                mediaPlayer?.let { mp ->
                    try {
                        if (mp.isPlaying) {
                            mp.stop()
                        }
                    } catch (e: Exception) {}
                    mp.reset()
                    mp.release()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Safe disposal error: ${e.message}")
            }
            mediaPlayer = null
            try {
                surfaceRef?.release()
            } catch (e: Exception) {}
            surfaceRef = null
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
                        mediaPlayer?.let { mp ->
                            try {
                                if (isPlaying) {
                                    if (mp.isPlaying) mp.pause()
                                    isPlaying = false
                                } else {
                                    mp.start()
                                    isPlaying = true
                                }
                            } catch (e: Exception) {
                                isPlaying = !isPlaying
                            }
                        }
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        // Thumbnail preview backdrop
        if (thumbnailUrl.isNotBlank()) {
            AsyncImage(
                model = thumbnailUrl,
                contentDescription = "Video Backdrop",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }

        // TextureView player
        key(retryTrigger) {
            AndroidView(
                factory = { ctx ->
                    TextureView(ctx).apply {
                        layoutParams = FrameLayout.LayoutParams(
                            FrameLayout.LayoutParams.MATCH_PARENT,
                            FrameLayout.LayoutParams.MATCH_PARENT
                        )

                        surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                            override fun onSurfaceTextureAvailable(st: SurfaceTexture, width: Int, height: Int) {
                                val surface = Surface(st)
                                surfaceRef = surface

                                val mp = MediaPlayer().apply {
                                    setSurface(surface)
                                    setAudioAttributes(
                                        AudioAttributes.Builder()
                                            .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                                            .setUsage(AudioAttributes.USAGE_MEDIA)
                                            .build()
                                    )
                                    isLooping = true

                                    setOnPreparedListener { player ->
                                        isPrepared = true
                                        isBuffering = false
                                        hasError = false
                                        try {
                                            if (isCurrentPage && isPlaying) {
                                                player.start()
                                            }
                                        } catch (e: Exception) {
                                            Log.w(TAG, "Initial start error handled: ${e.message}")
                                        }
                                    }

                                    setOnInfoListener { _, what, _ ->
                                        if (what == MediaPlayer.MEDIA_INFO_BUFFERING_START) {
                                            isBuffering = true
                                        } else if (what == MediaPlayer.MEDIA_INFO_BUFFERING_END ||
                                            what == MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START
                                        ) {
                                            isBuffering = false
                                        }
                                        false
                                    }

                                    setOnErrorListener { _, what, extra ->
                                        Log.w(TAG, "MediaPlayer error: what=$what, extra=$extra")
                                        isPrepared = false
                                        isBuffering = false
                                        hasError = true
                                        true
                                    }
                                }

                                try {
                                    if (videoUrl.isNotBlank()) {
                                        val uri = Uri.parse(videoUrl)
                                        mp.setDataSource(ctx, uri)
                                        mp.prepareAsync()
                                    } else {
                                        hasError = true
                                        isBuffering = false
                                    }
                                } catch (e: Exception) {
                                    Log.w(TAG, "prepareAsync failed: ${e.message}")
                                    hasError = true
                                    isBuffering = false
                                }

                                mediaPlayer = mp
                            }

                            override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, width: Int, height: Int) {}

                            override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
                                isPrepared = false
                                try {
                                    mediaPlayer?.let { mp ->
                                        try {
                                            if (mp.isPlaying) mp.pause()
                                        } catch (e: Exception) {}
                                        mp.setSurface(null)
                                    }
                                    surfaceRef?.release()
                                    surfaceRef = null
                                } catch (e: Exception) {
                                    Log.w(TAG, "Surface destruction handled: ${e.message}")
                                }
                                return true
                            }

                            override fun onSurfaceTextureUpdated(st: SurfaceTexture) {
                                isBuffering = false
                            }
                        }
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

        // Error with Retry button
        if (hasError && isCurrentPage) {
            Column(
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(16.dp))
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "Playback Error",
                    tint = TokRed,
                    modifier = Modifier.size(40.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Video stream unavailable",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(14.dp))
                Button(
                    onClick = {
                        hasError = false
                        isBuffering = true
                        retryTrigger++
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = TokCyan),
                    shape = RoundedCornerShape(20.dp)
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
