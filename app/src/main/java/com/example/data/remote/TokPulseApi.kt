package com.example.data.remote

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

// -------------------------------------------------------------
// Models
// -------------------------------------------------------------

@JsonClass(generateAdapter = true)
data class HealthResponse(
    val status: String? = null,
    val service: String? = null,
    val timestamp: String? = null,
    val version: String? = null,
    val database: String? = null,
    val databaseConnected: Boolean? = false,
    val storage: String? = null,
    val cdn: String? = null,
    val vercelProduction: Boolean? = true
)

@JsonClass(generateAdapter = true)
data class RegisterRequest(
    val email: String,
    val username: String,
    val password: String,
    val displayName: String? = null
)

@JsonClass(generateAdapter = true)
data class LoginRequest(
    val identifier: String,
    val password: String
)

@JsonClass(generateAdapter = true)
data class AuthResponse(
    val message: String? = null,
    val token: String? = null,
    val user: ApiUser? = null,
    val isNew: Boolean? = null,
    val linked: Boolean? = null,
    val error: String? = null
)

@JsonClass(generateAdapter = true)
data class ApiUser(
    val id: String,
    val email: String,
    val username: String,
    val displayName: String? = null,
    val avatarUrl: String? = null,
    val bio: String? = null,
    val followersCount: Int? = 0,
    val followingCount: Int? = 0,
    val role: String? = "USER",
    val phone: String? = null,
    val phoneVerified: Boolean? = false,
    val primaryProvider: String? = "email"
)

@JsonClass(generateAdapter = true)
data class FeedResponse(
    val videos: List<ApiVideo> = emptyList(),
    val nextCursor: String? = null,
    val hasMore: Boolean = false,
    val source: String? = null
)

@JsonClass(generateAdapter = true)
data class ApiVideo(
    val id: String,
    val creatorId: String,
    val creatorUsername: String,
    val creatorAvatar: String? = null,
    val caption: String,
    val streamUrl: String? = null,
    val videoUrl: String? = null,
    val thumbnailUrl: String? = null,
    val musicTitle: String? = null,
    val likesCount: Int = 0,
    val commentsCount: Int = 0,
    val sharesCount: Int = 0,
    val viewsCount: Int = 0,
    val aspectRatio: String? = "9:16",
    val source: String? = "chort",
    val provider: String? = null,
    val attributionUrl: String? = null,
    val photographerUrl: String? = null,
    val createdAt: Long = 0
)

@JsonClass(generateAdapter = true)
data class ExternalVideosResponse(
    val configured: Boolean = false,
    val provider: String? = "pexels",
    val page: Int = 1,
    val perPage: Int = 15,
    val total: Int = 0,
    val videos: List<ApiVideo> = emptyList(),
    val error: String? = null
)

@JsonClass(generateAdapter = true)
data class UploadTicketRequest(
    val filename: String,
    val contentType: String = "video/mp4",
    val userId: String
)

@JsonClass(generateAdapter = true)
data class UploadTicketResponse(
    val videoId: String,
    val objectKey: String,
    val uploadUrl: String,
    val thumbnailKey: String? = null,
    val thumbnailUploadUrl: String? = null,
    val directUpload: Boolean = false,
    val streamUrl: String,
    val thumbnailUrl: String? = null
)

@JsonClass(generateAdapter = true)
data class CompleteUploadRequest(
    val videoId: String,
    val userId: String,
    val caption: String,
    val videoUrl: String,
    val thumbnailUrl: String? = null,
    val musicTitle: String? = null,
    val aspectRatio: String? = "9:16",
    val objectKey: String? = null
)

@JsonClass(generateAdapter = true)
data class ViewResponse(
    val counted: Boolean = false,
    val viewsCount: Int = 0
)

@JsonClass(generateAdapter = true)
data class ShareRequest(
    val userId: String? = null
)

@JsonClass(generateAdapter = true)
data class ShareResponse(
    val shared: Boolean = false,
    val sharesCount: Int = 0
)

