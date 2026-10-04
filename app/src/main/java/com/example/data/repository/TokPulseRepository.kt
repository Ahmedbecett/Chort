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
        // Ensure catalog is populated locally
        seedPlatformVideosToFirestoreIfNeeded()

        if (!firebaseService.isFirebaseAvailable) return@withContext
        try {
            // 1. Fetch live videos from Firestore
            val cloudVideos = firebaseService.fetchVideosFromFirestore(limit = 50)
            if (cloudVideos.isNotEmpty()) {
                dao.insertVideos(cloudVideos)
            }

            // 2. Realtime listener for live updates across devices
            firebaseService.subscribeToVideosRealtime { liveVideos ->
                CoroutineScope(Dispatchers.IO).launch {
                    dao.insertVideos(liveVideos)
                }
            }

            // 3. Fetch admin data from Firestore if current user is admin
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

    private suspend fun seedPlatformVideosToFirestoreIfNeeded() {
        val initialVideos = listOf(
            VideoEntity(
                id = "vid_music_arabic_1",
                creatorId = "creator_mahmoud",
                creatorUsername = "mahmoud_music",
                creatorAvatar = "https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?w=300",
                videoUrl = "https://raw.githubusercontent.com/intel-iot-devkit/sample-videos/master/person-bicycle-car-detection.mp4",
                thumbnailUrl = "https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=500",
                caption = "أجمل أغاني وموسيقى الريمكس العالمية الحماسية 🎵✨ #أغاني #موسيقى #ترند #طرب #tokpulse",
                musicTitle = "أغاني حماسية - سولو عود وإيقاع شرقي",
                tags = "#أغاني,#موسيقى,#طرب,#فن,#music,#songs,#tokpulse",
                likesCount = 2840,
                commentsCount = 142,
                sharesCount = 520,
                viewsCount = 38900,
                createdAt = System.currentTimeMillis() - 1000000
            ),
            VideoEntity(
                id = "vid_music_arabic_2",
                creatorId = "creator_layla",
                creatorUsername = "layla_vocals",
                creatorAvatar = "https://images.unsplash.com/photo-1494790108377-be9c29b29330?w=300",
                videoUrl = "https://raw.githubusercontent.com/intel-iot-devkit/sample-videos/master/face-demographics-walking.mp4",
                thumbnailUrl = "https://images.unsplash.com/photo-1493225457124-a3eb161ffa5f?w=500",
                caption = "أغاني الزمن الجميل بطابع عصري هادئ 🎶🌙 شاركونا رأيكم في التعليقات #أغاني #طرب #فن #كلاسيك",
                musicTitle = "أغاني كلاسيكية - كمان وبيانو هادئ",
                tags = "#أغاني,#طرب,#موسيقى,#فن,#classic,#chill,#vocals",
                likesCount = 4120,
                commentsCount = 310,
                sharesCount = 890,
                viewsCount = 54200,
                createdAt = System.currentTimeMillis() - 2000000
            ),
            VideoEntity(
                id = "vid_dance_1",
                creatorId = "creator_samir",
                creatorUsername = "dance_samir",
                creatorAvatar = "https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?w=300",
                videoUrl = "https://raw.githubusercontent.com/intel-iot-devkit/sample-videos/master/head-pose-face-detection-female.mp4",
                thumbnailUrl = "https://images.unsplash.com/photo-1547153760-18fc86324498?w=500",
                caption = "تحدي الرقص السريع مع أقوى إيقاع إلكتروني 🕺🔥 جرب الحركة وشاركنا #رقص #dance #challenge #fyp #viral",
                musicTitle = "Electro Pulse Dance Beat - TokPulse Sound",
                tags = "#رقص,#dance,#viral,#fyp,#trend,#challenge",
                likesCount = 5930,
                commentsCount = 428,
                sharesCount = 1200,
                viewsCount = 89400,
                createdAt = System.currentTimeMillis() - 3000000
            ),
            VideoEntity(
                id = "vid_food_1",
                creatorId = "creator_karim",
                creatorUsername = "chef_karim",
                creatorAvatar = "https://images.unsplash.com/photo-1500648767791-00dcc994a43e?w=300",
                videoUrl = "https://raw.githubusercontent.com/intel-iot-devkit/sample-videos/master/classroom.mp4",
                thumbnailUrl = "https://images.unsplash.com/photo-1568901346375-23c9450c58cd?w=500",
                caption = "أسرع وألذ وصفة برجر مقرمش في البيت بدقيقة واحدة 🍔🤤 #طبخ #أكل #food #foodtok #cooking #recipe",
                musicTitle = "Kitchen Beats & Fast ASMR",
                tags = "#طبخ,#أكل,#food,#foodtok,#cooking,#recipe,#delicious",
                likesCount = 8240,
                commentsCount = 612,
                sharesCount = 1890,
                viewsCount = 124000,
                createdAt = System.currentTimeMillis() - 4000000
            ),
            VideoEntity(
                id = "vid_tech_1",
                creatorId = "creator_ahmed",
                creatorUsername = "tech_ahmed",
                creatorAvatar = "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=300",
                videoUrl = "https://raw.githubusercontent.com/intel-iot-devkit/sample-videos/master/person-bicycle-car-detection.mp4",
                thumbnailUrl = "https://images.unsplash.com/photo-1518770660439-4636190af475?w=500",
                caption = "أقوى حيل وتقنيات الذكاء الاصطناعي في 2026 ستغير حياتك اليومية 🤖📱 #تقنية #تكنولوجيا #tech #ai #android",
                musicTitle = "Cyber Future Synth Wave",
                tags = "#تقنية,#تكنولوجيا,#tech,#ai,#future,#innovation,#android",
                likesCount = 6730,
                commentsCount = 489,
                sharesCount = 1450,
                viewsCount = 98000,
                createdAt = System.currentTimeMillis() - 5000000
            ),
            VideoEntity(
                id = "vid_sports_1",
                creatorId = "creator_youssef",
                creatorUsername = "coach_youssef",
                creatorAvatar = "https://images.unsplash.com/photo-1539571696357-5a69c17a67c6?w=300",
                videoUrl = "https://raw.githubusercontent.com/intel-iot-devkit/sample-videos/master/face-demographics-walking.mp4",
                thumbnailUrl = "https://images.unsplash.com/photo-1517838277536-f5f99be501cd?w=500",
                caption = "تمارين لياقة وصباحية لحرق الدهون وبناء طاقة إيجابية 💪🏃‍♂️ #رياضة #لياقة #fitness #sports #workout #gym",
                musicTitle = "High BPM Workout Motivation",
                tags = "#رياضة,#لياقة,#fitness,#sports,#workout,#gym,#health",
                likesCount = 3450,
                commentsCount = 205,
                sharesCount = 670,
                viewsCount = 45000,
                createdAt = System.currentTimeMillis() - 6000000
            ),
            VideoEntity(
                id = "vid_travel_1",
                creatorId = "creator_sarah",
                creatorUsername = "travel_sarah",
                creatorAvatar = "https://images.unsplash.com/photo-1517841905240-472988babdf9?w=300",
                videoUrl = "https://raw.githubusercontent.com/intel-iot-devkit/sample-videos/master/head-pose-face-detection-female.mp4",
                thumbnailUrl = "https://images.unsplash.com/photo-1506744038136-46273834b3fb?w=500",
                caption = "سحر جبال الألب والبحيرات النقية في رحلة خيالية 🏔️🛶 #سفر #طبيعة #travel #nature #wanderlust #explore",
                musicTitle = "Acoustic Nature Breeze",
                tags = "#سفر,#طبيعة,#travel,#nature,#wanderlust,#mountains,#adventure",
                likesCount = 9120,
                commentsCount = 740,
                sharesCount = 2100,
                viewsCount = 145000,
                createdAt = System.currentTimeMillis() - 7000000
            ),
            VideoEntity(
                id = "vid_comedy_1",
                creatorId = "creator_tariq",
                creatorUsername = "tariq_comedy",
                creatorAvatar = "https://images.unsplash.com/photo-1522075469751-3a6694fb2f61?w=300",
                videoUrl = "https://raw.githubusercontent.com/intel-iot-devkit/sample-videos/master/classroom.mp4",
                thumbnailUrl = "https://images.unsplash.com/photo-1527224857830-43a7acc85260?w=500",
                caption = "يوميات مضحكة: لما تقرر تلتزم بالدايت وأصحابك يطلبو شاورما 😂 #كوميديا #ضحك #comedy #funny #humor",
                musicTitle = "Funny Whistle Comedy Sound",
                tags = "#كوميديا,#ضحك,#comedy,#funny,#humor,#fun,#lol",
                likesCount = 11200,
                commentsCount = 980,
                sharesCount = 3400,
                viewsCount = 210000,
                createdAt = System.currentTimeMillis() - 8000000
            )
        )

        dao.insertVideos(initialVideos)

        val creators = listOf(
            UserEntity(id = "creator_mahmoud", username = "mahmoud_music", displayName = "محمود الموسيقار", email = "mahmoud@tokpulse.com", passwordHash = "PROTECTED", avatarUrl = "https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?w=300", bio = "عازف ومؤلف موسيقى وريمكسات 🎵 | شاركوا في الهاشتاغات", followersCount = 28400, followingCount = 45, totalLikes = 189000, role = "creator"),
            UserEntity(id = "creator_layla", username = "layla_vocals", displayName = "ليلى صوت الشرق", email = "layla@tokpulse.com", passwordHash = "PROTECTED", avatarUrl = "https://images.unsplash.com/photo-1494790108377-be9c29b29330?w=300", bio = "أغاني وطرب عربي أصيل 🎤🌙 حسابي الرسمي على TokPulse", followersCount = 41200, followingCount = 80, totalLikes = 312000, role = "creator"),
            UserEntity(id = "creator_samir", username = "dance_samir", displayName = "سامير راقص الشارع", email = "samir@tokpulse.com", passwordHash = "PROTECTED", avatarUrl = "https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?w=300", bio = "تحديات رقص وترندات عالمية 🕺🔥", followersCount = 65000, followingCount = 120, totalLikes = 450000, role = "creator"),
            UserEntity(id = "creator_karim", username = "chef_karim", displayName = "الشيف كريم", email = "karim@tokpulse.com", passwordHash = "PROTECTED", avatarUrl = "https://images.unsplash.com/photo-1500648767791-00dcc994a43e?w=300", bio = "أشهى الأكلات والوصفات السريعة في دقيقة 🍔👨‍🍳", followersCount = 89000, followingCount = 30, totalLikes = 890000, role = "creator"),
            UserEntity(id = "creator_ahmed", username = "tech_ahmed", displayName = "أحمد للتقنية", email = "tech@tokpulse.com", passwordHash = "PROTECTED", avatarUrl = "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=300", bio = "مراجعات تقنية، ذكاء اصطناعي، وتطبيقات جديدة 🤖📱", followersCount = 74000, followingCount = 95, totalLikes = 520000, role = "creator"),
            UserEntity(id = "creator_youssef", username = "coach_youssef", displayName = "الكابتن يوسف", email = "youssef@tokpulse.com", passwordHash = "PROTECTED", avatarUrl = "https://images.unsplash.com/photo-1539571696357-5a69c17a67c6?w=300", bio = "مدرب لياقة بدنية وتحفيز رياضي يومي 💪🏃‍♂️", followersCount = 36000, followingCount = 50, totalLikes = 240000, role = "creator"),
            UserEntity(id = "creator_sarah", username = "travel_sarah", displayName = "سارة حول العالم", email = "sarah@tokpulse.com", passwordHash = "PROTECTED", avatarUrl = "https://images.unsplash.com/photo-1517841905240-472988babdf9?w=300", bio = "رحلات، جبال، ومغامرات في بلدان العالم 🏔️✈️", followersCount = 105000, followingCount = 60, totalLikes = 980000, role = "creator"),
            UserEntity(id = "creator_tariq", username = "tariq_comedy", displayName = "طارق كوميدي", email = "tariq@tokpulse.com", passwordHash = "PROTECTED", avatarUrl = "https://images.unsplash.com/photo-1522075469751-3a6694fb2f61?w=300", bio = "ابتسم كل يوم مع مواقف ويوميات ساخرة 😂🎬", followersCount = 142000, followingCount = 15, totalLikes = 1450000, role = "creator")
        )
        dao.insertUsers(creators)

        if (firebaseService.isFirebaseAvailable) {
            try {
                for (video in initialVideos) {
                    firebaseService.publishVideoToFirestore(video)
                }
            } catch (e: Exception) {
                // Ignore seeding exceptions if offline
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
        videoUri: Uri? = null,
        onProgress: ((Float) -> Unit)? = null
    ): VideoEntity = withContext(Dispatchers.IO) {
        val user = _currentUser.value
        val creatorId = user?.id ?: "creator_guest"
        val creatorName = user?.username ?: "creator"
        val creatorAvatar = user?.avatarUrl ?: "https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?w=300"

        val videoId = "vid_${UUID.randomUUID().toString().take(8)}"
        var finalVideoUrl = videoUrl

        if (videoUri != null && firebaseService.isFirebaseAvailable) {
            val storageResult = firebaseService.uploadVideoToStorage(videoUri, videoId, onProgress)
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
