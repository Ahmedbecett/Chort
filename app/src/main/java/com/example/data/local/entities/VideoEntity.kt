package com.example.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "videos")
data class VideoEntity(
    @PrimaryKey
    val id: String,
    val creatorId: String,
    val creatorUsername: String,
    val creatorAvatar: String,
    val videoUrl: String,
    val thumbnailUrl: String,
    val caption: String,
    val musicTitle: String = "Original Sound - TokPulse Creator",
    val tags: String = "#fyp,#viral,#pulse",
    val likesCount: Int = 0,
    val commentsCount: Int = 0,
    val sharesCount: Int = 0,
    val viewsCount: Int = 0,
    val isHidden: Boolean = false,
    val isDeleted: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)
