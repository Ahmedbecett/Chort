package com.example.ui.screens.discover

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Videocam
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
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

/**
 * Normalizes Arabic and English search strings (removes hamza variants, taa marbouta, punctuation)
 * to provide resilient, enterprise-grade search results matching YouTube/TikTok algorithms.
 */
private fun normalizeSearchQuery(text: String): String {
    return text.lowercase()
        .replace("[أإآ]".toRegex(), "ا")
        .replace("ة", "ه")
        .replace("ى", "ي")
        .replace("[\\p{Punct}\\s]+".toRegex(), " ")
        .trim()
}

@Composable
fun DiscoverScreen(
    repository: TokPulseRepository,
    onNavigateToProfile: (String) -> Unit,
    onSelectVideo: (VideoEntity) -> Unit
) {
    val scope = rememberCoroutineScope()
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("All") } // "All", "Videos", "Users", "Hashtags"

    val allVideos by repository.getActiveVideos().collectAsState(initial = emptyList())
    val allUsers by repository.getAllUsersAdmin().collectAsState(initial = emptyList())
    val currentUser by repository.currentUser.collectAsState()
    val followingIds by repository.getFollowingIds(currentUser?.id ?: "").collectAsState(initial = emptyList())

    val normalizedQuery = remember(searchQuery) { normalizeSearchQuery(searchQuery) }

    // Dynamic Video Search matching caption, hashtags, creator, or music title
    val searchResultsVideos = remember(normalizedQuery, allVideos) {
        if (normalizedQuery.isBlank()) {
            allVideos
        } else {
            allVideos.filter { video ->
                val captionNorm = normalizeSearchQuery(video.caption)
                val tagsNorm = normalizeSearchQuery(video.tags)
                val creatorNorm = normalizeSearchQuery(video.creatorUsername)
                val musicNorm = normalizeSearchQuery(video.musicTitle)

                captionNorm.contains(normalizedQuery) ||
                tagsNorm.contains(normalizedQuery) ||
                creatorNorm.contains(normalizedQuery) ||
                musicNorm.contains(normalizedQuery)
            }
        }
    }

    // Dynamic User Search matching username, display name, or bio
    val searchResultsUsers = remember(normalizedQuery, allUsers) {
        if (normalizedQuery.isBlank()) {
            allUsers.filter { it.role != "admin" }
        } else {
            allUsers.filter { user ->
                val usernameNorm = normalizeSearchQuery(user.username)
                val displayNameNorm = normalizeSearchQuery(user.displayName)
                val bioNorm = normalizeSearchQuery(user.bio)

                usernameNorm.contains(normalizedQuery) ||
                displayNameNorm.contains(normalizedQuery) ||
                bioNorm.contains(normalizedQuery)
            }
        }
    }

    val trendingTags = listOf(
        Pair("#أغاني", "2.8M views"),
        Pair("#موسيقى", "1.9M views"),
        Pair("#طرب", "940K views"),
        Pair("#رقص", "3.4M views"),
        Pair("#طبخ", "1.8M views"),
        Pair("#تقنية", "820K views"),
        Pair("#رياضة", "650K views"),
        Pair("#سفر", "1.2M views"),
        Pair("#كوميديا", "4.1M views"),
        Pair("#viral", "5.2B views"),
        Pair("#fyp", "8.9B views")
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TokDarkBg)
            .statusBarsPadding()
            .testTag("discover_screen")
    ) {
        // Search Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search videos, users, #أغاني, #رقص...", color = TextMuted, fontSize = 13.5.sp) },
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
                modifier = Modifier
                    .weight(1f)
                    .height(50.dp)
                    .testTag("search_input")
            )
        }

        // Filter chips: All, Videos, Users, Hashtags
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val filters = listOf("All", "Videos", "Users", "Hashtags")
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

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 60.dp)
        ) {
            // Trending Hashtags carousel
            if (selectedFilter == "All" || selectedFilter == "Hashtags") {
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
                            text = if (searchQuery.isBlank()) "Trending Hashtags" else "Hashtags matching \"$searchQuery\"",
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
                        val matchingTags = if (normalizedQuery.isBlank()) {
                            trendingTags
                        } else {
                            trendingTags.filter { normalizeSearchQuery(it.first).contains(normalizedQuery) }
                                .ifEmpty { listOf(Pair("#$searchQuery", "Live tag")) }
                        }

                        items(matchingTags) { (tag, count) ->
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

            // Creator Spotlight Section (Horizontal in All, Full vertical list in Users)
            if (selectedFilter == "All" && searchResultsUsers.isNotEmpty()) {
                item {
                    Text(
                        text = if (searchQuery.isBlank()) "Popular Creators" else "Creators (${searchResultsUsers.size})",
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
            } else if (selectedFilter == "Users") {
                item {
                    Text(
                        text = "Creators (${searchResultsUsers.size})",
                        color = TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                    )
                }

                if (searchResultsUsers.isEmpty()) {
                    item {
                        EmptySearchState(query = searchQuery, type = "creators")
                    }
                } else {
                    items(searchResultsUsers) { user ->
                        val isFollowing = user.id in followingIds
                        UserListRow(
                            user = user,
                            isFollowing = isFollowing,
                            onProfileClick = { onNavigateToProfile(user.id) },
                            onToggleFollow = {
                                scope.launch { repository.toggleFollow(user.id) }
                            }
                        )
                    }
                }
            }

            // Video Grid Section (Visible in All, Videos, or Hashtags)
            if (selectedFilter == "All" || selectedFilter == "Videos" || selectedFilter == "Hashtags") {
                item {
                    Text(
                        text = if (searchQuery.isBlank()) "Featured Clips" else "Videos Found (${searchResultsVideos.size})",
                        color = TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                    )
                }

                if (searchResultsVideos.isEmpty()) {
                    item {
                        EmptySearchState(query = searchQuery, type = "videos")
                    }
                } else {
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
private fun EmptySearchState(query: String, type: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 40.dp, horizontal = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = "Empty Search",
                tint = TextMuted,
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = if (query.isNotBlank()) "No $type found for \"$query\"" else "No $type found",
                color = TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Try searching for #أغاني, #رقص, #طبخ, or #تقنية",
                color = TextMuted,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun UserListRow(
    user: UserEntity,
    isFollowing: Boolean,
    onProfileClick: () -> Unit,
    onToggleFollow: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = TokDarkSurface),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clickable { onProfileClick() }
            .border(1.dp, TokBorder, RoundedCornerShape(12.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = user.avatarUrl,
                contentDescription = user.username,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .border(1.5.dp, TokCyan, CircleShape)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = user.displayName,
                    color = TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "@${user.username}",
                    color = TextMuted,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (user.bio.isNotBlank()) {
                    Text(
                        text = user.bio,
                        color = TextSecondary,
                        fontSize = 11.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = onToggleFollow,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isFollowing) TokDarkElevated else TokRed
                ),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                modifier = Modifier.height(32.dp)
            ) {
                Text(
                    text = if (isFollowing) "Following" else "Follow",
                    color = if (isFollowing) TextSecondary else Color.White,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold
                )
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
            AsyncImage(
                model = video.thumbnailUrl,
                contentDescription = video.caption,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            // Gradient shadow
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f))
                        )
                    )
            )
            // View count badge
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
        // Caption snippet
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
