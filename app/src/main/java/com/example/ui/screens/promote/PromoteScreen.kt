package com.example.ui.screens.promote

import android.widget.Toast
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import coil.compose.AsyncImage
import com.example.data.repository.ZevoraRepository
import com.example.ui.theme.AccentGold
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.ZevoraDarkBg
import com.example.ui.theme.ZevoraDarkSurface
import com.example.ui.theme.ZevoraRed
import com.example.util.AppPrefs
import java.text.SimpleDateFormat
import kotlinx.coroutines.launch
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Rivo 3.1.0 — Promote: spend earned coins on real video Boosts.
 * A boosted video jumps to the front of your profile grid with a BOOSTED
 * badge until the package expires. No real money is involved, ever.
 */
@Composable
fun PromoteScreen(
    repository: ZevoraRepository,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentUser by repository.currentUser.collectAsState()
    val meId = currentUser?.id ?: ""
    val myVideos by repository.getVideosByCreator(meId).collectAsState(initial = emptyList())
    val coins by AppPrefs.coins.collectAsState()

    var selectedVideoId by remember { mutableStateOf<String?>(null) }
    var selectedPackage by remember { mutableIntStateOf(0) }
    var boosts by remember { mutableStateOf(AppPrefs.getBoosts()) }

    val packages = remember {
        listOf(
            Triple("Spark", 100, 7),
            Triple("Surge", 250, 30),
            Triple("Blaze", 500, 90)
        )
    }

    fun refresh() {
        boosts = AppPrefs.getBoosts()
    }

    Column(modifier = Modifier.fillMaxSize().background(ZevoraDarkBg)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 48.dp, start = 8.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
            }
            Text("Promote", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(AccentGold.copy(alpha = 0.15f))
                    .padding(horizontal = 12.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.RocketLaunch, null, tint = AccentGold, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("$coins coins", color = AccentGold, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            item {
                Text(
                    "1 · Choose a video",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    modifier = Modifier.padding(start = 20.dp, top = 12.dp, bottom = 8.dp)
                )
            }
            if (myVideos.isEmpty()) {
                item {
                    Text(
                        "Publish a video first — then come back to promote it.",
                        color = TextMuted,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                    )
                }
            }
            items(myVideos, key = { it.id }) { video ->
                val selected = selectedVideoId == video.id
                val boosted = AppPrefs.isBoosted(video.id)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 5.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(ZevoraDarkSurface)
                        .border(
                            if (selected) 2.dp else 0.dp,
                            if (selected) ZevoraRed else Color.Transparent,
                            RoundedCornerShape(14.dp)
                        )
                        .clickable { selectedVideoId = if (selected) null else video.id }
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AsyncImage(
                        model = video.thumbnailUrl.ifBlank { video.videoUrl },
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .width(52.dp)
                            .aspectRatio(0.75f)
                            .clip(RoundedCornerShape(8.dp))
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            video.caption.ifBlank { "Untitled" },
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            "${video.viewsCount} views • ${video.likesCount} likes" +
                                if (boosted) " • BOOSTED" else "",
                            color = if (boosted) AccentGold else TextSecondary,
                            fontSize = 11.sp
                        )
                    }
                    if (selected) {
                        Box(
                            modifier = Modifier
                                .size(26.dp)
                                .clip(CircleShape)
                                .background(ZevoraRed),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
            item {
                Text(
                    "2 · Choose a package",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 8.dp)
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    packages.forEachIndexed { index, (name, cost, days) ->
                        val active = selectedPackage == index
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(14.dp))
                                .background(ZevoraDarkSurface)
                                .border(
                                    if (active) 2.dp else 0.dp,
                                    if (active) AccentGold else Color.Transparent,
                                    RoundedCornerShape(14.dp)
                                )
                                .clickable { selectedPackage = index }
                                .padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Spacer(Modifier.height(4.dp))
                            Text("$cost", color = AccentGold, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                            Text("coins", color = TextMuted, fontSize = 11.sp)
                            Spacer(Modifier.height(4.dp))
                            Text("$days days first", color = TextSecondary, fontSize = 11.sp)
                        }
                    }
                }
                val (pkgName, pkgCost, pkgDays) = packages[selectedPackage]
                Button(
                    onClick = {
                        val vid = selectedVideoId
                        if (vid == null) {
                            Toast.makeText(context, "Choose a video first", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        val label = myVideos.firstOrNull { it.id == vid }
                            ?.caption?.ifBlank { "video" }?.take(30) ?: "video"
                        scope.launch {
                            val spend = repository.spendCoins(pkgCost, "boost:$label")
                            if (spend.isSuccess) {
                                AppPrefs.spendCoins(pkgCost, "boost:$label")
                                AppPrefs.addBoost(vid, pkgDays)
                                refresh()
                                Toast.makeText(context, "$pkgName boost active for $pkgDays days", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(
                                    context,
                                    spend.exceptionOrNull()?.message ?: "Not enough coins (need $pkgCost)",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ZevoraRed),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                ) {
                    Icon(Icons.Default.RocketLaunch, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Boost for $pkgCost coins", fontWeight = FontWeight.Bold)
                }
                Text(
                    "Boosted videos appear first on your profile with a badge. " +
                        "Coins are earned in-app and have no cash value. No refunds on cancelled boosts.",
                    color = TextMuted,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(horizontal = 20.dp)
                )
                Text(
                    "Active boosts (${boosts.size})",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    modifier = Modifier.padding(start = 20.dp, top = 18.dp, bottom = 4.dp)
                )
            }
            if (boosts.isEmpty()) {
                item {
                    Text(
                        "No active boosts.",
                        color = TextMuted,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                    )
                    Spacer(Modifier.height(40.dp))
                }
            }
            items(boosts, key = { it.videoId }) { boost ->
                val video = myVideos.firstOrNull { it.id == boost.videoId }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 5.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(ZevoraDarkSurface)
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (video != null) {
                        AsyncImage(
                            model = video.thumbnailUrl.ifBlank { video.videoUrl },
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .width(44.dp)
                                .aspectRatio(0.75f)
                                .clip(RoundedCornerShape(8.dp))
                        )
                        Spacer(Modifier.width(12.dp))
                    }
                    Column(Modifier.weight(1f)) {
                        Text(
                            video?.caption?.ifBlank { "Untitled" } ?: "Removed video",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        val remaining = boost.expiresAt - System.currentTimeMillis()
                        val daysLeft = TimeUnit.MILLISECONDS.toDays(remaining.coerceAtLeast(0))
                        Text(
                            if (video == null) "Video deleted — boost will expire naturally"
                            else "Expires ${SimpleDateFormat("MMM d", Locale.US).format(Date(boost.expiresAt))} ($daysLeft days left)",
                            color = TextSecondary,
                            fontSize = 11.sp
                        )
                    }
                    IconButton(onClick = {
                        AppPrefs.removeBoost(boost.videoId)
                        refresh()
                        Toast.makeText(context, "Boost cancelled", Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(Icons.Default.Close, "Cancel boost", tint = TextMuted)
                    }
                }
            }
            item { Spacer(Modifier.height(40.dp)) }
        }
    }
}
