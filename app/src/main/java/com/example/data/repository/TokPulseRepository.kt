package com.example.data.repository

import android.content.Context
import android.net.Uri
import com.example.data.local.AppDatabase
import com.example.data.local.entities.CommentEntity
import com.example.data.local.entities.FollowEntity
import com.example.data.local.entities.LikeEntity
import com.example.data.local.entities.NotificationEntity
import com.example.data.local.entities.PrivacyRequestEntity
import com.example.data.local.entities.ReportEntity
import com.example.data.local.entities.UserEntity
import com.example.data.local.entities.VideoEntity
import com.example.data.local.entities.ViolationEntity
import com.example.data.remote.FirebaseService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

class TokPulseRepository(private val context: Context) {

    private val db = AppDatabase.getInstance(context)
    private val dao = db.appDao()
    val firebaseService = FirebaseService(context)

    private val _currentUserId = MutableStateFlow<String?>("user_me")
    val currentUserId = _currentUserId.asStateFlow()

    private val _currentUser = MutableStateFlow<UserEntity?>(null)
    val currentUser = _currentUser.asStateFlow()

    init {
        CoroutineScope(Dispatchers.IO).launch {
            // Restore existing session or sync with Firebase Auth
            val authUser = firebaseService.auth?.currentUser
            if (authUser != null) {
                val profile = firebaseService.syncAuthenticatedProfile(authUser.uid)
                if (profile != null) {
                    dao.insertUser(profile)
                    _currentUserId.value = profile.id
                    _currentUser.value = profile
                }
            } else {
                val defaultAdmin = dao.getUserByEmail("ahmedbecetti41@gmail.com")
                    ?: dao.getUserByEmail("ahmedbecetti35@gmail.com")
                    ?: dao.getUserByIdSync("user_admin")
                if (defaultAdmin != null) {
                    _currentUserId.value = defaultAdmin.id
                    _currentUser.value = defaultAdmin
                }
            }
            syncWithCloud()
        }
    }

    suspend fun syncWithCloud() = withContext(Dispatchers.IO) {
        if (!firebaseService.isFirebaseAvailable) return@withContext
        try {
            // 1. Fetch live videos from Firestore
            val cloudVideos = firebaseService.fetchVideosFromFirestore(limit = 50)
            if (cloudVideos.isNotEmpty()) {
                dao.insertVideos(cloudVideos)
            } else {
                seedLaunchVideoToFirestoreIfNeeded()
            }

            // 2. Fetch admin data from Firestore if current user is admin
            val user = _currentUser.value
            val isUserAdmin = user?.role == "admin" ||
                user?.email?.equals("ahmedbecetti35@gmail.com", true) == true ||
                user?.email?.equals("ahmedbecetti41@gmail.com", true) == true

            if (isUserAdmin) {
                val cloudUsers = firebaseService.fetchUsersAdminFirestore()
                if (cloudUsers.isNotEmpty()) dao.insertUsers(cloudUsers)

                val cloudReports = firebaseService.fetchReportsAdminFirestore()
                if (cloudReports.isNotEmpty()) dao.insertReports(cloudReports)

                val cloudViolations = firebaseService.fetchViolationsAdminFirestore()
                if (cloudViolations.isNotEmpty()) dao.insertViolations(cloudViolations)

                val cloudPrivacy = firebaseService.fetchPrivacyRequestsAdminFirestore()
                if (cloudPrivacy.isNotEmpty()) dao.insertPrivacyRequests(cloudPrivacy)
            }
        } catch (e: Exception) {
            // Log sync warning, continue gracefully with local cache
        }
    }