@JsonClass(generateAdapter = true)
data class GenericActionResponse(
    val success: Boolean = false,
    val message: String? = null,
    val deleted: Boolean = false
)

@JsonClass(generateAdapter = true)
data class CompleteUploadResponse(
    val message: String? = null,
    val video: ApiVideo? = null,
    val error: String? = null
)

@JsonClass(generateAdapter = true)
data class LikeRequest(
    val userId: String
)

@JsonClass(generateAdapter = true)
data class LikeResponse(
    val liked: Boolean = false,
    val likesCount: Int = 0
)

@JsonClass(generateAdapter = true)
data class CommentsResponse(
    val comments: List<ApiComment> = emptyList()
)

@JsonClass(generateAdapter = true)
data class ApiComment(
    val id: String,
    val videoId: String,
    val userId: String,
    val content: String,
    val createdAt: String? = null,
    val user: ApiCommentUser? = null
)

@JsonClass(generateAdapter = true)
data class ApiCommentUser(
    val username: String? = null,
    val profile: ApiCommentProfile? = null
)

@JsonClass(generateAdapter = true)
data class ApiCommentProfile(
    val displayName: String? = null,
    val avatarUrl: String? = null
)

@JsonClass(generateAdapter = true)
data class AddCommentRequest(
    val userId: String,
    val content: String
)

@JsonClass(generateAdapter = true)
data class CommentItemResponse(
    val comment: ApiComment? = null
)

@JsonClass(generateAdapter = true)
data class SearchResponse(
    val videos: List<ApiVideo> = emptyList(),
    val users: List<ApiSearchUser> = emptyList()
)

@JsonClass(generateAdapter = true)
data class ApiSearchUser(
    val id: String,
    val username: String,
    val displayName: String? = null,
    val avatarUrl: String? = null,
    val followersCount: Int = 0,
    val bio: String? = null
)

@JsonClass(generateAdapter = true)
data class UserProfileResponse(
    val user: ApiUserProfile? = null,
    val error: String? = null
)

@JsonClass(generateAdapter = true)
data class ApiUserProfile(
    val id: String,
    val username: String,
    val displayName: String? = null,
    val bio: String? = null,
    val avatarUrl: String? = null,
    val followersCount: Int = 0,
    val followingCount: Int = 0,
    val likesReceived: Int = 0,
    val videos: List<ApiVideo> = emptyList()
)

@JsonClass(generateAdapter = true)
data class OAuthGoogleRequest(val idToken: String)

@JsonClass(generateAdapter = true)
data class OAuthFacebookRequest(val accessToken: String)

@JsonClass(generateAdapter = true)
data class PhoneRequestBody(val phone: String)

@JsonClass(generateAdapter = true)
data class PhoneVerifyRequest(
    val phone: String,
    val code: String,
    val name: String? = null
)

@JsonClass(generateAdapter = true)
data class RecoverRequestBody(val phone: String)

@JsonClass(generateAdapter = true)
data class RecoverConfirmRequest(
    val phone: String,
    val code: String,
    val newPassword: String? = null
)

@JsonClass(generateAdapter = true)
data class OtpResponse(
    val sent: Boolean = false,
    val via: String? = null,
    val expiresInSeconds: Int = 600,
    val resendCooldownSeconds: Int = 60,
    val accountFound: Boolean? = null,
    // Setup-mode only: present while no SMS provider is configured server-side.
    val devOtp: String? = null,
    val error: String? = null
)

@JsonClass(generateAdapter = true)
data class LinkedProvider(
    val provider: String,
    val email: String? = null,
    val linkedAt: String? = null
)

@JsonClass(generateAdapter = true)
data class ProvidersResponse(
    val providers: List<LinkedProvider> = emptyList()
)

@JsonClass(generateAdapter = true)
data class LinkProviderRequest(
    val provider: String,
    val idToken: String? = null,
    val accessToken: String? = null
)

