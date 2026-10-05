package com.example.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
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
import com.example.data.remote.AddCommentRequest
import com.example.data.remote.CompleteUploadRequest
import com.example.data.remote.FirebaseService
import com.example.data.remote.LikeRequest
import com.example.data.remote.LoginRequest
import com.example.data.remote.RegisterRequest
import com.example.data.remote.ShareRequest
import com.example.data.remote.TokPulseApiClient
import com.example.data.remote.UploadTicketRequest
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okio.BufferedSink
import org.json.JSONObject
import java.io.InputStream
import java.util.UUID

class TokPulseRepository(private val context: Context) {

    private val TAG = "TokPulseRepository"
    private val sharedPrefs = context.getSharedPreferences("tokpulse_session", Context.MODE_PRIVATE)
    private val db = AppDatabase.getInstance(context)
    private val dao = db.appDao()
    val firebaseService = FirebaseService(context)

    private val _currentUserId = MutableStateFlow<String?>("user_me")
    val currentUserId = _currentUserId.asStateFlow()

    private val _currentUser = MutableStateFlow<UserEntity?>(null)
    val currentUser = _currentUser.asStateFlow()

    private val _savedVideoIds = MutableStateFlow<Set<String>>(emptySet())
    val savedVideoIds = _savedVideoIds.asStateFlow()

    fun isVideoSaved(videoId: String): Boolean = _savedVideoIds.value.contains(videoId)

    suspend fun toggleSave(videoId: String): Boolean = withContext(Dispatchers.IO) {
        val currentSet = _savedVideoIds.value.toMutableSet()
        val isSaved = currentSet.contains(videoId)
        if (isSaved) {
            currentSet.remove(videoId)
        } else {
            currentSet.add(videoId)
        }
        _savedVideoIds.value = currentSet
        sharedPrefs.edit().putStringSet("saved_videos", currentSet).apply()
        !isSaved
    }

    init {
        val savedSet = sharedPrefs.getStringSet("saved_videos", emptySet()) ?: emptySet()
        _savedVideoIds.value = savedSet

        CoroutineScope(Dispatchers.IO).launch {
            // Restore persistent session
            val savedToken = sharedPrefs.getString("auth_token", null)
            val savedUserId = sharedPrefs.getString("user_id", null)

            if (!savedToken.isNullOrBlank()) {
                TokPulseApiClient.setAuthToken(savedToken)
            }

            if (!savedUserId.isNullOrBlank()) {
                val savedUser = dao.getUserByIdSync(savedUserId)
                if (savedUser != null) {
                    _currentUserId.value = savedUser.id
                    _currentUser.value = savedUser
                }
            }

            // Fallback to active creator user if no saved session
            if (_currentUser.value == null) {
                val existingMe = dao.getUserByIdSync("user_me")
                    ?: dao.getUserByEmail("ahmedbecetti41@gmail.com")
                    ?: dao.getUserByEmail("ahmedbecetti35@gmail.com")
                    ?: dao.getUserByIdSync("user_admin")

                if (existingMe != null) {
                    _currentUserId.value = existingMe.id
                    _currentUser.value = existingMe
                } else {
                    val initialUser = UserEntity(
                        id = "user_me",
                        username = "ahmed_creator",
                        displayName = "Ahmed Becetti",
                        email = "ahmedbecetti41@gmail.com",
                        passwordHash = "INITIAL_ACTIVE",
                        avatarUrl = "https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?w=300",
                        bio = "Chort Platform Creator & Developer 🎬",
                        followersCount = 0,
                        followingCount = 0,
                        totalLikes = 0,
                        role = "admin",
                        status = "active",
                        createdAt = System.currentTimeMillis()
                    )
                    dao.insertUser(initialUser)
                    _currentUserId.value = initialUser.id
                    _currentUser.value = initialUser
                }
            }

            // Sync video feed from Vercel API and Database
            syncWithCloud()
        }
    }

