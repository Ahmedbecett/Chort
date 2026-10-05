package com.example.ui.screens.feed

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.material.icons.automirrored.filled.Comment
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Comment
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.LocalMall
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.local.entities.CommentEntity
import com.example.data.local.entities.VideoEntity
import com.example.data.repository.TokPulseRepository
import com.example.ui.components.BurstHeart
import com.example.ui.components.CommentBottomSheet
import com.example.ui.components.ReportDialog
import com.example.ui.components.ShareBottomSheet
import com.example.ui.components.VideoPlayerView
import com.example.ui.theme.AccentGold
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TokCyan
import com.example.ui.theme.TokRed
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedScreen(
    repository: TokPulseRepository,
    onNavigateToSearch: () -> Unit,
    onNavigateToProfile: (String) -> Unit,
    onNavigateToCreate: () -> Unit = {},
    onNavigateToLive: () -> Unit = {},
    onNavigateToSound: (String) -> Unit = {},
    onNavigateToTracking: () -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    val allVideos by repository.getActiveVideos().collectAsState(initial = emptyList())
    val currentUser by repository.currentUser.collectAsState()
    val likedVideoIds by repository.getUserLikedVideoIds(currentUser?.id ?: "").collectAsState(initial = emptyList())
    val savedVideoIds by repository.savedVideoIds.collectAsState(initial = emptySet())
    val followingIds by repository.getFollowingIds(currentUser?.id ?: "").collectAsState(initial = emptyList())

    var selectedTab by remember { mutableIntStateOf(1) } // 0 = Following, 1 = For You

    val displayedVideos = remember(allVideos, selectedTab, followingIds) {
        if (selectedTab == 0) {
            val followed = allVideos.filter { it.creatorId in followingIds }
            if (followed.isEmpty()) allVideos else followed
        } else {
            allVideos
        }
    }

    val pagerState = rememberPagerState(pageCount = { displayedVideos.size })

    // Active bottom sheet & report dialog state
    var activeCommentVideo by remember { mutableStateOf<VideoEntity?>(null) }
    var activeShareVideo by remember { mutableStateOf<VideoEntity?>(null) }
    var activeReportTarget by remember { mutableStateOf<Triple<String, String, String>?>(null) } // (type, id, snippet)

    val commentSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val shareSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Comments for active video
    val activeComments by if (activeCommentVideo != null) {
        repository.getComments(activeCommentVideo!!.id).collectAsState(initial = emptyList())
    } else {
        remember { mutableStateOf(emptyList<CommentEntity>()) }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .testTag("feed_screen")
    ) {
        if (displayedVideos.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Videocam,
                        contentDescription = "No Videos",
                        tint = TokCyan,
                        modifier = Modifier.size(54.dp)
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = if (selectedTab == 0) "No followed creators with videos yet" else "No videos published yet",
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Be the first to create and publish a video on Chort!",
                        color = TextSecondary,
                        fontSize = 13.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(18.dp))
                    androidx.compose.material3.Button(
                        onClick = onNavigateToCreate,
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = TokRed),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Create Video", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        } else {
            VerticalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                val video = displayedVideos[page]
                val isCurrent = (pagerState.currentPage == page)
                val isLiked = video.id in likedVideoIds
                val isSaved = video.id in savedVideoIds
                val isFollowing = video.creatorId in followingIds

                // Record view when swiped to page
                LaunchedEffect(isCurrent) {
                    if (isCurrent) {
                        repository.recordVideoView(video.id)
                    }
                }

                VideoFeedItem(
                    video = video,
                    isCurrentPage = isCurrent,
                    isLiked = isLiked,
                    isSaved = isSaved,
                    isFollowing = isFollowing,
                    onToggleLike = {
                        scope.launch { repository.toggleLike(video.id) }
                    },
                    onToggleSave = {
                        scope.launch { repository.toggleSave(video.id) }
                    },
                    onToggleFollow = {
                        scope.launch { repository.toggleFollow(video.creatorId) }
                    },
                    onOpenComments = {
                        activeCommentVideo = video
                    },
                    onOpenShare = {
                        activeShareVideo = video
                        scope.launch { repository.recordVideoShare(video.id) }
                    },
                    onOpenProfile = {
                        onNavigateToProfile(video.creatorId)
                    },
                    onOpenSound = {
                        onNavigateToSound(video.musicTitle)
                    },
                    onReport = {
                        activeReportTarget = Triple("video", video.id, video.caption)
                    }
                )
            }
        }

        // Top Navigation Bar (LIVE | Following | For You + Tracking Center + Search)
        TopFeedBar(
            selectedTab = selectedTab,
            onSelectTab = { selectedTab = it },
            onLiveClick = onNavigateToLive,
            onTrackingClick = onNavigateToTracking,
            onSearchClick = onNavigateToSearch,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
        )

        // Comment Sheet
        if (activeCommentVideo != null) {
            CommentBottomSheet(
                sheetState = commentSheetState,
                comments = activeComments,
                onDismiss = {
                    scope.launch {
                        commentSheetState.hide()
                        activeCommentVideo = null
                    }
                },
                onAddComment = { text ->
                    scope.launch {
                        repository.addComment(activeCommentVideo!!.id, text)
                    }
                },
                onLikeComment = { cId ->
                    scope.launch { repository.likeComment(cId) }
                },
                onReportComment = { c ->
                    activeReportTarget = Triple("comment", c.id, c.text)
                }
            )
        }

        // Share Sheet
        if (activeShareVideo != null) {
            ShareBottomSheet(
                sheetState = shareSheetState,
                video = activeShareVideo!!,
                onDismiss = {
                    scope.launch {
                        shareSheetState.hide()
                        activeShareVideo = null
                    }
                },
                onReport = {
                    activeReportTarget = Triple("video", activeShareVideo!!.id, activeShareVideo!!.caption)
                }
            )
        }

        // Report Dialog
        if (activeReportTarget != null) {
            val (targetType, targetId, snippet) = activeReportTarget!!
            ReportDialog(
                targetType = targetType,
                targetSnippet = snippet,
                onDismiss = { activeReportTarget = null },
                onSubmitReport = { reason, desc ->
                    scope.launch {
                        repository.submitReport(
                            targetType = targetType,
                            targetId = targetId,
                            targetOwnerUsername = "",
                            targetSnippet = snippet,
                            reason = reason,
                            description = desc
                        )
                        activeReportTarget = null
                    }
                }
            )
        }
    }
}

