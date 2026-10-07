package com.example.data.repository

import android.app.Activity
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
import com.example.data.remote.ApiLoginRecord
import com.example.data.remote.ApiSession
import com.example.data.remote.AuthResponse
import com.example.data.remote.ApiUser
import com.example.data.remote.ApiVideo
import com.example.data.remote.CompleteUploadRequest
import com.example.data.remote.FacebookAuth
import com.example.data.remote.FirebaseService
import com.example.data.remote.GoogleAuth
import com.example.data.remote.LikeRequest
import com.example.data.remote.LinkProviderRequest
import com.example.data.remote.LinkedProvider
import com.example.data.remote.LoginRequest
import com.example.data.remote.LoginRecordsResponse
import com.example.data.remote.AdminOverviewResponse
import com.example.data.remote.AdminReportsResponse
import com.example.data.remote.NotificationsReadRequest
import com.example.data.remote.OAuthFacebookRequest
import com.example.data.remote.OAuthGoogleRequest
import com.example.data.remote.OtpResponse
import com.example.data.remote.ChangePasswordRequest
import com.example.data.remote.UpdateUserRequest
import com.example.data.remote.PhoneRequestBody
import com.example.data.remote.PhoneVerifyRequest
import com.example.data.remote.RecoverConfirmRequest
import com.example.data.remote.RecoverRequestBody
import com.example.data.remote.RegisterRequest
import com.example.data.remote.ResolveReportRequest
import com.example.data.remote.ResolveReportResponse
import com.example.data.remote.ShareRequest
import com.example.data.remote.ZevoraApiClient
import com.example.data.remote.UploadTicketRequest
import java.io.ByteArrayOutputStream
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okio.BufferedSink
import org.json.JSONObject
import java.io.InputStream
import java.util.UUID
import java.util.concurrent.TimeUnit

class ZevoraRepository(private val context: Context) {

    private val TAG = "ZevoraRepository"
    private val sharedPrefs = context.getSharedPreferences("tokpulse_session", Context.MODE_PRIVATE)
    private val db = AppDatabase.getInstance(context)
    private val dao = db.appDao()
    val firebaseService = FirebaseService(context)