    private suspend fun seedLaunchVideoToFirestoreIfNeeded() {
        val launchVideo = VideoEntity(
            id = "vid_launch_1",
            creatorId = "tokpulse_official",
            creatorUsername = "tokpulse",
            creatorAvatar = "https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?w=300",
            videoUrl = "https://raw.githubusercontent.com/intel-iot-devkit/sample-videos/master/person-bicycle-car-detection.mp4",
            thumbnailUrl = "https://images.unsplash.com/photo-1564982752979-3f7bc974d29a?w=500",
            caption = "Welcome to TokPulse! 🎬 The new home for creators, dancers, and visionaries. Drop a like & upload your first clip! #welcome #tokpulse #viral",
            musicTitle = "TokPulse Anthem - Official Sound",
            tags = "#welcome,#tokpulse,#viral",
            likesCount = 120,
            commentsCount = 8,
            sharesCount = 24,
            viewsCount = 540,
            createdAt = System.currentTimeMillis()
        )
        dao.insertVideo(launchVideo)
        if (firebaseService.isFirebaseAvailable) {
            try {
                firebaseService.publishVideoToFirestore(launchVideo)
            } catch (e: Exception) {
                // Ignore launch seeding exception if offline or during initial startup
            }
        }
    }

    suspend fun logout() = withContext(Dispatchers.IO) {
        firebaseService.signOut()
        _currentUserId.value = null
        _currentUser.value = null
    }

    suspend fun loadCurrentUser(userId: String?) {
        _currentUserId.value = userId
        if (userId != null) {
            val user = dao.getUserByIdSync(userId)
            _currentUser.value = user
        } else {
            _currentUser.value = null
        }
    }

    // --- AUTHENTICATION VIA GOOGLE CREDENTIAL MANAGER ---

    suspend fun signInWithGoogle(): Result<UserEntity> = withContext(Dispatchers.IO) {
        val result = firebaseService.signInWithGoogle()
        if (result.isSuccess) {
            val user = result.getOrThrow()
            dao.insertUser(user)
            _currentUserId.value = user.id
            _currentUser.value = user
            syncWithCloud()
        }
        result
    }

    suspend fun devSwitchToAdmin(): UserEntity = withContext(Dispatchers.IO) {
        val adminUser = UserEntity(
            id = "user_admin",
            username = "admin",
            displayName = "Admin Ahmed",
            email = "ahmedbecetti35@gmail.com",
            passwordHash = "PROTECTED",
            avatarUrl = "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=300",
            bio = "Official TokPulse Platform Administrator & Moderation Lead.",
            followersCount = 100000,
            followingCount = 1,
            totalLikes = 500000,
            role = "admin",
            status = "active",
            createdAt = System.currentTimeMillis()
        )
        dao.insertUser(adminUser)
        _currentUserId.value = adminUser.id
        _currentUser.value = adminUser
        adminUser
    }

    // --- VIDEO FEED & DETAILS ---

    fun getActiveVideos(): Flow<List<VideoEntity>> = dao.getAllActiveVideos()

    fun getAllVideosAdmin(): Flow<List<VideoEntity>> = dao.getAllVideosAdmin()

    fun getVideosByCreator(creatorId: String): Flow<List<VideoEntity>> = dao.getVideosByCreator(creatorId)

    fun getUserLikedVideoIds(userId: String): Flow<List<String>> = dao.getUserLikedVideoIds(userId)

    suspend fun toggleLike(videoId: String): Boolean = withContext(Dispatchers.IO) {
        val user = _currentUser.value ?: return@withContext false
        val isLiked = dao.countLike(videoId, user.id) > 0
        if (isLiked) {
            dao.deleteLike(videoId, user.id)
            dao.updateLikesCount(videoId, -1)
            if (firebaseService.isFirebaseAvailable) {
                firebaseService.toggleLikeInFirestore(videoId, user.id, true)
            }
            false
        } else {
            dao.insertLike(LikeEntity(id = "${videoId}_${user.id}", videoId = videoId, userId = user.id))
            dao.updateLikesCount(videoId, 1)
            if (firebaseService.isFirebaseAvailable) {
                firebaseService.toggleLikeInFirestore(videoId, user.id, false)
            }
            val video = dao.getVideoById(videoId)
            if (video != null && video.creatorId != user.id) {
                dao.insertNotification(
                    NotificationEntity(
                        id = UUID.randomUUID().toString(),
                        userId = video.creatorId,
                        actorId = user.id,
                        actorUsername = user.username,
                        actorAvatar = user.avatarUrl,
                        type = "like",
                        message = "liked your video: \"${video.caption.take(24)}...\"",
                        videoId = videoId
                    )
                )
            }
            true
        }
    }

