package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.local.entities.UserEntity
import com.example.data.repository.TokPulseRepository
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TokCyan
import com.example.ui.theme.TokDarkElevated
import com.example.ui.theme.TokRed

/**
 * Horizontal stories strip: a Create tile plus the people you follow,
 * each with a live gradient ring. Shared by Friends and Inbox.
 */
@Composable
fun StoriesRow(
    repository: TokPulseRepository,
    onCreateStory: () -> Unit,
    onStoryClick: (UserEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    val currentUser by repository.currentUser.collectAsState()
    val followingIds by repository.getFollowingIds(currentUser?.id ?: "").collectAsState(initial = emptyList())
    val allUsers by repository.getAllUsersAdmin().collectAsState(initial = emptyList())

    val stories = remember(allUsers, followingIds, currentUser) {
        allUsers.filter { it.id in followingIds && it.id != currentUser?.id }.take(20)
    }

    LazyRow(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.width(68.dp)
            ) {
                Box(contentAlignment = Alignment.BottomEnd) {
                    Box(
                        modifier = Modifier
                            .size(62.dp)
                            .clip(CircleShape)
                            .background(TokDarkElevated)
                            .border(1.5.dp, TextSecondary.copy(alpha = 0.4f), CircleShape)
                            .clickable(onClick = onCreateStory),
                        contentAlignment = Alignment.Center
                    ) {
                        if (currentUser?.avatarUrl.isNullOrBlank()) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Create",
                                tint = TextPrimary,
                                modifier = Modifier.size(26.dp)
                            )
                        } else {
                            AsyncImage(
                                model = currentUser?.avatarUrl,
                                contentDescription = "Create",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(62.dp)
                                    .clip(CircleShape)
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(TokCyan)
                            .border(2.dp, Color.Black, CircleShape)
                            .clickable(onClick = onCreateStory),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            tint = Color.Black,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Create",
                    color = TextPrimary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    textAlign = TextAlign.Center
                )
            }
        }
        items(stories, key = { it.id }) { user ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.width(68.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(Brush.linearGradient(listOf(TokCyan, TokRed)))
                        .padding(2.5.dp)
                        .clip(CircleShape)
                        .background(Color.Black)
                        .padding(2.dp)
                        .clip(CircleShape)
                        .clickable { onStoryClick(user) },
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = user.avatarUrl,
                        contentDescription = user.username,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(58.dp)
                            .clip(CircleShape)
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = user.username,
                    color = TextPrimary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