    private val _currentUserId = MutableStateFlow<String?>(null)
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
                ZevoraApiClient.setAuthToken(savedToken)
            }
            // Expired/revoked token observed by the HTTP layer: forget the saved
            // token so the next launch returns to login instead of failing silently.
            ZevoraApiClient.onUnauthorized = {
                try {
                    sharedPrefs.edit().remove("auth_token").apply()
                } catch (_: Exception) {}
            }

            if (!savedUserId.isNullOrBlank()) {
                val savedUser = dao.getUserByIdSync(savedUserId)
                if (savedUser != null) {
                    _currentUserId.value = savedUser.id
                    _currentUser.value = savedUser
                }
            }

            // No saved session -> stay logged OUT (guest browsing). Never fabricate
            // a local user, and never auto-grant admin: identity comes only
            // from a real login whose session restores above.

            // Sync video feed from Vercel API and Database
            try {
                val cached = dao.getAllActiveVideosSync()
                if (cached.isNotEmpty()) {
                    _feedVideos.value = cached
                }
            } catch (_: Exception) {}
            syncWithCloud()
        }
    }

    // Dynamic feed stream: holds the exact ordered server feed without SQLite re-sorting
    private val _feedVideos = MutableStateFlow<List<VideoEntity>>(emptyList())
    val feedVideos: StateFlow<List<VideoEntity>> = _feedVideos.asStateFlow()

    // Feed pagination state: the server cursor + hasMore from the last page.
    private var feedCursor: String? = null
    private var feedHasMore: Boolean = true
    private var feedPaging: Boolean = false

    suspend fun syncWithCloud() = withContext(Dispatchers.IO) {
        // Clean any invalid/dummy test videos that cannot be resolved
        try {
            dao.cleanInvalidVideos()
        } catch (e: Exception) {
            Log.w(TAG, "cleanup notice: ${e.message}")
        }

        // 1. Fetch real feed from production Vercel API
        var syncedCount = 0
        feedCursor = null
        feedHasMore = true
        try {
            val feedResponse = try {
                ZevoraApiClient.api.getFeed(limit = 20)
            } catch (e: Exception) {
                Log.w(TAG, "Primary API error: ${e.message}")
                null
            }

            val feedBody = feedResponse?.takeIf { it.isSuccessful }?.body()

            if (feedBody != null && feedBody.videos.isNotEmpty()) {
                val validVideos = feedBody.videos.filter { apiVid ->
                    val url = (apiVid.videoUrl ?: apiVid.streamUrl ?: "").trim()
                    url.isNotBlank() &&
                        !url.contains("test.com") &&
                        !url.contains("example.com") &&
                        !url.startsWith("http://localhost") &&
                        (url.startsWith("http://") || url.startsWith("https://"))
                }
                val entities = validVideos.map { apiVid -> apiVideoToEntity(apiVid) }
                if (entities.isNotEmpty()) {
                    dao.insertVideos(entities)
                    _feedVideos.value = entities
                    syncedCount = entities.size
                }
                feedCursor = feedBody.nextCursor
                feedHasMore = feedBody.hasMore
                Log.i(TAG, "Successfully synced ${entities.size} valid videos from Vercel API")
            } else {
                Log.w(TAG, "Feed response empty or unsuccessful")
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
        val canonicalUrl = ZevoraApiClient.getCanonicalStreamUrl(videoId)
        if (existing != null) {
            dao.updateVideo(existing.copy(videoUrl = canonicalUrl))
        }
        canonicalUrl
    }

/**
     * Fetches the NEXT feed page with the stored server cursor plus the ids
     * already on device (seen), appends only genuinely new videos, and stores
     * the following cursor. This is what ends the same-first-page loop.
     */
    suspend fun loadMoreFeed(): Result<Int> = withContext(Dispatchers.IO) {
        if (feedPaging) return@withContext Result.success(0)
        if (!feedHasMore || feedCursor.isNullOrBlank()) return@withContext Result.success(0)
        feedPaging = true
        try {
            val seenIds = try {
                dao.getAllActiveVideosSync().map { it.id }.take(300)
            } catch (_: Exception) { emptyList() }
            val resp = try {
                ZevoraApiClient.api.getFeed(cursor = feedCursor, limit = 20, seen = seenIds.joinToString(","))
            } catch (e: Exception) {
                Log.w(TAG, "loadMoreFeed network error: ${e.message}")
                return@withContext Result.failure(e)
            }
            if (!resp.isSuccessful || resp.body() == null) {
                return@withContext Result.failure(Exception("Feed page failed (${resp.code()})"))
            }
            val body = resp.body()!!
            val fresh = body.videos.filter { apiVid ->
                val url = (apiVid.videoUrl ?: apiVid.streamUrl ?: "").trim()
                url.isNotBlank() &&
                    !url.contains("test.com") &&
                    !url.contains("example.com") &&
                    !url.startsWith("http://localhost") &&
                    (url.startsWith("http://") || url.startsWith("https://"))
            }
            val currentFeed = _feedVideos.value
            val existingIds = (seenIds + currentFeed.map { it.id }).toSet()
            val entities = fresh
                .filter { !existingIds.contains(it.id) }
                .map { apiVid -> apiVideoToEntity(apiVid) }
            if (entities.isNotEmpty()) {
                dao.insertVideos(entities)
                _feedVideos.value = currentFeed + entities
            }
            feedCursor = body.nextCursor
            feedHasMore = body.hasMore
            Log.i(TAG, "loadMoreFeed appended ${entities.size} new videos (hasMore=$feedHasMore)")
            Result.success(entities.size)
        } catch (e: Exception) {
            Log.e(TAG, "loadMoreFeed failed: ${e.message}", e)
            Result.failure(e)
        } finally {
            feedPaging = false
        }
    }

    /** Shared API-video -> Room-entity mapping (first page and appended pages). */
    private fun apiVideoToEntity(apiVid: ApiVideo): VideoEntity {
        val directStream = ZevoraApiClient.getCanonicalStreamUrl(apiVid.id)
        val url = (apiVid.videoUrl ?: apiVid.streamUrl ?: directStream).trim()
        val thumb = apiVid.thumbnailUrl?.takeIf { it.isNotBlank() && !it.contains("#t=") }
            ?: "${ZevoraApiClient.BASE_URL}api/v1/videos/${apiVid.id}/thumbnail"
        val isExt = apiVid.id.startsWith("pex_") ||
            apiVid.id.startsWith("cov_") ||
            apiVid.id.startsWith("pix_") ||
            apiVid.source?.lowercase() == "pexels" ||
            apiVid.provider?.lowercase() == "pexels"
        return VideoEntity(
            id = apiVid.id,
            creatorId = apiVid.creatorId,
            creatorUsername = apiVid.creatorUsername,
            creatorAvatar = apiVid.creatorAvatar ?: "",
            videoUrl = url,
            thumbnailUrl = thumb,
            caption = apiVid.caption,
            musicTitle = apiVid.musicTitle ?: "Original Audio",
            tags = if (isExt) "#licensed,#stock" else "#zevora,#fyp,#viral",
            likesCount = apiVid.likesCount,
            commentsCount = apiVid.commentsCount,
            sharesCount = apiVid.sharesCount,
            viewsCount = apiVid.viewsCount,
            source = apiVid.source ?: if (isExt) "licensed" else "zevora",
            provider = apiVid.provider ?: if (isExt) "licensed" else "zevora",
            isExternal = isExt,
            attributionUrl = apiVid.attributionUrl ?: "",
            photographerUrl = apiVid.photographerUrl ?: "",
            createdAt = if (apiVid.createdAt > 0) apiVid.createdAt else System.currentTimeMillis()
        )
    }

    suspend fun logout() = withContext(Dispatchers.IO) {
        try {
            ZevoraApiClient.api.logout()
        } catch (_: Exception) {
        }
        try {
            FacebookAuth.logout()
        } catch (_: Exception) {
        }
        sharedPrefs.edit().clear().apply()
        ZevoraApiClient.setAuthToken(null)
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
            val response = ZevoraApiClient.api.register(
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
                        ZevoraApiClient.setAuthToken(token)
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
                        avatarUrl = apiUser.avatarUrl ?: "",
                        bio = apiUser.bio ?: "",
                        followersCount = apiUser.followersCount ?: 0,
                        followingCount = apiUser.followingCount ?: 0,
                        totalLikes = 0,
                        role = (apiUser.role ?: "user").lowercase(),
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
            val response = ZevoraApiClient.api.login(
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
                        ZevoraApiClient.setAuthToken(token)
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
                        avatarUrl = apiUser.avatarUrl ?: "",
                        bio = apiUser.bio ?: "",
                        followersCount = apiUser.followersCount ?: 0,
                        followingCount = apiUser.followingCount ?: 0,
                        totalLikes = 0,
                        role = (apiUser.role ?: "user").lowercase(),
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

    // --- BACKEND AUTH: Google / Facebook / Phone / Recovery ---

    private fun backendError(errJson: String?, code: Int, fallback: String): String {
        return try {
            val msg = JSONObject(errJson ?: "").optString("error", "")
            if (msg.isNotBlank()) msg else "$fallback ($code)"
        } catch (_: Exception) {
            "$fallback ($code)"
        }
    }

    private suspend fun persistBackendSession(
        body: AuthResponse,
        displayFallback: String
    ): UserEntity? {
        val apiUser: ApiUser = body.user ?: return null
        val token = body.token
        if (!token.isNullOrBlank()) {
            ZevoraApiClient.setAuthToken(token)
            sharedPrefs.edit()
                .putString("auth_token", token)
                .putString("user_id", apiUser.id)
                .apply()
        }
        val userEntity = UserEntity(
            id = apiUser.id,
            username = apiUser.username,
            displayName = apiUser.displayName ?: displayFallback.ifBlank { apiUser.username },
            email = apiUser.email,
            passwordHash = "JWT_SECURED",
            avatarUrl = apiUser.avatarUrl
                ?: "",
            bio = apiUser.bio ?: "",
            followersCount = apiUser.followersCount ?: 0,
            followingCount = apiUser.followingCount ?: 0,
            totalLikes = 0,
            role = (apiUser.role ?: "user").lowercase(),
            status = "active",
            createdAt = System.currentTimeMillis()
        )
        dao.insertUser(userEntity)
        _currentUserId.value = userEntity.id
        _currentUser.value = userEntity
        return userEntity
    }

    suspend fun signInWithGoogleBackend(activity: Activity): Result<UserEntity> =
        withContext(Dispatchers.IO) {
            val idToken = GoogleAuth.getIdToken(activity).getOrElse {
                return@withContext Result.failure(it)
            }
            // 1. Sign in to Firebase Auth in parallel
            val fbResult = firebaseService.signInWithGoogleIdToken(idToken)

            // 2. Exchange token with backend server
            try {
                val response = ZevoraApiClient.api.oauthGoogle(OAuthGoogleRequest(idToken))
                val body = response.body()
                if (response.isSuccessful && body?.user != null) {
                    val user = persistBackendSession(body, "") ?: return@withContext Result.failure(
                        Exception("Google sign-in returned an empty profile.")
                    )
                    syncWithCloud()
                    return@withContext Result.success(user)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Backend Google OAuth API returned notice: ${e.message}")
            }

            // If backend exchange returned error/offline, but Firebase sign-in succeeded:
            if (fbResult.isSuccess) {
                val user = fbResult.getOrThrow()
                dao.insertUser(user)
                _currentUserId.value = user.id
                _currentUser.value = user
                syncWithCloud()
                return@withContext Result.success(user)
            }

            Result.failure(fbResult.exceptionOrNull() ?: Exception("Google sign-in failed."))
        }

    suspend fun signInWithFacebookBackend(activity: Activity): Result<UserEntity> =
        withContext(Dispatchers.IO) {
            val accessToken = FacebookAuth.login(activity).getOrElse {
                return@withContext Result.failure(it)
            }
            try {
                val response = ZevoraApiClient.api.oauthFacebook(OAuthFacebookRequest(accessToken))
                val body = response.body()
                if (response.isSuccessful && body?.user != null) {
                    val user = persistBackendSession(body, "") ?: return@withContext Result.failure(
                        Exception("Facebook sign-in returned an empty profile.")
                    )
                    syncWithCloud()
                    Result.success(user)
                } else {
                    Result.failure(
                        Exception(
                            backendError(
                                response.errorBody()?.string(),
                                response.code(),
                                "Facebook sign-in failed"
                            )
                        )
                    )
                }
            } catch (e: Exception) {
                Result.failure(Exception(e.message ?: "Facebook sign-in failed."))
            }
        }

    suspend fun requestPhoneOtp(phone: String): Result<OtpResponse> =
        withContext(Dispatchers.IO) {
            try {
                val response = ZevoraApiClient.api.phoneRequest(PhoneRequestBody(phone.trim()))
                val body = response.body()
                if (response.isSuccessful && body != null && body.sent) {
                    Result.success(body)
                } else {
                    Result.failure(
                        Exception(
                            body?.error
                                ?: backendError(
                                    response.errorBody()?.string(),
                                    response.code(),
                                    "Could not send the code"
                                )
                        )
                    )
                }
            } catch (e: Exception) {
                Result.failure(Exception(e.message ?: "Could not send the code."))
            }
        }

    suspend fun verifyPhoneOtp(phone: String, code: String, name: String? = null): Result<UserEntity> =
        withContext(Dispatchers.IO) {
            try {
                val response = ZevoraApiClient.api.phoneVerify(
                    PhoneVerifyRequest(phone.trim(), code.trim(), name?.trim()?.ifBlank { null })
                )
                val body = response.body()
                if ((response.isSuccessful || response.code() == 201) && body?.user != null) {
                    val user = persistBackendSession(body, name ?: "") ?: return@withContext Result.failure(
                        Exception("Phone verification returned an empty profile.")
                    )
                    syncWithCloud()
                    Result.success(user)
                } else {
                    Result.failure(
                        Exception(
                            backendError(
                                response.errorBody()?.string(),
                                response.code(),
                                "Verification failed"
                            )
                        )
                    )
                }
            } catch (e: Exception) {
                Result.failure(Exception(e.message ?: "Verification failed."))
            }
        }

    fun sendFirebasePhoneOtp(
        activity: Activity,
        phone: String,
        onCodeSent: (verificationId: String) -> Unit,
        onAutoVerified: (UserEntity) -> Unit,
        onError: (String) -> Unit
    ) {
        if (!firebaseService.isFirebaseAvailable) {
            onError("Firebase is not initialized")
            return
        }
        firebaseService.sendPhoneOtp(
            activity = activity,
            phoneNumber = phone,
            onCodeSent = { verificationId, _ ->
                onCodeSent(verificationId)
            },
            onVerificationCompleted = { credential ->
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val res = firebaseService.signInWithCredential(credential)
                        if (res.isSuccess) {
                            val userEntity = res.getOrThrow()
                            dao.insertUser(userEntity)
                            _currentUserId.value = userEntity.id
                            _currentUser.value = userEntity
                            withContext(Dispatchers.Main) { onAutoVerified(userEntity) }
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Auto-verification error: ${e.message}")
                    }
                }
            },
            onVerificationFailed = { e ->
                onError(e.message ?: "SMS verification failed")
            }
        )
    }

    suspend fun verifyFirebasePhoneOtp(
        verificationId: String,
        code: String,
        phone: String,
        name: String? = null
    ): Result<UserEntity> = withContext(Dispatchers.IO) {
        val fbResult = firebaseService.verifyPhoneCredential(verificationId, code, name)
        if (fbResult.isSuccess) {
            val user = fbResult.getOrThrow()
            dao.insertUser(user)
            _currentUserId.value = user.id
            _currentUser.value = user
            try {
                ZevoraApiClient.api.phoneVerify(
                    PhoneVerifyRequest(phone.trim(), code.trim(), name?.trim()?.ifBlank { null })
                )
            } catch (_: Exception) {}
            syncWithCloud()
            return@withContext Result.success(user)
        }
        // Fallback to backend verify
        try {
            val response = ZevoraApiClient.api.phoneVerify(
                PhoneVerifyRequest(phone.trim(), code.trim(), name?.trim()?.ifBlank { null })
            )
            val body = response.body()
            if ((response.isSuccessful || response.code() == 201) && body?.user != null) {
                val user = persistBackendSession(body, name ?: "") ?: return@withContext Result.failure(
                    Exception("Phone verification returned an empty profile.")
                )
                syncWithCloud()
                return@withContext Result.success(user)
            }
        } catch (_: Exception) {}
        fbResult
    }

    suspend fun requestRecoveryOtp(phone: String): Result<OtpResponse> =
        withContext(Dispatchers.IO) {
            try {
                val response = ZevoraApiClient.api.recoverRequest(RecoverRequestBody(phone.trim()))
                val body = response.body()
                if (response.isSuccessful && body != null && body.sent) {
                    Result.success(body)
                } else {
                    Result.failure(
                        Exception(
                            backendError(
                                response.errorBody()?.string(),
                                response.code(),
                                "Could not send the recovery code"
                            )
                        )
                    )
                }
            } catch (e: Exception) {
                Result.failure(Exception(e.message ?: "Could not send the recovery code."))
            }
        }

    suspend fun confirmRecovery(
        phone: String,
        code: String,
        newPassword: String? = null
    ): Result<UserEntity> =
        withContext(Dispatchers.IO) {
            try {
                val response = ZevoraApiClient.api.recoverConfirm(
                    RecoverConfirmRequest(phone.trim(), code.trim(), newPassword)
                )
                val body = response.body()
                if (response.isSuccessful && body?.user != null) {
                    val user = persistBackendSession(body, "") ?: return@withContext Result.failure(
                        Exception("Recovery returned an empty profile.")
                    )
                    syncWithCloud()
                    Result.success(user)
                } else {
                    Result.failure(
                        Exception(
                            backendError(
                                response.errorBody()?.string(),
                                response.code(),
                                "Recovery failed"
                            )
                        )
                    )
                }
            } catch (e: Exception) {
                Result.failure(Exception(e.message ?: "Recovery failed."))
            }
        }

    suspend fun getLinkedProviders(): Result<List<LinkedProvider>> =
        withContext(Dispatchers.IO) {
            try {
                val response = ZevoraApiClient.api.linkedProviders()
                if (response.isSuccessful && response.body() != null) {
                    Result.success(response.body()!!.providers)
                } else {
                    Result.failure(
                        Exception(
                            backendError(
                                response.errorBody()?.string(),
                                response.code(),
                                "Could not load linked accounts"
                            )
                        )
                    )
                }
            } catch (e: Exception) {
                Result.failure(Exception(e.message ?: "Could not load linked accounts."))
            }
        }

    suspend fun linkGoogleToMyAccount(activity: Activity): Result<String> =
        withContext(Dispatchers.IO) {
            val idToken = GoogleAuth.getIdToken(activity).getOrElse {
                return@withContext Result.failure(it)
            }
            try {
                val response = ZevoraApiClient.api.linkProvider(
                    LinkProviderRequest(provider = "google", idToken = idToken)
                )
                if (response.isSuccessful && response.body()?.linked == true) {
                    Result.success("google")
                } else {
                    Result.failure(
                        Exception(
                            response.body()?.error
                                ?: backendError(
                                    response.errorBody()?.string(),
                                    response.code(),
                                    "Could not link Google"
                                )
                        )
                    )
                }
            } catch (e: Exception) {
                Result.failure(Exception(e.message ?: "Could not link Google."))
            }
        }

    suspend fun linkFacebookToMyAccount(activity: Activity): Result<String> =
        withContext(Dispatchers.IO) {
            val accessToken = FacebookAuth.login(activity).getOrElse {
                return@withContext Result.failure(it)
            }
            try {
                val response = ZevoraApiClient.api.linkProvider(
                    LinkProviderRequest(provider = "facebook", accessToken = accessToken)
                )
                if (response.isSuccessful && response.body()?.linked == true) {
                    Result.success("facebook")
                } else {
                    Result.failure(
                        Exception(
                            response.body()?.error
                                ?: backendError(
                                    response.errorBody()?.string(),
                                    response.code(),
                                    "Could not link Facebook"
                                )
                        )
                    )
                }
            } catch (e: Exception) {
                Result.failure(Exception(e.message ?: "Could not link Facebook."))
            }
        }

    // --- LIVE SERVER DATA: notifications / sessions / admin ---

    /**
     * Pulls real notifications from the ZEVORA API into the local inbox.
     * Actor profiles are resolved best-effort (cache first, profile API
     * second) so rows always show genuine usernames/avatars.
     */
    suspend fun syncRemoteNotifications(): Result<Int> = withContext(Dispatchers.IO) {
        val me = _currentUser.value?.id ?: return@withContext Result.failure(
            Exception("Sign in to load notifications.")
        )
        try {
            val response = ZevoraApiClient.api.getNotifications(me, 1, 30)
            val body = response.body()
            if (!response.isSuccessful || body == null) {
                return@withContext Result.failure(
                    Exception(backendError(response.errorBody()?.string(), response.code(), "Inbox unavailable"))
                )
            }
            val entities = body.notifications.map { n ->
                var actorName: String? = null
                var actorAvatar: String? = null
                try {
                    val cached = dao.getUserByIdSync(n.actorId)
                    if (cached != null) {
                        actorName = cached.username
                        actorAvatar = cached.avatarUrl
                    } else {
                        val profile = ZevoraApiClient.api.getUserProfile(n.actorId)
                        val remote = profile.body()?.user
                        if (profile.isSuccessful && remote != null) {
                            actorName = remote.username
                            actorAvatar = remote.avatarUrl
                            dao.insertUser(
                                UserEntity(
                                    id = remote.id,
                                    username = remote.username,
                                    displayName = remote.displayName ?: remote.username,
                                    email = "",
                                    passwordHash = "REMOTE",
                                    avatarUrl = remote.avatarUrl
                                        ?: "",
                                    bio = remote.bio ?: "",
                                    followersCount = remote.followersCount,
                                    followingCount = remote.followingCount,
                                    totalLikes = remote.likesReceived,
                                    role = "user",
                                    status = "active",
                                    createdAt = System.currentTimeMillis()
                                )
                            )
                        }
                    }
                } catch (_: Exception) {
                }
                NotificationEntity(
                    id = n.id,
                    userId = me,
                    actorId = n.actorId,
                    actorUsername = actorName ?: "user_${n.actorId.take(6)}",
                    actorAvatar = actorAvatar
                        ?: "",
                    type = n.type,
                    message = n.message,
                    videoId = n.referenceId,
                    isRead = n.isRead,
                    createdAt = if (n.createdAt > 0) n.createdAt else System.currentTimeMillis()
                )
            }
            if (entities.isNotEmpty()) dao.insertNotifications(entities)
            Result.success(body.unreadCount)
        } catch (e: Exception) {
            Result.failure(Exception(e.message ?: "Inbox unavailable."))
        }
    }

    suspend fun markRemoteNotificationsRead(): Result<Int> = withContext(Dispatchers.IO) {
        val me = _currentUser.value?.id ?: return@withContext Result.failure(
            Exception("Sign in first.")
        )
        return@withContext try {
            val response = ZevoraApiClient.api.readNotifications(me, NotificationsReadRequest())
            dao.markNotificationsAsRead(me)
            Result.success(response.body()?.marked ?: 0)
        } catch (e: Exception) {
            try {
                dao.markNotificationsAsRead(me)
            } catch (_: Exception) {
            }
            Result.failure(Exception(e.message ?: "Could not sync read state."))
        }
    }

    suspend fun listMySessions(): Result<List<ApiSession>> = withContext(Dispatchers.IO) {
        try {
            val response = ZevoraApiClient.api.listSessions()
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!.sessions)
            } else {
                Result.failure(
                    Exception(
                        backendError(response.errorBody()?.string(), response.code(), "Sessions unavailable")
                    )
                )
            }
        } catch (e: Exception) {
            Result.failure(Exception(e.message ?: "Sessions unavailable."))
        }
    }

    suspend fun revokeMySession(sessionId: String): Result<Boolean> =
        withContext(Dispatchers.IO) {
            try {
                val response = ZevoraApiClient.api.revokeSession(sessionId)
                if (response.isSuccessful && response.body()?.revoked == true) {
                    Result.success(true)
                } else {
                    Result.failure(
                        Exception(
                            backendError(response.errorBody()?.string(), response.code(), "Could not revoke session")
                        )
                    )
                }
            } catch (e: Exception) {
                Result.failure(Exception(e.message ?: "Could not revoke session."))
            }
        }

    suspend fun getAdminLogins(page: Int = 1): Result<LoginRecordsResponse> =
        withContext(Dispatchers.IO) {
            try {
                val response = ZevoraApiClient.api.adminLogins(page, 20)
                if (response.isSuccessful && response.body() != null) {
                    Result.success(response.body()!!)
                } else {
                    Result.failure(
                        Exception(
                            backendError(response.errorBody()?.string(), response.code(), "Login records unavailable")
                        )
                    )
                }
            } catch (e: Exception) {
                Result.failure(Exception(e.message ?: "Login records unavailable."))
            }
        }

    suspend fun getAdminReports(status: String? = null, page: Int = 1): Result<AdminReportsResponse> =
        withContext(Dispatchers.IO) {
            try {
                val response = ZevoraApiClient.api.adminReports(status, page, 20)
                if (response.isSuccessful && response.body() != null) {
                    Result.success(response.body()!!)
                } else {
                    Result.failure(
                        Exception(
                            backendError(response.errorBody()?.string(), response.code(), "Reports unavailable")
                        )
                    )
                }
            } catch (e: Exception) {
                Result.failure(Exception(e.message ?: "Reports unavailable."))
            }
        }

    suspend fun getAdminOverview(): Result<AdminOverviewResponse> =
        withContext(Dispatchers.IO) {
            try {
                val response = ZevoraApiClient.api.adminOverview()
                if (response.isSuccessful && response.body() != null) {
                    Result.success(response.body()!!)
                } else {
                    Result.failure(
                        Exception(
                            backendError(response.errorBody()?.string(), response.code(), "Overview unavailable")
                        )
                    )
                }
            } catch (e: Exception) {
                Result.failure(Exception(e.message ?: "Overview unavailable."))
            }
        }

    suspend fun resolveAdminReport(reportId: String, action: String): Result<ResolveReportResponse> =
        withContext(Dispatchers.IO) {
            try {
                val response = ZevoraApiClient.api.resolveReport(reportId, ResolveReportRequest(action))
                if (response.isSuccessful && response.body() != null) {
                    Result.success(response.body()!!)
                } else {
                    Result.failure(
                        Exception(
                            backendError(response.errorBody()?.string(), response.code(), "Could not resolve report")
                        )
                    )
                }
            } catch (e: Exception) {
                Result.failure(Exception(e.message ?: "Could not resolve report."))
            }
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
            ZevoraApiClient.api.toggleLike(videoId, LikeRequest(user.id))
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
            ZevoraApiClient.api.recordView(videoId)
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
            ZevoraApiClient.api.recordShare(videoId, ShareRequest(userId = user?.id))
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
            ZevoraApiClient.api.addComment(videoId, AddCommentRequest(user.id, text))
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
            ZevoraApiClient.api.deleteComment(videoId, commentId)
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
                    message = "started following you on ZEVORA!"
                )
            )
            true
        }
    }

    // --- VIDEO UPLOAD PIPELINE ---

    /**
     * Real upload: ticket -> PUT bytes -> complete. Returns failure (never a
     * phantom local video) unless the server actually stored the publish, and
     * binds the EXACT server record returned by complete-upload.
     */
    suspend fun uploadVideo(
        videoUrl: String,
        caption: String,
        tags: String,
        musicTitle: String,
        videoUri: Uri? = null,
        onProgress: ((Float) -> Unit)? = null
    ): Result<VideoEntity> = withContext(Dispatchers.IO) {
        val user = _currentUser.value
        val creatorId = user?.id ?: "creator_guest"
        val creatorName = user?.username ?: "creator"
        val creatorAvatar = user?.avatarUrl ?: ""

        try {
            // 1. Upload ticket: the server mints the videoId + storage keys.
            val ticketResp = try {
                ZevoraApiClient.api.requestUploadUrl(
                    UploadTicketRequest(
                        filename = "video_${System.currentTimeMillis()}.mp4",
                        contentType = "video/mp4",
                        userId = creatorId
                    )
                )
            } catch (e: Exception) {
                return@withContext Result.failure(Exception("Could not reach the upload server: ${e.message}"))
            }
            if (!ticketResp.isSuccessful || ticketResp.body() == null) {
                return@withContext Result.failure(
                    Exception(backendError(ticketResp.errorBody()?.string(), ticketResp.code(), "Upload ticket rejected"))
                )
            }
            val ticket = ticketResp.body()!!
            val videoId = ticket.videoId
            var finalVideoUrl = ticket.streamUrl
            var objectKey: String? = ticket.objectKey
            val realThumbUrl: String? = ticket.thumbnailUrl

            if (videoUri != null) {
                // 2. PUT the actual bytes (streamed, never fully in memory).
                onProgress?.invoke(0.10f)
                val uploadSuccess = uploadBinaryToUrl(ticket.uploadUrl, videoUri, onProgress)
                if (!uploadSuccess) {
                    // Firebase fallback hosts bytes outside S3: send NO objectKey so the
                    // server verifies the Firebase URL itself instead of a missing S3 key.
                    if (!firebaseService.isFirebaseAvailable) {
                        return@withContext Result.failure(Exception("Video upload to storage failed. Check your connection and retry."))
                    }
                    val fbRes = firebaseService.uploadVideoToStorage(videoUri, videoId, onProgress)
                    if (fbRes.isFailure) {
                        return@withContext Result.failure(Exception("Video upload to storage failed: ${fbRes.exceptionOrNull()?.message}"))
                    }
                    finalVideoUrl = fbRes.getOrThrow()
                    objectKey = null
                }

                // Extract REAL video thumbnail frame (best effort, never fatal).
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
            } else {
                // No device file: only an externally-hosted https file can publish.
                if (videoUrl.startsWith("https://")) {
                    finalVideoUrl = videoUrl
                    objectKey = null
                } else {
                    return@withContext Result.failure(Exception("Select a video file from your device first."))
                }
            }

            // 3. Complete in Postgres — the response carries the EXACT stored record.
            val completeResp = try {
                ZevoraApiClient.api.completeUpload(
                    CompleteUploadRequest(
                        videoId = videoId,
                        userId = creatorId,
                        caption = caption,
                        videoUrl = finalVideoUrl,
                        thumbnailUrl = realThumbUrl ?: "${ZevoraApiClient.BASE_URL}api/v1/videos/$videoId/thumbnail",
                        musicTitle = musicTitle,
                        aspectRatio = "9:16",
                        objectKey = objectKey
                    )
                )
            } catch (e: Exception) {
                return@withContext Result.failure(Exception("Publish failed: ${e.message}"))
            }
            if (!completeResp.isSuccessful || completeResp.body()?.video == null) {
                return@withContext Result.failure(
                    Exception(backendError(completeResp.errorBody()?.string(), completeResp.code(), "Publish rejected by server"))
                )
            }
            val serverVideo = completeResp.body()!!.video!!

            val newVideo = VideoEntity(
                id = serverVideo.id,
                creatorId = serverVideo.creatorId,
                creatorUsername = serverVideo.creatorUsername,
                creatorAvatar = serverVideo.creatorAvatar ?: creatorAvatar,
                videoUrl = (serverVideo.videoUrl ?: serverVideo.streamUrl ?: finalVideoUrl).trim(),
                thumbnailUrl = serverVideo.thumbnailUrl?.takeIf { it.isNotBlank() && !it.contains("#t=") }
                    ?: "${ZevoraApiClient.BASE_URL}api/v1/videos/${serverVideo.id}/thumbnail",
                caption = serverVideo.caption,
                musicTitle = serverVideo.musicTitle ?: musicTitle.ifBlank { "Original Sound - $creatorName" },
                tags = tags,
                likesCount = serverVideo.likesCount,
                commentsCount = serverVideo.commentsCount,
                sharesCount = serverVideo.sharesCount,
                viewsCount = serverVideo.viewsCount,
                createdAt = if (serverVideo.createdAt > 0) serverVideo.createdAt else System.currentTimeMillis()
            )

            dao.insertVideo(newVideo)
            _feedVideos.value = listOf(newVideo) + _feedVideos.value.filter { it.id != newVideo.id }
            if (firebaseService.isFirebaseAvailable) {
                try { firebaseService.publishVideoToFirestore(newVideo) } catch (e: Exception) {}
            }
            Result.success(newVideo)
        } catch (e: Exception) {
            Log.e(TAG, "uploadVideo failed", e)
            Result.failure(Exception(e.message ?: "Upload failed"))
        }
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

    /**
     * Streams the file to the presigned PUT URL in 256KB chunks. Never loads
     * the whole video into memory (large files used to OOM and report errors).
     */
    private fun uploadBinaryToUrl(uploadUrl: String, videoUri: Uri, onProgress: ((Float) -> Unit)?): Boolean {
        var tempFile: java.io.File? = null
        return try {
            var total = try {
                context.contentResolver.openAssetFileDescriptor(videoUri, "r")?.use { it.length } ?: -1L
            } catch (_: Exception) { -1L }
            if (total <= 0L) {
                total = try {
                    context.contentResolver.openFileDescriptor(videoUri, "r")?.use { it.statSize } ?: -1L
                } catch (_: Exception) { -1L }
            }
            var streamUri = videoUri
            if (total <= 0L) {
                try {
                    val file = java.io.File(context.cacheDir, "up_${System.currentTimeMillis()}.mp4")
                    context.contentResolver.openInputStream(videoUri)?.use { input ->
                        file.outputStream().use { out -> input.copyTo(out) }
                    }
                    if (file.exists() && file.length() > 0) {
                        total = file.length()
                        streamUri = Uri.fromFile(file)
                        tempFile = file
                    }
                } catch (_: Exception) {}
            }

            val client = OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(10, TimeUnit.MINUTES)
                .readTimeout(60, TimeUnit.SECONDS)
                .build()
            val requestBody = object : RequestBody() {
                override fun contentType() = "video/mp4".toMediaTypeOrNull()
                override fun contentLength() = total
                override fun writeTo(sink: BufferedSink) {
                    val input = if (streamUri.scheme == "file") {
                        java.io.FileInputStream(java.io.File(streamUri.path ?: ""))
                    } else {
                        context.contentResolver.openInputStream(streamUri)
                    } ?: throw IOException("Cannot open video file")
                    input.use { stream ->
                        val buffer = ByteArray(256 * 1024)
                        var written = 0L
                        while (true) {
                            val read = stream.read(buffer)
                            if (read == -1) break
                            sink.write(buffer, 0, read)
                            written += read
                            if (total > 0) {
                                onProgress?.invoke((0.10f + 0.85f * written.toFloat() / total).coerceIn(0f, 1f))
                            }
                        }
                    }
                }
            }

            val request = Request.Builder()
                .url(uploadUrl)
                .put(requestBody)
                .build()

            client.newCall(request).execute().use { response -> response.isSuccessful }
        } catch (e: Exception) {
            Log.w(TAG, "uploadBinaryToUrl error: ${e.message}")
            false
        } finally {
            tempFile?.delete()
        }
    }

    // --- SEARCH & PROFILES ---

    fun searchVideos(query: String): Flow<List<VideoEntity>> = dao.searchVideos(query)

    fun searchUsers(query: String): Flow<List<UserEntity>> = dao.searchUsers(query)

    suspend fun searchRemote(query: String) = withContext(Dispatchers.IO) {
        val clean = query.trim()
        if (clean.isBlank()) return@withContext
        try {
            val response = ZevoraApiClient.api.search(clean)
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
                            tags = "#zevora,#fyp",
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
                            email = "",
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
        avatarUri: Uri? = null,
        avatarUrlOverride: String? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val user = _currentUser.value ?: return@withContext Result.failure(Exception("Log in first"))
        var avatarUrl = avatarUrlOverride?.takeIf { it.isNotBlank() } ?: user.avatarUrl

        if (avatarUri != null && firebaseService.isFirebaseAvailable) {
            val storageResult = firebaseService.uploadAvatarToStorage(avatarUri, user.id)
            if (storageResult.isSuccess) {
                avatarUrl = storageResult.getOrThrow()
            }
        }

        // Server first: the database owns username uniqueness + the canonical copy.
        var serverOk = false
        try {
            val resp = ZevoraApiClient.api.updateUser(
                user.id,
                UpdateUserRequest(
                    username = username.trim().takeIf { it.isNotBlank() },
                    displayName = displayName.trim().takeIf { it.isNotBlank() },
                    bio = bio.trim(),
                    avatarUrl = avatarUrl.takeIf { it.isNotBlank() && it.startsWith("http") }
                )
            )
            if (resp.isSuccessful && resp.body()?.user != null) {
                serverOk = true
            } else if (resp.code() == 409) {
                return@withContext Result.failure(Exception("That username is already taken"))
            }
        } catch (e: Exception) {
            Log.w(TAG, "Profile server sync failed, keeping local edit: ${e.message}")
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
        if (!serverOk) {
            Log.w(TAG, "Profile saved on device; server sync pending")
        }
        Result.success(Unit)
    }

    /** Change the account password (server verifies the current one). */
    suspend fun changePassword(currentPassword: String, newPassword: String): Result<String> =
        withContext(Dispatchers.IO) {
            if (newPassword.length < 6 || newPassword.length > 100) {
                return@withContext Result.failure(Exception("New password must be 6-100 characters"))
            }
            try {
                val resp = ZevoraApiClient.api.changePassword(ChangePasswordRequest(currentPassword, newPassword))
                if (resp.isSuccessful && resp.body()?.changed == true) {
                    Result.success("Password changed. Other devices were signed out.")
                } else {
                    Result.failure(Exception(backendError(resp.errorBody()?.string(), resp.code(), "Password change failed")))
                }
            } catch (e: Exception) {
                Result.failure(Exception(e.message ?: "Password change failed"))
            }
        }

    /** Permanently delete the account server-side, then wipe the device session. */
    suspend fun deleteAccount(): Result<String> = withContext(Dispatchers.IO) {
        val user = _currentUser.value ?: return@withContext Result.failure(Exception("Log in first"))
        try {
            val resp = ZevoraApiClient.api.deleteUser(user.id)
            if (!resp.isSuccessful) {
                return@withContext Result.failure(
                    Exception(backendError(resp.errorBody()?.string(), resp.code(), "Account deletion failed"))
                )
            }
            val removed = resp.body()?.videosRemoved ?: 0
            try { dao.deleteUser(user.id) } catch (_: Exception) {}
            logout()
            Result.success("Your account and $removed video(s) were permanently deleted.")
        } catch (e: Exception) {
            Result.failure(Exception(e.message ?: "Account deletion failed"))
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
            val resp = ZevoraApiClient.api.deleteVideo(videoId)
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

    /** Real reset entry-point: phone numbers trigger an SMS/OTP recovery code. */
    suspend fun resetPassword(account: String): Result<OtpResponse> = withContext(Dispatchers.IO) {
        val clean = account.trim()
        if (clean.startsWith("+")) {
            return@withContext requestRecoveryOtp(clean)
        }
        Result.failure(Exception("Password reset uses your verified phone number. Enter it in +213… format."))
    }
}