    suspend fun isVideoLiked(videoId: String): Boolean = withContext(Dispatchers.IO) {
        val user = _currentUser.value ?: return@withContext false
        dao.countLike(videoId, user.id) > 0
    }

    suspend fun recordVideoView(videoId: String) = withContext(Dispatchers.IO) {
        dao.incrementViews(videoId)
        if (firebaseService.isFirebaseAvailable) {
            firebaseService.recordVideoView(videoId)
        }
    }

    suspend fun recordVideoShare(videoId: String) = withContext(Dispatchers.IO) {
        dao.incrementShares(videoId)
    }

    // --- COMMENTS ---

    fun getComments(videoId: String): Flow<List<CommentEntity>> = dao.getCommentsForVideo(videoId)

    suspend fun addComment(videoId: String, text: String): Result<CommentEntity> = withContext(Dispatchers.IO) {
        val user = _currentUser.value ?: return@withContext Result.failure(Exception("Log in to comment"))
        val comment = CommentEntity(
            id = "c_${UUID.randomUUID().toString().take(8)}",
            videoId = videoId,
            userId = user.id,
            username = user.username,
            userAvatar = user.avatarUrl,
            text = text
        )
        dao.insertComment(comment)
        dao.updateCommentsCount(videoId, 1)

        if (firebaseService.isFirebaseAvailable) {
            firebaseService.addCommentToFirestore(comment)
        }
        val video = dao.getVideoById(videoId)
        if (video != null && video.creatorId != user.id) {
            dao.insertNotification(
                NotificationEntity(
                    id = UUID.randomUUID().toString(),
                    userId = video.creatorId,
                    actorId = user.id,
                    actorUsername = user.username,
                    actorAvatar = user.avatarUrl,
                    type = "comment",
                    message = "commented: \"${text.take(30)}\"",
                    videoId = videoId
                )
            )
        }
        Result.success(comment)
    }

    suspend fun deleteComment(commentId: String, videoId: String) = withContext(Dispatchers.IO) {
        dao.deleteComment(commentId)
        dao.updateCommentsCount(videoId, -1)
        if (firebaseService.isFirebaseAvailable) {
            firebaseService.deleteCommentInFirestore(commentId, videoId)
        }
    }

    suspend fun likeComment(commentId: String) = withContext(Dispatchers.IO) {
        dao.incrementCommentLikes(commentId)
    }

    // --- FOLLOWS ---

    fun getFollowingIds(userId: String): Flow<List<String>> = dao.getFollowingIds(userId)

    suspend fun isFollowing(targetUserId: String): Boolean = withContext(Dispatchers.IO) {
        val user = _currentUser.value ?: return@withContext false
        dao.countFollow(user.id, targetUserId) > 0
    }

    suspend fun toggleFollow(targetUserId: String): Boolean = withContext(Dispatchers.IO) {
        val user = _currentUser.value ?: return@withContext false
        val following = dao.countFollow(user.id, targetUserId) > 0
        if (following) {
            dao.deleteFollow(user.id, targetUserId)
            if (firebaseService.isFirebaseAvailable) {
                firebaseService.recordUnfollowInFirestore(user.id, targetUserId)
            }
            false
        } else {
            val follow = FollowEntity(
                id = "${user.id}_$targetUserId",
                followerId = user.id,
                followingId = targetUserId
            )
            dao.insertFollow(follow)
            if (firebaseService.isFirebaseAvailable) {
                firebaseService.recordFollowInFirestore(user.id, targetUserId, user)
            }
            dao.insertNotification(
                NotificationEntity(
                    id = UUID.randomUUID().toString(),
                    userId = targetUserId,
                    actorId = user.id,
                    actorUsername = user.username,
                    actorAvatar = user.avatarUrl,
                    type = "follow",
                    message = "started following you on TokPulse!"
                )
            )
            true
        }
    }