@JsonClass(generateAdapter = true)
data class LinkProviderResponse(
    val linked: Boolean = false,
    val provider: String? = null,
    val error: String? = null
)

@JsonClass(generateAdapter = true)
data class ApiSession(
    val id: String,
    val userAgent: String? = null,
    val ipAddress: String? = null,
    val expiresAt: Long = 0,
    val createdAt: Long = 0,
    val current: Boolean = false
)

@JsonClass(generateAdapter = true)
data class SessionsResponse(
    val sessions: List<ApiSession> = emptyList()
)

@JsonClass(generateAdapter = true)
data class RevokeResponse(
    val revoked: Boolean = false,
    val error: String? = null
)

@JsonClass(generateAdapter = true)
data class ChangePasswordRequest(
    val currentPassword: String,
    val newPassword: String
)

@JsonClass(generateAdapter = true)
data class ChangePasswordResponse(
    val changed: Boolean = false,
    val error: String? = null
)

@JsonClass(generateAdapter = true)
data class UpdateUserRequest(
    val username: String? = null,
    val displayName: String? = null,
    val bio: String? = null,
    val avatarUrl: String? = null,
    val bannerUrl: String? = null
)

@JsonClass(generateAdapter = true)
data class UpdateUserResponse(
    val user: ApiUser? = null,
    val error: String? = null
)

@JsonClass(generateAdapter = true)
data class DeleteUserResponse(
    val deleted: Boolean = false,
    val videosRemoved: Int = 0,
    val error: String? = null
)

@JsonClass(generateAdapter = true)
data class ApiNotification(
    val id: String,
    val actorId: String,
    val type: String,
    val message: String,
    val referenceId: String? = null,
    val isRead: Boolean = false,
    val createdAt: Long = 0
)

@JsonClass(generateAdapter = true)
data class NotificationsResponse(
    val notifications: List<ApiNotification> = emptyList(),
    val unreadCount: Int = 0,
    val page: Int = 1,
    val hasMore: Boolean = false
)

@JsonClass(generateAdapter = true)
data class NotificationsReadRequest(
    val ids: List<String>? = null
)

@JsonClass(generateAdapter = true)
data class NotificationsReadResponse(
    val marked: Int = 0
)

@JsonClass(generateAdapter = true)
data class ApiAdminReport(
    val id: String,
    val reporterId: String,
    val targetUserId: String? = null,
    val videoId: String? = null,
    val reason: String,
    val status: String = "PENDING",
    val createdAt: String? = null
)

@JsonClass(generateAdapter = true)
data class AdminReportsResponse(
    val reports: List<ApiAdminReport> = emptyList(),
    val page: Int = 1,
    val hasMore: Boolean = false
)

@JsonClass(generateAdapter = true)
data class AdminOverviewResponse(
    val usersTotal: Int = 0,
    val videosTotal: Int = 0,
    val videosPublic: Int = 0,
    val reportsPending: Int = 0,
    val reportsTotal: Int = 0,
    val sessionsActive: Int = 0,
    val sessionsTotal: Int = 0,
    val serverTime: String? = null
)

@JsonClass(generateAdapter = true)
data class ResolveReportRequest(
    val action: String // dismiss | hide_video | show_video
)

@JsonClass(generateAdapter = true)
data class ResolveReportResponse(
    val reportId: String? = null,
    val status: String? = null,
    val action: String? = null,
    val error: String? = null
)

@JsonClass(generateAdapter = true)
data class ApiLoginUser(
    val id: String,
    val username: String,
    val email: String,
    val phone: String? = null,
    val primaryProvider: String? = null
)

@JsonClass(generateAdapter = true)
data class ApiLoginRecord(
    val id: String,
    val user: ApiLoginUser? = null,
    val userAgent: String? = null,
    val ipAddress: String? = null,
    val createdAt: String? = null,
    val expiresAt: String? = null,
    val active: Boolean = true
)

