package com.plantogether.app.data

// ---------------- 请求 ----------------

data class RegisterRequest(
    val email: String,
    val password: String,
    val displayName: String,
)

data class LoginRequest(
    val email: String,
    val password: String,
)

data class TripRequest(
    val title: String,
    val destination: String? = null,
    val startDate: String? = null,
    val endDate: String? = null,
    val groupSize: Int? = null,
    val budgetTarget: Double? = null,
    val visibility: String = "PRIVATE",
    /** 编辑时带上读到的版本号，服务端发现被别人改过会返回 409。 */
    val version: Int? = null,
)

data class PlaceRequest(
    val name: String,
    val address: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val date: String? = null,
    val startTime: String? = null,
    val endTime: String? = null,
    val budget: Double? = null,
    val stayLength: String? = null,
    val notes: String? = null,
    val backupOption: String? = null,
    val mapLink: String? = null,
    val rating: String? = null,
    val avgCost: String? = null,
    val openTime: String? = null,
    val sortOrder: Int = 0,
    val version: Int? = null,
)

data class LinkRequest(
    val url: String,
    val title: String? = null,
    val source: String? = null,
    val tags: String? = null,
    val notes: String? = null,
    val version: Int? = null,
)

data class JoinTripRequest(
    val inviteCode: String,
)

data class MemberRoleRequest(
    val role: String,
)

data class CommentRequest(
    val body: String,
    val placeId: Long? = null,
)

data class ChecklistRequest(
    val title: String,
    val done: Boolean? = null,
    val assignee: String? = null,
    val version: Int? = null,
)

data class DecisionRequest(
    val title: String,
    val notes: String? = null,
    val options: List<String> = emptyList(),
)

data class OptionRequest(
    val title: String,
    val notes: String? = null,
)

data class DecisionStatusRequest(
    val status: String,
    val decidedOptionId: Long? = null,
    val version: Int? = null,
)

data class RevisionResponse(
    val revision: Long,
)

// ---------------- 响应 ----------------

data class AuthResponse(
    val token: String,
    val userId: Long,
    val email: String,
    val displayName: String,
)

data class MeResponse(
    val userId: Long,
    val email: String,
    val displayName: String,
)

data class TripResponse(
    val id: Long,
    val title: String,
    val destination: String? = null,
    val startDate: String? = null,
    val endDate: String? = null,
    val groupSize: Int? = null,
    val budgetTarget: Double? = null,
    val visibility: String = "PRIVATE",
    /** 仅创建者能看到邀请码，其他成员为 null。 */
    val inviteCode: String? = null,
    val ownerName: String = "",
    /** OWNER / EDITOR / VIEWER */
    val role: String = "OWNER",
    val memberCount: Int = 1,
    val version: Int = 0,
    /** 该行程的变更计数，用来判断列表上要不要显示「有新动态」。 */
    val revision: Long = 0,
    val createdAt: String = "",
) {
    val isOwner: Boolean get() = role == "OWNER"
    val canWrite: Boolean get() = role == "OWNER" || role == "EDITOR"
}

data class PlaceResponse(
    val id: Long,
    val name: String,
    val address: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val date: String? = null,
    val startTime: String? = null,
    val endTime: String? = null,
    val budget: Double? = null,
    val stayLength: String? = null,
    val notes: String? = null,
    val backupOption: String? = null,
    val mapLink: String? = null,
    val rating: String? = null,
    val avgCost: String? = null,
    val openTime: String? = null,
    val sortOrder: Int = 0,
    val createdById: Long? = null,
    val createdByName: String? = null,
    val version: Int = 0,
)

/**
 * 高德搜出来的一条地点。字段全是可空的——小店铺常常没有评分、人均、营业时间。
 */
data class PoiSuggestion(
    val id: String,
    val name: String,
    val address: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val type: String? = null,
    val rating: String? = null,
    val avgCost: String? = null,
    val tag: String? = null,
    val openTimeToday: String? = null,
    val tel: String? = null,
    val businessArea: String? = null,
) {
    /** 卡片副标题：分类取最后一段，如「餐饮服务;中餐厅;浙江菜」→「浙江菜」。 */
    val typeLabel: String?
        get() = type?.split(';')?.lastOrNull()?.trim()?.takeIf { it.isNotBlank() }

    /** 评分 + 人均拼成一行，缺哪个就少显示哪个。 */
    val scoreLabel: String?
        get() {
            val parts = buildList {
                rating?.let { add("★ $it") }
                avgCost?.takeIf { it.isNotBlank() && it != "0.00" }?.let { add("人均 ¥$it") }
            }
            return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
        }
}

data class PoiSearchResponse(
    val configured: Boolean = true,
    val items: List<PoiSuggestion> = emptyList(),
    val message: String? = null,
)

data class LinkResponse(
    val id: Long,
    val url: String,
    val title: String? = null,
    val source: String? = null,
    val tags: String? = null,
    val notes: String? = null,
    val createdById: Long? = null,
    val createdByName: String? = null,
    val version: Int = 0,
    val createdAt: String = "",
)

data class MemberResponse(
    val id: Long,
    val userId: Long,
    val displayName: String,
    val email: String,
    val role: String,
    val isOwner: Boolean = false,
    val joinedAt: String = "",
    val canManage: Boolean = false,
)

data class CommentResponse(
    val id: Long,
    val body: String,
    val placeId: Long? = null,
    val placeName: String? = null,
    val authorId: Long,
    val authorName: String,
    val createdAt: String = "",
    val canDelete: Boolean = false,
)

data class ChecklistResponse(
    val id: Long,
    val title: String,
    val done: Boolean = false,
    val assignee: String? = null,
    val createdByName: String? = null,
    val sortOrder: Int = 0,
    val version: Int = 0,
    val createdAt: String = "",
)

data class DecisionOptionResponse(
    val id: Long,
    val title: String,
    val notes: String? = null,
    val sortOrder: Int = 0,
    val voteCount: Int = 0,
    val votedByMe: Boolean = false,
    val voterNames: List<String> = emptyList(),
)

data class DecisionResponse(
    val id: Long,
    val title: String,
    val notes: String? = null,
    val status: String = "OPEN",
    val decidedOptionId: Long? = null,
    val options: List<DecisionOptionResponse> = emptyList(),
    val totalVotes: Int = 0,
    val createdByName: String? = null,
    val canDelete: Boolean = false,
    val version: Int = 0,
    val createdAt: String = "",
    val updatedAt: String = "",
) {
    val isDecided: Boolean get() = status == "DECIDED"
    val isCancelled: Boolean get() = status == "CANCELLED"
}

data class ActivityResponse(
    val id: Long,
    val kind: String,
    val summary: String,
    val userName: String? = null,
    val createdAt: String = "",
)

data class ErrorResponse(
    val error: String? = null,
)