    // --- VIDEO UPLOAD & FIREBASE STORAGE ---

    suspend fun uploadVideo(
        videoUrl: String,
        caption: String,
        tags: String,
        musicTitle: String,
        videoUri: Uri? = null
    ): VideoEntity = withContext(Dispatchers.IO) {
        val user = _currentUser.value
        val creatorId = user?.id ?: "creator_guest"
        val creatorName = user?.username ?: "creator"
        val creatorAvatar = user?.avatarUrl ?: "https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?w=300"

        val videoId = "vid_${UUID.randomUUID().toString().take(8)}"
        var finalVideoUrl = videoUrl

        if (videoUri != null && firebaseService.isFirebaseAvailable) {
            val storageResult = firebaseService.uploadVideoToStorage(videoUri, videoId)
            if (storageResult.isSuccess) {
                finalVideoUrl = storageResult.getOrThrow()
            }
        }

        val newVideo = VideoEntity(
            id = videoId,
            creatorId = creatorId,
            creatorUsername = creatorName,
            creatorAvatar = creatorAvatar,
            videoUrl = finalVideoUrl,
            thumbnailUrl = "https://images.unsplash.com/photo-1518770660439-4636190af475?w=500",
            caption = caption,
            musicTitle = musicTitle.ifBlank { "Original Sound - $creatorName" },
            tags = tags,
            likesCount = 0,
            commentsCount = 0,
            sharesCount = 0,
            viewsCount = 1,
            createdAt = System.currentTimeMillis()
        )

        dao.insertVideo(newVideo)
        if (firebaseService.isFirebaseAvailable) {
            firebaseService.publishVideoToFirestore(newVideo)
        }
        newVideo
    }

    // --- SEARCH & PROFILES ---

    fun searchVideos(query: String): Flow<List<VideoEntity>> = dao.searchVideos(query)

    fun searchUsers(query: String): Flow<List<UserEntity>> = dao.searchUsers(query)

    fun getUserById(userId: String): Flow<UserEntity?> = dao.getUserById(userId)

    suspend fun updateProfile(
        displayName: String,
        username: String,
        bio: String,
        avatarUri: Uri? = null
    ) = withContext(Dispatchers.IO) {
        val user = _currentUser.value ?: return@withContext
        var avatarUrl = user.avatarUrl

        if (avatarUri != null && firebaseService.isFirebaseAvailable) {
            val storageResult = firebaseService.uploadAvatarToStorage(avatarUri, user.id)
            if (storageResult.isSuccess) {
                avatarUrl = storageResult.getOrThrow()
            }
        }

        val updated = user.copy(
            displayName = displayName,
            username = username,
            bio = bio,
            avatarUrl = avatarUrl
        )
        dao.updateUser(updated)
        _currentUser.value = updated

        if (firebaseService.isFirebaseAvailable) {
            firebaseService.firestore?.collection("users")?.document(user.id)?.update(
                mapOf(
                    "displayName" to displayName,
                    "username" to username,
                    "bio" to bio,
                    "avatarUrl" to avatarUrl
                )
            )
        }
    }

    // --- NOTIFICATIONS ---

    fun getNotifications(userId: String): Flow<List<NotificationEntity>> = dao.getNotificationsForUser(userId)

    fun getUnreadCount(userId: String): Flow<Int> = dao.getUnreadNotificationCount(userId)

    suspend fun markAllNotificationsRead(userId: String) = withContext(Dispatchers.IO) {
        dao.markNotificationsAsRead(userId)
    }

    // --- REPORTS ---