@JsonClass(generateAdapter = true)
data class LoginRecordsResponse(
    val items: List<ApiLoginRecord> = emptyList(),
    val page: Int = 1,
    val limit: Int = 20,
    val total: Int = 0,
    val hasMore: Boolean = false
)

// -------------------------------------------------------------
// Retrofit API Service
// -------------------------------------------------------------

interface TokPulseApiService {

    @GET("api/v1/health")
    suspend fun healthCheck(): Response<HealthResponse>

    @POST("api/v1/auth/register")
    suspend fun register(@Body req: RegisterRequest): Response<AuthResponse>

    @POST("api/v1/auth/login")
    suspend fun login(@Body req: LoginRequest): Response<AuthResponse>

    @POST("api/v1/auth/logout")
    suspend fun logout(): Response<GenericActionResponse>

    @GET("api/v1/auth/sessions")
    suspend fun listSessions(): Response<SessionsResponse>

    @DELETE("api/v1/auth/sessions/{sessionId}")
    suspend fun revokeSession(@Path("sessionId") sessionId: String): Response<RevokeResponse>

    @POST("api/v1/auth/oauth/google")
    suspend fun oauthGoogle(@Body req: OAuthGoogleRequest): Response<AuthResponse>

    @POST("api/v1/auth/oauth/facebook")
    suspend fun oauthFacebook(@Body req: OAuthFacebookRequest): Response<AuthResponse>

    @POST("api/v1/auth/phone/request")
    suspend fun phoneRequest(@Body req: PhoneRequestBody): Response<OtpResponse>

    @POST("api/v1/auth/phone/verify")
    suspend fun phoneVerify(@Body req: PhoneVerifyRequest): Response<AuthResponse>

    @POST("api/v1/auth/recover/request")
    suspend fun recoverRequest(@Body req: RecoverRequestBody): Response<OtpResponse>

    @POST("api/v1/auth/recover/confirm")
    suspend fun recoverConfirm(@Body req: RecoverConfirmRequest): Response<AuthResponse>

    @GET("api/v1/auth/providers")
    suspend fun linkedProviders(): Response<ProvidersResponse>

    @POST("api/v1/auth/link")
    suspend fun linkProvider(@Body req: LinkProviderRequest): Response<LinkProviderResponse>

    @GET("api/v1/users/{userId}/notifications")
    suspend fun getNotifications(
        @Path("userId") userId: String,
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 20
    ): Response<NotificationsResponse>

    @POST("api/v1/users/{userId}/notifications/read")
    suspend fun readNotifications(
        @Path("userId") userId: String,
        @Body req: NotificationsReadRequest = NotificationsReadRequest()
    ): Response<NotificationsReadResponse>

    @GET("api/v1/admin/reports")
    suspend fun adminReports(
        @Query("status") status: String? = null,
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 20
    ): Response<AdminReportsResponse>

    @POST("api/v1/admin/reports/{reportId}/resolve")
    suspend fun resolveReport(
        @Path("reportId") reportId: String,
        @Body req: ResolveReportRequest
    ): Response<ResolveReportResponse>

    @GET("api/v1/admin/logins")
    suspend fun adminLogins(
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 20
    ): Response<LoginRecordsResponse>

    @GET("api/v1/admin/overview")
    suspend fun adminOverview(): Response<AdminOverviewResponse>

    @GET("api/v1/feed")
    suspend fun getFeed(
        @Query("cursor") cursor: String? = null,
        @Query("limit") limit: Int = 20,
        @Query("seen") seen: String? = null
    ): Response<FeedResponse>

    @GET("api/v1/external/videos")
    suspend fun getExternalVideos(
        @Query("page") page: Int = 1,
        @Query("per_page") perPage: Int = 15,
        @Query("query") query: String? = null
    ): Response<ExternalVideosResponse>

    @POST("api/v1/videos/upload-url")
    suspend fun requestUploadUrl(@Body req: UploadTicketRequest): Response<UploadTicketResponse>

