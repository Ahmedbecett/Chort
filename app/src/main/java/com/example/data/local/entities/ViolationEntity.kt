package com.example.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "violations")
data class ViolationEntity(
    @PrimaryKey
    val id: String,
    val userId: String,
    val username: String,
    val violationType: String,
    val reason: String,
    val relatedContent: String = "",
    val actionTaken: String, // "warning", "temporary_suspension", "permanent_ban"
    val adminUsername: String,
    val createdAt: Long = System.currentTimeMillis()
)
