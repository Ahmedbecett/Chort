package com.example.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "reports")
data class ReportEntity(
    @PrimaryKey
    val id: String,
    val reporterId: String,
    val reporterUsername: String,
    val targetType: String, // "video", "user", "comment"
    val targetId: String,
    val targetOwnerUsername: String = "",
    val targetSnippet: String = "",
    val reason: String,
    val description: String = "",
    val status: String = "pending", // "pending", "reviewing", "resolved", "rejected"
    val resolutionNotes: String = "",
    val resolvedByAdmin: String = "",
    val createdAt: Long = System.currentTimeMillis()
)