    @POST("api/v1/videos/complete-upload")
    suspend fun completeUpload(@Body req: CompleteUploadRequest): Response<CompleteUploadResponse>

    @POST("api/v1/videos/{videoId}/like")
    suspend fun toggleLike(
        @Path("videoId") videoId: String,
        @Body req: LikeRequest
    ): Response<LikeResponse>

    @GET("api/v1/videos/{videoId}/comments")
    suspend fun getComments(@Path("videoId") videoId: String): Response<CommentsResponse>

    @POST("api/v1/videos/{videoId}/comments")
    suspend fun addComment(
        @Path("videoId") videoId: String,
        @Body req: AddCommentRequest
    ): Response<CommentItemResponse>

    @DELETE("api/v1/videos/{videoId}/comments/{commentId}")
    suspend fun deleteComment(
        @Path("videoId") videoId: String,
        @Path("commentId") commentId: String
    ): Response<GenericActionResponse>

    @POST("api/v1/videos/{videoId}/view")
    suspend fun recordView(@Path("videoId") videoId: String): Response<ViewResponse>

    @POST("api/v1/videos/{videoId}/share")
    suspend fun recordShare(
        @Path("videoId") videoId: String,
        @Body req: ShareRequest
    ): Response<ShareResponse>

    @DELETE("api/v1/videos/{videoId}")
    suspend fun deleteVideo(@Path("videoId") videoId: String): Response<GenericActionResponse>

    @POST("api/v1/auth/password/change")
    suspend fun changePassword(@Body req: ChangePasswordRequest): Response<ChangePasswordResponse>

    @PATCH("api/v1/users/{userId}")
    suspend fun updateUser(
        @Path("userId") userId: String,
        @Body req: UpdateUserRequest
    ): Response<UpdateUserResponse>

    @DELETE("api/v1/users/{userId}")
    suspend fun deleteUser(@Path("userId") userId: String): Response<DeleteUserResponse>

    @GET("api/v1/search")
    suspend fun search(@Query("q") query: String): Response<SearchResponse>

    @GET("api/v1/users/{userId}/profile")
    suspend fun getUserProfile(@Path("userId") userId: String): Response<UserProfileResponse>
}

// -------------------------------------------------------------
// Singleton Client
// -------------------------------------------------------------

object TokPulseApiClient {
    // Production Vercel Base URL - Points to deployed backend
    const val BASE_URL = "https://chort-nine.vercel.app/"
    const val FALLBACK_BASE_URL = "https://chort-nmk4.vercel.app/"

    private var authToken: String? = null

    fun setAuthToken(token: String?) {
        authToken = token
    }

    /** Fired when an authenticated non-auth call gets 401 (expired/revoked token). */
    var onUnauthorized: (() -> Unit)? = null

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .addInterceptor { chain ->
            val requestBuilder = chain.request().newBuilder()
            authToken?.let { token ->
                requestBuilder.addHeader("Authorization", "Bearer $token")
            }
            chain.proceed(requestBuilder.build())
        }
        .addInterceptor { chain ->
            val request = chain.request()
            val response = chain.proceed(request)
            // Expired/revoked token: drop it so the next launch re-authenticates.
            // Auth endpoints are excluded (their 401s mean wrong password/code).
            if (response.code == 401 && authToken != null &&
                !request.url.encodedPath.contains("/auth/")
            ) {
                authToken = null
                try { onUnauthorized?.invoke() } catch (_: Exception) {}
            }
            response
        }
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.HEADERS
        })
        .build()

    val api: TokPulseApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(TokPulseApiService::class.java)
    }

    val fallbackApi: TokPulseApiService by lazy {
        Retrofit.Builder()
            .baseUrl(FALLBACK_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(TokPulseApiService::class.java)
    }

    fun getCanonicalStreamUrl(videoId: String): String = "${BASE_URL}api/v1/videos/$videoId/stream"
    fun getFallbackStreamUrl(videoId: String): String = "${FALLBACK_BASE_URL}api/v1/videos/$videoId/stream"
}
