package com.example.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "privacy_requests")
data class PrivacyRequestEntity(
    @PrimaryKey
    val id: String,
    val userId: String,
    val username: String,
    val email: String,
    val requestType: String = "account_deletion", // "account_deletion", "data_export"
    val reason: String = "",
    val status: String = "pending", // "pending", "approved", "processed", "rejected"
    val processedByAdmin: String = "",
    val createdAt: Long = System.currentTimeMillis()
)