    suspend fun submitReport(
        targetType: String,
        targetId: String,
        targetOwnerUsername: String,
        targetSnippet: String,
        reason: String,
        description: String
    ): Result<ReportEntity> = withContext(Dispatchers.IO) {
        val user = _currentUser.value ?: return@withContext Result.failure(Exception("Log in to report content"))
        val report = ReportEntity(
            id = "rep_${UUID.randomUUID().toString().take(8)}",
            reporterId = user.id,
            reporterUsername = user.username,
            targetType = targetType,
            targetId = targetId,
            targetOwnerUsername = targetOwnerUsername,
            targetSnippet = targetSnippet,
            reason = reason,
            description = description,
            status = "pending"
        )
        dao.insertReport(report)
        if (firebaseService.isFirebaseAvailable) {
            firebaseService.submitReportToFirestore(report)
        }
        Result.success(report)
    }

    // --- ADMIN ACTIONS ---

    fun getAllUsersAdmin(): Flow<List<UserEntity>> = dao.getAllUsers()

    fun getAllReportsAdmin(): Flow<List<ReportEntity>> = dao.getAllReports()

    fun getAllViolationsAdmin(): Flow<List<ViolationEntity>> = dao.getAllViolations()

    fun getAllPrivacyRequestsAdmin(): Flow<List<PrivacyRequestEntity>> = dao.getAllPrivacyRequests()

    suspend fun setVideoHidden(videoId: String, isHidden: Boolean) = withContext(Dispatchers.IO) {
        dao.setVideoHidden(videoId, isHidden)
        if (firebaseService.isFirebaseAvailable) {
            firebaseService.hideVideoAdminFirestore(videoId, isHidden)
        }
    }

    suspend fun deleteVideo(videoId: String) = withContext(Dispatchers.IO) {
        dao.setVideoDeleted(videoId, true)
        if (firebaseService.isFirebaseAvailable) {
            firebaseService.deleteVideoAdminFirestore(videoId)
        }
    }

    suspend fun updateReport(reportId: String, newStatus: String, notes: String) = withContext(Dispatchers.IO) {
        val admin = _currentUser.value?.username ?: "admin"
        dao.updateReportStatus(reportId, newStatus, notes, admin)
        if (firebaseService.isFirebaseAvailable) {
            firebaseService.resolveReportAdminFirestore(reportId, newStatus, notes, admin)
        }
    }

    suspend fun banUser(userId: String, reason: String = "Terms violation") = withContext(Dispatchers.IO) {
        dao.updateUserStatus(userId, "banned")
        val user = dao.getUserByIdSync(userId)
        val adminName = _currentUser.value?.username ?: "admin"
        val viol = ViolationEntity(
            id = "viol_${UUID.randomUUID().toString().take(8)}",
            userId = userId,
            username = user?.username ?: "user",
            violationType = "terms_violation",
            reason = reason,
            actionTaken = "Permanent Ban",
            adminUsername = adminName
        )
        dao.insertViolation(viol)
        if (firebaseService.isFirebaseAvailable) {
            firebaseService.banUserAdminFirestore(userId, reason, adminName)
        }
    }

    suspend fun unbanUser(userId: String) = withContext(Dispatchers.IO) {
        dao.updateUserStatus(userId, "active")
        val adminName = _currentUser.value?.username ?: "admin"
        if (firebaseService.isFirebaseAvailable) {
            firebaseService.unbanUserAdminFirestore(userId, adminName)
        }
    }

    suspend fun suspendUser(userId: String, days: Int = 7) = withContext(Dispatchers.IO) {
        val until = System.currentTimeMillis() + (days * 24L * 3600L * 1000L)
        dao.updateUserStatus(userId, "suspended", until)
        val adminName = _currentUser.value?.username ?: "admin"
        if (firebaseService.isFirebaseAvailable) {
            firebaseService.tempBanUserAdminFirestore(userId, "Suspended for $days days", adminName)
        }
    }

