package com.example.ui.screens.admin

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.remote.ApiAdminReport
import com.example.data.remote.ApiLoginRecord
import com.example.data.repository.ZevoraRepository
import com.example.ui.theme.StatusBanned
import com.example.ui.theme.StatusPending
import com.example.ui.theme.StatusResolved
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.ZevoraBorder
import com.example.ui.theme.ZevoraCyan
import com.example.ui.theme.ZevoraDarkElevated
import com.example.ui.theme.ZevoraDarkSurface
import com.example.ui.theme.ZevoraRed
import kotlinx.coroutines.launch

// ---------------------------------------------------------------------------
// LIVE REPORTS: real moderation queue from the Rivo API
// ---------------------------------------------------------------------------

@Composable
fun LiveReportsTab(repository: ZevoraRepository) {
    val scope = rememberCoroutineScope()
    var statusFilter by remember { mutableStateOf<String?>(null) }
    var page by remember { mutableIntStateOf(1) }
    var reports by remember { mutableStateOf<List<ApiAdminReport>>(emptyList()) }
    var hasMore by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(true) }
    var busyId by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }

    fun load(targetPage: Int) {
        isLoading = true
        message = null
        scope.launch {
            val result = repository.getAdminReports(statusFilter, targetPage)
            isLoading = false
            if (result.isSuccess) {
                val body = result.getOrThrow()
                reports = body.reports
                hasMore = body.hasMore
                page = body.page
            } else {
                message = result.exceptionOrNull()?.message
            }
        }
    }

    fun resolve(report: ApiAdminReport, action: String) {
        busyId = report.id + action
        scope.launch {
            val result = repository.resolveAdminReport(report.id, action)
            busyId = null
            if (result.isSuccess) {
                load(page)
            } else {
                message = result.exceptionOrNull()?.message
            }
        }
    }

    LaunchedEffect(statusFilter) { load(1) }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            listOf(null to "All", "PENDING" to "Pending", "RESOLVED" to "Resolved", "DISMISSED" to "Dismissed").forEach { (value, label) ->
                FilterChip(
                    selected = statusFilter == value,
                    onClick = { statusFilter = value },
                    label = { Text(label, fontSize = 12.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = ZevoraRed,
                        selectedLabelColor = Color.White,
                        containerColor = ZevoraDarkElevated,
                        labelColor = TextSecondary
                    ),
                    shape = RoundedCornerShape(16.dp)
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            IconButton(onClick = { load(page) }) {
                Icon(imageVector = Icons.Default.Refresh, contentDescription = "Refresh", tint = ZevoraCyan)
            }
        }

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = ZevoraCyan)
                    Spacer(modifier = Modifier.height(10.dp))
                    Text("Loading live reports…", color = TextMuted, fontSize = 13.sp)
                }
            }
        } else if (message != null && reports.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable { load(1) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = (message ?: "Unavailable") + "\nTap to retry.",
                    color = StatusBanned,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center
                )
            }
        } else if (reports.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null, tint = StatusResolved, modifier = Modifier.size(48.dp))
                    Spacer(modifier = Modifier.height(10.dp))
                    Text("Queue is clear", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text("No reports match this filter.", color = TextMuted, fontSize = 13.sp)
                }
            }
        } else {
            if (message != null) {
                Text(
                    text = message ?: "",
                    color = StatusBanned,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp)
                )
            }
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(reports, key = { it.id }) { report ->
                    LiveReportCard(
                        report = report,
                        busyKey = busyId,
                        onResolve = { action -> resolve(report, action) }
                    )
                }
                item { Spacer(modifier = Modifier.height(8.dp)) }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = { if (page > 1) load(page - 1) },
                    enabled = page > 1 && !isLoading,
                    colors = ButtonDefaults.buttonColors(containerColor = ZevoraDarkElevated)
                ) {
                    Text("Previous", color = TextPrimary, fontSize = 13.sp)
                }
                Text("Page $page", color = TextMuted, fontSize = 13.sp)
                Button(
                    onClick = { if (hasMore) load(page + 1) },
                    enabled = hasMore && !isLoading,
                    colors = ButtonDefaults.buttonColors(containerColor = ZevoraDarkElevated)
                ) {
                    Text("Next", color = TextPrimary, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun LiveReportCard(
    report: ApiAdminReport,
    busyKey: String?,
    onResolve: (String) -> Unit
) {
    val statusColor = when (report.status) {
        "PENDING" -> StatusPending
        "RESOLVED" -> StatusResolved
        else -> TextMuted
    }
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = ZevoraDarkSurface),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, ZevoraBorder, RoundedCornerShape(14.dp))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = Icons.Default.Flag, contentDescription = null, tint = ZevoraRed, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = report.reason,
                    color = TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(statusColor.copy(alpha = 0.18f))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(text = report.status, color = statusColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = buildString {
                    append("Reporter: ${report.reporterId.take(8)}…")
                    if (!report.videoId.isNullOrBlank()) append("  •  Video: ${report.videoId!!.take(8)}…")
                    if (!report.targetUserId.isNullOrBlank()) append("  •  User: ${report.targetUserId!!.take(8)}…")
                    if (!report.createdAt.isNullOrBlank()) append("\n${report.createdAt!!.take(10)} ${report.createdAt!!.drop(11).take(5)}")
                },
                color = TextSecondary,
                fontSize = 12.sp,
                lineHeight = 17.sp
            )
            if (report.status == "PENDING") {
                Spacer(modifier = Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LiveActionButton(
                        label = "Dismiss",
                        icon = Icons.Default.Close,
                        busy = busyKey == report.id + "dismiss",
                        onClick = { onResolve("dismiss") }
                    )
                    if (!report.videoId.isNullOrBlank()) {
                        LiveActionButton(
                            label = "Hide video",
                            icon = Icons.Default.VisibilityOff,
                            busy = busyKey == report.id + "hide_video",
                            onClick = { onResolve("hide_video") }
                        )
                    }
                }
            } else if (!report.videoId.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(12.dp))
                LiveActionButton(
                    label = "Show video again",
                    icon = Icons.Default.Visibility,
                    busy = busyKey == report.id + "show_video",
                    onClick = { onResolve("show_video") }
                )
            }
        }
    }
}

