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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.repository.ChortRepository
import com.example.ui.components.VideoPlayerView
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun UploadScreen(
    repository: ChortRepository,
    onUploadSuccess: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentUser by repository.currentUser.collectAsState()

    var selectedDeviceUri by remember { mutableStateOf<Uri?>(null) }
    var selectedVideoUri by remember { mutableStateOf<String>("") }
    var captionText by remember { mutableStateOf("") }
    var musicTitle by remember { mutableStateOf("") }
    var privacySetting by remember { mutableStateOf("Public") } // "Public", "Followers", "Private"

    var isUploading by remember { mutableStateOf(false) }
    var uploadProgress by remember { mutableFloatStateOf(0f) }
    var uploadStatusText by remember { mutableStateOf("") }

    // Android 13+ zero-permission Photo & Video picker
    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedDeviceUri = uri
            selectedVideoUri = uri.toString()
            Toast.makeText(context, "Video loaded from storage", Toast.LENGTH_SHORT).show()
        }
    }

    val suggestedHashtags = listOf("#أغاني", "#موسيقى", "#طرب", "#dance", "#طبخ", "#تقنية", "#رياضة", "#fyp", "#viral")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TokDarkBg)
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
            Column {
                Text(
                    text = "Upload Video",
                    color = TextPrimary,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Share short-form stories with millions on thileli dz",
                    color = TextMuted,
                    fontSize = 12.sp
                )
            }

            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(TokRed.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.CloudUpload,
                    contentDescription = "Upload",
                    tint = TokRed,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Video Picker Card
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = TokDarkSurface),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, TokBorder, RoundedCornerShape(16.dp))
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                if (selectedDeviceUri != null && selectedVideoUri.isNotBlank()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1.2f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.Black)
                    ) {
                        VideoPlayerView(
                            videoUrl = selectedVideoUri,
                            thumbnailUrl = "",
                            isCurrentPage = true,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Video selected from device",
                            color = TokCyan,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )

                        Button(
                            onClick = {
                                videoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                                )
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = TokDarkElevated),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.border(1.dp, TokBorder, RoundedCornerShape(12.dp))
                        ) {
                            Icon(
                                imageVector = Icons.Default.VideoLibrary,
                                contentDescription = "Change Video",
                                tint = TokCyan,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Change Video", color = TextPrimary, fontSize = 12.sp)
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(TokDarkElevated)
                            .border(1.5.dp, TokBorder, RoundedCornerShape(12.dp))
                            .clickable {
                                videoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                                )
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.VideoLibrary,
                                contentDescription = "Select Video",
                                tint = TokCyan,
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

        Spacer(modifier = Modifier.height(18.dp))

        // Video Caption TextField
        Text(
            text = "Video Caption & Hashtags",
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
                focusedBorderColor = TokCyan,
                unfocusedBorderColor = TokBorder,
                focusedContainerColor = TokDarkElevated,
                unfocusedContainerColor = TokDarkElevated
            ),
            shape = RoundedCornerShape(14.dp),
            minLines = 3,
            maxLines = 5,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("upload_caption_input")
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Quick Hashtags Row
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(suggestedHashtags) { tag ->
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(TokDarkElevated)
                        .border(1.dp, TokBorder, RoundedCornerShape(8.dp))
                        .clickable {
                            if (!captionText.contains(tag)) {
                                captionText = (captionText + " " + tag).trim()
                            }
                        }
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(text = tag, color = TokCyan, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Audio Title
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
                focusedBorderColor = TokCyan,
                unfocusedBorderColor = TokBorder,
                focusedContainerColor = TokDarkElevated,
                unfocusedContainerColor = TokDarkElevated
            ),
            shape = RoundedCornerShape(14.dp),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Privacy Setting
        Text(
            text = "Who Can Watch This Video",
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
                        containerColor = if (isSel) TokCyan.copy(alpha = 0.15f) else TokDarkElevated
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { privacySetting = opt }
                        .border(1.dp, if (isSel) TokCyan else TokBorder, RoundedCornerShape(12.dp))
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = opt,
                            tint = if (isSel) TokCyan else TextMuted,
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

        // Upload Progress Indicator
        AnimatedVisibility(visible = isUploading) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(TokDarkElevated)
                    .padding(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = uploadStatusText,
                        color = TokCyan,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold
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
                    color = TokRed,
                    trackColor = TokBorder,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Submit Button
        Button(
            onClick = {
                if (selectedDeviceUri == null) {
                    Toast.makeText(context, "Please select a video from your device first", Toast.LENGTH_SHORT).show()
                    return@Button
                }

                // Validate video file size: Max 100MB
                val pfd = try {
                    context.contentResolver.openFileDescriptor(selectedDeviceUri!!, "r")
                } catch (e: Exception) { null }
                val byteSize = pfd?.statSize ?: 0L
                pfd?.close()
                if (byteSize > 100 * 1024 * 1024) {
                    Toast.makeText(context, "File size exceeds 100MB limit", Toast.LENGTH_LONG).show()
                    return@Button
                }

                if (captionText.isBlank()) {
                    captionText = "Check out this energetic moment! ✨ #chort #viral"
                }

                isUploading = true
                scope.launch {
                    uploadStatusText = "Connecting to Cloud Storage & CDN..."
                    uploadProgress = 0.05f

                    val extractedTags = Regex("#[\\p{L}0-9_]+")
                        .findAll(captionText)
                        .map { it.value }
                        .joinToString(",")
                    val finalTags = if (extractedTags.isNotBlank()) extractedTags else "#chort,#fyp,#viral"
                    val finalMusic = if (musicTitle.isNotBlank()) musicTitle.trim() else ("Original Sound - " + (currentUser?.displayName ?: "Creator"))

                    val result = repository.uploadVideo(
                        videoUrl = selectedVideoUri,
                        caption = captionText.trim(),
                        tags = finalTags,
                        musicTitle = finalMusic,
                        videoUri = selectedDeviceUri,
                        onProgress = { progress ->
                            uploadProgress = 0.05f + (progress * 0.9f)
                            uploadStatusText = "Uploading to Cloud Storage: ${(progress * 100).toInt()}%"
                        }
                    )

                    uploadProgress = 1f
                    uploadStatusText = "Video published to Cloud & Feed!"
                    delay(200)

                    isUploading = false
                    Toast.makeText(context, "Video published to thileli dz!", Toast.LENGTH_SHORT).show()
                    onUploadSuccess()
                }
            },
            enabled = !isUploading,
            colors = ButtonDefaults.buttonColors(containerColor = TokRed),
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
                text = if (isUploading) "Publishing..." else "Publish Video",
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
