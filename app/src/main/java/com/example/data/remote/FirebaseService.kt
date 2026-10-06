package com.example.data.remote

import android.app.Activity
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.example.R
import com.example.data.local.entities.CommentEntity
import com.example.data.local.entities.NotificationEntity
import com.example.data.local.entities.PrivacyRequestEntity
import com.example.data.local.entities.ReportEntity
import com.example.data.local.entities.UserEntity
import com.example.data.local.entities.VideoEntity
import com.example.data.local.entities.ViolationEntity
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.tasks.await
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Production Firebase Service handling Cloud Firestore, Firebase Auth, and Firebase Storage.
 * Configured with named custom Firestore database ID and Jetpack Credential Manager Google Sign-In.
 */
class FirebaseService(private val context: Context) {

    private val TAG = "FirebaseService"

    val isFirebaseAvailable: Boolean
        get() = try {
            FirebaseApp.getApps(context).isNotEmpty()
        } catch (e: Exception) {
            false
        }

    val auth: FirebaseAuth?
        get() = if (isFirebaseAvailable) {
            try { FirebaseAuth.getInstance() } catch (e: Exception) { null }
        } else null

    val firestore: FirebaseFirestore?
        get() = if (isFirebaseAvailable) {
            try {
                val dbId = try { context.getString(R.string.firestore_database_id).trim() } catch (_: Exception) { "" }
                if (dbId.isNotBlank() && dbId != "(default)" && !dbId.startsWith("ai-studio-android")) {
                    FirebaseFirestore.getInstance(FirebaseApp.getInstance(), dbId)
                } else {
                    FirebaseFirestore.getInstance(FirebaseApp.getInstance())
                }
            } catch (e: Exception) {
                try {
                    FirebaseFirestore.getInstance(FirebaseApp.getInstance())
                } catch (e2: Exception) {
                    null
                }
            }
        } else null

    val storage: FirebaseStorage?
        get() = if (isFirebaseAvailable) {
            try {
                FirebaseStorage.getInstance("gs://shortvideoapp-6b870.firebasestorage.app")
            } catch (e: Exception) {
                try { FirebaseStorage.getInstance() } catch (e2: Exception) { null }
            }
        } else null

    // --- AUTHENTICATION & USER SNAPSHOT CREATION ---

    fun isAdminEmail(email: String): Boolean {
        return email.equals("ahmedbecetti35@gmail.com", ignoreCase = true) ||
            email.equals("ahmedbecetti41@gmail.com", ignoreCase = true) ||
            email.contains("admin", ignoreCase = true)
    }

    suspend fun createOrUpdateUserSnapshot(user: UserEntity): Result<UserEntity> {
        val db = firestore ?: return Result.success(user)
        return try {
            val userDocRef = db.collection("users").document(user.id)
            val snapshotMap = hashMapOf<String, Any>(
                "id" to user.id,
                "username" to user.username,
                "displayName" to user.displayName,
                "email" to user.email,
                "avatarUrl" to user.avatarUrl,
                "bio" to user.bio,
                "followersCount" to user.followersCount.toLong(),
                "followingCount" to user.followingCount.toLong(),
                "totalLikes" to user.totalLikes.toLong(),
                "role" to user.role,
                "status" to user.status,
                "strikeCount" to 0L,
                "isVerified" to (user.role == "admin"),
                "createdAt" to user.createdAt
            )
            userDocRef.set(snapshotMap, SetOptions.merge()).await()
            Result.success(user)
        } catch (e: Exception) {
            Log.e(TAG, "createOrUpdateUserSnapshot warning, proceeding with local snapshot", e)
            Result.success(user)
        }
    }

    suspend fun registerWithEmail(
        email: String,
        password: String,
        rawUsername: String,
        rawDisplayName: String
    ): Result<UserEntity> {
        val authInstance = auth ?: return Result.failure(Exception("Firebase Auth service is unavailable."))
        return try {
            val authResult = authInstance.createUserWithEmailAndPassword(email.trim(), password).await()
            val firebaseUser = authResult.user ?: throw Exception("Failed to register Firebase user.")
            val uid = firebaseUser.uid
            val username = rawUsername.trim().ifEmpty { email.substringBefore("@").lowercase().replace(".", "_") }
            val displayName = rawDisplayName.trim().ifEmpty { username }
            val photoUrl = "https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?w=300"
            val isAdmin = isAdminEmail(email)

            val newUser = UserEntity(
                id = uid,
                username = username,
                displayName = displayName,
                email = email.trim(),
                passwordHash = "AUTH_SECURE",
                avatarUrl = photoUrl,
                bio = "Welcome to my thileli dz profile! 🎬",
                followersCount = 0,
                followingCount = 0,
                totalLikes = 0,
                role = if (isAdmin) "admin" else "user",
                status = "active",
                createdAt = System.currentTimeMillis()
            )

            createOrUpdateUserSnapshot(newUser)
            Result.success(newUser)
        } catch (e: Exception) {
            Log.e(TAG, "Email Registration failed", e)
            Result.failure(e)
        }
    }

