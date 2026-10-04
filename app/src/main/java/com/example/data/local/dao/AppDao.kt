package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.local.entities.CommentEntity
import com.example.data.local.entities.FollowEntity
import com.example.data.local.entities.LikeEntity
import com.example.data.local.entities.NotificationEntity
import com.example.data.local.entities.PrivacyRequestEntity
import com.example.data.local.entities.ReportEntity
import com.example.data.local.entities.UserEntity
import com.example.data.local.entities.VideoEntity
import com.example.data.local.entities.ViolationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AppDao {

    // --- USERS ---
    @Query("SELECT * FROM users WHERE id = :id")
    fun getUserById(id: String): Flow<UserEntity?>

    @Query("SELECT * FROM users WHERE id = :id")
    suspend fun getUserByIdSync(id: String): UserEntity?

    @Query("SELECT * FROM users WHERE LOWER(email) = LOWER(:email) LIMIT 1")
    suspend fun getUserByEmail(email: String): UserEntity?

    @Query("SELECT * FROM users WHERE LOWER(username) = LOWER(:username) LIMIT 1")
    suspend fun getUserByUsername(username: String): UserEntity?

    @Query("SELECT * FROM users ORDER BY createdAt DESC")
    fun getAllUsers(): Flow<List<UserEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: UserEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUsers(users: List<UserEntity>)

    @Update
    suspend fun updateUser(user: UserEntity)

    @Query("DELETE FROM users WHERE id = :userId")
    suspend fun deleteUser(userId: String)

    @Query("UPDATE users SET status = :status, suspensionUntil = :suspensionUntil WHERE id = :userId")
    suspend fun updateUserStatus(userId: String, status: String, suspensionUntil: Long? = null)

    @Query("SELECT * FROM users WHERE username LIKE '%' || :query || '%' OR displayName LIKE '%' || :query || '%'")
    fun searchUsers(query: String): Flow<List<UserEntity>>

    // --- VIDEOS ---
    @Query("SELECT * FROM videos WHERE isHidden = 0 AND isDeleted = 0 ORDER BY createdAt DESC")
    fun getAllActiveVideos(): Flow<List<VideoEntity>>

    @Query("SELECT * FROM videos ORDER BY createdAt DESC")
    fun getAllVideosAdmin(): Flow<List<VideoEntity>>

    @Query("SELECT * FROM videos WHERE creatorId = :creatorId AND isDeleted = 0 ORDER BY createdAt DESC")
    fun getVideosByCreator(creatorId: String): Flow<List<VideoEntity>>

    @Query("SELECT * FROM videos WHERE id = :id")
    suspend fun getVideoById(id: String): VideoEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVideo(video: VideoEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVideos(videos: List<VideoEntity>)

    @Update
    suspend fun updateVideo(video: VideoEntity)

    @Query("UPDATE videos SET isHidden = :isHidden WHERE id = :id")
    suspend fun setVideoHidden(id: String, isHidden: Boolean)

    @Query("UPDATE videos SET isDeleted = :isDeleted WHERE id = :id")
    suspend fun setVideoDeleted(id: String, isDeleted: Boolean)

    @Query("UPDATE videos SET likesCount = MAX(0, likesCount + :delta) WHERE id = :id")
    suspend fun updateLikesCount(id: String, delta: Int)

    @Query("UPDATE videos SET viewsCount = viewsCount + 1 WHERE id = :id")
    suspend fun incrementViews(id: String)

    @Query("UPDATE videos SET commentsCount = MAX(0, commentsCount + :delta) WHERE id = :id")
    suspend fun updateCommentsCount(id: String, delta: Int)

    @Query("UPDATE videos SET sharesCount = sharesCount + 1 WHERE id = :id")
    suspend fun incrementShares(id: String)

    @Query("SELECT * FROM videos WHERE (caption LIKE '%' || :query || '%' OR tags LIKE '%' || :query || '%' OR creatorUsername LIKE '%' || :query || '%') AND isHidden = 0 AND isDeleted = 0")
    fun searchVideos(query: String): Flow<List<VideoEntity>>

    // --- COMMENTS ---
    @Query("SELECT * FROM comments WHERE videoId = :videoId ORDER BY createdAt DESC")
    fun getCommentsForVideo(videoId: String): Flow<List<CommentEntity>>

    @Query("SELECT * FROM comments ORDER BY createdAt DESC")
    fun getAllCommentsAdmin(): Flow<List<CommentEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertComment(comment: CommentEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertComments(comments: List<CommentEntity>)

    @Query("DELETE FROM comments WHERE id = :id")
    suspend fun deleteComment(id: String)

    @Query("UPDATE comments SET likesCount = likesCount + 1 WHERE id = :id")
    suspend fun incrementCommentLikes(id: String)

    // --- LIKES ---
    @Query("SELECT COUNT(*) FROM likes WHERE videoId = :videoId AND userId = :userId")
    suspend fun countLike(videoId: String, userId: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLike(like: LikeEntity)

    @Query("DELETE FROM likes WHERE videoId = :videoId AND userId = :userId")
    suspend fun deleteLike(videoId: String, userId: String)

    @Query("SELECT videoId FROM likes WHERE userId = :userId")
    fun getUserLikedVideoIds(userId: String): Flow<List<String>>

    // --- FOLLOWS ---
    @Query("SELECT COUNT(*) FROM follows WHERE followerId = :followerId AND followingId = :followingId")
    suspend fun countFollow(followerId: String, followingId: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFollow(follow: FollowEntity)

    @Query("DELETE FROM follows WHERE followerId = :followerId AND followingId = :followingId")
    suspend fun deleteFollow(followerId: String, followingId: String)

    @Query("SELECT followingId FROM follows WHERE followerId = :followerId")
    fun getFollowingIds(followerId: String): Flow<List<String>>

    // --- NOTIFICATIONS ---
    @Query("SELECT * FROM notifications WHERE userId = :userId ORDER BY createdAt DESC")
    fun getNotificationsForUser(userId: String): Flow<List<NotificationEntity>>

    @Query("SELECT COUNT(*) FROM notifications WHERE userId = :userId AND isRead = 0")
    fun getUnreadNotificationCount(userId: String): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotification(notification: NotificationEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotifications(notifications: List<NotificationEntity>)

    @Query("UPDATE notifications SET isRead = 1 WHERE userId = :userId")
    suspend fun markNotificationsAsRead(userId: String)

    // --- REPORTS ---
    @Query("SELECT * FROM reports ORDER BY createdAt DESC")
    fun getAllReports(): Flow<List<ReportEntity>>

    @Query("SELECT * FROM reports WHERE status = :status ORDER BY createdAt DESC")
    fun getReportsByStatus(status: String): Flow<List<ReportEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReport(report: ReportEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReports(reports: List<ReportEntity>)

    @Query("UPDATE reports SET status = :status, resolutionNotes = :notes, resolvedByAdmin = :adminUsername WHERE id = :id")
    suspend fun updateReportStatus(id: String, status: String, notes: String, adminUsername: String)

    // --- VIOLATIONS ---
    @Query("SELECT * FROM violations ORDER BY createdAt DESC")
    fun getAllViolations(): Flow<List<ViolationEntity>>

    @Query("SELECT * FROM violations WHERE userId = :userId ORDER BY createdAt DESC")
    fun getViolationsForUser(userId: String): Flow<List<ViolationEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertViolation(violation: ViolationEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertViolations(violations: List<ViolationEntity>)

    // --- PRIVACY REQUESTS ---
    @Query("SELECT * FROM privacy_requests ORDER BY createdAt DESC")
    fun getAllPrivacyRequests(): Flow<List<PrivacyRequestEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPrivacyRequest(request: PrivacyRequestEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPrivacyRequests(requests: List<PrivacyRequestEntity>)

    @Query("UPDATE privacy_requests SET status = :status, processedByAdmin = :adminUsername WHERE id = :id")
    suspend fun updatePrivacyRequestStatus(id: String, status: String, adminUsername: String)
}
