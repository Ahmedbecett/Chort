package com.example.ui.screens.discover

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.local.entities.UserEntity
import com.example.data.local.entities.VideoEntity
import com.example.data.repository.TokPulseRepository
import com.example.ui.theme.AccentGold
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

data class SoundSearchResult(
    val title: String,
    val creator: String,
    val videoCount: Int,
    val sampleVideo: VideoEntity?
)

@Composable
fun DiscoverScreen(
    repository: TokPulseRepository,
    onNavigateToProfile: (String) -> Unit,
    onSelectVideo: (VideoEntity) -> Unit,
    onNavigateToSound: (String) -> Unit = {},
    onBack: () -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("All") } // "All", "Videos", "Users", "Sounds", "Hashtags"
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val searchPrefs = remember { context.getSharedPreferences("chort_search", Context.MODE_PRIVATE) }
    var searchHistory by remember {
        mutableStateOf(searchPrefs.getStringSet("history", emptySet())?.toList().orEmpty())
    }
    var historyExpanded by remember { mutableStateOf(false) }
    var suggestionSeed by remember { mutableStateOf(0) }

    fun saveToHistory(query: String) {
        val clean = query.trim()
        if (clean.length < 2) return
        val updated = (listOf(clean) + searchHistory.filterNot { it.equals(clean, ignoreCase = true) }).take(10)
        searchHistory = updated
        searchPrefs.edit().putStringSet("history", updated.toSet()).apply()
    }
    fun removeFromHistory(query: String) {
        val updated = searchHistory.filterNot { it == query }
        searchHistory = updated
        searchPrefs.edit().putStringSet("history", updated.toSet()).apply()
    }
    fun submitSearch() {
        saveToHistory(searchQuery)
        keyboardController?.hide()
    }

    val allVideos by repository.getActiveVideos().collectAsState(initial = emptyList())
    val allUsers by repository.getAllUsersAdmin().collectAsState(initial = emptyList())
    val currentUser by repository.currentUser.collectAsState()
    val followingIds by repository.getFollowingIds(currentUser?.id ?: "").collectAsState(initial = emptyList())

    LaunchedEffect(searchQuery) {
        if (searchQuery.isNotBlank() && searchQuery.trim().length >= 2) {
            repository.searchRemote(searchQuery.trim())
        }
    }

    // Video search includes caption, tags, creator, AND music/sound title!
    val searchResultsVideos = remember(searchQuery, allVideos) {
        if (searchQuery.isBlank()) allVideos else {
            allVideos.filter {
                it.caption.contains(searchQuery, ignoreCase = true) ||
                it.tags.contains(searchQuery, ignoreCase = true) ||
                it.creatorUsername.contains(searchQuery, ignoreCase = true) ||
                it.musicTitle.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    // User search
    val searchResultsUsers = remember(searchQuery, allUsers) {
        if (searchQuery.isBlank()) allUsers.filter { it.role != "admin" } else {
            allUsers.filter {
                it.username.contains(searchQuery, ignoreCase = true) ||
                it.displayName.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    // Sound / Music search results extracted from real videos
    val allSounds = remember(allVideos) {
        allVideos
            .filter { it.musicTitle.isNotBlank() }
            .groupBy { it.musicTitle }
            .map { (title, vids) ->
                SoundSearchResult(
                    title = title,
                    creator = vids.firstOrNull()?.creatorUsername ?: "Creator",
                    videoCount = vids.size,
                    sampleVideo = vids.firstOrNull()
                )
            }
    }

    val searchResultsSounds = remember(searchQuery, allSounds) {
        if (searchQuery.isBlank()) allSounds else {
            allSounds.filter {
                it.title.contains(searchQuery, ignoreCase = true) ||
                it.creator.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    // Dynamic real hashtags extracted from actual video tags
    val dynamicTags = remember(allVideos) {
        val tagMap = mutableMapOf<String, Int>()
        allVideos.forEach { vid ->
            vid.tags.split(",", " ", "#").filter { it.isNotBlank() }.forEach { tag ->
                val clean = "#${tag.trim()}"
                tagMap[clean] = (tagMap[clean] ?: 0) + 1
            }
        }
        if (tagMap.isEmpty()) {
            listOf(Pair("#chort", "Official"), Pair("#fyp", "Trending"), Pair("#viral", "Featured"))
        } else {
            tagMap.entries.sortedByDescending { it.value }.map { Pair(it.key, "${it.value} clips") }
        }
    }

    val hasAnyResults = searchResultsVideos.isNotEmpty() || searchResultsUsers.isNotEmpty() || searchResultsSounds.isNotEmpty()

    // "You may like": suggestions drawn from real on-device content.
    val youMayLike = remember(dynamicTags, allSounds, allUsers, suggestionSeed) {
        val pool = mutableListOf<String>()
        pool += dynamicTags.take(8).map { it.first }
        pool += allSounds.take(6).map { it.title }
        pool += allUsers.filter { it.role != "admin" }.take(6).map { "@${it.username}" }
        if (pool.isEmpty()) pool += listOf("#chort", "#fyp", "#viral", "#newhere")
        pool.distinct().shuffled(java.util.Random(suggestionSeed * 31L + 7))
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TokDarkBg)
            .statusBarsPadding()
            .testTag("discover_screen")
    ) {
        // Search Header: back + field + Search action
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = TextPrimary,
                    modifier = Modifier.size(22.dp)
                )
            }
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search songs, creators, videos, #hashtags...", color = TextMuted, fontSize = 13.5.sp) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = TokCyan,
                        modifier = Modifier.size(20.dp)
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotBlank()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Clear",
                                tint = TextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    focusedBorderColor = TokCyan,
                    unfocusedBorderColor = TokBorder,
                    focusedContainerColor = TokDarkElevated,
                    unfocusedContainerColor = TokDarkElevated
                ),
                shape = RoundedCornerShape(24.dp),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { submitSearch() }),
                modifier = Modifier
                    .weight(1f)
                    .height(50.dp)
                    .testTag("search_input")
            )
            Text(
                text = "Search",
                color = TokRed,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clickable { submitSearch() }
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            )
        }

        // Filter chips (All, Videos, Users, Sounds, Hashtags)
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val filters = listOf("All", "Videos", "Users", "Sounds", "Hashtags")
            items(filters) { filter ->
                FilterChip(
                    selected = (selectedFilter == filter),
                    onClick = { selectedFilter = filter },
                    label = { Text(filter, fontSize = 12.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = TokRed,
                        selectedLabelColor = Color.White,
                        containerColor = TokDarkElevated,
                        labelColor = TextSecondary
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        borderColor = TokBorder,
                        selectedBorderColor = TokRed,
                        enabled = true,
                        selected = selectedFilter == filter
                    ),
                    shape = RoundedCornerShape(16.dp)
                )
            }
        }

        // Empty state when search produces no matches
        if (searchQuery.isNotBlank() && !hasAnyResults) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.SearchOff,
                        contentDescription = "No results",
                        tint = TokRed,
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "No results found for \"$searchQuery\"",
                        color = TextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Try searching by creator handle, song title, or #hashtag.",
                        color = TextMuted,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(18.dp))
                    Button(
                        onClick = { searchQuery = "" },
                        colors = ButtonDefaults.buttonColors(containerColor = TokDarkElevated),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.border(1.dp, TokBorder, RoundedCornerShape(12.dp))
                    ) {
                        Text("Reset Search", color = TokCyan, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 60.dp)
            ) {
                // Search history (idle state)
                if (searchQuery.isBlank() && searchHistory.isNotEmpty()) {
                    val visibleHistory = if (historyExpanded) searchHistory else searchHistory.take(4)
                    items(visibleHistory) { past ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { searchQuery = past }
                                .padding(horizontal = 18.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.History,
                                contentDescription = null,
                                tint = TextMuted,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = past,
                                color = TextPrimary,
                                fontSize = 15.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = { removeFromHistory(past) },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Remove",
                                    tint = TextMuted,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                    if (searchHistory.size > 4) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (historyExpanded) "See less" else "See more",
                                    color = TextSecondary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.clickable { historyExpanded = !historyExpanded }
                                )
                            }
                        }
                    }
                }

                // "You may like" suggestions (idle state, from real content)
                if (searchQuery.isBlank()) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 18.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "You may like",
                                color = TextPrimary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.clickable { suggestionSeed++ }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Refresh",
                                    tint = TextSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Refresh",
                                    color = TextSecondary,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                    items(youMayLike.take(8).withIndex().toList()) { (index, suggestion) ->
                        val hot = index < 2
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    searchQuery = suggestion.removePrefix("#").removePrefix("@")
                                    submitSearch()
                                }
                                .padding(horizontal = 18.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(if (hot) TokRed else TextMuted)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = suggestion,
                                color = if (hot) TokRed else TextPrimary,
                                fontSize = 15.sp,
                                fontWeight = if (hot) FontWeight.Bold else FontWeight.Normal,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                // Trending Hashtags section
                if (searchQuery.isBlank() || selectedFilter == "Hashtags" || selectedFilter == "All") {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.TrendingUp,
                                contentDescription = "Trending",
                                tint = TokCyan,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Trending Hashtags",
                                color = TextPrimary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        LazyRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(dynamicTags) { (tag, count) ->
                                Card(
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(containerColor = TokDarkElevated),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable { searchQuery = tag.removePrefix("#") }
                                        .border(1.dp, TokBorder, RoundedCornerShape(12.dp))
                                ) {
                                    Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                                        Text(
                                            text = tag,
                                            color = TokCyan,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = count,
                                            color = TextMuted,
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                    }
                }

                // Sounds & Music Section (Search for songs / أغاني)
                if ((selectedFilter == "All" || selectedFilter == "Sounds") && searchResultsSounds.isNotEmpty()) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.MusicNote,
                                contentDescription = null,
                                tint = AccentGold,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (searchQuery.isBlank()) "Popular Sounds & Music" else "Songs & Sounds (${searchResultsSounds.size})",
                                color = TextPrimary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    items(searchResultsSounds.take(6)) { sound ->
                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = TokDarkSurface),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 4.dp)
                                .border(1.dp, TokBorder, RoundedCornerShape(14.dp))
                                .clickable { onNavigateToSound(sound.title) }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(46.dp)
                                        .clip(CircleShape)
                                        .background(TokDarkElevated)
                                        .border(1.5.dp, TokCyan, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.GraphicEq,
                                        contentDescription = "Sound",
                                        tint = AccentGold,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = sound.title,
                                        color = TextPrimary,
                                        fontSize = 13.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "By @${sound.creator} • ${sound.videoCount} videos",
                                        color = TextMuted,
                                        fontSize = 11.5.sp,
                                        maxLines = 1
                                    )
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                Button(
                                    onClick = { onNavigateToSound(sound.title) },
                                    colors = ButtonDefaults.buttonColors(containerColor = TokRed),
                                    shape = RoundedCornerShape(12.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    Text("Listen", color = Color.White, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    item { Spacer(modifier = Modifier.height(14.dp)) }
                }

                // Creator Spotlight Section
                if ((selectedFilter == "All" || selectedFilter == "Users") && searchResultsUsers.isNotEmpty()) {
                    item {
                        Text(
                            text = if (searchQuery.isBlank()) "Creators" else "Users (${searchResultsUsers.size})",
                            color = TextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                        )

                        LazyRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(searchResultsUsers) { user ->
                                val isFollowing = user.id in followingIds
                                CreatorCard(
                                    user = user,
                                    isFollowing = isFollowing,
                                    onProfileClick = { onNavigateToProfile(user.id) },
                                    onToggleFollow = {
                                        scope.launch { repository.toggleFollow(user.id) }
                                    }
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }

                // Video Grid Section
                if ((selectedFilter == "All" || selectedFilter == "Videos") && searchResultsVideos.isNotEmpty()) {
                    item {
                        Text(
                            text = if (searchQuery.isBlank()) "Featured Videos" else "Video Results (${searchResultsVideos.size})",
                            color = TextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                        )
                    }

                    val chunkedVideos = searchResultsVideos.chunked(2)
                    items(chunkedVideos) { rowVideos ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            rowVideos.forEach { video ->
                                Box(modifier = Modifier.weight(1f)) {
                                    VideoGridCard(video = video, onClick = { onSelectVideo(video) })
                                }
                            }
                            if (rowVideos.size == 1) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CreatorCard(
    user: UserEntity,
    isFollowing: Boolean,
    onProfileClick: () -> Unit,
    onToggleFollow: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = TokDarkSurface),
        modifier = Modifier
            .width(130.dp)
            .border(1.dp, TokBorder, RoundedCornerShape(16.dp))
            .clickable { onProfileClick() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AsyncImage(
                model = user.avatarUrl,
                contentDescription = user.username,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .border(1.5.dp, TokCyan, CircleShape)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = user.displayName,
                color = TextPrimary,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = "@${user.username}",
                color = TextMuted,
                fontSize = 10.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = onToggleFollow,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isFollowing) TokDarkElevated else TokRed
                ),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                modifier = Modifier.height(28.dp)
            ) {
                Text(
                    text = if (isFollowing) "Following" else "Follow",
                    color = if (isFollowing) TextSecondary else Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun VideoGridCard(
    video: VideoEntity,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = TokDarkSurface),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .border(1.dp, TokBorder, RoundedCornerShape(12.dp))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.72f)
        ) {
            if (video.thumbnailUrl.isNotBlank()) {
                AsyncImage(
                    model = video.thumbnailUrl,
                    contentDescription = video.caption,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f))
                        )
                    )
            )

            Row(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Plays",
                    tint = Color.White,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(3.dp))
                Text(
                    text = "${video.viewsCount}",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        Column(modifier = Modifier.padding(8.dp)) {
            Text(
                text = video.caption,
                color = TextPrimary,
                fontSize = 12.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 15.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(
                    model = video.creatorAvatar,
                    contentDescription = video.creatorUsername,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = video.creatorUsername,
                    color = TextMuted,
                    fontSize = 10.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
