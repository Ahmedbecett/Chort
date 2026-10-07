package com.example.ui.screens.wallet

import android.widget.Toast
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.VideoCall
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.repository.ZevoraRepository
import com.example.ui.theme.AccentGold
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.ZevoraCyan
import com.example.ui.theme.ZevoraDarkBg
import com.example.ui.theme.ZevoraDarkElevated
import com.example.ui.theme.ZevoraDarkSurface
import com.example.ui.theme.ZevoraRed
import com.example.util.AppPrefs
import java.text.SimpleDateFormat
import kotlinx.coroutines.launch
import java.util.Date
import java.util.Locale

/**
 * Rivo 3.1.0 — Balance & Coins. Server-synced ledger with offline queue:
 * when logged in the shown balance is the server truth (synced across
 * devices); guests and offline time fall back to the on-device ledger. Coins
 * genuine in-app activity (check-ins, watching, publishing) and spent on
 * video Boosts. There is deliberately no cash withdrawal or top-up — coins
 * have no cash value, and the screen says so honestly.
 */
@Composable
fun WalletScreen(
    repository: ZevoraRepository,
    onOpenPromote: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentUser by repository.currentUser.collectAsState()
    val coins by AppPrefs.coins.collectAsState()
    var events by remember { mutableStateOf(AppPrefs.getCoinEvents()) }
    var canCheckIn by remember { mutableStateOf(AppPrefs.canClaimDailyCheckIn()) }
    var serverCoins by remember { mutableStateOf<Int?>(null) }
    var pendingCount by remember { mutableIntStateOf(AppPrefs.pendingEarnCount()) }

    LaunchedEffect(currentUser?.id) {
        events = AppPrefs.getCoinEvents()
        canCheckIn = AppPrefs.canClaimDailyCheckIn()
        if (currentUser?.id != null) {
            pendingCount = repository.syncPendingEarns()
            repository.getWalletBalance().onSuccess { serverCoins = it.balance }
            pendingCount = AppPrefs.pendingEarnCount()
        } else {
            serverCoins = null
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ZevoraDarkBg)
            .verticalScroll(rememberScrollState())
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
            Text("Balance", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }

        // ---- balance hero ----
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(
                    Brush.linearGradient(
                        listOf(Color(0xFF3A0D18), Color(0xFF0E2A2E))
                    )
                )
                .padding(22.dp)
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(AccentGold.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.AccountBalanceWallet, null, tint = AccentGold)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("@${currentUser?.username ?: "guest"}", color = TextSecondary, fontSize = 13.sp)
                        Text("Rivo Coins", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    "${serverCoins ?: coins}",
                    color = AccentGold,
                    fontSize = 44.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    when {
                        serverCoins != null && pendingCount > 0 ->
                            "Synced · $pendingCount offline earn${if (pendingCount > 1) "s" else ""} pending"
                        serverCoins != null -> "Synced across your devices"
                        currentUser == null -> "Log in to sync across devices"
                        else -> "Syncing…"
                    },
                    color = TextMuted,
                    fontSize = 12.sp
                )
                Text("Coins have no cash value — they power video Boosts.", color = TextMuted, fontSize = 12.sp)
                Spacer(Modifier.height(14.dp))
                Button(
                    onClick = onOpenPromote,
                    colors = ButtonDefaults.buttonColors(containerColor = ZevoraRed),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.RocketLaunch, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Boost a video", fontWeight = FontWeight.Bold)
                }
            }
        }

        // ---- daily check-in ----
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(ZevoraDarkSurface)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(ZevoraCyan.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.CalendarMonth, null, tint = ZevoraCyan)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Daily check-in", color = Color.White, fontWeight = FontWeight.Bold)
                Text(
                    if (canCheckIn) "Claim +25 coins today" else "Claimed — come back tomorrow",
                    color = TextSecondary,
                    fontSize = 12.sp
                )
            }
            Button(
                onClick = {
                    if (AppPrefs.claimDailyCheckIn()) {
                        events = AppPrefs.getCoinEvents()
                        canCheckIn = false
                        scope.launch {
                            repository.earnServerOnly(25, "daily_check_in")
                            repository.getWalletBalance().onSuccess { serverCoins = it.balance }
                        }
                        Toast.makeText(context, "+25 coins claimed", Toast.LENGTH_SHORT).show()
                    }
                },
                enabled = canCheckIn,
                colors = ButtonDefaults.buttonColors(
                    containerColor = AccentGold,
                    disabledContainerColor = ZevoraDarkElevated
                ),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(
                    if (canCheckIn) "Claim" else "Done",
                    color = if (canCheckIn) Color.Black else TextMuted,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // ---- how to earn ----
        Text(
            "How to earn",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            modifier = Modifier.padding(start = 20.dp, top = 20.dp, bottom = 8.dp)
        )
        EarnRow(
            Icons.Default.CalendarMonth,
            "Daily check-in",
            "+25 coins every day you open the app"
        )
        EarnRow(
            Icons.Default.PlayCircle,
            "Watch videos",
            "+1 coin for every 10 videos you watch"
        )
        EarnRow(
            Icons.Default.VideoCall,
            "Publish videos",
            "+5 coins for every video you publish"
        )

        // ---- history ----
        Text(
            "Transaction history",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            modifier = Modifier.padding(start = 20.dp, top = 20.dp, bottom = 8.dp)
        )
        if (events.isEmpty()) {
            Text(
                "No transactions yet — claim your daily check-in to start.",
                color = TextMuted,
                fontSize = 13.sp,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
            )
        } else {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                events.take(40).forEach { e ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(
                                    if (e.delta > 0) ZevoraCyan.copy(alpha = 0.15f)
                                    else ZevoraRed.copy(alpha = 0.15f)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                if (e.delta > 0) Icons.Default.Add else Icons.Default.Remove,
                                null,
                                tint = if (e.delta > 0) ZevoraCyan else ZevoraRed,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                earnLabel(e.reason),
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                SimpleDateFormat("MMM d, HH:mm", Locale.US).format(Date(e.timestamp)),
                                color = TextMuted,
                                fontSize = 11.sp
                            )
                        }
                        Text(
                            (if (e.delta > 0) "+" else "") + "${e.delta}",
                            color = if (e.delta > 0) ZevoraCyan else ZevoraRed,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun EarnRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(ZevoraDarkSurface)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = AccentGold, modifier = Modifier.size(26.dp))
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Text(subtitle, color = TextSecondary, fontSize = 12.sp)
        }
    }
}

private fun earnLabel(reason: String): String = when (reason) {
    "daily_check_in" -> "Daily check-in"
    "watch_reward" -> "Watch reward"
    "video_published" -> "Video published"
    else -> if (reason.startsWith("boost:")) {
        "Boost: ${reason.removePrefix("boost:").take(40)}"
    } else {
        reason.replace('_', ' ').replaceFirstChar { it.uppercase() }.take(48)
    }
}
