package com.example.ui.screens.create

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashAuto
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Timer10
import androidx.compose.material.icons.filled.Timer3
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.example.data.repository.ZevoraRepository
import com.example.ui.components.VideoPlayerView
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.ZevoraCyan
import com.example.ui.theme.ZevoraDarkElevated
import com.example.ui.theme.ZevoraRed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Rivo 3.1.0 — real CameraX capture screen in the reference layout:
 * right toolbar (flip / flash / timer / grid / mic), durations 15s-60s-10m,
 * PHOTO + TEXT modes, Add-sound, live preview with retake/next, and a
 * thumbnail shortcut back to the gallery.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraCaptureScreen(
    repository: ZevoraRepository,
    onVideoConfirmed: (Uri, Uri?) -> Unit,
    onOpenPicker: () -> Unit,
    onGoLive: () -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentUser by repository.currentUser.collectAsState()
    val mainExecutor = remember { ContextCompat.getMainExecutor(context) }

    fun hasCameraPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED

    fun hasAudioPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    var permissionGranted by remember { mutableStateOf(hasCameraPermission()) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        permissionGranted = hasCameraPermission()
    }

    // ---- camera state ----
    var lensFacing by remember { mutableIntStateOf(CameraSelector.LENS_FACING_BACK) }
    var flashMode by remember { mutableIntStateOf(ImageCapture.FLASH_MODE_OFF) }
    var timerSec by remember { mutableIntStateOf(0) } // 0 | 3 | 10
    var gridOn by remember { mutableStateOf(false) }
    var micOn by remember { mutableStateOf(true) }
    var captureMode by remember { mutableStateOf("video") } // "video" | "photo" | "text"
    var maxDurationSec by remember { mutableIntStateOf(15) } // 15 | 60 | 600

    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    val previewUseCase = remember { Preview.Builder().build() }
    val imageCapture = remember {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()
    }
    val videoCapture = remember {
        val recorder = Recorder.Builder()
            .setQualitySelector(QualitySelector.from(Quality.HIGHEST))
            .build()
        VideoCapture.withOutput(recorder)
    }
    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }

    // ---- recording state ----
    var activeRecording by remember { mutableStateOf<Recording?>(null) }
    var isRecording by remember { mutableStateOf(false) }
    var recordStartMs by remember { mutableStateOf(0L) }
    var elapsedSec by remember { mutableFloatStateOf(0f) }
    var countingDown by remember { mutableIntStateOf(0) }
    var pendingVideoUri by remember { mutableStateOf<Uri?>(null) }
    var pendingPhotoUri by remember { mutableStateOf<Uri?>(null) }
    var busy by remember { mutableStateOf(false) }
    var busyText by remember { mutableStateOf("") }
    var showAudioSheet by remember { mutableStateOf(false) }
    var audioUri by remember { mutableStateOf<Uri?>(null) }
    var audioTitle by remember { mutableStateOf("") }
    var lastThumbUri by remember { mutableStateOf<Uri?>(null) }

    // ---- text mode ----
    var textContent by remember { mutableStateOf("") }
    var textStyle by remember { mutableIntStateOf(0) }

    fun stamp(): String =
        SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())

    fun bindCamera() {
        val provider = cameraProvider ?: return
        try {
            imageCapture.flashMode = flashMode
            previewUseCase.setSurfaceProvider(previewView.surfaceProvider)
            provider.unbindAll()
            camera = provider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.Builder().requireLensFacing(lensFacing).build(),
                previewUseCase,
                imageCapture,
                videoCapture
            )
            try {
                camera?.cameraControl?.enableTorch(false)
            } catch (_: Exception) {
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Camera unavailable: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission()) {
            permissionLauncher.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO))
        }
    }
    LaunchedEffect(permissionGranted) {
        if (permissionGranted && cameraProvider == null) {
            withContext(Dispatchers.IO) {
                try {
                    val provider = ProcessCameraProvider.getInstance(context).get()
                    withContext(Dispatchers.Main) {
                        cameraProvider = provider
                        bindCamera()
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Camera init failed: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }
    LaunchedEffect(lensFacing, flashMode) {
        if (cameraProvider != null) bindCamera()
    }
    DisposableEffect(Unit) {
        onDispose {
            try {
                activeRecording?.stop()
            } catch (_: Exception) {
            }
            try {
                cameraProvider?.unbindAll()
            } catch (_: Exception) {
            }
        }
    }

    // Recording clock + auto-stop at the selected max duration.
    LaunchedEffect(isRecording) {
        if (!isRecording) return@LaunchedEffect
        while (isRecording) {
            delay(120)
            val elapsed = (System.currentTimeMillis() - recordStartMs) / 1000f
            elapsedSec = elapsed
            if (elapsed >= maxDurationSec) {
                try {
                    activeRecording?.stop()
                } catch (_: Exception) {
                }
                break
            }
        }
    }

    fun startRecording() {
        if (isRecording || busy) return
        val outFile = File(context.cacheDir, "zevora_rec_${stamp()}.mp4")
        try {
            val outputOptions = FileOutputOptions.Builder(outFile).build()
            val recording = videoCapture.output
                .prepareRecording(context, outputOptions)
                .apply {
                    if (micOn && hasAudioPermission()) withAudioEnabled()
                }
                .start(mainExecutor) { event ->
                    when (event) {
                        is VideoRecordEvent.Start -> {
                            recordStartMs = System.currentTimeMillis()
                            isRecording = true
                            if (flashMode == ImageCapture.FLASH_MODE_ON) {
                                try {
                                    camera?.cameraControl?.enableTorch(true)
                                } catch (_: Exception) {
                                }
                            }
                        }
                        is VideoRecordEvent.Finalize -> {
                            isRecording = false
                            activeRecording = null
                            try {
                                camera?.cameraControl?.enableTorch(false)
                            } catch (_: Exception) {
                            }
                            if (!event.hasError()) {
                                pendingVideoUri = event.outputResults.outputUri
                                lastThumbUri = event.outputResults.outputUri
                            } else {
                                Toast.makeText(
                                    context,
                                    "Recording failed: ${event.error}",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                        else -> Unit
                    }
                }
            activeRecording = recording
        } catch (e: Exception) {
            Toast.makeText(context, "Recording failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    fun stopRecording() {
        try {
            activeRecording?.stop()
        } catch (_: Exception) {
            isRecording = false
        }
    }

    fun onShutter() {
        if (busy || pendingVideoUri != null || pendingPhotoUri != null) return
        if (captureMode == "video") {
            if (isRecording) {
                stopRecording()
                return
            }
            if (timerSec > 0) {
                scope.launch {
                    countingDown = timerSec
                    while (countingDown > 0) {
                        delay(1000)
                        countingDown--
                    }
                    startRecording()
                }
            } else {
                startRecording()
            }
        } else if (captureMode == "photo") {
            busy = true
            busyText = "Capturing…"
            val outFile = File(context.cacheDir, "zevora photo_${stamp()}.jpg")
            imageCapture.takePicture(
                ImageCapture.OutputFileOptions.Builder(outFile).build(),
                mainExecutor,
                object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                        scope.launch(Dispatchers.IO) {
                            val galleryUri = saveImageToGallery(context, outFile)
                            withContext(Dispatchers.Main) {
                                busy = false
                                pendingPhotoUri = galleryUri ?: Uri.fromFile(outFile)
                                lastThumbUri = pendingPhotoUri
                                Toast.makeText(context, "Photo saved to gallery", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }

                    override fun onError(exc: ImageCaptureException) {
                        busy = false
                        Toast.makeText(context, "Capture failed: ${exc.message}", Toast.LENGTH_LONG).show()
                    }
                }
            )
        }
    }

    fun confirmVideo(uri: Uri) {
        val sound = audioUri
        if (sound == null) {
            onVideoConfirmed(uri, null)
            return
        }
        busy = true
        busyText = "Mixing sound…"
        scope.launch(Dispatchers.IO) {
            val mixed = File(context.cacheDir, "zevora_mixed_${stamp()}.mp4")
            val result = AudioMuxer.replaceAudio(context, uri, sound, mixed)
            withContext(Dispatchers.Main) {
                busy = false
                if (result.ok) {
                    onVideoConfirmed(Uri.fromFile(mixed), sound)
                } else {
                    Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    fun makeTextClip() {
        if (textContent.isBlank()) {
            Toast.makeText(context, "Write something first", Toast.LENGTH_SHORT).show()
            return
        }
        busy = true
        busyText = "Creating text clip…"
        scope.launch(Dispatchers.IO) {
            val out = File(context.cacheDir, "zevora_text_${stamp()}.mp4")
            val result = TextClipEncoder.encodeTextClip(textContent, textStyle, 3, out)
            withContext(Dispatchers.Main) {
                busy = false
                if (result.ok) {
                    pendingVideoUri = Uri.fromFile(out)
                    lastThumbUri = pendingVideoUri
                } else {
                    Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    fun makePhotoClip(photoUri: Uri) {
        busy = true
        busyText = "Creating photo clip…"
        scope.launch(Dispatchers.IO) {
            try {
                val bitmap = decodeSampledBitmap(context, photoUri, 1080)
                if (bitmap == null) {
                    withContext(Dispatchers.Main) {
                        busy = false
                        Toast.makeText(context, "Could not read this photo", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }
                val out = File(context.cacheDir, "zevora_photo_${stamp()}.mp4")
                val result = TextClipEncoder.encodePhotoClip(bitmap, 3, out)
                try {
                    bitmap.recycle()
                } catch (_: Exception) {
                }
                withContext(Dispatchers.Main) {
                    busy = false
                    if (result.ok) {
                        pendingPhotoUri = null
                        pendingVideoUri = Uri.fromFile(out)
                        lastThumbUri = pendingVideoUri
                    } else {
                        Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    busy = false
                    Toast.makeText(context, "Photo clip failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        if (!permissionGranted) {
            CameraPermissionPanel(onGrant = {
                permissionLauncher.launch(
                    arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
                )
            })
        } else {
            AndroidView(
                factory = { previewView },
                modifier = Modifier.fillMaxSize()
            )
            if (gridOn) {
                GridOverlay()
            }

            // ---- top bar ----
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 44.dp, start = 8.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = {
                    try {
                        activeRecording?.stop()
                    } catch (_: Exception) {
                    }
                    onClose()
                }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
                }
                Spacer(Modifier.weight(1f))
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color.Black.copy(alpha = 0.45f))
                        .clickable { showAudioSheet = true }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.MusicNote, null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        if (audioUri == null) "Add sound" else audioTitle.ifBlank { "Sound ✓" }.take(22),
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.weight(1f))
                Spacer(Modifier.width(48.dp))
            }

            // ---- recording indicator ----
            if (isRecording) {
                Row(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 100.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.Black.copy(alpha = 0.55f))
                        .padding(horizontal = 14.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(ZevoraRed))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        formatDuration((elapsedSec * 1000).toLong()) + " / " + formatDuration(maxDurationSec * 1000L),
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // ---- right toolbar ----
            if (pendingVideoUri == null && pendingPhotoUri == null && captureMode != "text") {
                Column(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    ToolbarButton(
                        icon = Icons.Default.Cameraswitch,
                        label = "Flip",
                        onClick = {
                            lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                                CameraSelector.LENS_FACING_FRONT
                            } else {
                                CameraSelector.LENS_FACING_BACK
                            }
                        }
                    )
                    ToolbarButton(
                        icon = when (flashMode) {
                            ImageCapture.FLASH_MODE_ON -> Icons.Default.FlashOn
                            ImageCapture.FLASH_MODE_AUTO -> Icons.Default.FlashAuto
                            else -> Icons.Default.FlashOff
                        },
                        label = "Flash",
                        active = flashMode != ImageCapture.FLASH_MODE_OFF,
                        onClick = {
                            flashMode = when (flashMode) {
                                ImageCapture.FLASH_MODE_OFF -> ImageCapture.FLASH_MODE_ON
                                ImageCapture.FLASH_MODE_ON -> ImageCapture.FLASH_MODE_AUTO
                                else -> ImageCapture.FLASH_MODE_OFF
                            }
                        }
                    )
                    ToolbarButton(
                        icon = when (timerSec) {
                            3 -> Icons.Default.Timer3
                            10 -> Icons.Default.Timer10
                            else -> Icons.Default.Timer
                        },
                        label = if (timerSec == 0) "Timer" else "${timerSec}s",
                        active = timerSec > 0,
                        onClick = {
                            timerSec = when (timerSec) {
                                0 -> 3
                                3 -> 10
                                else -> 0
                            }
                        }
                    )
                    ToolbarButton(
                        icon = Icons.Default.GridOn,
                        label = "Grid",
                        active = gridOn,
                        onClick = { gridOn = !gridOn }
                    )
                    ToolbarButton(
                        icon = if (micOn) Icons.Default.Mic else Icons.Default.MicOff,
                        label = "Mic",
                        active = micOn,
                        onClick = {
                            if (!micOn && !hasAudioPermission()) {
                                permissionLauncher.launch(
                                    arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
                                )
                            }
                            micOn = !micOn
                        }
                    )
                }
            }

            // ---- countdown ----
            if (countingDown > 0) {
                Text(
                    "$countingDown",
                    color = Color.White,
                    fontSize = 92.sp,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.align(Alignment.Center)
                )
            }

            // ---- bottom controls ----
            if (pendingVideoUri == null && pendingPhotoUri == null) {
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(bottom = 34.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (captureMode == "text") {
                        TextComposerPanel(
                            text = textContent,
                            onTextChange = { textContent = it },
                            style = textStyle,
                            onStyleChange = { textStyle = it },
                            onCreate = { makeTextClip() }
                        )
                        Spacer(Modifier.height(14.dp))
                    } else if (captureMode == "video") {
                        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                            DurationChip("10m", maxDurationSec == 600) { maxDurationSec = 600 }
                            DurationChip("60s", maxDurationSec == 60) { maxDurationSec = 60 }
                            DurationChip("15s", maxDurationSec == 15) { maxDurationSec = 15 }
                        }
                        Spacer(Modifier.height(16.dp))
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 22.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // thumbnail -> gallery
                        Box(
                            modifier = Modifier
                                .size(46.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(ZevoraDarkElevated)
                                .border(1.dp, Color.White.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                .clickable(onClick = onOpenPicker),
                            contentAlignment = Alignment.Center
                        ) {
                            val thumb = lastThumbUri
                            if (thumb != null) {
                                AsyncImage(
                                    model = thumb,
                                    contentDescription = "Gallery",
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Text("▦", color = Color.White, fontSize = 20.sp)
                            }
                        }
                        // shutter
                        if (captureMode != "text") {
                            ShutterButton(
                                isRecording = isRecording,
                                isPhoto = captureMode == "photo",
                                progress = if (maxDurationSec > 0) (elapsedSec / maxDurationSec).coerceIn(0f, 1f) else 0f,
                                onClick = { onShutter() }
                            )
                        } else {
                            Spacer(Modifier.width(76.dp))
                        }
                        // modes
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            MiniMode("LIVE", false) { onGoLive() }
                            MiniMode("POST", false) {
                                Toast.makeText(context, "Capture or pick a video first", Toast.LENGTH_SHORT).show()
                            }
                            MiniMode("CREATE", true) { }
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    // capture mode switcher
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color.Black.copy(alpha = 0.5f))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        CaptureModeChip("VIDEO", captureMode == "video") { captureMode = "video" }
                        CaptureModeChip("PHOTO", captureMode == "photo") { captureMode = "photo" }
                        CaptureModeChip("TEXT", captureMode == "text") { captureMode = "text" }
                    }
                }
            }

            // ---- video preview ----
            pendingVideoUri?.let { uri ->
                Box(
                    modifier = Modifier.fillMaxSize().background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    VideoPlayerView(
                        videoUrl = uri.toString(),
                        thumbnailUrl = "",
                        isCurrentPage = true,
                        videoId = null,
                        modifier = Modifier.fillMaxSize()
                    )
                    IconButton(
                        onClick = { pendingVideoUri = null },
                        modifier = Modifier.align(Alignment.TopStart).padding(top = 44.dp, start = 8.dp)
                    ) {
                        Icon(Icons.Default.Close, "Retake", tint = Color.White)
                    }
                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 40.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Button(
                            onClick = { pendingVideoUri = null },
                            colors = ButtonDefaults.buttonColors(containerColor = ZevoraDarkElevated),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Retake")
                        }
                        Button(
                            onClick = { confirmVideo(uri) },
                            colors = ButtonDefaults.buttonColors(containerColor = ZevoraRed),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(if (audioUri == null) "Next" else "Mix & Next", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // ---- photo preview ----
            pendingPhotoUri?.let { uri ->
                Box(
                    modifier = Modifier.fillMaxSize().background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = uri,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize()
                    )
                    IconButton(
                        onClick = { pendingPhotoUri = null },
                        modifier = Modifier.align(Alignment.TopStart).padding(top = 44.dp, start = 8.dp)
                    ) {
                        Icon(Icons.Default.Close, "Retake", tint = Color.White)
                    }
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 40.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = { makePhotoClip(uri) },
                            colors = ButtonDefaults.buttonColors(containerColor = ZevoraRed),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Post as 3s clip", fontWeight = FontWeight.Bold)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                onClick = { pendingPhotoUri = null },
                                colors = ButtonDefaults.buttonColors(containerColor = ZevoraDarkElevated),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Retake")
                            }
                            Button(
                                onClick = {
                                    val me = currentUser
                                    if (me == null) {
                                        Toast.makeText(context, "Log in first", Toast.LENGTH_SHORT).show()
                                        return@Button
                                    }
                                    if (!repository.firebaseService.isFirebaseAvailable) {
                                        Toast.makeText(
                                            context,
                                            "Cloud storage unavailable — photo is in your gallery",
                                            Toast.LENGTH_LONG
                                        ).show()
                                        return@Button
                                    }
                                    busy = true
                                    busyText = "Updating photo…"
                                    scope.launch {
                                        try {
                                            val result = repository.updateProfile(
                                                displayName = me.displayName,
                                                username = me.username,
                                                bio = me.bio,
                                                avatarUri = uri
                                            )
                                            Toast.makeText(
                                                context,
                                                if (result.isSuccess) "Profile photo updated" else "Update failed",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                            if (result.isSuccess) pendingPhotoUri = null
                                        } finally {
                                            busy = false
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = ZevoraDarkElevated),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Use as avatar")
                            }
                        }
                    }
                }
            }

            // ---- busy overlay ----
            if (busy) {
                Box(
                    modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = ZevoraRed)
                        Spacer(Modifier.height(12.dp))
                        Text(busyText, color = Color.White, fontWeight = FontWeight.SemiBold)
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
                }
                showAudioSheet = false
            }
        )
    }
}

@Composable
private fun ToolbarButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    active: Boolean = false,
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(if (active) Color.White else Color.Black.copy(alpha = 0.45f))
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                label,
                tint = if (active) Color.Black else Color.White,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(Modifier.height(3.dp))
        Text(label, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun DurationChip(label: String, active: Boolean, onClick: () -> Unit) {
    Text(
        label,
        color = if (active) Color.Black else Color.White,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (active) Color.White else Color.Black.copy(alpha = 0.45f))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp)
    )
}

@Composable
private fun CaptureModeChip(label: String, active: Boolean, onClick: () -> Unit) {
    Text(
        label,
        color = if (active) Color.Black else Color.White.copy(alpha = 0.85f),
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (active) Color.White else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 7.dp)
    )
}

@Composable
private fun MiniMode(label: String, active: Boolean, onClick: () -> Unit) {
    Text(
        label,
        color = if (active) Color.White else TextMuted,
        fontSize = 11.sp,
        fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 5.dp)
    )
}

@Composable
private fun ShutterButton(isRecording: Boolean, isPhoto: Boolean, progress: Float, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(78.dp)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(Color.White.copy(alpha = 0.35f), radius = size.minDimension / 2, style = Stroke(5.dp.toPx()))
            if (isRecording) {
                drawArc(
                    Color.White,
                    startAngle = -90f,
                    sweepAngle = 360f * progress,
                    useCenter = false,
                    style = Stroke(5.dp.toPx(), cap = StrokeCap.Round)
                )
            }
        }
        Box(
            modifier = Modifier
                .size(62.dp)
                .clip(CircleShape)
                .background(Color.White)
                .border(3.dp, Color.Black.copy(alpha = 0.15f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (isRecording) {
                Box(
                    Modifier.size(26.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(ZevoraRed)
                )
            } else {
                Box(
                    Modifier.size(if (isPhoto) 52.dp else 52.dp)
                        .clip(CircleShape)
                        .background(ZevoraRed)
                )
            }
        }
    }
}

@Composable
private fun GridOverlay() {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val color = Color.White.copy(alpha = 0.35f)
        val stroke = 1.5.dp.toPx()
        for (i in 1..2) {
            drawLine(color, Offset(w * i / 3, 0f), Offset(w * i / 3, h), stroke)
            drawLine(color, Offset(0f, h * i / 3), Offset(w, h * i / 3), stroke)
        }
    }
}

@Composable
private fun CameraPermissionPanel(onGrant: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Camera access needed", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Spacer(Modifier.height(8.dp))
        Text(
            "Allow the camera (and microphone for sound) to record clips.",
            color = TextSecondary,
            fontSize = 13.sp
        )
        Spacer(Modifier.height(18.dp))
        Button(
            onClick = onGrant,
            colors = ButtonDefaults.buttonColors(containerColor = ZevoraRed)
        ) {
            Text("Allow camera", fontWeight = FontWeight.Bold)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TextComposerPanel(
    text: String,
    onTextChange: (String) -> Unit,
    style: Int,
    onStyleChange: (Int) -> Unit,
    onCreate: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 22.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.Black.copy(alpha = 0.55f))
            .padding(14.dp)
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = { onTextChange(it.take(280)) },
            placeholder = { Text("Write your text…", color = TextMuted) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            maxLines = 4,
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = ZevoraRed,
                unfocusedBorderColor = Color.White.copy(alpha = 0.25f)
            )
        )
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextClipEncoder.STYLE_NAMES.forEachIndexed { index, name ->
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            if (style == index) Color.White else Color.White.copy(alpha = 0.15f)
                        )
                        .clickable { onStyleChange(index) }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (style == index) {
                        Icon(Icons.Default.Check, null, tint = Color.Black, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                    }
                    Text(
                        name,
                        color = if (style == index) Color.Black else Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.width(8.dp))
            }
            Spacer(Modifier.weight(1f))
            Button(
                onClick = onCreate,
                colors = ButtonDefaults.buttonColors(containerColor = ZevoraRed),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Create", fontWeight = FontWeight.Bold)
            }
        }
    }
}

/** Copies a captured JPEG into the public gallery, returning its MediaStore Uri. */
private fun saveImageToGallery(context: android.content.Context, file: File): Uri? {
    return try {
        val name = "Rivo_${System.currentTimeMillis()}.jpg"
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "DCIM/Rivo")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }
        val uri = context.contentResolver.insert(collection, values) ?: return null
        context.contentResolver.openOutputStream(uri)?.use { out ->
            file.inputStream().use { it.copyTo(out) }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            context.contentResolver.update(uri, values, null, null)
        } else {
            @Suppress("DEPRECATION")
            MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), arrayOf("image/jpeg"), null)
        }
        uri
    } catch (_: Exception) {
        null
    }
}

/** Memory-safe bitmap decode capped at [maxDim] pixels on the long edge. */
private fun decodeSampledBitmap(context: android.content.Context, uri: Uri, maxDim: Int): Bitmap? {
    return try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, bounds)
        }
        var sample = 1
        val longEdge = maxOf(bounds.outWidth, bounds.outHeight)
        while (longEdge / (sample * 2) >= maxDim) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, opts)
        }
    } catch (_: Exception) {
        null
    }
}
