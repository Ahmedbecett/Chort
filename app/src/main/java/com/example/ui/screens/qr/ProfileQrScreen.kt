package com.example.ui.screens.qr

import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.repository.ZevoraRepository
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.ZevoraDarkBg
import com.example.ui.theme.ZevoraDarkElevated
import com.example.ui.theme.ZevoraRed
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Rivo 3.1.0 — the real profile QR code: generated on-device with ZXing,
 * encoding this user's identity payload. Save-to-gallery and share both work.
 */
@Composable
fun ProfileQrScreen(
    repository: ZevoraRepository,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentUser by repository.currentUser.collectAsState()

    val payload = remember(currentUser) {
        val id = currentUser?.id ?: "unknown"
        val username = currentUser?.username ?: "unknown"
        "zevora:user:$id:$username"
    }
    var qrBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var working by remember { mutableStateOf(false) }

    LaunchedEffect(payload) {
        scope.launch(Dispatchers.Default) {
            val bitmap = try {
                val matrix = QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, 640, 640)
                val pixels = IntArray(640 * 640)
                for (y in 0 until 640) {
                    for (x in 0 until 640) {
                        pixels[y * 640 + x] = if (matrix.get(x, y)) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
                    }
                }
                Bitmap.createBitmap(640, 640, Bitmap.Config.ARGB_8888).apply {
                    setPixels(pixels, 0, 640, 0, 0, 640, 640)
                }
            } catch (_: Exception) {
                null
            }
            withContext(Dispatchers.Main) { qrBitmap = bitmap }
        }
    }

    fun saveQrToGallery(): Uri? {
        val bitmap = qrBitmap ?: return null
        return try {
            val name = "Rivo-QR-${currentUser?.username ?: "me"}.png"
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, name)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
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
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                context.contentResolver.update(uri, values, null, null)
            }
            uri
        } catch (_: Exception) {
            null
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ZevoraDarkBg)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 48.dp, start = 8.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
            }
            Text("Your QR code", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(10.dp))

        Box(
            modifier = Modifier
                .size(92.dp)
                .clip(CircleShape)
                .background(ZevoraDarkElevated),
            contentAlignment = Alignment.Center
        ) {
            val avatar = currentUser?.avatarUrl ?: ""
            if (avatar.isNotBlank()) {
                AsyncImage(
                    model = avatar,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(Icons.Default.Person, null, tint = TextMuted, modifier = Modifier.size(46.dp))
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            currentUser?.displayName ?: "",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp
        )
        Text("@${currentUser?.username ?: "unknown"}", color = TextSecondary, fontSize = 14.sp)
        Spacer(Modifier.height(22.dp))

        Box(
            modifier = Modifier
                .size(264.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Color.White)
                .padding(20.dp),
            contentAlignment = Alignment.Center
        ) {
            val bitmap = qrBitmap
            if (bitmap != null) {
                Image(bitmap = bitmap.asImageBitmap(), contentDescription = "Profile QR code")
            } else {
                CircularProgressIndicator(color = ZevoraRed)
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            "Scan with any camera to open this Rivo profile",
            color = TextMuted,
            fontSize = 12.sp
        )
        Spacer(Modifier.height(22.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = {
                    working = true
                    scope.launch(Dispatchers.IO) {
                        val uri = saveQrToGallery()
                        withContext(Dispatchers.Main) {
                            working = false
                            Toast.makeText(
                                context,
                                if (uri != null) "QR code saved to gallery" else "Save failed",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                },
                enabled = !working && qrBitmap != null,
                colors = ButtonDefaults.buttonColors(containerColor = ZevoraDarkElevated),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Download, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Save")
            }
            Button(
                onClick = {
                    working = true
                    scope.launch(Dispatchers.IO) {
                        val uri = saveQrToGallery()
                        withContext(Dispatchers.Main) {
                            working = false
                            if (uri != null) {
                                try {
                                    val share = Intent(Intent.ACTION_SEND).apply {
                                        type = "image/png"
                                        putExtra(Intent.EXTRA_STREAM, uri)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(Intent.createChooser(share, "Share QR code"))
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Share failed: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                            } else {
                                Toast.makeText(context, "Share failed", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                },
                enabled = !working && qrBitmap != null,
                colors = ButtonDefaults.buttonColors(containerColor = ZevoraRed),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Share, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Share", fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(
            payload,
            color = TextMuted,
            fontSize = 11.sp,
            modifier = Modifier.padding(horizontal = 32.dp)
        )
        Spacer(Modifier.height(40.dp))
    }
}