@Composable
private fun TopFeedBar(
    selectedTab: Int,
    onSelectTab: (Int) -> Unit,
    onLiveClick: () -> Unit,
    onTrackingClick: () -> Unit,
    onSearchClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // LIVE Button with sleek badge
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(Color.Black.copy(alpha = 0.45f))
                .clickable { onLiveClick() }
                .padding(horizontal = 9.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Videocam,
                contentDescription = "LIVE",
                tint = TokRed,
                modifier = Modifier.size(15.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "LIVE",
                color = Color.White,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.ExtraBold
            )
        }

        // Following | For You Tabs
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onSelectTab(0) }
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "Following",
                    color = if (selectedTab == 0) Color.White else Color.White.copy(alpha = 0.65f),
                    fontSize = 16.5.sp,
                    fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal
                )
                if (selectedTab == 0) {
                    Box(
                        modifier = Modifier
                            .padding(top = 3.dp)
                            .width(22.dp)
                            .height(2.5.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(TokRed)
                    )
                } else {
                    Spacer(modifier = Modifier.height(5.5.dp))
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onSelectTab(1) }
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "For You",
                    color = if (selectedTab == 1) Color.White else Color.White.copy(alpha = 0.65f),
                    fontSize = 16.5.sp,
                    fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal
                )
                if (selectedTab == 1) {
                    Box(
                        modifier = Modifier
                            .padding(top = 3.dp)
                            .width(22.dp)
                            .height(2.5.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(TokCyan)
                    )
                } else {
                    Spacer(modifier = Modifier.height(5.5.dp))
                }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            // Dedicated External Tracking Portal Button
            IconButton(
                onClick = onTrackingClick,
                modifier = Modifier
                    .size(36.dp)
                    .testTag("feed_tracking_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = "Tracking Center",
                    tint = TokCyan,
                    modifier = Modifier.size(19.dp)
                )
            }

            IconButton(
                onClick = onSearchClick,
                modifier = Modifier
                    .size(36.dp)
                    .testTag("feed_search_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search",
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

@Composable
private fun VideoFeedItem(
    video: VideoEntity,
    isCurrentPage: Boolean,
    isLiked: Boolean,
    isSaved: Boolean,
    isFollowing: Boolean,
    onToggleLike: () -> Unit,
    onToggleSave: () -> Unit,
    onToggleFollow: () -> Unit,
    onOpenComments: () -> Unit,
    onOpenShare: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenSound: () -> Unit,
    onReport: () -> Unit
) {
    var heartTrigger by remember { mutableLongStateOf(0L) }
    var isCaptionExpanded by remember { mutableStateOf(false) }

    val infiniteTransition = rememberInfiniteTransition(label = "disc_spin")
    val discRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    // Compute navigation bar insets to guarantee caption and action rail never collide with bottom navigation
    val navBarBottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val bottomSafeMargin = 58.dp + navBarBottomInset

    val isLongCaption = remember(video.caption) { video.caption.length > 55 || video.caption.contains("\n") }
    val displayMusicTitle = remember(video.musicTitle, video.creatorUsername) {
        val raw = video.musicTitle.trim()
        if (raw.isBlank() || raw.contains("Chort", ignoreCase = true) || raw.contains("TokPulse", ignoreCase = true) || raw.contains("Original", ignoreCase = true)) {
            "Original sound - @${video.creatorUsername}"
        } else {
            raw
        }
    }
    val formattedTags = remember(video.tags) {
        video.tags.split(",")
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .joinToString(" ") { if (it.startsWith("#")) it else "#$it" }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Video View Player running true full-screen
        VideoPlayerView(
            videoUrl = video.videoUrl,
            thumbnailUrl = video.thumbnailUrl,
            isCurrentPage = isCurrentPage,
            onDoubleTap = {
                heartTrigger = System.currentTimeMillis()
                if (!isLiked) {
                    onToggleLike()
                }
            }
        )

        // Top gradient overlay for status bar & top tabs contrast
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(110.dp)
                .align(Alignment.TopCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.5f),
                            Color.Transparent
                        )
                    )
                )
        )

        // Bottom gradient overlay for captions and bottom nav contrast
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(260.dp)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.35f),
                            Color.Black.copy(alpha = 0.78f)
                        )
                    )
                )
        )

        // Flying heart animation on double-tap
        BurstHeart(
            trigger = heartTrigger,
            onComplete = { heartTrigger = 0L }
        )

        // Bottom Left Info Overlay (Creator handle, expandable caption, hashtags, audio title)
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 14.dp, end = 76.dp, bottom = bottomSafeMargin)
        ) {
            // Creator Handle with verified check or Pexels attribution badge
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onOpenProfile() }
                    .padding(vertical = 2.dp)
            ) {
                Text(
                    text = "@${video.creatorUsername}",
                    color = Color.White,
                    fontSize = 15.5.sp,
                    fontWeight = FontWeight.Bold
                )
                if (video.isExternal || video.source.equals("pexels", true) || video.provider.equals("pexels", true)) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFF05A081).copy(alpha = 0.9f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "Pexels Licensed",
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Default.Verified,
                        contentDescription = "Verified",
                        tint = TokCyan,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Caption text with Expand / Collapse toggle
            Column {
                Text(
                    text = video.caption,
                    color = Color.White,
                    fontSize = 13.5.sp,
                    lineHeight = 18.sp,
                    maxLines = if (isCaptionExpanded) 12 else 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (isLongCaption) {
                    Text(
                        text = if (isCaptionExpanded) "less" else "...more",
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clickable { isCaptionExpanded = !isCaptionExpanded }
                            .padding(vertical = 2.dp)
                    )
                }
            }

            // Hashtags / topics
            if (formattedTags.isNotBlank()) {
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = formattedTags,
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Audio / Sound Bar (Original sound or real sound title)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Black.copy(alpha = 0.42f))
                    .clickable { onOpenSound() }
                    .padding(horizontal = 8.dp, vertical = 3.5.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.MusicNote,
                    contentDescription = "Sound",
                    tint = Color.White,
                    modifier = Modifier.size(13.dp)
                )
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = displayMusicTitle,
                    color = Color.White,
                    fontSize = 11.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Right Action Rail (Avatar, Like, Comment, Save/Bookmark, Share, Sound Disc)
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 10.dp, bottom = bottomSafeMargin),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(13.dp)
        ) {
            // Creator Avatar with Follow '+' button
            Box(
                contentAlignment = Alignment.BottomCenter,
                modifier = Modifier.padding(bottom = 4.dp)
            ) {
                AsyncImage(
                    model = video.creatorAvatar,
                    contentDescription = video.creatorUsername,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .border(1.5.dp, Color.White, CircleShape)
                        .clickable { onOpenProfile() }
                )

                if (!isFollowing && !video.isExternal && !video.source.equals("pexels", true)) {
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .align(Alignment.BottomCenter)
                            .clip(CircleShape)
                            .background(TokRed)
                            .clickable { onToggleFollow() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Follow",
                            tint = Color.White,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
            }

            // Like Action (Heart)
            ActionRailItem(
                icon = if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                label = formatCount(video.likesCount + if (isLiked) 1 else 0),
                tint = if (isLiked) TokRed else Color.White,
                onClick = onToggleLike,
                testTag = "like_button"
            )

            // Comments Action (Bubble)
            ActionRailItem(
                icon = Icons.AutoMirrored.Filled.Comment,
                label = formatCount(video.commentsCount),
                tint = Color.White,
                onClick = onOpenComments,
                testTag = "comment_button"
            )

            // Save / Bookmark Action
            ActionRailItem(
                icon = if (isSaved) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                label = "Save",
                tint = if (isSaved) AccentGold else Color.White,
                onClick = onToggleSave,
                testTag = "save_button"
            )

            // Share Action
            ActionRailItem(
                icon = Icons.Default.Share,
                label = formatCount(video.sharesCount),
                tint = Color.White,
                onClick = onOpenShare,
                testTag = "share_button"
            )

            // Rotating Vinyl Album Disc
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(Color.DarkGray)
                    .border(1.5.dp, Color.White.copy(alpha = 0.5f), CircleShape)
                    .rotate(discRotation)
                    .clickable { onOpenSound() },
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = video.creatorAvatar,
                    contentDescription = "Sound Disc",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                )
            }
        }
    }
}

@Composable
private fun ActionRailItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: Color,
    onClick: () -> Unit,
    testTag: String
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(onClick = onClick)
            .testTag(testTag)
            .padding(2.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(28.dp)
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

private fun formatCount(count: Int): String {
    return when {
        count >= 1_000_000 -> String.format("%.1fM", count / 1_000_000.0)
        count >= 1_000 -> String.format("%.1fK", count / 1_000.0)
        else -> count.toString()
    }
}
