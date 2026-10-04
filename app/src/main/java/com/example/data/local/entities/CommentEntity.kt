package com.example.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "comments")
data class CommentEntity(
    @PrimaryKey
    val id: String,
    val videoId: String,
    val userId: String,
    val username: String,
    val userAvatar: String,
    val text: String,
    val likesCount: Int = 0,
    val isReported: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)