@Composable
private fun LiveActionButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    busy: Boolean,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .border(1.dp, ZevoraCyan, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp)
    ) {
        if (busy) {
            CircularProgressIndicator(color = ZevoraCyan, strokeWidth = 2.dp, modifier = Modifier.size(14.dp))
            Spacer(modifier = Modifier.width(6.dp))
        } else {
            Icon(imageVector = icon, contentDescription = null, tint = ZevoraCyan, modifier = Modifier.size(15.dp))
            Spacer(modifier = Modifier.width(6.dp))
        }
        Text(text = label, color = ZevoraCyan, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
    }
}

// ---------------------------------------------------------------------------
// LOGINS: real sign-in records from the Rivo API
// ---------------------------------------------------------------------------

@Composable
fun LiveLoginsTab(repository: ZevoraRepository) {
    val scope = rememberCoroutineScope()
    var page by remember { mutableIntStateOf(1) }
    var records by remember { mutableStateOf<List<ApiLoginRecord>>(emptyList()) }
    var total by remember { mutableIntStateOf(0) }
    var hasMore by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(true) }
    var message by remember { mutableStateOf<String?>(null) }

    fun load(targetPage: Int) {
        isLoading = true
        message = null
        scope.launch {
            val result = repository.getAdminLogins(targetPage)
            isLoading = false
            if (result.isSuccess) {
                val body = result.getOrThrow()
                records = body.items
                total = body.total
                hasMore = body.hasMore
                page = body.page
            } else {
                message = result.exceptionOrNull()?.message
            }
        }
    }

    LaunchedEffect(Unit) { load(1) }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = if (total > 0) "$total sign-in records" else "Sign-in records",
                color = TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
            IconButton(onClick = { load(page) }) {
                Icon(imageVector = Icons.Default.Refresh, contentDescription = "Refresh", tint = ZevoraCyan)
            }
        }

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = ZevoraCyan)
                    Spacer(modifier = Modifier.height(10.dp))
                    Text("Loading login records…", color = TextMuted, fontSize = 13.sp)
                }
            }
        } else if (message != null && records.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable { load(1) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = (message ?: "Unavailable") + "\nTap to retry.",
                    color = StatusBanned,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center
                )
            }
        } else if (records.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(imageVector = Icons.Default.Security, contentDescription = null, tint = TextMuted, modifier = Modifier.size(48.dp))
                    Spacer(modifier = Modifier.height(10.dp))
                    Text("No login records yet", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(records, key = { it.id }) { record ->
                    LoginRecordCard(record = record)
                }
                item { Spacer(modifier = Modifier.height(8.dp)) }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = { if (page > 1) load(page - 1) },
                    enabled = page > 1 && !isLoading,
                    colors = ButtonDefaults.buttonColors(containerColor = ZevoraDarkElevated)
                ) {
                    Text("Previous", color = TextPrimary, fontSize = 13.sp)
                }
                Text("Page $page", color = TextMuted, fontSize = 13.sp)
                Button(
                    onClick = { if (hasMore) load(page + 1) },
                    enabled = hasMore && !isLoading,
                    colors = ButtonDefaults.buttonColors(containerColor = ZevoraDarkElevated)
                ) {
                    Text("Next", color = TextPrimary, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun LoginRecordCard(record: ApiLoginRecord) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = ZevoraDarkSurface),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, ZevoraBorder, RoundedCornerShape(14.dp))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(9.dp)
                        .clip(CircleShape)
                        .background(if (record.active) StatusResolved else TextMuted)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "@${record.user?.username ?: "unknown"}",
                    color = TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                if (!record.user?.primaryProvider.isNullOrBlank()) {
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(ZevoraCyan.copy(alpha = 0.14f))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = record.user!!.primaryProvider!!,
                            color = ZevoraCyan,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = buildString {
                    val mail = record.user?.email ?: ""
                    val phone = record.user?.phone ?: ""
                    if (mail.isNotBlank()) append(mail)
                    if (phone.isNotBlank()) {
                        if (isNotEmpty()) append("  •  ")
                        append(phone)
                    }
                    val agent = shortAgentLabel(record.userAgent)
                    if (agent.isNotBlank()) {
                        if (isNotEmpty()) append("\n")
                        append(agent)
                    }
                    if (!record.ipAddress.isNullOrBlank()) append("  •  ${record.ipAddress}")
                    val at = record.createdAt?.take(10) ?: ""
                    val time = record.createdAt?.drop(11)?.take(5) ?: ""
                    if (at.isNotBlank()) {
                        if (isNotEmpty()) append("\n")
                        append("$at $time")
                    }
                },
                color = TextSecondary,
                fontSize = 12.sp,
                lineHeight = 17.sp
            )
        }
    }
}

private fun shortAgentLabel(userAgent: String?): String {
    if (userAgent.isNullOrBlank()) return ""
    val ua = userAgent.lowercase()
    return when {
        ua.contains("android") -> "Android app"
        ua.contains("iphone") || ua.contains("ipad") || ua.contains("ios") -> "iOS app"
        ua.contains("windows") -> "Windows browser"
        ua.contains("mac") -> "Mac browser"
        ua.contains("linux") -> "Linux browser"
        else -> userAgent.take(28)
    }
}
