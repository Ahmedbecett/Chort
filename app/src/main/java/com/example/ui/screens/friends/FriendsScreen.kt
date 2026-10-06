package com.example.ui.screens.friends

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
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entities.CommentEntity
import com.example.data.local.entities.VideoEntity
import com.example.data.repository.TokPulseRepository
import com.example.ui.components.CommentBottomSheet
import com.example.ui.components.ReportDialog
import com.example.ui.components.ShareBottomSheet
import com.example.ui.components.StoriesRow
import com.example.ui.screens.feed.VideoFeedItem
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TokBorder
import com.example.ui.theme.TokCyan
import com.example.ui.theme.TokDarkBg
import com.example.ui.theme.TokDarkElevated
import com.example.ui.theme.TokRed
import kotlinx.coroutines.launch

/**
 * Friends tab: stories from people you follow on top, their latest
 * videos below in the same full-screen player as Home.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FriendsScreen(
    repository: TokPulseRepository,
    onNavigateToSearch: () -> Unit,
    onNavigateToProfile: (String) -> Unit,
    onNavigateToCreate: () -> Unit,
    onNavigateToSound: (String) -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    val allVideos by repository.getActiveVideos().collectAsState(initial = emptyList())
    val currentUser by repository.currentUser.collectAsState()
    val likedVideoIds by repository.getUserLikedVideoIds(currentUser?.id ?: "").collectAsState(initial = emptyList())
    val savedVideoIds by repository.savedVideoIds.collectAsState(initial = emptySet())
    val followingIds by repository.getFollowingIds(currentUser?.id ?: "").collectAsState(initial = emptyList())

    var storyFilterUserId by remember { mutableStateOf<String?>(null) }
    var isMuted by remember { mutableStateOf(false) }

    val friendsVideos = remember(allVideos, followingIds, storyFilterUserId) {
        val base = allVideos.filter { it.creatorId in followingIds }
        if (storyFilterUserId != null) base.filter { it.creatorId == storyFilterUserId } else base
    }
    val pagerState = rememberPagerState(pageCount = { friendsVideos.size })

    var activeCommentVideo by remember { mutableStateOf<VideoEntity?>(null) }
    var activeShareVideo by remember { mutableStateOf<VideoEntity?>(null) }
    var activeReportTarget by remember { mutableStateOf<Triple<String, String, String>?>(null) }

    val commentSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val shareSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val activeComments by if (activeCommentVideo != null) {
        repository.getComments(activeCommentVideo!!.id).collectAsState(initial = emptyList())
    } else {
        remember { mutableStateOf(emptyList<CommentEntity>()) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .statusBarsPadding()
            .testTag("friends_screen")
    ) {
        // Header: centered title + search (mirrors reference structure)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(modifier = Modifier.width(48.dp))
            Text(
                text = "Friends",
                color = TextPrimary,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onNavigateToSearch) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search",
                    tint = TextPrimary,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        StoriesRow(
            repository = repository,
            onCreateStory = onNavigateToCreate,
            onStoryClick = { user ->
                storyFilterUserId = if (storyFilterUserId == user.id) null else user.id
            },
            modifier = Modifier.padding(horizontal = 12.dp)
        )

        if (storyFilterUserId != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Showing one creator",
                    color = TokCyan,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Show all",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable { storyFilterUserId = null }
                )
            }
        }

        if (friendsVideos.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 72.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Groups,
                        contentDescription = "No friends videos",
                        tint = TokCyan,
                        modifier = Modifier.size(54.dp)
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "Nothing from friends yet",
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Follow creators you love and their newest videos will play here.",
                        color = TextSecondary,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(18.dp))
                    Button(
                        onClick = onNavigateToSearch,
                        colors = ButtonDefaults.buttonColors(containerColor = TokRed),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Text("Find creators", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        } else {
            Box(modifier = Modifier.weight(1f)) {
                VerticalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize()
                ) { page ->
                    val video = friendsVideos[page]
                    val isCurrent = (pagerState.currentPage == page)

                    LaunchedEffect(isCurrent) {
                        if (isCurrent) repository.recordVideoView(video.id)
                    }

                    VideoFeedItem(
                        video = video,
                        isCurrentPage = isCurrent,
                        isLiked = video.id in likedVideoIds,
                        isSaved = video.id in savedVideoIds,
                        isFollowing = video.creatorId in followingIds,
                        isMuted = isMuted,
                        onToggleMute = { isMuted = !isMuted },
                        onRetry = {
                            scope.launch {
                                repository.refreshFeed()
                                repository.refreshVideoUrl(video.id)
                            }
                        },
                        onSkip = {
                            scope.launch {
                                if (pagerState.currentPage < friendsVideos.size - 1) {
                                    pagerState.animateScrollToPage(pagerState.currentPage + 1)
                                }
                            }
                        },
                        onToggleLike = { scope.launch { repository.toggleLike(video.id) } },
                        onToggleSave = { scope.launch { repository.toggleSave(video.id) } },
                        onToggleFollow = { scope.launch { repository.toggleFollow(video.creatorId) } },
                        onOpenComments = { activeCommentVideo = video },
                        onOpenShare = {
                            activeShareVideo = video
                            scope.launch { repository.recordVideoShare(video.id) }
                        },
                        onOpenProfile = { onNavigateToProfile(video.creatorId) },
                        onOpenSound = { onNavigateToSound(video.musicTitle) },
                        onReport = { activeReportTarget = Triple("video", video.id, video.caption) }
                    )
                }
            }
        }

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
                    scope.launch { repository.addComment(activeCommentVideo!!.id, text) }
                },
                onLikeComment = { cId -> scope.launch { repository.likeComment(cId) } },
                onReportComment = { c ->
                    activeReportTarget = Triple("comment", c.id, c.text)
                }
            )
        }

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
