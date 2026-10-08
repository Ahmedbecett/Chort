package com.example.ui.screens.settings

import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.LocationManager
import android.net.Uri
import android.provider.ContactsContract
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.example.BuildConfig
import com.example.data.remote.ApiSession
import com.example.data.repository.ZevoraRepository
import com.example.ui.screens.activity.compactCount
import com.example.ui.screens.create.MediaStoreLoader
import com.example.ui.screens.offline.OfflineStore
import com.example.ui.screens.offline.formatBytes
import com.example.util.AppPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Light reference styling (matches the mandated screenshots).
private val PageBg = Color(0xFFF6F6F7)
private val PageCard = Color.White
private val PageInk = Color(0xFF111111)
private val PageMuted = Color(0xFF8D8D92)
private val PageAccent = Color(0xFFFF2D55)

// ---------------------------------------------------------------- widgets

@Composable
fun SettingsPageShell(
    title: String,
    onBack: () -> Unit,
    content: @Composable () -> Unit
) {
    Column(Modifier.fillMaxSize().background(PageBg)) {
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, "Back", tint = PageInk, modifier = Modifier.size(28.dp))
            }
            Text(title, color = PageInk, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
        }
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 15.dp)
        ) {
            content()
            Spacer(Modifier.height(60.dp))
        }
    }
}

@Composable
fun PrefCard(content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(PageCard)
    ) { content() }
}

@Composable
fun PrefNote(text: String) {
    Text(
        text,
        color = PageMuted,
        fontSize = 12.5.sp,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 14.dp)
    )
}

@Composable
fun PrefSectionTitle(text: String) {
    Text(
        text,
        color = Color(0xFF85858A),
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(start = 16.dp, top = 18.dp, bottom = 8.dp)
    )
}

@Composable
fun PrefRow(
    icon: ImageVector?,
    title: String,
    subtitle: String? = null,
    value: String? = null,
    danger: Boolean = false,
    showChevron: Boolean = true,
    onClick: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(icon, null, tint = if (danger) PageAccent else Color(0xFFAAAAAE), modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(14.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                title,
                color = if (danger) PageAccent else PageInk,
                fontSize = 15.5.sp,
                fontWeight = FontWeight.SemiBold
            )
            if (subtitle != null) {
                Text(subtitle, color = PageMuted, fontSize = 12.5.sp)
            }
        }
        if (value != null) Text(value, color = PageMuted, fontSize = 13.5.sp)
        if (showChevron) {
            Icon(Icons.Default.ChevronRight, null, tint = Color(0xFFAAAAAE), modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
fun SwitchRow(
    icon: ImageVector?,
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onChange: (Boolean) -> Unit
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(icon, null, tint = Color(0xFFAAAAAE), modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(14.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, color = PageInk, fontSize = 15.5.sp, fontWeight = FontWeight.SemiBold)
            if (subtitle != null) Text(subtitle, color = PageMuted, fontSize = 12.5.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = PageAccent,
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = Color(0xFFE0E0E4)
            )
        )
    }
}

@Composable
private fun LightOutlinedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    singleLine: Boolean = false,
    maxLines: Int = Int.MAX_VALUE,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default
) {
    androidx.compose.material3.OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        singleLine = singleLine,
        maxLines = maxLines,
        keyboardOptions = keyboardOptions,
        shape = RoundedCornerShape(12.dp),
        colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
            focusedTextColor = PageInk,
            unfocusedTextColor = PageInk,
            focusedContainerColor = PageCard,
            unfocusedContainerColor = PageCard,
            focusedBorderColor = PageAccent,
            unfocusedBorderColor = Color(0xFFD8D8DE),
            cursorColor = PageAccent
        )
    )
}

@Composable
fun ChoicePage(
    title: String,
    note: String,
    options: List<Pair<String, String?>>,
    current: String,
    onPick: (String) -> Unit,
    onBack: () -> Unit
) {
    SettingsPageShell(title = title, onBack = onBack) {
        PrefCard {
            options.forEach { (value, label) ->
                Row(
                    Modifier.fillMaxWidth()
                        .clickable { onPick(value) }
                        .padding(horizontal = 18.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(label ?: value, color = PageInk, fontSize = 15.5.sp, fontWeight = FontWeight.SemiBold)
                    }
                    if (value == current) {
                        Icon(Icons.Default.Check, null, tint = PageAccent)
                    }
                }
            }
        }
        PrefNote(note)
    }
}

/** Private-account sync shared by every switch (settings list + audience). */
fun syncPrivateAccount(
    repository: ZevoraRepository,
    scope: kotlinx.coroutines.CoroutineScope,
    context: Context,
    enabled: Boolean,
    onDone: (Boolean) -> Unit
) {
    scope.launch {
        val result = repository.setPrivateAccount(enabled)
        withContext(Dispatchers.Main) {
            if (result.isSuccess) {
                Toast.makeText(
                    context,
                    if (enabled) "Private account on" else "Private account off",
                    Toast.LENGTH_SHORT
                ).show()
                onDone(enabled)
            } else {
                Toast.makeText(
                    context,
                    result.exceptionOrNull()?.message ?: "Sync failed",
                    Toast.LENGTH_LONG
                ).show()
                onDone(!enabled)
            }
        }
    }
}

// ---------------------------------------------------------------- pages

