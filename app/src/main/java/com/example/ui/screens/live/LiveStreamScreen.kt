package com.example.ui.screens.live

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
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
import com.example.ui.theme.AccentGold
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.ZevoraBorder
import com.example.ui.theme.ZevoraCyan
import com.example.ui.theme.ZevoraDarkBg
import com.example.ui.theme.ZevoraDarkElevated
import com.example.ui.theme.ZevoraDarkSurface
import com.example.ui.theme.ZevoraRed
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class LiveChatMessage(
    val username: String,
    val message: String,
    val badgeColor: Color = ZevoraCyan
)

data class LiveGift(
    val id: String,
    val name: String,
    val emoji: String,
    val coins: Int
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveStreamScreen(
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var viewerCount by remember { mutableIntStateOf(14820) }
    var userCoins by remember { mutableIntStateOf(2500) }
    var showGiftSheet by remember { mutableStateOf(false) }
    var activeGiftBurst by remember { mutableStateOf<LiveGift?>(null) }

    var commentInput by remember { mutableStateOf("") }
    val chatMessages = remember {
        mutableStateListOf(
            LiveChatMessage("sara_vibe", "Welcome to the LIVE stream everyone!! 🔥", ZevoraCyan),
            LiveChatMessage("dj_marcus", "The sound quality is unreal today 🎧", ZevoraRed),
            LiveChatMessage("lina_dance", "Can you show the next choreo move?? ✨", AccentGold),
            LiveChatMessage("ahmed_99", "Greetings from Dubai! Amazing vibes 🌴", ZevoraCyan)
        )
    }

    // Auto-feed incoming live comments for authentic TikTok feel
    LaunchedEffect(Unit) {
        val extraComments = listOf(
            LiveChatMessage("skater_boy", "Let's win this PK battle!! 🚀"),
            LiveChatMessage("nora_fashion", "Dropped 5 roses! Keep it up 🌹"),
            LiveChatMessage("tech_guru", "Followed the host! 💫"),
            LiveChatMessage("zack_beats", "BASS BOOSTED TO THE MAX 🔊"),
            LiveChatMessage("yasmin_art", "This is so fun to watch haha ❤️")
        )
        for (msg in extraComments) {
            delay(3500)
            chatMessages.add(msg)
            if (chatMessages.size > 20) chatMessages.removeAt(0)
            viewerCount += (1..5).random()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Simulated Live Video Feed Background
        AsyncImage(
            model = "https://images.unsplash.com/photo-1516450360452-9312f5e86fc7?w=1000",
            contentDescription = "Host Live Broadcast",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        // Gradient overlay for contrast
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.6f),
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.85f)
                        )
                    )
                )
        )

        // TOP LIVE HEADER
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Host Pill
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(end = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AsyncImage(
                    model = "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=300",
                    contentDescription = null,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Column {
                    Text(
                        text = "Pulse Live Studio",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(ZevoraRed)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${viewerCount / 1000}k viewers",
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 10.sp
                        )
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(ZevoraRed)
                        .clickable { Toast.makeText(context, "Followed Live Host!", Toast.LENGTH_SHORT).show() }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text("+ Follow", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }

            // Right actions (Close)
            IconButton(
                onClick = onClose,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.5f))
                    .testTag("live_close_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Exit Live",
                    tint = Color.White
                )
            }
        }

        // TIKTOK PK BATTLE BAR
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 90.dp, start = 16.dp, end = 16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = "🔥 PK BATTLE • LIVE", color = ZevoraCyan, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                Text(text = "02:45 left", color = AccentGold, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
            ) {
                Box(
                    modifier = Modifier
                        .weight(0.64f)
                        .background(ZevoraCyan)
                )
                Box(
                    modifier = Modifier
                        .weight(0.36f)
                        .background(ZevoraRed)
                )
            }
        }

        // CHAT STREAM OVERLAY (BOTTOM LEFT)
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 12.dp, bottom = 76.dp, end = 80.dp)
                .fillMaxWidth(0.85f)
        ) {
            LazyColumn(
                modifier = Modifier.height(180.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(chatMessages) { chat ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.Black.copy(alpha = 0.55f))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = chat.username,
                                color = chat.badgeColor,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = chat.message,
                                color = Color.White,
                                fontSize = 11.5.sp
                            )
                        }
                    }
                }
            }
        }

        // BOTTOM ACTION BAR
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Chat Input
            OutlinedTextField(
                value = commentInput,
                onValueChange = { commentInput = it },
                placeholder = { Text("Send a comment...", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent,
                    focusedContainerColor = Color.Black.copy(alpha = 0.6f),
                    unfocusedContainerColor = Color.Black.copy(alpha = 0.6f)
                ),
                shape = RoundedCornerShape(20.dp),
                singleLine = true,
                modifier = Modifier
                    .weight(1f)
                    .height(46.dp),
                trailingIcon = {
                    if (commentInput.isNotBlank()) {
                        IconButton(
                            onClick = {
                                chatMessages.add(LiveChatMessage("You", commentInput.trim(), ZevoraCyan))
                                commentInput = ""
                            }
                        ) {
                            Icon(Icons.Default.Send, contentDescription = "Send", tint = ZevoraCyan)
                        }
                    }
                }
            )

            Spacer(modifier = Modifier.width(8.dp))

            // Gift Button (TikTok Iconic Gift Box)
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            listOf(ZevoraRed, ZevoraCyan)
                        )
                    )
                    .clickable { showGiftSheet = true }
                    .testTag("live_gift_button"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.CardGiftcard,
                    contentDescription = "Gifts",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        // FULL SCREEN DYNAMIC GIFT BURST ANIMATION OVERLAY
        AnimatedVisibility(
            visible = activeGiftBurst != null,
            enter = scaleIn(animationSpec = tween(300, easing = FastOutSlowInEasing)) + fadeIn(),
            exit = scaleOut(animationSpec = tween(300)) + fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            activeGiftBurst?.let { gift ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color.Black.copy(alpha = 0.85f))
                        .border(2.dp, ZevoraCyan, RoundedCornerShape(24.dp))
                        .padding(32.dp)
                ) {
                    Text(
                        text = gift.emoji,
                        fontSize = 80.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "SENT ${gift.name.uppercase()}!",
                        color = AccentGold,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = "${gift.coins} Coins Contribution ✨",
                        color = Color.White,
                        fontSize = 13.sp
                    )
                }
            }
        }

        // GIFTS BOTTOM SHEET
        if (showGiftSheet) {
            ModalBottomSheet(
                onDismissRequest = { showGiftSheet = false },
                containerColor = ZevoraDarkSurface
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "TikTok LIVE Gifts",
                            color = TextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(text = "🪙 $userCoins Coins", color = AccentGold, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    val gifts = listOf(
                        LiveGift("g1", "Rose", "🌹", 1),
                        LiveGift("g2", "TikTok Heart", "💖", 5),
                        LiveGift("g3", "Confetti", "🎉", 25),
                        LiveGift("g4", "Fireworks", "🎆", 99),
                        LiveGift("g5", "Galaxy Lion", "🦁", 999)
                    )

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(gifts) { gift ->
                            Card(
                                modifier = Modifier
                                    .width(85.dp)
                                    .clickable {
                                        if (userCoins >= gift.coins) {
                                            userCoins -= gift.coins
                                            activeGiftBurst = gift
                                            chatMessages.add(
                                                LiveChatMessage("You", "sent a ${gift.name} ${gift.emoji}!", AccentGold)
                                            )
                                            showGiftSheet = false
                                            scope.launch {
                                                delay(2500)
                                                activeGiftBurst = null
                                            }
                                        } else {
                                            Toast.makeText(context, "Insufficient Coins!", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = ZevoraDarkElevated),
                                border = androidx.compose.foundation.BorderStroke(1.dp, ZevoraBorder)
                            ) {
                                Column(
                                    modifier = Modifier.padding(10.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(text = gift.emoji, fontSize = 28.sp)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(text = gift.name, color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    Text(text = "🪙 ${gift.coins}", color = AccentGold, fontSize = 10.5.sp)
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(20.dp))
                }
            }
        }
    }
}
