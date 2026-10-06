package com.example.ui.screens.admin

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
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.example.data.local.entities.PrivacyRequestEntity
import com.example.data.local.entities.ReportEntity
import com.example.data.local.entities.UserEntity
import com.example.data.local.entities.VideoEntity
import com.example.data.local.entities.ViolationEntity
import com.example.data.repository.TokPulseRepository
import com.example.ui.theme.AccentGold
import com.example.ui.theme.AccentGreen
import com.example.ui.theme.AccentOrange
import com.example.ui.theme.StatusActive
import com.example.ui.theme.StatusBanned
import com.example.ui.theme.StatusPending
import com.example.ui.theme.StatusResolved
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TokBorder
import com.example.ui.theme.TokCyan
import com.example.ui.theme.TokDarkBg
import com.example.ui.theme.TokDarkElevated
import com.example.ui.theme.TokDarkSurface
import com.example.ui.theme.TokRed
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AdminDashboardScreen(
    repository: TokPulseRepository,
    onBackToFeed: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val tabs = listOf("Overview", "Reports", "Users", "Videos", "Violations", "Privacy", "Live Reports", "Logins")

    val allUsers by repository.getAllUsersAdmin().collectAsState(initial = emptyList())
    val allVideos by repository.getAllVideosAdmin().collectAsState(initial = emptyList())
    val allReports by repository.getAllReportsAdmin().collectAsState(initial = emptyList())
    val allViolations by repository.getAllViolationsAdmin().collectAsState(initial = emptyList())
    val privacyRequests by repository.getAllPrivacyRequestsAdmin().collectAsState(initial = emptyList())

    // Calculations for Stats Overview
    val totalUsers = allUsers.size
    val activeUsers = allUsers.count { it.status == "active" }
    val bannedUsers = allUsers.count { it.status == "banned" || it.status == "suspended" }
    val totalVideos = allVideos.size
    val totalViews = allVideos.sumOf { it.viewsCount }
    val totalReports = allReports.size
    val pendingReports = allReports.count { it.status == "pending" || it.status == "reviewing" }
    val openPrivacyRequests = privacyRequests.count { it.status == "pending" }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TokDarkBg)
            .statusBarsPadding()
            .testTag("admin_dashboard_screen")
    ) {
        // Admin Top Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(TokDarkSurface)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBackToFeed) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = TextPrimary
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(TokRed.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.AdminPanelSettings,
                        contentDescription = "Admin",
                        tint = TokRed,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "Chort Admin Center",
                        color = TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Platform Security & Moderation",
                        color = TextMuted,
                        fontSize = 11.sp
                    )
                }
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(AccentGreen.copy(alpha = 0.2f))
                    .border(0.8.dp, AccentGreen, RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "System: Online",
                    color = AccentGreen,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Sub Tabs
        ScrollableTabRow(
            selectedTabIndex = selectedTabIndex,
            containerColor = TokDarkSurface,
            contentColor = TextPrimary,
            edgePadding = 12.dp,
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex]),
                    color = TokRed
                )
            }
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTabIndex == index,
                    onClick = { selectedTabIndex = index },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(title, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            if (title == "Reports" && pendingReports > 0) {
                                Spacer(modifier = Modifier.width(4.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .background(TokRed)
                                        .padding(horizontal = 5.dp, vertical = 1.dp)
                                ) {
                                    Text("$pendingReports", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                )
            }
        }

        // Tab Content
        when (selectedTabIndex) {
            0 -> AdminOverviewTab(
                totalUsers = totalUsers,
                activeUsers = activeUsers,
                bannedUsers = bannedUsers,
                totalVideos = totalVideos,
                totalViews = totalViews,
                totalReports = totalReports,
                pendingReports = pendingReports,
                openPrivacyRequests = openPrivacyRequests
            )
            1 -> AdminReportsTab(
                reports = allReports,
                onResolveReport = { id, status, notes ->
                    scope.launch {
                        repository.updateReport(id, status, notes)
                        Toast.makeText(context, "Report status updated to $status", Toast.LENGTH_SHORT).show()
                    }
                },
                onPenalizeUser = { uid, username, vType, reason, content, action ->
                    scope.launch {
                        repository.recordViolationAndAction(uid, username, vType, reason, content, action)
                        Toast.makeText(context, "Action applied: $action to @$username", Toast.LENGTH_SHORT).show()
                    }
                }
            )
            2 -> AdminUsersTab(
                users = allUsers,
                onBanUser = { user ->
                    scope.launch {
                        repository.recordViolationAndAction(
                            userId = user.id,
                            username = user.username,
                            violationType = "Manual Moderation Ban",
                            reason = "Terms of service violation by admin decision",
                            relatedContent = "",
                            actionTaken = "permanent_ban"
                        )
                        Toast.makeText(context, "User @${user.username} permanently banned", Toast.LENGTH_SHORT).show()
                    }
                },
                onUnbanUser = { user ->
                    scope.launch {
                        repository.recordViolationAndAction(
                            userId = user.id,
                            username = user.username,
                            violationType = "Admin Pardon",
                            reason = "Restored user standing",
                            relatedContent = "",
                            actionTaken = "unban"
                        )
                        Toast.makeText(context, "User @${user.username} unbanned", Toast.LENGTH_SHORT).show()
                    }
                },
                onSuspendUser = { user ->
                    scope.launch {
                        repository.recordViolationAndAction(
                            userId = user.id,
                            username = user.username,
                            violationType = "Platform Rule Infraction",
                            reason = "7-day temporary safety suspension",
                            relatedContent = "",
                            actionTaken = "temporary_suspension"
                        )
                        Toast.makeText(context, "User @${user.username} suspended for 7 days", Toast.LENGTH_SHORT).show()
                    }
                },
                onDeleteUser = { user ->
                    scope.launch {
                        repository.adminDeleteUser(user.id)
                        Toast.makeText(context, "User @${user.username} account purged", Toast.LENGTH_SHORT).show()
                    }
                }
            )
            3 -> AdminVideosTab(
                videos = allVideos,
                onToggleHide = { video ->
                    scope.launch {
                        repository.setVideoHidden(video.id, !video.isHidden)
                        Toast.makeText(context, if (!video.isHidden) "Video hidden from feed" else "Video restored", Toast.LENGTH_SHORT).show()
                    }
                },
                onDeleteVideo = { video ->
                    scope.launch {
                        repository.deleteVideo(video.id)
                        Toast.makeText(context, "Video deleted", Toast.LENGTH_SHORT).show()
                    }
                }
            )
            4 -> AdminViolationsTab(violations = allViolations)
            5 -> AdminPrivacyTab(
                requests = privacyRequests,
                onProcessRequest = { id, status ->
                    scope.launch {
                        repository.processPrivacyRequest(id, status)
                        Toast.makeText(context, "Request updated to $status", Toast.LENGTH_SHORT).show()
                    }
                }
            )
            6 -> LiveReportsTab(repository = repository)
            7 -> LiveLoginsTab(repository = repository)
        }
    }
}

