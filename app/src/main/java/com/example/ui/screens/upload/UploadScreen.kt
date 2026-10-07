package com.example.ui.screens.upload

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.repository.ZevoraRepository
import com.example.ui.components.VideoPlayerView
import com.example.ui.screens.create.AudioMuxer
import com.example.ui.screens.create.AudioPickerSheet
import com.example.ui.screens.create.DeviceAudio
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.ZevoraBorder
import com.example.ui.theme.ZevoraCyan
import com.example.ui.theme.ZevoraDarkBg
import com.example.ui.theme.ZevoraDarkElevated
import com.example.ui.theme.ZevoraDarkSurface
import com.example.ui.theme.ZevoraRed
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * ZEVORA 3.1.0 — publish screen fed by the picker / camera / remix flow.
 * Multi-video queue with per-item progress, optional real sound mixing,
 * server-enforced Public/Private visibility, and +5 coins per publish.
 */
@Composable
fun UploadScreen(
    repository: ZevoraRepository,
    onUploadSuccess: () -> Unit,
    initialUris: List<Uri> = emptyList(),
    initialAudioUri: Uri? = null,
    initialAudioTitle: String? = null,
    onBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentUser by repository.currentUser.collectAsState()

    val queue = remember {
        mutableStateListOf<Uri>().apply { addAll(initialUris.distinct().take(10)) }
    }
    var currentIndex by remember { mutableIntStateOf(0) }
    var audioUri by remember { mutableStateOf(initialAudioUri) }
    var audioTitle by remember { mutableStateOf(initialAudioTitle ?: "") }
    var showAudioSheet by remember { mutableStateOf(false) }
    var captionText by remember { mutableStateOf("") }
    var musicTitle by remember { mutableStateOf(initialAudioTitle ?: "") }
    var privacySetting by remember { mutableStateOf("Public") } // "Public" | "Private"

    var isUploading by remember { mutableStateOf(false) }
    var uploadProgress by remember { mutableFloatStateOf(0f) }
    var uploadStatusText by remember { mutableStateOf("") }
    var doneCount by remember { mutableIntStateOf(0) }

    val currentUri = queue.getOrNull(currentIndex)

    fun addUri(uri: Uri) {
        if (queue.size >= 10) {
            Toast.makeText(context, "Maximum 10 videos", Toast.LENGTH_SHORT).show()
            return
        }
        if (!queue.contains(uri)) {
            queue.add(uri)
            currentIndex = queue.lastIndex
        }
    }

    val documentPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) addUri(uri)
    }

    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {}
            addUri(uri)
        }
    }

    fun openAnyVideoPicker() {
        try {
            if (ActivityResultContracts.PickVisualMedia.isPhotoPickerAvailable(context)) {
                videoPickerLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                )
            } else {
                documentPickerLauncher.launch("video/*")
            }
        } catch (_: Exception) {
            documentPickerLauncher.launch("video/*")
        }
    }

    fun fileSizeOf(uri: Uri): Long {
        return try {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize } ?: 0L
        } catch (_: Exception) {
            0L
        }
    }

    fun publishQueue() {
        if (currentUser == null) {
            Toast.makeText(context, "Log in to publish videos", Toast.LENGTH_LONG).show()
            return
        }
        if (queue.isEmpty()) {
            Toast.makeText(context, "Select at least one video first", Toast.LENGTH_SHORT).show()
            return
        }
        queue.forEachIndexed { index, uri ->
            if (fileSizeOf(uri) > 100 * 1024 * 1024) {
                Toast.makeText(context, "Video ${index + 1} exceeds the 100MB limit", Toast.LENGTH_LONG).show()
                return
            }
        }
        if (captionText.isBlank()) {
            captionText = "Check out this energetic moment! ✨ #zevora #viral"
        }
        isUploading = true
        doneCount = 0
        scope.launch {
            val total = queue.size
            var published = 0
            var failed = 0
            val visibility = if (privacySetting == "Private") "PRIVATE" else "PUBLIC"
            val extractedTags = Regex("#[\\p{L}0-9_]+")
                .findAll(captionText)
                .map { it.value }
                .joinToString(",")
            val finalTags = if (extractedTags.isNotBlank()) extractedTags else "#zevora,#fyp,#viral"
            val finalMusic = if (musicTitle.isNotBlank()) musicTitle.trim()
            else ("Original Sound - " + (currentUser?.displayName ?: "Creator"))

            queue.toList().forEachIndexed { index, uri ->
                val base = index.toFloat() / total
                try {
                    uploadStatusText = if (total > 1) "Preparing video ${index + 1}/$total…" else "Preparing video…"
                    var uploadUri = uri
                    // Real sound mixing per item (when a sound is attached).
                    if (audioUri != null) {
                        uploadStatusText = if (total > 1) "Mixing sound ${index + 1}/$total…" else "Mixing sound…"
                        val mixed = java.io.File(context.cacheDir, "zevora_pub_${System.currentTimeMillis()}_$index.mp4")
                        val mux = AudioMuxer.replaceAudio(context, uri, audioUri!!, mixed)
                        if (mux.ok) {
                            uploadUri = Uri.fromFile(mixed)
                        } else {
                            uploadStatusText = mux.message
                            delay(600)
                        }
                    }
                    uploadProgress = base + 0.02f
                    val result = repository.uploadVideo(
                        videoUrl = uploadUri.toString(),
                        caption = captionText.trim(),
                        tags = finalTags,
                        musicTitle = finalMusic,
                        videoUri = uploadUri,
                        onProgress = { progress ->
                            val span = 0.95f / total
                            uploadProgress = (base + progress * span).coerceIn(0f, 1f)
                            uploadStatusText = if (total > 1) {
                                "Uploading ${index + 1}/$total: ${(progress * 100).toInt()}%"
                            } else {
                                "Uploading to Cloud Storage: ${(progress * 100).toInt()}%"
                            }
                        },
                        visibility = visibility
                    )
                    if (result.isSuccess) {
                        published++
                        doneCount = published
                        repository.earnCoins(5, "video_published")
                    } else {
                        failed++
                        uploadStatusText = "Video ${index + 1} failed: ${result.exceptionOrNull()?.message}"
                        delay(700)
                    }
                } catch (e: Exception) {
                    failed++
                    uploadStatusText = "Video ${index + 1} failed: ${e.message}"
                    delay(700)
                }
            }
            uploadProgress = 1f
            isUploading = false
            if (published > 0) {
                uploadStatusText = if (failed == 0) "Published to ZEVORA!" else "Published $published of $total"
                Toast.makeText(
                    context,
                    if (failed == 0) "Video${if (published > 1) "s" else ""} published! +${published * 5} coins"
                    else "Published $published of $total ($failed failed)",
                    Toast.LENGTH_LONG
                ).show()
                delay(300)
                onUploadSuccess()
            } else {
                uploadProgress = 0f
                Toast.makeText(context, "Publish failed — check your connection and retry", Toast.LENGTH_LONG).show()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ZevoraDarkBg)
            .statusBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .padding(bottom = 60.dp)
            .testTag("upload_screen")
    ) {
        // Title
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { if (!isUploading) onBack() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = TextPrimary)
                }
                Column {
                    Text(
                        text = if (queue.size > 1) "New posts (${queue.size})" else "New post",
                        color = TextPrimary,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Share short-form stories on ZEVORA",
                        color = TextMuted,
                        fontSize = 12.sp
                    )
                }
            }
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(ZevoraRed.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.CloudUpload,
                    contentDescription = "Upload",
                    tint = ZevoraRed,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Queue strip (multi-select)
        if (queue.isNotEmpty()) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                itemsIndexed(queue) { index, uri ->
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(ZevoraDarkElevated)
                            .border(
                                if (index == currentIndex) 2.dp else 0.dp,
                                if (index == currentIndex) ZevoraRed else Color.Transparent,
                                RoundedCornerShape(10.dp)
                            )
                            .clickable(enabled = !isUploading) { currentIndex = index }
                    ) {
                        AsyncImage(
                            model = uri,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        Text(
                            "${index + 1}",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(3.dp)
                                .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 4.dp)
                        )
                        if (!isUploading && queue.size > 1) {
                            Icon(
                                Icons.Default.Close,
                                "Remove",
                                tint = Color.White,
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(2.dp)
                                    .size(16.dp)
                                    .clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.6f))
                                    .clickable {
                                        queue.removeAt(index)
                                        currentIndex = currentIndex.coerceIn(0, (queue.size - 1).coerceAtLeast(0))
                                    }
                                    .padding(2.dp)
                            )
                        }
                    }
                }
                if (!isUploading && queue.size < 10) {
                    item {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(ZevoraDarkElevated)
                                .border(1.dp, ZevoraBorder, RoundedCornerShape(10.dp))
                                .clickable { openAnyVideoPicker() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Add, "Add video", tint = ZevoraCyan)
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        // Preview card
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = ZevoraDarkSurface),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, ZevoraBorder, RoundedCornerShape(16.dp))
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                if (currentUri != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1.2f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.Black)
                    ) {
                        key(currentUri) {
                            VideoPlayerView(
                                videoUrl = currentUri.toString(),
                                thumbnailUrl = "",
                                isCurrentPage = true,
                                onRetry = { },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (queue.size > 1) "Previewing ${currentIndex + 1} of ${queue.size}" else "Video ready to publish",
                            color = ZevoraCyan,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Button(
                            onClick = { openAnyVideoPicker() },
                            enabled = !isUploading,
                            colors = ButtonDefaults.buttonColors(containerColor = ZevoraDarkElevated),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.border(1.dp, ZevoraBorder, RoundedCornerShape(12.dp))
                        ) {
                            Icon(
                                imageVector = Icons.Default.VideoLibrary,
                                contentDescription = "Change Video",
                                tint = ZevoraCyan,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Add Video", color = TextPrimary, fontSize = 12.sp)
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(ZevoraDarkElevated)
                            .border(1.5.dp, ZevoraBorder, RoundedCornerShape(12.dp))
                            .clickable { openAnyVideoPicker() },
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.VideoLibrary,
                                contentDescription = "Select Video",
                                tint = ZevoraCyan,
                                modifier = Modifier.size(44.dp)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "Select Video from Gallery",
                                color = TextPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Tap to pick your MP4/WebM video file",
                                color = TextMuted,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Sound card
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(ZevoraDarkSurface)
                .border(1.dp, ZevoraBorder, RoundedCornerShape(12.dp))
                .clickable(enabled = !isUploading) { showAudioSheet = true }
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.MusicNote, null, tint = ZevoraCyan, modifier = Modifier.size(22.dp))
            Spacer(modifier = Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (audioUri == null) "Attach a sound" else audioTitle.ifBlank { "Sound attached" },
                    color = TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    if (audioUri == null) "Optional — mixed into every video for real"
                    else "Mixed into every video on publish",
                    color = TextMuted,
                    fontSize = 11.5.sp
                )
            }
            if (audioUri != null && !isUploading) {
                Icon(
                    Icons.Default.Close,
                    "Remove sound",
                    tint = TextSecondary,
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable {
                            audioUri = null
                            audioTitle = ""
                        }
                        .padding(4.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Caption
        Text(
            text = "Video Caption & Hashtags" + if (queue.size > 1) " (applies to all ${queue.size})" else "",
            color = TextPrimary,
            fontSize = 13.5.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(6.dp))
        OutlinedTextField(
            value = captionText,
            onValueChange = { captionText = it },
            placeholder = { Text("Write a compelling caption for your clip...", color = TextMuted, fontSize = 13.sp) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary,
                focusedBorderColor = ZevoraCyan,
                unfocusedBorderColor = ZevoraBorder,
                focusedContainerColor = ZevoraDarkElevated,
                unfocusedContainerColor = ZevoraDarkElevated
            ),
            shape = RoundedCornerShape(14.dp),
            minLines = 3,
            maxLines = 5,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("upload_caption_input")
        )
        Spacer(modifier = Modifier.height(8.dp))
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(suggestedHashtags) { tag ->
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(ZevoraDarkElevated)
                        .border(1.dp, ZevoraBorder, RoundedCornerShape(8.dp))
                        .clickable {
                            if (!captionText.contains(tag)) {
                                captionText = (captionText + " " + tag).trim()
                            }
                        }
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(text = tag, color = ZevoraCyan, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))

        // Audio title
        Text(
            text = "Audio Track Name",
            color = TextPrimary,
            fontSize = 13.5.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(6.dp))
        OutlinedTextField(
            value = musicTitle,
            onValueChange = { musicTitle = it },
            placeholder = { Text("Original Sound - ${currentUser?.displayName ?: "Creator"}", color = TextMuted, fontSize = 13.sp) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.MusicNote,
                    contentDescription = "Audio",
                    tint = TextSecondary,
                    modifier = Modifier.size(18.dp)
                )
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary,
                focusedBorderColor = ZevoraCyan,
                unfocusedBorderColor = ZevoraBorder,
                focusedContainerColor = ZevoraDarkElevated,
                unfocusedContainerColor = ZevoraDarkElevated
            ),
            shape = RoundedCornerShape(14.dp),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(16.dp))

        // Privacy
        Text(
            text = "Who Can Watch",
            color = TextPrimary,
            fontSize = 13.5.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            listOf(
                Triple("Public", Icons.Default.Public, "Everyone"),
                Triple("Private", Icons.Default.Lock, "Only me")
            ).forEach { (opt, icon, sub) ->
                val isSel = (privacySetting == opt)
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSel) ZevoraCyan.copy(alpha = 0.15f) else ZevoraDarkElevated
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(enabled = !isUploading) { privacySetting = opt }
                        .border(1.dp, if (isSel) ZevoraCyan else ZevoraBorder, RoundedCornerShape(12.dp))
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = opt,
                            tint = if (isSel) ZevoraCyan else TextMuted,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = opt,
                                color = if (isSel) Color.White else TextSecondary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(text = sub, color = TextMuted, fontSize = 10.sp)
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(24.dp))

        // Progress
        AnimatedVisibility(visible = isUploading) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(ZevoraDarkElevated)
                    .padding(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = uploadStatusText,
                        color = ZevoraCyan,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "${(uploadProgress * 100).toInt()}%",
                        color = TextPrimary,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { uploadProgress },
                    color = ZevoraRed,
                    trackColor = ZevoraBorder,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                )
            }
        }
        Spacer(modifier = Modifier.height(14.dp))

        // Submit
        Button(
            onClick = { publishQueue() },
            enabled = !isUploading && queue.isNotEmpty(),
            colors = ButtonDefaults.buttonColors(containerColor = ZevoraRed),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("publish_video_button")
        ) {
            Icon(
                imageVector = Icons.Default.CloudUpload,
                contentDescription = "Post",
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = when {
                    isUploading && queue.size > 1 -> "Publishing $doneCount/${queue.size}…"
                    isUploading -> "Publishing..."
                    queue.size > 1 -> "Publish ${queue.size} Videos"
                    else -> "Publish Video"
                },
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }

    if (showAudioSheet) {
        AudioPickerSheet(
            onDismiss = { showAudioSheet = false },
            onPick = { track: DeviceAudio? ->
                if (track == null) {
                    audioUri = null
                    audioTitle = ""
                    musicTitle = ""
                } else {
                    audioUri = track.uri
                    audioTitle = track.title
                    musicTitle = "${track.title} — ${track.artist}"
                }
                showAudioSheet = false
            }
        )
    }
}

private val suggestedHashtags = listOf("#أغاني", "#موسيقى", "#طرب", "#dance", "#طبخ", "#تقنية", "#رياضة", "#fyp", "#viral")
