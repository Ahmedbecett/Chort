package com.example.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "follows")
data class FollowEntity(
    @PrimaryKey
    val id: String, // followerId + "_" + followingId
    val followerId: String,
    val followingId: String,
    val createdAt: Long = System.currentTimeMillis()
)