// --- OVERVIEW TAB ---
@Composable
private fun AdminOverviewTab(
    totalUsers: Int,
    activeUsers: Int,
    bannedUsers: Int,
    totalVideos: Int,
    totalViews: Int,
    totalReports: Int,
    pendingReports: Int,
    openPrivacyRequests: Int
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        item {
            Text(
                text = "Key Platform Metrics",
                color = TextPrimary,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(12.dp))

            // Stat Cards Grid
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AdminStatCard(
                    title = "Total Users",
                    value = "$totalUsers",
                    sub = "$activeUsers active / $bannedUsers restricted",
                    icon = Icons.Default.People,
                    color = TokCyan,
                    modifier = Modifier.weight(1f)
                )
                AdminStatCard(
                    title = "Total Videos",
                    value = "$totalVideos",
                    sub = "${formatCount(totalViews)} total views",
                    icon = Icons.Default.Movie,
                    color = TokRed,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AdminStatCard(
                    title = "Moderation Reports",
                    value = "$totalReports",
                    sub = "$pendingReports pending review",
                    icon = Icons.Outlined.Flag,
                    color = if (pendingReports > 0) StatusPending else AccentGreen,
                    modifier = Modifier.weight(1f)
                )
                AdminStatCard(
                    title = "Privacy / Deletions",
                    value = "$openPrivacyRequests",
                    sub = "GDPR & CCPA Requests",
                    icon = Icons.Default.Lock,
                    color = AccentGold,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Infrastructure & Backend Status
            Text(
                text = "Backend & Cloud Services",
                color = TextPrimary,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(10.dp))

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = TokDarkSurface),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, TokBorder, RoundedCornerShape(16.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    StatusRow(service = "REST API Gateway", status = "Operational", ping = "14ms", isOk = true)
                    HorizontalDivider(color = TokBorder, modifier = Modifier.padding(vertical = 8.dp))
                    StatusRow(service = "Room SQLite Database", status = "Connected & Synced", ping = "Local Persistent", isOk = true)
                    HorizontalDivider(color = TokBorder, modifier = Modifier.padding(vertical = 8.dp))
                    StatusRow(service = "Cloud Video Storage CDN", status = "Ready", ping = "Multi-Region", isOk = true)
                    HorizontalDivider(color = TokBorder, modifier = Modifier.padding(vertical = 8.dp))
                    StatusRow(service = "Content Moderation Queue", status = if (pendingReports > 0) "$pendingReports Pending" else "Clear", ping = "Live", isOk = pendingReports == 0)
                }
            }
        }
    }
}