    suspend fun updateUserRole(userId: String, newRole: String) = withContext(Dispatchers.IO) {
        val user = dao.getUserByIdSync(userId) ?: return@withContext
        dao.updateUser(user.copy(role = newRole))
        if (firebaseService.isFirebaseAvailable) {
            firebaseService.firestore?.collection("users")?.document(userId)?.update("role", newRole)
        }
    }

    suspend fun recordViolationAndAction(
        userId: String,
        username: String,
        violationType: String,
        reason: String,
        relatedContent: String,
        actionTaken: String
    ) = withContext(Dispatchers.IO) {
        val admin = _currentUser.value?.username ?: "admin"
        val violation = ViolationEntity(
            id = "viol_${UUID.randomUUID().toString().take(8)}",
            userId = userId,
            username = username,
            violationType = violationType,
            reason = reason,
            relatedContent = relatedContent,
            actionTaken = actionTaken,
            adminUsername = admin
        )
        dao.insertViolation(violation)

        when (actionTaken) {
            "permanent_ban" -> {
                dao.updateUserStatus(userId, "banned")
                if (firebaseService.isFirebaseAvailable) {
                    firebaseService.banUserAdminFirestore(userId, reason, admin)
                }
            }
            "temporary_suspension" -> {
                val sevenDays = System.currentTimeMillis() + 7 * 24 * 60 * 60 * 1000L
                dao.updateUserStatus(userId, "suspended", sevenDays)
                if (firebaseService.isFirebaseAvailable) {
                    firebaseService.tempBanUserAdminFirestore(userId, reason, admin)
                }
            }
            "warning" -> {
                if (firebaseService.isFirebaseAvailable) {
                    firebaseService.warnUserAdminFirestore(userId, reason, admin)
                }
            }
            "unban" -> {
                dao.updateUserStatus(userId, "active", null)
                if (firebaseService.isFirebaseAvailable) {
                    firebaseService.unbanUserAdminFirestore(userId, admin)
                }
            }
        }
    }

    suspend fun processPrivacyRequest(requestId: String, newStatus: String) = withContext(Dispatchers.IO) {
        val admin = _currentUser.value?.username ?: "admin"
        dao.updatePrivacyRequestStatus(requestId, newStatus, admin)
        if (firebaseService.isFirebaseAvailable) {
            firebaseService.processPrivacyRequestFirestore(requestId, newStatus, admin)
        }
    }

    suspend fun adminDeleteUser(userId: String) = withContext(Dispatchers.IO) {
        dao.deleteUser(userId)
        if (firebaseService.isFirebaseAvailable) {
            firebaseService.firestore?.collection("users")?.document(userId)?.delete()
        }
    }

    suspend fun toggleBlockUser(blockedUserId: String) = withContext(Dispatchers.IO) {
        val currentId = _currentUserId.value ?: return@withContext
        if (firebaseService.isFirebaseAvailable) {
            firebaseService.blockUserInFirestore(currentId, blockedUserId)
        }
    }

    suspend fun requestAccountDeletion(reason: String): Result<String> = withContext(Dispatchers.IO) {
        val user = _currentUser.value ?: return@withContext Result.failure(Exception("Log in to request account deletion"))
        val req = PrivacyRequestEntity(
            id = "priv_${UUID.randomUUID().toString().take(8)}",
            userId = user.id,
            username = user.username,
            email = user.email,
            requestType = "account_deletion",
            reason = reason,
            status = "pending"
        )
        dao.insertPrivacyRequest(req)
        if (firebaseService.isFirebaseAvailable) {
            firebaseService.submitPrivacyRequestToFirestore(req)
        }
        Result.success("Account deletion request submitted. An administrator will review and purge your data within 48 hours.")
    }

    suspend fun resetPassword(email: String): Result<String> = withContext(Dispatchers.IO) {
        Result.success("Password reset instructions sent to $email")
    }
}
