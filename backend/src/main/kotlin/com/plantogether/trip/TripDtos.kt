package com.plantogether.trip

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.Size
import java.time.Instant
import java.time.LocalDate

data class TripRequest(
    @field:NotBlank(message = "行程标题不能为空")
    @field:Size(max = 120, message = "行程标题最多 120 字")
    val title: String,

    @field:Size(max = 120, message = "目的地最多 120 字")
    val destination: String? = null,

    val startDate: LocalDate? = null,
    val endDate: LocalDate? = null,

    @field:Min(value = 1, message = "人数至少为 1")
    val groupSize: Int? = null,

    /** 手动填写的总预算目标，不做任何自动计价。 */
    val budgetTarget: Double? = null,

    val visibility: String = "PRIVATE",

    /** 客户端读到的版本号；带上时做乐观锁校验，不一致返回 409。 */
    val version: Int? = null,
)

data class TripResponse(
    val id: Long,
    val title: String,
    val destination: String?,
    val startDate: LocalDate?,
    val endDate: LocalDate?,
    val groupSize: Int?,
    val budgetTarget: Double?,
    val visibility: String,
    /** 仅创建者可见邀请码，其他成员返回 null。 */
    val inviteCode: String?,
    val ownerName: String,
    /** 当前登录用户在这个行程里的角色：OWNER / EDITOR / VIEWER。 */
    val role: String,
    val memberCount: Int,
    val version: Int,
    /** 该行程的变更计数（最新动态 id），用于列表上的「有新动态」红点。 */
    val revision: Long,
    val createdAt: Instant,
)

data class PlaceRequest(
    @field:NotBlank(message = "地点名称不能为空")
    @field:Size(max = 120, message = "地点名称最多 120 字")
    val name: String,

    val address: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val date: LocalDate? = null,
    val startTime: String? = null,
    val endTime: String? = null,
    val budget: Double? = null,
    val stayLength: String? = null,
    val notes: String? = null,
    val backupOption: String? = null,
    val mapLink: String? = null,
    /** 从高德带回来的评分、人均、营业时间（可空，用户手填地点时一般没有）。 */
    val rating: String? = null,
    val avgCost: String? = null,
    val openTime: String? = null,
    val sortOrder: Int = 0,
    val version: Int? = null,
)

data class PlaceResponse(
    val id: Long,
    val name: String,
    val address: String?,
    val latitude: Double?,
    val longitude: Double?,
    val date: LocalDate?,
    val startTime: String?,
    val endTime: String?,
    val budget: Double?,
    val stayLength: String?,
    val notes: String?,
    val backupOption: String?,
    val mapLink: String?,
    val rating: String?,
    val avgCost: String?,
    val openTime: String?,
    val sortOrder: Int,
    val createdById: Long?,
    val createdByName: String?,
    val version: Int,
)

// ---- 成员 ----

data class JoinTripRequest(
    @field:NotBlank(message = "邀请码不能为空")
    val inviteCode: String,
)

data class MemberResponse(
    val id: Long,
    val userId: Long,
    val displayName: String,
    val email: String,
    val role: String,
    val isOwner: Boolean,
    val joinedAt: Instant,
    /** 当前用户能否把这位成员移除/改角色（仅创建者可操作，且不能操作自己）。 */
    val canManage: Boolean,
)

data class MemberRoleRequest(
    @field:NotBlank(message = "角色不能为空")
    val role: String,
)

// ---- 活动记录 ----

data class ActivityResponse(
    val id: Long,
    val kind: String,
    val summary: String,
    val userName: String?,
    val createdAt: Instant,
)