@Composable
private fun StatusRow(service: String, status: String, ping: String, isOk: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text(text = service, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text(text = ping, color = TextMuted, fontSize = 11.sp)
        }
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(if (isOk) AccentGreen.copy(alpha = 0.2f) else StatusPending.copy(alpha = 0.2f))
                .padding(horizontal = 8.dp, vertical = 3.dp)
        ) {
            Text(
                text = status,
                color = if (isOk) AccentGreen else StatusPending,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun AdminStatCard(
    title: String,
    value: String,
    sub: String,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = TokDarkSurface),
        modifier = modifier.border(1.dp, TokBorder, RoundedCornerShape(16.dp))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = title, color = TextMuted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(color.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(imageVector = icon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = value, color = TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(modifier = Modifier.height(2.dp))
            Text(text = sub, color = TextSecondary, fontSize = 11.sp)
        }
    }
}

// --- REPORTS TAB ---
@Composable
private fun AdminReportsTab(
    reports: List<ReportEntity>,
    onResolveReport: (String, String, String) -> Unit,
    onPenalizeUser: (userId: String, username: String, vType: String, reason: String, content: String, action: String) -> Unit
) {
    var statusFilter by remember { mutableStateOf("All") }
    var reportUnderAction by remember { mutableStateOf<ReportEntity?>(null) }

    val filtered = remember(reports, statusFilter) {
        if (statusFilter == "All") reports else reports.filter { it.status.equals(statusFilter, ignoreCase = true) }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Filters
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val filters = listOf("All", "Pending", "Reviewing", "Resolved", "Rejected")
            items(filters) { f ->
                FilterChip(
                    selected = (statusFilter == f),
                    onClick = { statusFilter = f },
                    label = { Text(f, fontSize = 12.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = TokRed,
                        selectedLabelColor = Color.White,
                        containerColor = TokDarkElevated,
                        labelColor = TextSecondary
                    ),
                    shape = RoundedCornerShape(14.dp)
                )
            }
        }

        if (filtered.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No reports found matching this filter.", color = TextMuted, fontSize = 13.5.sp)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
            ) {
                items(filtered, key = { it.id }) { rep ->
                    ReportCard(
                        report = rep,
                        onTakeAction = { reportUnderAction = rep }
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                }
            }
        }
    }

    // Moderation Action Dialog
    if (reportUnderAction != null) {
        val rep = reportUnderAction!!
        Dialog(onDismissRequest = { reportUnderAction = null }) {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = TokDarkSurface),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, TokBorder, RoundedCornerShape(20.dp))
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "Moderation Action",
                        color = TextPrimary,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Target: ${rep.targetType.uppercase()} (ID: ${rep.targetId})",
                        color = TokCyan,
                        fontSize = 12.sp
                    )
                    Text(
                        text = "Reason: ${rep.reason}",
                        color = StatusBanned,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text("Choose resolution action:", color = TextSecondary, fontSize = 12.5.sp)
                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = {
                            onResolveReport(rep.id, "resolved", "Dismissed after safety review: No policy violation found.")
                            reportUnderAction = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = TokDarkElevated),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Dismiss - No Violation Found", color = TextPrimary, fontSize = 12.sp)
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Button(
                        onClick = {
                            onResolveReport(rep.id, "resolved", "Warning issued to content creator.")
                            onPenalizeUser(rep.targetId, rep.targetOwnerUsername.ifBlank { "reported_creator" }, rep.reason, rep.description, rep.targetSnippet, "warning")
                            reportUnderAction = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = StatusPending.copy(alpha = 0.25f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Issue Formal Warning", color = StatusPending, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Button(
                        onClick = {
                            onResolveReport(rep.id, "resolved", "Content removed and user suspended for 7 days.")
                            onPenalizeUser(rep.targetId, rep.targetOwnerUsername.ifBlank { "reported_creator" }, rep.reason, rep.description, rep.targetSnippet, "temporary_suspension")
                            reportUnderAction = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = TokRed),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Suspend Creator (7 Days)", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Button(
                        onClick = {
                            onResolveReport(rep.id, "resolved", "Permanent ban applied for severe policy breach.")
                            onPenalizeUser(rep.targetId, rep.targetOwnerUsername.ifBlank { "reported_creator" }, rep.reason, rep.description, rep.targetSnippet, "permanent_ban")
                            reportUnderAction = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = StatusBanned),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Permanent Ban & Purge", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun ReportCard(
    report: ReportEntity,
    onTakeAction: () -> Unit
) {
    val statusColor = when (report.status) {
        "pending" -> StatusPending
        "resolved" -> StatusResolved
        "rejected" -> StatusBanned
        else -> TokCyan
    }

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = TokDarkSurface),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, TokBorder, RoundedCornerShape(14.dp))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(TokDarkElevated)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = report.targetType.uppercase(),
                            color = TokCyan,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Reported by @${report.reporterUsername}",
                        color = TextMuted,
                        fontSize = 11.5.sp
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(statusColor.copy(alpha = 0.2f))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = report.status.uppercase(),
                        color = statusColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Reason: ${report.reason}",
                color = TextPrimary,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Bold
            )

            if (report.description.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "\"${report.description}\"",
                    color = TextSecondary,
                    fontSize = 12.sp
                )
            }

            if (report.targetSnippet.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(TokDarkElevated)
                        .padding(8.dp)
                ) {
                    Text(
                        text = "Target snippet: ${report.targetSnippet}",
                        color = TextSecondary,
                        fontSize = 11.sp,
                        maxLines = 2
                    )
                }
            }

            if (report.resolutionNotes.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Resolution: ${report.resolutionNotes} (by @${report.resolvedByAdmin})",
                    color = AccentGreen,
                    fontSize = 11.sp
                )
            }

            Spacer(modifier = Modifier.height(10.dp))
            Button(
                onClick = onTakeAction,
                colors = ButtonDefaults.buttonColors(containerColor = TokDarkElevated),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(34.dp)
                    .border(1.dp, TokBorder, RoundedCornerShape(10.dp))
            ) {
                Icon(Icons.Default.Gavel, contentDescription = null, tint = TokRed, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Inspect & Moderation Actions", color = TextPrimary, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

// --- USERS TAB ---
@Composable
private fun AdminUsersTab(
    users: List<UserEntity>,
    onBanUser: (UserEntity) -> Unit,
    onUnbanUser: (UserEntity) -> Unit,
    onSuspendUser: (UserEntity) -> Unit,
    onDeleteUser: (UserEntity) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }

    val filtered = remember(users, searchQuery) {
        if (searchQuery.isBlank()) users else users.filter {
            it.username.contains(searchQuery, ignoreCase = true) ||
            it.displayName.contains(searchQuery, ignoreCase = true) ||
            it.email.contains(searchQuery, ignoreCase = true)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search users by handle, email...", color = TextMuted, fontSize = 12.sp) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(18.dp)) },
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary,
                focusedBorderColor = TokCyan,
                unfocusedBorderColor = TokBorder
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .height(48.dp)
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            items(filtered, key = { it.id }) { user ->
                AdminUserCard(
                    user = user,
                    onBan = { onBanUser(user) },
                    onUnban = { onUnbanUser(user) },
                    onSuspend = { onSuspendUser(user) },
                    onDelete = { onDeleteUser(user) }
                )
                Spacer(modifier = Modifier.height(10.dp))
            }
        }
    }
}

@Composable
private fun AdminUserCard(
    user: UserEntity,
    onBan: () -> Unit,
    onUnban: () -> Unit,
    onSuspend: () -> Unit,
    onDelete: () -> Unit
) {
    val statusColor = when (user.status) {
        "active" -> StatusActive
        "suspended" -> StatusPending
        "banned" -> StatusBanned
        else -> TextMuted
    }

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = TokDarkSurface),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, TokBorder, RoundedCornerShape(14.dp))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AsyncImage(
                    model = user.avatarUrl,
                    contentDescription = user.username,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                )

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = user.displayName,
                            color = TextPrimary,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                        if (user.role == "admin") {
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(TokRed)
                                    .padding(horizontal = 4.dp, vertical = 1.dp)
                            ) {
                                Text("ADMIN", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    Text(
                        text = "@${user.username} • ${user.email}",
                        color = TextMuted,
                        fontSize = 11.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(statusColor.copy(alpha = 0.2f))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = user.status.uppercase(),
                        color = statusColor,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Action row for Admin
            if (user.role != "admin") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (user.status == "banned" || user.status == "suspended") {
                        Button(
                            onClick = onUnban,
                            colors = ButtonDefaults.buttonColors(containerColor = AccentGreen),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(32.dp)
                        ) {
                            Text("Unban / Restore", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Button(
                            onClick = onSuspend,
                            colors = ButtonDefaults.buttonColors(containerColor = TokDarkElevated),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(32.dp)
                                .border(1.dp, TokBorder, RoundedCornerShape(8.dp))
                        ) {
                            Text("Suspend 7d", color = StatusPending, fontSize = 11.sp)
                        }

                        Button(
                            onClick = onBan,
                            colors = ButtonDefaults.buttonColors(containerColor = StatusBanned.copy(alpha = 0.2f)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(32.dp)
                                .border(1.dp, StatusBanned, RoundedCornerShape(8.dp))
                        ) {
                            Text("Ban User", color = StatusBanned, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Button(
                        onClick = onDelete,
                        colors = ButtonDefaults.buttonColors(containerColor = TokDarkElevated),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .size(32.dp)
                            .border(1.dp, TokBorder, RoundedCornerShape(8.dp))
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = StatusBanned, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}

// --- VIDEOS TAB ---
@Composable
private fun AdminVideosTab(
    videos: List<VideoEntity>,
    onToggleHide: (VideoEntity) -> Unit,
    onDeleteVideo: (VideoEntity) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val filtered = remember(videos, searchQuery) {
        if (searchQuery.isBlank()) videos else videos.filter {
            it.caption.contains(searchQuery, ignoreCase = true) ||
            it.creatorUsername.contains(searchQuery, ignoreCase = true)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search videos by creator or caption...", color = TextMuted, fontSize = 12.sp) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(18.dp)) },
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary,
                focusedBorderColor = TokCyan,
                unfocusedBorderColor = TokBorder
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .height(48.dp)
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            items(filtered, key = { it.id }) { video ->
                AdminVideoCard(
                    video = video,
                    onToggleHide = { onToggleHide(video) },
                    onDelete = { onDeleteVideo(video) }
                )
                Spacer(modifier = Modifier.height(10.dp))
            }
        }
    }
}

@Composable
private fun AdminVideoCard(
    video: VideoEntity,
    onToggleHide: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = TokDarkSurface),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, TokBorder, RoundedCornerShape(14.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = video.thumbnailUrl,
                contentDescription = video.caption,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(60.dp)
                    .clip(RoundedCornerShape(8.dp))
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "@${video.creatorUsername}",
                    color = TokCyan,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = video.caption,
                    color = TextPrimary,
                    fontSize = 13.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${video.viewsCount} views • ${video.likesCount} likes",
                    color = TextMuted,
                    fontSize = 11.sp
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                IconButton(onClick = onToggleHide) {
                    Icon(
                        imageVector = if (video.isHidden) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = "Hide/Unhide",
                        tint = if (video.isHidden) StatusBanned else AccentGreen,
                        modifier = Modifier.size(20.dp)
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = StatusBanned,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

// --- VIOLATIONS TAB ---
@Composable
private fun AdminViolationsTab(violations: List<ViolationEntity>) {
    if (violations.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No recorded violations in audit log.", color = TextMuted, fontSize = 13.5.sp)
        }
    } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            items(violations, key = { it.id }) { viol ->
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = TokDarkSurface),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, TokBorder, RoundedCornerShape(14.dp))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Action: ${viol.actionTaken.uppercase()}",
                                color = StatusBanned,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Admin: @${viol.adminUsername}",
                                color = TextMuted,
                                fontSize = 11.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Violator: @${viol.username} (ID: ${viol.userId})",
                            color = TextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Type: ${viol.violationType}",
                            color = TokCyan,
                            fontSize = 12.sp
                        )
                        Text(
                            text = "Reason: ${viol.reason}",
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                        val dateStr = remember(viol.createdAt) {
                            SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()).format(Date(viol.createdAt))
                        }
                        Text(
                            text = "Recorded on $dateStr",
                            color = TextMuted,
                            fontSize = 10.5.sp
                        )
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
            }
        }
    }
}

// --- PRIVACY TAB ---
@Composable
private fun AdminPrivacyTab(
    requests: List<PrivacyRequestEntity>,
    onProcessRequest: (String, String) -> Unit
) {
    if (requests.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No pending privacy or account deletion requests.", color = TextMuted, fontSize = 13.5.sp)
        }
    } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            items(requests, key = { it.id }) { req ->
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = TokDarkSurface),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, TokBorder, RoundedCornerShape(14.dp))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = req.requestType.replace("_", " ").uppercase(),
                                color = StatusBanned,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (req.status == "pending") StatusPending.copy(alpha = 0.2f) else AccentGreen.copy(alpha = 0.2f))
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = req.status.uppercase(),
                                    color = if (req.status == "pending") StatusPending else AccentGreen,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "User: @${req.username} (${req.email})",
                            color = TextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        if (req.reason.isNotBlank()) {
                            Text(
                                text = "Reason: \"${req.reason}\"",
                                color = TextSecondary,
                                fontSize = 12.sp
                            )
                        }

                        if (req.status == "pending") {
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = { onProcessRequest(req.id, "approved_and_purged") },
                                    colors = ButtonDefaults.buttonColors(containerColor = StatusBanned),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(34.dp)
                                ) {
                                    Text("Approve & Purge Data", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }

                                Button(
                                    onClick = { onProcessRequest(req.id, "rejected") },
                                    colors = ButtonDefaults.buttonColors(containerColor = TokDarkElevated),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(34.dp)
                                ) {
                                    Text("Reject", color = TextSecondary, fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
            }
        }
    }
}

private fun formatCount(count: Int): String {
    return when {
        count >= 1_000_000 -> String.format("%.1fM", count / 1_000_000.0)
        count >= 1_000 -> String.format("%.1fK", count / 1_000.0)
        else -> count.toString()
    }
}
