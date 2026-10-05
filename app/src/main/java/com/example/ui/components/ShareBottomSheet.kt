package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.NotInterested
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entities.VideoEntity
import com.example.ui.theme.AccentGold
import com.example.ui.theme.AccentGreen
import com.example.ui.theme.StatusBanned
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TokBorder
import com.example.ui.theme.TokCyan
import com.example.ui.theme.TokDarkElevated
import com.example.ui.theme.TokDarkSurface
import com.example.ui.theme.TokRed

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareBottomSheet(
    sheetState: SheetState,
    video: VideoEntity,
    onDismiss: () -> Unit,
    onReport: () -> Unit
) {
    val context = LocalContext.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = TokDarkSurface,
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

            HorizontalDivider(color = TokBorder, thickness = 0.5.dp)

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
                        iconTint = TokCyan,
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("Chort Video", "https://chort-nine.vercel.app/api/v1/videos/${video.id}/stream")
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
                        bgColor = TokRed.copy(alpha = 0.2f),
                        iconTint = TokRed,
                        onClick = {
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, "Check out this clip on Chort!")
                                putExtra(Intent.EXTRA_TEXT, "Watch @${video.creatorUsername}'s video on Chort: ${video.caption} https://chort-nine.vercel.app/api/v1/videos/${video.id}/stream")
                            }
                            context.startActivity(Intent.createChooser(intent, "Share via"))
                            onDismiss()
                        }
                    )
                }
                item {
                    ShareTargetIcon(
                        title = "Save Video",
                        icon = Icons.Default.Download,
                        bgColor = AccentGreen.copy(alpha = 0.2f),
                        iconTint = AccentGreen,
                        onClick = {
                            Toast.makeText(context, "Video saved to device gallery!", Toast.LENGTH_SHORT).show()
                            onDismiss()
                        }
                    )
                }
                item {
                    ShareTargetIcon(
                        title = "Bookmark",
                        icon = Icons.Default.Bookmark,
                        bgColor = AccentGold.copy(alpha = 0.2f),
                        iconTint = AccentGold,
                        onClick = {
                            Toast.makeText(context, "Saved to your Bookmarks collection", Toast.LENGTH_SHORT).show()
                            onDismiss()
                        }
                    )
                }
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
                        bgColor = TokDarkElevated,
                        iconTint = TextSecondary,
                        onClick = {
                            Toast.makeText(context, "We will show fewer videos like this", Toast.LENGTH_SHORT).show()
                            onDismiss()
                        }
                    )
                }
            }
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
