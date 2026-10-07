package com.example.ui.screens.chat

import android.widget.Toast
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.ui.theme.StatusResolved
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.ZevoraBorder
import com.example.ui.theme.ZevoraCyan
import com.example.ui.theme.ZevoraDarkBg
import com.example.ui.theme.ZevoraDarkElevated
import com.example.ui.theme.ZevoraDarkSurface
import com.example.ui.theme.ZevoraRed

data class DmMessage(
    val id: String,
    val senderIsMe: Boolean,
    val text: String,
    val time: String
)

@Composable
fun DirectMessageScreen(
    recipientName: String = "Alex Rivera",
    recipientUsername: String = "alex_skates",
    recipientAvatar: String = "https://images.unsplash.com/photo-1539571696357-5a69c17a67c6?w=300",
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var inputMessage by remember { mutableStateOf("") }

    val messages = remember {
        mutableStateListOf(
            DmMessage("m1", false, "Hey! Loved your latest skate reel at Venice! 🔥", "10:14 AM"),
            DmMessage("m2", true, "Thanks Alex! The lighting was perfect right before sunset.", "10:16 AM"),
            DmMessage("m3", false, "Let's do a duet next week for the #skatechallenge! 🛹", "10:18 AM")
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ZevoraDarkBg)
            .statusBarsPadding()
            .imePadding()
    ) {
        // TOP APP BAR
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(ZevoraDarkSurface)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = TextPrimary
                )
            }

            Box(contentAlignment = Alignment.BottomEnd) {
                AsyncImage(
                    model = recipientAvatar,
                    contentDescription = recipientName,
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                )
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(StatusResolved)
                        .border(1.5.dp, ZevoraDarkSurface, CircleShape)
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = recipientName,
                    color = TextPrimary,
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "@$recipientUsername • Active now",
                    color = ZevoraCyan,
                    fontSize = 11.sp
                )
            }

            IconButton(onClick = { Toast.makeText(context, "More options", Toast.LENGTH_SHORT).show() }) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "Options",
                    tint = TextSecondary
                )
            }
        }

        // CHAT MESSAGE STREAM
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(messages, key = { it.id }) { msg ->
                val alignment = if (msg.senderIsMe) Alignment.End else Alignment.Start
                val bubbleColor = if (msg.senderIsMe) ZevoraRed else ZevoraDarkElevated
                val textColor = Color.White

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = alignment
                ) {
                    Box(
                        modifier = Modifier
                            .clip(
                                RoundedCornerShape(
                                    topStart = 16.dp,
                                    topEnd = 16.dp,
                                    bottomStart = if (msg.senderIsMe) 16.dp else 4.dp,
                                    bottomEnd = if (msg.senderIsMe) 4.dp else 16.dp
                                )
                            )
                            .background(bubbleColor)
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Text(
                            text = msg.text,
                            color = textColor,
                            fontSize = 13.5.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = msg.time,
                            color = TextMuted,
                            fontSize = 9.5.sp
                        )
                        if (msg.senderIsMe) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Default.DoneAll,
                                contentDescription = "Read",
                                tint = ZevoraCyan,
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }
                }
            }
        }

        // QUICK EMOJI BAR
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .background(ZevoraDarkSurface)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            val emojis = listOf("❤️", "🔥", "😂", "👏", "🛹", "✨", "💯", "😍")
            items(emojis) { emoji ->
                Text(
                    text = emoji,
                    fontSize = 20.sp,
                    modifier = Modifier
                        .clickable {
                            messages.add(
                                DmMessage(
                                    id = "m_${System.currentTimeMillis()}",
                                    senderIsMe = true,
                                    text = emoji,
                                    time = "Just now"
                                )
                            )
                        }
                        .padding(horizontal = 4.dp)
                )
            }
        }

        // INPUT BAR
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(ZevoraDarkSurface)
                .navigationBarsPadding()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { Toast.makeText(context, "Opening camera...", Toast.LENGTH_SHORT).show() }) {
                Icon(
                    imageVector = Icons.Default.PhotoCamera,
                    contentDescription = "Camera",
                    tint = TextSecondary
                )
            }

            OutlinedTextField(
                value = inputMessage,
                onValueChange = { inputMessage = it },
                placeholder = { Text("Send a message...", color = TextMuted, fontSize = 12.5.sp) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    focusedBorderColor = ZevoraBorder,
                    unfocusedBorderColor = ZevoraBorder,
                    focusedContainerColor = ZevoraDarkElevated,
                    unfocusedContainerColor = ZevoraDarkElevated
                ),
                shape = RoundedCornerShape(20.dp),
                singleLine = true,
                modifier = Modifier
                    .weight(1f)
                    .height(46.dp)
            )

            Spacer(modifier = Modifier.width(6.dp))

            if (inputMessage.isNotBlank()) {
                IconButton(
                    onClick = {
                        messages.add(
                            DmMessage(
                                id = "m_${System.currentTimeMillis()}",
                                senderIsMe = true,
                                text = inputMessage.trim(),
                                time = "Just now"
                            )
                        )
                        inputMessage = ""
                    }
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send",
                        tint = ZevoraCyan
                    )
                }
            } else {
                IconButton(onClick = { Toast.makeText(context, "Voice note recording...", Toast.LENGTH_SHORT).show() }) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = "Voice note",
                        tint = ZevoraRed
                    )
                }
            }
        }
    }
}
