package br.com.bragasaude.data.remote.api

import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.*

/**
 * Interface Retrofit que declara os endpoints do Gateway Python (Braga Saúde).
 * Progressivamente substitui as chamadas HttpURLConnection do BragaApiClient.
 */
interface BragaApiService {

    @POST("api/profile/sync")
    suspend fun syncProfile(@Body body: ProfileDto): Response<SyncResponseDto>

    @GET("api/profile/{userId}")
    suspend fun getProfile(@Path("userId") userId: String): Response<ProfileDto>

    @POST("api/vitals/sync")
    suspend fun syncVitals(@Body vitals: List<VitalSignDto>): Response<SyncResponseDto>

    @GET("api/vitals/{userId}")
    suspend fun getVitals(
        @Path("userId") userId: String,
        @Query("since") sinceTimestamp: Long? = null
    ): Response<List<VitalSignDto>>

    @POST("api/hydration/sync")
    suspend fun syncHydration(@Body hydration: HydrationDto): Response<SyncResponseDto>

    @GET("api/social/feed")
    suspend fun getGlobalFeed(
        @Query("limit") limit: Int = 20,
        @Query("offset") offset: Int = 0
    ): Response<List<SocialPostDto>>

    @POST("api/social/posts/{postId}/react")
    suspend fun reactToPost(
        @Path("postId") postId: String,
        @Body reaction: ReactionDto
    ): Response<Unit>
}

// ==================== DTOs ====================

@Serializable
data class SyncResponseDto(
    val success: Boolean,
    val message: String? = null,
    val timestamp: Long? = null
)

@Serializable
data class ProfileDto(
    val id: String,
    val fullName: String,
    val birthDate: String? = null,
    val gender: String? = null,
    val height: Float? = null,
    val weight: Float? = null,
    val isSmoker: Boolean = false,
    val hasDiabetes: Boolean = false,
    val hasHypertension: Boolean = false
)

@Serializable
data class VitalSignDto(
    val id: String,
    val userId: String,
    val type: String,
    val value: Double,
    val unit: String,
    val timestamp: Long
)

@Serializable
data class HydrationDto(
    val userId: String,
    val date: String,
    val amountMl: Int,
    val dailyGoalMl: Int
)

@Serializable
data class SocialPostDto(
    val id: String,
    val authorName: String,
    val title: String,
    val content: String,
    val createdAt: Long,
    val reactionCount: Int = 0
)

@Serializable
data class ReactionDto(
    val userId: String,
    val reactionType: String = "SUPPORT"
)