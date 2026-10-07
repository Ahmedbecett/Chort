package com.example.ui.screens.create

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.example.data.repository.ZevoraRepository
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.ZevoraCyan
import com.example.ui.theme.ZevoraDarkBg
import com.example.ui.theme.ZevoraDarkElevated
import com.example.ui.theme.ZevoraDarkSurface
import com.example.ui.theme.ZevoraRed
import kotlinx.coroutines.launch

/**
 * ZEVORA 3.1.0 — TikTok-style create entry: Recents/albums, All/Videos/Photos
 * tabs, Camera tile first, multi-select, Add-sound, and POST/CREATE/LIVE modes.
 * Everything is backed by real MediaStore queries — no placeholders.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaPickerScreen(
    repository: ZevoraRepository,
    initialMode: String = "post",
    onMediaConfirmed: (List<Uri>, Uri?) -> Unit,
    onOpenCamera: () -> Unit,
    onGoLive: () -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentUser by repository.currentUser.collectAsState()

    val storagePermissions = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.READ_MEDIA_AUDIO
            )
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }
    fun hasStoragePermission(): Boolean =
        storagePermissions.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }

    var permissionGranted by remember { mutableStateOf(hasStoragePermission()) }
    var permissionAsked by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        permissionGranted = grants.values.all { it } || hasStoragePermission()
    }

    var mode by remember { mutableStateOf(initialMode) } // "post" | "create" | "live"
    var tab by remember { mutableStateOf(0) } // 0 All, 1 Videos, 2 Photos
    var album by remember { mutableStateOf("Recents") }
    var multiSelect by remember { mutableStateOf(initialMode == "create") }
    val selected = remember { mutableStateListOf<Uri>() }
    var media by remember { mutableStateOf<List<DeviceMedia>>(emptyList()) }
    var albums by remember { mutableStateOf<List<MediaAlbum>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var showAlbumSheet by remember { mutableStateOf(false) }
    var showAudioSheet by remember { mutableStateOf(false) }
    var audioUri by remember { mutableStateOf<Uri?>(null) }
    var audioTitle by remember { mutableStateOf("") }
    var previewPhoto by remember { mutableStateOf<DeviceMedia?>(null) }
    var liveTitle by remember { mutableStateOf("") }
    var settingAvatar by remember { mutableStateOf(false) }

    fun reload() {
        if (!hasStoragePermission()) return
        scope.launch {
            loading = true
            try {
                albums = MediaStoreLoader.loadAlbums(context)
                media = MediaStoreLoader.loadMedia(
                    context,
                    videosOnly = tab == 1,
                    photosOnly = tab == 2,
                    album = album
                )
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(Unit) {
        if (!hasStoragePermission() && !permissionAsked) {
            permissionAsked = true
            permissionLauncher.launch(storagePermissions)
        }
    }
    LaunchedEffect(permissionGranted, tab, album) {
        if (permissionGranted) reload()
    }
    LaunchedEffect(mode) {
        if (mode == "create") multiSelect = true
    }

    fun onTapMedia(item: DeviceMedia) {
        if (!item.isVideo) {
            previewPhoto = item
            return
        }
        if (multiSelect) {
            if (selected.contains(item.uri)) selected.remove(item.uri)
            else if (selected.size < 10) selected.add(item.uri)
            else Toast.makeText(context, "Maximum 10 videos at once", Toast.LENGTH_SHORT).show()
        } else {
            if (mode == "create") {
                selected.clear()
                selected.add(item.uri)
            } else {
                onMediaConfirmed(listOf(item.uri), audioUri)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ZevoraDarkBg)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // ---- top bar: back + album + multiselect ----
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 48.dp, start = 8.dp, end = 12.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onClose) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
                }
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { showAlbumSheet = true }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(ZevoraRed)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(album, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    Icon(Icons.Default.ArrowDropDown, "Albums", tint = Color.White)
                }
                Spacer(Modifier.weight(1f))
                if (mode != "live") {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { multiSelect = !multiSelect }
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Select multiple", color = Color.White, fontSize = 13.sp)
                        Spacer(Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .clip(CircleShape)
                                .background(if (multiSelect) ZevoraCyan else Color.Transparent)
                                .border(
                                    1.5.dp,
                                    if (multiSelect) ZevoraCyan else Color.White.copy(alpha = 0.6f),
                                    CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (multiSelect) {
                                Icon(Icons.Default.Check, null, tint = Color.Black, modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                }
            }

            if (mode == "live") {
                LiveSetupPanel(
                    liveTitle = liveTitle,
                    onTitleChange = { liveTitle = it },
                    displayName = currentUser?.displayName ?: currentUser?.username ?: "Creator",
                    onGoLive = onGoLive
                )
            } else {
                // ---- tabs ----
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(22.dp)
                ) {
                    listOf("All", "Videos", "Photos").forEachIndexed { index, label ->
                        Column(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable {
                                    tab = index
                                    selected.clear()
                                }
                                .padding(horizontal = 4.dp, vertical = 6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                label,
                                color = if (tab == index) Color.White else TextMuted,
                                fontSize = 15.sp,
                                fontWeight = if (tab == index) FontWeight.Bold else FontWeight.Medium
                            )
                            Spacer(Modifier.height(4.dp))
                            Box(
                                modifier = Modifier
                                    .width(26.dp)
                                    .height(2.5.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(if (tab == index) Color.White else Color.Transparent)
                            )
                        }
                    }
                }

                // ---- grid ----
                if (!permissionGranted) {
                    PermissionRationale(
                        onGrant = { permissionLauncher.launch(storagePermissions) },
                        onOpenSettings = {
                            try {
                                context.startActivity(
                                    Intent(
                                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                        Uri.parse("package:${context.packageName}")
                                    )
                                )
                            } catch (_: Exception) {
                            }
                        }
                    )
                } else if (loading && media.isEmpty()) {
                    Box(Modifier.fillMaxSize().weight(1f), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = ZevoraRed)
                    }
                } else if (media.isEmpty()) {
                    EmptyMediaPanel(onOpenCamera = onOpenCamera)
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(4),
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentPadding = PaddingValues(2.dp),
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        item(key = "camera_tile") {
                            Box(
                                modifier = Modifier
                                    .aspectRatio(0.72f)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(ZevoraDarkElevated)
                                    .clickable(onClick = onOpenCamera),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        Icons.Default.PhotoCamera,
                                        "Camera",
                                        tint = Color.White,
                                        modifier = Modifier.size(30.dp)
                                    )
                                    Spacer(Modifier.height(6.dp))
                                    Text("Camera", color = Color.White, fontSize = 12.sp)
                                }
                            }
                        }
                        items(media, key = { it.uri.toString() }) { item ->
                            val order = selected.indexOf(item.uri)
                            val isSelected = order >= 0
                            Box(
                                modifier = Modifier
                                    .aspectRatio(0.72f)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(ZevoraDarkSurface)
                                    .border(
                                        if (isSelected) 2.5.dp else 0.dp,
                                        if (isSelected) ZevoraCyan else Color.Transparent,
                                        RoundedCornerShape(4.dp)
                                    )
                                    .clickable { onTapMedia(item) }
                            ) {
                                AsyncImage(
                                    model = item.uri,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                                if (item.isVideo && item.durationMs > 0) {
                                    Text(
                                        formatDuration(item.durationMs),
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier
                                            .align(Alignment.BottomStart)
                                            .padding(5.dp)
                                            .background(
                                                Color.Black.copy(alpha = 0.55f),
                                                RoundedCornerShape(4.dp)
                                            )
                                            .padding(horizontal = 5.dp, vertical = 2.dp)
                                    )
                                }
                                if (!item.isVideo) {
                                    Icon(
                                        Icons.Default.Image,
                                        null,
                                        tint = Color.White.copy(alpha = 0.85f),
                                        modifier = Modifier
                                            .align(Alignment.BottomEnd)
                                            .padding(5.dp)
                                            .size(14.dp)
                                    )
                                }
                                if (isSelected) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(5.dp)
                                            .size(22.dp)
                                            .clip(CircleShape)
                                            .background(ZevoraCyan),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            if (multiSelect) "${order + 1}" else "✓",
                                            color = Color.Black,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // ---- create-mode action bar ----
                if (mode == "create" && permissionGranted) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(ZevoraDarkElevated)
                                .clickable { showAudioSheet = true }
                                .padding(horizontal = 12.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.MusicNote, null, tint = ZevoraCyan, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                if (audioUri == null) "Add sound" else audioTitle.ifBlank { "Sound attached" },
                                color = Color.White,
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.width(110.dp)
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        Button(
                            onClick = {
                                if (selected.isNotEmpty()) onMediaConfirmed(selected.toList(), audioUri)
                                else Toast.makeText(context, "Select at least one video", Toast.LENGTH_SHORT).show()
                            },
                            enabled = selected.isNotEmpty(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ZevoraRed,
                                disabledContainerColor = ZevoraDarkElevated
                            ),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(
                                if (selected.isEmpty()) "Next" else "Next (${selected.size})",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                // ---- post-mode multiselect confirm ----
                if (mode == "post" && multiSelect && selected.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Button(
                            onClick = { onMediaConfirmed(selected.toList(), null) },
                            colors = ButtonDefaults.buttonColors(containerColor = ZevoraRed),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Next (${selected.size})", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // ---- bottom mode bar: POST | CREATE | LIVE ----
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                ModeTab("POST", mode == "post") { mode = "post" }
                Spacer(Modifier.width(34.dp))
                ModeTab("CREATE", mode == "create") { mode = "create" }
                Spacer(Modifier.width(34.dp))
                ModeTab("LIVE", mode == "live") { mode = "live" }
            }
        }

        // ---- photo preview overlay (share + set as avatar are both real) ----
        previewPhoto?.let { photo ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.96f))
                    .clickable { previewPhoto = null },
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = photo.uri,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                )
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 70.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = {
                            try {
                                val share = Intent(Intent.ACTION_SEND).apply {
                                    type = "image/*"
                                    putExtra(Intent.EXTRA_STREAM, photo.uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(share, "Share photo"))
                            } catch (e: Exception) {
                                Toast.makeText(context, "Share failed: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ZevoraDarkElevated)
                    ) {
                        Icon(Icons.Default.Share, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Share")
                    }
                    Button(
                        onClick = {
                            val me = currentUser
                            if (me == null) {
                                Toast.makeText(context, "Log in to change your photo", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            if (!repository.firebaseService.isFirebaseAvailable) {
                                Toast.makeText(
                                    context,
                                    "Photo uploads need cloud storage — photo kept in your gallery",
                                    Toast.LENGTH_LONG
                                ).show()
                                return@Button
                            }
                            settingAvatar = true
                            scope.launch {
                                try {
                                    val result = repository.updateProfile(
                                        displayName = me.displayName,
                                        username = me.username,
                                        bio = me.bio,
                                        avatarUri = photo.uri
                                    )
                                    Toast.makeText(
                                        context,
                                        if (result.isSuccess) "Profile photo updated" else "Update failed",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    if (result.isSuccess) previewPhoto = null
                                } finally {
                                    settingAvatar = false
                                }
                            }
                        },
                        enabled = !settingAvatar,
                        colors = ButtonDefaults.buttonColors(containerColor = ZevoraRed)
                    ) {
                        Text(if (settingAvatar) "Saving…" else "Use as profile photo", fontWeight = FontWeight.Bold)
                    }
                }
                IconButton(
                    onClick = { previewPhoto = null },
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(top = 44.dp, start = 8.dp)
                ) {
                    Icon(Icons.Default.Close, "Close", tint = Color.White)
                }
            }
        }
    }

    if (showAlbumSheet) {
        ModalBottomSheet(
            onDismissRequest = { showAlbumSheet = false },
            sheetState = rememberModalBottomSheetState(),
            containerColor = ZevoraDarkSurface
        ) {
            Text(
                "Albums",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
            )
            LazyColumn(modifier = Modifier.padding(bottom = 32.dp)) {
                items(albums) { a ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                album = a.name
                                selected.clear()
                                showAlbumSheet = false
                            }
                            .padding(horizontal = 20.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (a.coverUri != null) {
                            AsyncImage(
                                model = a.coverUri,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(RoundedCornerShape(8.dp))
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(ZevoraDarkElevated),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Album, null, tint = TextSecondary)
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(a.name, color = Color.White, fontWeight = FontWeight.SemiBold)
                            Text("${a.count} items", color = TextMuted, fontSize = 12.sp)
                        }
                        if (a.name == album) {
                            Icon(Icons.Default.RadioButtonChecked, null, tint = ZevoraRed)
                        }
                    }
                }
            }
        }
    }

    if (showAudioSheet) {
        AudioPickerSheet(
            onDismiss = { showAudioSheet = false },
            onPick = { track ->
                if (track == null) {
                    audioUri = null
                    audioTitle = ""
                } else {
                    audioUri = track.uri
                    audioTitle = track.title
                    if (!MediaStoreLoader.isMuxFriendly(track.mimeType)) {
                        Toast.makeText(
                            context,
                            "Note: ${track.mimeType} may not embed on this device — AAC/M4A always works",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
                showAudioSheet = false
            }
        )
    }
}

@Composable
private fun ModeTab(label: String, active: Boolean, onClick: () -> Unit) {
    Text(
        label,
        color = if (active) Color.White else TextMuted,
        fontSize = 14.sp,
        fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    )
}

@Composable
private fun androidx.compose.foundation.layout.ColumnScope.PermissionRationale(onGrant: () -> Unit, onOpenSettings: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f)
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.Image, null, tint = TextMuted, modifier = Modifier.size(52.dp))
        Spacer(Modifier.height(12.dp))
        Text(
            "Allow access to photos and videos",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "ZEVORA needs media permission to show your gallery.",
            color = TextSecondary,
            fontSize = 13.sp
        )
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = onGrant,
            colors = ButtonDefaults.buttonColors(containerColor = ZevoraRed)
        ) {
            Text("Allow access", fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "Open app settings",
            color = ZevoraCyan,
            fontSize = 13.sp,
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .clickable(onClick = onOpenSettings)
                .padding(8.dp)
        )
    }
}

@Composable
private fun androidx.compose.foundation.layout.ColumnScope.EmptyMediaPanel(onOpenCamera: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f)
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.PhotoCamera, null, tint = TextMuted, modifier = Modifier.size(52.dp))
        Spacer(Modifier.height(12.dp))
        Text("No media here yet", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Spacer(Modifier.height(6.dp))
        Text("Record your first clip with the ZEVORA camera.", color = TextSecondary, fontSize = 13.sp)
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = onOpenCamera,
            colors = ButtonDefaults.buttonColors(containerColor = ZevoraRed)
        ) {
            Text("Open camera", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.ColumnScope.LiveSetupPanel(
    liveTitle: String,
    onTitleChange: (String) -> Unit,
    displayName: String,
    onGoLive: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(ZevoraRed)
                .padding(horizontal = 14.dp, vertical = 6.dp)
        ) {
            Text("LIVE", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
        }
        Spacer(Modifier.height(14.dp))
        Text(
            "Go live as $displayName",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 17.sp
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "Your followers will be notified when you start.",
            color = TextSecondary,
            fontSize = 13.sp
        )
        Spacer(Modifier.height(18.dp))
        OutlinedTextField(
            value = liveTitle,
            onValueChange = onTitleChange,
            placeholder = { Text("Add a title…", color = TextMuted) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = ZevoraRed,
                unfocusedBorderColor = Color.White.copy(alpha = 0.2f)
            )
        )
        Spacer(Modifier.height(18.dp))
        Button(
            onClick = onGoLive,
            colors = ButtonDefaults.buttonColors(containerColor = ZevoraRed),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Go LIVE", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioPickerSheet(
    onDismiss: () -> Unit,
    onPick: (DeviceAudio?) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var tracks by remember { mutableStateOf<List<DeviceAudio>?>(null) }

    fun hasAudioPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_MEDIA_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
    }
    var audioGranted by remember { mutableStateOf(hasAudioPermission()) }
    val audioPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> audioGranted = granted || hasAudioPermission() }

    LaunchedEffect(audioGranted) {
        if (!audioGranted) {
            tracks = emptyList()
            return@LaunchedEffect
        }
        scope.launch {
            tracks = MediaStoreLoader.loadAudio(context)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(),
        containerColor = ZevoraDarkSurface
    ) {
        Text(
            "Add sound",
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
        )
        Text(
            "A sound from your device — mixed into the video for real.",
            color = TextMuted,
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 20.dp)
        )
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onPick(null) }
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Close, null, tint = TextSecondary)
            Spacer(Modifier.width(12.dp))
            Text("Original sound (no music)", color = Color.White, fontWeight = FontWeight.SemiBold)
        }
        if (!audioGranted) {
            Button(
                onClick = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        audioPermissionLauncher.launch(Manifest.permission.READ_MEDIA_AUDIO)
                    } else {
                        audioPermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = ZevoraRed),
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)
            ) {
                Text("Allow music access", fontWeight = FontWeight.Bold)
            }
        }
        val list = tracks
        if (list == null) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(160.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = ZevoraRed)
            }
        } else if (list.isEmpty()) {
            Text(
                "No audio files found on this device.",
                color = TextMuted,
                fontSize = 13.sp,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp)
            )
        } else {
            LazyColumn(modifier = Modifier.padding(bottom = 32.dp)) {
                items(list, key = { it.uri.toString() }) { t ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPick(t) }
                            .padding(horizontal = 20.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(ZevoraDarkElevated),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.MusicNote, null, tint = ZevoraCyan)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                t.title,
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                "${t.artist} • ${formatDuration(t.durationMs)}",
                                color = TextMuted,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (MediaStoreLoader.isMuxFriendly(t.mimeType)) {
                            Text(
                                "AAC",
                                color = Color.Black,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(ZevoraCyan)
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

fun formatDuration(ms: Long): String {
    val totalSec = (ms / 1000).toInt().coerceAtLeast(0)
    val m = totalSec / 60
    val s = totalSec % 60
    return if (m > 0) "%d:%02d".format(m, s) else "0:%02d".format(s)
}
