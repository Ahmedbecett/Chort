package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.NotInterested
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ZevoraApplication
import com.example.data.local.entities.VideoEntity
import com.example.ui.screens.offline.OfflineStore
import com.example.ui.theme.AccentGold
import com.example.ui.theme.AccentGreen
import com.example.ui.theme.StatusBanned
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.ZevoraBorder
import com.example.ui.theme.ZevoraCyan
import com.example.ui.theme.ZevoraDarkElevated
import com.example.ui.theme.ZevoraDarkSurface
import com.example.ui.theme.ZevoraRed
import com.example.util.AppPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * Rivo 3.1.0 — every action here is real: gallery download writes bytes to
 * MediaStore, bookmark toggles the synced saved set, offlinevault uses the
 * download manager, and "Not interested" hides the video from all feeds.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareBottomSheet(
    sheetState: SheetState,
    video: VideoEntity,
    onDismiss: () -> Unit,
    onReport: () -> Unit,
    onRemixVideo: ((android.net.Uri) -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember { ZevoraApplication.instance.repository }
    val savedIds by repository.savedVideoIds.collectAsState(initial = emptySet())
    val isBookmarked = video.id in savedIds
    val isMyVideo = remember(video.creatorId) {
        repository.currentUser.value?.id == video.creatorId
    }
    // My Downloads privacy is enforced here, on my own videos.
    val downloadsLocked = isMyVideo && !AppPrefs.allowDownloads.value
    var busyAction by remember { mutableStateOf<String?>(null) }
    var downloadProgress by remember { mutableFloatStateOf(0f) }

    fun downloadToGallery() {
        if (busyAction != null) return
        busyAction = "gallery"
        Toast.makeText(context, "Downloading video…", Toast.LENGTH_SHORT).show()
        scope.launch(Dispatchers.IO) {
            val result = saveVideoToGallery(context, video)
            withContext(Dispatchers.Main) {
                busyAction = null
                Toast.makeText(
                    context,
                    if (result.isSuccess) "Video saved to gallery" else "Save failed: ${result.exceptionOrNull()?.message}",
                    Toast.LENGTH_LONG
                ).show()
                if (result.isSuccess) onDismiss()
            }
        }
    }

    // Remix is gated by the creator's own Reuse setting on their videos.
    val remixLocked = isMyVideo && !AppPrefs.allowReuse.value

    fun remixVideo() {
        if (busyAction != null || onRemixVideo == null) return
        busyAction = "remix"
        Toast.makeText(context, "Preparing remix…", Toast.LENGTH_SHORT).show()
        scope.launch {
            val result = OfflineStore.download(
                context,
                videoId = video.id,
                videoUrl = video.videoUrl,
                caption = video.caption,
                creator = video.creatorUsername
            )
            busyAction = null
            if (result.isSuccess) {
                onDismiss()
                onRemixVideo(android.net.Uri.fromFile(result.getOrThrow()))
            } else {
                Toast.makeText(context, "Remix failed: video unavailable offline", Toast.LENGTH_LONG).show()
            }
        }
    }

    fun downloadOffline() {
        if (busyAction != null) return
        if (OfflineStore.isDownloaded(context, video.id)) {
            Toast.makeText(context, "Already in your offline vault", Toast.LENGTH_SHORT).show()
            return
        }
        busyAction = "offline"
        downloadProgress = 0f
        scope.launch {
            val result = OfflineStore.download(
                context,
                videoId = video.id,
                videoUrl = video.videoUrl,
                caption = video.caption,
                creator = video.creatorUsername,
                onProgress = { downloadProgress = it }
            )
            busyAction = null
            Toast.makeText(
                context,
                if (result.isSuccess) "Saved offline — find it in Offline videos" else "Offline save failed",
                Toast.LENGTH_LONG
            ).show()
            if (result.isSuccess) onDismiss()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = ZevoraDarkSurface,
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 24.dp)
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Spacer(modifier = Modifier.size(32.dp))
                Text(
                    text = "Share to",
                    color = TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            HorizontalDivider(color = ZevoraBorder, thickness = 0.5.dp)

            Spacer(modifier = Modifier.height(16.dp))

            // Social platforms row
            Text(
                text = "SEND TO FRIENDS",
                color = TextMuted,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )

            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    ShareTargetIcon(
                        title = "Copy Link",
                        icon = Icons.Default.Link,
                        bgColor = Color(0xFF25F4EE).copy(alpha = 0.2f),
                        iconTint = ZevoraCyan,
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("Rivo Video", "https://chort-nine.vercel.app/api/v1/videos/${video.id}/stream")
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(context, "Link copied to clipboard!", Toast.LENGTH_SHORT).show()
                            onDismiss()
                        }
                    )
                }
                item {
                    ShareTargetIcon(
                        title = "System Share",
                        icon = Icons.Default.Share,
                        bgColor = ZevoraRed.copy(alpha = 0.2f),
                        iconTint = ZevoraRed,
                        onClick = {
                            val shareText = if (AppPrefs.showProfileOnShare()) {
                                "Watch @${video.creatorUsername}'s video on Rivo: ${video.caption} https://chort-nine.vercel.app/api/v1/videos/${video.id}/stream"
                            } else {
                                "Watch this video on Rivo: https://chort-nine.vercel.app/api/v1/videos/${video.id}/stream"
                            }
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, "Check out this clip on Rivo!")
                                putExtra(Intent.EXTRA_TEXT, shareText)
                            }
                            context.startActivity(Intent.createChooser(intent, "Share via"))
                            onDismiss()
                        }
                    )
                }
                if (downloadsLocked) {
                    item {
                        ShareTargetIcon(
                            title = "Locked",
                            icon = Icons.Default.Lock,
                            bgColor = ZevoraDarkElevated,
                            iconTint = TextMuted,
                            onClick = {
                                Toast.makeText(
                                    context,
                                    "Downloads are off for your videos (Settings → Downloads)",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        )
                    }
                } else {
                    item {
                        ShareTargetIcon(
                            title = if (busyAction == "gallery") "Saving…" else "Save Video",
                            icon = Icons.Default.Download,
                            bgColor = AccentGreen.copy(alpha = 0.2f),
                            iconTint = AccentGreen,
                            onClick = { downloadToGallery() }
                        )
                    }
                    item {
                        val already = OfflineStore.isDownloaded(context, video.id)
                        ShareTargetIcon(
                            title = when {
                                busyAction == "offline" -> "${(downloadProgress * 100).toInt()}%"
                                already -> "In Offline"
                                else -> "Offline"
                            },
                            icon = Icons.Default.CloudDownload,
                            bgColor = ZevoraCyan.copy(alpha = 0.2f),
                            iconTint = ZevoraCyan,
                            onClick = { downloadOffline() }
                        )
                    }
                }
                if (onRemixVideo != null && !remixLocked) {
                    item {
                        ShareTargetIcon(
                            title = if (busyAction == "remix") "Remix…" else "Remix",
                            icon = Icons.Default.VideoLibrary,
                            bgColor = AccentGold.copy(alpha = 0.2f),
                            iconTint = AccentGold,
                            onClick = { remixVideo() }
                        )
                    }
                }
                item {
                    ShareTargetIcon(
                        title = if (isBookmarked) "Bookmarked" else "Bookmark",
                        icon = if (isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                        bgColor = AccentGold.copy(alpha = 0.2f),
                        iconTint = AccentGold,
                        onClick = {
                            scope.launch {
                                val nowSaved = repository.toggleSave(video.id)
                                Toast.makeText(
                                    context,
                                    if (nowSaved) "Saved to your bookmarks" else "Removed from bookmarks",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                            onDismiss()
                        }
                    )
                }
            }

            if (busyAction == "offline") {
                LinearProgressIndicator(
                    progress = { downloadProgress },
                    color = ZevoraCyan,
                    trackColor = ZevoraDarkElevated,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 8.dp)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Actions row
            Text(
                text = "SAFETY & CONTROLS",
                color = TextMuted,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )

            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    ShareTargetIcon(
                        title = "Report Video",
                        icon = Icons.Outlined.Flag,
                        bgColor = StatusBanned.copy(alpha = 0.2f),
                        iconTint = StatusBanned,
                        onClick = {
                            onDismiss()
                            onReport()
                        }
                    )
                }
                item {
                    ShareTargetIcon(
                        title = "Not Interested",
                        icon = Icons.Outlined.NotInterested,
                        bgColor = ZevoraDarkElevated,
                        iconTint = TextSecondary,
                        onClick = {
                            AppPrefs.hideVideo(video.id)
                            Toast.makeText(context, "Hidden — you won't see this video again", Toast.LENGTH_SHORT).show()
                            onDismiss()
                        }
                    )
                }
            }
        }
    }
}

