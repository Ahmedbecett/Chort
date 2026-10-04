package com.example.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "likes")
data class LikeEntity(
    @PrimaryKey
    val id: String, // videoId + "_" + userId
    val videoId: String,
    val userId: String,
    val createdAt: Long = System.currentTimeMillis()
)
