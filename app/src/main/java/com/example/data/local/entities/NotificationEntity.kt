package com.example.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "notifications")
data class NotificationEntity(
    @PrimaryKey
    val id: String,
    val userId: String,
    val actorId: String,
    val actorUsername: String,
    val actorAvatar: String,
    val type: String, // "like", "comment", "follow", "system"
    val message: String,
    val videoId: String? = null,
    val isRead: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)