/** Streams the video bytes into the public gallery (DCIM/Rivo). Real file IO. */
private suspend fun saveVideoToGallery(context: Context, video: VideoEntity): Result<String> =
    withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            val name = "Rivo_${video.id.take(12)}_${System.currentTimeMillis()}.mp4"
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, name)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Video.Media.RELATIVE_PATH, "DCIM/Rivo")
                    put(MediaStore.Video.Media.IS_PENDING, 1)
                }
            }
            val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            }
            connection = (URL(video.videoUrl).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = true
                connectTimeout = 15_000
                readTimeout = 180_000
                setRequestProperty("User-Agent", "Rivo-Android/save")
            }
            connection.connect()
            if (connection.responseCode !in 200..299) {
                return@withContext Result.failure(Exception("HTTP ${connection.responseCode}"))
            }
            val uri = context.contentResolver.insert(collection, values)
                ?: return@withContext Result.failure(Exception("gallery unavailable"))
            var copied = 0L
            try {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    connection.inputStream.use { input ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val read = input.read(buffer)
                            if (read <= 0) break
                            out.write(buffer, 0, read)
                            copied += read
                        }
                    }
                }
            } catch (e: Exception) {
                try {
                    context.contentResolver.delete(uri, null, null)
                } catch (_: Exception) {
                }
                return@withContext Result.failure(e)
            }
            if (copied <= 0) {
                try {
                    context.contentResolver.delete(uri, null, null)
                } catch (_: Exception) {
                }
                return@withContext Result.failure(Exception("empty file"))
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.Video.Media.IS_PENDING, 0)
                context.contentResolver.update(uri, values, null, null)
            }
            Result.success(uri.toString())
        } catch (e: Exception) {
            Result.failure(Exception(e.message ?: "download failed"))
        } finally {
            try {
                connection?.disconnect()
            } catch (_: Exception) {
            }
        }
    }

@Composable
private fun ShareTargetIcon(
    title: String,
    icon: ImageVector,
    bgColor: Color,
    iconTint: Color,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(54.dp)
                .clip(CircleShape)
                .background(bgColor),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = iconTint,
                modifier = Modifier.size(26.dp)
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = title,
            color = TextSecondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