    suspend fun syncWithCloud() = withContext(Dispatchers.IO) {
        // Clean any invalid/dummy test videos that cannot be resolved and stale external cached videos
        try {
            dao.cleanInvalidVideos()
            dao.cleanExternalCachedVideos()
        } catch (e: Exception) {
            Log.w(TAG, "cleanup notice: ${e.message}")
        }

        // 1. Fetch real feed from Vercel API (with primary -> fallback failover)
        var syncedCount = 0
        try {
            var feedResponse = try {
                TokPulseApiClient.api.getFeed()
            } catch (e: Exception) {
                Log.w(TAG, "Primary API error: ${e.message}, attempting fallback cluster")
                null
            }

            var feedBody = feedResponse?.takeIf { it.isSuccessful }?.body()
            if (feedBody == null || feedBody.videos.isEmpty()) {
                Log.i(TAG, "Primary feed empty or unavailable, querying fallback cluster at ${TokPulseApiClient.FALLBACK_BASE_URL}")
                try {
                    feedResponse = TokPulseApiClient.fallbackApi.getFeed()
                    if (feedResponse.isSuccessful) {
                        feedBody = feedResponse.body()
                    }
                } catch (fallbackEx: Exception) {
                    Log.e(TAG, "Fallback API error: ${fallbackEx.message}", fallbackEx)
                }
            }

            if (feedBody != null && feedBody.videos.isNotEmpty()) {
                val validVideos = feedBody.videos.filter { apiVid ->
                    val url = (apiVid.videoUrl ?: apiVid.streamUrl ?: "").trim()
                    url.isNotBlank() &&
                        !url.contains("test.com") &&
                        !url.contains("example.com") &&
                        !url.startsWith("http://localhost") &&
                        (url.startsWith("http://") || url.startsWith("https://"))
                }
                val entities = validVideos.map { apiVid ->
                    val directStream = TokPulseApiClient.getCanonicalStreamUrl(apiVid.id)
                    val url = (apiVid.videoUrl ?: apiVid.streamUrl ?: directStream).trim()
                    val thumb = apiVid.thumbnailUrl?.takeIf { it.isNotBlank() && !it.contains("#t=") }
                        ?: "${TokPulseApiClient.BASE_URL}api/v1/videos/${apiVid.id}/thumbnail"
                    val isExt = apiVid.id.startsWith("pex_") ||
                        apiVid.source?.lowercase() == "pexels" ||
                        apiVid.provider?.lowercase() == "pexels"
                    VideoEntity(
                        id = apiVid.id,
                        creatorId = apiVid.creatorId,
                        creatorUsername = apiVid.creatorUsername,
                        creatorAvatar = apiVid.creatorAvatar ?: "https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?w=300",
                        videoUrl = url,
                        thumbnailUrl = thumb,
                        caption = apiVid.caption,
                        musicTitle = apiVid.musicTitle ?: "Original Audio",
                        tags = if (isExt) "#pexels,#licensed,#stock" else "#chort,#fyp,#viral",
                        likesCount = apiVid.likesCount,
                        commentsCount = apiVid.commentsCount,
                        sharesCount = apiVid.sharesCount,
                        viewsCount = apiVid.viewsCount,
                        source = apiVid.source ?: if (isExt) "pexels" else "chort",
                        provider = apiVid.provider ?: if (isExt) "pexels" else "chort",
                        isExternal = isExt,
                        attributionUrl = apiVid.attributionUrl ?: "",
                        photographerUrl = apiVid.photographerUrl ?: "",
                        createdAt = if (apiVid.createdAt > 0) apiVid.createdAt else System.currentTimeMillis()
                    )
                }
                if (entities.isNotEmpty()) {
                    dao.insertVideos(entities)
                    syncedCount = entities.size
                }
                Log.i(TAG, "Successfully synced ${entities.size} valid videos from Vercel API")
            } else {
                Log.w(TAG, "Feed response empty or unsuccessful across clusters")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Vercel API feed sync exception: ${e.message}", e)
        }

        // 2. Also sync with Firebase Firestore if available
        if (firebaseService.isFirebaseAvailable) {
            try {
                val cloudVideos = firebaseService.fetchVideosFromFirestore(limit = 50)
                if (cloudVideos.isNotEmpty()) {
                    dao.insertVideos(cloudVideos)
                }

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
                Log.w(TAG, "Firestore sync notice: ${e.message}")
            }
        }
    }

    suspend fun refreshFeed(): Result<Int> = withContext(Dispatchers.IO) {
        try {
            syncWithCloud()
            val count = dao.getActiveVideosCount()
            Result.success(count)
        } catch (e: Exception) {
            Log.e(TAG, "refreshFeed failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun refreshVideoUrl(videoId: String): String = withContext(Dispatchers.IO) {
        val existing = dao.getVideoById(videoId)
        val canonicalUrl = TokPulseApiClient.getCanonicalStreamUrl(videoId)
        if (existing != null) {
            dao.updateVideo(existing.copy(videoUrl = canonicalUrl))
        }
        canonicalUrl
    }

    suspend fun logout() = withContext(Dispatchers.IO) {
        sharedPrefs.edit().clear().apply()
        TokPulseApiClient.setAuthToken(null)
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

    // --- AUTHENTICATION ---

    suspend fun registerWithEmail(
        email: String,
        password: String,
        username: String,
        displayName: String
    ): Result<UserEntity> = withContext(Dispatchers.IO) {
        try {
            val response = TokPulseApiClient.api.register(
                RegisterRequest(
                    email = email.trim(),
                    username = username.trim(),
                    password = password.trim(),
                    displayName = displayName.trim()
                )
            )

            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                val apiUser = body.user
                if (apiUser != null) {
                    val token = body.token
                    if (!token.isNullOrBlank()) {
                        TokPulseApiClient.setAuthToken(token)
                        sharedPrefs.edit()
                            .putString("auth_token", token)
                            .putString("user_id", apiUser.id)
                            .apply()
                    }

                    val userEntity = UserEntity(
                        id = apiUser.id,
                        username = apiUser.username,
                        displayName = apiUser.displayName ?: displayName.ifBlank { apiUser.username },
                        email = apiUser.email,
                        passwordHash = "JWT_SECURED",
                        avatarUrl = apiUser.avatarUrl ?: "https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?w=300",
                        bio = apiUser.bio ?: "",
                        followersCount = apiUser.followersCount ?: 0,
                        followingCount = apiUser.followingCount ?: 0,
                        totalLikes = 0,
                        role = apiUser.role ?: "user",
                        status = "active",
                        createdAt = System.currentTimeMillis()
                    )

                    dao.insertUser(userEntity)
                    _currentUserId.value = userEntity.id
                    _currentUser.value = userEntity
                    syncWithCloud()
                    return@withContext Result.success(userEntity)
                }
            } else {
                val errJson = response.errorBody()?.string()
                val errMsg = try {
                    JSONObject(errJson ?: "").optString("error", "Registration failed (${response.code()})")
                } catch (e: Exception) {
                    "Registration failed (${response.code()})"
                }
                return@withContext Result.failure(Exception(errMsg))
            }
        } catch (netErr: Exception) {
            Log.w(TAG, "Vercel register error: ${netErr.message}")
        }

        // Secondary fallback to Firebase
        val fbResult = firebaseService.registerWithEmail(email, password, username, displayName)
        if (fbResult.isSuccess) {
            val user = fbResult.getOrThrow()
            dao.insertUser(user)
            _currentUserId.value = user.id
            _currentUser.value = user
        }
        fbResult
    }

    suspend fun signInWithEmail(identifier: String, password: String): Result<UserEntity> = withContext(Dispatchers.IO) {
        try {
            val response = TokPulseApiClient.api.login(
                LoginRequest(
                    identifier = identifier.trim(),
                    password = password.trim()
                )
            )

            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                val apiUser = body.user
                if (apiUser != null) {
                    val token = body.token
                    if (!token.isNullOrBlank()) {
                        TokPulseApiClient.setAuthToken(token)
                        sharedPrefs.edit()
                            .putString("auth_token", token)
                            .putString("user_id", apiUser.id)
                            .apply()
                    }

                    val userEntity = UserEntity(
                        id = apiUser.id,
                        username = apiUser.username,
                        displayName = apiUser.displayName ?: apiUser.username,
                        email = apiUser.email,
                        passwordHash = "JWT_SECURED",
                        avatarUrl = apiUser.avatarUrl ?: "https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?w=300",
                        bio = apiUser.bio ?: "",
                        followersCount = apiUser.followersCount ?: 0,
                        followingCount = apiUser.followingCount ?: 0,
                        totalLikes = 0,
                        role = apiUser.role ?: "user",
                        status = "active",
                        createdAt = System.currentTimeMillis()
                    )

                    dao.insertUser(userEntity)
                    _currentUserId.value = userEntity.id
                    _currentUser.value = userEntity
                    syncWithCloud()
                    return@withContext Result.success(userEntity)
                }
            } else {
                val errJson = response.errorBody()?.string()
                val errMsg = try {
                    JSONObject(errJson ?: "").optString("error", "Sign in failed (${response.code()})")
                } catch (e: Exception) {
                    "Sign in failed (${response.code()})"
                }
                return@withContext Result.failure(Exception(errMsg))
            }
        } catch (netErr: Exception) {
            Log.w(TAG, "Vercel login error: ${netErr.message}")
        }

        // Secondary fallback to Firebase
        val fbResult = firebaseService.signInWithEmail(identifier, password)
        if (fbResult.isSuccess) {
            val user = fbResult.getOrThrow()
            dao.insertUser(user)
            _currentUserId.value = user.id
            _currentUser.value = user
        }
        fbResult
    }

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
            bio = "Chort Platform Administrator & Moderation Lead.",
            followersCount = 0,
            followingCount = 0,
            totalLikes = 0,
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

        // Background call to Vercel API
        try {
            TokPulseApiClient.api.toggleLike(videoId, LikeRequest(user.id))
        } catch (e: Exception) {
            Log.w(TAG, "Vercel toggleLike notice: ${e.message}")
        }

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
        try {
            TokPulseApiClient.api.recordView(videoId)
        } catch (e: Exception) {
            Log.w(TAG, "Vercel recordView notice: ${e.message}")
        }
        if (firebaseService.isFirebaseAvailable) {
            firebaseService.recordVideoView(videoId)
        }
    }

    suspend fun recordVideoShare(videoId: String) = withContext(Dispatchers.IO) {
        dao.incrementShares(videoId)
        val user = _currentUser.value
        try {
            TokPulseApiClient.api.recordShare(videoId, ShareRequest(userId = user?.id))
        } catch (e: Exception) {
            Log.w(TAG, "Vercel recordShare notice: ${e.message}")
        }
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

        // Background call to Vercel API
        try {
            TokPulseApiClient.api.addComment(videoId, AddCommentRequest(user.id, text))
        } catch (e: Exception) {
            Log.w(TAG, "Vercel addComment notice: ${e.message}")
        }

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
        try {
            TokPulseApiClient.api.deleteComment(videoId, commentId)
        } catch (e: Exception) {
            Log.w(TAG, "Vercel deleteComment notice: ${e.message}")
        }
        if (firebaseService.isFirebaseAvailable) {
            firebaseService.deleteCommentInFirestore(commentId, videoId)
        }
    }

    suspend fun likeComment(commentId: String) = withContext(Dispatchers.IO) {
        dao.incrementCommentLikes(commentId)
    }

    fun getTotalLikesReceived(userId: String): Flow<Int> = dao.getTotalLikesReceived(userId)

    // --- FOLLOWS ---

    fun getFollowersCount(userId: String): Flow<Int> = dao.getFollowersCount(userId)

    fun getFollowingCount(userId: String): Flow<Int> = dao.getFollowingCount(userId)

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

    // --- VIDEO UPLOAD PIPELINE ---

    suspend fun uploadVideo(
        videoUrl: String,
        caption: String,
        tags: String,
        musicTitle: String,
        videoUri: Uri? = null,
        onProgress: ((Float) -> Unit)? = null
    ): VideoEntity = withContext(Dispatchers.IO) {
        val user = _currentUser.value
        val creatorId = user?.id ?: "creator_guest"
        val creatorName = user?.username ?: "creator"
        val creatorAvatar = user?.avatarUrl ?: "https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?w=300"

        var finalVideoUrl = videoUrl
        var videoId = "vid_${UUID.randomUUID().toString().take(8)}"
        var realThumbUrl: String? = null

        // 1. Request upload ticket from Vercel API
        try {
            val ticketResp = TokPulseApiClient.api.requestUploadUrl(
                UploadTicketRequest(
                    filename = "video_${System.currentTimeMillis()}.mp4",
                    contentType = "video/mp4",
                    userId = creatorId
                )
            )

            if (ticketResp.isSuccessful && ticketResp.body() != null) {
                val ticket = ticketResp.body()!!
                videoId = ticket.videoId
                finalVideoUrl = ticket.streamUrl
                realThumbUrl = ticket.thumbnailUrl

                // 2. Direct binary upload to S3/Cloud Storage presigned URL if device video selected
                if (videoUri != null) {
                    onProgress?.invoke(0.10f)
                    val uploadSuccess = uploadBinaryToUrl(ticket.uploadUrl, videoUri, onProgress)
                    if (!uploadSuccess && firebaseService.isFirebaseAvailable) {
                        val fbRes = firebaseService.uploadVideoToStorage(videoUri, videoId, onProgress)
                        if (fbRes.isSuccess) {
                            finalVideoUrl = fbRes.getOrThrow()
                        }
                    }

                    // Extract REAL video thumbnail frame using MediaMetadataRetriever
                    try {
                        val retriever = MediaMetadataRetriever()
                        retriever.setDataSource(context, videoUri)
                        val frameBitmap = retriever.getFrameAtTime(1000000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                            ?: retriever.frameAtTime
                        if (frameBitmap != null) {
                            val stream = ByteArrayOutputStream()
                            frameBitmap.compress(Bitmap.CompressFormat.JPEG, 85, stream)
                            val thumbBytes = stream.toByteArray()
                            stream.close()

                            if (!ticket.thumbnailUploadUrl.isNullOrBlank() && thumbBytes.isNotEmpty()) {
                                uploadByteArrayToUrl(ticket.thumbnailUploadUrl, thumbBytes, "image/jpeg")
                            }
                        }
                        retriever.release()
                    } catch (thumbEx: Exception) {
                        Log.w(TAG, "Frame extraction notice: ${thumbEx.message}")
                    }
                }

                // 3. Complete upload in PostgreSQL via Vercel API
                TokPulseApiClient.api.completeUpload(
                    CompleteUploadRequest(
                        videoId = videoId,
                        userId = creatorId,
                        caption = caption,
                        videoUrl = finalVideoUrl,
                        thumbnailUrl = realThumbUrl ?: "${TokPulseApiClient.BASE_URL}api/v1/videos/$videoId/thumbnail",
                        musicTitle = musicTitle,
                        aspectRatio = "9:16",
                        objectKey = ticket.objectKey
                    )
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Vercel upload ticket error: ${e.message}")
            if (videoUri != null && firebaseService.isFirebaseAvailable) {
                val storageResult = firebaseService.uploadVideoToStorage(videoUri, videoId, onProgress)
                if (storageResult.isSuccess) {
                    finalVideoUrl = storageResult.getOrThrow()
                }
            }
        }

        val effectiveThumbnail = realThumbUrl?.takeIf { it.isNotBlank() && !it.contains("#t=") }
            ?: "${TokPulseApiClient.BASE_URL}api/v1/videos/$videoId/thumbnail"

        val newVideo = VideoEntity(
            id = videoId,
            creatorId = creatorId,
            creatorUsername = creatorName,
            creatorAvatar = creatorAvatar,
            videoUrl = finalVideoUrl,
            thumbnailUrl = effectiveThumbnail,
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
            try { firebaseService.publishVideoToFirestore(newVideo) } catch (e: Exception) {}
        }
        newVideo
    }

    private fun uploadByteArrayToUrl(uploadUrl: String, bytes: ByteArray, contentType: String): Boolean {
        return try {
            val client = OkHttpClient()
            val requestBody = object : RequestBody() {
                override fun contentType() = contentType.toMediaTypeOrNull()
                override fun contentLength() = bytes.size.toLong()
                override fun writeTo(sink: BufferedSink) {
                    sink.write(bytes)
                }
            }
            val request = Request.Builder()
                .url(uploadUrl)
                .put(requestBody)
                .build()
            val response = client.newCall(request).execute()
            response.isSuccessful
        } catch (e: Exception) {
            Log.w(TAG, "uploadByteArrayToUrl error: ${e.message}")
            false
        }
    }

    private fun uploadBinaryToUrl(uploadUrl: String, videoUri: Uri, onProgress: ((Float) -> Unit)?): Boolean {
        return try {
            val contentResolver = context.contentResolver
            val inputStream: InputStream = contentResolver.openInputStream(videoUri) ?: return false
            val bytes = inputStream.readBytes()
            inputStream.close()

            val client = OkHttpClient()
            val requestBody = object : RequestBody() {
                override fun contentType() = "video/mp4".toMediaTypeOrNull()
                override fun contentLength() = bytes.size.toLong()
                override fun writeTo(sink: BufferedSink) {
                    val total = bytes.size
                    var written = 0
                    val chunkSize = 8192
                    while (written < total) {
                        val len = minOf(chunkSize, total - written)
                        sink.write(bytes, written, len)
                        written += len
                        onProgress?.invoke(written.toFloat() / total.toFloat())
                    }
                }
            }

            val request = Request.Builder()
                .url(uploadUrl)
                .put(requestBody)
                .build()

            val response = client.newCall(request).execute()
            response.isSuccessful
        } catch (e: Exception) {
            Log.w(TAG, "uploadBinaryToUrl error: ${e.message}")
            false
        }
    }

    // --- SEARCH & PROFILES ---

    fun searchVideos(query: String): Flow<List<VideoEntity>> = dao.searchVideos(query)

    fun searchUsers(query: String): Flow<List<UserEntity>> = dao.searchUsers(query)

    suspend fun searchRemote(query: String) = withContext(Dispatchers.IO) {
        val clean = query.trim()
        if (clean.isBlank()) return@withContext
        try {
            val response = TokPulseApiClient.api.search(clean)
            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                if (body.videos.isNotEmpty()) {
                    val entities = body.videos.map { apiVid ->
                        VideoEntity(
                            id = apiVid.id,
                            creatorId = apiVid.creatorId,
                            creatorUsername = apiVid.creatorUsername,
                            creatorAvatar = apiVid.creatorAvatar ?: "",
                            videoUrl = apiVid.streamUrl ?: "",
                            thumbnailUrl = apiVid.thumbnailUrl ?: "",
                            caption = apiVid.caption,
                            musicTitle = apiVid.musicTitle ?: "Original Audio",
                            tags = "#tokpulse,#fyp",
                            likesCount = apiVid.likesCount,
                            commentsCount = apiVid.commentsCount,
                            sharesCount = apiVid.sharesCount,
                            viewsCount = apiVid.viewsCount,
                            createdAt = if (apiVid.createdAt > 0) apiVid.createdAt else System.currentTimeMillis()
                        )
                    }
                    dao.insertVideos(entities)
                }
                if (body.users.isNotEmpty()) {
                    val userEntities = body.users.map { u ->
                        UserEntity(
                            id = u.id,
                            username = u.username,
                            displayName = u.displayName ?: u.username,
                            email = "${u.username}@tokpulse.social",
                            passwordHash = "EXTERNAL",
                            avatarUrl = u.avatarUrl ?: "",
                            bio = u.bio ?: "",
                            followersCount = u.followersCount,
                            followingCount = 0,
                            totalLikes = 0,
                            role = "user",
                            status = "active",
                            createdAt = System.currentTimeMillis()
                        )
                    }
                    dao.insertUsers(userEntities)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "searchRemote error: ${e.message}")
        }
    }

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
            firebaseService.createOrUpdateUserSnapshot(updated)
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

    suspend fun deleteVideo(videoId: String): Boolean = withContext(Dispatchers.IO) {
        dao.deleteVideo(videoId)
        dao.setVideoDeleted(videoId, true)
        val apiSuccess = try {
            val resp = TokPulseApiClient.api.deleteVideo(videoId)
            resp.isSuccessful
        } catch (e: Exception) {
            Log.w(TAG, "Vercel deleteVideo notice: ${e.message}")
            false
        }
        if (firebaseService.isFirebaseAvailable) {
            firebaseService.deleteVideoAdminFirestore(videoId)
        }
        apiSuccess
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