@Composable
fun ManagePostsPage(repository: ZevoraRepository, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentUser by repository.currentUser.collectAsState()
    val myVideos by repository.getVideosByCreator(currentUser?.id ?: "").collectAsState(initial = emptyList())
    var confirmDelete by remember { mutableStateOf<String?>(null) }

    SettingsPageShell(title = "Manage posts", onBack = onBack) {
        PrefNote("Your published videos (${myVideos.size}). Deleting removes the video from the server and this device.")
        if (myVideos.isEmpty()) {
            PrefCard {
                Text(
                    "Nothing published yet.",
                    color = PageMuted,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(20.dp)
                )
            }
        } else {
            PrefCard {
                myVideos.forEach { video ->
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
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
                                color = PageInk,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                "${video.viewsCount} views • ${video.likesCount} likes",
                                color = PageMuted,
                                fontSize = 12.sp
                            )
                        }
                        IconButton(onClick = { confirmDelete = video.id }) {
                            Icon(Icons.Default.Delete, "Delete", tint = PageAccent)
                        }
                    }
                }
            }
        }
    }

    confirmDelete?.let { videoId ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Delete this video?") },
            text = { Text("It will be removed from Rivo permanently. This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = null
                    scope.launch {
                        val ok = repository.deleteVideo(videoId)
                        AppPrefs.removeBoost(videoId)
                        withContext(Dispatchers.Main) {
                            Toast.makeText(
                                context,
                                if (ok) "Video deleted" else "Delete failed",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }) { Text("Delete", color = PageAccent, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
fun ContentPrefsPage(onBack: () -> Unit) {
    val context = LocalContext.current
    val restricted by AppPrefs.restrictedMode.collectAsState()
    var askPin by remember { mutableStateOf(false) }
    var pinInput by remember { mutableStateOf("") }
    val hiddenCount = remember { AppPrefs.getHiddenVideoIds().size }

    SettingsPageShell(title = "Content preferences", onBack = onBack) {
        PrefCard {
            SwitchRow(
                icon = null,
                title = "Restricted Mode",
                subtitle = "Hide videos matching the on-device mature-content filter",
                checked = restricted,
                onChange = { want ->
                    if (!want && AppPrefs.isFamilyLocked()) {
                        pinInput = ""
                        askPin = true
                    } else {
                        AppPrefs.setRestrictedMode(want)
                        Toast.makeText(
                            context,
                            if (want) "Restricted Mode on" else "Restricted Mode off",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            )
        }
        PrefNote("When Family Pairing is on, turning Restricted Mode off requires the family PIN.")
        PrefCard {
            PrefRow(
                icon = null,
                title = "Hidden videos",
                subtitle = "Videos you marked “Not interested”",
                value = "$hiddenCount",
                onClick = { }
            )
        }
        if (hiddenCount > 0) {
            Button(
                onClick = {
                    AppPrefs.clearHiddenVideos()
                    Toast.makeText(context, "Hidden videos restored to feeds", Toast.LENGTH_SHORT).show()
                    onBack()
                },
                colors = ButtonDefaults.buttonColors(containerColor = PageInk),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
            ) {
                Text("Show hidden videos again")
            }
        }
        PrefNote("Blocked accounts never appear in your feeds. Manage them under Privacy → Blocked accounts.")
    }

    if (askPin) {
        AlertDialog(
            onDismissRequest = { askPin = false },
            title = { Text("Family PIN required") },
            text = {
                Column {
                    Text("Enter the 4-digit family PIN to turn Restricted Mode off.")
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = pinInput,
                        onValueChange = { pinInput = it.filter(Char::isDigit).take(6) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (pinInput == AppPrefs.getFamilyPin() && pinInput.length >= 4) {
                        AppPrefs.setRestrictedMode(false)
                        askPin = false
                        Toast.makeText(context, "Restricted Mode off", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Wrong PIN", Toast.LENGTH_SHORT).show()
                    }
                }) { Text("Confirm") }
            },
            dismissButton = {
                TextButton(onClick = { askPin = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
fun NotificationsPage(repository: ZevoraRepository, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentUser by repository.currentUser.collectAsState()
    var push by remember { mutableStateOf(AppPrefs.isPushEnabled()) }
    var likes by remember { mutableStateOf(AppPrefs.isNotifTypeEnabled("like")) }
    var comments by remember { mutableStateOf(AppPrefs.isNotifTypeEnabled("comment")) }
    var follows by remember { mutableStateOf(AppPrefs.isNotifTypeEnabled("follow")) }
    var mentions by remember { mutableStateOf(AppPrefs.isNotifTypeEnabled("mention")) }
    var system by remember { mutableStateOf(AppPrefs.isNotifTypeEnabled("system")) }
    var syncing by remember { mutableStateOf(false) }

    SettingsPageShell(title = "Notifications", onBack = onBack) {
        PrefCard {
            SwitchRow(null, "Push notifications", "Master switch for the inbox badge and sync", push) {
                push = it
                AppPrefs.setPushEnabled(it)
            }
        }
        PrefSectionTitle("Inbox categories")
        PrefCard {
            SwitchRow(null, "Likes", null, likes) { likes = it; AppPrefs.setNotifTypeEnabled("like", it) }
            SwitchRow(null, "Comments", null, comments) { comments = it; AppPrefs.setNotifTypeEnabled("comment", it) }
            SwitchRow(null, "New followers", null, follows) { follows = it; AppPrefs.setNotifTypeEnabled("follow", it) }
            SwitchRow(null, "Mentions", "Also see Privacy → Mentions", mentions) {
                mentions = it
                AppPrefs.setNotifTypeEnabled("mention", it)
            }
            SwitchRow(null, "System", null, system) { system = it; AppPrefs.setNotifTypeEnabled("system", it) }
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = {
                    syncing = true
                    scope.launch {
                        val result = repository.syncRemoteNotifications()
                        syncing = false
                        Toast.makeText(
                            context,
                            if (result.isSuccess) "Synced ${result.getOrNull() ?: 0} notifications" else "Sync failed",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                },
                enabled = !syncing,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = PageInk),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(if (syncing) "Syncing…" else "Sync now")
            }
            Button(
                onClick = {
                    scope.launch {
                        val id = currentUser?.id ?: return@launch
                        repository.markAllNotificationsRead(id)
                        repository.markRemoteNotificationsRead()
                        Toast.makeText(context, "All notifications marked as read", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = PageCard),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Mark all read", color = PageInk)
            }
        }
        PrefNote("Turning a category off hides it from your Inbox immediately.")
    }
}

@Composable
fun ScreenTimePage(onBack: () -> Unit) {
    val today = remember { AppPrefs.getTodayForegroundMinutes() }
    val week = remember { AppPrefs.getWeekForegroundMinutes() }
    var reminder by remember { mutableStateOf(AppPrefs.getScreenTimeReminderMinutes()) }
    val maxDay = remember(week) { (week.maxOfOrNull { it.second } ?: 1L).coerceAtLeast(1L) }

    SettingsPageShell(title = "Time and well-being", onBack = onBack) {
        PrefCard {
            Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Today on Rivo", color = PageMuted, fontSize = 13.sp)
                Text(
                    if (today < 60) "${today}m" else "${today / 60}h ${today % 60}m",
                    color = PageInk,
                    fontSize = 40.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Text("tracked on this device only", color = PageMuted, fontSize = 12.sp)
            }
        }
        PrefSectionTitle("This week")
        PrefCard {
            Row(
                Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.Bottom
            ) {
                week.forEach { (day, minutes) ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            if (minutes >= 60) "${minutes / 60}h" else "${minutes}m",
                            color = PageInk,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(4.dp))
                        Box(
                            modifier = Modifier
                                .width(26.dp)
                                .height((12 + 88 * minutes / maxDay).toInt().dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (minutes >= reminder && reminder > 0) PageAccent else PageInk)
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(day.take(1), color = PageMuted, fontSize = 11.sp)
                    }
                }
            }
        }
        PrefSectionTitle("Daily reminder")
        PrefCard {
            listOf(0 to "Off", 15 to "15 minutes", 30 to "30 minutes", 60 to "1 hour", 120 to "2 hours").forEach { (value, label) ->
                Row(
                    Modifier.fillMaxWidth()
                        .clickable {
                            reminder = value
                            AppPrefs.setScreenTimeReminderMinutes(value)
                        }
                        .padding(horizontal = 18.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(label, color = PageInk, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    if (value == reminder) Icon(Icons.Default.Check, null, tint = PageAccent)
                }
            }
        }
        PrefNote(
            if (reminder > 0 && today >= reminder) "You have reached today's $reminder-minute goal. Take a break!"
            else if (reminder > 0) "We'll highlight the days you pass $reminder minutes."
            else "Turn on a daily reminder to keep screen time in check."
        )
    }
}

@Composable
fun FamilyPage(onBack: () -> Unit) {
    val context = LocalContext.current
    var locked by remember { mutableStateOf(AppPrefs.isFamilyLocked()) }
    var pin1 by remember { mutableStateOf("") }
    var pin2 by remember { mutableStateOf("") }
    var oldPin by remember { mutableStateOf("") }
    var changing by remember { mutableStateOf(false) }

    SettingsPageShell(title = "Family Pairing", onBack = onBack) {
        if (!locked) {
            PrefNote("Set a family PIN to lock Restricted Mode on. Turning it off will require this PIN.")
            PrefCard {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text("New 4–6 digit PIN", color = PageInk, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    LightOutlinedTextField(
                        value = pin1,
                        onValueChange = { pin1 = it.filter(Char::isDigit).take(6) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(10.dp))
                    Text("Confirm PIN", color = PageInk, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    LightOutlinedTextField(
                        value = pin2,
                        onValueChange = { pin2 = it.filter(Char::isDigit).take(6) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(14.dp))
                    Button(
                        onClick = {
                            if (pin1.length < 4) {
                                Toast.makeText(context, "PIN must be at least 4 digits", Toast.LENGTH_SHORT).show()
                            } else if (pin1 != pin2) {
                                Toast.makeText(context, "PINs do not match", Toast.LENGTH_SHORT).show()
                            } else {
                                AppPrefs.setFamilyPin(pin1)
                                AppPrefs.setRestrictedMode(true)
                                locked = true
                                Toast.makeText(context, "Family Pairing on — Restricted Mode locked", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = PageAccent),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Enable Family Pairing", fontWeight = FontWeight.Bold)
                    }
                }
            }
        } else {
            PrefCard {
                SwitchRow(null, "Family Pairing", "Restricted Mode is locked on", true) { }
            }
            PrefNote("To turn pairing off or change the PIN, enter the current PIN below.")
            PrefCard {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text("Current PIN", color = PageInk, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    LightOutlinedTextField(
                        value = oldPin,
                        onValueChange = { oldPin = it.filter(Char::isDigit).take(6) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (changing) {
                        Spacer(Modifier.height(10.dp))
                        Text("New PIN", color = PageInk, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(8.dp))
                        LightOutlinedTextField(
                            value = pin1,
                            onValueChange = { pin1 = it.filter(Char::isDigit).take(6) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = {
                                if (oldPin == AppPrefs.getFamilyPin()) {
                                    if (changing) {
                                        if (pin1.length < 4) {
                                            Toast.makeText(context, "New PIN too short", Toast.LENGTH_SHORT).show()
                                        } else {
                                            AppPrefs.setFamilyPin(pin1)
                                            changing = false
                                            oldPin = ""
                                            pin1 = ""
                                            Toast.makeText(context, "PIN changed", Toast.LENGTH_SHORT).show()
                                        }
                                    } else {
                                        changing = true
                                    }
                                } else {
                                    Toast.makeText(context, "Wrong PIN", Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = PageInk),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(if (changing) "Save PIN" else "Change PIN")
                        }
                        Button(
                            onClick = {
                                if (oldPin == AppPrefs.getFamilyPin()) {
                                    AppPrefs.setFamilyPin("")
                                    locked = false
                                    Toast.makeText(context, "Family Pairing off", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "Wrong PIN", Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = PageCard),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Turn off", color = PageAccent)
                        }
                    }
                }
            }
        }
        PrefNote("This is a soft on-device lock: it stops casual changes on this phone.")
    }
}

@Composable
fun AccountPage(repository: ZevoraRepository, onBack: () -> Unit, onDeleted: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentUser by repository.currentUser.collectAsState()
    val me = currentUser
    var displayName by remember(me) { mutableStateOf(me?.displayName ?: "") }
    var username by remember(me) { mutableStateOf(me?.username ?: "") }
    var bio by remember(me) { mutableStateOf(me?.bio ?: "") }
    var saving by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    SettingsPageShell(title = "Account", onBack = onBack) {
        if (me == null) {
            PrefCard {
                Text("You are not logged in.", color = PageMuted, modifier = Modifier.padding(20.dp))
            }
            return@SettingsPageShell
        }
        PrefCard {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                Text("Display name", color = PageInk, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                LightOutlinedTextField(value = displayName, onValueChange = { displayName = it.take(50) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(10.dp))
                Text("Username", color = PageInk, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                LightOutlinedTextField(value = username, onValueChange = { username = it.take(30) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(10.dp))
                Text("Bio", color = PageInk, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                LightOutlinedTextField(value = bio, onValueChange = { bio = it.take(300) }, modifier = Modifier.fillMaxWidth(), maxLines = 3)
                Spacer(Modifier.height(14.dp))
                Button(
                    onClick = {
                        saving = true
                        scope.launch {
                            val result = repository.updateProfile(displayName.trim(), username.trim(), bio.trim())
                            saving = false
                            Toast.makeText(
                                context,
                                if (result.isSuccess) "Profile saved" else "Save failed: ${result.exceptionOrNull()?.message}",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    },
                    enabled = !saving,
                    colors = ButtonDefaults.buttonColors(containerColor = PageAccent),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (saving) "Saving…" else "Save changes", fontWeight = FontWeight.Bold)
                }
            }
        }
        PrefSectionTitle("Details")
        PrefCard {
            PrefRow(null, "Email", me.email.ifBlank { "—" }, showChevron = false) { }
            PrefRow(null, "User ID", me.id.take(18) + "…", showChevron = false) {
                try {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("Rivo user id", me.id))
                    Toast.makeText(context, "User ID copied", Toast.LENGTH_SHORT).show()
                } catch (_: Exception) {
                }
            }
            PrefRow(
                null,
                "Member since",
                SimpleDateFormat("MMM d, yyyy", Locale.US).format(Date(me.createdAt)),
                showChevron = false
            ) { }
            PrefRow(null, "Role", me.role, showChevron = false) { }
        }
        PrefSectionTitle("Danger zone")
        PrefCard {
            PrefRow(null, "Delete account", "Removes your profile, videos and data", danger = true, showChevron = false) {
                confirmDelete = true
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete your account?") },
            text = { Text("Your profile, videos, likes and comments will be permanently removed. This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    scope.launch {
                        val result = repository.deleteAccount()
                        if (result.isSuccess) {
                            repository.logout()
                            withContext(Dispatchers.Main) { onDeleted() }
                        } else {
                            withContext(Dispatchers.Main) {
                                Toast.makeText(context, "Delete failed: ${result.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                }) { Text("Delete forever", color = PageAccent, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
fun SecurityPage(repository: ZevoraRepository, onBack: () -> Unit, onChangePassword: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var sessions by remember { mutableStateOf<List<ApiSession>?>(null) }
    var providers by remember { mutableStateOf<List<com.example.data.remote.LinkedProvider>?>(null) }
    var linking by remember { mutableStateOf<String?>(null) }

    fun refreshSessions() {
        scope.launch {
            sessions = repository.listMySessions().getOrNull()
        }
    }

    fun refreshProviders() {
        scope.launch {
            providers = repository.getLinkedProviders().getOrNull()
        }
    }

    LaunchedEffect(Unit) {
        refreshSessions()
        refreshProviders()
    }

    fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { }

    SettingsPageShell(title = "Security & permissions", onBack = onBack) {
        PrefCard {
            PrefRow(null, "Change password", "Update your login password") { onChangePassword() }
        }
        PrefSectionTitle("Active sessions")
        PrefCard {
            val list = sessions
            if (list == null) {
                Row(Modifier.fillMaxWidth().padding(20.dp), horizontalArrangement = Arrangement.Center) {
                    CircularProgressIndicator(color = PageAccent, modifier = Modifier.size(22.dp))
                }
            } else if (list.isEmpty()) {
                Text("No active sessions found.", color = PageMuted, modifier = Modifier.padding(20.dp))
            } else {
                list.forEach { session ->
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                (if (session.current) "This device • " else "") +
                                    (session.userAgent?.take(40) ?: "Unknown device"),
                                color = PageInk,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                (session.ipAddress ?: "IP hidden") + " • since " +
                                    SimpleDateFormat("MMM d", Locale.US).format(Date(session.createdAt)),
                                color = PageMuted,
                                fontSize = 12.sp
                            )
                        }
                        if (!session.current) {
                            TextButton(onClick = {
                                scope.launch {
                                    val ok = repository.revokeMySession(session.id).getOrNull() == true
                                    Toast.makeText(
                                        context,
                                        if (ok) "Session revoked" else "Revoke failed",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    if (ok) refreshSessions()
                                }
                            }) {
                                Text("Revoke", color = PageAccent, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
        }
        PrefSectionTitle("Linked accounts")
        PrefCard {
            val list = providers
            if (list == null) {
                Row(Modifier.fillMaxWidth().padding(20.dp), horizontalArrangement = Arrangement.Center) {
                    CircularProgressIndicator(color = PageAccent, modifier = Modifier.size(22.dp))
                }
            } else {
                if (list.isEmpty()) {
                    Text(
                        "No social accounts linked yet.",
                        color = PageMuted,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp)
                    )
                }
                list.forEach { provider ->
                    PrefRow(
                        null,
                        provider.provider.replaceFirstChar { it.uppercase() },
                        provider.email ?: provider.linkedAt ?: "Linked",
                        value = "✓",
                        showChevron = false
                    ) { }
                }
                val activity = context as? Activity
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = {
                            if (activity == null) return@Button
                            linking = "google"
                            scope.launch {
                                val result = repository.linkGoogleToMyAccount(activity)
                                linking = null
                                Toast.makeText(
                                    context,
                                    if (result.isSuccess) "Google linked" else "Link failed",
                                    Toast.LENGTH_SHORT
                                ).show()
                                refreshProviders()
                            }
                        },
                        enabled = linking == null,
                        colors = ButtonDefaults.buttonColors(containerColor = PageInk),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(if (linking == "google") "…" else "+ Google", fontSize = 13.sp)
                    }
                    Button(
                        onClick = {
                            if (activity == null) return@Button
                            linking = "facebook"
                            scope.launch {
                                val result = repository.linkFacebookToMyAccount(activity)
                                linking = null
                                Toast.makeText(
                                    context,
                                    if (result.isSuccess) "Facebook linked" else "Link failed",
                                    Toast.LENGTH_SHORT
                                ).show()
                                refreshProviders()
                            }
                        },
                        enabled = linking == null,
                        colors = ButtonDefaults.buttonColors(containerColor = PageInk),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(if (linking == "facebook") "…" else "+ Facebook", fontSize = 13.sp)
                    }
                }
            }
        }
        PrefSectionTitle("App permissions")
        PrefCard {
            PermissionRow("Camera", Manifest.permission.CAMERA, ::hasPermission, permissionLauncher)
            PermissionRow("Microphone", Manifest.permission.RECORD_AUDIO, ::hasPermission, permissionLauncher)
            val storagePermission = if (android.os.Build.VERSION.SDK_INT >= 33) {
                Manifest.permission.READ_MEDIA_VIDEO
            } else {
                Manifest.permission.READ_EXTERNAL_STORAGE
            }
            PermissionRow("Photos & videos", storagePermission, ::hasPermission, permissionLauncher)
            if (android.os.Build.VERSION.SDK_INT >= 33) {
                PermissionRow("Notifications", Manifest.permission.POST_NOTIFICATIONS, ::hasPermission, permissionLauncher)
            }
            PrefRow(null, "Open system settings", "Manage every permission in one place") {
                try {
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
                    )
                } catch (_: Exception) {
                }
            }
        }
    }
}

@Composable
private fun PermissionRow(
    label: String,
    permission: String,
    hasPermission: (String) -> Boolean,
    launcher: androidx.activity.result.ActivityResultLauncher<Array<String>>
) {
    val granted = hasPermission(permission)
    PrefRow(
        null,
        label,
        value = if (granted) "Granted" else "Not granted",
        showChevron = !granted
    ) {
        if (!granted) launcher.launch(arrayOf(permission))
    }
}

@Composable
fun AnalyticsPage(repository: ZevoraRepository, onBack: () -> Unit, onOpenStudio: () -> Unit) {
    val currentUser by repository.currentUser.collectAsState()
    val meId = currentUser?.id ?: ""
    val myVideos by repository.getVideosByCreator(meId).collectAsState(initial = emptyList())
    val followers by repository.getFollowersCount(meId).collectAsState(initial = 0)
    val totalViews = remember(myVideos) { myVideos.sumOf { it.viewsCount } }
    val totalLikes = remember(myVideos) { myVideos.sumOf { it.likesCount } }
    val engagement = remember(totalViews, totalLikes) {
        if (totalViews <= 0) 0f else totalLikes.toFloat() / totalViews * 100
    }

    SettingsPageShell(title = "Analytics", onBack = onBack) {
        PrefCard {
            Column(Modifier.fillMaxWidth().padding(18.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    MiniStat(compactCount(totalViews), "Views")
                    MiniStat(compactCount(totalLikes), "Likes")
                    MiniStat(compactCount(followers), "Followers")
                    MiniStat("${myVideos.size}", "Videos")
                }
                Spacer(Modifier.height(14.dp))
                Text("Engagement ${"%.1f%%".format(engagement)}", color = PageInk, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { (engagement / 100f).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                    color = PageAccent,
                    trackColor = Color(0xFFE8E8EC)
                )
            }
        }
        Button(
            onClick = onOpenStudio,
            colors = ButtonDefaults.buttonColors(containerColor = PageInk),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
        ) {
            Text("Open Rivo Studio")
        }
        PrefNote("Full per-video breakdowns live in Rivo Studio.")
    }
}

@Composable
private fun MiniStat(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = PageInk, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
        Text(label, color = PageMuted, fontSize = 12.sp)
    }
}

@Composable
fun ShareProfilePage(repository: ZevoraRepository, onBack: () -> Unit, onOpenQr: () -> Unit) {
    val context = LocalContext.current
    val currentUser by repository.currentUser.collectAsState()
    val payload = "zevora:user:${currentUser?.id ?: "unknown"}:${currentUser?.username ?: "unknown"}"
    val showProfile = remember { AppPrefs.showProfileOnShare() }

    SettingsPageShell(title = "Share profile", onBack = onBack) {
        PrefCard {
            Column(Modifier.fillMaxWidth().padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("@${currentUser?.username ?: "unknown"}", color = PageInk, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text("Share your Rivo identity anywhere", color = PageMuted, fontSize = 13.sp)
                Spacer(Modifier.height(14.dp))
                Button(
                    onClick = {
                        try {
                            val text = if (showProfile) {
                                "Follow @${currentUser?.username} on Rivo! $payload"
                            } else {
                                "Find me on Rivo!"
                            }
                            val share = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, text)
                            }
                            context.startActivity(Intent.createChooser(share, "Share profile"))
                        } catch (e: Exception) {
                            Toast.makeText(context, "Share failed: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PageAccent),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Share profile", fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = onOpenQr,
                    colors = ButtonDefaults.buttonColors(containerColor = PageInk),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Show my QR code")
                }
            }
        }
        PrefNote("“Display profile when sharing links” in Privacy controls whether your handle is included.")
    }
}

@Composable
fun BlockedPage(repository: ZevoraRepository, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var blocked by remember { mutableStateOf(AppPrefs.getBlockedIds().toList()) }

    LaunchedEffect(Unit) {
        blocked = AppPrefs.getBlockedIds().toList()
    }

    SettingsPageShell(title = "Blocked accounts", onBack = onBack) {
        PrefNote("Blocked accounts can't appear in your feeds, and their videos are hidden everywhere.")
        if (blocked.isEmpty()) {
            PrefCard {
                Text("No blocked accounts.", color = PageMuted, modifier = Modifier.padding(20.dp))
            }
        } else {
            PrefCard {
                blocked.forEach { userId ->
                    val user by repository.getUserById(userId).collectAsState(initial = null)
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "@${user?.username ?: "unknown"}",
                                color = PageInk,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp
                            )
                            Text(user?.displayName ?: userId.take(12), color = PageMuted, fontSize = 12.sp)
                        }
                        TextButton(onClick = {
                            scope.launch {
                                AppPrefs.toggleBlocked(userId)
                                blocked = AppPrefs.getBlockedIds().toList()
                                Toast.makeText(context, "Unblocked", Toast.LENGTH_SHORT).show()
                            }
                        }) {
                            Text("Unblock", color = PageAccent, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MusicPage(onBack: () -> Unit) {
    val context = LocalContext.current
    var count by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val tracks = try {
                MediaStoreLoader.loadAudio(context)
            } catch (_: Exception) {
                emptyList()
            }
            withContext(Dispatchers.Main) { count = tracks.size }
        }
    }

    SettingsPageShell(title = "Music", onBack = onBack) {
        PrefCard {
            Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                val n = count
                if (n == null) {
                    CircularProgressIndicator(color = PageAccent)
                } else {
                    Text("$n", color = PageInk, fontSize = 40.sp, fontWeight = FontWeight.ExtraBold)
                    Text("audio tracks on this device", color = PageMuted, fontSize = 13.sp)
                }
            }
        }
        PrefNote("Pick any device track with “Add sound” in CREATE mode or the camera — it is mixed into your video for real. AAC/M4A embeds on every phone.")
    }
}

@Composable
fun AudiencePage(repository: ZevoraRepository, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val privateAccount by AppPrefs.privateAccount.collectAsState()
    val blockedCount = remember { AppPrefs.getBlockedIds().size }

    SettingsPageShell(title = "Audience control", onBack = onBack) {
        PrefCard {
            SwitchRow(
                null,
                "Private account",
                "Only followers see your followers, following and liked lists",
                privateAccount,
                onChange = { want ->
                    syncPrivateAccount(repository, scope, context, want) { }
                }
            )
        }
        PrefNote("Followers, following and liked lists on private accounts return a lock to strangers — enforced by the server.")
        PrefCard {
            PrefRow(null, "Blocked accounts", "$blockedCount blocked", showChevron = false) { }
            PrefRow(null, "Following list", AppPrefs.getFollowingVisibility(), showChevron = false) { }
            PrefRow(null, "Liked videos", AppPrefs.getLikedVisibility(), showChevron = false) { }
        }
        PrefNote("Change each list's visibility under Privacy in the main settings list.")
    }
}

@Composable
fun AdsPage(onBack: () -> Unit) {
    var personalized by remember { mutableStateOf(AppPrefs.isAdsPersonalized()) }

    SettingsPageShell(title = "Ads", onBack = onBack) {
        PrefCard {
            SwitchRow(
                null,
                "Personalized ads",
                "Use your activity to personalize future ads",
                personalized
            ) {
                personalized = it
                AppPrefs.setAdsPersonalized(it)
            }
        }
        PrefNote("Rivo currently shows no advertisements at all. This choice is saved on your device and will apply if an ads program ever launches.")
    }
}

@Composable
fun PlaybackPage(onBack: () -> Unit) {
    val autoplay by AppPrefs.autoplay.collectAsState()
    val muted by AppPrefs.feedMuted.collectAsState()
    val saver by AppPrefs.dataSaver.collectAsState()

    SettingsPageShell(title = "Playback", onBack = onBack) {
        PrefCard {
            SwitchRow(null, "Autoplay videos", "Play feed videos automatically", autoplay) {
                AppPrefs.setAutoplay(it)
            }
            SwitchRow(null, "Start muted", "Feeds open with sound off", muted) {
                AppPrefs.setFeedMuted(it)
            }
            SwitchRow(
                null,
                "Data Saver",
                "No autoplay and muted start on any connection",
                saver
            ) {
                AppPrefs.setDataSaver(it)
            }
        }
        PrefNote("Data Saver overrides autoplay: with it on, every video waits for your tap.")
    }
}

@Composable
fun ContactsPage(repository: ZevoraRepository, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var contactCount by remember { mutableStateOf<Int?>(null) }
    var city by remember { mutableStateOf(AppPrefs.getProfileCity()) }
    var showCity by remember { mutableStateOf(AppPrefs.showProfileCity()) }
    var locating by remember { mutableStateOf(false) }

    fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    fun countContacts() {
        scope.launch(Dispatchers.IO) {
            val count = try {
                context.contentResolver.query(
                    ContactsContract.Contacts.CONTENT_URI,
                    arrayOf(ContactsContract.Contacts._ID),
                    null,
                    null,
                    null
                )?.use { it.count } ?: 0
            } catch (_: Exception) {
                -1
            }
            withContext(Dispatchers.Main) { contactCount = count }
        }
    }

    val contactsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) countContacts()
        else Toast.makeText(context, "Contacts permission denied", Toast.LENGTH_SHORT).show()
    }

    val pickContact = rememberLauncherForActivityResult(
        ActivityResultContracts.PickContact()
    ) { uri ->
        if (uri != null) inviteContactBySms(context, uri)
    }

    val locationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { detectCity(context, scope, onResult = { found ->
        locating = false
        if (found != null) {
            city = found
            AppPrefs.setProfileCity(found, showCity)
            Toast.makeText(context, "City detected: $found", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Could not detect city — enter it manually", Toast.LENGTH_LONG).show()
        }
    }) }

    LaunchedEffect(Unit) {
        if (hasPermission(Manifest.permission.READ_CONTACTS)) countContacts()
    }

    SettingsPageShell(title = "Contacts and location", onBack = onBack) {
        PrefSectionTitle("Contacts")
        PrefCard {
            val n = contactCount
            if (n == null) {
                PrefRow(null, "Find friends", "Allow access to count your contacts") {
                    contactsLauncher.launch(Manifest.permission.READ_CONTACTS)
                }
            } else if (n < 0) {
                PrefRow(null, "Contacts unavailable", "Permission denied or empty", showChevron = false) { }
            } else {
                PrefRow(null, "$n contacts on this phone", "Invite anyone by SMS", showChevron = false) { }
                PrefRow(null, "Invite a contact", "Pick a contact and send an SMS invite") {
                    pickContact.launch(null)
                }
            }
        }
        PrefNote("Contact names and numbers never leave your phone — invites go through your SMS app.")
        PrefSectionTitle("Location")
        PrefCard {
            PrefRow(null, "Detect my city", city.ifBlank { "Use GPS or network location" }, showChevron = false) {
                if (hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION) ||
                    hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                ) {
                    locating = true
                    detectCity(context, scope, onResult = { found ->
                        locating = false
                        if (found != null) {
                            city = found
                            AppPrefs.setProfileCity(found, showCity)
                            Toast.makeText(context, "City detected: $found", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Could not detect city", Toast.LENGTH_LONG).show()
                        }
                    })
                } else {
                    locationLauncher.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_COARSE_LOCATION,
                            Manifest.permission.ACCESS_FINE_LOCATION
                        )
                    )
                }
            }
            if (locating) {
                Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.Center) {
                    CircularProgressIndicator(color = PageAccent, modifier = Modifier.size(22.dp))
                }
            }
            Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp)) {
                Text("Or type your city", color = PageInk, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                LightOutlinedTextField(
                    value = city,
                    onValueChange = {
                        city = it.take(60)
                        AppPrefs.setProfileCity(it.take(60), showCity)
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            SwitchRow(null, "Show city on my profile", city.ifBlank { "No city set" }, showCity && city.isNotBlank()) {
                showCity = it
                AppPrefs.setProfileCity(city, it)
            }
        }
        PrefNote("Your city only ever appears on your own profile, and only when you enable it.")
    }
}

private fun inviteContactBySms(context: Context, contactUri: Uri) {
    try {
        val id = contactUri.lastPathSegment ?: return
        var number: String? = null
        var name = ""
        context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.NUMBER,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME
            ),
            "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
            arrayOf(id),
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                number = cursor.getString(0)
                name = cursor.getString(1) ?: ""
            }
        }
        if (number.isNullOrBlank()) {
            Toast.makeText(context, "This contact has no phone number", Toast.LENGTH_SHORT).show()
            return
        }
        val sms = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$number")).apply {
            putExtra(
                "sms_body",
                "Hi${if (name.isNotBlank()) " $name" else ""}! Join me on Rivo — short videos, no noise: https://chort-nine.vercel.app"
            )
        }
        context.startActivity(sms)
    } catch (e: Exception) {
        Toast.makeText(context, "Invite failed: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

private fun detectCity(
    context: Context,
    scope: kotlinx.coroutines.CoroutineScope,
    onResult: (String?) -> Unit
) {
    scope.launch(Dispatchers.IO) {
        var found: String? = null
        try {
            val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val providers = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
            for (provider in providers) {
                try {
                    @Suppress("MissingPermission")
                    val location = manager.getLastKnownLocation(provider)
                    if (location != null) {
                        val results = try {
                            Geocoder(context, Locale.getDefault()).getFromLocation(
                                location.latitude,
                                location.longitude,
                                1
                            )
                        } catch (_: Exception) {
                            null
                        }
                        val first = results?.firstOrNull()
                        found = first?.locality ?: first?.subAdminArea ?: first?.adminArea
                        if (found != null) break
                    }
                } catch (_: Exception) {
                }
            }
        } catch (_: Exception) {
        }
        withContext(Dispatchers.Main) { onResult(found) }
    }
}

@Composable
fun StoragePage(onBack: () -> Unit, onOpenOffline: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var cacheBytes by remember { mutableStateOf<Long?>(null) }
    var vaultBytes by remember { mutableStateOf(OfflineStore.totalBytes(context)) }
    var historyCount by remember { mutableStateOf(AppPrefs.getWatchHistory().size) }

    fun refresh() {
        scope.launch(Dispatchers.IO) {
            val cache = try {
                context.cacheDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
            } catch (_: Exception) {
                0L
            }
            withContext(Dispatchers.Main) {
                cacheBytes = cache
                vaultBytes = OfflineStore.totalBytes(context)
                historyCount = AppPrefs.getWatchHistory().size
            }
        }
    }

    LaunchedEffect(Unit) { refresh() }

    SettingsPageShell(title = "Free up space", onBack = onBack) {
        PrefCard {
            PrefRow(null, "App cache", cacheBytes?.let { formatBytes(it) } ?: "…", showChevron = false) { }
            PrefRow(null, "Offline videos", formatBytes(vaultBytes)) { onOpenOffline() }
            PrefRow(null, "Watch history", "$historyCount entries", showChevron = false) { }
        }
        Button(
            onClick = {
                scope.launch(Dispatchers.IO) {
                    var freed = 0L
                    try {
                        context.cacheDir.listFiles()?.forEach { file ->
                            try {
                                freed += file.walkTopDown().filter { it.isFile }.sumOf { it.length() }
                                file.deleteRecursively()
                            } catch (_: Exception) {
                            }
                        }
                    } catch (_: Exception) {
                    }
                    withContext(Dispatchers.Main) {
                        refresh()
                        Toast.makeText(context, "Freed ${formatBytes(freed)} of cache", Toast.LENGTH_SHORT).show()
                    }
                }
            },
            colors = ButtonDefaults.buttonColors(containerColor = PageAccent),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
        ) {
            Text("Clear cache", fontWeight = FontWeight.Bold)
        }
        Button(
            onClick = {
                AppPrefs.clearWatchHistory()
                historyCount = 0
                Toast.makeText(context, "Watch history cleared", Toast.LENGTH_SHORT).show()
            },
            colors = ButtonDefaults.buttonColors(containerColor = PageInk),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        ) {
            Text("Clear watch history")
        }
        PrefNote("Cache holds thumbnails and previews — clearing it never deletes your account, videos or coins.")
    }
}

@Composable
fun HelpPage(onBack: () -> Unit) {
    val context = LocalContext.current
    var expanded by remember { mutableStateOf(-1) }
    val faqs = remember {
        listOf(
            "How do I publish a video?" to "Tap the + button, pick a video (or record with the camera), add a caption and press Post. Publishing earns you 5 coins.",
            "How do coins and Boosts work?" to "Check in daily (+25), watch videos (+1 per 10) and publish (+5). Spend coins in Promote to pin a video to the front of your profile with a BOOSTED badge.",
            "How do I go private?" to "Settings → Private account. Your followers, following and liked lists become visible to followers only, enforced by the server.",
            "A video won't play. What now?" to "Use Retry on the player, then check Free up space and your connection. If it persists, report it from the share sheet.",
            "How do I delete my data?" to "Settings → Account → Delete account removes your profile, videos and data from the server permanently."
        )
    }

    fun openUrl(url: String) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: Exception) {
            Toast.makeText(context, "Could not open: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    SettingsPageShell(title = "Help Center", onBack = onBack) {
        PrefCard {
            faqs.forEachIndexed { index, (question, answer) ->
                Column(
                    Modifier.fillMaxWidth()
                        .clickable { expanded = if (expanded == index) -1 else index }
                        .padding(horizontal = 18.dp, vertical = 13.dp)
                ) {
                    Text(question, color = PageInk, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    if (expanded == index) {
                        Spacer(Modifier.height(6.dp))
                        Text(answer, color = PageMuted, fontSize = 13.5.sp)
                    }
                }
            }
        }
        PrefSectionTitle("Still stuck?")
        PrefCard {
            PrefRow(null, "Browse known issues", "Public tracker with fixes and status") {
                openUrl("https://github.com/Ahmedbecett/Chort/issues")
            }
            PrefRow(null, "Report a problem", "Opens a pre-addressed issue form") {
                openUrl("https://github.com/Ahmedbecett/Chort/issues/new")
            }
        }
        PrefNote("The tracker is public — never post passwords or private data there.")
    }
}

@Composable
fun AboutPage(repository: ZevoraRepository, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var serverOk by remember { mutableStateOf<Boolean?>(null) }

    LaunchedEffect(Unit) {
        scope.launch {
            serverOk = repository.pingServer()
        }
    }

    SettingsPageShell(title = "About Rivo", onBack = onBack) {
        PrefCard {
            Column(Modifier.fillMaxWidth().padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Rivo", color = PageInk, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold)
                Text("Short videos, zero noise", color = PageMuted, fontSize = 13.sp)
                Spacer(Modifier.height(10.dp))
                Text(
                    "Version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                    color = PageInk,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(6.dp))
                val ok = serverOk
                Text(
                    when (ok) {
                        null -> "Checking server…"
                        true -> "● Production server reachable"
                        false -> "○ Server unreachable — offline mode"
                    },
                    color = if (ok == false) PageAccent else Color(0xFF0A9B58),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
        PrefSectionTitle("Credits")
        PrefCard {
            PrefRow(null, "Developer", "Ahmed Becetti", showChevron = false) { }
            PrefRow(null, "Package", com.example.BuildConfig.APPLICATION_ID, showChevron = false) { }
            PrefRow(null, "Backend", "chort-nine.vercel.app", showChevron = false) { }
        }
        PrefNote("Rivo is a global short-video platform. Every screen in this app is wired to production services — no demo content.")
    }
}

@Composable
fun SwitchAccountPage(
    repository: ZevoraRepository,
    onBack: () -> Unit,
    onLoggedOut: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentUser by repository.currentUser.collectAsState()
    val last = remember { AppPrefs.getLastAccount() }
    var confirm by remember { mutableStateOf(false) }

    SettingsPageShell(title = "Switch account", onBack = onBack) {
        PrefSectionTitle("Current session")
        PrefCard {
            PrefRow(
                null,
                "@${currentUser?.username ?: "guest"}",
                currentUser?.email ?: "Not logged in",
                value = "Active",
                showChevron = false
            ) { }
        }
        if (last != null && last.userId != currentUser?.id) {
            PrefSectionTitle("Previously used here")
            PrefCard {
                PrefRow(null, "@${last.username}", "Tap Log out, then sign in as ${last.username}", showChevron = false) { }
            }
            PrefNote("For your safety Rivo never stores passwords: switching signs you out first.")
        }
        Button(
            onClick = { confirm = true },
            colors = ButtonDefaults.buttonColors(containerColor = PageInk),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().padding(top = 14.dp)
        ) {
            Text("Log out & switch")
        }
    }

    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Log out?") },
            text = { Text("Your account and server data will not be deleted.") },
            confirmButton = {
                TextButton(onClick = {
                    confirm = false
                    scope.launch {
                        repository.logout()
                        withContext(Dispatchers.Main) { onLoggedOut() }
                    }
                }) { Text("Log out", color = PageAccent) }
            },
            dismissButton = {
                TextButton(onClick = { confirm = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
fun FollowingVisibilityPage(onBack: () -> Unit) {
    var current by remember { mutableStateOf(AppPrefs.getFollowingVisibility()) }
    ChoicePage(
        title = "Following list",
        note = "Who can see the accounts you follow. Viewers on Rivo 3.1+ always respect this; combine with Private account for full server enforcement.",
        options = listOf("Everyone" to "Everyone", "Followers" to "Followers", "Only you" to "Only you"),
        current = current,
        onPick = {
            current = it
            AppPrefs.setFollowingVisibility(it)
        },
        onBack = onBack
    )
}

@Composable
fun LikedVisibilityPage(onBack: () -> Unit) {
    var current by remember { mutableStateOf(AppPrefs.getLikedVisibility()) }
    ChoicePage(
        title = "Liked videos",
        note = "Who can see the videos you liked. Viewers on Rivo 3.1+ always respect this; combine with Private account for full server enforcement.",
        options = listOf("Everyone" to "Everyone", "Followers" to "Followers", "Only you" to "Only you"),
        current = current,
        onPick = {
            current = it
            AppPrefs.setLikedVisibility(it)
        },
        onBack = onBack
    )
}

@Composable
fun CommentsChoicePage(onBack: () -> Unit) {
    var current by remember { mutableStateOf(AppPrefs.allowComments.value) }
    ChoicePage(
        title = "Comments",
        note = "Who can comment on your videos. Enforced on this device and on updated apps; viewers on older versions may still comment.",
        options = listOf("everyone" to "Everyone", "followers" to "Followers", "none" to "No one"),
        current = current,
        onPick = {
            current = it
            AppPrefs.setAllowComments(it)
        },
        onBack = onBack
    )
}

@Composable
fun MentionsChoicePage(onBack: () -> Unit) {
    var current by remember { mutableStateOf(AppPrefs.getMentionMode()) }
    ChoicePage(
        title = "Mentions",
        note = "Who can mention you. “Followers” shows mentions from people you follow back; “No one” hides them all from your Inbox.",
        options = listOf("everyone" to "Everyone", "followers" to "Followers", "none" to "No one"),
        current = current,
        onPick = {
            current = it
            AppPrefs.setMentionMode(it)
        },
        onBack = onBack
    )
}

@Composable
fun DownloadsChoicePage(onBack: () -> Unit) {
    var current by remember { mutableStateOf(if (AppPrefs.allowDownloads.value) "on" else "off") }
    ChoicePage(
        title = "Downloads",
        note = "When off, the Save and Offline buttons disappear from your own videos on this device.",
        options = listOf("on" to "On", "off" to "Off"),
        current = current,
        onPick = {
            current = it
            AppPrefs.setAllowDownloads(it == "on")
        },
        onBack = onBack
    )
}

@Composable
fun DisplayPage(onBack: () -> Unit) {
    val current by AppPrefs.themeMode.collectAsState()
    ChoicePage(
        title = "Display",
        note = "Dark is the signature Rivo look. Pure black saves battery on OLED screens.",
        options = listOf("dark" to "Dark", "black" to "Pure black (OLED)"),
        current = current,
        onPick = { AppPrefs.setThemeMode(it) },
        onBack = onBack
    )
}

@Composable
fun LanguagePage(onBack: () -> Unit) {
    val context = LocalContext.current
    val current by AppPrefs.language.collectAsState()
    ChoicePage(
        title = "Language",
        note = "Applies after a quick restart of the current screen.",
        options = listOf(
            "system" to "System default",
            "ar" to "العربية",
            "en" to "English",
            "fr" to "Français"
        ),
        current = current,
        onPick = {
            AppPrefs.setLanguage(it)
            try {
                (context as? Activity)?.recreate()
            } catch (_: Exception) {
            }
        },
        onBack = onBack
    )
}

@Composable
fun CaptionSizePage(onBack: () -> Unit) {
    var current by remember { mutableStateOf(AppPrefs.getCaptionSize()) }
    ChoicePage(
        title = "Caption size",
        note = "Changes video caption text across Home and Friends feeds.",
        options = listOf("small" to "Small", "normal" to "Normal", "large" to "Large"),
        current = current,
        onPick = {
            current = it
            AppPrefs.setCaptionSize(it)
        },
        onBack = onBack
    )
}

@Composable
fun DmChoicePage(onBack: () -> Unit) {
    var current by remember { mutableStateOf(AppPrefs.getDmMode()) }
    ChoicePage(
        title = "Direct messages",
        note = "Your choice is saved and will apply as soon as chat launches. Meanwhile, follows and mentions reach you in the Inbox.",
        options = listOf("everyone" to "Everyone", "followers" to "Followers", "none" to "No one"),
        current = current,
        onPick = {
            current = it
            AppPrefs.setDmMode(it)
        },
        onBack = onBack
    )
}

@Composable
fun ReusePage(onBack: () -> Unit) {
    val current by AppPrefs.allowReuse.collectAsState()
    ChoicePage(
        title = "Reuse of content",
        note = "When on, the Remix button appears on your videos so others can build on them.",
        options = listOf("on" to "Allow remixes", "off" to "Don't allow"),
        current = if (current) "on" else "off",
        onPick = { AppPrefs.setAllowReuse(it == "on") },
        onBack = onBack
    )
}

@Composable
fun DataSaverInfoPage(onBack: () -> Unit) {
    val saver by AppPrefs.dataSaver.collectAsState()
    ChoicePage(
        title = "Data Saver",
        note = "With Data Saver on, videos never autoplay and always start muted — on Wi-Fi and mobile data alike.",
        options = listOf("on" to "On", "off" to "Off"),
        current = if (saver) "on" else "off",
        onPick = { AppPrefs.setDataSaver(it == "on") },
        onBack = onBack
    )
}

@Composable
fun LiteModePage(onBack: () -> Unit) {
    var lite by remember { mutableStateOf(AppPrefs.isLiteMode()) }
    ChoicePage(
        title = "Lite mode",
        note = "Lite mode turns Data Saver on and autoplay off for the lightest experience, and restores your previous choices when you leave it.",
        options = listOf("on" to "On", "off" to "Off"),
        current = if (lite) "on" else "off",
        onPick = {
            lite = it == "on"
            AppPrefs.setLiteMode(lite)
        },
        onBack = onBack
    )
}
