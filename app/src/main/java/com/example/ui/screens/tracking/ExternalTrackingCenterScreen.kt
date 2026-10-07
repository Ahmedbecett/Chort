package com.example.ui.screens.tracking

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Report
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.local.entities.ReportEntity
import com.example.data.local.entities.UserEntity
import com.example.data.repository.ZevoraRepository
import com.example.ui.theme.AccentGold
import com.example.ui.theme.StatusBanned
import com.example.ui.theme.StatusPending
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
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExternalTrackingCenterScreen(
    repository: ZevoraRepository,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    val allUsers by repository.getAllUsersAdmin().collectAsState(initial = emptyList())
    val allReports by repository.getAllReportsAdmin().collectAsState(initial = emptyList())
    val allViolations by repository.getAllViolationsAdmin().collectAsState(initial = emptyList())

    var selectedTab by remember { mutableIntStateOf(0) } // 0: Users, 1: Reports, 2: Analytics & Audit
    var userSearchQuery by remember { mutableStateOf("") }
    var userFilterStatus by remember { mutableStateOf("ALL") } // ALL, active, suspended, banned, admin

    var reportFilterStatus by remember { mutableStateOf("ALL") } // ALL, pending, resolved, rejected

    var showExportDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ZevoraDarkBg)
            .statusBarsPadding()
    ) {
        // TOP APP BAR
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(ZevoraDarkSurface)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.testTag("tracking_back_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = TextPrimary
                )
            }

            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(ZevoraCyan.copy(alpha = 0.2f))
                    .border(1.dp, ZevoraCyan, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Shield,
                    contentDescription = null,
                    tint = ZevoraCyan,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "مركز التتبع والمراقبة",
                        color = TextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(ZevoraRed.copy(alpha = 0.2f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "PORTAL",
                            color = ZevoraRed,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.FiberManualRecord,
                        contentDescription = null,
                        tint = StatusResolved,
                        modifier = Modifier.size(8.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "نظام المراقبة وتتبع الحسابات نشط • Room Live",
                        color = TextMuted,
                        fontSize = 11.sp
                    )
                }
            }

            IconButton(
                onClick = { showExportDialog = true },
                modifier = Modifier.testTag("tracking_export_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Download,
                    contentDescription = "Export Report",
                    tint = ZevoraCyan
                )
            }
        }

        // METRICS KPI BAR
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            KpiCard(
                title = "المسجلين",
                count = allUsers.size.toString(),
                subtext = "${allUsers.count { it.status == "active" }} نشط",
                accentColor = ZevoraCyan,
                modifier = Modifier.weight(1f)
            )
            KpiCard(
                title = "البلاغات",
                count = allReports.size.toString(),
                subtext = "${allReports.count { it.status == "pending" }} قيد الفحص",
                accentColor = ZevoraRed,
                modifier = Modifier.weight(1f)
            )
            KpiCard(
                title = "المحظورين",
                count = allUsers.count { it.status == "banned" }.toString(),
                subtext = "مخالفة السياسات",
                accentColor = StatusBanned,
                modifier = Modifier.weight(1f)
            )
            KpiCard(
                title = "معدل الأمان",
                count = "99.4%",
                subtext = "SLA سريع",
                accentColor = AccentGold,
                modifier = Modifier.weight(1f)
            )
        }

        // TABS
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = ZevoraDarkSurface,
            contentColor = TextPrimary,
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                    color = when (selectedTab) {
                        0 -> ZevoraCyan
                        1 -> ZevoraRed
                        else -> AccentGold
                    }
                )
            }
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("المسجلين (${allUsers.size})", fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                    }
                }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Report, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("البلاغات (${allReports.count { it.status == "pending" }})", fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                    }
                }
            )
            Tab(
                selected = selectedTab == 2,
                onClick = { selectedTab = 2 },
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.TrendingUp, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("سجل الإجراءات", fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                    }
                }
            )
        }

        // TAB CONTENTS
        when (selectedTab) {
            0 -> {
                // USERS TRACKER
                UsersTrackerSection(
                    users = allUsers,
                    searchQuery = userSearchQuery,
                    onSearchQueryChange = { userSearchQuery = it },
                    filterStatus = userFilterStatus,
                    onFilterStatusChange = { userFilterStatus = it },
                    onBanUser = { userId, reason ->
                        scope.launch {
                            repository.banUser(userId, reason)
                            Toast.makeText(context, "تم حظر المستخدم بنجاح", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onUnbanUser = { userId ->
                        scope.launch {
                            repository.unbanUser(userId)
                            Toast.makeText(context, "تم رفع الحظر وتنشيط الحساب", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onSuspendUser = { userId, days ->
                        scope.launch {
                            repository.suspendUser(userId, days)
                            Toast.makeText(context, "تم تعليق الحساب لمدة $days أيام", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onToggleAdmin = { userId, makeAdmin ->
                        scope.launch {
                            repository.updateUserRole(userId, if (makeAdmin) "admin" else "user")
                            Toast.makeText(context, "تم تعديل الصلاحية", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }
            1 -> {
                // REPORTS TRACKER
                ReportsTrackerSection(
                    reports = allReports,
                    filterStatus = reportFilterStatus,
                    onFilterStatusChange = { reportFilterStatus = it },
                    onResolveReport = { reportId, status, note ->
                        scope.launch {
                            repository.updateReport(reportId, status, note)
                            Toast.makeText(context, "تم تحديث حالة البلاغ", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onDeleteVideo = { videoId ->
                        scope.launch {
                            repository.deleteVideo(videoId)
                            Toast.makeText(context, "تم حذف الفيديو المخالف نهائياً", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onBanReportedUser = { userId, reason ->
                        scope.launch {
                            repository.banUser(userId, reason)
                            Toast.makeText(context, "تم حظر صاحب المحتوى المخالف", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }
            2 -> {
                // AUDIT LOG & ANALYTICS
                AuditAndAnalyticsSection(
                    users = allUsers,
                    reports = allReports,
                    violations = allViolations
                )
            }
        }
    }

    // EXPORT DATA DIALOG
    if (showExportDialog) {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        val csvSummary = buildString {
            append("--- TOKPULSE AUDIT & TRACKING REPORT ---\n")
            append("Generated At: ${dateFormat.format(Date())}\n")
            append("Total Registered Users: ${allUsers.size}\n")
            append("Active Users: ${allUsers.count { it.status == "active" }}\n")
            append("Banned Users: ${allUsers.count { it.status == "banned" }}\n")
            append("Total Reports Filed: ${allReports.size}\n")
            append("Pending Reports: ${allReports.count { it.status == "pending" }}\n\n")
            append("=== REGISTERED USERS LIST ===\n")
            allUsers.forEach { u ->
                append("ID: ${u.id} | @${u.username} | ${u.email} | Status: ${u.status} | Role: ${u.role}\n")
            }
            append("\n=== RECENT REPORTS ===\n")
            allReports.forEach { r ->
                append("RepID: ${r.id} | Type: ${r.targetType} | Reason: ${r.reason} | Status: ${r.status}\n")
            }
        }

        AlertDialog(
            onDismissRequest = { showExportDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Download, contentDescription = null, tint = ZevoraCyan)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("تقرير التتبع والتدقيق (Audit Summary)")
                }
            },
            text = {
                Column {
                    Text(
                        text = "تم إنشاء ملخص شامل للمسجلين والبلاغات من قاعدة البيانات المحلية. يمكنك نسخه لمشاركته أو حفظه كتقرير أمان رسمي.",
                        color = TextSecondary,
                        fontSize = 12.5.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(ZevoraDarkElevated)
                            .padding(8.dp)
                    ) {
                        Text(
                            text = csvSummary,
                            color = ZevoraCyan,
                            fontSize = 10.sp,
                            lineHeight = 14.sp
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        clipboardManager.setText(AnnotatedString(csvSummary))
                        Toast.makeText(context, "تم نسخ التقرير كاملاً للحافظة!", Toast.LENGTH_SHORT).show()
                        showExportDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ZevoraCyan)
                ) {
                    Text("نسخ التقرير (Copy)", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showExportDialog = false }) {
                    Text("إغلاق", color = TextSecondary)
                }
            },
            containerColor = ZevoraDarkSurface
        )
    }
}

@Composable
private fun KpiCard(
    title: String,
    count: String,
    subtext: String,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = ZevoraDarkSurface),
        border = androidx.compose.foundation.BorderStroke(1.dp, ZevoraBorder)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(text = title, color = TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.height(2.dp))
            Text(text = count, color = accentColor, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(modifier = Modifier.height(2.dp))
            Text(text = subtext, color = TextSecondary, fontSize = 9.5.sp)
        }
    }
}

@Composable
private fun UsersTrackerSection(
    users: List<UserEntity>,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    filterStatus: String,
    onFilterStatusChange: (String) -> Unit,
    onBanUser: (String, String) -> Unit,
    onUnbanUser: (String) -> Unit,
    onSuspendUser: (String, Int) -> Unit,
    onToggleAdmin: (String, Boolean) -> Unit
) {
    val filteredUsers = remember(users, searchQuery, filterStatus) {
        users.filter { user ->
            val matchesSearch = searchQuery.isBlank() ||
                    user.username.contains(searchQuery, ignoreCase = true) ||
                    user.displayName.contains(searchQuery, ignoreCase = true) ||
                    user.email.contains(searchQuery, ignoreCase = true) ||
                    user.id.contains(searchQuery, ignoreCase = true)

            val matchesFilter = when (filterStatus) {
                "active" -> user.status == "active"
                "suspended" -> user.status == "suspended"
                "banned" -> user.status == "banned"
                "admin" -> user.role == "admin"
                else -> true
            }

            matchesSearch && matchesFilter
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
        // Search Input
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            placeholder = { Text("بحث عن مستخدم بالاسم، البريد أو المعرف...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = ZevoraCyan) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary,
                focusedBorderColor = ZevoraCyan,
                unfocusedBorderColor = ZevoraBorder,
                focusedContainerColor = ZevoraDarkSurface,
                unfocusedContainerColor = ZevoraDarkSurface
            ),
            shape = RoundedCornerShape(12.dp),
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("tracking_user_search_input")
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Filter chips
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            val filters = listOf(
                "ALL" to "الكل (${users.size})",
                "active" to "نشط (${users.count { it.status == "active" }})",
                "suspended" to "معلق (${users.count { it.status == "suspended" }})",
                "banned" to "محظور (${users.count { it.status == "banned" }})",
                "admin" to "مسؤولين (${users.count { it.role == "admin" }})"
            )
            items(filters) { (key, label) ->
                FilterChip(
                    selected = (filterStatus == key),
                    onClick = { onFilterStatusChange(key) },
                    label = { Text(label, fontSize = 11.5.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = ZevoraCyan,
                        selectedLabelColor = Color.Black,
                        containerColor = ZevoraDarkSurface,
                        labelColor = TextSecondary
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        borderColor = ZevoraBorder,
                        selectedBorderColor = ZevoraCyan,
                        enabled = true,
                        selected = filterStatus == key
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Users List
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(filteredUsers, key = { it.id }) { user ->
                UserTrackingCard(
                    user = user,
                    onBan = { onBanUser(user.id, "انتهاك سياسات المحتوى") },
                    onUnban = { onUnbanUser(user.id) },
                    onSuspend = { onSuspendUser(user.id, 7) },
                    onToggleAdmin = { onToggleAdmin(user.id, user.role != "admin") }
                )
            }
        }
    }
}

@Composable
private fun UserTrackingCard(
    user: UserEntity,
    onBan: () -> Unit,
    onUnban: () -> Unit,
    onSuspend: () -> Unit,
    onToggleAdmin: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault()) }
    val joinDate = remember(user.createdAt) { dateFormat.format(Date(user.createdAt)) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = ZevoraDarkSurface),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            when (user.status) {
                "banned" -> StatusBanned.copy(alpha = 0.5f)
                "suspended" -> StatusPending.copy(alpha = 0.5f)
                else -> ZevoraBorder
            }
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AsyncImage(
                    model = user.avatarUrl,
                    contentDescription = user.username,
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(ZevoraDarkElevated)
                )

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = user.displayName,
                            color = TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        if (user.role == "admin") {
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Default.AdminPanelSettings,
                                contentDescription = "Admin",
                                tint = AccentGold,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    Text(
                        text = "@${user.username} • ID: ${user.id}",
                        color = ZevoraCyan,
                        fontSize = 11.5.sp
                    )
                    Text(
                        text = user.email,
                        color = TextMuted,
                        fontSize = 11.sp
                    )
                }

                // Status Badge
                val (badgeBg, badgeText, statusColor) = when (user.status) {
                    "banned" -> Triple(StatusBanned.copy(alpha = 0.2f), "محظور", StatusBanned)
                    "suspended" -> Triple(StatusPending.copy(alpha = 0.2f), "معلق", StatusPending)
                    else -> Triple(StatusResolved.copy(alpha = 0.2f), "نشط", StatusResolved)
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(badgeBg)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = badgeText,
                        color = statusColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Stats row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(ZevoraDarkElevated)
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "المتابعون", color = TextMuted, fontSize = 10.sp)
                    Text(text = "${user.followersCount}", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "يتابع", color = TextMuted, fontSize = 10.sp)
                    Text(text = "${user.followingCount}", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "الإعجابات", color = TextMuted, fontSize = 10.sp)
                    Text(text = "${user.totalLikes}", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "تاريخ التسجيل", color = TextMuted, fontSize = 10.sp)
                    Text(text = joinDate, color = TextPrimary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (user.status == "banned") {
                    Button(
                        onClick = onUnban,
                        colors = ButtonDefaults.buttonColors(containerColor = StatusResolved),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f).height(34.dp)
                    ) {
                        Icon(Icons.Default.LockOpen, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("إلغاء الحظر", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                } else {
                    OutlinedButton(
                        onClick = onBan,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = StatusBanned),
                        border = androidx.compose.foundation.BorderStroke(1.dp, StatusBanned),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f).height(34.dp)
                    ) {
                        Icon(Icons.Default.Block, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("حظر", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = onSuspend,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = StatusPending),
                        border = androidx.compose.foundation.BorderStroke(1.dp, StatusPending),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f).height(34.dp)
                    ) {
                        Text("تعليق 7 أيام", fontSize = 11.sp, fontWeight = FontWeight.Medium)
                    }
                }

                OutlinedButton(
                    onClick = onToggleAdmin,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = if (user.role == "admin") AccentGold else TextSecondary
                    ),
                    border = androidx.compose.foundation.BorderStroke(1.dp, ZevoraBorder),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.height(34.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AdminPanelSettings,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (user.role == "admin") "مسؤول" else "ترقية مشرف",
                        fontSize = 10.5.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun ReportsTrackerSection(
    reports: List<ReportEntity>,
    filterStatus: String,
    onFilterStatusChange: (String) -> Unit,
    onResolveReport: (String, String, String) -> Unit,
    onDeleteVideo: (String) -> Unit,
    onBanReportedUser: (String, String) -> Unit
) {
    val filteredReports = remember(reports, filterStatus) {
        when (filterStatus) {
            "pending" -> reports.filter { it.status == "pending" }
            "resolved" -> reports.filter { it.status == "resolved" }
            "rejected" -> reports.filter { it.status == "rejected" }
            else -> reports
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
        // Filter row
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            val filters = listOf(
                "ALL" to "كافة البلاغات (${reports.size})",
                "pending" to "قيد المراجعة (${reports.count { it.status == "pending" }})",
                "resolved" to "تم المعالجة (${reports.count { it.status == "resolved" }})",
                "rejected" to "مرفوضة (${reports.count { it.status == "rejected" }})"
            )
            items(filters) { (key, label) ->
                FilterChip(
                    selected = (filterStatus == key),
                    onClick = { onFilterStatusChange(key) },
                    label = { Text(label, fontSize = 11.5.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = ZevoraRed,
                        selectedLabelColor = Color.White,
                        containerColor = ZevoraDarkSurface,
                        labelColor = TextSecondary
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        borderColor = ZevoraBorder,
                        selectedBorderColor = ZevoraRed,
                        enabled = true,
                        selected = filterStatus == key
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (filteredReports.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = StatusResolved,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("لا توجد بلاغات معلقة في هذا القسم", color = TextPrimary, fontWeight = FontWeight.Bold)
                    Text("سجل الرقابة نظيف وآمن 100%", color = TextSecondary, fontSize = 12.sp)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filteredReports, key = { it.id }) { report ->
                    ReportTrackingCard(
                        report = report,
                        onResolve = { action, notes -> onResolveReport(report.id, action, notes) },
                        onDeleteContent = {
                            if (report.targetType == "video") {
                                onDeleteVideo(report.targetId)
                                onResolveReport(report.id, "resolved", "تم حذف الفيديو المخالف")
                            }
                        },
                        onBanUser = {
                            onBanReportedUser(report.targetId, "مخالفة بلاغ: ${report.reason}")
                            onResolveReport(report.id, "resolved", "تم حظر المستخدم المبلغ عنه")
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ReportTrackingCard(
    report: ReportEntity,
    onResolve: (String, String) -> Unit,
    onDeleteContent: () -> Unit,
    onBanUser: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault()) }
    val time = remember(report.createdAt) { dateFormat.format(Date(report.createdAt)) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = ZevoraDarkSurface),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (report.status == "pending") ZevoraRed.copy(alpha = 0.6f) else ZevoraBorder
        )
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
                            .background(
                                when (report.targetType) {
                                    "video" -> ZevoraRed.copy(alpha = 0.2f)
                                    "user" -> ZevoraCyan.copy(alpha = 0.2f)
                                    else -> AccentGold.copy(alpha = 0.2f)
                                }
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = report.targetType.uppercase(),
                            color = when (report.targetType) {
                                "video" -> ZevoraRed
                                "user" -> ZevoraCyan
                                else -> AccentGold
                            },
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "ID: ${report.id}",
                        color = TextMuted,
                        fontSize = 11.sp
                    )
                }

                Text(
                    text = time,
                    color = TextMuted,
                    fontSize = 11.sp
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "سبب الإبلاغ: ${report.reason}",
                color = TextPrimary,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Bold
            )

            if (report.targetSnippet.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(ZevoraDarkElevated)
                        .padding(8.dp)
                ) {
                    Text(
                        text = "«${report.targetSnippet}»",
                        color = TextSecondary,
                        fontSize = 11.5.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "المُبلِّغ: @${report.reporterUsername}",
                    color = ZevoraCyan,
                    fontSize = 11.sp
                )
                if (report.targetOwnerUsername.isNotBlank()) {
                    Text(text = " • ", color = TextMuted)
                    Text(
                        text = "المُبلَّغ عنه: @${report.targetOwnerUsername}",
                        color = ZevoraRed,
                        fontSize = 11.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Action buttons if pending
            if (report.status == "pending") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (report.targetType == "video") {
                        Button(
                            onClick = onDeleteContent,
                            colors = ButtonDefaults.buttonColors(containerColor = ZevoraRed),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f).height(34.dp)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("حذف الفيديو", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    OutlinedButton(
                        onClick = onBanUser,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = StatusBanned),
                        border = androidx.compose.foundation.BorderStroke(1.dp, StatusBanned),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f).height(34.dp)
                    ) {
                        Icon(Icons.Default.Block, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("حظر الحساب", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = { onResolve("rejected", "تم فحص المحتوى ولا يخالف المعايير") },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary),
                        border = androidx.compose.foundation.BorderStroke(1.dp, ZevoraBorder),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Text("رفض البلاغ", fontSize = 11.sp)
                    }
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (report.status == "resolved") Icons.Default.CheckCircle else Icons.Default.Warning,
                        contentDescription = null,
                        tint = if (report.status == "resolved") StatusResolved else TextMuted,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "الحالة: ${if (report.status == "resolved") "تم الإجراء بنجاح" else "تم رفض البلاغ"} بواسطة ${report.resolvedByAdmin}",
                        color = if (report.status == "resolved") StatusResolved else TextMuted,
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun AuditAndAnalyticsSection(
    users: List<UserEntity>,
    reports: List<ReportEntity>,
    violations: List<com.example.data.local.entities.ViolationEntity>
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(14.dp)
    ) {
        Text(
            text = "إحصائيات المنصة ونزاهة المحتوى",
            color = TextPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(10.dp))

        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = ZevoraDarkSurface),
            border = androidx.compose.foundation.BorderStroke(1.dp, ZevoraBorder)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(text = "معدل سرعة الاستجابة للبلاغات", color = TextSecondary, fontSize = 12.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(text = "0.8 دقيقة", color = ZevoraCyan, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "(أسرع من معيار الصناعة بـ 85%)", color = StatusResolved, fontSize = 11.sp)
                }

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = ZevoraBorder)
                Spacer(modifier = Modifier.height(12.dp))

                Text(text = "سجل الإجراءات والعقوبات الأخيرة", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))

                if (violations.isEmpty()) {
                    Text("لا توجد مخالفات مسجلة حالياً.", color = TextMuted, fontSize = 11.5.sp)
                } else {
                    violations.take(5).forEach { viol ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(text = "إجراء: ${viol.actionTaken}", color = ZevoraRed, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                                Text(text = "السبب: ${viol.reason}", color = TextMuted, fontSize = 10.sp)
                            }
                            Text(text = "بواسطة @${viol.adminUsername}", color = ZevoraCyan, fontSize = 10.5.sp)
                        }
                    }
                }
            }
        }
    }
}