    suspend fun signInWithEmail(email: String, password: String): Result<UserEntity> {
        val authInstance = auth ?: return Result.failure(Exception("Firebase Auth service is unavailable."))
        return try {
            val authResult = authInstance.signInWithEmailAndPassword(email.trim(), password).await()
            val firebaseUser = authResult.user ?: throw Exception("User not found.")
            val profile = syncAuthenticatedProfile(firebaseUser.uid, firebaseUser.email, firebaseUser.displayName)
                ?: UserEntity(
                    id = firebaseUser.uid,
                    username = email.substringBefore("@").lowercase().replace(".", "_"),
                    displayName = firebaseUser.displayName ?: email.substringBefore("@"),
                    email = email.trim(),
                    passwordHash = "AUTH_SECURE",
                    avatarUrl = "https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?w=300",
                    bio = "Welcome to my thileli dz profile! 🎬",
                    role = if (isAdminEmail(email)) "admin" else "user",
                    status = "active"
                )
            Result.success(profile)
        } catch (e: Exception) {
            Log.e(TAG, "Email Sign-In failed", e)
            Result.failure(e)
        }
    }

    suspend fun signInWithGoogle(): Result<UserEntity> {
        val authInstance = auth ?: return Result.failure(Exception("Firebase Auth is unavailable."))
        val db = firestore ?: return Result.failure(Exception("Cloud Firestore is unavailable."))

        return try {
            val serverClientId = context.getString(R.string.default_web_client_id)
            val signInWithGoogleOption = GetSignInWithGoogleOption.Builder(serverClientId)
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(signInWithGoogleOption)
                .build()

            val credentialManager = CredentialManager.create(context)
            val result = credentialManager.getCredential(request = request, context = context)

            val credential = result.credential
            if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                val authCredential = GoogleAuthProvider.getCredential(googleIdTokenCredential.idToken, null)
                val authResult = authInstance.signInWithCredential(authCredential).await()
                val firebaseUser = authResult.user ?: throw Exception("Empty user returned from Google Sign-In.")

                val uid = firebaseUser.uid
                val email = firebaseUser.email ?: "user@tokpulse.com"
                val displayName = firebaseUser.displayName ?: "thileli dz Creator"
                val photoUrl = firebaseUser.photoUrl?.toString()
                    ?: "https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?w=300"
                val username = email.substringBefore("@").lowercase().replace(".", "_")

                val userDocRef = db.collection("users").document(uid)
                val existingDoc = userDocRef.get().await()
                val isAdmin = isAdminEmail(email)

                val userEntity = if (existingDoc.exists()) {
                    val status = existingDoc.getString("status")?.takeIf { it.isNotBlank() } ?: "active"
                    if (status == "banned") {
                        authInstance.signOut()
                        throw Exception("This account has been banned for safety violations.")
                    }
                    if (status == "suspended") {
                        authInstance.signOut()
                        throw Exception("This account is currently suspended.")
                    }
                    val role = if (isAdmin) "admin" else (existingDoc.getString("role") ?: "user")
                    if (role != existingDoc.getString("role")) {
                        try {
                            userDocRef.set(mapOf("role" to role), SetOptions.merge()).await()
                        } catch (_: Exception) {}
                    }
                    UserEntity(
                        id = uid,
                        username = existingDoc.getString("username")?.takeIf { it.isNotBlank() } ?: username,
                        displayName = existingDoc.getString("displayName")?.takeIf { it.isNotBlank() } ?: displayName,
                        email = email,
                        passwordHash = "GOOGLE_AUTH",
                        avatarUrl = existingDoc.getString("avatarUrl")?.takeIf { it.isNotBlank() } ?: photoUrl,
                        bio = existingDoc.getString("bio")?.takeIf { it.isNotBlank() } ?: "Welcome to my thileli dz profile! 🎬",
                        followersCount = (existingDoc.getLong("followersCount") ?: 0L).toInt().coerceAtLeast(0),
                        followingCount = (existingDoc.getLong("followingCount") ?: 0L).toInt().coerceAtLeast(0),
                        totalLikes = (existingDoc.getLong("totalLikes") ?: 0L).toInt().coerceAtLeast(0),
                        role = role,
                        status = status,
                        createdAt = existingDoc.getLong("createdAt") ?: System.currentTimeMillis()
                    )
                } else {
                    val newUser = UserEntity(
                        id = uid,
                        username = username,
                        displayName = displayName,
                        email = email,
                        passwordHash = "GOOGLE_AUTH",
                        avatarUrl = photoUrl,
                        bio = "Welcome to my thileli dz profile! 🎬",
                        followersCount = 0,
                        followingCount = 0,
                        totalLikes = 0,
                        role = if (isAdmin) "admin" else "user",
                        status = "active",
                        createdAt = System.currentTimeMillis()
                    )
                    val userMap = hashMapOf<String, Any>(
                        "id" to newUser.id,
                        "username" to newUser.username,
                        "displayName" to newUser.displayName,
                        "email" to newUser.email,
                        "avatarUrl" to newUser.avatarUrl,
                        "bio" to newUser.bio,
                        "followersCount" to 0L,
                        "followingCount" to 0L,
                        "totalLikes" to 0L,
                        "role" to newUser.role,
                        "status" to newUser.status,
                        "strikeCount" to 0L,
                        "isVerified" to isAdmin,
                        "createdAt" to newUser.createdAt
                    )
                    userDocRef.set(userMap, SetOptions.merge()).await()
                    newUser
                }

                Result.success(userEntity)
            } else {
                Result.failure(Exception("Unsupported credential type: ${credential.type}"))
            }
        } catch (e: GetCredentialCancellationException) {
            Log.w(TAG, "User cancelled Google Sign-In", e)
            Result.failure(Exception("Google Sign-In was cancelled."))
        } catch (e: Exception) {
            Log.e(TAG, "Google Sign-In failed", e)
            Result.failure(e)
        }
    }

    suspend fun signInWithGoogleIdToken(idToken: String): Result<UserEntity> {
        val authInstance = auth ?: return Result.failure(Exception("Firebase Auth unavailable"))
        val db = firestore
        return try {
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            val authResult = authInstance.signInWithCredential(credential).await()
            val firebaseUser = authResult.user ?: throw Exception("Empty user from Google sign-in")
            val uid = firebaseUser.uid
            val email = firebaseUser.email ?: "user@chort.app"
            val displayName = firebaseUser.displayName ?: "thileli dz Creator"
            val photoUrl = firebaseUser.photoUrl?.toString()
                ?: "https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?w=300"
            val username = email.substringBefore("@").lowercase().replace(".", "_")

            val userEntity = UserEntity(
                id = uid,
                username = username,
                displayName = displayName,
                email = email,
                passwordHash = "GOOGLE_OAUTH",
                avatarUrl = photoUrl,
                bio = "Content Creator on thileli dz",
                followersCount = 0,
                followingCount = 0,
                totalLikes = 0,
                role = if (isAdminEmail(email)) "admin" else "user",
                status = "active",
                createdAt = System.currentTimeMillis()
            )

            if (db != null) {
                try {
                    db.collection("users").document(uid).set(mapOf(
                        "id" to uid,
                        "username" to username,
                        "displayName" to displayName,
                        "email" to email,
                        "avatarUrl" to photoUrl,
                        "role" to userEntity.role,
                        "status" to "active",
                        "createdAt" to System.currentTimeMillis()
                    ), SetOptions.merge()).await()
                } catch (_: Exception) {}
            }
            Result.success(userEntity)
        } catch (e: Exception) {
            Log.e(TAG, "signInWithGoogleIdToken error", e)
            Result.failure(e)
        }
    }

    fun sendPhoneOtp(
        activity: Activity,
        phoneNumber: String,
        onCodeSent: (verificationId: String, resendToken: PhoneAuthProvider.ForceResendingToken?) -> Unit,
        onVerificationCompleted: (credential: PhoneAuthCredential) -> Unit,
        onVerificationFailed: (Exception) -> Unit
    ) {
        val authInstance = auth
        if (authInstance == null) {
            onVerificationFailed(Exception("Firebase Authentication is not available"))
            return
        }

        val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
            override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                Log.d(TAG, "Phone verification instant auto-completed")
                onVerificationCompleted(credential)
            }

            override fun onVerificationFailed(e: FirebaseException) {
                Log.e(TAG, "Phone verification failed: ${e.message}", e)
                onVerificationFailed(e)
            }

            override fun onCodeSent(
                verificationId: String,
                token: PhoneAuthProvider.ForceResendingToken
            ) {
                Log.d(TAG, "SMS Code sent to $phoneNumber with verificationId=$verificationId")
                onCodeSent(verificationId, token)
            }
        }

        val options = PhoneAuthOptions.newBuilder(authInstance)
            .setPhoneNumber(phoneNumber)
            .setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(activity)
            .setCallbacks(callbacks)
            .build()

        PhoneAuthProvider.verifyPhoneNumber(options)
    }

    suspend fun verifyPhoneCredential(
        verificationId: String,
        code: String,
        displayName: String? = null
    ): Result<UserEntity> {
        val authInstance = auth ?: return Result.failure(Exception("Firebase Auth unavailable"))
        val db = firestore
        return try {
            val credential = PhoneAuthProvider.getCredential(verificationId, code)
            val authResult = authInstance.signInWithCredential(credential).await()
            val firebaseUser = authResult.user ?: throw Exception("Empty user from phone sign-in")
            val uid = firebaseUser.uid
            val phone = firebaseUser.phoneNumber ?: ""
            val cleanPhone = phone.filter { it.isDigit() }
            val name = displayName?.takeIf { it.isNotBlank() } ?: "User_${cleanPhone.takeLast(4)}"
            val username = "phone_${cleanPhone.takeLast(6).ifBlank { uid.take(6) }}"

            val userEntity = UserEntity(
                id = uid,
                username = username,
                displayName = name,
                email = "${username}@chort.app",
                passwordHash = "PHONE_AUTH",
                avatarUrl = "https://api.dicebear.com/7.x/avataaars/png?seed=$username",
                bio = "Content Creator on thileli dz",
                followersCount = 0,
                followingCount = 0,
                totalLikes = 0,
                role = "user",
                status = "active",
                createdAt = System.currentTimeMillis()
            )

            if (db != null) {
                try {
                    db.collection("users").document(uid).set(mapOf(
                        "id" to uid,
                        "phone" to phone,
                        "username" to username,
                        "displayName" to name,
                        "status" to "active",
                        "createdAt" to System.currentTimeMillis()
                    ), SetOptions.merge()).await()
                } catch (_: Exception) {}
            }
            Result.success(userEntity)
        } catch (e: Exception) {
            Log.e(TAG, "verifyPhoneCredential error", e)
            Result.failure(e)
        }
    }

    suspend fun syncAuthenticatedProfile(
        uid: String,
        fallbackEmail: String? = null,
        fallbackDisplayName: String? = null
    ): UserEntity? {
        val db = firestore
        val authUser = auth?.currentUser

        val effectiveEmail = fallbackEmail ?: authUser?.email ?: ""
        val effectiveDisplayName = fallbackDisplayName ?: authUser?.displayName ?: "thileli dz Creator"
        val effectiveUsername = effectiveEmail.substringBefore("@").ifEmpty { "user_${uid.take(6)}" }.lowercase().replace(".", "_")
        val effectiveAvatar = authUser?.photoUrl?.toString() ?: "https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?w=300"
        val isAdmin = isAdminEmail(effectiveEmail)

        if (db == null) {
            return UserEntity(
                id = uid,
                username = effectiveUsername,
                displayName = effectiveDisplayName,
                email = effectiveEmail,
                passwordHash = "AUTHENTICATED",
                avatarUrl = effectiveAvatar,
                bio = "Welcome to my thileli dz profile! 🎬",
                followersCount = 0,
                followingCount = 0,
                totalLikes = 0,
                role = if (isAdmin) "admin" else "user",
                status = "active",
                createdAt = System.currentTimeMillis()
            )
        }

        return try {
            val userDocRef = db.collection("users").document(uid)
            val doc = userDocRef.get().await()

            if (doc.exists()) {
                val status = doc.getString("status")?.takeIf { it.isNotBlank() } ?: "active"
                val existingRole = doc.getString("role")?.takeIf { it.isNotBlank() } ?: "user"
                val role = if (isAdmin) "admin" else existingRole

                if (role != existingRole) {
                    try {
                        userDocRef.set(mapOf("role" to role), SetOptions.merge()).await()
                    } catch (_: Exception) {}
                }

                UserEntity(
                    id = uid,
                    username = doc.getString("username")?.takeIf { it.isNotBlank() } ?: effectiveUsername,
                    displayName = doc.getString("displayName")?.takeIf { it.isNotBlank() } ?: effectiveDisplayName,
                    email = doc.getString("email")?.takeIf { it.isNotBlank() } ?: effectiveEmail,
                    passwordHash = "AUTHENTICATED",
                    avatarUrl = doc.getString("avatarUrl")?.takeIf { it.isNotBlank() } ?: effectiveAvatar,
                    bio = doc.getString("bio")?.takeIf { it.isNotBlank() } ?: "Welcome to my thileli dz profile! 🎬",
                    followersCount = (doc.getLong("followersCount") ?: 0L).toInt().coerceAtLeast(0),
                    followingCount = (doc.getLong("followingCount") ?: 0L).toInt().coerceAtLeast(0),
                    totalLikes = (doc.getLong("totalLikes") ?: 0L).toInt().coerceAtLeast(0),
                    role = role,
                    status = status,
                    createdAt = doc.getLong("createdAt") ?: System.currentTimeMillis()
                )
            } else {
                val newSnapshot = UserEntity(
                    id = uid,
                    username = effectiveUsername,
                    displayName = effectiveDisplayName,
                    email = effectiveEmail,
                    passwordHash = "AUTHENTICATED",
                    avatarUrl = effectiveAvatar,
                    bio = "Welcome to my thileli dz profile! 🎬",
                    followersCount = 0,
                    followingCount = 0,
                    totalLikes = 0,
                    role = if (isAdmin) "admin" else "user",
                    status = "active",
                    createdAt = System.currentTimeMillis()
                )
                val snapshotMap = hashMapOf<String, Any>(
                    "id" to newSnapshot.id,
                    "username" to newSnapshot.username,
                    "displayName" to newSnapshot.displayName,
                    "email" to newSnapshot.email,
                    "avatarUrl" to newSnapshot.avatarUrl,
                    "bio" to newSnapshot.bio,
                    "followersCount" to 0L,
                    "followingCount" to 0L,
                    "totalLikes" to 0L,
                    "role" to newSnapshot.role,
                    "status" to newSnapshot.status,
                    "strikeCount" to 0L,
                    "isVerified" to (newSnapshot.role == "admin"),
                    "createdAt" to newSnapshot.createdAt
                )
                try {
                    userDocRef.set(snapshotMap, SetOptions.merge()).await()
                } catch (e: Exception) {
                    Log.w(TAG, "Failed writing user snapshot to firestore, returning local snapshot", e)
                }
                newSnapshot
            }
        } catch (e: Exception) {
            Log.e(TAG, "syncAuthenticatedProfile error, creating resilient fallback snapshot", e)
            UserEntity(
                id = uid,
                username = effectiveUsername,
                displayName = effectiveDisplayName,
                email = effectiveEmail,
                passwordHash = "AUTHENTICATED",
                avatarUrl = effectiveAvatar,
                bio = "Welcome to my thileli dz profile! 🎬",
                followersCount = 0,
                followingCount = 0,
                totalLikes = 0,
                role = if (isAdmin) "admin" else "user",
                status = "active",
                createdAt = System.currentTimeMillis()
            )
        }
    }

    fun signOut() {
        try {
            auth?.signOut()
        } catch (e: Exception) {
            Log.e(TAG, "signOut failed", e)
        }
    }

    // --- VIDEO CLOUD STORAGE & FIRESTORE ---

    suspend fun uploadVideoToStorage(
        videoUri: Uri,
        videoId: String,
        onProgress: ((Float) -> Unit)? = null
    ): Result<String> {
        val storageInstance = storage ?: return Result.failure(Exception("Firebase Storage unavailable"))
        return try {
            val ref = storageInstance.reference.child("videos/$videoId.mp4")
            val uploadTask = ref.putFile(videoUri)
            if (onProgress != null) {
                uploadTask.addOnProgressListener { snapshot ->
                    if (snapshot.totalByteCount > 0) {
                        val progress = snapshot.bytesTransferred.toFloat() / snapshot.totalByteCount.toFloat()
                        onProgress(progress.coerceIn(0f, 1f))
                    }
                }
            }
            uploadTask.await()
            val downloadUrl = ref.downloadUrl.await().toString()
            Result.success(downloadUrl)
        } catch (e: Exception) {
            Log.e(TAG, "uploadVideoToStorage failed", e)
            Result.failure(e)
        }
    }

    suspend fun uploadAvatarToStorage(avatarUri: Uri, userId: String): Result<String> {
        val storageInstance = storage ?: return Result.failure(Exception("Firebase Storage unavailable"))
        return try {
            val ref = storageInstance.reference.child("avatars/$userId.jpg")
            ref.putFile(avatarUri).await()
            val downloadUrl = ref.downloadUrl.await().toString()
            Result.success(downloadUrl)
        } catch (e: Exception) {
            Log.e(TAG, "uploadAvatarToStorage failed", e)
            Result.failure(e)
        }
    }

    suspend fun publishVideoToFirestore(video: VideoEntity): Result<Unit> {
        val db = firestore ?: return Result.failure(Exception("Firestore unavailable"))
        return try {
            val videoMap = hashMapOf(
                "id" to video.id,
                "creatorId" to video.creatorId,
                "creatorUsername" to video.creatorUsername,
                "creatorAvatar" to video.creatorAvatar,
                "videoUrl" to video.videoUrl,
                "thumbnailUrl" to video.thumbnailUrl,
                "caption" to video.caption,
                "musicTitle" to video.musicTitle,
                "tags" to video.tags,
                "likesCount" to video.likesCount,
                "commentsCount" to video.commentsCount,
                "sharesCount" to video.sharesCount,
                "viewsCount" to video.viewsCount,
                "isHidden" to false,
                "isDeleted" to false,
                "createdAt" to video.createdAt
            )
            db.collection("videos").document(video.id).set(videoMap, SetOptions.merge()).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "publishVideoToFirestore failed", e)
            Result.failure(e)
        }
    }

    suspend fun fetchVideosFromFirestore(limit: Long = 50): List<VideoEntity> {
        val db = firestore ?: return emptyList()
        return try {
            val querySnapshot = db.collection("videos")
                .whereEqualTo("isDeleted", false)
                .whereEqualTo("isHidden", false)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(limit)
                .get()
                .await()

            querySnapshot.documents.mapNotNull { doc ->
                VideoEntity(
                    id = doc.getString("id") ?: doc.id,
                    creatorId = doc.getString("creatorId") ?: "",
                    creatorUsername = doc.getString("creatorUsername") ?: "creator",
                    creatorAvatar = doc.getString("creatorAvatar") ?: "https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?w=300",
                    videoUrl = doc.getString("videoUrl") ?: "",
                    thumbnailUrl = doc.getString("thumbnailUrl") ?: "https://images.unsplash.com/photo-1518770660439-4636190af475?w=500",
                    caption = doc.getString("caption") ?: "",
                    musicTitle = doc.getString("musicTitle") ?: "Original Sound",
                    tags = doc.getString("tags") ?: "#tokpulse",
                    likesCount = (doc.getLong("likesCount") ?: 0).toInt(),
                    commentsCount = (doc.getLong("commentsCount") ?: 0).toInt(),
                    sharesCount = (doc.getLong("sharesCount") ?: 0).toInt(),
                    viewsCount = (doc.getLong("viewsCount") ?: 0).toInt(),
                    isHidden = doc.getBoolean("isHidden") ?: false,
                    isDeleted = doc.getBoolean("isDeleted") ?: false,
                    createdAt = doc.getLong("createdAt") ?: System.currentTimeMillis()
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "fetchVideosFromFirestore error", e)
            emptyList()
        }
    }

    suspend fun recordVideoView(videoId: String) {
        val db = firestore ?: return
        try {
            db.collection("videos").document(videoId).update("viewsCount", FieldValue.increment(1)).await()
        } catch (e: Exception) {
            Log.e(TAG, "recordVideoView failed", e)
        }
    }

    // --- LIKES, COMMENTS, FOLLOWS IN FIRESTORE ---

    suspend fun toggleLikeInFirestore(videoId: String, userId: String, isLiked: Boolean) {
        val db = firestore ?: return
        try {
            val likeDocId = "${videoId}_$userId"
            val videoRef = db.collection("videos").document(videoId)
            val likeRef = db.collection("likes").document(likeDocId)

            if (isLiked) {
                likeRef.delete().await()
                videoRef.update("likesCount", FieldValue.increment(-1)).await()
            } else {
                val likeData = hashMapOf(
                    "id" to likeDocId,
                    "videoId" to videoId,
                    "userId" to userId,
                    "createdAt" to System.currentTimeMillis()
                )
                likeRef.set(likeData).await()
                videoRef.update("likesCount", FieldValue.increment(1)).await()
            }
        } catch (e: Exception) {
            Log.e(TAG, "toggleLikeInFirestore failed", e)
        }
    }

    suspend fun addCommentToFirestore(comment: CommentEntity) {
        val db = firestore ?: return
        try {
            val commentMap = hashMapOf(
                "id" to comment.id,
                "videoId" to comment.videoId,
                "userId" to comment.userId,
                "username" to comment.username,
                "userAvatar" to comment.userAvatar,
                "text" to comment.text,
                "likesCount" to comment.likesCount,
                "createdAt" to comment.createdAt
            )
            db.collection("comments").document(comment.id).set(commentMap).await()
            db.collection("videos").document(comment.videoId).update("commentsCount", FieldValue.increment(1)).await()
        } catch (e: Exception) {
            Log.e(TAG, "addCommentToFirestore failed", e)
        }
    }

    suspend fun deleteCommentInFirestore(commentId: String, videoId: String) {
        val db = firestore ?: return
        try {
            db.collection("comments").document(commentId).delete().await()
            db.collection("videos").document(videoId).update("commentsCount", FieldValue.increment(-1)).await()
        } catch (e: Exception) {
            Log.e(TAG, "deleteCommentInFirestore failed", e)
        }
    }

    suspend fun recordFollowInFirestore(followerId: String, followingId: String, followerUser: UserEntity) {
        val db = firestore ?: return
        try {
            val followId = "${followerId}_$followingId"
            val followData = hashMapOf(
                "id" to followId,
                "followerId" to followerId,
                "followingId" to followingId,
                "createdAt" to System.currentTimeMillis()
            )
            db.collection("followers").document(followId).set(followData).await()

            db.collection("users").document(followerId).update("followingCount", FieldValue.increment(1)).await()
            db.collection("users").document(followingId).update("followersCount", FieldValue.increment(1)).await()

            val notifId = UUID.randomUUID().toString()
            val notifData = hashMapOf(
                "id" to notifId,
                "userId" to followingId,
                "actorId" to followerId,
                "actorUsername" to followerUser.username,
                "actorAvatar" to followerUser.avatarUrl,
                "type" to "follow",
                "message" to "started following you on thileli dz!",
                "isRead" to false,
                "createdAt" to System.currentTimeMillis()
            )
            db.collection("notifications").document(notifId).set(notifData).await()
        } catch (e: Exception) {
            Log.e(TAG, "recordFollowInFirestore failed", e)
        }
    }

    suspend fun recordUnfollowInFirestore(followerId: String, followingId: String) {
        val db = firestore ?: return
        try {
            val followId = "${followerId}_$followingId"
            db.collection("followers").document(followId).delete().await()
            db.collection("users").document(followerId).update("followingCount", FieldValue.increment(-1)).await()
            db.collection("users").document(followingId).update("followersCount", FieldValue.increment(-1)).await()
        } catch (e: Exception) {
            Log.e(TAG, "recordUnfollowInFirestore failed", e)
        }
    }

    // --- REPORTS & MODERATION ---

    suspend fun submitReportToFirestore(report: ReportEntity): Result<Unit> {
        val db = firestore ?: return Result.failure(Exception("Firestore unavailable"))
        return try {
            val reportMap = hashMapOf(
                "id" to report.id,
                "reporterId" to report.reporterId,
                "reporterUsername" to report.reporterUsername,
                "targetType" to report.targetType,
                "targetId" to report.targetId,
                "targetOwnerUsername" to report.targetOwnerUsername,
                "targetSnippet" to report.targetSnippet,
                "reason" to report.reason,
                "description" to report.description,
                "status" to report.status,
                "resolutionNotes" to report.resolutionNotes,
                "resolvedByAdmin" to report.resolvedByAdmin,
                "createdAt" to report.createdAt
            )
            db.collection("reports").document(report.id).set(reportMap).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchReportsAdminFirestore(): List<ReportEntity> {
        val db = firestore ?: return emptyList()
        return try {
            val snapshot = db.collection("reports")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .get()
                .await()
            snapshot.documents.mapNotNull { doc ->
                ReportEntity(
                    id = doc.getString("id") ?: doc.id,
                    reporterId = doc.getString("reporterId") ?: "",
                    reporterUsername = doc.getString("reporterUsername") ?: "reporter",
                    targetType = doc.getString("targetType") ?: "video",
                    targetId = doc.getString("targetId") ?: "",
                    targetOwnerUsername = doc.getString("targetOwnerUsername") ?: "",
                    targetSnippet = doc.getString("targetSnippet") ?: "",
                    reason = doc.getString("reason") ?: "Inappropriate Content",
                    description = doc.getString("description") ?: "",
                    status = doc.getString("status") ?: "pending",
                    resolutionNotes = doc.getString("resolutionNotes") ?: "",
                    resolvedByAdmin = doc.getString("resolvedByAdmin") ?: "",
                    createdAt = doc.getLong("createdAt") ?: System.currentTimeMillis()
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "fetchReportsAdminFirestore error", e)
            emptyList()
        }
    }

    suspend fun resolveReportAdminFirestore(
        reportId: String,
        status: String,
        notes: String,
        adminUsername: String
    ) {
        val db = firestore ?: return
        try {
            db.collection("reports").document(reportId).update(
                mapOf(
                    "status" to status,
                    "resolutionNotes" to notes,
                    "resolvedByAdmin" to adminUsername
                )
            ).await()
        } catch (e: Exception) {
            Log.e(TAG, "resolveReportAdminFirestore failed", e)
        }
    }

    // --- ADMIN USER & CONTENT MANAGEMENT ---

    suspend fun fetchUsersAdminFirestore(): List<UserEntity> {
        val db = firestore ?: return emptyList()
        return try {
            val snapshot = db.collection("users").get().await()
            snapshot.documents.mapNotNull { doc ->
                UserEntity(
                    id = doc.getString("id") ?: doc.id,
                    username = doc.getString("username") ?: "user",
                    displayName = doc.getString("displayName") ?: "thileli dz User",
                    email = doc.getString("email") ?: "",
                    passwordHash = "PROTECTED",
                    avatarUrl = doc.getString("avatarUrl") ?: "https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?w=300",
                    bio = doc.getString("bio") ?: "",
                    followersCount = (doc.getLong("followersCount") ?: 0).toInt(),
                    followingCount = (doc.getLong("followingCount") ?: 0).toInt(),
                    totalLikes = (doc.getLong("totalLikes") ?: 0).toInt(),
                    role = doc.getString("role") ?: "user",
                    status = doc.getString("status") ?: "active",
                    createdAt = doc.getLong("createdAt") ?: System.currentTimeMillis()
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "fetchUsersAdminFirestore failed", e)
            emptyList()
        }
    }

    suspend fun hideVideoAdminFirestore(videoId: String, isHidden: Boolean) {
        val db = firestore ?: return
        try {
            db.collection("videos").document(videoId).update("isHidden", isHidden).await()
        } catch (e: Exception) {
            Log.e(TAG, "hideVideoAdminFirestore failed", e)
        }
    }

    suspend fun deleteVideoAdminFirestore(videoId: String) {
        val db = firestore ?: return
        try {
            db.collection("videos").document(videoId).update("isDeleted", true).await()
        } catch (e: Exception) {
            Log.e(TAG, "deleteVideoAdminFirestore failed", e)
        }
    }

    suspend fun warnUserAdminFirestore(userId: String, reason: String, adminUsername: String) {
        val db = firestore ?: return
        try {
            db.collection("users").document(userId).update("strikeCount", FieldValue.increment(1)).await()
            val violId = "viol_${UUID.randomUUID().toString().take(8)}"
            val violMap = hashMapOf(
                "id" to violId,
                "userId" to userId,
                "username" to "user",
                "violationType" to "warning",
                "reason" to reason,
                "relatedContent" to "",
                "actionTaken" to "warning",
                "adminUsername" to adminUsername,
                "createdAt" to System.currentTimeMillis()
            )
            db.collection("violations").document(violId).set(violMap).await()

            // Send system warning notification
            val notifId = UUID.randomUUID().toString()
            val notifData = hashMapOf(
                "id" to notifId,
                "userId" to userId,
                "actorId" to "system_moderation",
                "actorUsername" to "thileli dz Safety",
                "actorAvatar" to "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=300",
                "type" to "warning",
                "message" to "Official Warning: $reason. Repeated violations will lead to permanent suspension.",
                "isRead" to false,
                "createdAt" to System.currentTimeMillis()
            )
            db.collection("notifications").document(notifId).set(notifData).await()
        } catch (e: Exception) {
            Log.e(TAG, "warnUserAdminFirestore failed", e)
        }
    }

    suspend fun tempBanUserAdminFirestore(userId: String, reason: String, adminUsername: String) {
        val db = firestore ?: return
        try {
            db.collection("users").document(userId).update("status", "suspended").await()
            val violId = "viol_${UUID.randomUUID().toString().take(8)}"
            val violMap = hashMapOf(
                "id" to violId,
                "userId" to userId,
                "username" to "user",
                "violationType" to "temporary_suspension",
                "reason" to reason,
                "relatedContent" to "",
                "actionTaken" to "temporary_suspension",
                "adminUsername" to adminUsername,
                "createdAt" to System.currentTimeMillis()
            )
            db.collection("violations").document(violId).set(violMap).await()
        } catch (e: Exception) {
            Log.e(TAG, "tempBanUserAdminFirestore failed", e)
        }
    }

    suspend fun banUserAdminFirestore(userId: String, reason: String, adminUsername: String) {
        val db = firestore ?: return
        try {
            db.collection("users").document(userId).update("status", "banned").await()
            val violId = "viol_${UUID.randomUUID().toString().take(8)}"
            val violMap = hashMapOf(
                "id" to violId,
                "userId" to userId,
                "username" to "user",
                "violationType" to "permanent_ban",
                "reason" to reason,
                "relatedContent" to "",
                "actionTaken" to "permanent_ban",
                "adminUsername" to adminUsername,
                "createdAt" to System.currentTimeMillis()
            )
            db.collection("violations").document(violId).set(violMap).await()
        } catch (e: Exception) {
            Log.e(TAG, "banUserAdminFirestore failed", e)
        }
    }

    suspend fun unbanUserAdminFirestore(userId: String, adminUsername: String) {
        val db = firestore ?: return
        try {
            db.collection("users").document(userId).update("status", "active").await()
            val violId = "viol_${UUID.randomUUID().toString().take(8)}"
            val violMap = hashMapOf(
                "id" to violId,
                "userId" to userId,
                "username" to "user",
                "violationType" to "unban",
                "reason" to "Account restored upon safety review",
                "relatedContent" to "",
                "actionTaken" to "unban",
                "adminUsername" to adminUsername,
                "createdAt" to System.currentTimeMillis()
            )
            db.collection("violations").document(violId).set(violMap).await()
        } catch (e: Exception) {
            Log.e(TAG, "unbanUserAdminFirestore failed", e)
        }
    }

    suspend fun fetchViolationsAdminFirestore(): List<ViolationEntity> {
        val db = firestore ?: return emptyList()
        return try {
            val snapshot = db.collection("violations")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .get()
                .await()
            snapshot.documents.mapNotNull { doc ->
                ViolationEntity(
                    id = doc.getString("id") ?: doc.id,
                    userId = doc.getString("userId") ?: "",
                    username = doc.getString("username") ?: "user",
                    violationType = doc.getString("violationType") ?: "Terms Violation",
                    reason = doc.getString("reason") ?: "Terms Violation",
                    relatedContent = doc.getString("relatedContent") ?: "",
                    actionTaken = doc.getString("actionTaken") ?: "warning",
                    adminUsername = doc.getString("adminUsername") ?: "admin",
                    createdAt = doc.getLong("createdAt") ?: System.currentTimeMillis()
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "fetchViolationsAdminFirestore failed", e)
            emptyList()
        }
    }

    // --- PRIVACY & ACCOUNT DELETION ---

    suspend fun submitPrivacyRequestToFirestore(request: PrivacyRequestEntity): Result<Unit> {
        val db = firestore ?: return Result.failure(Exception("Firestore unavailable"))
        return try {
            val requestMap = hashMapOf(
                "id" to request.id,
                "userId" to request.userId,
                "username" to request.username,
                "email" to request.email,
                "requestType" to request.requestType,
                "reason" to request.reason,
                "status" to request.status,
                "processedByAdmin" to request.processedByAdmin,
                "createdAt" to request.createdAt
            )
            db.collection("privacyRequests").document(request.id).set(requestMap).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchPrivacyRequestsAdminFirestore(): List<PrivacyRequestEntity> {
        val db = firestore ?: return emptyList()
        return try {
            val snapshot = db.collection("privacyRequests")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .get()
                .await()
            snapshot.documents.mapNotNull { doc ->
                PrivacyRequestEntity(
                    id = doc.getString("id") ?: doc.id,
                    userId = doc.getString("userId") ?: "",
                    username = doc.getString("username") ?: "user",
                    email = doc.getString("email") ?: "",
                    requestType = doc.getString("requestType") ?: "account_deletion",
                    reason = doc.getString("reason") ?: "",
                    status = doc.getString("status") ?: "pending",
                    processedByAdmin = doc.getString("processedByAdmin") ?: "",
                    createdAt = doc.getLong("createdAt") ?: System.currentTimeMillis()
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "fetchPrivacyRequestsAdminFirestore failed", e)
            emptyList()
        }
    }

    suspend fun processPrivacyRequestFirestore(requestId: String, status: String, adminUsername: String) {
        val db = firestore ?: return
        try {
            db.collection("privacyRequests").document(requestId).update(
                mapOf(
                    "status" to status,
                    "processedByAdmin" to adminUsername
                )
            ).await()
        } catch (e: Exception) {
            Log.e(TAG, "processPrivacyRequestFirestore failed", e)
        }
    }

    suspend fun blockUserInFirestore(userId: String, blockedUserId: String) {
        val db = firestore ?: return
        try {
            val docId = "${userId}_$blockedUserId"
            val map = hashMapOf(
                "id" to docId,
                "userId" to userId,
                "blockedUserId" to blockedUserId,
                "createdAt" to System.currentTimeMillis()
            )
            db.collection("blockedUsers").document(docId).set(map).await()
        } catch (e: Exception) {
            Log.e(TAG, "blockUserInFirestore failed", e)
        }
    }
}
