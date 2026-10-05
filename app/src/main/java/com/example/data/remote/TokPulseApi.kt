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
import retrofit2.http.GET
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
    val role: String? = "USER"
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
    val thumbnailUrl: String? = null,
    val musicTitle: String? = null,
    val likesCount: Int = 0,
    val commentsCount: Int = 0,
    val sharesCount: Int = 0,
    val viewsCount: Int = 0,
    val aspectRatio: String? = "9:16",
    val createdAt: Long = 0
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
    val aspectRatio: String? = "9:16"
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

    @GET("api/v1/feed")
    suspend fun getFeed(
        @Query("cursor") cursor: String? = null,
        @Query("limit") limit: Int = 20
    ): Response<FeedResponse>

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

    @GET("api/v1/search")
    suspend fun search(@Query("q") query: String): Response<SearchResponse>

    @GET("api/v1/users/{userId}/profile")
    suspend fun getUserProfile(@Path("userId") userId: String): Response<UserProfileResponse>
}

// -------------------------------------------------------------
// Singleton Client
// -------------------------------------------------------------

object TokPulseApiClient {
    // Production Vercel Base URL - Never localhost or empty!
    const val BASE_URL = "https://chort-nmk4.vercel.app/"

    private var authToken: String? = null

    fun setAuthToken(token: String?) {
        authToken = token
    }

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val requestBuilder = chain.request().newBuilder()
            authToken?.let { token ->
                requestBuilder.addHeader("Authorization", "Bearer $token")
            }
            chain.proceed(requestBuilder.build())
        }
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
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
}
