package com.example.ui.screens.viewer

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.data.local.entities.CommentEntity
import com.example.data.local.entities.VideoEntity
import com.example.data.repository.ZevoraRepository
import com.example.ui.components.CommentBottomSheet
import com.example.ui.components.ReportDialog
import com.example.ui.components.ShareBottomSheet
import com.example.ui.screens.feed.VideoFeedItem
import com.example.util.AppPrefs
import kotlinx.coroutines.launch

/**
 * Rivo 3.1.0 — shared full-screen video viewer used by profile grids,
 * activity center and studio: the exact feed experience (like/save/follow,
 * comments, share, remix, report) for one video.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoViewerScreen(
    repository: ZevoraRepository,
    video: VideoEntity,
    onViewProfile: (String) -> Unit,
    onRemixVideo: (Uri) -> Unit,
    onClose: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val currentUser by repository.currentUser.collectAsState()
    val allVideos by repository.getActiveVideos().collectAsState(initial = emptyList())
    val fresh = remember(allVideos, video) { allVideos.firstOrNull { it.id == video.id } ?: video }
    val likedIds by repository.getUserLikedVideoIds(currentUser?.id ?: "").collectAsState(initial = emptyList())
    val savedIds by repository.savedVideoIds.collectAsState(initial = emptySet())
    val followingIds by repository.getFollowingIds(currentUser?.id ?: "").collectAsState(initial = emptyList())
    val comments by repository.getComments(fresh.id).collectAsState(initial = emptyList())

    var showComments by remember { mutableStateOf(false) }
    var showShare by remember { mutableStateOf(false) }
    var reportTarget by remember { mutableStateOf<Triple<String, String, String>?>(null) }
    val commentSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val shareSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        VideoFeedItem(
            video = fresh,
            isCurrentPage = true,
            isLiked = fresh.id in likedIds,
            isSaved = fresh.id in savedIds,
            isFollowing = fresh.creatorId in followingIds,
            isMuted = AppPrefs.feedMuted.value || AppPrefs.dataSaver.value,
            onToggleMute = { AppPrefs.setFeedMuted(!AppPrefs.feedMuted.value) },
            onRetry = { scope.launch { repository.refreshVideoUrl(fresh.id) } },
            onSkip = onClose,
            onToggleLike = { scope.launch { repository.toggleLike(fresh.id) } },
            onToggleSave = { scope.launch { repository.toggleSave(fresh.id) } },
            onToggleFollow = { scope.launch { repository.toggleFollow(fresh.creatorId) } },
            onOpenComments = { showComments = true },
            onOpenShare = {
                showShare = true
                scope.launch { repository.recordVideoShare(fresh.id) }
            },
            onOpenProfile = { onViewProfile(fresh.creatorId) },
            onOpenSound = {},
            onReport = { reportTarget = Triple("video", fresh.id, fresh.caption) },
            autoPlay = AppPrefs.autoplay.value && !AppPrefs.dataSaver.value
        )
        IconButton(
            onClick = onClose,
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(start = 4.dp)
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Close", tint = Color.White)
        }

        if (showComments) {
            CommentBottomSheet(
                sheetState = commentSheetState,
                comments = comments,
                onDismiss = {
                    scope.launch {
                        commentSheetState.hide()
                        showComments = false
                    }
                },
                onAddComment = { text ->
                    scope.launch { repository.addComment(fresh.id, text) }
                },
                onLikeComment = { cId -> scope.launch { repository.likeComment(cId) } },
                onReportComment = { c: CommentEntity ->
                    reportTarget = Triple("comment", c.id, c.text)
                },
                commentsAllowed = fresh.creatorId != currentUser?.id ||
                    AppPrefs.allowComments.value != "none"
            )
        }
        if (showShare) {
            ShareBottomSheet(
                sheetState = shareSheetState,
                video = fresh,
                onDismiss = {
                    scope.launch {
                        shareSheetState.hide()
                        showShare = false
                    }
                },
                onReport = { reportTarget = Triple("video", fresh.id, fresh.caption) },
                onRemixVideo = onRemixVideo
            )
        }
        reportTarget?.let { (type, id, snippet) ->
            ReportDialog(
                targetType = type,
                targetSnippet = snippet,
                onDismiss = { reportTarget = null },
                onSubmitReport = { reason, desc ->
                    scope.launch {
                        repository.submitReport(
                            targetType = type,
                            targetId = id,
                            targetOwnerUsername = fresh.creatorUsername,
                            targetSnippet = snippet,
                            reason = reason,
                            description = desc
                        )
                    }
                }
            )
        }
    }
}
