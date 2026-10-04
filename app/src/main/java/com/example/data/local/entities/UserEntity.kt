package com.example.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey
    val id: String,
    val username: String,
    val displayName: String,
    val email: String,
    val passwordHash: String,
    val avatarUrl: String,
    val bio: String,
    val followersCount: Int = 0,
    val followingCount: Int = 0,
    val totalLikes: Int = 0,
    val role: String = "user", // "user" or "admin"
    val status: String = "active", // "active", "suspended", "banned"
    val suspensionUntil: Long? = null,
    val createdAt: Long = System.currentTimeMillis()
)
