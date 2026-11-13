package com.plantogether.app.data

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

interface ApiService {

    /** 用来在「服务器设置」里测通不通；不需要登录。 */
    @GET("api/health")
    suspend fun health(): Map<String, String>

    // ---- 账号 ----

    @POST("api/auth/register")
    suspend fun register(@Body request: RegisterRequest): AuthResponse

    @POST("api/auth/login")
    suspend fun login(@Body request: LoginRequest): AuthResponse

    @GET("api/auth/me")
    suspend fun me(): MeResponse

    // ---- 行程 ----

    @GET("api/trips")
    suspend fun listTrips(): List<TripResponse>

    @POST("api/trips")
    suspend fun createTrip(@Body request: TripRequest): TripResponse

    @GET("api/trips/{tripId}")
    suspend fun getTrip(@Path("tripId") tripId: Long): TripResponse

    @PUT("api/trips/{tripId}")
    suspend fun updateTrip(@Path("tripId") tripId: Long, @Body request: TripRequest): TripResponse

    @DELETE("api/trips/{tripId}")
    suspend fun deleteTrip(@Path("tripId") tripId: Long): Response<Unit>

    @POST("api/trips/join")
    suspend fun joinTrip(@Body request: JoinTripRequest): TripResponse

    @POST("api/trips/{tripId}/invite-code/rotate")
    suspend fun rotateInviteCode(@Path("tripId") tripId: Long): TripResponse

    @GET("api/trips/{tripId}/export")
    suspend fun exportTrip(@Path("tripId") tripId: Long): Response<ResponseBody>

    /** 轻量变更计数：数字变大说明有别人改了东西。 */
    @GET("api/trips/{tripId}/revision")
    suspend fun getRevision(@Path("tripId") tripId: Long): RevisionResponse

    // ---- 成员 / 动态 ----

    @GET("api/trips/{tripId}/members")
    suspend fun listMembers(@Path("tripId") tripId: Long): List<MemberResponse>

    @PUT("api/trips/{tripId}/members/{memberId}")
    suspend fun updateMemberRole(
        @Path("tripId") tripId: Long,
        @Path("memberId") memberId: Long,
        @Body request: MemberRoleRequest,
    ): MemberResponse

    @DELETE("api/trips/{tripId}/members/{memberId}")
    suspend fun removeMember(
        @Path("tripId") tripId: Long,
        @Path("memberId") memberId: Long,
    ): Response<Unit>

    /** 成员自己退出行程。 */
    @DELETE("api/trips/{tripId}/members/me")
    suspend fun leaveTrip(@Path("tripId") tripId: Long): Response<Unit>

    @GET("api/trips/{tripId}/activity")
    suspend fun listActivity(@Path("tripId") tripId: Long): List<ActivityResponse>

    // ---- 地点 ----

    @GET("api/trips/{tripId}/places")
    suspend fun listPlaces(@Path("tripId") tripId: Long): List<PlaceResponse>

    @POST("api/trips/{tripId}/places")
    suspend fun addPlace(@Path("tripId") tripId: Long, @Body request: PlaceRequest): PlaceResponse

    @PUT("api/trips/{tripId}/places/{placeId}")
    suspend fun updatePlace(
        @Path("tripId") tripId: Long,
        @Path("placeId") placeId: Long,
        @Body request: PlaceRequest,
    ): PlaceResponse

    @DELETE("api/trips/{tripId}/places/{placeId}")
    suspend fun deletePlace(
        @Path("tripId") tripId: Long,
        @Path("placeId") placeId: Long,
    ): Response<Unit>

    @PUT("api/trips/{tripId}/places/reorder")
    suspend fun reorderPlaces(@Path("tripId") tripId: Long, @Body orderedIds: List<Long>): List<PlaceResponse>

    // ---- 链接 ----

    @GET("api/trips/{tripId}/links")
    suspend fun listLinks(@Path("tripId") tripId: Long): List<LinkResponse>

    @POST("api/trips/{tripId}/links")
    suspend fun addLink(@Path("tripId") tripId: Long, @Body request: LinkRequest): LinkResponse

    @PUT("api/trips/{tripId}/links/{linkId}")
    suspend fun updateLink(
        @Path("tripId") tripId: Long,
        @Path("linkId") linkId: Long,
        @Body request: LinkRequest,
    ): LinkResponse

    @DELETE("api/trips/{tripId}/links/{linkId}")
    suspend fun deleteLink(
        @Path("tripId") tripId: Long,
        @Path("linkId") linkId: Long,
    ): Response<Unit>

    // ---- 评论 ----

    @GET("api/trips/{tripId}/comments")
    suspend fun listComments(@Path("tripId") tripId: Long): List<CommentResponse>

    @POST("api/trips/{tripId}/comments")
    suspend fun addComment(@Path("tripId") tripId: Long, @Body request: CommentRequest): CommentResponse

    @DELETE("api/trips/{tripId}/comments/{commentId}")
    suspend fun deleteComment(
        @Path("tripId") tripId: Long,
        @Path("commentId") commentId: Long,
    ): Response<Unit>

    // ---- 清单 ----

    @GET("api/trips/{tripId}/checklist")
    suspend fun listChecklist(@Path("tripId") tripId: Long): List<ChecklistResponse>

    @POST("api/trips/{tripId}/checklist")
    suspend fun addChecklistItem(
        @Path("tripId") tripId: Long,
        @Body request: ChecklistRequest,
    ): ChecklistResponse

    @PUT("api/trips/{tripId}/checklist/{itemId}")
    suspend fun updateChecklistItem(
        @Path("tripId") tripId: Long,
        @Path("itemId") itemId: Long,
        @Body request: ChecklistRequest,
    ): ChecklistResponse

    @DELETE("api/trips/{tripId}/checklist/{itemId}")
    suspend fun deleteChecklistItem(
        @Path("tripId") tripId: Long,
        @Path("itemId") itemId: Long,
    ): Response<Unit>

    // ---- 决策投票 ----

    @GET("api/trips/{tripId}/decisions")
    suspend fun listDecisions(@Path("tripId") tripId: Long): List<DecisionResponse>

    @POST("api/trips/{tripId}/decisions")
    suspend fun createDecision(
        @Path("tripId") tripId: Long,
        @Body request: DecisionRequest,
    ): DecisionResponse

    @POST("api/trips/{tripId}/decisions/{decisionId}/options")
    suspend fun addDecisionOption(
        @Path("tripId") tripId: Long,
        @Path("decisionId") decisionId: Long,
        @Body request: OptionRequest,
    ): DecisionResponse

    @DELETE("api/trips/{tripId}/decisions/{decisionId}/options/{optionId}")
    suspend fun removeDecisionOption(
        @Path("tripId") tripId: Long,
        @Path("decisionId") decisionId: Long,
        @Path("optionId") optionId: Long,
    ): DecisionResponse

    @POST("api/trips/{tripId}/decisions/{decisionId}/options/{optionId}/vote")
    suspend fun voteDecisionOption(
        @Path("tripId") tripId: Long,
        @Path("decisionId") decisionId: Long,
        @Path("optionId") optionId: Long,
    ): DecisionResponse

    @PUT("api/trips/{tripId}/decisions/{decisionId}/status")
    suspend fun setDecisionStatus(
        @Path("tripId") tripId: Long,
        @Path("decisionId") decisionId: Long,
        @Body request: DecisionStatusRequest,
    ): DecisionResponse

    @DELETE("api/trips/{tripId}/decisions/{decisionId}")
    suspend fun deleteDecision(
        @Path("tripId") tripId: Long,
        @Path("decisionId") decisionId: Long,
    ): Response<Unit>
}
